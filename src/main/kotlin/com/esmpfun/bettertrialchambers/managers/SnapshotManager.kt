package com.esmpfun.bettertrialchambers.managers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.utils.CompressionUtil
import com.esmpfun.bettertrialchambers.utils.NBTUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Manages chamber snapshots - creating, saving, loading, and validating.
 * This is the most critical component for chamber resets.
 *
 * Snapshots are stored as compressed files on disk (NOT in the database).
 *
 * File format (v2, streamed): gzip stream containing a small header followed by
 * per-block records that reference a running palette of block-data strings.
 * Capture writes each batch straight to disk and load reads straight from disk,
 * so memory use no longer scales with three full copies of the chamber — this
 * is what lets multi-million-block chambers snapshot on small-heap servers.
 * Files written by older versions (Java-serialized [SnapshotData]) are detected
 * by their leading bytes and still load fine.
 */
class SnapshotManager(private val plugin: BetterTrialChambers) {

    private companion object {
        /** "BTC2" — first four bytes (after gzip) of a v2 streamed snapshot. */
        const val MAGIC_V2 = 0x42544332
        const val FORMAT_VERSION = 2

        const val RECORD_END = 0
        const val RECORD_PALETTE = 1
        const val RECORD_BLOCK = 2
    }

    /**
     * Serializable wrapper for legacy (pre-v2) snapshot data. Only used to
     * read files written by older plugin versions.
     */
    private data class SnapshotData(
        val worldName: String,
        val originX: Int,
        val originY: Int,
        val originZ: Int,
        val blocks: Map<Triple<Int, Int, Int>, BlockSnapshot>
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** One captured block, relative to the chamber origin, held only per batch. */
    private class CapturedBlock(
        val relX: Int,
        val relY: Int,
        val relZ: Int,
        val snapshot: BlockSnapshot
    )

    /**
     * Incremental writer for the v2 format. Blocks are appended as they are
     * captured; block-data strings are deduplicated through a palette that is
     * emitted inline the first time each string appears.
     */
    private class SnapshotWriter(
        file: File,
        worldName: String,
        originX: Int,
        originY: Int,
        originZ: Int
    ) : AutoCloseable {
        private val out = DataOutputStream(
            BufferedOutputStream(GZIPOutputStream(FileOutputStream(file)), 1 shl 16)
        )
        private val palette = HashMap<String, Int>()
        private var closed = false
        var blocksWritten = 0
            private set

        init {
            out.writeInt(MAGIC_V2)
            out.writeByte(FORMAT_VERSION)
            out.writeUTF(worldName)
            out.writeInt(originX)
            out.writeInt(originY)
            out.writeInt(originZ)
        }

        fun writeBlock(relX: Int, relY: Int, relZ: Int, snapshot: BlockSnapshot) {
            val paletteId = palette.getOrPut(snapshot.blockData) {
                out.writeByte(RECORD_PALETTE)
                out.writeUTF(snapshot.blockData)
                palette.size
            }
            out.writeByte(RECORD_BLOCK)
            out.writeInt(relX)
            out.writeInt(relY)
            out.writeInt(relZ)
            out.writeInt(paletteId)
            val tile = snapshot.tileEntity
            if (tile == null) {
                out.writeBoolean(false)
            } else {
                out.writeBoolean(true)
                // Tile entities are rare (vaults, spawners, pots); a per-entity
                // serialized blob keeps the common block record tiny.
                val buffer = ByteArrayOutputStream()
                ObjectOutputStream(buffer).use { it.writeObject(HashMap(tile)) }
                val bytes = buffer.toByteArray()
                out.writeInt(bytes.size)
                out.write(bytes)
            }
            blocksWritten++
        }

        override fun close() {
            if (closed) return
            closed = true
            out.writeByte(RECORD_END)
            out.close()
        }
    }

    /**
     * Creates a complete snapshot of a chamber.
     * Captures all blocks, tile entities, and NBT data within the chamber bounds.
     *
     * Each capture batch is written straight to the snapshot file, so peak
     * memory is one batch of blocks regardless of chamber size.
     *
     * @param chamber The chamber to snapshot
     * @return The snapshot file created
     * @throws Exception if snapshot creation fails
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun createSnapshot(chamber: Chamber): File {
        plugin.logger.info("Creating snapshot for chamber: ${chamber.name}")

        val world = chamber.getWorld() ?: throw IllegalStateException("World ${chamber.world} not found")

        var totalBlocks = 0
        var capturedBlocks = 0

        // Origin point for relative coordinates
        val originX = chamber.minX
        val originY = chamber.minY
        val originZ = chamber.minZ

        // Written to a temp file first so a crash mid-capture never corrupts an
        // existing good snapshot; moved into place only after a clean finish.
        val finalFile = File(plugin.snapshotsDir, "${chamber.name}.dat")
        val tempFile = File(plugin.snapshotsDir, "${chamber.name}.dat.tmp")

        // v1.7.0: capture in X-slice batches, one scheduled task per batch, so snapshotting
        // a multi-million-block chamber (structure-bounds discovery / imported dungeons)
        // never stalls a tick. Small chambers still complete in one batch, as before.
        // Batches run sequentially (each awaited) and are streamed to disk between
        // batches. MUST run on the owning region thread to access block entities;
        // each batch is scheduled at its own slice's location (Folia region correctness).
        val sizeY = chamber.maxY - chamber.minY + 1
        val sizeZ = chamber.maxZ - chamber.minZ + 1
        val sliceBlocks = maxOf(1, sizeY * sizeZ)
        val slicesPerBatch = maxOf(1, ChamberManager.VOLUME_SCAN_BLOCKS_PER_BATCH / sliceBlocks)
        val midY = (chamber.minY + chamber.maxY) / 2.0
        val midZ = (chamber.minZ + chamber.maxZ) / 2.0

        val writer = withContext(Dispatchers.IO) {
            SnapshotWriter(tempFile, chamber.world, originX, originY, originZ)
        }

        try {
            var xStart = chamber.minX
            while (xStart <= chamber.maxX) {
                val xEnd = minOf(xStart + slicesPerBatch - 1, chamber.maxX)
                val batchStartX = xStart
                val batch: Pair<Int, ArrayList<CapturedBlock>> =
                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        plugin.scheduler.runAtLocation(Location(world, (batchStartX + xEnd) / 2.0, midY, midZ), Runnable {
                            try {
                                var batchTotal = 0
                                val captured = ArrayList<CapturedBlock>()
                                for (x in batchStartX..xEnd) {
                                    for (y in chamber.minY..chamber.maxY) {
                                        for (z in chamber.minZ..chamber.maxZ) {
                                            batchTotal++

                                            val block = world.getBlockAt(x, y, z)

                                            // Skip air blocks to reduce file size (optional optimization)
                                            if (block.type == Material.AIR) {
                                                continue
                                            }

                                            // Capture block data
                                            val blockData = block.blockData.asString

                                            // Capture tile entity data if applicable (MUST be on main thread)
                                            val tileEntity = NBTUtil.captureTileEntity(block.state)

                                            captured.add(
                                                CapturedBlock(
                                                    x - originX,
                                                    y - originY,
                                                    z - originZ,
                                                    BlockSnapshot(blockData, tileEntity)
                                                )
                                            )
                                        }
                                    }
                                }
                                continuation.resume(batchTotal to captured) {}
                            } catch (e: Exception) {
                                continuation.resumeWith(Result.failure(e))
                            }
                        })
                    }

                totalBlocks += batch.first
                capturedBlocks += batch.second.size

                // Flush this batch to disk before capturing the next one
                withContext(Dispatchers.IO) {
                    for (captured in batch.second) {
                        writer.writeBlock(captured.relX, captured.relY, captured.relZ, captured.snapshot)
                    }
                }

                xStart = xEnd + 1
            }

            plugin.logger.info("Captured $capturedBlocks blocks (${totalBlocks - capturedBlocks} air blocks skipped)")

            return withContext(Dispatchers.IO) {
                writer.close()
                Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                val fileSize = CompressionUtil.formatSize(finalFile.length())
                plugin.logger.info("Snapshot created: ${finalFile.name} ($fileSize, $capturedBlocks blocks)")

                finalFile
            }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                runCatching { writer.close() }
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * Lightweight description of a snapshot's coverage, produced by
     * [prepareAndScan] without loading any block data: the covered bounds plus
     * every covered position packed via [com.esmpfun.bettertrialchambers.utils.BlockRestorer.pack],
     * sorted ascending. ~8 bytes per block instead of a full Location-keyed map.
     */
    class SnapshotScan(
        val worldName: String,
        val blockCount: Int,
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val sortedPositions: LongArray
    )

    /**
     * First pass of a streamed restore: migrates a legacy (Java-serialized)
     * snapshot file to the v2 streamed format in place if needed, then scans
     * the file and returns its coverage ([SnapshotScan]) without keeping any
     * block data in memory. Returns null if the file is missing or unreadable.
     */
    suspend fun prepareAndScan(file: File): SnapshotScan? = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            plugin.logger.severe("Snapshot file not found: ${file.name}")
            return@withContext null
        }

        try {
            // Read pass. Legacy files must be fully deserialized once (Java
            // serialization can't stream); the data is used to rewrite the file
            // in v2 format below so every later pass is cheap.
            var legacyData: SnapshotData? = null
            var scan: SnapshotScan? = null

            FileInputStream(file).use { raw ->
                BufferedInputStream(GZIPInputStream(raw), 1 shl 16).use { input ->
                    input.mark(8)
                    val header = ByteArray(4)
                    val read = input.readNBytes(header, 0, 4)
                    val magic = if (read == 4) ByteBuffer.wrap(header).int else 0

                    if (magic == MAGIC_V2) {
                        scan = scanV2(DataInputStream(input), file.name)
                    } else {
                        input.reset()
                        legacyData = ObjectInputStream(input).use { it.readObject() as SnapshotData }
                    }
                }
            }

            val legacy = legacyData
            if (legacy != null) {
                if (!validateSnapshot(legacy)) {
                    plugin.logger.severe("Snapshot validation failed: ${file.name}")
                    return@withContext null
                }
                migrateLegacySnapshot(file, legacy)
                scan = scanFromLegacy(legacy)
            }
            scan
        } catch (e: Exception) {
            plugin.logger.severe("Failed to scan snapshot ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Streams a snapshot file's blocks in [batchSize] groups without ever
     * materializing the whole snapshot, invoking [onBatch] for each group.
     * Legacy files fall back to a full load and are then fed out in batches —
     * run [prepareAndScan] first to migrate them so this path stays cheap.
     *
     * @return the total number of blocks streamed, or null on failure.
     */
    suspend fun streamSnapshotBlocks(
        file: File,
        batchSize: Int,
        onBatch: suspend (List<Pair<Location, BlockSnapshot>>) -> Unit
    ): Int? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { raw ->
                BufferedInputStream(GZIPInputStream(raw), 1 shl 16).use { input ->
                    input.mark(8)
                    val header = ByteArray(4)
                    val read = input.readNBytes(header, 0, 4)
                    val magic = if (read == 4) ByteBuffer.wrap(header).int else 0

                    if (magic == MAGIC_V2) {
                        streamV2Blocks(DataInputStream(input), file.name, batchSize, readTiles = true, onBatch)
                    } else {
                        input.reset()
                        val blocks = readLegacySnapshot(input, file.name) ?: return@withContext null
                        blocks.entries
                            .chunked(batchSize) { chunk -> chunk.map { it.key to it.value } }
                            .forEach { onBatch(it) }
                        blocks.size
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to stream snapshot ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /** Builds a [SnapshotScan] from a v2 stream (magic already consumed). */
    private suspend fun scanV2(input: DataInputStream, label: String): SnapshotScan? {
        var worldName = ""
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        var positions = LongArray(4096)
        var count = 0

        val streamed = streamV2Blocks(input, label, batchSize = 8192, readTiles = false) { batch ->
            for ((location, _) in batch) {
                if (worldName.isEmpty()) worldName = location.world?.name ?: ""
                val x = location.blockX; val y = location.blockY; val z = location.blockZ
                if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
                if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
                if (count == positions.size) positions = positions.copyOf(positions.size * 2)
                positions[count++] = com.esmpfun.bettertrialchambers.utils.BlockRestorer.pack(x, y, z)
            }
        } ?: return null

        val sorted = positions.copyOf(count)
        sorted.sort()
        return SnapshotScan(worldName, streamed, minX, minY, minZ, maxX, maxY, maxZ, sorted)
    }

    /** Builds a [SnapshotScan] from fully-loaded legacy data. */
    private fun scanFromLegacy(data: SnapshotData): SnapshotScan {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        val positions = LongArray(data.blocks.size)
        var count = 0

        for (relativePos in data.blocks.keys) {
            val x = data.originX + relativePos.first
            val y = data.originY + relativePos.second
            val z = data.originZ + relativePos.third
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
            positions[count++] = com.esmpfun.bettertrialchambers.utils.BlockRestorer.pack(x, y, z)
        }
        positions.sort()
        return SnapshotScan(data.worldName, count, minX, minY, minZ, maxX, maxY, maxZ, positions)
    }

    /**
     * Rewrites a legacy snapshot file in the v2 streamed format (via a temp
     * file, so an interruption never corrupts the original). One-time cost per
     * file; every later load/stream then takes the cheap path.
     */
    private fun migrateLegacySnapshot(file: File, data: SnapshotData) {
        val tempFile = File(file.parentFile, file.name + ".tmp")
        try {
            SnapshotWriter(tempFile, data.worldName, data.originX, data.originY, data.originZ).use { writer ->
                data.blocks.forEach { (relativePos, snapshot) ->
                    writer.writeBlock(relativePos.first, relativePos.second, relativePos.third, snapshot)
                }
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            plugin.logger.info("Migrated legacy snapshot ${file.name} to the streamed format")
        } catch (e: Exception) {
            tempFile.delete()
            plugin.logger.warning("Could not migrate legacy snapshot ${file.name} (will keep working, just slower to load): ${e.message}")
        }
    }

    /**
     * Loads a snapshot from disk.
     *
     * @param file The snapshot file
     * @return Map of locations to block snapshots, or null if loading fails
     */
    suspend fun loadSnapshot(file: File): Map<Location, BlockSnapshot>? = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            plugin.logger.severe("Snapshot file not found: ${file.name}")
            return@withContext null
        }

        try {
            val blocks = FileInputStream(file).use { input ->
                readSnapshot(input, file.name)
            } ?: return@withContext null

            plugin.logger.info("Loaded snapshot: ${file.name} (${blocks.size} blocks)")
            blocks
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load snapshot ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads a snapshot from in-memory gzip-compressed bytes (the same format
     * a snapshot file holds). Used by the public `ChamberResetEvent.snapshotOverride`
     * hook (v1.4.0+) so listeners can substitute reset content without
     * touching the chamber's persisted snapshot file.
     *
     * Returns null if the bytes fail to decompress, fail validation, or
     * reference an unknown world. Caller falls back to the on-disk snapshot
     * in that case.
     *
     * @param bytes Gzip-compressed snapshot bytes (v2 streamed format or the
     *              legacy Java-serialized form — both are accepted).
     * @param contextLabel Short label included in log messages (e.g. the
     *                     chamber name) so admins can trace which override
     *                     produced a parse failure.
     */
    suspend fun loadSnapshotFromBytes(
        bytes: ByteArray,
        contextLabel: String = "<override>"
    ): Map<Location, BlockSnapshot>? = withContext(Dispatchers.IO) {
        try {
            val blocks = ByteArrayInputStream(bytes).use { input ->
                readSnapshot(input, contextLabel)
            } ?: return@withContext null

            plugin.logger.info("Loaded snapshot override for $contextLabel (${blocks.size} blocks)")
            blocks
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load snapshot override for $contextLabel: ${e.message}")
            null
        }
    }

    /**
     * Reads a snapshot from a raw (still-gzipped) stream, auto-detecting the
     * v2 streamed format vs the legacy Java-serialized format. Returns the
     * absolute-location block map, or null (with a logged reason) on failure.
     */
    private suspend fun readSnapshot(rawInput: InputStream, label: String): Map<Location, BlockSnapshot>? {
        BufferedInputStream(GZIPInputStream(rawInput), 1 shl 16).use { input ->
            input.mark(8)
            val header = ByteArray(4)
            val read = input.readNBytes(header, 0, 4)
            val magic = if (read == 4) ByteBuffer.wrap(header).int else 0

            return if (magic == MAGIC_V2) {
                val blocks = HashMap<Location, BlockSnapshot>()
                streamV2Blocks(DataInputStream(input), label, batchSize = 8192, readTiles = true) { batch ->
                    for ((location, snapshot) in batch) blocks[location] = snapshot
                } ?: return null
                if (blocks.isEmpty()) {
                    plugin.logger.warning("Invalid snapshot $label: no blocks captured")
                    return null
                }
                blocks
            } else {
                input.reset()
                readLegacySnapshot(input, label)
            }
        }
    }

    /**
     * Core v2 record walker (magic already consumed): reads the header, then
     * emits blocks to [onBatch] in [batchSize] groups. With [readTiles] false,
     * tile-entity blobs are skipped without deserializing (cheap position scan).
     *
     * @return the number of blocks read, or null on corruption (logged).
     */
    private suspend fun streamV2Blocks(
        input: DataInputStream,
        label: String,
        batchSize: Int,
        readTiles: Boolean,
        onBatch: suspend (List<Pair<Location, BlockSnapshot>>) -> Unit
    ): Int? {
        val version = input.readByte().toInt()
        if (version != FORMAT_VERSION) {
            plugin.logger.severe("Snapshot $label has unsupported format version $version")
            return null
        }

        val worldName = input.readUTF()
        val originX = input.readInt()
        val originY = input.readInt()
        val originZ = input.readInt()

        if (worldName.isBlank()) {
            plugin.logger.warning("Invalid snapshot $label: empty world name")
            return null
        }
        val world = plugin.server.getWorld(worldName)
        if (world == null) {
            plugin.logger.severe("World not found for snapshot $label: $worldName")
            return null
        }

        val palette = ArrayList<String>()
        var batch = ArrayList<Pair<Location, BlockSnapshot>>(batchSize)
        var count = 0

        while (true) {
            when (val record = input.readByte().toInt()) {
                RECORD_END -> break
                RECORD_PALETTE -> palette.add(input.readUTF())
                RECORD_BLOCK -> {
                    val relX = input.readInt()
                    val relY = input.readInt()
                    val relZ = input.readInt()
                    val paletteId = input.readInt()
                    if (paletteId !in palette.indices) {
                        plugin.logger.severe("Snapshot $label is corrupt: palette id $paletteId out of range")
                        return null
                    }
                    val tileEntity = if (input.readBoolean()) {
                        val length = input.readInt()
                        if (readTiles) {
                            val blob = ByteArray(length)
                            input.readFully(blob)
                            @Suppress("UNCHECKED_CAST")
                            ObjectInputStream(ByteArrayInputStream(blob)).use { it.readObject() as Map<String, Any> }
                        } else {
                            input.skipNBytes(length.toLong())
                            null
                        }
                    } else {
                        null
                    }
                    batch.add(
                        Location(
                            world,
                            (originX + relX).toDouble(),
                            (originY + relY).toDouble(),
                            (originZ + relZ).toDouble()
                        ) to BlockSnapshot(palette[paletteId], tileEntity)
                    )
                    count++
                    if (batch.size >= batchSize) {
                        onBatch(batch)
                        batch = ArrayList(batchSize)
                    }
                }
                else -> {
                    plugin.logger.severe("Snapshot $label is corrupt: unknown record type $record")
                    return null
                }
            }
        }

        if (batch.isNotEmpty()) onBatch(batch)
        return count
    }

    /** Reads a legacy Java-serialized [SnapshotData] snapshot (pre-v2 files). */
    private fun readLegacySnapshot(input: InputStream, label: String): Map<Location, BlockSnapshot>? {
        val snapshotData = ObjectInputStream(input).use { it.readObject() as SnapshotData }

        if (!validateSnapshot(snapshotData)) {
            plugin.logger.severe("Snapshot validation failed: $label")
            return null
        }

        val world = plugin.server.getWorld(snapshotData.worldName)
        if (world == null) {
            plugin.logger.severe("World not found for snapshot $label: ${snapshotData.worldName}")
            return null
        }

        // Convert relative coordinates back to absolute locations
        val blocks = HashMap<Location, BlockSnapshot>(snapshotData.blocks.size * 2)
        snapshotData.blocks.forEach { (relativePos, blockSnapshot) ->
            val location = Location(
                world,
                (snapshotData.originX + relativePos.first).toDouble(),
                (snapshotData.originY + relativePos.second).toDouble(),
                (snapshotData.originZ + relativePos.third).toDouble()
            )
            blocks[location] = blockSnapshot
        }
        return blocks
    }

    /**
     * Validates a legacy snapshot's data integrity.
     *
     * @param snapshotData The snapshot data to validate
     * @return True if valid, false otherwise
     */
    private fun validateSnapshot(snapshotData: SnapshotData): Boolean {
        // Check world name
        if (snapshotData.worldName.isBlank()) {
            plugin.logger.warning("Invalid snapshot: empty world name")
            return false
        }

        // Check blocks map
        if (snapshotData.blocks.isEmpty()) {
            plugin.logger.warning("Invalid snapshot: no blocks captured")
            return false
        }

        // Validate block data strings
        var invalidBlocks = 0
        snapshotData.blocks.forEach { (_, blockSnapshot) ->
            if (blockSnapshot.blockData.isBlank()) {
                invalidBlocks++
            }
        }

        if (invalidBlocks > 0) {
            plugin.logger.warning("Snapshot contains $invalidBlocks invalid block entries")
        }

        return true
    }

    /**
     * Gets the snapshot file for a chamber.
     *
     * @param chamberName The chamber name
     * @return The snapshot file, or null if not found
     */
    fun getSnapshotFile(chamberName: String): File? {
        val file = File(plugin.snapshotsDir, "$chamberName.dat")
        return if (file.exists()) file else null
    }

    /**
     * Deletes a snapshot file.
     *
     * @param chamberName The chamber name
     * @return True if deleted successfully
     */
    fun deleteSnapshot(chamberName: String): Boolean {
        val file = getSnapshotFile(chamberName) ?: return false
        return file.delete().also {
            if (it) {
                plugin.logger.info("Deleted snapshot: $chamberName.dat")
            }
        }
    }

    /**
     * Lists all available snapshot files.
     *
     * @return List of snapshot file names (without extension)
     */
    fun listSnapshots(): List<String> {
        return plugin.snapshotsDir.listFiles()
            ?.filter { it.extension == "dat" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Gets snapshot file information.
     *
     * @param chamberName The chamber name
     * @return Map of info, or null if not found
     */
    fun getSnapshotInfo(chamberName: String): Map<String, String>? {
        val file = getSnapshotFile(chamberName) ?: return null

        return mapOf(
            "name" to file.name,
            "size" to CompressionUtil.formatSize(file.length()),
            "lastModified" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(java.util.Date(file.lastModified()))
        )
    }
}

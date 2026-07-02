package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.utils.CompressionUtil
import io.github.darkstarworks.trialChamberPro.utils.NBTUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import java.io.File
import java.io.Serializable

/**
 * Manages chamber snapshots - creating, saving, loading, and validating.
 * This is the most critical component for chamber resets.
 *
 * Snapshots are stored as compressed files on disk (NOT in the database).
 */
class SnapshotManager(private val plugin: TrialChamberPro) {

    /**
     * Serializable wrapper for snapshot data.
     * Stores relative coordinates to reduce file size.
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

    /**
     * Creates a complete snapshot of a chamber.
     * Captures all blocks, tile entities, and NBT data within the chamber bounds.
     *
     * @param chamber The chamber to snapshot
     * @return The snapshot file created
     * @throws Exception if snapshot creation fails
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun createSnapshot(chamber: Chamber): File {
        plugin.logger.info("Creating snapshot for chamber: ${chamber.name}")

        val blocks = mutableMapOf<Triple<Int, Int, Int>, BlockSnapshot>()
        val world = chamber.getWorld() ?: throw IllegalStateException("World ${chamber.world} not found")

        var totalBlocks = 0
        var capturedBlocks = 0

        // Origin point for relative coordinates
        val originX = chamber.minX
        val originY = chamber.minY
        val originZ = chamber.minZ

        // v1.7.0: capture in X-slice batches, one scheduled task per batch, so snapshotting
        // a multi-million-block chamber (structure-bounds discovery / imported dungeons)
        // never stalls a tick. Small chambers still complete in one batch, as before.
        // Batches run sequentially (each awaited), so the shared map is never touched
        // concurrently. MUST run on the owning region thread to access block entities;
        // each batch is scheduled at its own slice's location (Folia region correctness).
        val sizeY = chamber.maxY - chamber.minY + 1
        val sizeZ = chamber.maxZ - chamber.minZ + 1
        val sliceBlocks = maxOf(1, sizeY * sizeZ)
        val slicesPerBatch = maxOf(1, ChamberManager.VOLUME_SCAN_BLOCKS_PER_BATCH / sliceBlocks)
        val midY = (chamber.minY + chamber.maxY) / 2.0
        val midZ = (chamber.minZ + chamber.maxZ) / 2.0

        var xStart = chamber.minX
        while (xStart <= chamber.maxX) {
            val xEnd = minOf(xStart + slicesPerBatch - 1, chamber.maxX)
            val batchStartX = xStart
            val batchCounts: Pair<Int, Int> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                plugin.scheduler.runAtLocation(Location(world, (batchStartX + xEnd) / 2.0, midY, midZ), Runnable {
                    try {
                        var batchTotal = 0
                        var batchCaptured = 0
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

                                    // Store with relative coordinates
                                    val relativePos = Triple(
                                        x - originX,
                                        y - originY,
                                        z - originZ
                                    )

                                    blocks[relativePos] = BlockSnapshot(blockData, tileEntity)
                                    batchCaptured++
                                }
                            }
                        }
                        continuation.resume(batchTotal to batchCaptured) {}
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }
            totalBlocks += batchCounts.first
            capturedBlocks += batchCounts.second
            xStart = xEnd + 1
        }

        plugin.logger.info("Captured $capturedBlocks blocks (${totalBlocks - capturedBlocks} air blocks skipped)")

        // File I/O operations on IO dispatcher
        return withContext(Dispatchers.IO) {
            // Create snapshot data
            val snapshotData = SnapshotData(
                worldName = chamber.world,
                originX = originX,
                originY = originY,
                originZ = originZ,
                blocks = blocks
            )

            // Serialize and compress
            val compressed = CompressionUtil.compressObject(snapshotData)

            // Save to file
            val file = File(plugin.snapshotsDir, "${chamber.name}.dat")
            file.writeBytes(compressed)

            val fileSize = CompressionUtil.formatSize(file.length())
            plugin.logger.info("Snapshot created: ${file.name} ($fileSize, $capturedBlocks blocks)")

            file
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
            val compressed = file.readBytes()
            val snapshotData = CompressionUtil.decompressObject<SnapshotData>(compressed)

            // Validate snapshot
            if (!validateSnapshot(snapshotData)) {
                plugin.logger.severe("Snapshot validation failed: ${file.name}")
                return@withContext null
            }

            val world = plugin.server.getWorld(snapshotData.worldName)
            if (world == null) {
                plugin.logger.severe("World not found for snapshot: ${snapshotData.worldName}")
                return@withContext null
            }

            // Convert relative coordinates back to absolute locations
            val blocks = mutableMapOf<Location, BlockSnapshot>()

            snapshotData.blocks.forEach { (relativePos, blockSnapshot) ->
                val location = Location(
                    world,
                    (snapshotData.originX + relativePos.first).toDouble(),
                    (snapshotData.originY + relativePos.second).toDouble(),
                    (snapshotData.originZ + relativePos.third).toDouble()
                )
                blocks[location] = blockSnapshot
            }

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
     * @param bytes Gzip-compressed serialized [SnapshotData].
     * @param contextLabel Short label included in log messages (e.g. the
     *                     chamber name) so admins can trace which override
     *                     produced a parse failure.
     */
    suspend fun loadSnapshotFromBytes(
        bytes: ByteArray,
        contextLabel: String = "<override>"
    ): Map<Location, BlockSnapshot>? = withContext(Dispatchers.IO) {
        try {
            val snapshotData = CompressionUtil.decompressObject<SnapshotData>(bytes)

            if (!validateSnapshot(snapshotData)) {
                plugin.logger.warning("Snapshot override validation failed for $contextLabel")
                return@withContext null
            }

            val world = plugin.server.getWorld(snapshotData.worldName)
            if (world == null) {
                plugin.logger.warning("Snapshot override references unknown world '${snapshotData.worldName}' for $contextLabel")
                return@withContext null
            }

            val blocks = mutableMapOf<Location, BlockSnapshot>()
            snapshotData.blocks.forEach { (relativePos, blockSnapshot) ->
                val location = Location(
                    world,
                    (snapshotData.originX + relativePos.first).toDouble(),
                    (snapshotData.originY + relativePos.second).toDouble(),
                    (snapshotData.originZ + relativePos.third).toDouble()
                )
                blocks[location] = blockSnapshot
            }

            plugin.logger.info("Loaded snapshot override for $contextLabel (${blocks.size} blocks)")
            blocks
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load snapshot override for $contextLabel: ${e.message}")
            null
        }
    }

    /**
     * Validates a snapshot's data integrity.
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

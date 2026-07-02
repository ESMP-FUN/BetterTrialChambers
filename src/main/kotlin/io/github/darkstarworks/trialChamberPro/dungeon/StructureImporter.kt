package io.github.darkstarworks.trialChamberPro.dungeon

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import io.github.darkstarworks.trialChamberPro.utils.NBTUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Material
import org.bukkit.block.BlockFace
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Imports vanilla `.nbt` structure templates (the format datapacks use for
 * custom jigsaw rooms) into TCP's dungeon engine as [RoomTemplate]s (v1.7.0).
 *
 * Sources: a loose `.nbt` file, a folder of them, or a whole datapack `.zip`
 * (entries matching `data/<namespace>/structure(s)/**/*.nbt`). Templates land
 * in `dungeon/rooms/` via [RoomTemplateManager.register] and behave exactly
 * like in-world captures: `minecraft:jigsaw` cells become [Connector]s
 * (horizontal fronts only) and are rewritten to wall.
 *
 * Documented limits (v1): only palette 0 is read; worldgen processor-list
 * randomizers are NOT applied (imported rooms are literal); jigsaw
 * pool/name/final_state NBT isn't exposed by the Bukkit API (same as capture);
 * tile-entity NBT from unplaced palette states is best-effort.
 */
class StructureImporter(private val plugin: TrialChamberPro) {

    data class ImportResult(val id: String, val blocks: Int, val connectors: Int, val tags: Set<String>)

    /**
     * Imports one `.nbt` stream as room template [id]. Must be called from a coroutine;
     * the Bukkit `StructureManager` load runs on the global scheduler thread to be safe.
     */
    suspend fun importStream(input: InputStream, id: String, tags: Set<String>, wallFallback: String): ImportResult {
        // Treat loadStructure as Bukkit API: hop to the global region/main thread.
        val structure = suspendCancellableCoroutine<org.bukkit.structure.Structure> { cont ->
            plugin.scheduler.runTask(Runnable {
                try {
                    cont.resume(plugin.server.structureManager.loadStructure(input)) {}
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(e))
                }
            })
        }

        val palette = structure.palettes.firstOrNull()
            ?: throw IllegalArgumentException("structure '$id' has no palette")
        if (structure.palettes.size > 1) {
            plugin.logger.info("Import '$id': ${structure.palettes.size} palettes found — using palette 0 only.")
        }

        val sizeX = structure.size.blockX
        val sizeY = structure.size.blockY
        val sizeZ = structure.size.blockZ

        val blocks = mutableMapOf<Triple<Int, Int, Int>, BlockSnapshot>()
        val materials = mutableMapOf<Triple<Int, Int, Int>, Material>()
        val jigsaws = mutableListOf<Pair<Triple<Int, Int, Int>, BlockFace?>>()
        var nbtFailures = 0

        // Pass 1: non-jigsaw blocks (air/void omitted, parity with capture).
        for (state in palette.blocks) {
            val pos = Triple(state.x, state.y, state.z)
            when (state.type) {
                Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.STRUCTURE_VOID -> {}
                Material.JIGSAW -> {
                    materials[pos] = Material.JIGSAW
                    val face = JigsawUtil.orientationToFace(
                        (state.blockData as? org.bukkit.block.data.type.Jigsaw)?.orientation
                    )
                    jigsaws += pos to face
                }
                else -> {
                    materials[pos] = state.type
                    // captureTileEntity on an UNPLACED palette BlockState may not behave like a
                    // placed one — degrade to null (block keeps its blockdata, loses tile NBT).
                    val nbt = try {
                        NBTUtil.captureTileEntity(state)
                    } catch (_: Exception) {
                        nbtFailures++
                        null
                    }
                    blocks[pos] = BlockSnapshot(state.blockData.asString, nbt)
                }
            }
        }
        if (nbtFailures > 0) {
            plugin.logger.warning("Import '$id': tile-entity NBT could not be read for $nbtFailures block(s) — imported without it.")
        }

        // Pass 2: jigsaw cells → connectors, cell rewritten to a sampled wall block.
        val connectors = mutableListOf<Connector>()
        for ((pos, face) in jigsaws) {
            val wall = sampleWallInMemory(pos, face, blocks, materials) ?: wallFallback
            blocks[pos] = BlockSnapshot(wall, null)
            if (face != null) {
                connectors += Connector(pos.first, pos.second, pos.third, face)
            } else {
                plugin.logger.warning("Import '$id': jigsaw at ${pos.first},${pos.second},${pos.third} has a vertical/unsupported orientation — treated as wall, no connector.")
            }
        }

        val template = RoomTemplate(id, sizeX, sizeY, sizeZ, blocks, connectors, tags)
        plugin.roomTemplateManager.register(template)
        plugin.logger.info("Imported room '$id': ${blocks.size} blocks, ${connectors.size} connector(s), tags=$tags")
        return ImportResult(id, blocks.size, connectors.size, tags)
    }

    /** Imports a loose `.nbt` file (id = sanitized filename). */
    suspend fun importFile(file: File, tags: Set<String>, wallFallback: String): ImportResult =
        file.inputStream().use { importStream(it, sanitizeId(file.nameWithoutExtension), tags, wallFallback) }

    /** Imports every `.nbt` in [folder] (non-recursive); parent-folder name is auto-tagged. */
    suspend fun importFolder(folder: File, tags: Set<String>, wallFallback: String): List<ImportResult> {
        val autoTag = sanitizeId(folder.name)
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("nbt", true) }?.sortedBy { it.name }
            ?: emptyList()
        return files.map { importFile(it, tags + autoTag, wallFallback) }
    }

    /**
     * Imports all room `.nbt` entries from a datapack [zip]
     * (`data/<ns>/structure(s)/**/*.nbt`). Ids are `<ns>_<subpath-flattened>`;
     * each entry is auto-tagged with its immediate parent folder (e.g. `spawner`).
     */
    suspend fun importZip(zip: File, tags: Set<String>, wallFallback: String): List<ImportResult> {
        val results = mutableListOf<ImportResult>()
        ZipFile(zip).use { zf ->
            val entries = zf.entries().asSequence()
                .filter { !it.isDirectory }
                .mapNotNull { e -> zipEntryInfo(e.name)?.let { e to it } }
                .sortedBy { it.first.name }
                .toList()
            for ((entry, info) in entries) {
                val entryTags = tags + setOfNotNull(info.autoTag)
                try {
                    zf.getInputStream(entry).use {
                        results += importStream(it, info.id, entryTags, wallFallback)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Import: failed on zip entry '${entry.name}': ${e.message}")
                }
            }
        }
        return results
    }

    /** In-memory neighbour sampling (import-time analogue of capture's block sampling). */
    private fun sampleWallInMemory(
        pos: Triple<Int, Int, Int>,
        facing: BlockFace?,
        blocks: Map<Triple<Int, Int, Int>, BlockSnapshot>,
        materials: Map<Triple<Int, Int, Int>, Material>,
    ): String? {
        for (f in JigsawUtil.sampleOrder(facing)) {
            val n = Triple(pos.first + f.modX, pos.second + f.modY, pos.third + f.modZ)
            val mat = materials[n] ?: continue
            if (mat != Material.JIGSAW && mat.isSolid) return blocks[n]?.blockData
        }
        return null
    }

    companion object {
        data class ZipRoomEntry(val id: String, val autoTag: String?)

        private val STRUCTURE_PATH = Regex("""^data/([^/]+)/structures?/(.+)\.nbt$""", RegexOption.IGNORE_CASE)

        /**
         * Parses a zip entry path into a room id + auto-tag, or null when the entry isn't a
         * datapack structure template. Pure — unit-tested.
         * `data/crazy_chambers/structure/spawner/small_1.nbt` →
         * id `crazy_chambers_spawner_small_1`, autoTag `spawner`.
         */
        fun zipEntryInfo(path: String): ZipRoomEntry? {
            val normalized = path.replace('\\', '/').removePrefix("/")
            val m = STRUCTURE_PATH.find(normalized) ?: return null
            val ns = m.groupValues[1]
            val sub = m.groupValues[2]
            val parts = sub.split('/')
            val autoTag = parts.dropLast(1).lastOrNull()?.let { sanitizeId(it) }?.takeIf { it.isNotEmpty() }
            val id = sanitizeId("${ns}_${parts.joinToString("_")}")
            return ZipRoomEntry(id, autoTag)
        }

        /** Lowercases and squashes anything outside `[a-z0-9_]` to `_` (filesystem/command safe). */
        fun sanitizeId(raw: String): String =
            raw.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
    }
}

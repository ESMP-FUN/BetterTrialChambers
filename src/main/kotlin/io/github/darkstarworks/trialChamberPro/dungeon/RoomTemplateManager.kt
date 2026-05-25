package io.github.darkstarworks.trialChamberPro.dungeon

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import io.github.darkstarworks.trialChamberPro.utils.CompressionUtil
import io.github.darkstarworks.trialChamberPro.utils.NBTUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.Orientation
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures, stores and loads [RoomTemplate]s. Capture scans a selection for
 * `minecraft:jigsaw` blocks, turning each into a [Connector] (cell + outward
 * facing from its orientation) and recording a sampled wall block in its place
 * so an unconnected door stays a wall. Air is skipped (parity with snapshots).
 */
class RoomTemplateManager(private val plugin: TrialChamberPro) {

    private val dir = File(plugin.dataFolder, "dungeon/rooms").apply { mkdirs() }
    private val cache = ConcurrentHashMap<String, RoomTemplate>()

    suspend fun capture(
        world: World,
        c1: Location,
        c2: Location,
        id: String,
        tags: Set<String>,
        wallFallback: String,
    ): RoomTemplate {
        val minX = minOf(c1.blockX, c2.blockX); val maxX = maxOf(c1.blockX, c2.blockX)
        val minY = minOf(c1.blockY, c2.blockY); val maxY = maxOf(c1.blockY, c2.blockY)
        val minZ = minOf(c1.blockZ, c2.blockZ); val maxZ = maxOf(c1.blockZ, c2.blockZ)

        val blocks = mutableMapOf<Triple<Int, Int, Int>, BlockSnapshot>()
        val connectors = mutableListOf<Connector>()

        suspendCancellableCoroutine { cont ->
            plugin.scheduler.runAtLocation(Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()), Runnable {
                try {
                    for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        val rel = Triple(x - minX, y - minY, z - minZ)
                        when {
                            block.type == Material.JIGSAW -> {
                                val face = orientationToFace((block.blockData as? org.bukkit.block.data.type.Jigsaw)?.orientation)
                                val cap = sampleWall(block, face, wallFallback)
                                blocks[rel] = BlockSnapshot(cap, null)
                                if (face != null) {
                                    connectors.add(Connector(rel.first, rel.second, rel.third, face))
                                } else {
                                    plugin.logger.warning("Room '$id': jigsaw at $x,$y,$z has a vertical/unsupported orientation — treated as wall, no connector.")
                                }
                            }
                            block.type != Material.AIR ->
                                blocks[rel] = BlockSnapshot(block.blockData.asString, NBTUtil.captureTileEntity(block.state))
                        }
                    }
                    cont.resume(Unit) {}
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(e))
                }
            })
        }

        val template = RoomTemplate(id, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, blocks, connectors, tags)
        save(template)
        cache[id] = template
        plugin.logger.info("Captured room '$id': ${blocks.size} blocks, ${connectors.size} connectors, tags=$tags")
        return template
    }

    fun load(id: String): RoomTemplate? {
        cache[id]?.let { return it }
        val file = File(dir, "$id.dat")
        if (!file.exists()) return null
        return try {
            CompressionUtil.decompressObject<RoomTemplate>(file.readBytes()).also { cache[id] = it }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load room template '$id': ${e.message}")
            null
        }
    }

    fun loadAll(): List<RoomTemplate> =
        dir.listFiles { f -> f.extension == "dat" }?.mapNotNull { load(it.nameWithoutExtension) } ?: emptyList()

    fun list(): List<String> =
        dir.listFiles { f -> f.extension == "dat" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

    fun delete(id: String): Boolean {
        cache.remove(id)
        return File(dir, "$id.dat").delete()
    }

    private suspend fun save(template: RoomTemplate) = withContext(Dispatchers.IO) {
        File(dir, "${template.id}.dat").writeBytes(CompressionUtil.compressObject(template))
    }

    /** Sample a solid neighbour to fill a jigsaw cell so unconnected doors stay walls. */
    private fun sampleWall(block: org.bukkit.block.Block, facing: BlockFace?, fallback: String): String {
        val faces = buildList {
            if (facing != null) {
                addAll(perpendicular(facing))
                add(facing.oppositeFace)
            }
            add(BlockFace.UP); add(BlockFace.DOWN); add(BlockFace.NORTH); add(BlockFace.SOUTH); add(BlockFace.EAST); add(BlockFace.WEST)
        }
        for (f in faces) {
            val n = block.getRelative(f)
            if (n.type != Material.AIR && n.type != Material.JIGSAW && n.type.isSolid) return n.blockData.asString
        }
        return fallback
    }

    private fun perpendicular(facing: BlockFace): List<BlockFace> = when (facing) {
        BlockFace.EAST, BlockFace.WEST -> listOf(BlockFace.NORTH, BlockFace.SOUTH)
        else -> listOf(BlockFace.EAST, BlockFace.WEST)
    }

    private fun orientationToFace(o: Orientation?): BlockFace? = when (o) {
        Orientation.NORTH_UP -> BlockFace.NORTH
        Orientation.EAST_UP -> BlockFace.EAST
        Orientation.SOUTH_UP -> BlockFace.SOUTH
        Orientation.WEST_UP -> BlockFace.WEST
        else -> null // UP_*/DOWN_* = vertical front, unsupported in v1
    }
}

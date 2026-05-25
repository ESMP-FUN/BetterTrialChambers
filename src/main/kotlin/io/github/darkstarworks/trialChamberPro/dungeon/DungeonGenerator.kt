package io.github.darkstarworks.trialChamberPro.dungeon

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import io.github.darkstarworks.trialChamberPro.utils.BlockRestorer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace

/**
 * Assembles a dungeon from room templates and writes it to the world: runs the
 * pure [DungeonStitcher], places each room (rotated coords + rotated blockdata)
 * through the Folia-safe [BlockRestorer], carves the joined doorways open, then
 * snapshots the whole thing and registers it as a normal TCP chamber so every
 * downstream system (reset, scaling, themes) applies unchanged.
 */
class DungeonGenerator(
    private val plugin: TrialChamberPro,
    private val templates: RoomTemplateManager,
) {

    private val stitcher = DungeonStitcher()

    /** Generate + register a dungeon chamber named [name]. Returns true on success. */
    suspend fun generate(
        world: World,
        origin: Location,
        seed: Long,
        name: String,
        params: StitchParams,
        doorWidth: Int,
        doorHeight: Int,
    ): Boolean {
        if (plugin.chamberManager.getChamber(name) != null) {
            plugin.logger.warning("Dungeon '$name' already exists.")
            return false
        }
        val all = templates.loadAll()
        if (all.isEmpty()) {
            plugin.logger.warning("No room templates found — capture some first.")
            return false
        }
        val byId = all.associateBy { it.id }
        val result = stitcher.assembleWithRetry(seed, all.map { it.shape() }, params)
        if (result.placements.isEmpty()) {
            plugin.logger.warning("Stitcher produced no layout for '$name' (check start/required tags).")
            return false
        }

        val combined = HashMap<Location, BlockSnapshot>()
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE

        for (p in result.placements) {
            val tpl = byId[p.roomId] ?: continue
            for ((rel, snap) in tpl.blocks) {
                val (rx, rz) = p.rotation.rotate(rel.first, rel.third, tpl.sizeX, tpl.sizeZ)
                val wx = p.offsetX + rx
                val wy = p.offsetY + rel.second
                val wz = p.offsetZ + rz
                val rotated = BlockDataRotator.rotate(snap.blockData, p.rotation)
                combined[Location(world, wx.toDouble(), wy.toDouble(), wz.toDouble())] =
                    BlockSnapshot(rotated, snap.tileEntity)
            }
            minX = minOf(minX, p.offsetX); minY = minOf(minY, p.offsetY); minZ = minOf(minZ, p.offsetZ)
            maxX = maxOf(maxX, p.offsetX + p.rotation.sizeX(tpl.sizeX, tpl.sizeZ) - 1)
            maxY = maxOf(maxY, p.offsetY + tpl.sizeY - 1)
            maxZ = maxOf(maxZ, p.offsetZ + p.rotation.sizeZ(tpl.sizeX, tpl.sizeZ) - 1)
        }

        // Place all room blocks (region-batched, Folia-safe), then carve joins open.
        BlockRestorer(plugin).restoreBlocks(combined)
        carve(world, result.doorways, doorWidth, doorHeight)

        // Register as a chamber + snapshot it as the reset baseline.
        val chamber = plugin.chamberManager.createChamber(
            name,
            Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
            Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
        ) ?: run {
            plugin.logger.warning("Failed to register chamber '$name'.")
            return false
        }
        val file = plugin.snapshotManager.createSnapshot(chamber)
        plugin.chamberManager.setSnapshotFile(name, file.absolutePath)
        plugin.logger.info(
            "Dungeon '$name' generated: ${result.placements.size} rooms, " +
                "bounds [$minX,$minY,$minZ]..[$maxX,$maxY,$maxZ]"
        )
        return true
    }

    /**
     * Carve a [width]×[height] opening at each joined doorway. The connector cell
     * is the bottom-centre of the doorway, so we go up by [height] and ±half the
     * width along the wall plane (perpendicular to the facing).
     */
    private fun carve(world: World, doorways: List<Doorway>, width: Int, height: Int) {
        val half = (width - 1) / 2
        for (d in doorways) {
            for (h in 0 until height) {
                for (w in -half..(width - 1 - half)) {
                    val dx: Int
                    val dz: Int
                    if (d.facing == BlockFace.EAST || d.facing == BlockFace.WEST) {
                        dx = 0; dz = w
                    } else {
                        dx = w; dz = 0
                    }
                    val loc = Location(world, (d.x + dx).toDouble(), (d.y + h).toDouble(), (d.z + dz).toDouble())
                    plugin.scheduler.runAtLocation(loc, Runnable { loc.block.setType(Material.AIR, false) })
                }
            }
        }
    }
}

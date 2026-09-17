package com.esmpfun.bettertrialchambers.dungeon

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import com.esmpfun.bettertrialchambers.utils.BlockRestorer
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
    private val plugin: BetterTrialChambers,
    private val templates: RoomTemplateManager,
) {

    private val stitcher = DungeonStitcher()

    private companion object {
        /**
         * How a trial spawner appears at the start of a saved block description,
         * with or without a trailing `[state=...]` part. Matched as text because
         * the check runs before the blocks exist in the world.
         */
        const val TRIAL_SPAWNER_ID = "minecraft:trial_spawner"
    }

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
            plugin.logger.warning("No room templates found, capture some first.")
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

        // Open the joined doorways in the blocks themselves before any of them are
        // placed. Carving afterwards wrote the openings with their own scheduled
        // tasks that nothing waited for, so the snapshot taken below could be of a
        // dungeon whose doorways were still walled up, and the first reset would
        // seal them. One pass now places the rooms with their doorways already open.
        carveInto(combined, world, result.doorways, doorWidth, doorHeight)

        // Place all room blocks (region-batched, Folia-safe). Suspends until done.
        BlockRestorer(plugin).restoreBlocks(combined)

        // Tell the spawner index about the trial spawners we just placed.
        // The index normally learns about spawners by watching players place
        // them, and nothing here does: the blocks are written straight into the
        // world. Without this a freshly built dungeon had no wave tracking and
        // no boss bars at all until something happened to reload its chunks.
        // The blocks are already in hand, so no scan of the world is needed.
        combined.forEach { (loc, snapshot) ->
            if (snapshot.blockData.startsWith(TRIAL_SPAWNER_ID)) {
                plugin.trialSpawnerIndex.add(world, loc.blockX, loc.blockY, loc.blockZ)
            }
        }

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
     * Turns each joined doorway into a [width]x[height] opening in [blocks], the
     * set of blocks about to be placed. The connector cell is the bottom-centre
     * of the doorway, so this goes up by [height] and half the width either way
     * along the wall (perpendicular to the facing).
     */
    private fun carveInto(
        blocks: MutableMap<Location, BlockSnapshot>,
        world: World,
        doorways: List<Doorway>,
        width: Int,
        height: Int,
    ) {
        val half = (width - 1) / 2
        val air = BlockSnapshot(Material.AIR.key.toString())
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
                    blocks[loc] = air
                }
            }
        }
    }
}

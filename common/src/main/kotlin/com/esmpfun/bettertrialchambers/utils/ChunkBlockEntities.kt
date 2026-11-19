package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.integrations.MetricsService
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.TileState

/**
 * The only way BTC walks a chunk's block entities.
 *
 * `CraftChunk.getTileEntities` walks the chunk's block-entity map with no copy and calls `getState`
 * on each entry mid-walk. `getState` goes through `LevelChunk.getBlockEntity`, which removes an
 * entry flagged as removed from that same map, so the walk can corrupt its own fastutil iterator
 * (an NPE or index error). The predicate only reads the block type, so it collects the matches and
 * returns false, and states are built after the walk has finished.
 */
object ChunkBlockEntities {

    val TRIAL_SPAWNER = setOf(Material.TRIAL_SPAWNER)

    /** Block entities in [chunk] whose type is in [types], or null if the walk was interrupted. */
    fun find(
        plugin: BetterTrialChambers,
        chunk: Chunk,
        types: Set<Material>,
        operation: String,
        useSnapshot: Boolean = false,
    ): List<BlockState>? {
        val matches = mutableListOf<Block>()
        try {
            chunk.getTileEntities({ if (it.type in types) matches += it; false }, false)
        } catch (e: Exception) {
            plugin.logger.warning(
                "Could not check chunk ${chunk.x}, ${chunk.z} in ${chunk.world.name} ($operation), so it" +
                    " was skipped this time and will be checked again later. Details: ${e.message}"
            )
            MetricsService.reportHandled(e, operation)
            return null
        }
        // A block entity removed between the walk and here comes back as a plain BlockState.
        return matches.mapNotNull { block -> block.getState(useSnapshot).takeIf { it is TileState } }
    }
}

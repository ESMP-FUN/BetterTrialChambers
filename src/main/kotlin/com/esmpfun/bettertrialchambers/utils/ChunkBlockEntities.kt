package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.integrations.MetricsService
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.block.BlockState

/**
 * The only way BTC walks a chunk's block entities.
 *
 * The no-arg `Chunk.tileEntities` builds a snapshot `BlockState` for every chest, sign and hopper
 * in the chunk; the filtering overload tests the cheap `Block` first. Either way the server walks
 * its own block-entity map with no copy, so another plugin writing to it from a second thread
 * breaks the walk mid-way (an NPE or index error from inside fastutil).
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
    ): Collection<BlockState>? = try {
        chunk.getTileEntities({ it.type in types }, useSnapshot)
    } catch (e: Exception) {
        plugin.logger.warning(
            "Could not check chunk ${chunk.x}, ${chunk.z} in ${chunk.world.name} ($operation), so it" +
                " was skipped this time. This is usually another plugin changing blocks from a second" +
                " thread. Details: ${e.message}"
        )
        MetricsService.reportHandled(e, operation)
        null
    }
}

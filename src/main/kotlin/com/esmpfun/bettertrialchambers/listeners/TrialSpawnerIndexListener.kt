package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.integrations.MetricsService
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * Maintains [com.esmpfun.bettertrialchambers.managers.TrialSpawnerIndex]
 * in lock-step with the world.
 *
 * **Chunk load**, re-scan the chunk's tile entities for `TRIAL_SPAWNER` and
 * replace the index's entries for that chunk. Catches spawners that were broken
 * while the chunk was unloaded. The filtering overload is deliberate: the no-arg
 * `tileEntities` builds a snapshot `BlockState` for every chest, sign and hopper
 * in the chunk, on every chunk load, to find at most a handful of spawners.
 *
 * **Block break / place**, keep the index live as players modify the world.
 * MONITOR priority + `ignoreCancelled = true` so cancelled events (by
 * `ProtectionListener` or third-party plugins) don't desync the index.
 *
 * **Chunk unload / world unload**, no-op / drop world index. We keep
 * unloaded-chunk entries in memory because (a) the spawner-wave proximity
 * query only inspects chunks near the player, which are loaded by definition,
 * and (b) on reload the chunk's scan-pass replaces stale entries anyway. The
 * memory cost is tiny (one packed `Long` per spawner).
 */
class TrialSpawnerIndexListener(private val plugin: BetterTrialChambers) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val spawners = try {
            chunk.getTileEntities({ it.type == Material.TRIAL_SPAWNER }, false).map { it.block }
        } catch (e: Exception) {
            // The server walks its own block-entity map here with no copy, so another plugin
            // writing to it from a second thread breaks the walk mid-way. Skipping one chunk
            // only delays it: any break or place in it re-indexes, as does the next load.
            plugin.logger.warning(
                "Could not check chunk ${chunk.x}, ${chunk.z} in ${chunk.world.name} for trial" +
                    " spawners, so they may be missing from this session's list until the chunk" +
                    " loads again. This is usually another plugin changing blocks from a second" +
                    " thread. Details: ${e.message}"
            )
            MetricsService.reportHandled(e, "spawner-index-chunk-load")
            return
        }
        plugin.trialSpawnerIndex.rescanChunk(chunk.world, chunk.x, chunk.z, spawners)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.TRIAL_SPAWNER) return
        val b = event.block
        plugin.trialSpawnerIndex.remove(b.world, b.x, b.y, b.z)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.blockPlaced.type != Material.TRIAL_SPAWNER) return
        val b = event.blockPlaced
        plugin.trialSpawnerIndex.add(b.world, b.x, b.y, b.z)
    }

    /**
     * Optional: a chunk going unloaded doesn't invalidate our cached entries
     * (we'll rescan on reload), but if memory pressure becomes a concern we
     * could evict here. Currently a no-op, see class doc.
     */
    @Suppress("unused")
    fun onChunkUnload(event: ChunkUnloadEvent) {
        // Intentionally not @EventHandler-annotated. Keep cached.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldUnload(event: WorldUnloadEvent) {
        plugin.trialSpawnerIndex.forgetWorld(event.world.uid)
    }
}

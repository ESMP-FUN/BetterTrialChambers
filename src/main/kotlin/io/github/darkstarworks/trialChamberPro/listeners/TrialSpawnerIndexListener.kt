package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
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
 * Maintains [io.github.darkstarworks.trialChamberPro.managers.TrialSpawnerIndex]
 * in lock-step with the world.
 *
 * **Chunk load** — re-scan the chunk's tile entities for `TRIAL_SPAWNER` and
 * replace the index's entries for that chunk. Cheap (no block iteration; uses
 * the cached tile-entity list). Catches spawners that were broken while the
 * chunk was unloaded.
 *
 * **Block break / place** — keep the index live as players modify the world.
 * MONITOR priority + `ignoreCancelled = true` so cancelled events (by
 * `ProtectionListener` or third-party plugins) don't desync the index.
 *
 * **Chunk unload / world unload** — no-op / drop world index. We keep
 * unloaded-chunk entries in memory because (a) the spawner-wave proximity
 * query only inspects chunks near the player, which are loaded by definition,
 * and (b) on reload the chunk's scan-pass replaces stale entries anyway. The
 * memory cost is tiny (one packed `Long` per spawner).
 */
class TrialSpawnerIndexListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val spawners = chunk.tileEntities.filter { it.block.type == Material.TRIAL_SPAWNER }.map { it.block }
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
     * could evict here. Currently a no-op — see class doc.
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

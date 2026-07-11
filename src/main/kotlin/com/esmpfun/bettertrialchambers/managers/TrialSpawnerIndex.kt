package com.esmpfun.bettertrialchambers.managers

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-world spatial index of trial-spawner block positions.
 *
 * Replaces the O(r³) block scan in `SpawnerWaveListener.onPlayerMove`: instead
 * of poking every block in a 41×41×41 cube via `world.getBlockAt(...).type`,
 * we keep a chunk-keyed cache of known trial-spawner positions and answer
 * proximity queries by iterating the (few) chunks overlapping the query
 * volume and distance-checking the (typically zero or one) spawner positions
 * inside each one.
 *
 * Populated by [com.esmpfun.bettertrialchambers.listeners.TrialSpawnerIndexListener]:
 *
 *   - `ChunkLoadEvent` → `rescanChunk(chunk's tile entities filtered to TRIAL_SPAWNER)`.
 *     Cheap (no block iteration; uses the already-cached tile-entity list).
 *   - `BlockBreakEvent` for `TRIAL_SPAWNER` → `remove(pos)`.
 *   - `BlockPlaceEvent` for `TRIAL_SPAWNER` → `add(pos)`.
 *
 * Plus a startup sweep over every loaded Overworld chunk via `seedFromLoadedChunks`
 * — symmetric to `ChamberDiscoveryManager`'s startup sweep — so we don't have to
 * wait for the first `ChunkLoadEvent` after enable to populate seeded chambers.
 *
 * The index is purely transient cache; correctness comes from the listener
 * keeping it in lock-step with the world. The DB `spawners` table is **not**
 * consulted because we only care about *currently-present* spawners (a chamber
 * scanned years ago may have had a spawner mined since), and chunk-load
 * re-scanning catches truth on each chunk reload regardless of unload races.
 *
 * Thread safety: every operation is `ConcurrentHashMap`-backed; queries can run
 * from any thread. Mutations from listeners happen on the chunk's region thread
 * (Folia-safe).
 */
class TrialSpawnerIndex {

    /** Per-world index. Keyed by `World.uid` so `World` instances themselves can be GC'd. */
    private val worlds = ConcurrentHashMap<UUID, WorldIndex>()

    /** Single world's chunk-keyed spawner positions. */
    private class WorldIndex {
        /** Chunk key (packed chunkX|chunkZ) → set of packed block positions. */
        val chunks = ConcurrentHashMap<Long, MutableSet<Long>>()
    }

    /**
     * Add a known trial-spawner position. Called from `BlockPlaceEvent` and from
     * chunk-load rescans.
     */
    fun add(world: World, x: Int, y: Int, z: Int) {
        val wi = worlds.computeIfAbsent(world.uid) { WorldIndex() }
        val chunkKey = packChunk(x shr 4, z shr 4)
        val set = wi.chunks.computeIfAbsent(chunkKey) {
            // Synchronized-set so the rescan replace-pass and add/remove from
            // other events don't race on iteration. Reads (in `query`) snapshot
            // into a local list before distance-checking, so no `synchronized`
            // block is needed at the call site.
            java.util.Collections.synchronizedSet(HashSet())
        }
        set.add(packBlock(x, y, z))
    }

    /** Remove a known trial-spawner position. Called from `BlockBreakEvent`. */
    fun remove(world: World, x: Int, y: Int, z: Int) {
        val wi = worlds[world.uid] ?: return
        val chunkKey = packChunk(x shr 4, z shr 4)
        val set = wi.chunks[chunkKey] ?: return
        set.remove(packBlock(x, y, z))
        if (set.isEmpty()) wi.chunks.remove(chunkKey)
    }

    /**
     * Replace the entire spawner set for a chunk with `positions`. Called from
     * `ChunkLoadEvent`: the chunk's tile-entity list is the authoritative truth
     * for "trial spawners currently in this chunk," so we wipe stale entries
     * (e.g. spawners broken while the chunk was unloaded) in one pass.
     */
    fun rescanChunk(world: World, chunkX: Int, chunkZ: Int, positions: Collection<Block>) {
        val wi = worlds.computeIfAbsent(world.uid) { WorldIndex() }
        val chunkKey = packChunk(chunkX, chunkZ)
        if (positions.isEmpty()) {
            wi.chunks.remove(chunkKey)
            return
        }
        val packed = HashSet<Long>(positions.size * 2)
        for (b in positions) packed.add(packBlock(b.x, b.y, b.z))
        wi.chunks[chunkKey] = java.util.Collections.synchronizedSet(packed)
    }

    /**
     * Find all trial-spawner positions within `radius` blocks of `center`.
     *
     * Iterates the chunks overlapping the query AABB (at radius=20 that's a
     * 3×3 grid in the worst case), snapshots each chunk's spawner set into a
     * local list, and filters by spherical distance. Returns block-centered
     * `Location`s identical to what the old `findNearbyTrialSpawners` block
     * scan produced.
     */
    fun query(center: Location, radius: Int): List<Location> {
        val world = center.world ?: return emptyList()
        val wi = worlds[world.uid] ?: return emptyList()
        if (wi.chunks.isEmpty()) return emptyList()

        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        val r2 = radius * radius

        val minChunkX = (cx - radius) shr 4
        val maxChunkX = (cx + radius) shr 4
        val minChunkZ = (cz - radius) shr 4
        val maxChunkZ = (cz + radius) shr 4

        val out = mutableListOf<Location>()
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val set = wi.chunks[packChunk(chunkX, chunkZ)] ?: continue
                // Snapshot under the set's monitor — the synchronized wrapper
                // requires explicit `synchronized` for safe iteration.
                val snapshot: LongArray
                synchronized(set) {
                    snapshot = LongArray(set.size).also {
                        var i = 0
                        for (p in set) { it[i++] = p }
                    }
                }
                for (packed in snapshot) {
                    val x = unpackX(packed)
                    val y = unpackY(packed)
                    val z = unpackZ(packed)
                    val dx = x - cx
                    val dy = y - cy
                    val dz = z - cz
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        out.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
                    }
                }
            }
        }
        return out
    }

    /**
     * Drop every entry for a world — used when a world unloads. Keeping stale
     * `World` UUIDs around is harmless (queries are scoped by world), but the
     * `Set<Long>` memory is worth reclaiming.
     */
    fun forgetWorld(worldId: UUID) {
        worlds.remove(worldId)
    }

    /**
     * Startup-sweep seed: iterate every currently-loaded chunk in `world` and
     * scan its tile entities for `TRIAL_SPAWNER`. Symmetric to the discovery
     * manager's startup sweep so chambers already resident at enable time get
     * indexed before any player movement.
     *
     * Must be called on the main thread (or the world's region thread on Folia).
     */
    fun seedFromLoadedChunks(world: World): Int {
        var count = 0
        for (chunk in world.loadedChunks) {
            val spawners = chunk.tileEntities.filter { it.block.type == Material.TRIAL_SPAWNER }.map { it.block }
            if (spawners.isNotEmpty()) {
                rescanChunk(world, chunk.x, chunk.z, spawners)
                count += spawners.size
            }
        }
        return count
    }

    /** Diagnostic: total spawner positions tracked across all worlds. */
    fun size(): Int = worlds.values.sumOf { wi -> wi.chunks.values.sumOf { it.size } }

    // -------- packing helpers --------
    // Chunk key: 32-bit X in high half, 32-bit Z in low half — same shape as
    // Mojang's `ChunkPos.asLong`.
    private fun packChunk(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)

    // Block position: 26 bits X | 12 bits Y | 26 bits Z. Standard Minecraft
    // world-coordinate ranges fit (X/Z ±33M, Y -2048..2047). We don't expect
    // chambers anywhere near those extremes.
    private fun packBlock(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFFL) shl 38) or
        ((z.toLong() and 0x3FFFFFFL) shl 12) or
        (y.toLong() and 0xFFFL)

    private fun unpackX(packed: Long): Int {
        val raw = ((packed shr 38) and 0x3FFFFFFL).toInt()
        return if (raw and 0x2000000 != 0) raw or 0x3FFFFFF.inv() else raw  // sign-extend 26-bit
    }
    private fun unpackY(packed: Long): Int {
        val raw = (packed and 0xFFFL).toInt()
        return if (raw and 0x800 != 0) raw or 0xFFF.inv() else raw  // sign-extend 12-bit
    }
    private fun unpackZ(packed: Long): Int {
        val raw = ((packed shr 12) and 0x3FFFFFFL).toInt()
        return if (raw and 0x2000000 != 0) raw or 0x3FFFFFF.inv() else raw
    }
}

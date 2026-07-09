package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Auto-discovery of naturally-generated Trial Chambers.
 *
 * Flow: a seed (VAULT or TRIAL_SPAWNER location) is registered via [seed].
 * After a short delay (to allow adjacent chunks to load), a BFS flood-fill
 * over chamber structural blocks computes a bounding box. If all touched
 * chunks are loaded and the AABB passes sanity checks, the chamber is
 * registered via [ChamberManager.createChamber] using an auto-generated name.
 *
 * If the BFS frontier wants to enter unloaded chunks, the seed is re-queued
 * for retry a limited number of times before giving up.
 */
class ChamberDiscoveryManager(private val plugin: TrialChamberPro) {

    private data class PendingSeed(
        val world: World,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
        val attemptsRemaining: Int,
        val method: io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method
    ) {
        fun toLocation(): Location = Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5)
    }

    // Region-bucket -> expiry epoch millis. Debounces further seeds from the same area.
    private val recentlyProcessed = ConcurrentHashMap<String, Long>()

    // Region-buckets currently being scanned, prevents duplicate concurrent BFS.
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    // Serializes the registration step so two near-simultaneous discoveries (e.g. startup
    // sweep) can't both read "no existing nearby chamber" and both register before either
    // commits. Without this, two adjacent vanilla chambers (~500 blocks apart) would each
    // create a separate registration instead of merging.
    private val registrationMutex = Mutex()

    /**
     * Register a seed block location to kick off discovery.
     * Safe to call from any thread.
     */
    fun seed(
        world: World,
        x: Int, y: Int, z: Int,
        method: io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method =
            io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method.CHUNK_LOAD
    ) {
        if (!plugin.config.getBoolean("discovery.enabled", false)) return
        if (world.environment != World.Environment.NORMAL) return
        if (plugin.isWorldExcluded(world)) return

        val key = regionKey(world, x, z)
        val now = System.currentTimeMillis()
        val expiry = recentlyProcessed[key]
        if (expiry != null && now < expiry) return
        if (!inFlight.add(key)) return

        val maxAttempts = 6
        val loc = Location(world, x + 0.5, y + 0.5, z + 0.5)
        plugin.scheduler.runAtLocationLater(loc, Runnable {
            attemptDiscovery(PendingSeed(world, x, y, z, maxAttempts, method), key)
        }, 100L) // initial 5-second delay so nearby chunks settle
    }

    /**
     * Sweeps all currently-loaded chunks in every loaded Overworld, seeding
     * discovery for any VAULT / TRIAL_SPAWNER tile-entities found.
     *
     * Covers chunks that were already loaded before the listener was registered
     * (e.g. spawn regions on fresh server start). Chunks that become loaded
     * later are handled by [ChamberDiscoveryListener] via ChunkLoadEvent.
     *
     * Safe to call on startup or from a command. Per-region debounce and the
     * existing-chamber check prevent duplicate registrations.
     */
    fun runStartupSweep() {
        if (!plugin.config.getBoolean("discovery.enabled", false)) return

        val worlds = plugin.server.worlds
        var scheduled = 0
        for (world in worlds) {
            if (world.environment != World.Environment.NORMAL) continue
            if (plugin.isWorldExcluded(world)) continue
            val chunks = world.loadedChunks
            for (chunk in chunks) {
                val centerLoc = Location(
                    world,
                    (chunk.x shl 4) + 8.0,
                    world.minHeight.toDouble(),
                    (chunk.z shl 4) + 8.0
                )
                plugin.scheduler.runAtLocation(centerLoc, Runnable {
                    try {
                        sweepChunk(world, chunk)
                    } catch (e: Exception) {
                        plugin.logger.warning("[Discovery] Startup sweep failed at chunk ${chunk.x},${chunk.z}: ${e.message}")
                    }
                })
                scheduled++
            }
        }
        if (scheduled > 0) {
            plugin.logger.info("[Discovery] Startup sweep queued across $scheduled loaded chunk(s)")
        }
    }

    private fun sweepChunk(world: World, chunk: org.bukkit.Chunk) {
        val tileEntities = chunk.tileEntities
        if (tileEntities.isEmpty()) return
        for (state in tileEntities) {
            if (!isSeedBlock(state.type)) continue
            val loc = Location(world, state.x + 0.5, state.y + 0.5, state.z + 0.5)
            if (plugin.chamberManager.getCachedChamberAt(loc) != null) continue
            seed(world, state.x, state.y, state.z,
                io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method.STARTUP_SWEEP)
            return // one seed per chunk is enough
        }
    }

    private fun attemptDiscovery(seed: PendingSeed, key: String) {
        // Runs on region thread owning the seed location.
        try {
            // v1.7.0: prefer the game's own structure bounds when the seed sits inside a
            // generated minecraft:trial_chambers structure. Exact, immune to the BFS's
            // structural-block predicate — which datapack-enlarged chambers (custom .nbt
            // rooms overriding the vanilla start_pool) routinely break. Player-built
            // chambers aren't generated structures, so they fall through to the BFS.
            if (plugin.config.getBoolean("discovery.use-structure-bounds", true)) {
                val structResult = structureBoundsResult(seed.world, seed.blockX, seed.blockY, seed.blockZ)
                if (structResult != null) {
                    val maxVolume = plugin.config.getLong("discovery.structure-max-volume", 15_000_000L)
                    val volume = structResult.sizeX.toLong() * structResult.sizeY.toLong() * structResult.sizeZ.toLong()
                    if (maxVolume >= 0 && volume > maxVolume) {
                        finalizeFailed(key, "structure bounds exceed structure-max-volume ($volume > $maxVolume)")
                        return
                    }
                    plugin.logger.info("[Discovery] Using generated-structure bounds at ${seed.blockX},${seed.blockY},${seed.blockZ}: ${structResult.sizeX}x${structResult.sizeY}x${structResult.sizeZ}")
                    registerChamber(seed.world, structResult, key, seed.method, boundsConfirmed = true)
                    return
                }
            }

            val result = try {
                bfsCompute(seed.world, seed.blockX, seed.blockY, seed.blockZ)
            } catch (e: Exception) {
                plugin.logger.warning("[Discovery] BFS failed at ${seed.blockX},${seed.blockY},${seed.blockZ}: ${e.message}")
                null
            }

            if (result == null) {
                finalizeFailed(key, "BFS error")
                return
            }

            if (result.hitUnloadedChunks && seed.attemptsRemaining > 1) {
                // Defer retry - adjacent chunks may still be loading
                val retryDelay = plugin.config.getLong("discovery.pending-retry-seconds", 30L).coerceAtLeast(5L)
                plugin.scheduler.runAtLocationLater(seed.toLocation(), Runnable {
                    attemptDiscovery(seed.copy(attemptsRemaining = seed.attemptsRemaining - 1), key)
                }, retryDelay * 20L)
                return
            }

            if (!validateResult(result)) {
                finalizeFailed(key, "AABB failed validation (vaults=${result.vaultCount}, spawners=${result.spawnerCount}, size=${result.sizeX}x${result.sizeY}x${result.sizeZ}, centerY=${result.centerY})")
                return
            }

            registerChamber(seed.world, result, key, seed.method)
        } catch (e: Exception) {
            plugin.logger.severe("[Discovery] Unexpected error during discovery: ${e.message}")
            e.printStackTrace()
            finalizeFailed(key, "exception")
        }
    }

    /**
     * The exact bounds of the generated `minecraft:trial_chambers` structure containing the
     * seed block, or null when the seed isn't inside one (player-built) or the API fails.
     * Datapacks that enlarge chambers override the same structure key, so this matches
     * those too. Must run on the region thread owning the seed.
     */
    private fun structureBoundsResult(world: World, x: Int, y: Int, z: Int): BfsResult? = try {
        world.getStructures(x shr 4, z shr 4, org.bukkit.generator.structure.Structure.TRIAL_CHAMBERS)
            // Paper passes vanilla's BLOCK-INCLUSIVE min/max through unchanged
            // (CraftGeneratedStructure), but Bukkit's BoundingBox.contains() treats max as
            // exclusive — a seed block exactly on the max face would miss. Compare inclusively.
            .firstOrNull { gs ->
                val b = gs.boundingBox
                x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ
            }
            ?.let { gs ->
                val bb = gs.boundingBox
                BfsResult(
                    minX = bb.minX.toInt(), minY = bb.minY.toInt(), minZ = bb.minZ.toInt(),
                    maxX = bb.maxX.toInt(), maxY = bb.maxY.toInt(), maxZ = bb.maxZ.toInt(),
                    // Placeholder counts — the authoritative scanChamber pass in registerNew
                    // reports real vault/spawner numbers. The seed block itself guarantees
                    // the box isn't empty.
                    vaultCount = 0, spawnerCount = 0, structuralCount = 1,
                    hitUnloadedChunks = false
                )
            }
    } catch (e: Exception) {
        plugin.logger.warning("[Discovery] structure-bounds lookup failed at $x,$y,$z: ${e.message} — falling back to block scan")
        null
    }

    private fun registerChamber(
        world: World,
        result: BfsResult,
        key: String,
        method: io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method,
        boundsConfirmed: Boolean = false
    ) {
        val worldName = world.name
        val name = "auto_${worldName}_${result.centerX}_${result.centerZ}"

        plugin.launchAsync {
            registrationMutex.withLock {
                try {
                    // Structure-bounds results are EXACT: only merge when the boxes actually
                    // overlap (i.e. the same structure was re-seeded, or a BFS-registered
                    // fragment of it exists). The 250-block proximity merge is for clipped
                    // block-scan fragments and would wrongly glue two distinct neighbouring
                    // structures into one chamber.
                    val nearby = if (boundsConfirmed) findOverlappingChamber(worldName, result)
                                 else findNearbyChamber(worldName, result)
                    if (nearby != null) {
                        mergeIntoExisting(world, nearby, result, key, method, exactBounds = boundsConfirmed)
                    } else {
                        registerNew(world, name, result, key, method, boundsConfirmed)
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("[Discovery] Unexpected error during registration: ${e.message}")
                    e.printStackTrace()
                    finalizeFailed(key, "registration exception")
                }
            }
        }
    }

    /**
     * Looks for a registered chamber in the same world whose AABB is within
     * `discovery.merge-distance-blocks` (Chebyshev distance, edge-to-edge) of the
     * BFS result. Used to fold multiple seeds from the same physical chamber, and
     * to absorb seeds from chambers that the BFS truncated due to radius caps,
     * into a single registration.
     */
    private fun findNearbyChamber(
        worldName: String,
        result: BfsResult
    ): io.github.darkstarworks.trialChamberPro.models.Chamber? {
        val mergeDistance = plugin.config.getInt("discovery.merge-distance-blocks", 250)
        if (mergeDistance < 0) return null
        return plugin.chamberManager.getCachedChambers().firstOrNull { c ->
            c.world == worldName && aabbsWithin(
                result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ,
                c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ,
                mergeDistance
            )
        }
    }

    /** Registered chamber whose AABB actually overlaps/touches the result box (distance 0). */
    private fun findOverlappingChamber(
        worldName: String,
        result: BfsResult
    ): io.github.darkstarworks.trialChamberPro.models.Chamber? =
        plugin.chamberManager.getCachedChambers().firstOrNull { c ->
            c.world == worldName && aabbsWithin(
                result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ,
                c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ,
                0
            )
        }

    /** Chebyshev edge-to-edge distance check between two AABBs. */
    private fun aabbsWithin(
        aMinX: Int, aMinY: Int, aMinZ: Int, aMaxX: Int, aMaxY: Int, aMaxZ: Int,
        bMinX: Int, bMinY: Int, bMinZ: Int, bMaxX: Int, bMaxY: Int, bMaxZ: Int,
        distance: Int
    ): Boolean {
        val dx = maxOf(0, maxOf(aMinX - bMaxX, bMinX - aMaxX))
        val dy = maxOf(0, maxOf(aMinY - bMaxY, bMinY - aMaxY))
        val dz = maxOf(0, maxOf(aMinZ - bMaxZ, bMinZ - aMaxZ))
        return maxOf(dx, dy, dz) <= distance
    }

    private suspend fun mergeIntoExisting(
        world: World,
        existing: io.github.darkstarworks.trialChamberPro.models.Chamber,
        result: BfsResult,
        key: String,
        method: io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method,
        exactBounds: Boolean = false
    ) {
        val newMinX = minOf(existing.minX, result.minX)
        val newMinY = minOf(existing.minY, result.minY)
        val newMinZ = minOf(existing.minZ, result.minZ)
        val newMaxX = maxOf(existing.maxX, result.maxX)
        val newMaxY = maxOf(existing.maxY, result.maxY)
        val newMaxZ = maxOf(existing.maxZ, result.maxZ)

        if (newMinX == existing.minX && newMinY == existing.minY && newMinZ == existing.minZ &&
            newMaxX == existing.maxX && newMaxY == existing.maxY && newMaxZ == existing.maxZ) {
            // Result fully contained in existing chamber — no work needed. Checked BEFORE the
            // volume cap: a re-seed of an already-registered chamber larger than
            // max-merged-volume (possible with v1.7.0 structure-bounds registrations) is a
            // pure duplicate, not a merge, and must not log a cap failure every cooldown.
            finalizeFailed(key, "region fully covered by existing chamber '${existing.name}'")
            return
        }

        // Cap the merged volume so a runaway BFS or pathological geometry can't
        // swallow half the world into one logical chamber. When the incoming result is
        // EXACT structure bounds (v1.7.0) — e.g. absorbing an old clipped block-scan
        // fragment of the same structure — the relevant ceiling is structure-max-volume,
        // not the (much smaller) BFS merge cap.
        val newVolume = (newMaxX - newMinX + 1).toLong() *
                (newMaxY - newMinY + 1).toLong() *
                (newMaxZ - newMinZ + 1).toLong()
        val maxMerged = if (exactBounds) plugin.config.getLong("discovery.structure-max-volume", 15_000_000L)
                        else plugin.config.getLong("discovery.max-merged-volume", 1_500_000L)
        if (maxMerged >= 0 && newVolume > maxMerged) {
            finalizeFailed(key, "merge with '${existing.name}' would exceed the merge volume cap ($newVolume > $maxMerged) — leaving as separate region")
            return
        }

        val ok = plugin.chamberManager.updateBounds(existing.id, newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ)
        if (!ok) {
            finalizeFailed(key, "updateBounds failed for chamber '${existing.name}'")
            return
        }

        // Re-fetch the refreshed chamber for the rescan.
        val refreshed = plugin.chamberManager.getChamber(existing.name)
        if (refreshed != null) {
            plugin.chamberManager.scanChamber(refreshed)

            // Re-snapshot when auto-snapshot is on OR the chamber already has a
            // snapshot. A pre-merge snapshot covers the old, smaller bounds —
            // restoring it against the grown AABB makes the reset's
            // clear-added-blocks pass wipe everything in the newly annexed
            // volume. A stale snapshot here is actively dangerous, not just
            // incomplete, so it must be recaptured (and DB-linked) immediately.
            val hadSnapshot = refreshed.snapshotFile != null
            if (hadSnapshot || plugin.config.getBoolean("discovery.auto-snapshot", false)) {
                try {
                    val file = plugin.snapshotManager.createSnapshot(refreshed)
                    if (!plugin.chamberManager.setSnapshotFile(refreshed.name, file.absolutePath)) {
                        plugin.logger.warning(
                            "[Discovery] Post-merge snapshot for '${existing.name}' captured but DB link " +
                                "failed — run /tcp snapshot create ${existing.name} to retry."
                        )
                    }
                } catch (e: Exception) {
                    plugin.logger.warning(
                        "[Discovery] Auto-snapshot after merge failed for '${existing.name}': ${e.message}" +
                            if (hadSnapshot) " — the existing snapshot is STALE (pre-merge bounds); " +
                                "run /tcp snapshot create ${existing.name} before the next reset." else ""
                    )
                }
            }
        }

        plugin.logger.info("[Discovery] Merged region into existing chamber '${existing.name}' " +
                "(new bounds ${newMaxX - newMinX + 1}x${newMaxY - newMinY + 1}x${newMaxZ - newMinZ + 1}, " +
                "absorbed ${result.vaultCount} vaults / ${result.spawnerCount} spawners from new region)")

        if (plugin.config.getBoolean("discovery.notify-ops", true)) {
            val msg = plugin.getMessageComponent(
                "discovery-merged",
                "name" to existing.name,
                "vaults" to result.vaultCount,
                "spawners" to result.spawnerCount
            )
            plugin.scheduler.runTask(Runnable {
                plugin.server.onlinePlayers
                    .filter { it.hasPermission("tcp.discovery.notify") }
                    .forEach { it.sendMessage(msg) }
            })
        }

        markProcessed(key)
    }

    /** Outcome of an operator-triggered [expandExisting] pass. */
    data class ExpandResult(
        val grew: Boolean,
        val reason: String,
        val oldVolume: Long,
        val newVolume: Long,
        val vaults: Int,
        val spawners: Int
    )

    /**
     * Operator-triggered re-discovery (`/tcp scan add`): re-runs the flood-fill
     * from the chamber's known structural blocks (its registered vaults) with all
     * chunks now loaded, and grows the chamber's AABB to cover any sections that
     * the original auto-discovery clipped — typically a wing whose chunks were
     * unloaded at detection time, so the flood stopped at the chunk boundary.
     *
     * Flooding from MULTIPLE spread-out vault seeds (and unioning the results)
     * also works around the single-flood node cap, since each seed gets its own
     * budget. Re-uses the same updateBounds → rescan → re-snapshot path as the
     * auto-discovery region merge.
     */
    suspend fun expandExisting(
        chamber: io.github.darkstarworks.trialChamberPro.models.Chamber,
        forceLoad: Boolean = plugin.config.getBoolean("discovery.expand-force-load", false),
        markConfirmed: Boolean = false
    ): ExpandResult {
        val oldVolume = (chamber.maxX - chamber.minX + 1).toLong() *
                (chamber.maxY - chamber.minY + 1).toLong() *
                (chamber.maxZ - chamber.minZ + 1).toLong()
        val world = chamber.getWorld()
            ?: return ExpandResult(false, "world '${chamber.world}' not loaded", oldVolume, oldVolume, 0, 0)

        val seeds = plugin.vaultManager.getVaultsForChamber(chamber.id)
        if (seeds.isEmpty()) {
            return ExpandResult(false, "no registered vaults to flood from — run /tcp scan first", oldVolume, oldVolume, 0, 0)
        }

        // forceLoad pulls in unloaded chunks on demand so a never-visited wing can
        // be reached — but it's Paper-only (off-region getChunkAt throws on Folia).
        val effectiveForceLoad = forceLoad && !plugin.scheduler.isFolia

        var minX = chamber.minX; var minY = chamber.minY; var minZ = chamber.minZ
        var maxX = chamber.maxX; var maxY = chamber.maxY; var maxZ = chamber.maxZ

        for (v in seeds) {
            val r = floodOnRegion(world, v.x, v.y, v.z, effectiveForceLoad) ?: continue
            // structuralCount <= 1 means the seed block itself was air/unreadable — skip.
            if (r.structuralCount <= 1) continue
            minX = minOf(minX, r.minX); minY = minOf(minY, r.minY); minZ = minOf(minZ, r.minZ)
            maxX = maxOf(maxX, r.maxX); maxY = maxOf(maxY, r.maxY); maxZ = maxOf(maxZ, r.maxZ)
        }

        val newVolume = (maxX - minX + 1).toLong() *
                (maxY - minY + 1).toLong() *
                (maxZ - minZ + 1).toLong()

        if (minX == chamber.minX && minY == chamber.minY && minZ == chamber.minZ &&
            maxX == chamber.maxX && maxY == chamber.maxY && maxZ == chamber.maxZ
        ) {
            // A thorough pass that found nothing still confirms the bounds.
            if (markConfirmed) plugin.chamberManager.setBoundsConfirmed(chamber.id, true)
            return ExpandResult(false, "no additional sections found", oldVolume, oldVolume, 0, 0)
        }

        val maxMerged = plugin.config.getLong("discovery.max-merged-volume", 1_500_000L)
        if (newVolume > maxMerged) {
            return ExpandResult(false, "expansion would exceed max-merged-volume ($newVolume > $maxMerged)", oldVolume, newVolume, 0, 0)
        }

        if (!plugin.chamberManager.updateBounds(chamber.id, minX, minY, minZ, maxX, maxY, maxZ)) {
            return ExpandResult(false, "updateBounds failed", oldVolume, newVolume, 0, 0)
        }

        val refreshed = plugin.chamberManager.getChamber(chamber.name)
            ?: return ExpandResult(true, "bounds updated but re-fetch failed", oldVolume, newVolume, 0, 0)

        val (vaults, spawners, _) = plugin.chamberManager.scanChamber(refreshed)

        // A snapshot taken against the old, smaller bounds is now stale (and
        // dangerous at reset time). Recapture when one existed or auto-snapshot
        // is on — same policy as the auto-discovery merge.
        val hadSnapshot = refreshed.snapshotFile != null
        if (hadSnapshot || plugin.config.getBoolean("discovery.auto-snapshot", false)) {
            try {
                val file = plugin.snapshotManager.createSnapshot(refreshed)
                if (!plugin.chamberManager.setSnapshotFile(refreshed.name, file.absolutePath)) {
                    plugin.logger.warning("[Discovery] scan-add snapshot for '${refreshed.name}' captured but DB link failed — run /tcp snapshot create ${refreshed.name}.")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[Discovery] scan-add re-snapshot for '${refreshed.name}' failed: ${e.message}" +
                    if (hadSnapshot) " — the existing snapshot is STALE; run /tcp snapshot create ${refreshed.name} before the next reset." else "")
            }
        }

        if (markConfirmed) plugin.chamberManager.setBoundsConfirmed(chamber.id, true)
        plugin.logger.info("[Discovery] scan-add grew '${chamber.name}' from $oldVolume to $newVolume blocks ($vaults vaults, $spawners spawners)")
        return ExpandResult(true, "expanded", oldVolume, newVolume, vaults, spawners)
    }

    /** Runs [bfsCompute] on the region thread owning the seed and awaits the result. */
    private suspend fun floodOnRegion(world: World, sx: Int, sy: Int, sz: Int, forceLoad: Boolean): BfsResult? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            plugin.scheduler.runAtLocation(Location(world, sx + 0.5, sy + 0.5, sz + 0.5), Runnable {
                val r = try {
                    bfsCompute(world, sx, sy, sz, forceLoad)
                } catch (e: Exception) {
                    plugin.logger.warning("[Discovery] scan-add flood failed at $sx,$sy,$sz: ${e.message}")
                    null
                }
                cont.resume(r) {}
            })
        }

    private suspend fun registerNew(
        world: World,
        name: String,
        result: BfsResult,
        key: String,
        method: io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent.Method,
        boundsConfirmed: Boolean = false
    ) {
        // Fire pre-registration event; listeners may abort.
        val corner1 = Location(world, result.minX.toDouble(), result.minY.toDouble(), result.minZ.toDouble())
        val corner2 = Location(world, result.maxX.toDouble(), result.maxY.toDouble(), result.maxZ.toDouble())
        val discoveryEvent = io.github.darkstarworks.trialChamberPro.api.events.ChamberDiscoveredEvent(
            world = world,
            suggestedName = name,
            minCorner = corner1,
            maxCorner = corner2,
            vaultCount = result.vaultCount,
            spawnerCount = result.spawnerCount,
            method = method
        )
        plugin.server.pluginManager.callEvent(discoveryEvent)
        if (discoveryEvent.isCancelled) {
            finalizeFailed(key, "ChamberDiscoveredEvent cancelled by listener")
            return
        }

        val chamber = plugin.chamberManager.createChamber(name, corner1, corner2)
        if (chamber == null) {
            finalizeFailed(key, "createChamber returned null (duplicate name or DB error)")
            return
        }

        // Scan FIRST and report the authoritative counts. The BFS result
        // undercounts (its 50k-node hard cap can be exhausted among the chamber's
        // thousands of tuff/copper blocks before it reaches the recessed trial
        // spawners), which produced the long-standing "0 spawners" notification.
        val (scannedVaults, scannedSpawners, _) = plugin.chamberManager.scanChamber(chamber)

        plugin.logger.info("[Discovery] Registered chamber '$name' (${result.sizeX}x${result.sizeY}x${result.sizeZ}, $scannedVaults vaults, $scannedSpawners spawners)")

        if (plugin.config.getBoolean("discovery.notify-ops", true)) {
            val msg = plugin.getMessageComponent(
                "discovery-registered",
                "name" to name,
                "vaults" to scannedVaults,
                "spawners" to scannedSpawners
            )
            plugin.scheduler.runTask(Runnable {
                plugin.server.onlinePlayers
                    .filter { it.hasPermission("tcp.discovery.notify") }
                    .forEach { it.sendMessage(msg) }
            })
        }

        if (plugin.config.getBoolean("discovery.auto-snapshot", false)) {
            try {
                val file = plugin.snapshotManager.createSnapshot(chamber)
                // Link the file in the DB row — without this the chamber reports
                // "no snapshot" at reset time even though the .dat is on disk.
                if (!plugin.chamberManager.setSnapshotFile(name, file.absolutePath)) {
                    plugin.logger.warning(
                        "[Discovery] Auto-snapshot for '$name' captured but DB link failed — " +
                            "run /tcp snapshot create $name to retry."
                    )
                }
            } catch (e: Exception) {
                plugin.logger.warning("[Discovery] Auto-snapshot failed for '$name': ${e.message}")
            }
        }

        markProcessed(key)

        // Structure-bounds discoveries (v1.7.0) are exact — mark confirmed and skip the
        // auto-expand pass, which exists only to fix clipped block-scan results.
        if (boundsConfirmed) {
            plugin.chamberManager.setBoundsConfirmed(chamber.id, true)
            return
        }

        // Lazy-admin safety net: most operators will never run `/tcp scan add`, so
        // automatically run one expand pass shortly after registering. The initial
        // single-seed flood can clip a chamber (its node cap, or chunks that were
        // unloaded at detection time); a delayed multi-seed re-flood from the now-
        // committed vault rows — by which point nearby chunks have usually loaded —
        // grows the bounds to cover what the first pass missed. Best-effort and
        // gated by config; logs only (no second player notification).
        if (plugin.config.getBoolean("discovery.expand-on-discover", true)) {
            val delaySec = plugin.config.getLong("discovery.expand-delay-seconds", 10L).coerceAtLeast(2L)
            plugin.launchAsync {
                kotlinx.coroutines.delay(delaySec * 1000L)
                val fresh = plugin.chamberManager.getChamber(name) ?: return@launchAsync
                try {
                    val r = expandExisting(fresh)
                    if (r.grew) {
                        plugin.logger.info("[Discovery] Auto-expand grew '$name' by ${r.newVolume - r.oldVolume} blocks (now ${r.vaults} vaults, ${r.spawners} spawners)")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[Discovery] Auto-expand for '$name' failed: ${e.message}")
                }
            }
        }
    }

    private fun finalizeFailed(key: String, reason: String) {
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Discovery] Skipped region $key: $reason")
        }
        markProcessed(key)
    }

    private fun markProcessed(key: String) {
        val cooldownMs = plugin.config.getLong("discovery.cooldown-seconds", 300L) * 1000L
        recentlyProcessed[key] = System.currentTimeMillis() + cooldownMs
        inFlight.remove(key)
        // Trim stale entries occasionally to prevent unbounded growth
        if (recentlyProcessed.size > 512) {
            val now = System.currentTimeMillis()
            recentlyProcessed.entries.removeIf { it.value < now }
        }
    }

    private data class BfsResult(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val vaultCount: Int,
        val spawnerCount: Int,
        val structuralCount: Int,
        val hitUnloadedChunks: Boolean
    ) {
        val sizeX get() = maxX - minX + 1
        val sizeY get() = maxY - minY + 1
        val sizeZ get() = maxZ - minZ + 1
        val centerX get() = (minX + maxX) / 2
        val centerY get() = (minY + maxY) / 2
        val centerZ get() = (minZ + maxZ) / 2
    }

    /**
     * BFS flood-fill over connected structural blocks, capped by configured radii.
     * Must be called on the region thread owning the seed location.
     */
    private fun bfsCompute(world: World, sx: Int, sy: Int, sz: Int, forceLoad: Boolean = false): BfsResult {
        val maxRadiusXZ = plugin.config.getInt("discovery.max-radius-xz", 60)
        val maxRadiusY = plugin.config.getInt("discovery.max-radius-y", 45)

        var minX = sx; var minY = sy; var minZ = sz
        var maxX = sx; var maxY = sy; var maxZ = sz
        var vaults = 0
        var spawners = 0
        var structural = 0
        var hitUnloaded = false

        val visited = HashSet<Long>(4096)
        val queue = ArrayDeque<IntArray>()
        queue.add(intArrayOf(sx, sy, sz))
        visited.add(packKey(sx, sy, sz))

        val dirs = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
        )

        val hardCap = 50_000 // safety bound on BFS node count

        while (queue.isNotEmpty() && visited.size < hardCap) {
            val cur = queue.poll()
            val cx = cur[0]; val cy = cur[1]; val cz = cur[2]

            // Radius check from seed
            if (Math.abs(cx - sx) > maxRadiusXZ || Math.abs(cz - sz) > maxRadiusXZ || Math.abs(cy - sy) > maxRadiusY) continue

            // Chunk must be loaded to safely read the block. With forceLoad on
            // (the opt-in `discovery.expand-force-load` path, Paper-only), pull it
            // in on demand so a never-visited wing can still be reached; otherwise
            // note the gap and skip (the caller may retry once chunks stream in).
            val chunkX = cx shr 4
            val chunkZ = cz shr 4
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (forceLoad) {
                    world.getChunkAt(chunkX, chunkZ) // loads synchronously (generates only if absent on disk)
                } else {
                    hitUnloaded = true
                    continue
                }
            }

            val block = world.getBlockAt(cx, cy, cz)
            val type = block.type
            if (!isChamberStructuralOrFeature(type)) continue

            structural++
            when (type) {
                Material.VAULT -> vaults++
                Material.TRIAL_SPAWNER -> spawners++
                else -> {}
            }

            if (cx < minX) minX = cx
            if (cy < minY) minY = cy
            if (cz < minZ) minZ = cz
            if (cx > maxX) maxX = cx
            if (cy > maxY) maxY = cy
            if (cz > maxZ) maxZ = cz

            for (d in dirs) {
                val nx = cx + d[0]; val ny = cy + d[1]; val nz = cz + d[2]
                val k = packKey(nx, ny, nz)
                if (visited.add(k)) queue.add(intArrayOf(nx, ny, nz))
            }
        }

        return BfsResult(
            minX, minY, minZ, maxX, maxY, maxZ,
            vaults, spawners, structural, hitUnloaded
        )
    }

    private fun validateResult(r: BfsResult): Boolean {
        val minTotal = plugin.config.getInt("discovery.min-vaults-plus-spawners", 2)
        if (r.vaultCount + r.spawnerCount < minTotal) return false
        // Vanilla minimum chamber dimensions
        if (r.sizeX < 31 || r.sizeY < 15 || r.sizeZ < 31) return false
        val maxCenterY = plugin.config.getInt("discovery.max-center-y", 10)
        if (r.centerY > maxCenterY) return false
        val maxVolume = plugin.config.getLong("generation.max-volume", 750_000L)
        if (r.sizeX.toLong() * r.sizeY.toLong() * r.sizeZ.toLong() > maxVolume) return false
        return true
    }

    private fun packKey(x: Int, y: Int, z: Int): Long {
        // y is [-64, 320], x/z fit into 21 bits comfortably for a 60-radius search
        return (x.toLong() and 0x3FFFFF) or
                ((z.toLong() and 0x3FFFFF) shl 22) or
                ((y.toLong() and 0xFFFF) shl 44)
    }

    private fun regionKey(world: World, x: Int, z: Int): String {
        // ~128 block buckets (8x8 chunks), large enough to cover a whole chamber
        return "${world.name}:${x shr 7}:${z shr 7}"
    }

    companion object {
        fun isChamberStructuralOrFeature(m: Material): Boolean = when (m) {
            Material.VAULT,
            Material.TRIAL_SPAWNER,
            Material.HEAVY_CORE,
            // Tuff brick family (crafted, do not naturally spawn outside chambers)
            Material.TUFF_BRICKS,
            Material.TUFF_BRICK_SLAB,
            Material.TUFF_BRICK_STAIRS,
            Material.TUFF_BRICK_WALL,
            Material.CHISELED_TUFF,
            Material.CHISELED_TUFF_BRICKS,
            Material.POLISHED_TUFF,
            Material.POLISHED_TUFF_SLAB,
            Material.POLISHED_TUFF_STAIRS,
            Material.POLISHED_TUFF_WALL,
            // Copper feature blocks (crafted/decorative variants)
            Material.CHISELED_COPPER,
            Material.EXPOSED_CHISELED_COPPER,
            Material.WEATHERED_CHISELED_COPPER,
            Material.OXIDIZED_CHISELED_COPPER,
            Material.WAXED_CHISELED_COPPER,
            Material.WAXED_EXPOSED_CHISELED_COPPER,
            Material.WAXED_WEATHERED_CHISELED_COPPER,
            Material.WAXED_OXIDIZED_CHISELED_COPPER,
            Material.COPPER_GRATE,
            Material.EXPOSED_COPPER_GRATE,
            Material.WEATHERED_COPPER_GRATE,
            Material.OXIDIZED_COPPER_GRATE,
            Material.WAXED_COPPER_GRATE,
            Material.WAXED_EXPOSED_COPPER_GRATE,
            Material.WAXED_WEATHERED_COPPER_GRATE,
            Material.WAXED_OXIDIZED_COPPER_GRATE,
            Material.COPPER_BULB,
            Material.EXPOSED_COPPER_BULB,
            Material.WEATHERED_COPPER_BULB,
            Material.OXIDIZED_COPPER_BULB,
            Material.WAXED_COPPER_BULB,
            Material.WAXED_EXPOSED_COPPER_BULB,
            Material.WAXED_WEATHERED_COPPER_BULB,
            Material.WAXED_OXIDIZED_COPPER_BULB,
            Material.COPPER_DOOR,
            Material.EXPOSED_COPPER_DOOR,
            Material.WEATHERED_COPPER_DOOR,
            Material.OXIDIZED_COPPER_DOOR,
            Material.WAXED_COPPER_DOOR,
            Material.WAXED_EXPOSED_COPPER_DOOR,
            Material.WAXED_WEATHERED_COPPER_DOOR,
            Material.WAXED_OXIDIZED_COPPER_DOOR,
            Material.COPPER_TRAPDOOR,
            Material.EXPOSED_COPPER_TRAPDOOR,
            Material.WEATHERED_COPPER_TRAPDOOR,
            Material.OXIDIZED_COPPER_TRAPDOOR,
            Material.WAXED_COPPER_TRAPDOOR,
            Material.WAXED_EXPOSED_COPPER_TRAPDOOR,
            Material.WAXED_WEATHERED_COPPER_TRAPDOOR,
            Material.WAXED_OXIDIZED_COPPER_TRAPDOOR -> true
            else -> false
        }

        fun isSeedBlock(m: Material): Boolean = m == Material.VAULT || m == Material.TRIAL_SPAWNER
    }
}

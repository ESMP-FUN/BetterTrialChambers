package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Trial Spawner wave tracking with boss bar display.
 *
 * Tracks:
 * - Active waves per spawner location
 * - Mob spawn/death counts
 * - Participating players
 * - Boss bar progress display
 *
 * Features:
 * - Boss bar shows wave progress (mobs killed / total mobs)
 * - Ominous spawners show purple boss bar, normal shows yellow
 * - Wave completion triggers bonus statistics
 */
class SpawnerWaveManager(private val plugin: TrialChamberPro) {

    /**
     * Represents the state of an active trial spawner wave.
     * Uses AtomicInteger for thread-safe counter access from multiple event handlers.
     */
    data class WaveState(
        val spawnerId: String,
        val location: Location,
        val isOminous: Boolean,
        val startTime: Long = System.currentTimeMillis(),
        val totalMobsExpected: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val mobsSpawned: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val mobsKilled: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val trackedMobs: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
        val participatingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
        var bossBar: BossBar? = null,
        var waveNumber: Int = 1,
        @Volatile var completed: Boolean = false,
        @Volatile var glowEntityId: UUID? = null
    ) {
        fun getProgress(): Float {
            val expected = totalMobsExpected.get()
            if (expected <= 0) return 0f
            return (mobsKilled.get().toFloat() / expected.toFloat()).coerceIn(0f, 1f)
        }

        fun isAllMobsKilled(): Boolean = mobsKilled.get() >= totalMobsExpected.get() && totalMobsExpected.get() > 0
    }

    // Active wave states keyed by spawner location string
    private val activeWaves = ConcurrentHashMap<String, WaveState>()

    // Map mob UUIDs to their spawner location for death tracking
    private val mobToSpawner = ConcurrentHashMap<UUID, String>()

    // Player to spawner mapping for boss bar cleanup
    private val playerBossBars = ConcurrentHashMap<UUID, MutableSet<String>>()

    // v1.5.0: per-chamber tracking for ChamberClearedEvent. A chamber is
    // "cleared" when every trial spawner inside it has completed a wave
    // within the same reset cycle (i.e. without a ChamberResetEvent in
    // between). Cleared on every chamber reset via clearWavesInChamber.
    private val chamberSpawnersCompletedThisCycle = ConcurrentHashMap<Int, MutableSet<String>>()
    private val chamberParticipantsThisCycle = ConcurrentHashMap<Int, MutableSet<UUID>>()
    private val chamberCycleStartMs = ConcurrentHashMap<Int, Long>()
    // Lazy cache of spawner counts per chamber (block scan is O(volume); cache so
    // subsequent wave completions are O(1)). Reset cycles preserve the count —
    // a chamber's snapshot restoration keeps spawner geometry stable.
    private val chamberSpawnerCountCache = ConcurrentHashMap<Int, Int>()

    // PDC marker stamped on glow-overlay entities so mob-cap / anti-cheat
    // plugins can identify them as a TCP overlay and skip them from monster
    // counts or detection scans. Value is a sentinel byte.
    private val glowMarkerKey = org.bukkit.NamespacedKey(plugin, "glow_marker")

    // v1.5.4: chamber-remaining glow tracking. Maps chamberId → (spawnerKey → entityUUID)
    // for "standalone" glows spawned on uncleared sister-spawners in chamber-remaining mode.
    // Distinct from WaveState.glowEntityId (which tracks the wave-attached glow on the
    // spawner that triggered the wave). On wave-start for a spawner that was previously
    // standalone-glowed, the standalone is upgraded to a wave-attached glow.
    private val chamberRemainingGlows = ConcurrentHashMap<Int, MutableMap<String, UUID>>()

    // v1.5.4: cache of spawner LOCATIONS per chamber (vs. chamberSpawnerCountCache
    // which caches just the count). Same lazy-scan-then-cache pattern; populated on
    // first chamber-remaining glow refresh and reused on subsequent waves in the cycle.
    private val chamberSpawnerLocationsCache = ConcurrentHashMap<Int, List<Location>>()

    init {
        // Periodic sweep: drop UUIDs whose entity is gone (despawned, /kill, removed by another
        // plugin, void death without an EntityDeathEvent); close out waves whose spawner block
        // has already entered cooldown vanilla-side or whose block no longer exists. Without this
        // the boss bar deadlocks at e.g. 2/6 forever when a tracked mob disappears silently.
        try {
            plugin.scheduler.runTaskTimer(Runnable { sweepWaves() }, 100L, 100L)
        } catch (e: Throwable) {
            plugin.logger.warning("[SpawnerWave] Failed to schedule wave sweeper: ${e.message}")
        }
    }

    /**
     * Reads the spawner's actual `total_mobs` / `total_mobs_added_per_player` from its
     * configuration NBT and computes the expected wave size. Falls back to 6 if the block
     * isn't a trial spawner or the API call fails.
     *
     * v1.4.0: matches Mojang's `TrialSpawnerData` formula exactly. The previous v1.3.2
     * implementation used `× players` (over-counting by `perPlayer` per nearby player),
     * which produced bars showing e.g. "1/30" when vanilla actually completed at 20.
     * Vanilla counts the FIRST tracked player as the trigger (gets `base` mobs only) and
     * each ADDITIONAL player as a `perPlayer` bonus — i.e. `additional = max(0, players - 1)`.
     */
    private fun computeExpectedMobs(location: Location, isOminous: Boolean, playerCountFallback: Int): Int {
        return try {
            val world = location.world ?: return 6
            val state = world.getBlockAt(location).state as? org.bukkit.block.TrialSpawner ?: return 6
            val cfg = if (isOminous) state.ominousConfiguration else state.normalConfiguration
            // `trackedPlayers` is Paper's mirror of Mojang's `detectedPlayers` — snapshotted
            // at trigger time. Falls back to caller-provided count (clamped >= 1) only if
            // the API returns empty (e.g. between waves or post-cooldown).
            val players = state.trackedPlayers.size.takeIf { it > 0 } ?: playerCountFallback.coerceAtLeast(1)
            val base = cfg.baseSpawnsBeforeCooldown
            val perPlayer = cfg.additionalSpawnsBeforeCooldown
            // Mojang formula (net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData):
            //   additional = max(0, detectedPlayers.size - 1)
            //   totalMobsToSpawn = floor(totalMobs + totalMobsAddedPerPlayer * additional)
            val additional = (players - 1).coerceAtLeast(0)
            kotlin.math.floor(base + perPlayer * additional).toInt().coerceAtLeast(1)
        } catch (_: Throwable) {
            6
        }
    }

    /**
     * Gets spawner key from location.
     */
    private fun getSpawnerKey(location: Location): String {
        return "${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Starts or updates a wave for a trial spawner.
     */
    fun startWave(spawnerLocation: Location, isOminous: Boolean, expectedMobs: Int): WaveState {
        val key = getSpawnerKey(spawnerLocation)

        val existingWave = activeWaves[key]
        if (existingWave != null) {
            if (!existingWave.completed) {
                // Update existing active wave with new expected mob count (atomic max)
                existingWave.totalMobsExpected.updateAndGet { current -> maxOf(current, expectedMobs) }
                return existingWave
            } else {
                // Wave is completed but still in cleanup delay - remove old boss bar immediately
                // to prevent duplicate bars when starting a new wave
                removeBossBar(existingWave)
                activeWaves.remove(key)
            }
        }

        // Create new wave state
        val wave = WaveState(
            spawnerId = key,
            location = spawnerLocation.clone(),
            isOminous = isOminous
        ).apply {
            totalMobsExpected.set(expectedMobs)
        }

        // Create boss bar
        if (plugin.config.getBoolean("spawner-waves.show-boss-bar", true)) {
            wave.bossBar = createBossBar(wave)
        }

        activeWaves[key] = wave

        // Spawn glow outline on active spawner (v1.2.27)
        spawnGlowDisplay(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Started wave at $key: expected $expectedMobs mobs, ominous=$isOminous")
        }

        return wave
    }

    /**
     * Records a mob spawn from a trial spawner.
     */
    fun recordMobSpawn(spawnerLocation: Location, mob: Entity, isOminous: Boolean) {
        val key = getSpawnerKey(spawnerLocation)
        val mobUUID = mob.uniqueId

        // Get or create wave
        var wave = activeWaves[key]
        if (wave == null || wave.completed) {
            // Read real total_mobs from spawner NBT instead of assuming 6
            val initialExpected = computeExpectedMobs(spawnerLocation, isOminous, 1)
            wave = startWave(spawnerLocation, isOminous, initialExpected)
        }

        // Track the mob
        wave.trackedMobs.add(mobUUID)
        val spawned = wave.mobsSpawned.incrementAndGet()
        mobToSpawner[mobUUID] = key

        // Ratchet expected count: max of (configured base+per-player), actual spawn count, and
        // current value. The configured value can grow as more players join (additional per-player).
        // v1.4.0: fallback was `wave.participatingPlayers.size` (boss-bar detection radius,
        // default 20 blocks) — which over-counted relative to vanilla's `requiredPlayerRange`
        // (default 14). Pass `1` instead so the fallback can never inflate beyond what
        // `state.trackedPlayers.size` would say. Vanilla's count is authoritative; the
        // fallback only matters if Paper returns an empty trackedPlayers (rare).
        val recomputed = computeExpectedMobs(spawnerLocation, isOminous, 1)
        wave.totalMobsExpected.updateAndGet { current -> maxOf(current, maxOf(spawned, recomputed)) }

        // Update boss bar
        updateBossBar(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Mob spawned at $key: ${mob.type}, total=${wave.mobsSpawned.get()}/${wave.totalMobsExpected.get()}")
        }
    }

    /**
     * Records a mob death and updates wave progress.
     */
    fun recordMobDeath(mob: Entity, killer: Player?): Boolean {
        val mobUUID = mob.uniqueId
        val key = mobToSpawner.remove(mobUUID) ?: return false

        val wave = activeWaves[key] ?: return false
        if (!wave.trackedMobs.remove(mobUUID)) return false

        wave.mobsKilled.incrementAndGet()

        // Track killer as participant
        if (killer != null) {
            wave.participatingPlayers.add(killer.uniqueId)
            addPlayerToBossBar(killer, wave)
        }

        // Update boss bar
        updateBossBar(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Mob killed at $key: ${wave.mobsKilled.get()}/${wave.totalMobsExpected.get()}")
        }

        // Check if wave is complete
        if (wave.isAllMobsKilled() && wave.trackedMobs.isEmpty()) {
            completeWave(wave)
            return true
        }

        return true
    }

    /**
     * Adds a player to the wave tracking (e.g., when they enter spawner range).
     */
    fun addPlayerToWave(player: Player, spawnerLocation: Location) {
        val key = getSpawnerKey(spawnerLocation)
        val wave = activeWaves[key] ?: return

        wave.participatingPlayers.add(player.uniqueId)
        addPlayerToBossBar(player, wave)
    }

    /**
     * Removes a player from all wave tracking (e.g., on disconnect).
     */
    fun removePlayer(player: Player) {
        val playerUUID = player.uniqueId
        val spawnerKeys = playerBossBars.remove(playerUUID) ?: return

        spawnerKeys.forEach { key ->
            val wave = activeWaves[key] ?: return@forEach
            wave.participatingPlayers.remove(playerUUID)
            wave.bossBar?.let { bar ->
                bar.removeViewer(player)
            }
        }
    }

    /**
     * Removes the player from any wave whose spawner is farther than [removeDistance] blocks.
     * Prevents the boss bar from lingering after a player leaves the chamber area.
     */
    fun removePlayerFromDistantWaves(player: Player, removeDistance: Double) {
        val playerUUID = player.uniqueId
        val tracked = playerBossBars[playerUUID] ?: return
        if (tracked.isEmpty()) return

        val playerLoc = player.location
        val removeDistSq = removeDistance * removeDistance

        // Iterate a snapshot to avoid CME
        tracked.toList().forEach { key ->
            val wave = activeWaves[key] ?: run {
                tracked.remove(key)
                return@forEach
            }
            val waveLoc = wave.location
            // Ignore cross-world cases (also stale)
            if (waveLoc.world != playerLoc.world ||
                playerLoc.distanceSquared(waveLoc) > removeDistSq
            ) {
                wave.bossBar?.removeViewer(player)
                wave.participatingPlayers.remove(playerUUID)
                tracked.remove(key)
            }
        }

        if (tracked.isEmpty()) {
            playerBossBars.remove(playerUUID)
        }
    }

    /**
     * Cancels and tears down the wave at [spawnerLocation] without firing completion rewards.
     * Called when the spawner block is broken — the wave is no longer meaningful.
     */
    fun cancelWaveAt(spawnerLocation: Location) {
        val key = getSpawnerKey(spawnerLocation)
        val wave = activeWaves.remove(key) ?: return
        wave.completed = true
        removeBossBar(wave)
        mobToSpawner.entries.removeIf { it.value == key }
    }

    /**
     * Removes the chamber-remaining standalone glow at [spawnerLocation], if one
     * exists. Called when a trial-spawner block is broken: [cancelWaveAt] only
     * cleans the wave-attached glow of an *active* spawner — a non-active
     * spawner glowing in `chamber-remaining` mode would otherwise keep its
     * orphaned glow shulker floating in place until the next chamber reset.
     */
    fun removeStandaloneGlowAt(spawnerLocation: Location) {
        val chamber = plugin.chamberManager.getCachedChamberAt(spawnerLocation) ?: return
        val key = getSpawnerKey(spawnerLocation)
        val entityId = chamberRemainingGlows[chamber.id]?.remove(key) ?: return
        val world = spawnerLocation.world ?: return
        val ent = world.getEntity(entityId) ?: return
        plugin.scheduler.runAtEntity(ent, Runnable {
            try { ent.remove() } catch (_: Throwable) { /* already gone */ }
        })
    }

    /**
     * Periodic sweep that fixes the two ways a wave can deadlock:
     *   1. A tracked mob disappears without firing EntityDeathEvent (despawn, /kill, void,
     *      removed by another plugin) — its UUID stays in trackedMobs and the wave never
     *      satisfies trackedMobs.isEmpty().
     *   2. The vanilla spawner has already entered cooldown / ejecting_reward but our kill
     *      counter never reached the (possibly inflated) expected value, so the bar hangs.
     *
     * For each active wave: drop dead/missing UUIDs, then if the spawner block is gone or in
     * cooldown, force-complete; otherwise complete normally if conditions now match.
     */
    private fun sweepWaves() {
        activeWaves.values.toList().forEach { wave ->
            if (wave.completed) return@forEach
            plugin.scheduler.runAtLocation(wave.location, Runnable {
                try {
                    val world = wave.location.world ?: return@Runnable

                    // Phase 1: drop UUIDs whose entity is gone
                    val stale = wave.trackedMobs.filter { uuid ->
                        val ent = world.getEntity(uuid)
                        ent == null || ent.isDead || !ent.isValid
                    }
                    if (stale.isNotEmpty()) {
                        stale.forEach { uuid ->
                            wave.trackedMobs.remove(uuid)
                            mobToSpawner.remove(uuid)
                        }
                        updateBossBar(wave)
                    }

                    // Phase 2: spawner block gone? cancel.
                    val block = world.getBlockAt(wave.location)
                    if (block.type != Material.TRIAL_SPAWNER) {
                        cancelWaveAt(wave.location)
                        return@Runnable
                    }

                    // Phase 3: spawner finished vanilla-side? force-complete (handles inflated
                    // expected counts and silently-vanished mobs).
                    val stateStr = block.blockData.asString
                    val vanillaDone = stateStr.contains("trial_spawner_state=cooldown") ||
                        stateStr.contains("trial_spawner_state=ejecting_reward")
                    if (vanillaDone && wave.trackedMobs.isEmpty()) {
                        completeWave(wave)
                        return@Runnable
                    }

                    // Phase 4: normal completion check (in case stale removal tipped us over)
                    if (wave.isAllMobsKilled() && wave.trackedMobs.isEmpty()) {
                        completeWave(wave)
                    }
                } catch (e: Throwable) {
                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.warning("[SpawnerWave] Sweep failed for ${wave.spawnerId}: ${e.message}")
                    }
                }
            })
        }
    }

    /**
     * Force-clears all active waves inside a chamber. Called on chamber reset so boss bars
     * don't linger after blocks/entities have been wiped.
     */
    fun clearWavesInChamber(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber) {
        val toRemove = activeWaves.entries.filter { chamber.contains(it.value.location) }
        toRemove.forEach { (key, wave) ->
            removeBossBar(wave)
            // Drop any tracked mob UUIDs pointing at this wave
            mobToSpawner.entries.removeIf { it.value == key }
            activeWaves.remove(key)
        }
        // v1.5.0: reset per-cycle ChamberClearedEvent tracking on chamber reset.
        // The spawner-count cache is preserved — the chamber's spawner geometry
        // doesn't change across a reset cycle (snapshot restoration is faithful).
        chamberSpawnersCompletedThisCycle.remove(chamber.id)
        chamberParticipantsThisCycle.remove(chamber.id)
        chamberCycleStartMs.remove(chamber.id)
        // v1.5.4: drop any chamber-remaining standalone glows the reset would orphan.
        clearChamberRemainingGlows(chamber.id)
    }

    /**
     * Gets the active wave at a spawner location.
     */
    fun getWaveAt(spawnerLocation: Location): WaveState? {
        val key = getSpawnerKey(spawnerLocation)
        return activeWaves[key]?.takeIf { !it.completed }
    }

    /**
     * Marks a spawner wave as complete (e.g., when spawner enters cooldown).
     */
    fun completeWave(spawnerLocation: Location) {
        val key = getSpawnerKey(spawnerLocation)
        val wave = activeWaves[key] ?: return
        completeWave(wave)
    }

    /**
     * Completes a wave and gives rewards.
     */
    private fun completeWave(wave: WaveState) {
        if (wave.completed) return
        wave.completed = true

        // Remove glow immediately on completion (don't wait for boss bar's 3s delay)
        removeGlowDisplay(wave)

        val durationMs = System.currentTimeMillis() - wave.startTime
        val durationSeconds = durationMs / 1000

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Wave complete at ${wave.spawnerId}: " +
                "killed ${wave.mobsKilled.get()}, participants=${wave.participatingPlayers.size}, " +
                "duration=${durationSeconds}s")
        }

        // Configure cooldown for wild spawners (must be done BEFORE spawner enters cooldown state)
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Calling configureWildSpawnerCooldownAtCompletion for ${wave.spawnerId}")
        }
        configureWildSpawnerCooldownAtCompletion(wave)

        // v1.3.0: Plugin-driven key drops for non-vanilla providers.
        // Vanilla trial spawners drop their own keys via the spawner state machine; when we've
        // replaced the spawns with provider mobs the spawner still enters cooldown but will not
        // eject keys (it's tracking UUIDs that no longer exist). We compensate here.
        //
        // wave.isOminous is captured at wave creation (WaveState ctor) — it cannot flip mid-wave,
        // so it is safe to use here as the "ominous-at-start" snapshot.
        maybeDropProviderKeys(wave)

        // Award bonus stats to participants
        if (plugin.config.getBoolean("spawner-waves.award-stats", true)) {
            wave.participatingPlayers.forEach { playerUUID ->
                plugin.launchAsync {
                    // Track mobs killed stat (already tracked per kill in StatisticsManager)
                    // Could add wave completion bonus here if desired
                }
            }
        }

        // Send completion message to participants
        if (plugin.config.getBoolean("spawner-waves.completion-message", true)) {
            val typeStr = plugin.getRawMessage(
                if (wave.isOminous) "wave-type-ominous" else "wave-type-normal",
                if (wave.isOminous) "Ominous" else "Trial"
            )
            val message = plugin.getMessageComponent("spawner-wave-complete",
                "type" to typeStr,
                "killed" to wave.mobsKilled.get(),
                "duration" to formatDuration(durationSeconds)
            )

            wave.participatingPlayers.forEach { playerUUID ->
                plugin.server.getPlayer(playerUUID)?.sendMessage(message)
            }
        }

        // Update boss bar to show completion then remove
        wave.bossBar?.let { bar ->
            bar.name(getMessageComponent("spawner-wave-boss-bar-complete"))
            bar.progress(1.0f)
            bar.color(BossBar.Color.GREEN)

            // Remove after a short delay (must be sync - removeBossBar accesses Bukkit API)
            plugin.scheduler.runTaskLater(Runnable {
                removeBossBar(wave)
                // Clean up wave state after delay
                activeWaves.remove(wave.spawnerId)
            }, 60L) // 3 seconds
        } ?: run {
            // No boss bar, clean up immediately
            activeWaves.remove(wave.spawnerId)
        }

        // Fire post-event for downstream consumers (stat plugins, custom rewards, etc).
        // Resolved chamber may be null for wild spawners — listeners must tolerate that.
        val resolvedChamber = plugin.chamberManager.getCachedChamberAt(wave.location)
        plugin.server.pluginManager.callEvent(
            io.github.darkstarworks.trialChamberPro.api.events.SpawnerWaveCompleteEvent(
                spawnerLocation = wave.location,
                chamber = resolvedChamber,
                ominous = wave.isOminous,
                participants = wave.participatingPlayers.toSet(),
                durationMs = durationMs
            )
        )

        // v1.5.0: aggregate per-chamber wave completions. When every spawner in
        // the chamber has finished a wave in this reset cycle, fire a single
        // ChamberClearedEvent so progression / reward modules (Mythic Trials et
        // al.) get one clean signal per "run cleared" without scraping wave
        // state themselves. Wild spawners (chamber == null) don't contribute.
        if (resolvedChamber != null && !resolvedChamber.isPaused) {
            maybeFireChamberCleared(resolvedChamber, wave)
        }
    }

    /**
     * Records this wave's spawner against the chamber's per-cycle completion
     * set and, if every spawner in the chamber has now completed a wave, fires
     * [io.github.darkstarworks.trialChamberPro.api.events.ChamberClearedEvent]
     * exactly once before the chamber's next reset.
     */
    private fun maybeFireChamberCleared(
        chamber: io.github.darkstarworks.trialChamberPro.models.Chamber,
        wave: WaveState
    ) {
        val cid = chamber.id
        val completedSet = chamberSpawnersCompletedThisCycle
            .computeIfAbsent(cid) { ConcurrentHashMap.newKeySet() }
        val participantSet = chamberParticipantsThisCycle
            .computeIfAbsent(cid) { ConcurrentHashMap.newKeySet() }
        chamberCycleStartMs.putIfAbsent(cid, wave.startTime)
        participantSet.addAll(wave.participatingPlayers)
        val wasNewSpawner = completedSet.add(wave.spawnerId)
        if (!wasNewSpawner) return

        val expected = countSpawnersInChamber(chamber)
        if (expected <= 0 || completedSet.size < expected) return

        // Snapshot + clear tracking state BEFORE firing so a listener that
        // triggers further wave activity in the same chamber (e.g. opens a new
        // trial via a reward effect) starts a fresh cycle counter.
        val cycleStart = chamberCycleStartMs.remove(cid) ?: wave.startTime
        val cumulativeParticipants = participantSet.toSet()
        chamberSpawnersCompletedThisCycle.remove(cid)
        chamberParticipantsThisCycle.remove(cid)

        plugin.server.pluginManager.callEvent(
            io.github.darkstarworks.trialChamberPro.api.events.ChamberClearedEvent(
                chamber = chamber,
                participants = cumulativeParticipants,
                durationMs = System.currentTimeMillis() - cycleStart
            )
        )

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info(
                "[ChamberCleared] ${chamber.name}: $expected spawners cleared, " +
                "${cumulativeParticipants.size} participant(s)"
            )
        }
    }

    /**
     * Counts trial-spawner blocks inside [chamber], caching the result.
     * Backed by [scanSpawnerLocations] (chunk tile-entity scan, not a per-block
     * scan). Invalidate via [invalidateChamberSpawnerCaches] when the chamber's
     * bounds change (discovery merge), after `/tcp scan`, or when a spawner
     * block is broken/placed inside the chamber.
     */
    private fun countSpawnersInChamber(
        chamber: io.github.darkstarworks.trialChamberPro.models.Chamber
    ): Int {
        chamberSpawnerCountCache[chamber.id]?.let { return it }
        val locations = scanSpawnerLocations(chamber) ?: return 0
        chamberSpawnerCountCache[chamber.id] = locations.size
        return locations.size
    }

    /**
     * Scans [chamber] for trial-spawner blocks by iterating each chunk's tile
     * entities — O(chunks × tile-entities) instead of the previous
     * O(chamber volume) per-block scan, which on a merged discovery chamber
     * near the 1.5M-block cap meant millions of `getBlockAt` calls on the
     * region thread (a multi-second freeze on first wave completion).
     * Returns null when the world is gone or the scan fails.
     */
    private fun scanSpawnerLocations(
        chamber: io.github.darkstarworks.trialChamberPro.models.Chamber
    ): List<Location>? {
        val world = chamber.getWorld() ?: return null
        val out = mutableListOf<Location>()
        try {
            for (cx in (chamber.minX shr 4)..(chamber.maxX shr 4)) {
                for (cz in (chamber.minZ shr 4)..(chamber.maxZ shr 4)) {
                    for (state in world.getChunkAt(cx, cz).tileEntities) {
                        if (state.type != Material.TRIAL_SPAWNER) continue
                        if (state.x in chamber.minX..chamber.maxX &&
                            state.y in chamber.minY..chamber.maxY &&
                            state.z in chamber.minZ..chamber.maxZ
                        ) {
                            out += Location(world, state.x.toDouble(), state.y.toDouble(), state.z.toDouble())
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            plugin.logger.warning(
                "[SpawnerScan] Failed to scan spawners in ${chamber.name}: ${e.message}"
            )
            return null
        }
        return out
    }

    /**
     * Drops the cached spawner count + locations for [chamberId] so the next
     * read re-scans. Call when the chamber's spawner geometry may have changed:
     * bounds updates (discovery merges), `/tcp scan`, or a trial-spawner block
     * broken/placed inside the chamber. Without this, [ChamberClearedEvent]'s
     * all-spawners-complete threshold goes stale and fires early or never.
     */
    fun invalidateChamberSpawnerCaches(chamberId: Int) {
        chamberSpawnerCountCache.remove(chamberId)
        chamberSpawnerLocationsCache.remove(chamberId)
    }

    /**
     * Drops Trial Keys / Ominous Trial Keys for participants when a wave was driven by a
     * non-vanilla [io.github.darkstarworks.trialChamberPro.providers.TrialMobProvider].
     *
     * Vanilla trial spawners eject keys through their own state machine; when we substituted
     * custom mobs, the spawner enters cooldown but can't find its tracked entities and won't
     * drop anything. This method mirrors vanilla behavior: one key per unique participating
     * player, dropped above the spawner block with a small upward velocity, tagged with the
     * participant's UUID + timestamp so [io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener]
     * can enforce owner-only pickup during the grace window.
     *
     * No-op for vanilla-driven waves. No-op for wild spawners not in a registered chamber —
     * we can't determine the configured provider there and vanilla still handles them correctly.
     */
    private fun maybeDropProviderKeys(wave: WaveState) {
        val chamber = plugin.chamberManager.getCachedChamberAt(wave.location) ?: return
        if (!chamber.hasCustomMobProvider(wave.isOminous)) return
        if (wave.participatingPlayers.isEmpty()) return

        val keyMaterial = if (wave.isOminous) Material.OMINOUS_TRIAL_KEY else Material.TRIAL_KEY
        val dropLoc = wave.location.clone().add(0.5, 1.2, 0.5)
        val participants = wave.participatingPlayers.toList() // snapshot
        val now = System.currentTimeMillis()

        plugin.scheduler.runAtLocation(wave.location, Runnable {
            val world = dropLoc.world ?: return@Runnable
            try {
                participants.forEach { uuid ->
                    // Fire pre-drop event; listeners may suppress an individual key.
                    val dropEvent = io.github.darkstarworks.trialChamberPro.api.events.TrialKeyDropEvent(
                        location = dropLoc,
                        keyType = keyMaterial,
                        ownerUuid = uuid
                    )
                    plugin.server.pluginManager.callEvent(dropEvent)
                    if (dropEvent.isCancelled) return@forEach

                    val stack = org.bukkit.inventory.ItemStack(keyMaterial, 1)
                    val itemEntity = world.dropItem(dropLoc, stack)
                    // Small upward pop to approximate vanilla vault/spawner ejection
                    itemEntity.velocity = org.bukkit.util.Vector(
                        (Math.random() - 0.5) * 0.15,
                        0.3,
                        (Math.random() - 0.5) * 0.15
                    )
                    // Tag for owner-only pickup enforcement
                    itemEntity.persistentDataContainer.set(
                        io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener.OWNER_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        uuid.toString()
                    )
                    itemEntity.persistentDataContainer.set(
                        io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener.DROPPED_AT_KEY,
                        org.bukkit.persistence.PersistentDataType.LONG,
                        now
                    )
                    // Pickup-hint visual
                    try { itemEntity.owner = uuid } catch (_: Throwable) { /* owner setter unavailable on some forks */ }
                }

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[SpawnerWave] Dropped ${participants.size} ${keyMaterial.name} for provider-driven wave at ${wave.spawnerId}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[SpawnerWave] Failed to drop provider keys: ${e.message}")
            }
        })
    }

    /**
     * Configures cooldown for wild spawners at wave completion.
     * This is the critical timing - must be set BEFORE the spawner transitions to cooldown state.
     * For registered chambers, cooldown is handled by ResetManager during chamber reset.
     *
     * When cooldown is 0, also clears tracked players so the spawner can reactivate for them.
     */
    private fun configureWildSpawnerCooldownAtCompletion(wave: WaveState) {
        val wildCooldownMinutes = plugin.config.getInt("reset.wild-spawner-cooldown-minutes", -1)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[WildSpawner] Wave complete - checking cooldown config: wild-spawner-cooldown-minutes=$wildCooldownMinutes")
        }

        if (wildCooldownMinutes == -1) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Skipping - using vanilla default (config is -1)")
            }
            return
        }

        // Check if this spawner is in a registered chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(wave.location)
        if (chamber != null) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Skipping - spawner is inside registered chamber '${chamber.name}'")
            }
            return
        }

        try {
            val world = wave.location.world ?: return
            val block = world.getBlockAt(wave.location)

            if (block.type != Material.TRIAL_SPAWNER) {
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.warning("[WildSpawner] Block at ${wave.location} is not a trial spawner")
                }
                return
            }

            val state = block.state
            if (state is org.bukkit.block.TrialSpawner) {
                val cooldownTicks = if (wildCooldownMinutes == 0) {
                    1 // Minimum 1 tick to avoid potential issues
                } else {
                    wildCooldownMinutes * 60 * 20 // Convert minutes to ticks
                }

                state.cooldownLength = cooldownTicks

                // For instant reactivation (cooldown 0), also clear tracked players
                // so the spawner will activate again for the same players
                if (wildCooldownMinutes == 0) {
                    val trackedPlayers = state.trackedPlayers.toList() // Copy to avoid CME
                    trackedPlayers.forEach { player ->
                        state.stopTrackingPlayer(player)
                    }
                    // Also clear tracked entities (spawned mobs)
                    val trackedEntities = state.trackedEntities.toList()
                    trackedEntities.forEach { entity ->
                        state.stopTrackingEntity(entity)
                    }

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[WildSpawner] Cleared ${trackedPlayers.size} tracked players " +
                            "and ${trackedEntities.size} tracked entities for instant reactivation")
                    }

                    // Schedule a delayed task to force-reset spawner state after key ejection
                    // This handles copied spawners that have old cooldown values baked in
                    val spawnerLocation = wave.location.clone()
                    plugin.scheduler.runAtLocationLater(spawnerLocation, Runnable {
                        forceResetSpawnerState(spawnerLocation)
                    }, 40L) // 2 seconds - enough time for key ejection
                }

                state.update()

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    val typeStr = if (wave.isOminous) "ominous" else "normal"
                    plugin.logger.info("[WildSpawner] Set $typeStr spawner cooldown at wave completion: " +
                        "${wave.location.blockX},${wave.location.blockY},${wave.location.blockZ} " +
                        "cooldown=${wildCooldownMinutes}min (${cooldownTicks} ticks)")
                }
            }
        } catch (e: Exception) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[WildSpawner] Failed to configure cooldown at completion: ${e.message}")
            }
        }
    }

    /**
     * Forces a trial spawner to reset from cooldown state to waiting_for_players.
     * Used for instant reactivation when wild-spawner-cooldown-minutes is 0.
     * This handles copied spawners that have old cooldown values baked into their NBT.
     */
    private fun forceResetSpawnerState(location: Location) {
        try {
            val world = location.world ?: return
            val block = world.getBlockAt(location)

            if (block.type != Material.TRIAL_SPAWNER) return

            val blockData = block.blockData
            val blockDataString = blockData.asString

            // Check if spawner is in cooldown state
            if (!blockDataString.contains("trial_spawner_state=cooldown")) {
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[WildSpawner] Spawner not in cooldown state, no force-reset needed")
                }
                return
            }

            // Replace cooldown state with waiting_for_players
            val newBlockDataString = blockDataString.replace(
                "trial_spawner_state=cooldown",
                "trial_spawner_state=waiting_for_players"
            )

            // Create new block data from the modified string
            val newBlockData = plugin.server.createBlockData(newBlockDataString)
            block.setBlockData(newBlockData, false)

            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Force-reset spawner state to waiting_for_players at " +
                    "${location.blockX},${location.blockY},${location.blockZ}")
            }
        } catch (e: Exception) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[WildSpawner] Failed to force-reset spawner state: ${e.message}")
            }
        }
    }

    /**
     * Creates a boss bar for a wave.
     */
    private fun createBossBar(wave: WaveState): BossBar {
        val color = if (wave.isOminous) BossBar.Color.PURPLE else BossBar.Color.YELLOW
        val messageKey = if (wave.isOminous) "spawner-wave-boss-bar-ominous" else "spawner-wave-boss-bar-normal"
        val title = getMessageComponent(messageKey, "wave" to wave.waveNumber)

        return BossBar.bossBar(
            title,
            0f,
            color,
            BossBar.Overlay.PROGRESS
        )
    }

    /**
     * Updates the boss bar display.
     */
    private fun updateBossBar(wave: WaveState) {
        val bar = wave.bossBar ?: return

        val progress = wave.getProgress()
        bar.progress(progress)

        // Update title with kill count
        val typeStr = plugin.getRawMessage(
            if (wave.isOminous) "wave-boss-type-ominous" else "wave-boss-type-normal",
            if (wave.isOminous) "Ominous Trial" else "Trial Spawner"
        )
        bar.name(getMessageComponent("spawner-wave-boss-bar-progress",
            "type" to typeStr,
            "killed" to wave.mobsKilled.get(),
            "total" to wave.totalMobsExpected.get()
        ))
    }

    /**
     * Adds a player as a boss bar viewer.
     */
    private fun addPlayerToBossBar(player: Player, wave: WaveState) {
        val bar = wave.bossBar ?: return

        // Check if already tracked to avoid redundant operations
        val playerSpawners = playerBossBars.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }
        if (playerSpawners.contains(wave.spawnerId)) {
            return // Already viewing this bar
        }

        bar.addViewer(player)
        playerSpawners.add(wave.spawnerId)
    }

    /**
     * Removes the boss bar from all viewers.
     */
    private fun removeBossBar(wave: WaveState) {
        // Always attempt glow cleanup, even if there's no boss bar to remove
        removeGlowDisplay(wave)

        val bar = wave.bossBar ?: return

        // Remove from all players
        wave.participatingPlayers.forEach { playerUUID ->
            plugin.server.getPlayer(playerUUID)?.let { player ->
                bar.removeViewer(player)
            }
            playerBossBars[playerUUID]?.remove(wave.spawnerId)
        }

        wave.bossBar = null
    }

    /**
     * Spawns an invisible Shulker at the spawner with the GLOWING effect applied,
     * producing a colored 1×1×1 cube outline that is visible through walls.
     *
     * **Why a Shulker and not Interaction / Display:** Interaction entities are
     * explicitly *immune* to the glow effect in vanilla — they have no renderable
     * model for the outline pass to draw around. (Confirmed against the Minecraft
     * Wiki: Withers, ender dragons, dropped items, display entities, and
     * **Interaction entities are immune to Glowing**.) The pre-v1.5.4 implementation
     * used an Interaction, which is why the feature silently rendered nothing on
     * every client since v1.2.27. A Shulker's shell is a perfect 1×1×1 cube; with
     * INVISIBILITY applied the shell hides but the glow outline remains, giving the
     * intended block-shaped marker.
     *
     * Configured to be inert: AI off, silent, non-persistent. Tagged with
     * [glowMarkerKey] in PDC so mob-cap and anti-cheat plugins can identify it as
     * a TCP overlay and exclude it from monster counts. Opt-in via
     * `spawner-waves.glow-active-spawners`; colors configurable per-type.
     */
    private fun spawnGlowDisplay(wave: WaveState) {
        if (!plugin.config.getBoolean("spawner-waves.glow-active-spawners", false)) return
        val world = wave.location.world ?: return
        // Align the shulker's 1×1×1 shell with the spawner block: centre on X/Z,
        // but feet on the block floor (Y offset 0). A shulker's bounding box starts
        // at its feet, so a +0.5 Y push the outline up ~a block above the spawner.
        val center = wave.location.clone().add(0.5, 0.0, 0.5)

        plugin.scheduler.runAtLocation(wave.location, Runnable {
            try {
                val colorHex = if (wave.isOminous) {
                    plugin.config.getString("spawner-waves.glow-color-ominous", "#A020F0") ?: "#A020F0"
                } else {
                    plugin.config.getString("spawner-waves.glow-color-normal", "#FFFF55") ?: "#FFFF55"
                }
                val color = parseGlowColor(colorHex)

                // Pre-resolve the invisibility effect type via Registry so we use
                // the modern path (the static PotionEffectType.INVISIBILITY field
                // is deprecated on 1.21+).
                val invisType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                    org.bukkit.NamespacedKey.minecraft("invisibility")
                )

                val entity = world.spawn(center, org.bukkit.entity.Shulker::class.java) { s ->
                    s.setAI(false)
                    s.isSilent = true
                    s.isPersistent = false
                    s.isGlowing = true
                    // Invulnerable: a damageable marker can be killed (removing the
                    // glow mid-wave) and a dead shulker rolls vanilla loot — free
                    // shulker shells farmable at every glowing spawner. Collidable
                    // off so it doesn't intercept arrows or push entities.
                    s.isInvulnerable = true
                    s.isCollidable = false
                    // Tag for mob-cap / anti-cheat allow-listing.
                    s.persistentDataContainer.set(
                        glowMarkerKey,
                        org.bukkit.persistence.PersistentDataType.BYTE,
                        1
                    )
                    if (invisType != null) {
                        // Infinite-duration invisibility so the shulker shell never
                        // re-appears; particles + icon off to keep the chamber visually clean.
                        s.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                invisType,
                                org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                                0,
                                false,  // ambient
                                false,  // particles
                                false   // icon
                            )
                        )
                    }
                    if (color != null) {
                        try {
                            // Reflective so we don't hard-bind to a specific Paper API revision.
                            // Falls back to the default team-less white outline on older forks.
                            s.javaClass.getMethod("setGlowColorOverride", org.bukkit.Color::class.java)
                                .invoke(s, color)
                        } catch (_: Throwable) {
                            // Older fork / API: leave the outline white. Better than nothing.
                        }
                    }
                }
                wave.glowEntityId = entity.uniqueId

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[SpawnerWave] Spawned glow shulker ${entity.uniqueId} at ${wave.spawnerId}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[SpawnerWave] Failed to spawn glow entity: ${e.message}")
            }
        })

        // v1.5.4: chamber-remaining mode also lights up every uncleared sister-spawner
        // in the same chamber, so players see at a glance which spawners are still
        // pending in the current cycle. Skipped in default wave-active mode.
        if (plugin.config.getString("spawner-waves.glow-mode", "wave-active") == "chamber-remaining") {
            refreshChamberRemainingGlows(wave)
        }
    }

    /**
     * Removes the glow shulker previously spawned for this wave, if any.
     * Safe to call multiple times — no-op once the entity id is cleared.
     *
     * Also clears the chamber-remaining-mode standalone-glow record for this
     * spawner so the next wave-start can re-create it cleanly.
     */
    private fun removeGlowDisplay(wave: WaveState) {
        // Drop the standalone-glow record for this spawner if present (no entity
        // to remove — the wave-attached entity is the one that was visible).
        plugin.chamberManager.getCachedChamberAt(wave.location)?.let { chamber ->
            chamberRemainingGlows[chamber.id]?.remove(wave.spawnerId)
        }
        val id = wave.glowEntityId ?: return
        wave.glowEntityId = null
        val world = wave.location.world ?: return
        plugin.scheduler.runAtLocation(wave.location, Runnable {
            try {
                world.getEntity(id)?.remove()
            } catch (_: Throwable) {
                // Entity may already be unloaded/removed; harmless
            }
        })
    }

    /**
     * Spawns a standalone glow shulker at a spawner location for chamber-remaining
     * mode. Same entity setup as [spawnGlowDisplay] but not attached to any
     * [WaveState] — tracked in [chamberRemainingGlows] for later cleanup.
     */
    private fun spawnStandaloneGlow(spawnerLocation: Location, isOminous: Boolean, chamberId: Int) {
        val world = spawnerLocation.world ?: return
        // Feet on block floor (Y offset 0) so the 1×1×1 shell outlines the spawner
        // block exactly — see spawnGlowDisplay for why +0.5 Y reads as a block too high.
        val center = spawnerLocation.clone().add(0.5, 0.0, 0.5)
        val spawnerKey = getSpawnerKey(spawnerLocation)

        plugin.scheduler.runAtLocation(spawnerLocation, Runnable {
            try {
                val colorHex = if (isOminous) {
                    plugin.config.getString("spawner-waves.glow-color-ominous", "#A020F0") ?: "#A020F0"
                } else {
                    plugin.config.getString("spawner-waves.glow-color-normal", "#FFFF55") ?: "#FFFF55"
                }
                val color = parseGlowColor(colorHex)
                val invisType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                    org.bukkit.NamespacedKey.minecraft("invisibility")
                )

                val entity = world.spawn(center, org.bukkit.entity.Shulker::class.java) { s ->
                    s.setAI(false)
                    s.isSilent = true
                    s.isPersistent = false
                    s.isGlowing = true
                    // See spawnGlowDisplay: marker must be unkillable + non-colliding.
                    s.isInvulnerable = true
                    s.isCollidable = false
                    s.persistentDataContainer.set(
                        glowMarkerKey,
                        org.bukkit.persistence.PersistentDataType.BYTE,
                        1
                    )
                    if (invisType != null) {
                        s.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                invisType,
                                org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                                0, false, false, false
                            )
                        )
                    }
                    if (color != null) {
                        try {
                            s.javaClass.getMethod("setGlowColorOverride", org.bukkit.Color::class.java)
                                .invoke(s, color)
                        } catch (_: Throwable) { /* white fallback */ }
                    }
                }
                chamberRemainingGlows.computeIfAbsent(chamberId) { ConcurrentHashMap() }[spawnerKey] = entity.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[SpawnerWave] Failed to spawn standalone glow at $spawnerKey: ${e.message}")
            }
        })
    }

    /**
     * Chamber-remaining mode refresh: for every uncleared spawner in [triggerWave]'s
     * chamber, ensure a glow is up. Uncleared = not in [chamberSpawnersCompletedThisCycle]
     * AND not the wave's own spawner (which already has its own wave-attached glow).
     * Already-glowed standalones are left alone — re-spawning would just churn entities.
     */
    private fun refreshChamberRemainingGlows(triggerWave: WaveState) {
        val chamber = plugin.chamberManager.getCachedChamberAt(triggerWave.location)
            ?.takeIf { !it.isPaused } ?: return
        val locations = spawnerLocationsInChamber(chamber)
        if (locations.isEmpty()) return

        val completed = chamberSpawnersCompletedThisCycle[chamber.id] ?: emptySet()
        val existing = chamberRemainingGlows[chamber.id] ?: emptyMap()
        for (loc in locations) {
            val key = getSpawnerKey(loc)
            if (key == triggerWave.spawnerId) continue        // covered by wave-attached glow
            if (key in completed) continue                     // already done this cycle
            if (key in existing) continue                      // already glowing
            spawnStandaloneGlow(loc, triggerWave.isOminous, chamber.id)
        }
    }

    /**
     * Returns every trial-spawner block location inside [chamber]. Lazy block-scan
     * cached for the chamber's lifetime — resets preserve geometry, so the cache
     * survives across reset cycles. Mirrors the pattern used by [countSpawnersInChamber].
     */
    private fun spawnerLocationsInChamber(
        chamber: io.github.darkstarworks.trialChamberPro.models.Chamber
    ): List<Location> {
        chamberSpawnerLocationsCache[chamber.id]?.let { return it }
        val out = scanSpawnerLocations(chamber) ?: return emptyList()
        chamberSpawnerLocationsCache[chamber.id] = out
        return out
    }

    /**
     * Removes every standalone chamber-remaining glow for [chamberId]. Called on
     * chamber reset (after [clearWavesInChamber] has dropped the wave-attached
     * glows) and on plugin shutdown.
     */
    private fun clearChamberRemainingGlows(chamberId: Int) {
        val entries = chamberRemainingGlows.remove(chamberId) ?: return
        for ((_, entityId) in entries) {
            // Best-effort removal across any world the entity might be in.
            for (world in plugin.server.worlds) {
                val ent = world.getEntity(entityId) ?: continue
                plugin.scheduler.runAtEntity(ent, Runnable {
                    try { ent.remove() } catch (_: Throwable) { /* already gone */ }
                })
                break
            }
        }
    }

    /**
     * Parses a hex color string (e.g., "#FFFF55" or "FFFF55") into a Bukkit Color.
     * Returns null on parse failure so the caller can fall back to the default outline color.
     */
    private fun parseGlowColor(hex: String): org.bukkit.Color? {
        return try {
            val clean = hex.trim().removePrefix("#")
            org.bukkit.Color.fromRGB(clean.toInt(16))
        } catch (_: Exception) {
            plugin.logger.warning("[SpawnerWave] Invalid glow color '$hex' — expected hex like #FFFF55")
            null
        }
    }

    /**
     * Formats duration in seconds to a readable string.
     */
    private fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    /**
     * Converts a message key with placeholders to a Component for boss bars.
     * Uses the messages.yml translations without the plugin prefix.
     */
    /**
     * Boss-bar message lookup. v1.4.0: delegates to the plugin's MM-aware
     * `getMessageComponent` so boss bars get full MiniMessage fidelity
     * (gradients, hover, etc.) instead of being routed through the legacy
     * section-string path.
     */
    private fun getMessageComponent(key: String, vararg replacements: Pair<String, Any?>): Component {
        return plugin.getMessageComponent(key, *replacements)
    }

    /**
     * Cleans up all active waves (called on disable).
     */
    fun shutdown() {
        activeWaves.values.forEach { wave ->
            removeBossBar(wave)
        }
        activeWaves.clear()
        mobToSpawner.clear()
        playerBossBars.clear()
        chamberSpawnersCompletedThisCycle.clear()
        chamberParticipantsThisCycle.clear()
        chamberCycleStartMs.clear()
        chamberSpawnerCountCache.clear()
        // v1.5.4: drop any chamber-remaining standalone glow entities tied to active chambers.
        chamberRemainingGlows.keys.toList().forEach { clearChamberRemainingGlows(it) }
        chamberSpawnerLocationsCache.clear()
    }

    /**
     * Gets statistics about active waves.
     */
    fun getActiveWaveCount(): Int = activeWaves.count { !it.value.completed }

    /**
     * Gets all active waves for a chamber.
     */
    fun getWavesInChamber(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber): List<WaveState> {
        return activeWaves.values.filter { wave ->
            !wave.completed && chamber.contains(wave.location)
        }
    }
}

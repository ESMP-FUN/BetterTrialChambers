package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.api.events.ChamberResetCompleteEvent
import io.github.darkstarworks.trialChamberPro.api.events.ChamberResetEvent
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.utils.BlockRestorer
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages automatic chamber resets with warnings, player teleportation, and snapshot restoration.
 */
class ResetManager(private val plugin: TrialChamberPro) {

    private val resetScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scheduledResets = ConcurrentHashMap<Int, Job>()
    private val warningJobs = ConcurrentHashMap<Int, MutableList<Job>>()

    // Chambers with a reset currently running/queued — guards against the same
    // chamber being reset twice concurrently (e.g. the 60s scheduler re-firing).
    private val inProgress = ConcurrentHashMap.newKeySet<Int>()

    // Chambers that are due for reset but awaiting operator confirmation
    // (when global.reset-require-confirmation is on). Confirmed via /tcp reset confirm.
    private val pendingResets = ConcurrentHashMap.newKeySet<Int>()

    // Throttles concurrent resets so a wave of due chambers can't all restore at
    // once and crater TPS. Permit count is read once (config reload needs a restart).
    private val resetGate by lazy {
        Semaphore(plugin.config.getInt("global.max-concurrent-resets", 1).coerceAtLeast(1))
    }
    private val resetStaggerMs: Long
        get() = plugin.config.getLong("global.reset-stagger-seconds", 5L).coerceAtLeast(0L) * 1000L

    private companion object {
        // Solid blocks you still shouldn't be dropped onto.
        val HAZARD_BLOCKS = setOf(
            Material.LAVA, Material.MAGMA_BLOCK, Material.POINTED_DRIPSTONE, Material.CACTUS,
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW,
        )
    }

    /**
     * Starts monitoring and scheduling resets for all chambers.
     */
    fun startResetScheduler() {
        resetScope.launch {
            while (isActive) {
                try {
                    val before = pendingResets.size
                    val chambers = plugin.chamberManager.getAllChambers()
                    chambers.forEach { chamber ->
                        scheduleResetIfNeeded(chamber)
                    }
                    val newlyPending = pendingResets.size - before
                    if (newlyPending > 0) {
                        plugin.logger.info(
                            "$newlyPending chamber(s) due for reset — ${pendingResets.size} total awaiting confirmation. " +
                                "Use /tcp reset pending to list, /tcp reset confirm all to release."
                        )
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Error in reset scheduler: ${e.message}")
                }

                // Check every minute
                delay(60000)
            }
        }

        plugin.logger.info("Reset scheduler started")
    }

    /**
     * Schedules a reset for a chamber if it's due.
     * If resetInterval is <= 0, automatic resets are disabled for this chamber.
     */
    private suspend fun scheduleResetIfNeeded(chamber: Chamber) {
        // Skip if already scheduled, currently resetting, or awaiting confirmation
        if (scheduledResets.containsKey(chamber.id)) return
        if (chamber.id in inProgress) return
        if (chamber.id in pendingResets) return

        // Skip paused chambers — they have no active behavior including auto-resets
        if (chamber.isPaused) return

        // Skip paused chambers — they have no active behavior including auto-resets
        if (chamber.isPaused) return

        // Skip if automatic resets are disabled (resetInterval <= 0)
        if (chamber.resetInterval <= 0) {
            plugin.logger.fine("Automatic resets disabled for chamber ${chamber.name} (interval: ${chamber.resetInterval})")
            return
        }

        val lastReset = chamber.lastReset ?: chamber.createdAt
        val resetIntervalMs = chamber.resetInterval * 1000
        val nextResetTime = lastReset + resetIntervalMs
        val now = System.currentTimeMillis()

        if (now >= nextResetTime) {
            // Due now — enqueue for confirmation or launch through the throttle.
            triggerScheduledReset(chamber)
        } else {
            // Schedule future reset
            val delayMs = nextResetTime - now
            val resetJob = resetScope.launch {
                // Schedule warnings
                scheduleWarnings(chamber, delayMs)

                delay(delayMs)
                // Remove ourselves BEFORE resetting so resetChamber's "cancel any
                // pending reset" can never cancel the coroutine we're running in.
                scheduledResets.remove(chamber.id)
                triggerScheduledReset(chamber)
            }

            scheduledResets[chamber.id] = resetJob
        }
    }

    /**
     * Fire (or queue) a scheduled reset. When operator confirmation is required
     * the chamber is parked in [pendingResets] and admins are notified; otherwise
     * it's launched immediately through the concurrency/stagger throttle.
     */
    private fun triggerScheduledReset(chamber: Chamber) {
        if (plugin.config.getBoolean("global.reset-require-confirmation", false)) {
            if (pendingResets.add(chamber.id)) {
                notifyAdminsPending(chamber)
            }
        } else {
            resetScope.launch { resetChamber(chamber, reason = ChamberResetEvent.Reason.SCHEDULED) }
        }
    }

    private fun notifyAdminsPending(chamber: Chamber) {
        plugin.scheduler.runTask(Runnable {
            Bukkit.getOnlinePlayers()
                .filter { it.hasPermission("tcp.admin.reset") }
                .forEach { p ->
                    p.sendRichMessage(
                        "<gold>[TCP] <yellow>${chamber.name}</yellow> is ready to reset — " +
                            "<click:run_command:'/tcp reset confirm ${chamber.name}'><green>[confirm]</green></click> " +
                            "<click:run_command:'/tcp reset pending'><aqua>[list all]</aqua></click>"
                    )
                }
        })
    }

    /** Names of chambers currently awaiting reset confirmation. */
    fun pendingResetNames(): List<String> =
        pendingResets.mapNotNull { id -> plugin.chamberManager.getCachedChamberById(id)?.name }.sorted()

    /** Confirm a single pending reset; returns false if the chamber wasn't pending. */
    fun confirmReset(chamber: Chamber): Boolean {
        if (!pendingResets.remove(chamber.id)) return false
        resetScope.launch { resetChamber(chamber, reason = ChamberResetEvent.Reason.SCHEDULED) }
        return true
    }

    /** Confirm every pending reset; they queue through the throttle (staggered). Returns the count. */
    fun confirmAllResets(): Int {
        var n = 0
        pendingResets.toList().forEach { id ->
            if (pendingResets.remove(id)) {
                val chamber = plugin.chamberManager.getCachedChamberById(id) ?: return@forEach
                resetScope.launch { resetChamber(chamber, reason = ChamberResetEvent.Reason.SCHEDULED) }
                n++
            }
        }
        return n
    }

    /**
     * Schedules warning messages before reset.
     */
    private fun scheduleWarnings(chamber: Chamber, delayMs: Long) {
        val warningTimes = plugin.config.getIntegerList("global.reset-warning-times")
            .map { it * 1000L } // Convert to milliseconds

        val warnings = mutableListOf<Job>()

        warningTimes.forEach { warningTimeMs ->
            if (warningTimeMs < delayMs) {
                val warningDelay = delayMs - warningTimeMs
                val warningJob = resetScope.launch {
                    delay(warningDelay)
                    sendResetWarning(chamber, warningTimeMs / 1000)
                }
                warnings.add(warningJob)
            }
        }

        warningJobs[chamber.id] = warnings
    }

    /**
     * Sends a reset warning to all players in the chamber.
     */
    private fun sendResetWarning(chamber: Chamber, secondsRemaining: Long) {
        val timeString = MessageUtil.formatTimeSeconds(secondsRemaining)
        val message = plugin.getMessageComponent("chamber-reset-warning",
            "chamber" to chamber.name,
            "time" to timeString
        )

        chamber.getPlayersInside().forEach { player ->
            player.sendMessage(message)
        }
    }

    /**
     * Resets a chamber completely.
     *
     * @param chamber The chamber to reset
     * @param initiatingPlayer Optional player who initiated the reset (for WorldEdit undo support)
     */
    suspend fun resetChamber(
        chamber: Chamber,
        initiatingPlayer: Player? = null,
        reason: ChamberResetEvent.Reason = ChamberResetEvent.Reason.MANUAL
    ): Boolean {
        if (!inProgress.add(chamber.id)) {
            // Visible (not fine) so admins running /tcp reset see WHY it failed when the
            // previous attempt is still mid-restore (or got stuck and never released the slot).
            plugin.logger.warning(
                "Reset already in progress for chamber '${chamber.name}'; ignoring duplicate request. " +
                    "If this persists after a minute or two, restart the server to clear stuck state."
            )
            return false
        }
        return try {
            resetGate.withPermit {
                doReset(chamber, initiatingPlayer, reason).also {
                    // Space resets out so a backlog drains gradually instead of all at once.
                    if (resetStaggerMs > 0L) delay(resetStaggerMs)
                }
            }
        } finally {
            inProgress.remove(chamber.id)
        }
    }

    private suspend fun doReset(
        chamber: Chamber,
        initiatingPlayer: Player?,
        reason: ChamberResetEvent.Reason
    ): Boolean = withContext(Dispatchers.IO) {
        plugin.logger.info("Resetting chamber: ${chamber.name}")

        // Fire pre-reset event; listeners may abort.
        val preEvent = ChamberResetEvent(chamber, reason, initiatingPlayer)
        plugin.server.pluginManager.callEvent(preEvent)
        if (preEvent.isCancelled) {
            plugin.logger.info("Chamber reset cancelled by listener: ${chamber.name}")
            return@withContext false
        }

        val resetStart = System.currentTimeMillis()
        var blocksRestored = 0

        try {
            // Cancel any OTHER pending reset/warnings for this chamber. A scheduled
            // job removes itself from the map before calling us (see scheduleResetIfNeeded),
            // so this can never cancel the coroutine we're currently running in.
            scheduledResets.remove(chamber.id)?.cancel()
            warningJobs.remove(chamber.id)?.forEach { it.cancel() }

            // Step 0: Strip Trial/Bad Omen from players inside so it doesn't carry into the next
            // cycle. Independent of teleport — works even when players stay in the chamber.
            if (plugin.config.getBoolean("reset.clear-trial-omen-effect", true)) {
                clearTrialOmen(chamber)
            }

            // Step 1: Teleport players out
            if (plugin.config.getBoolean("global.teleport-players-on-reset", true)) {
                teleportPlayersOut(chamber)
            }

            // Step 2: Clear entities
            clearEntities(chamber)

            // Step 2b: Clear any active spawner waves (removes lingering boss bars)
            plugin.spawnerWaveManager.clearWavesInChamber(chamber)

            // Step 3: Restore from snapshot
            // v1.4.0: ChamberResetEvent.snapshotOverride lets listeners substitute
            // a different snapshot for this reset cycle (premium / schematic
            // injection use case). Falls back to the on-disk snapshot if the
            // override fails to load.
            val overrideBytes = preEvent.snapshotOverride
            if (overrideBytes != null) {
                val overrideBlocks = plugin.snapshotManager.loadSnapshotFromBytes(overrideBytes, chamber.name)
                if (overrideBlocks != null) {
                    restoreFromSnapshotBlocks(chamber, overrideBlocks, initiatingPlayer)
                    blocksRestored = overrideBlocks.size
                } else {
                    plugin.logger.warning(
                        "Snapshot override for chamber ${chamber.name} failed to load — falling back to on-disk snapshot"
                    )
                    val snapshotFile = chamber.getSnapshotFile()
                    if (snapshotFile != null && snapshotFile.exists()) {
                        restoreFromSnapshot(chamber, snapshotFile, initiatingPlayer)
                        blocksRestored = chamber.getVolume()
                    } else {
                        plugin.logger.warning("No on-disk snapshot found for chamber ${chamber.name}, skipping block restoration")
                    }
                }
            } else {
                val snapshotFile = chamber.getSnapshotFile()
                if (snapshotFile != null && snapshotFile.exists()) {
                    // restoreFromSnapshot now suspends until every region-thread batch
                    // finishes, so spawner reset (Step 4) and vault cooldown clear
                    // (Step 6) are guaranteed to act on the restored blocks.
                    restoreFromSnapshot(chamber, snapshotFile, initiatingPlayer)
                    blocksRestored = chamber.getVolume()
                } else {
                    plugin.logger.warning("No snapshot found for chamber ${chamber.name}, skipping block restoration")
                }
            }

            // Step 4: Reset trial spawners (clear tracked players so they drop keys again)
            // This runs AFTER block restoration completes to apply config-based cooldown
            if (plugin.config.getBoolean("reset.reset-trial-spawners", true)) {
                resetTrialSpawners(chamber)
            }

            // Step 5: Update last reset time and clear the auto-pause destruction counter
            // so a reset chamber starts counting from zero again.
            val now = System.currentTimeMillis()
            plugin.chamberManager.updateLastReset(chamber.id, now)
            plugin.chamberManager.resetDestructionCounter(chamber.id)

            // Step 6: Optionally reset vault cooldowns (defaults to true for vanilla behavior)
            if (plugin.config.getBoolean("reset.reset-vault-cooldowns", true)) {
                val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)
                vaults.forEach { vault ->
                    plugin.vaultManager.resetAllCooldowns(vault.id)
                }
            }

            // v1.5.7: per-player container-loot copies reset with the chamber —
            // everyone gets fresh chest loot next cycle. No-op when unused.
            plugin.containerLootManager.clearChamber(chamber.id)

            // Send completion message if broadcasts are enabled globally and for this chamber
            val globalAlerts = plugin.config.getBoolean("global.reset-complete-alert", true)
            if (globalAlerts && chamber.broadcastResetComplete) {
                plugin.scheduler.runTask(Runnable {
                    val message = plugin.getMessageComponent("chamber-reset-complete")
                    Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
                })
            }

            plugin.logger.info("Chamber ${chamber.name} reset successfully")

            // Fire post-reset event for downstream consumers.
            val durationMs = System.currentTimeMillis() - resetStart
            plugin.server.pluginManager.callEvent(
                ChamberResetCompleteEvent(chamber, durationMs, blocksRestored)
            )
            true

        } catch (e: CancellationException) {
            // Don't swallow coroutine cancellation (e.g. plugin disable) as a "failure".
            plugin.logger.warning("Chamber reset for ${chamber.name} was cancelled before completing (likely shutdown or a pre-empting reset).")
            throw e
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reset chamber ${chamber.name}: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes Trial Omen and Bad Omen from players currently inside [chamber]. Fire-and-forget,
     * Folia-correct (per-entity scheduling). Uses the registry lookup so it degrades cleanly on
     * server versions without the effect rather than referencing a constant by name.
     */
    private fun clearTrialOmen(chamber: Chamber) {
        val trialOmen = org.bukkit.Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft("trial_omen"))
        val badOmen = org.bukkit.Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.minecraft("bad_omen"))
        if (trialOmen == null && badOmen == null) return
        plugin.scheduler.runTask(Runnable {
            chamber.getPlayersInside().forEach { player ->
                plugin.scheduler.runAtEntity(player, Runnable {
                    trialOmen?.let { player.removePotionEffect(it) }
                    badOmen?.let { player.removePotionEffect(it) }
                })
            }
        })
    }

    /**
     * Teleports all players out of the chamber.
     * Folia compatible: Uses entity-based scheduling for player teleportation.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun teleportPlayersOut(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Get players list on global/main thread first
            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runTask(Runnable {
                    try {
                        val players = chamber.getPlayersInside()
                        if (players.isEmpty()) {
                            continuation.resume(Unit) {}
                            return@Runnable
                        }

                        val teleportMode = plugin.config.getString("global.teleport-location", "EXIT_POINT")
                        var remaining = players.size

                        // Teleport each player on their own region thread (Folia compatible)
                        players.forEach { player ->
                            plugin.scheduler.runAtEntity(player, Runnable {
                                val destination = when (teleportMode) {
                                    "EXIT_POINT" -> chamber.getExitLocation() ?: getOutsideLocation(chamber, player.location)
                                    "OUTSIDE_BOUNDS" -> getOutsideLocation(chamber, player.location)
                                    "WORLD_SPAWN" -> player.world.spawnLocation
                                    else -> chamber.getExitLocation() ?: player.world.spawnLocation
                                }

                                // teleportAsync is required on Folia/Luminol; sync teleport throws in region threads
                                player.teleportAsync(destination).thenRun {
                                    player.sendMessage(plugin.getMessageComponent("teleported-to-exit", "chamber" to chamber.name))
                                    synchronized(this) {
                                        remaining--
                                        if (remaining == 0) {
                                            continuation.resume(Unit) {}
                                        }
                                    }
                                }
                            }, Runnable {
                                // Player retired/removed - still count as done
                                synchronized(this) {
                                    remaining--
                                    if (remaining == 0) {
                                        continuation.resume(Unit) {}
                                    }
                                }
                            })
                        }
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }
        }
    }

    /**
     * Finds a safe standing location just OUTSIDE the chamber's horizontal bounds.
     *
     * The old version scanned the chamber's CENTRE column (inside the structure)
     * and returned `y+2` above the first solid block without checking the
     * destination was actually open — so players were teleported into the
     * ceiling/walls and suffocated. This scans columns a couple of blocks beyond
     * each edge and requires solid ground with two passable, non-liquid cells
     * above it before accepting a spot.
     */
    private fun getOutsideLocation(chamber: Chamber, currentLocation: Location): Location {
        val world = chamber.getWorld() ?: return currentLocation
        val midX = (chamber.minX + chamber.maxX) / 2
        val midZ = (chamber.minZ + chamber.maxZ) / 2
        val startY = (chamber.maxY + 6).coerceAtMost(world.maxHeight - 2)

        val columns = listOf(
            chamber.maxX + 2 to midZ,
            chamber.minX - 2 to midZ,
            midX to chamber.maxZ + 2,
            midX to chamber.minZ - 2,
        )
        for ((x, z) in columns) {
            val feetY = findSafeStandY(world, x, z, startY)
            if (feetY != null) return Location(world, x + 0.5, feetY.toDouble(), z + 0.5)
        }

        plugin.logger.warning("No safe ground found outside chamber ${chamber.name}, using world spawn")
        return world.spawnLocation
    }

    /** Scan column (x,z) downward for solid ground with two passable cells above; returns the feet Y, or null. */
    private fun findSafeStandY(world: org.bukkit.World, x: Int, z: Int, startY: Int): Int? {
        var y = startY
        while (y > world.minHeight + 1) {
            val below = world.getBlockAt(x, y - 1, z)
            val feet = world.getBlockAt(x, y, z)
            val head = world.getBlockAt(x, y + 1, z)
            if (below.type.isSolid && below.type !in HAZARD_BLOCKS &&
                feet.isPassable && !feet.isLiquid && head.isPassable && !head.isLiquid
            ) {
                return y
            }
            y--
        }
        return null
    }

    /**
     * Clears entities from the chamber.
     * Folia compatible: Uses location-based scheduling for entity removal.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun clearEntities(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Use a representative location in the chamber for region scheduling
            val chamberCenter = Location(
                chamber.getWorld(),
                (chamber.minX + chamber.maxX) / 2.0,
                (chamber.minY + chamber.maxY) / 2.0,
                (chamber.minZ + chamber.maxZ) / 2.0
            )

            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runAtLocation(chamberCenter, Runnable {
                    try {
                        val entities = chamber.getEntitiesInside()

                        entities.forEach { entity ->
                            when {
                                entity is Item && plugin.config.getBoolean("reset.clear-ground-items", true) -> {
                                    entity.remove()
                                }
                                entity is LivingEntity && entity !is Player -> {
                                    val shouldRemove = when {
                                        plugin.config.getBoolean("reset.remove-spawner-mobs", true) -> {
                                            // Check if mob is from a spawner (simplified check)
                                            true
                                        }
                                        plugin.config.getBoolean("reset.remove-non-chamber-mobs", false) -> true
                                        else -> false
                                    }

                                    if (shouldRemove) {
                                        entity.remove()
                                    }
                                }
                            }
                        }
                        continuation.resume(Unit) {}
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }
        }
    }

    /**
     * Resets all trial spawners in a chamber.
     * CRITICAL: This clears tracked players so spawners can be reactivated
     * and will drop trial keys again (50% chance per player per vanilla behavior).
     *
     * Folia compatible: Uses location-based scheduling.
     *
     * @param chamber The chamber to reset spawners in
     * @param cooldownMinutesOverride Optional per-chamber cooldown override (null = use global config)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun resetTrialSpawners(chamber: Chamber, cooldownMinutesOverride: Int? = null) {
        withTimeout(10000) {  // 10 second timeout for larger chambers
            val chamberCenter = Location(
                chamber.getWorld(),
                (chamber.minX + chamber.maxX) / 2.0,
                (chamber.minY + chamber.maxY) / 2.0,
                (chamber.minZ + chamber.maxZ) / 2.0
            )

            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runAtLocation(chamberCenter, Runnable {
                    try {
                        val world = chamber.getWorld() ?: run {
                            continuation.resume(Unit) {}
                            return@Runnable
                        }

                        var resetCount = 0
                        val resetOminous = plugin.config.getBoolean("reset.reset-ominous-spawners", true)

                        // Get cooldown setting: per-chamber override > global config > vanilla default (-1)
                        // Sentinel -2 = "match chamber reset interval" (clamped to >= 0 minutes).
                        val rawCooldown = cooldownMinutesOverride
                            ?: chamber.spawnerCooldownMinutes
                            ?: plugin.config.getInt("reset.spawner-cooldown-minutes", -1)
                        val cooldownMinutes = if (rawCooldown == -2) {
                            (chamber.resetInterval / 60L).toInt().coerceAtLeast(0)
                        } else rawCooldown

                        val verboseLogging = plugin.config.getBoolean("debug.verbose-logging", false)
                        if (verboseLogging) {
                            plugin.logger.info("[SpawnerReset] Chamber: ${chamber.name}, cooldownMinutes: $cooldownMinutes " +
                                "(override: $cooldownMinutesOverride, perChamber: ${chamber.spawnerCooldownMinutes}, " +
                                "config: ${plugin.config.getInt("reset.spawner-cooldown-minutes", -1)})")
                        }

                        // Scan chamber for trial spawners
                        for (x in chamber.minX..chamber.maxX) {
                            for (y in chamber.minY..chamber.maxY) {
                                for (z in chamber.minZ..chamber.maxZ) {
                                    val block = world.getBlockAt(x, y, z)
                                    if (block.type == Material.TRIAL_SPAWNER) {
                                        val state = block.state
                                        if (state is org.bukkit.block.TrialSpawner) {
                                            val oldCooldown = state.cooldownLength

                                            // Clear tracked players - KEY fix for trial key drops!
                                            state.trackedPlayers.forEach { player ->
                                                state.stopTrackingPlayer(player)
                                            }

                                            // Clear tracked entities (spawned mobs)
                                            state.trackedEntities.forEach { entity ->
                                                state.stopTrackingEntity(entity)
                                            }

                                            // Optionally reset ominous spawners back to normal
                                            if (resetOminous && state.isOminous) {
                                                state.isOminous = false
                                            }

                                            // Apply custom cooldown if configured
                                            // -1 = vanilla default (don't change)
                                            // 0 = no cooldown (immediate reactivation)
                                            // >0 = custom cooldown in minutes
                                            if (cooldownMinutes >= 0) {
                                                val cooldownTicks = cooldownMinutes * 60 * 20 // minutes to ticks
                                                state.cooldownLength = cooldownTicks

                                                if (verboseLogging) {
                                                    plugin.logger.info("[SpawnerReset] Spawner at ${block.x},${block.y},${block.z}: " +
                                                        "cooldown $oldCooldown -> $cooldownTicks ticks (${cooldownMinutes}m)")
                                                }
                                            }

                                            // Commit changes
                                            state.update(true, false)

                                            // Verify the change was applied (debug)
                                            if (verboseLogging && cooldownMinutes >= 0) {
                                                val newState = block.state as? org.bukkit.block.TrialSpawner
                                                val actualCooldown = newState?.cooldownLength ?: -1
                                                val cooldownTicks = cooldownMinutes * 60 * 20
                                                if (actualCooldown != cooldownTicks) {
                                                    plugin.logger.warning("[SpawnerReset] MISMATCH! Expected $cooldownTicks but got $actualCooldown")
                                                }
                                            }

                                            resetCount++
                                        }
                                    }
                                }
                            }
                        }

                        if (resetCount > 0) {
                            val cooldownInfo = when {
                                cooldownMinutes < 0 -> "vanilla default"
                                cooldownMinutes == 0 -> "no cooldown (0 ticks)"
                                else -> "${cooldownMinutes}m cooldown (${cooldownMinutes * 60 * 20} ticks)"
                            }
                            plugin.logger.info("Reset $resetCount trial spawners in chamber ${chamber.name} ($cooldownInfo)")
                        }

                        continuation.resume(Unit) {}
                    } catch (e: Exception) {
                        plugin.logger.warning("Error resetting trial spawners: ${e.message}")
                        continuation.resume(Unit) {}  // Don't fail the whole reset
                    }
                })
            }
        }
    }

    /**
     * Restores the chamber from a snapshot.
     *
     * @param chamber The chamber being restored
     * @param snapshotFile The snapshot file to restore from
     * @param initiatingPlayer Optional player who initiated the restoration (for WorldEdit undo support)
     */
    private suspend fun restoreFromSnapshot(chamber: Chamber, snapshotFile: File, initiatingPlayer: Player? = null) {
        plugin.logger.info("Restoring chamber ${chamber.name} from snapshot")

        val snapshot = plugin.snapshotManager.loadSnapshot(snapshotFile)
        if (snapshot == null) {
            plugin.logger.severe("Failed to load snapshot for chamber ${chamber.name}")
            return
        }

        restoreFromSnapshotBlocks(chamber, snapshot, initiatingPlayer)
    }

    /**
     * Shared block-restoration tail used by both the on-disk path and the
     * v1.4.0 [ChamberResetEvent.snapshotOverride] path. Suspends until every
     * region-thread batch completes.
     */
    private suspend fun restoreFromSnapshotBlocks(
        chamber: Chamber,
        snapshot: Map<org.bukkit.Location, io.github.darkstarworks.trialChamberPro.models.BlockSnapshot>,
        initiatingPlayer: Player? = null
    ) {
        val blockRestorer = BlockRestorer(plugin)

        // Snapshots skip air on capture, so restoreBlocks alone can't revert
        // blocks a player ADDED into formerly-empty cells (lava, cobble, etc.).
        // Clear those first; disable via reset.clear-added-blocks if a server
        // intentionally lets players build inside chambers.
        //
        // SAFETY: the clear region is the INTERSECTION of the chamber bounds and
        // the snapshot's own coverage. If the chamber AABB grew after capture
        // (e.g. a discovery merge), clearing the full chamber bounds would wipe
        // everything in the annexed volume that the snapshot can't put back —
        // terrain, builds, the lot. Never clear ground the snapshot doesn't cover.
        if (plugin.config.getBoolean("reset.clear-added-blocks", true) && snapshot.isNotEmpty()) {
            val world = chamber.getWorld()
            if (world != null) {
                var snapMinX = Int.MAX_VALUE; var snapMinY = Int.MAX_VALUE; var snapMinZ = Int.MAX_VALUE
                var snapMaxX = Int.MIN_VALUE; var snapMaxY = Int.MIN_VALUE; var snapMaxZ = Int.MIN_VALUE
                for (loc in snapshot.keys) {
                    if (loc.blockX < snapMinX) snapMinX = loc.blockX
                    if (loc.blockY < snapMinY) snapMinY = loc.blockY
                    if (loc.blockZ < snapMinZ) snapMinZ = loc.blockZ
                    if (loc.blockX > snapMaxX) snapMaxX = loc.blockX
                    if (loc.blockY > snapMaxY) snapMaxY = loc.blockY
                    if (loc.blockZ > snapMaxZ) snapMaxZ = loc.blockZ
                }
                if (snapMinX > chamber.minX || snapMinY > chamber.minY || snapMinZ > chamber.minZ ||
                    snapMaxX < chamber.maxX || snapMaxY < chamber.maxY || snapMaxZ < chamber.maxZ
                ) {
                    plugin.logger.warning(
                        "Snapshot for chamber ${chamber.name} covers a smaller region than the chamber " +
                            "bounds (chamber likely grew after capture). Clearing only the snapshot-covered " +
                            "region — run /tcp snapshot create ${chamber.name} to recapture the full bounds."
                    )
                }
                val clrMinX = maxOf(chamber.minX, snapMinX)
                val clrMinY = maxOf(chamber.minY, snapMinY)
                val clrMinZ = maxOf(chamber.minZ, snapMinZ)
                val clrMaxX = minOf(chamber.maxX, snapMaxX)
                val clrMaxY = minOf(chamber.maxY, snapMaxY)
                val clrMaxZ = minOf(chamber.maxZ, snapMaxZ)
                if (clrMinX <= clrMaxX && clrMinY <= clrMaxY && clrMinZ <= clrMaxZ) {
                    blockRestorer.clearAddedBlocks(
                        world,
                        clrMinX, clrMinY, clrMinZ,
                        clrMaxX, clrMaxY, clrMaxZ,
                        snapshot,
                    )
                }
            }
        }

        // Fast path: FastAsyncWorldEdit for scheduled (no-player) resets, to smooth out
        // the lag of large restores. Paper-only; manual resets keep the BlockRestorer
        // path so //undo still works. Falls back to BlockRestorer on any failure.
        if (initiatingPlayer == null &&
            plugin.config.getBoolean("global.use-fawe", false) &&
            io.github.darkstarworks.trialChamberPro.utils.FaweResetPlacer.isAvailable(plugin)
        ) {
            try {
                io.github.darkstarworks.trialChamberPro.utils.FaweResetPlacer(plugin).place(snapshot)
                plugin.logger.info("Restored ${snapshot.size} blocks for chamber ${chamber.name} (FAWE)")
                return
            } catch (e: Exception) {
                plugin.logger.warning("FAWE reset failed for ${chamber.name}, falling back to batched restore: ${e.message}")
            }
        }

        blockRestorer.restoreBlocks(
            snapshot,
            onProgress = { processed, total ->
                if (processed % 1000 == 0) {
                    plugin.logger.info("Restoring ${chamber.name}: $processed/$total blocks")
                }
            },
            onComplete = {
                plugin.logger.info("Restored ${snapshot.size} blocks for chamber ${chamber.name}")
            },
            initiatingPlayer = initiatingPlayer
        )
    }

    /**
     * Forces an immediate reset of a chamber.
     *
     * @param chamberName The name of the chamber to reset
     * @param initiatingPlayer Optional player who initiated the reset (for WorldEdit undo support)
     */
    suspend fun forceReset(chamberName: String, initiatingPlayer: Player? = null): Boolean {
        val chamber = plugin.chamberManager.getChamber(chamberName) ?: return false
        return resetChamber(chamber, initiatingPlayer)
    }

    /**
     * Cancels a scheduled reset.
     */
    fun cancelScheduledReset(chamberId: Int) {
        scheduledResets.remove(chamberId)?.cancel()
        warningJobs.remove(chamberId)?.forEach { it.cancel() }
    }

    /**
     * Gets the time until the next reset for a chamber.
     */
    fun getTimeUntilReset(chamber: Chamber): Long {
        val lastReset = chamber.lastReset ?: chamber.createdAt
        val resetIntervalMs = chamber.resetInterval * 1000
        val nextResetTime = lastReset + resetIntervalMs
        val now = System.currentTimeMillis()

        return maxOf(0, nextResetTime - now)
    }

    /**
     * Stops the reset scheduler.
     */
    fun shutdown() {
        resetScope.cancel()
        plugin.logger.info("Reset scheduler stopped")
    }
}

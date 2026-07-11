package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.api.events.ChamberEnteredEvent
import com.esmpfun.bettertrialchambers.api.events.ChamberExitedEvent
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.scheduler.ScheduledTask
import com.esmpfun.bettertrialchambers.utils.AdvancementUtil
import kotlinx.coroutines.*
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized time tracking for players in Trial Chambers.
 * Only checks on block boundary crossings to minimize performance impact.
 */
class PlayerMovementListener(private val plugin: BetterTrialChambers) : Listener {

    private val movementScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // In-memory tracking of players currently in chambers
    private val playersInChambers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerEntryTimes = ConcurrentHashMap<UUID, Long>()

    init {
        // Start periodic flush task (every 5 minutes)
        if (plugin.config.getBoolean("statistics.enabled", true) &&
            plugin.config.getBoolean("statistics.track-time-spent", true)) {
            startTimeTrackingTask()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to

        // Only check when crossing block boundaries (major performance optimization).
        // Done first — applies to both event-firing AND stats paths; sub-block movement
        // can't change chamber membership.
        if (from.blockX == to.blockX &&
            from.blockY == to.blockY &&
            from.blockZ == to.blockZ) {
            return
        }

        handleTransition(event.player, from, to)
    }

    /**
     * v1.7.2: teleports do NOT fire PlayerMoveEvent (PlayerTeleportEvent has its
     * own handler list) — so `/tp`, `/home`, ender pearls and the reset eviction
     * itself crossed chamber boundaries without firing entry/exit events, leaving
     * stale time tracking and a stale playersInChambers entry. Same transition
     * logic as movement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
        handleTransition(event.player, event.from, event.to)
    }

    /**
     * v1.7.2: joining inside a chamber (logout inside → login) never registered
     * as an entry until the first move — time tracking and the public
     * ChamberEnteredEvent started late. Entry message suppressed (relogging
     * shouldn't re-announce).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val location = player.location
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true) &&
            plugin.config.getBoolean("statistics.track-time-spent", true)
        movementScope.launch {
            val chamber = plugin.chamberManager.getChamberAt(location)?.takeIf { !it.isPaused } ?: return@launch
            onChamberEntered(player, uuid, chamber, statsEnabled, sendEntryMessage = false)
        }
    }

    /** Shared chamber-transition dispatch for moves and teleports. */
    private fun handleTransition(player: org.bukkit.entity.Player, from: org.bukkit.Location, to: org.bukkit.Location) {
        val uuid = player.uniqueId
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true) &&
            plugin.config.getBoolean("statistics.track-time-spent", true)

        movementScope.launch {
            val previousChamber = plugin.chamberManager.getChamberAt(from)?.takeIf { !it.isPaused }
            val currentChamber = plugin.chamberManager.getChamberAt(to)?.takeIf { !it.isPaused }
            val wasInChamber = previousChamber != null
            val isInChamber = currentChamber != null

            // Player entered a chamber (or crossed straight from one chamber to another).
            if (!wasInChamber && isInChamber) {
                onChamberEntered(player, uuid, currentChamber!!, statsEnabled, sendEntryMessage = true)
            }
            // Player left a chamber (or crossed straight from one chamber to another).
            else if (wasInChamber && !isInChamber) {
                onChamberExited(player, uuid, previousChamber!!, statsEnabled, sendExitMessage = true)
            }
            // Direct chamber-to-chamber transition (different chamber ids). Fire both.
            else if (wasInChamber && isInChamber && previousChamber!!.id != currentChamber!!.id) {
                onChamberExited(player, uuid, previousChamber, statsEnabled, sendExitMessage = false)
                onChamberEntered(player, uuid, currentChamber, statsEnabled, sendEntryMessage = false)
            }
        }
    }

    /**
     * Common entry handling — fires the public [ChamberEnteredEvent] unconditionally,
     * then runs the stats / advancement / message side-effects when their respective
     * config flags are on. Pulled out of the move handler so a chamber-to-chamber
     * transition can reuse it without duplicating the body.
     */
    private fun onChamberEntered(
        player: org.bukkit.entity.Player,
        uuid: UUID,
        chamber: Chamber,
        statsEnabled: Boolean,
        sendEntryMessage: Boolean,
    ) {
        // Public event — always fires regardless of stats config.
        plugin.server.pluginManager.callEvent(ChamberEnteredEvent(player, chamber))

        if (statsEnabled) {
            playersInChambers.add(uuid)
            playerEntryTimes[uuid] = System.currentTimeMillis()
        }

        // Grant "Minecraft: Trial(s) Edition" advancement (must run on entity thread).
        // Only grant to survival/adventure players — not spectators or creative.
        plugin.scheduler.runAtEntity(player, Runnable {
            if (player.isOnline &&
                (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE)) {
                val granted = AdvancementUtil.grantTrialChamberEntry(player)
                if (!granted) {
                    plugin.logger.warning("Failed to grant trial chamber entry advancement to ${player.name}")
                }
            }
        })

        // Optional: Send entry message (suppressed on chamber→chamber transitions).
        if (sendEntryMessage && plugin.config.getBoolean("messages.chamber-entry-message", false)) {
            player.sendMessage(
                plugin.getMessageComponent("chamber-entered", "chamber" to chamber.name)
            )
        }
    }

    /**
     * Common exit handling — fires the public [ChamberExitedEvent] unconditionally,
     * then runs the stats / message side-effects when their respective config flags
     * are on.
     */
    private suspend fun onChamberExited(
        player: org.bukkit.entity.Player,
        uuid: UUID,
        chamber: Chamber,
        statsEnabled: Boolean,
        sendExitMessage: Boolean,
    ) {
        // Public event — always fires regardless of stats config.
        plugin.server.pluginManager.callEvent(ChamberExitedEvent(player, chamber))

        if (statsEnabled) {
            playersInChambers.remove(uuid)
            // Immediately flush their time.
            flushPlayerTime(uuid)
        }

        // Optional: Send exit message (suppressed on chamber→chamber transitions).
        if (sendExitMessage && plugin.config.getBoolean("messages.chamber-exit-message", false)) {
            player.sendMessage(plugin.getMessageComponent("chamber-exited"))
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val location = player.location

        // Fire ChamberExitedEvent for any player who disconnects while inside a chamber
        // — keeps the entry/exit pair balanced for downstream listeners (e.g. MT's HUD
        // that allocates per-player state on entry). Decoupled from stats so it fires
        // whether or not time-tracking is on.
        movementScope.launch {
            val chamber = plugin.chamberManager.getChamberAt(location)?.takeIf { !it.isPaused }
            if (chamber != null) {
                plugin.server.pluginManager.callEvent(ChamberExitedEvent(player, chamber))
            }
        }

        // Stats path (unchanged): flush their tracked time if we were tracking it.
        if (playersInChambers.remove(uuid)) {
            movementScope.launch {
                flushPlayerTime(uuid)
            }
        }
    }

    // Track the timer task so we can cancel it if needed
    private var timeTrackingTask: ScheduledTask? = null

    /**
     * Starts the periodic flush task that saves time data every 5 minutes.
     * Folia compatible: Uses scheduler adapter for timer tasks.
     */
    private fun startTimeTrackingTask() {
        val interval = plugin.config.getInt("performance.time-tracking-interval", 300) // seconds
        val intervalTicks = interval * 20L

        timeTrackingTask = plugin.scheduler.runTaskTimer(Runnable {
            movementScope.launch {
                flushAllPlayerTimes()
            }
        }, intervalTicks, intervalTicks)
    }

    /**
     * Flushes all currently tracked player times to the database.
     * Uses batch update for efficiency.
     */
    private suspend fun flushAllPlayerTimes() {
        if (playersInChambers.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val currentPlayers = playersInChambers.toSet()

        // Collect all time updates into a map
        val updates = mutableMapOf<UUID, Long>()
        currentPlayers.forEach { uuid ->
            val entryTime = playerEntryTimes[uuid] ?: return@forEach
            val timeSpent = (currentTime - entryTime) / 1000 // Convert to seconds

            if (timeSpent > 0) {
                updates[uuid] = timeSpent
                playerEntryTimes[uuid] = currentTime // Reset entry time
            }
        }

        // Batch update all players in a single transaction
        if (updates.isNotEmpty()) {
            plugin.statisticsManager.batchAddTimeSpent(updates)
            plugin.logger.info("Flushed time tracking for ${updates.size} players")
        }
    }

    /**
     * Flushes time data for a specific player.
     */
    private suspend fun flushPlayerTime(uuid: UUID) {
        val entryTime = playerEntryTimes.remove(uuid) ?: return
        val timeSpent = (System.currentTimeMillis() - entryTime) / 1000 // Convert to seconds

        if (timeSpent > 0) {
            plugin.statisticsManager.addTimeSpent(uuid, timeSpent)
        }
    }

    /**
     * Gets the set of players currently in chambers.
     */
    fun getPlayersInChambers(): Set<UUID> = playersInChambers.toSet()

    /**
     * Checks if a player is currently in a chamber.
     */
    fun isPlayerInChamber(uuid: UUID): Boolean = playersInChambers.contains(uuid)

    /**
     * Flushes all pending time data and cancels the coroutine scope on plugin shutdown.
     */
    fun shutdown() {
        // Flush remaining player times before shutting down
        val currentTime = System.currentTimeMillis()
        val updates = mutableMapOf<UUID, Long>()

        playersInChambers.forEach { uuid ->
            val entryTime = playerEntryTimes.remove(uuid)
            if (entryTime != null) {
                val timeSpent = (currentTime - entryTime) / 1000
                if (timeSpent > 0) {
                    updates[uuid] = timeSpent
                }
            }
        }

        if (updates.isNotEmpty()) {
            try {
                runBlocking {
                    plugin.statisticsManager.batchAddTimeSpent(updates)
                }
                plugin.logger.info("Flushed final time tracking for ${updates.size} players on shutdown")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to flush time tracking on shutdown: ${e.message}")
            }
        }

        movementScope.cancel()
        playersInChambers.clear()
        playerEntryTimes.clear()
    }
}

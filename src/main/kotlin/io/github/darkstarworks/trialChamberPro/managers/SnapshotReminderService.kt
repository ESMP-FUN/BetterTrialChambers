package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import kotlinx.coroutines.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.File

/**
 * Periodically (and on admin join) reminds operators that auto-discovered chambers
 * exist without a snapshot — without one the chamber can't be reset, so this is
 * actionable nagging rather than informational chatter.
 *
 * Config (`discovery.snapshot-reminder.*`):
 * - `enabled` (default true)
 * - `interval-minutes` (default 30)  — periodic console summary + admin chat ping
 * - `on-join` (default true)         — ping admins individually when they log in
 *
 * Notification target is anyone with `tcp.admin.snapshot` (the perm that lets them
 * act on it via `/tcp snapshot create`).
 */
class SnapshotReminderService(private val plugin: TrialChamberPro) : Listener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var schedulerJob: Job? = null

    fun startScheduler() {
        if (!isEnabled()) return
        val intervalMs = plugin.config.getLong("discovery.snapshot-reminder.interval-minutes", 30L)
            .coerceAtLeast(1L) * 60_000L
        schedulerJob = scope.launch {
            // Wait one interval before the first tick so startup isn't noisy.
            delay(intervalMs)
            while (isActive) {
                try {
                    val snapshotless = snapshotlessChambers()
                    if (snapshotless.isNotEmpty()) {
                        plugin.logger.info(
                            "${snapshotless.size} discovered chamber(s) have no snapshot yet — " +
                                "they cannot be reset. Run /tcp snapshot create <chamber> on each."
                        )
                        plugin.scheduler.runTask(Runnable {
                            plugin.server.onlinePlayers
                                .filter { it.hasPermission("tcp.admin.snapshot") }
                                .forEach { sendSummary(it, snapshotless) }
                        })
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Snapshot reminder scheduler tick failed: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    fun shutdown() {
        schedulerJob?.cancel()
        scope.cancel()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isEnabled()) return
        if (!plugin.config.getBoolean("discovery.snapshot-reminder.on-join", true)) return
        val player = event.player
        if (!player.hasPermission("tcp.admin.snapshot")) return

        // Defer one tick so the join-message stream has settled, then check off-thread.
        plugin.scheduler.runTask(Runnable {
            plugin.launchAsync {
                val snapshotless = snapshotlessChambers()
                if (snapshotless.isEmpty()) return@launchAsync
                plugin.scheduler.runAtEntity(player, Runnable {
                    if (player.isOnline) sendSummary(player, snapshotless)
                })
            }
        })
    }

    private fun isEnabled(): Boolean =
        plugin.config.getBoolean("discovery.snapshot-reminder.enabled", true)

    /** Chambers with no on-disk snapshot file. */
    private suspend fun snapshotlessChambers(): List<Chamber> {
        val all = plugin.chamberManager.getAllChambers()
        return all.filter { c ->
            val path = c.snapshotFile
            path.isNullOrBlank() || !File(path).isFile
        }
    }

    private fun sendSummary(player: Player, chambers: List<Chamber>) {
        val sample = chambers.take(5).joinToString(", ") { it.name }
        val more = if (chambers.size > 5) " (+${chambers.size - 5} more)" else ""
        player.sendMessage(plugin.getMessageComponent("snapshot-reminder",
            "count" to chambers.size, "chambers" to "$sample$more"))
    }
}

package io.github.darkstarworks.trialChamberPro.setup

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Gentle, capped nudge toward `/tcp setup` — never forced.
 *
 * On an admin's join, if they've **never touched** the setup tour, posts a one-line
 * clickable hint. Throttled to at most once a week and at most [MAX_REMINDERS] times ever
 * (state in [SetupState]); the moment `/tcp setup` is run, [SetupState.touched] flips and
 * the nudge stops permanently. Config-gated by `setup.reminder.enabled` (default true).
 *
 * Mirrors [io.github.darkstarworks.trialChamberPro.managers.SnapshotReminderService]'s
 * op-join + clickable-MiniMessage pattern.
 */
class SetupReminderService(
    private val plugin: TrialChamberPro,
    private val state: SetupState,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!plugin.config.getBoolean("setup.reminder.enabled", true)) return
        if (state.completed) return                       // finished → never nudge
        val player = event.player
        if (!player.hasPermission("tcp.admin.setup")) return
        val now = System.currentTimeMillis()

        // 1) "You started but stopped" follow-up — one-time, a week after a Stop.
        if (state.touched) {
            if (state.followUpEpoch != 0L && !state.followUpShown && now >= state.followUpEpoch) {
                send(player) {
                    player.sendMessage(plugin.getMessageComponent("setup.reminder-resume"))
                    state.followUpShown = true
                }
            }
            return
        }

        // 2) Initial reminders — up to MAX_REMINDERS, at most weekly, while never-touched.
        if (state.reminderCount >= MAX_REMINDERS) return
        if (now - state.lastReminderEpoch < WEEK_MS) return
        send(player) {
            player.sendMessage(plugin.getMessageComponent("setup.reminder"))
            state.reminderCount = state.reminderCount + 1
            state.lastReminderEpoch = now
        }
    }

    /** Defer a tick (join stream settles), then run [action] on the player's region thread. */
    private fun send(player: org.bukkit.entity.Player, action: () -> Unit) {
        plugin.scheduler.runTask(Runnable {
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) action()
            })
        })
    }

    companion object {
        const val MAX_REMINDERS = 3
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

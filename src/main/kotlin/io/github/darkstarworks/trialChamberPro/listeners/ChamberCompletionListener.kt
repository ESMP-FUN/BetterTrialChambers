package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.api.events.ChamberClearedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Records chamber completions for statistics (v1.5.12).
 *
 * Trial-chamber completion was never credited before — `chambers_completed`
 * was always 0, dead-ending the chambers leaderboard and the
 * `%tcp_chambers_completed%` / `%tcp_leaderboard_chambers%` /
 * `%tcp_top_chambers_*%` placeholders. This listener consumes
 * [ChamberClearedEvent] (fired once per reset cycle when every trial spawner in
 * a registered chamber has finished its wave) and credits each participant.
 *
 * The event already de-duplicates per cycle (it fires exactly once when the
 * chamber is first fully cleared, and its tracking resets on every chamber
 * reset), so a player can't be credited twice for the same clear. Participants
 * are credited even if offline by the time the chamber clears — the stat write
 * is keyed by UUID.
 *
 * [ChamberClearedEvent] may fire on a region thread (it is flagged async when
 * not on the primary thread), so the per-UUID stat writes are dispatched via
 * [TrialChamberPro.launchAsync] rather than run inline.
 */
class ChamberCompletionListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChamberCleared(event: ChamberClearedEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return
        if (!plugin.config.getBoolean("statistics.track-chamber-completion", true)) return

        val participants = event.participants
        if (participants.isEmpty()) return

        plugin.launchAsync {
            participants.forEach { uuid ->
                plugin.statisticsManager.incrementChambersCompleted(uuid)
            }
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info(
                    "[Stats] Credited ${participants.size} participant(s) with a chamber completion for '${event.chamber.name}'."
                )
            }
        }
    }
}

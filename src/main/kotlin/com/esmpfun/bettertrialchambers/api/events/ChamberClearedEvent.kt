package com.esmpfun.bettertrialchambers.api.events

import com.esmpfun.bettertrialchambers.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when every trial spawner inside a registered chamber has completed
 * its current wave during the same reset cycle — i.e. the chamber has been
 * "cleared" in one continuous run, before any auto- or manual reset.
 *
 * Not cancellable; the clear has already happened. Tracking is kept per
 * chamber: as each spawner completes a wave, its key is added to a
 * per-chamber set. The event fires exactly once when that set first equals
 * the chamber's total spawner count. The set is reset on every
 * [ChamberResetEvent] / [ChamberResetCompleteEvent] cycle, so a chamber
 * that's cleared, reset, and cleared again fires the event twice.
 *
 * Wild spawners (those outside any registered chamber) do not contribute —
 * the event is chamber-scoped only.
 *
 * Fires on whichever thread `SpawnerWaveManager.completeWave` is running
 * on — typically the wave's region thread. Asynchronous flag is computed
 * at fire time so listeners can rely on `event.isAsynchronous()`.
 *
 * Primary intended consumer: the planned premium "Mythic Trials" module,
 * which uses this signal to bump per-player chamber tiers and dispatch
 * end-of-run rewards (XP, currency, crate keys via TCP-VaultCrates'
 * `KeyDropService`, leaderboard entry).
 *
 * @property chamber       The chamber that was cleared.
 * @property participants  Cumulative set of UUIDs credited across every
 *                         wave in this cycle. Union of each spawner's
 *                         participant set at its completion time. May
 *                         differ from any single wave's participants when
 *                         players join or leave the chamber mid-run.
 * @property durationMs    Wall-clock duration of the clear, from the
 *                         first spawner's wave-start time in this cycle
 *                         to the last spawner's wave-completion.
 *
 * @since v1.5.0
 */
class ChamberClearedEvent(
    val chamber: Chamber,
    val participants: Set<UUID>,
    val durationMs: Long
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

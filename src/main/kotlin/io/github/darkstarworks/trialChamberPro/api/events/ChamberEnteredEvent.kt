package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired when a [Player] crosses into the bounding box of a registered, non-paused
 * [Chamber] — the boundary-crossing transition from "outside chamber" to "inside
 * chamber" detected by [io.github.darkstarworks.trialChamberPro.listeners.PlayerMovementListener].
 *
 * Fires exactly once per entry. The matching exit fires [ChamberExitedEvent]. A
 * player who moves between two chambers fires an exit for the old and an entry
 * for the new.
 *
 * Unlike the time-tracking and entry-message logic which is gated on
 * `statistics.*` config flags, this event always fires when entry is detected —
 * downstream listeners that need to react to chamber presence shouldn't have
 * their behaviour silently disabled by the server admin's stats preferences.
 *
 * Not cancellable; the entry has already happened.
 *
 * Fires from the listener's coroutine on the default dispatcher (not the player's
 * region thread). Listeners that need to call Bukkit API on the player should
 * schedule onto the entity thread themselves via
 * `plugin.scheduler.runAtEntity(player, ...)`.
 *
 * **Intended consumers:** sibling premium modules (TCP-MythicTrials uses this to
 * drive its in-chamber HUD) and any third-party plugin reacting to chamber
 * presence (welcome messages, region effects, etc.).
 *
 * @property player  The player who entered.
 * @property chamber The chamber the player is now inside.
 *
 * @since v1.5.4
 */
class ChamberEnteredEvent(
    val player: Player,
    val chamber: Chamber,
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

package com.esmpfun.bettertrialchambers.api.events

import com.esmpfun.bettertrialchambers.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired when a [Player] crosses out of the bounding box of a registered, non-paused
 * [Chamber] — the boundary-crossing transition from "inside chamber" to "outside
 * chamber" detected by [com.esmpfun.bettertrialchambers.listeners.PlayerMovementListener].
 *
 * Fires exactly once per exit. The matching entry fires [ChamberEnteredEvent]. A
 * player who moves between two chambers fires an exit for the old and an entry
 * for the new.
 *
 * Unlike the time-tracking and exit-message logic which is gated on
 * `statistics.*` config flags, this event always fires when exit is detected —
 * downstream listeners that need to react to chamber presence shouldn't have
 * their behaviour silently disabled by the server admin's stats preferences.
 *
 * Also fires on `PlayerQuitEvent` for any player currently inside a chamber, so
 * listeners that allocate per-player state on entry can release it cleanly even
 * if the player disconnected without walking out.
 *
 * Not cancellable; the exit has already happened.
 *
 * Fires from the listener's coroutine on the default dispatcher (not the player's
 * region thread). Listeners that need to call Bukkit API on the player should
 * schedule onto the entity thread themselves via
 * `plugin.scheduler.runAtEntity(player, ...)`.
 *
 * @property player  The player who exited (still online unless this fires from quit).
 * @property chamber The chamber they were inside.
 *
 * @since v1.5.4
 */
class ChamberExitedEvent(
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

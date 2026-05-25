package io.github.darkstarworks.trialChamberPro.api.events

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired immediately after a player's statistics are persisted to the database
 * (vault opened, mob killed, chamber completed, death, or time added).
 *
 * This is the canonical **outbound** hook for the planned premium "Network Sync"
 * module: it listens here, debounces, and broadcasts an "invalidate player X"
 * message to peer servers over its messaging transport (Redis / plugin
 * messaging) so cross-server leaderboards and stats stay fresh. Single-server
 * installs have no listener, so the event is a cheap no-op.
 *
 * Fired on whichever thread the persist runs on — currently always
 * `Dispatchers.IO`, never the primary thread — so the asynchronous flag is
 * computed at fire time and listeners MUST treat this as an async event:
 * schedule onto the appropriate region thread before touching any Bukkit API.
 *
 * High-frequency reasons (notably [Reason.MOB_KILL], which can fire many times
 * per wave) are intentionally NOT debounced here — debouncing network
 * broadcasts is the consuming module's responsibility, not the free core's.
 *
 * @property playerUuid The player whose stats changed.
 * @property reason     What kind of update triggered the persist.
 *
 * @since v1.5.1
 */
class StatisticsUpdatedEvent(
    val playerUuid: UUID,
    val reason: Reason
) : Event(!Bukkit.isPrimaryThread()) {

    enum class Reason {
        VAULT,
        MOB_KILL,
        CHAMBER_COMPLETE,
        DEATH,
        TIME
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One-shot chat input collector for renaming a chamber from the chamber-detail GUI
 * (v1.6.1). Mirrors [MobIdInputListener]: the GUI registers a pending rename and tells
 * the player to type a name (or "cancel"); the next chat line sets the chamber's display
 * name and reopens the detail view. Chat input (rather than the Dialog API) keeps this
 * consistent with the existing mob-id editor and working on every supported version.
 */
class ChamberRenameInputListener(private val plugin: BetterTrialChambers) : Listener {

    companion object {
        // playerId -> (chamberId, expiresAt)
        private val pending = ConcurrentHashMap<UUID, Pair<Int, Long>>()
        private const val TIMEOUT_MS = 60_000L
        private const val MAX_NAME_LENGTH = 48

        /** Registers a pending rename for [playerId]. Overwrites any previous entry. */
        fun awaitInput(playerId: UUID, chamberId: Int) {
            pending[playerId] = chamberId to (System.currentTimeMillis() + TIMEOUT_MS)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val entry = pending[player.uniqueId] ?: return
        val (chamberId, expiresAt) = entry
        if (expiresAt < System.currentTimeMillis()) {
            pending.remove(player.uniqueId)
            return
        }

        event.isCancelled = true
        pending.remove(player.uniqueId)

        val raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        if (raw.isEmpty() || raw.equals("cancel", ignoreCase = true)) {
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("gui-rename-input-cancelled"))
                reopen(player.uniqueId, chamberId)
            })
            return
        }
        val newName = raw.take(MAX_NAME_LENGTH)

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getCachedChamberById(chamberId)
            if (chamber == null) {
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessageComponent("gui-mob-input-no-chamber"))
                })
                return@launchAsync
            }
            val ok = plugin.chamberManager.setDisplayName(chamber.id, newName)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessageComponent("gui-rename-input-set", "name" to newName))
                } else {
                    player.sendMessage(plugin.getMessageComponent("gui-rename-input-failed"))
                }
                reopen(player.uniqueId, chamber.id)
            })
        }
    }

    private fun reopen(playerId: UUID, chamberId: Int) {
        val player = plugin.server.getPlayer(playerId) ?: return
        val refreshed = plugin.chamberManager.getCachedChamberById(chamberId) ?: return
        plugin.menuService.openChamberDetail(player, refreshed)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }
}

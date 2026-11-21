package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Observes WorldEdit `//undo` (and `/undo`) without intercepting it. If the player is
 * standing inside a registered chamber when they run undo, posts a one-line hint that
 * `/trial delete <name>` may be needed to also clean up the registration. Purely
 * informational, never cancels the event, never locks the player out.
 */
class PostUndoHintListener(private val plugin: BetterTrialChambers) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val firstToken = event.message.trim().substringBefore(' ').lowercase()
        if (firstToken != "/undo" && firstToken != "//undo") return

        val player = event.player
        val chamber = plugin.chamberManager.getCachedChamberAt(player.location) ?: return
        player.sendMessage(
            plugin.getMessageComponent("undo-cleanup-hint", "chamber" to chamber.name)
        )
    }
}

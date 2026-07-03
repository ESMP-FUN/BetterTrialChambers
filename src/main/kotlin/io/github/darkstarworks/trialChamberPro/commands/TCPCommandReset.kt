package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val resetCommandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun handleReset(plugin: TrialChamberPro, sender: CommandSender, args: Array<out String>) {
    if (!sender.hasPermission("tcp.admin.reset")) {
        sender.sendMessage(plugin.getMessageComponent("no-permission"))
        return
    }

    if (args.size < 2) {
        sender.sendMessage(plugin.getMessageComponent("usage-reset"))
        return
    }

    // Operator-confirmation queue (when global.reset-require-confirmation is on).
    when (args[1].lowercase()) {
        "pending" -> {
            val names = plugin.resetManager.pendingResetNames()
            if (names.isEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("reset-pending-empty"))
            } else {
                sender.sendMessage(plugin.getMessageComponent("reset-pending-page-header", "count" to names.size))
                names.forEach {
                    sender.sendMessage(plugin.getMessageComponent("reset-pending-list-item", "chamber" to it))
                }
            }
            return
        }
        "confirm" -> {
            val target = args.getOrNull(2)
            if (target == null) {
                sender.sendMessage(plugin.getMessageComponent("reset-confirm-usage"))
                return
            }
            if (target.equals("all", ignoreCase = true)) {
                val n = plugin.resetManager.confirmAllResets()
                sender.sendMessage(
                    if (n > 0) plugin.getMessageComponent("reset-confirm-all", "count" to n)
                    else plugin.getMessageComponent("reset-confirm-all-none")
                )
                return
            }
            resetCommandScope.launch {
                val chamber = plugin.chamberManager.getChamber(target)
                if (chamber == null) {
                    sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to target))
                    return@launch
                }
                if (plugin.resetManager.confirmReset(chamber)) {
                    sender.sendMessage(plugin.getMessageComponent("reset-confirmed", "chamber" to target))
                } else {
                    sender.sendMessage(plugin.getMessageComponent("reset-confirm-not-pending", "chamber" to target))
                }
            }
            return
        }
    }

    val chamberName = args[1]

    resetCommandScope.launch {
        val chamber = plugin.chamberManager.getChamber(chamberName)
        if (chamber == null) {
            sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            return@launch
        }

        sender.sendMessage(plugin.getMessageComponent("chamber-resetting", "chamber" to chamberName))

        // Pass player for WorldEdit undo support if sender is a player
        val initiatingPlayer = sender as? Player
        val success = plugin.resetManager.resetChamber(chamber, initiatingPlayer)
        if (success) {
            sender.sendMessage(plugin.getMessageComponent("reset-success", "chamber" to chamberName))
        } else {
            sender.sendMessage(plugin.getMessageComponent("reset-failed", "error" to "Check console for details"))
        }
    }
}

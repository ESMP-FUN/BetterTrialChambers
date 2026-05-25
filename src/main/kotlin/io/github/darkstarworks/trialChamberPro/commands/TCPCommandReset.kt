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
                sender.sendRichMessage("<gray>No chambers are awaiting reset confirmation.")
            } else {
                sender.sendRichMessage(
                    "<gold>Awaiting reset confirmation (${names.size}): " +
                        "<click:run_command:'/tcp reset confirm all'><green>[confirm all]</green></click>"
                )
                names.forEach {
                    sender.sendRichMessage(
                        "<gray>• <yellow>$it</yellow> <click:run_command:'/tcp reset confirm $it'><green>[confirm]</green></click>"
                    )
                }
            }
            return
        }
        "confirm" -> {
            val target = args.getOrNull(2)
            if (target == null) {
                sender.sendRichMessage("<red>Usage: /tcp reset confirm <chamber|all>")
                return
            }
            if (target.equals("all", ignoreCase = true)) {
                val n = plugin.resetManager.confirmAllResets()
                sender.sendRichMessage(
                    if (n > 0) "<green>Confirmed <yellow>$n</yellow> reset(s) — they'll run staggered."
                    else "<gray>No chambers were awaiting confirmation."
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
                    sender.sendRichMessage("<green>Confirmed reset for <yellow>$target</yellow>.")
                } else {
                    sender.sendRichMessage("<gray><yellow>$target</yellow> wasn't awaiting confirmation.")
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

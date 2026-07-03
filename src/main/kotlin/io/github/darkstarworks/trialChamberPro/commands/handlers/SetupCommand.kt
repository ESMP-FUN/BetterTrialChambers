package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.DialogPromptManager
import io.github.darkstarworks.trialChamberPro.setup.SetupController
import io.github.darkstarworks.trialChamberPro.setup.SetupStep
import io.github.darkstarworks.trialChamberPro.setup.SetupTourChat
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/tcp setup` — the opt-in settings tour.
 *
 *   /tcp setup [start]   start the tour from the top
 *   /tcp setup continue  resume a paused tour
 *
 * Internal actions the clickable-chat buttons route back to (also usable by hand):
 *   enable|disable|skip <index> · set <index> <optionId> · pause <index> · stop
 *
 * Dialog is the primary UI; on servers without Paper's Dialog API the clickable-chat tour
 * is used instead. The Dialog renderer references Paper-only classes, so it's instantiated
 * **only when available** — on other servers its class is never loaded.
 */
class SetupCommand(
    private val plugin: TrialChamberPro,
    private val controller: SetupController,
) : SubcommandHandler {

    private val chatTour = SetupTourChat(plugin, controller)
    private val dialogTour: io.github.darkstarworks.trialChamberPro.setup.SetupTourDialog? =
        if (DialogPromptManager.isAvailable())
            io.github.darkstarworks.trialChamberPro.setup.SetupTourDialog(plugin, controller)
        else null

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.setup")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission")); return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage(plugin.getMessageComponent("player-only")); return
        }

        when (args.getOrNull(1)?.lowercase()) {
            null, "start" -> startTour(player)
            "continue", "resume" -> continueTour(player)
            "enable" -> applyToggle(player, args, enabled = true)
            "disable" -> applyToggle(player, args, enabled = false)
            "skip" -> indexArg(player, args)?.let { renderNext(player, it) }
            "set" -> applyChoice(player, args)
            "prev" -> indexArg(player, args)?.let { chatTour.render(player, (it - 1).coerceAtLeast(0)) }
            "pause" -> indexArg(player, args)?.let {
                controller.jumpTo(player.uniqueId, it)
                controller.pause(player.uniqueId)
                player.sendMessage(plugin.getMessageComponent("setup.paused"))
            }
            "stop" -> {
                controller.stop(player.uniqueId)
                player.sendMessage(plugin.getMessageComponent("setup.stopped"))
            }
            else -> player.sendMessage(plugin.getMessageComponent("setup.usage"))
        }
    }

    private fun startTour(player: Player) {
        controller.start(player.uniqueId)
        render(player, 0, intro = true)
    }

    private fun continueTour(player: Player) {
        if (!controller.hasPaused(player.uniqueId)) { startTour(player); return }
        val idx = controller.resume(player.uniqueId)
        render(player, idx, intro = false)
    }

    /** Apply a Toggle action (from a chat link or by hand), then show the next step in chat. */
    private fun applyToggle(player: Player, args: Array<out String>, enabled: Boolean) {
        val index = indexArg(player, args) ?: return
        (controller.stepAt(index) as? SetupStep.Toggle)?.let { controller.applyToggle(it.configPath, enabled) }
        renderNext(player, index)
    }

    private fun applyChoice(player: Player, args: Array<out String>) {
        val index = indexArg(player, args) ?: return
        val optionId = args.getOrNull(3) ?: run { player.sendMessage(plugin.getMessageComponent("setup.missing-option")); return }
        val choice = controller.stepAt(index) as? SetupStep.Choice
        val opt = choice?.options?.firstOrNull { it.optionId == optionId }
        if (choice != null && opt != null) controller.applyChoice(choice.configPath, opt.value)
        renderNext(player, index)
    }

    private fun indexArg(player: Player, args: Array<out String>): Int? {
        val i = args.getOrNull(2)?.toIntOrNull()
        if (i == null || i < 0 || i >= controller.stepCount) {
            player.sendMessage(plugin.getMessageComponent("setup.invalid-step")); return null
        }
        return i
    }

    /** Chat-path continuation: show the step after [index] (chat renderer; commands → chat). */
    private fun renderNext(player: Player, index: Int) {
        val next = index + 1
        if (next >= controller.stepCount) chatTour.complete(player) else chatTour.render(player, next)
    }

    /** Pick the renderer (Dialog if available, else chat) and show the step at [index]. */
    private fun render(player: Player, index: Int, intro: Boolean) {
        val tour = dialogTour
        if (tour != null) {
            tour.render(player, index)
        } else {
            if (intro) chatTour.intro(player)
            chatTour.render(player, index)
        }
    }
}

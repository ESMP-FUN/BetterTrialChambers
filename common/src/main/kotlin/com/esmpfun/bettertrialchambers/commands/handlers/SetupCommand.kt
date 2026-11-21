package com.esmpfun.bettertrialchambers.commands.handlers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.setup.SetupController
import com.esmpfun.bettertrialchambers.setup.SetupStep
import com.esmpfun.bettertrialchambers.setup.SetupTourChat
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/trial setup`, the opt-in settings tour.
 *
 *   /trial setup [start]   start the tour from the top
 *   /trial setup continue  resume a paused tour
 *
 * Internal actions the clickable-chat buttons route back to (also usable by hand):
 *   enable|disable|skip <index>, set <index> <optionId>, pause <index>, stop
 *
 * Dialog is the primary UI; on servers without Paper's Dialog API the clickable-chat tour
 * is used instead. The Dialog renderer references Paper-only classes, so it's instantiated
 * **only when available**, on other servers its class is never loaded.
 */
class SetupCommand(
    private val plugin: BetterTrialChambers,
    private val controller: SetupController,
) : SubcommandHandler {

    private val chatTour = SetupTourChat(plugin, controller)

    // Both the class probe and instantiation are inside runCatching so any LinkageError
    // on an older or changed Paper build degrades cleanly to the clickable-chat tour.
    private val dialogTour: com.esmpfun.bettertrialchambers.setup.SetupTourDialog? =
        runCatching {
            Class.forName("io.papermc.paper.dialog.Dialog")
            com.esmpfun.bettertrialchambers.setup.SetupTourDialog(plugin, controller)
        }.getOrNull()

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("btc.admin.setup")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission")); return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage(plugin.getMessageComponent("player-only")); return
        }

        when (args.getOrNull(1)?.lowercase()) {
            null, "start" -> startTour(player, forceChat = false)
            "chat" -> startTour(player, forceChat = true)
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

    private fun startTour(player: Player, forceChat: Boolean) {
        controller.start(player.uniqueId)
        render(player, 0, intro = true, forceChat = forceChat)
    }

    private fun continueTour(player: Player) {
        if (!controller.hasPaused(player.uniqueId)) { startTour(player, forceChat = false); return }
        val idx = controller.resume(player.uniqueId)
        render(player, idx, intro = false, forceChat = false)
    }

    /** Apply a Toggle action (from a chat link or by hand), then show the next step in chat. */
    private fun applyToggle(player: Player, args: Array<out String>, enabled: Boolean) {
        val index = indexArg(player, args) ?: return
        val step = controller.stepAt(index) as? SetupStep.Toggle
        if (step != null) {
            if (enabled && step.requiresPlugin != null &&
                !plugin.server.pluginManager.isPluginEnabled(step.requiresPlugin)
            ) {
                player.sendMessage(plugin.getMessageComponent("setup.requires-plugin", "plugin" to (step.requiresPluginLabel ?: step.requiresPlugin)))
                chatTour.render(player, index)
                return
            }
            controller.markTouched()
            controller.applyToggle(step.configPath, enabled)
        }
        renderNext(player, index)
    }

    private fun applyChoice(player: Player, args: Array<out String>) {
        val index = indexArg(player, args) ?: return
        val optionId = args.getOrNull(3) ?: run { player.sendMessage(plugin.getMessageComponent("setup.missing-option")); return }
        val choice = controller.stepAt(index) as? SetupStep.Choice
        val opt = choice?.options?.firstOrNull { it.optionId == optionId }
        if (choice == null || opt == null) {
            player.sendMessage(plugin.getMessageComponent("setup.missing-option"))
            return
        }
        controller.markTouched()
        controller.applyChoice(choice.configPath, opt.value, player)
        renderNext(player, index)
    }

    private fun indexArg(player: Player, args: Array<out String>): Int? {
        val i = args.getOrNull(2)?.toIntOrNull()
        if (i == null || i < 0 || i >= controller.stepCount) {
            player.sendMessage(plugin.getMessageComponent("setup.invalid-step")); return null
        }
        return i
    }

    /** Chat-path continuation: show the step after [index] (chat renderer; commands -> chat). */
    private fun renderNext(player: Player, index: Int) {
        val next = index + 1
        if (next >= controller.stepCount) chatTour.complete(player) else chatTour.render(player, next)
    }

    /** Pick the renderer (Dialog if available and not forced to chat, else chat) and show [index]. */
    private fun render(player: Player, index: Int, intro: Boolean, forceChat: Boolean) {
        val tour = if (forceChat) null else dialogTour
        if (tour != null) {
            val ok = runCatching { tour.render(player, index) }.isSuccess
            if (ok) return
        }
        if (intro) chatTour.intro(player)
        chatTour.render(player, index)
    }
}

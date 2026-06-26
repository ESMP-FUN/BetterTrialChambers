package io.github.darkstarworks.trialChamberPro.setup

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.DialogPromptManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Paper Dialog renderer for the setup tour — the primary UI when the Dialog API is present.
 *
 * **References Paper-only Dialog classes** (via [DialogPromptManager]); only ever instantiated
 * when [DialogPromptManager.isAvailable]. The dialog *title* is a neutral "Setup" header (so it
 * isn't mistaken for a warning), and the **setting name leads the body** as a bold heading right
 * above its description. Buttons run in-process — apply via the controller, then show the next
 * step's dialog.
 */
class SetupTourDialog(
    private val plugin: TrialChamberPro,
    private val controller: SetupController,
) {
    private val dialogs = DialogPromptManager(plugin)

    fun render(player: Player, index: Int) {
        val step = controller.stepAt(index) ?: run { complete(player); return }
        controller.jumpTo(player.uniqueId, index)

        val b = dialogs.prompt()
            .title(Component.text("TrialChamberPro Setup (${index + 1}/${controller.stepCount})", NamedTextColor.AQUA))
            .canCloseWithEscape(true)
        // Setting name as a bold heading, immediately above the description.
        b.bodyText(plugin.getGuiText(step.titleKey).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        plugin.getGuiLore(step.descKey).forEach { b.bodyText(it) }
        step.cpuImpact?.let { b.bodyText(cpuLine(it)) }
        b.bodyText(currentLine(step))

        b.showMultiAction(player, actions(player, step, index))
    }

    fun complete(player: Player) {
        controller.markComplete()
        val lines = plugin.getGuiLore("setup.complete").ifEmpty {
            listOf(Component.text("Setup complete! Re-run /tcp setup anytime.", NamedTextColor.GREEN))
        }
        val b = dialogs.prompt().title(Component.text("Setup complete", NamedTextColor.GREEN))
        lines.forEach { b.bodyText(it) }
        b.showNotice(player, Component.text("Done", NamedTextColor.GREEN)) { }
    }

    fun intro(player: Player) = render(player, 0)

    // ── pieces ─────────────────────────────────────────────────────────────────

    private fun cpuLine(impact: CpuImpact): Component =
        Component.text("CPU impact: ", NamedTextColor.GRAY)
            .append(Component.text(impact.text, impact.color))

    private fun currentLine(step: SetupStep): Component = when (step) {
        is SetupStep.Toggle -> {
            val on = controller.isEnabled(step.configPath, step.default)
            Component.text("Currently: ", NamedTextColor.GRAY)
                .append(if (on) Component.text("Enabled", NamedTextColor.GREEN)
                        else Component.text("Disabled", NamedTextColor.RED))
        }
        is SetupStep.Choice -> {
            val label = step.formatCurrent?.invoke(plugin.config.get(step.configPath))
                ?: (plugin.config.get(step.configPath)?.toString() ?: "default")
            Component.text("Currently: ", NamedTextColor.GRAY).append(Component.text(label, NamedTextColor.WHITE))
        }
    }

    private fun actions(player: Player, step: SetupStep, index: Int): List<DialogPromptManager.Action> {
        val list = mutableListOf<DialogPromptManager.Action>()
        when (step) {
            is SetupStep.Toggle -> {
                val missingPlugin = step.requiresPlugin?.let { !Bukkit.getPluginManager().isPluginEnabled(it) } ?: false
                if (missingPlugin) {
                    list += DialogPromptManager.Action(
                        Component.text("${step.requiresPlugin} not present", NamedTextColor.DARK_GRAY)
                    ) {
                        player.sendRichMessage("<gray>That option needs the <yellow>${step.requiresPlugin}</yellow> plugin, which isn't installed.")
                        render(player, index)
                    }
                } else {
                    list += DialogPromptManager.Action(Component.text("Enable", NamedTextColor.GREEN)) {
                        controller.applyToggle(step.configPath, true); next(player)
                    }
                }
                list += DialogPromptManager.Action(Component.text("Disable", NamedTextColor.RED)) {
                    controller.applyToggle(step.configPath, false); next(player)
                }
            }
            is SetupStep.Choice -> {
                step.options.forEach { opt ->
                    list += DialogPromptManager.Action(plugin.getGuiText(opt.labelKey).color(NamedTextColor.AQUA)) {
                        controller.applyChoice(step.configPath, opt.value); next(player)
                    }
                }
            }
        }
        if (index > 0) {
            list += DialogPromptManager.Action(Component.text("← Prev", NamedTextColor.GRAY)) { prev(player) }
        }
        list += DialogPromptManager.Action(Component.text("Skip Question →", NamedTextColor.GRAY)) { next(player) }
        list += DialogPromptManager.Action(Component.text("⏸ Pause Setup", NamedTextColor.YELLOW)) {
            controller.pause(player.uniqueId)
            player.sendMessage(plugin.getMessageComponent("setup.paused"))
        }
        list += DialogPromptManager.Action(Component.text("⏹ Stop Setup", NamedTextColor.RED)) {
            controller.stop(player.uniqueId)
            player.sendMessage(plugin.getMessageComponent("setup.stopped"))
        }
        return list
    }

    private fun next(player: Player) {
        val next = controller.advance(player.uniqueId)
        if (next == null) complete(player) else render(player, next)
    }

    private fun prev(player: Player) = render(player, controller.back(player.uniqueId))
}

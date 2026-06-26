package io.github.darkstarworks.trialChamberPro.setup

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Clickable-chat renderer for the setup tour — the fallback when Paper's Dialog API isn't
 * available (older or non-Paper servers). Uses `sendRichMessage` + `<click:run_command:…>`
 * links, the same style as [io.github.darkstarworks.trialChamberPro.managers.SnapshotReminderService].
 *
 * Stateless: each button is a `/tcp setup <action> <index>` command, so a step renders from
 * its index alone (no per-player session needed on this path).
 */
class SetupTourChat(
    private val plugin: TrialChamberPro,
    private val controller: SetupController,
) {

    /** Render the step at [index] to [player] (or the completion message when past the end). */
    fun render(player: Player, index: Int) {
        val step = controller.stepAt(index) ?: run { complete(player); return }
        val sb = StringBuilder()
        sb.append("<dark_aqua><st>                    </st> <gold>Setup <gray>(${index + 1}/${controller.stepCount})</gold> <dark_aqua><st>                    </st>\n")
        sb.append("<yellow><bold>").append(title(step)).append("</bold>\n")
        desc(step).forEach { sb.append("<gray>").append(it).append("\n") }
        step.cpuImpact?.let { sb.append("<gray>CPU impact: <").append(it.mini).append(">").append(it.text).append("\n") }
        sb.append("\n").append(currentLine(step)).append("\n")
        sb.append(buttons(step, index))
        player.sendRichMessage(sb.toString())
    }

    fun complete(player: Player) {
        controller.markComplete()
        player.sendRichMessage(plugin.getMessageList("setup.complete")
            .ifEmpty { listOf("<green>Setup complete! You can re-run <yellow>/tcp setup</yellow> anytime.") }
            .joinToString("\n"))
    }

    fun intro(player: Player) {
        plugin.getMessageList("setup.intro")
            .ifEmpty { listOf("<gold>Welcome — let's tour the main settings. Nothing is forced; pick what suits your server.") }
            .forEach { player.sendRichMessage(it) }
    }

    // ── pieces ─────────────────────────────────────────────────────────────────

    private fun title(step: SetupStep): String =
        plugin.getMessageList(step.titleKey).firstOrNull() ?: step.configPath

    private fun desc(step: SetupStep): List<String> =
        plugin.getMessageList(step.descKey).ifEmpty { listOf("<dark_gray>(${step.configPath})") }

    private fun currentLine(step: SetupStep): String = when (step) {
        is SetupStep.Toggle -> {
            val on = controller.isEnabled(step.configPath, step.default)
            "<gray>Currently: " + if (on) "<green>Enabled" else "<red>Disabled"
        }
        is SetupStep.Choice -> {
            val label = step.formatCurrent?.invoke(plugin.config.get(step.configPath))
                ?: (plugin.config.get(step.configPath)?.toString() ?: "default")
            "<gray>Currently: <white>$label"
        }
    }

    private fun buttons(step: SetupStep, index: Int): String {
        val b = StringBuilder()
        when (step) {
            is SetupStep.Toggle -> {
                val missingPlugin = step.requiresPlugin?.let { !Bukkit.getPluginManager().isPluginEnabled(it) } ?: false
                if (missingPlugin) {
                    b.append("<dark_gray>[${step.requiresPlugin} not present]")
                } else {
                    b.append(link("/tcp setup enable $index", "<green>[Enable]", "Turn this on"))
                }
                b.append(" ").append(link("/tcp setup disable $index", "<red>[Disable]", "Turn this off"))
            }
            is SetupStep.Choice -> {
                step.options.forEach { opt ->
                    val label = plugin.getMessageList(opt.labelKey).firstOrNull() ?: opt.optionId
                    b.append(link("/tcp setup set $index ${opt.optionId}", "<aqua>[$label]", "Use this value")).append(" ")
                }
            }
        }
        b.append("\n")
        if (index > 0) b.append(link("/tcp setup prev $index", "<gray>[← Prev]", "Back one question")).append("  ")
        b.append(link("/tcp setup skip $index", "<gray>[Skip Question →]", "Leave as-is, next"))
        b.append("\n")
        b.append(link("/tcp setup pause $index", "<yellow>[⏸ Pause Setup]", "Resume later with /tcp setup continue"))
        b.append("  ").append(link("/tcp setup stop", "<red>[⏹ Stop Setup]", "End the tour"))
        return b.toString()
    }

    private fun link(command: String, label: String, hover: String): String =
        "<click:run_command:'$command'><hover:show_text:'<gray>$hover'>$label</hover></click>"
}

package com.esmpfun.bettertrialchambers.setup

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Clickable-chat renderer for the setup tour, the fallback when Paper's Dialog API isn't
 * available (older or non-Paper servers). Uses `sendRichMessage` + `<click:run_command:...>`
 * links, the same style as [com.esmpfun.bettertrialchambers.managers.SnapshotReminderService].
 *
 * Stateless: each button is a `/trial setup <action> <index>` command, so a step renders from
 * its index alone (no per-player session needed on this path).
 */
class SetupTourChat(
    private val plugin: BetterTrialChambers,
    private val controller: SetupController,
) {

    /** Render the step at [index] to [player] (or the completion message when past the end). */
    fun render(player: Player, index: Int) {
        val step = controller.stepAt(index) ?: run { complete(player); return }
        val sb = StringBuilder()
        sb.append("<dark_aqua><st>                    </st> <gold>Setup</gold> <gray>(${index + 1}/${controller.stepCount})</gray> <dark_aqua><st>                    </st>\n")
        sb.append("<yellow><bold>").append(title(step)).append("</bold></yellow>\n")
        desc(step).forEach { sb.append("<gray>").append(it).append("</gray>\n") }
        step.cpuImpact?.let { sb.append("<gray>CPU impact: <").append(it.mini).append(">").append(it.text).append("\n") }
        sb.append("\n").append(currentLine(step)).append("\n")
        sb.append(buttons(step, index))
        player.sendMessage(com.esmpfun.bettertrialchambers.utils.MessageParser.parse(sb.toString()))
    }

    fun complete(player: Player) {
        controller.markComplete()
        val raw = plugin.getMessageList("setup.complete")
            .ifEmpty { listOf("<green>Setup complete! You can re-run <yellow>/trial setup</yellow> anytime.") }
            .joinToString("\n")
        player.sendMessage(com.esmpfun.bettertrialchambers.utils.MessageParser.parse(raw))
    }

    fun intro(player: Player) {
        plugin.getMessageList("setup.intro")
            .ifEmpty { listOf("<gold>Welcome, let's tour the main settings. Nothing is forced; pick what suits your server.") }
            .forEach { player.sendMessage(com.esmpfun.bettertrialchambers.utils.MessageParser.parse(it)) }
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
            val rawVal = if (step.configPath == "vaults.loot-mode") {
                com.esmpfun.bettertrialchambers.models.VaultLootMode.resolve(plugin).name
            } else {
                plugin.config.get(step.configPath)
            }
            val label = step.formatCurrent?.invoke(rawVal)
                ?: (rawVal?.toString() ?: "default")
            "<gray>Currently: <white>$label"
        }
    }

    private fun buttons(step: SetupStep, index: Int): String {
        val b = StringBuilder()
        when (step) {
            is SetupStep.Toggle -> {
                val missingPlugin = step.requiresPlugin?.let { !Bukkit.getPluginManager().isPluginEnabled(it) } ?: false
                if (missingPlugin) {
                    b.append("<dark_gray>[" + plugin.rawMessage("setup.requires-plugin-button",
                        "plugin" to (step.requiresPluginLabel ?: step.requiresPlugin)) + "]")
                } else {
                    b.append(link("/trial setup enable $index", "<green>[Enable]", "Turn this on"))
                }
                b.append(" ").append(link("/trial setup disable $index", "<red>[Disable]", "Turn this off"))
            }
            is SetupStep.Choice -> {
                step.options.forEach { opt ->
                    val label = plugin.getMessageList(opt.labelKey).firstOrNull() ?: opt.optionId
                    b.append(link("/trial setup set $index ${opt.optionId}", "<aqua>[$label]", "Use this value")).append(" ")
                }
            }
        }
        b.append("\n")
        if (index > 0) b.append(link("/trial setup prev $index", "<gray>[Prev]", "Back one question")).append("  ")
        b.append(link("/trial setup skip $index", "<gray>[Skip Question]", "Leave as-is, next"))
        b.append("\n")
        b.append(link("/trial setup pause $index", "<yellow>[⏸ Pause Setup]", "Resume later with /trial setup continue"))
        b.append("  ").append(link("/trial setup stop", "<red>[⏹ Stop Setup]", "End the tour"))
        return b.toString()
    }

    private fun link(command: String, label: String, hover: String): String =
        "<click:run_command:'$command'><hover:show_text:'<gray>$hover'>$label</hover></click>"
}

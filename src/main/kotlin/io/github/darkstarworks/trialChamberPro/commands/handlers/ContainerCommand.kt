package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/tcp container …` — admin management of per-player container loot
 * (`chests.per-player-loot`) templates (v1.5.9). CLI parity with the chamber
 * GUI's Container Loot view.
 *
 *   list <chamber>            — how many templates / player copies exist
 *   materialize <chamber>     — roll + store a template for every container
 *   reset <chamber>           — delete all templates (re-materialize on access)
 *   resetone <chamber> <#>    — reset one template to vanilla (re-roll on access)
 *   clearcopies <chamber>     — drop every player's private copies
 *   tp <chamber> <#>          — teleport to a template (index from `list`)
 *   edit <chamber> <#>        — open a template to edit it
 *
 * Gated on `tcp.admin.containers`. Output is localized via the
 * `container-*` keys in messages.yml (v1.7.1).
 */
class ContainerCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.containers")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.size < 2) {
            usage(sender)
            return
        }
        val action = args[1].lowercase()
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessageComponent("container-usage-action", "action" to action))
            return
        }
        val chamberName = args[2]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }
            when (action) {
                "list" -> {
                    val templates = plugin.containerLootManager.listTemplates(chamber.id)
                    val copies = plugin.containerLootManager.countPlayerCopies(chamber.id)
                    val enabled = plugin.config.getBoolean("chests.per-player-loot", false)
                    // rawMessage: the on/off label is nested into the header's {state} placeholder.
                    val state = plugin.rawMessage(if (enabled) "container-state-on" else "container-state-off")
                    sender.sendMessage(plugin.getMessageComponent("container-list-page-header",
                        "chamber" to chamber.name, "state" to state))
                    sender.sendMessage(plugin.getMessageComponent("container-list-stats",
                        "templates" to templates.size, "copies" to copies))
                    templates.take(20).forEachIndexed { i, t ->
                        val items = t.contents.count { it != null && !it.type.isAir }
                        sender.sendMessage(plugin.getMessageComponent("container-list-item",
                            "index" to (i + 1), "x" to t.pos.x, "y" to t.pos.y, "z" to t.pos.z, "items" to items))
                    }
                    if (templates.size > 20) sender.sendMessage(plugin.getMessageComponent("container-list-more", "count" to (templates.size - 20)))
                    if (templates.isEmpty()) sender.sendMessage(plugin.getMessageComponent("container-list-empty-hint", "chamber" to chamber.name))
                }
                "materialize" -> {
                    sender.sendMessage(plugin.getMessageComponent("container-materialize-start", "chamber" to chamber.name))
                    val created = plugin.containerLootListener.materializeChamber(chamber)
                    sender.sendMessage(plugin.getMessageComponent("container-materialize-done", "count" to created))
                }
                "reset", "cleartemplates" -> {
                    val n = plugin.containerLootManager.clearTemplates(chamber.id)
                    sender.sendMessage(plugin.getMessageComponent("container-cleared-templates", "count" to n))
                }
                "clearcopies" -> {
                    val n = plugin.containerLootManager.clearChamber(chamber.id)
                    sender.sendMessage(plugin.getMessageComponent("container-cleared-copies", "count" to n))
                }
                "resetone" -> {
                    val idx = args.getOrNull(3)?.toIntOrNull()
                    if (idx == null || idx < 1) {
                        sender.sendMessage(plugin.getMessageComponent("container-usage-index",
                            "action" to "resetone", "chamber" to chamber.name))
                        return@launchAsync
                    }
                    val templates = plugin.containerLootManager.listTemplates(chamber.id)
                    val target = templates.getOrNull(idx - 1)
                    if (target == null) {
                        sender.sendMessage(plugin.getMessageComponent("container-no-template", "index" to idx, "count" to templates.size))
                        return@launchAsync
                    }
                    val ok = plugin.containerLootManager.markVanilla(chamber.id, target.pos)
                    if (ok) sender.sendMessage(plugin.getMessageComponent("container-resetone-success", "index" to idx))
                    else sender.sendMessage(plugin.getMessageComponent("container-resetone-failed", "index" to idx))
                }
                "tp", "edit" -> {
                    val player = sender as? Player
                    if (player == null) {
                        sender.sendMessage(plugin.getMessageComponent("player-only"))
                        return@launchAsync
                    }
                    val idx = args.getOrNull(3)?.toIntOrNull()
                    if (idx == null || idx < 1) {
                        sender.sendMessage(plugin.getMessageComponent("container-usage-index",
                            "action" to action, "chamber" to chamber.name))
                        return@launchAsync
                    }
                    val templates = plugin.containerLootManager.listTemplates(chamber.id)
                    val target = templates.getOrNull(idx - 1)
                    if (target == null) {
                        sender.sendMessage(plugin.getMessageComponent("container-no-template", "index" to idx, "count" to templates.size))
                        return@launchAsync
                    }
                    if (action == "tp") {
                        val world = chamber.getWorld()
                        if (world == null) {
                            sender.sendMessage(plugin.getMessageComponent("container-world-not-loaded"))
                            return@launchAsync
                        }
                        val loc = Location(world, target.pos.x + 0.5, target.pos.y + 1.0, target.pos.z + 0.5)
                        player.teleportAsync(loc)
                        sender.sendMessage(plugin.getMessageComponent("container-teleported",
                            "index" to idx, "x" to target.pos.x, "y" to target.pos.y, "z" to target.pos.z))
                    } else {
                        plugin.containerLootListener.openTemplateEditor(player, chamber, target.pos)
                    }
                }
                else -> usage(sender)
            }
        }
    }

    private fun usage(sender: CommandSender) {
        sender.sendMessage(plugin.getMessageComponent("container-usage"))
    }
}

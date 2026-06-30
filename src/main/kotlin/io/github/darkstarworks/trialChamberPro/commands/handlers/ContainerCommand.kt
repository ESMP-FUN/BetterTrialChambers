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
 * Gated on `tcp.admin.containers`. Admin-facing, so output is literal
 * MiniMessage (matching the other extracted handlers) rather than messages.yml.
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
            sender.sendRichMessage("<red>Usage: /tcp container $action <chamber>")
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
                    sender.sendRichMessage("<gold>Container loot — <yellow>${chamber.name}</yellow></gold> <gray>(per-player-loot: ${if (enabled) "<green>on" else "<red>off"}<gray>)")
                    sender.sendRichMessage("<gray>Templates: <yellow>${templates.size}</yellow>  ·  Player copies: <yellow>$copies</yellow>")
                    templates.take(20).forEachIndexed { i, t ->
                        val items = t.contents.count { it != null && !it.type.isAir }
                        sender.sendRichMessage("<gray> #${i + 1} <white>${t.pos.x}, ${t.pos.y}, ${t.pos.z}</white> <dark_gray>— <yellow>$items<gray> item stack(s)")
                    }
                    if (templates.size > 20) sender.sendRichMessage("<dark_gray>…and ${templates.size - 20} more.")
                    if (templates.isEmpty()) sender.sendRichMessage("<gray>No templates yet — run <yellow>/tcp container materialize ${chamber.name}</yellow> or open a container in-world.")
                }
                "materialize" -> {
                    sender.sendRichMessage("<gray>Scanning <yellow>${chamber.name}</yellow> for containers…")
                    val created = plugin.containerLootListener.materializeChamber(chamber)
                    sender.sendRichMessage("<green>Materialized <yellow>$created</yellow> new container template(s).")
                }
                "reset", "cleartemplates" -> {
                    val n = plugin.containerLootManager.clearTemplates(chamber.id)
                    sender.sendRichMessage("<green>Cleared <yellow>$n</yellow> template(s) — they re-materialize from each container's loot table on next access.")
                }
                "clearcopies" -> {
                    val n = plugin.containerLootManager.clearChamber(chamber.id)
                    sender.sendRichMessage("<green>Cleared <yellow>$n</yellow> per-player container copy/copies — everyone re-clones the template next open.")
                }
                "resetone" -> {
                    val idx = args.getOrNull(3)?.toIntOrNull()
                    if (idx == null || idx < 1) {
                        sender.sendRichMessage("<red>Usage: /tcp container resetone ${chamber.name} <#>  (index from /tcp container list)")
                        return@launchAsync
                    }
                    val templates = plugin.containerLootManager.listTemplates(chamber.id)
                    val target = templates.getOrNull(idx - 1)
                    if (target == null) {
                        sender.sendRichMessage("<red>No template #$idx — there are ${templates.size}.")
                        return@launchAsync
                    }
                    val ok = plugin.containerLootManager.markVanilla(chamber.id, target.pos)
                    if (ok) sender.sendRichMessage("<green>Reverted container #$idx to vanilla — it rolls fresh loot per player on each open.")
                    else sender.sendRichMessage("<red>Couldn't revert container #$idx.")
                }
                "tp", "edit" -> {
                    val player = sender as? Player
                    if (player == null) {
                        sender.sendMessage(plugin.getMessageComponent("player-only"))
                        return@launchAsync
                    }
                    val idx = args.getOrNull(3)?.toIntOrNull()
                    if (idx == null || idx < 1) {
                        sender.sendRichMessage("<red>Usage: /tcp container $action ${chamber.name} <#>  (index from /tcp container list)")
                        return@launchAsync
                    }
                    val templates = plugin.containerLootManager.listTemplates(chamber.id)
                    val target = templates.getOrNull(idx - 1)
                    if (target == null) {
                        sender.sendRichMessage("<red>No template #$idx — there are ${templates.size}.")
                        return@launchAsync
                    }
                    if (action == "tp") {
                        val world = chamber.getWorld()
                        if (world == null) {
                            sender.sendRichMessage("<red>Chamber world isn't loaded.")
                            return@launchAsync
                        }
                        val loc = Location(world, target.pos.x + 0.5, target.pos.y + 1.0, target.pos.z + 0.5)
                        player.teleportAsync(loc)
                        sender.sendRichMessage("<green>Teleported to template #$idx (<yellow>${target.pos.x}, ${target.pos.y}, ${target.pos.z}</yellow>).")
                    } else {
                        plugin.containerLootListener.openTemplateEditor(player, chamber, target.pos)
                    }
                }
                else -> usage(sender)
            }
        }
    }

    private fun usage(sender: CommandSender) {
        sender.sendRichMessage("<gold>/tcp container</gold> <gray>— list | materialize | reset | resetone <#> | clearcopies | tp <#> | edit <#> <white><chamber></white></gray>")
    }
}

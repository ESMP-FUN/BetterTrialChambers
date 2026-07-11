package com.esmpfun.bettertrialchambers.commands.handlers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.VaultType
import org.bukkit.command.CommandSender

/**
 * `/trial loot <set|clear|info|list> ...` — manages per-chamber loot table
 * overrides. Wraps the four sub-actions (`set`, `clear`, `info`, `list`) that
 * were previously private methods on `TCPCommand`.
 *
 * Extracted in v1.3.0 Phase 3.
 */
class LootCommand(private val plugin: BetterTrialChambers) : SubcommandHandler {

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("btc.admin.loot")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-loot"))
            return
        }

        when (args[1].lowercase()) {
            "set" -> handleSet(sender, args)
            "clear" -> handleClear(sender, args)
            "info" -> handleInfo(sender, args)
            "list" -> handleList(sender)
            "audit" -> handleAudit(sender)
            else -> sender.sendMessage(plugin.getMessageComponent("usage-loot"))
        }
    }

    /**
     * `/trial loot audit` — lists loot entries that lack serialized NBT and look
     * like pre-1.5.0 leftovers (enchanted books without enchantments, potions
     * without potion type, etc). The faithful-loot fix only applies to entries
     * added after upgrading; older rows have to be re-entered through the
     * loot editor.
     */
    private fun handleAudit(sender: CommandSender) {
        val legacy = plugin.lootManager.findLegacyItems()
        if (legacy.isEmpty()) {
            sender.sendMessage(plugin.getMessageComponent("loot-audit-clean"))
            return
        }

        sender.sendMessage(plugin.getMessageComponent("loot-audit-found", "count" to legacy.size))
        // Group by table:pool for readability; cap output at ~50 lines to avoid spam.
        val capped = legacy.take(50)
        capped.groupBy { "${it.table} / ${it.pool}" }.forEach { (header, items) ->
            sender.sendMessage(plugin.getMessageComponent("loot-audit-group-list-item", "group" to header))
            items.forEach { ref ->
                sender.sendMessage(plugin.getMessageComponent("loot-audit-entry-list-item",
                    "kind" to ref.kind, "index" to ref.index, "material" to ref.material.name, "reason" to ref.reason))
            }
        }
        if (legacy.size > capped.size) {
            sender.sendMessage(plugin.getMessageComponent("loot-audit-more-list-item", "count" to (legacy.size - capped.size)))
        }
        sender.sendMessage(plugin.getMessageComponent("loot-audit-hint"))
    }

    /** `/trial loot set <chamber> <normal|ominous> <table>` */
    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            sender.sendMessage(plugin.getMessageComponent("usage-loot-set"))
            return
        }

        val chamberName = args[2]
        val typeStr = args[3].lowercase()
        val tableName = args[4]

        val vaultType = when (typeStr) {
            "normal" -> VaultType.NORMAL
            "ominous" -> VaultType.OMINOUS
            else -> {
                sender.sendMessage(plugin.getMessageComponent("error-invalid-type"))
                return
            }
        }

        if (plugin.lootManager.getTable(tableName) == null) {
            sender.sendMessage(plugin.getMessageComponent("loot-table-not-found", "table" to tableName))
            return
        }

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            val success = plugin.chamberManager.setLootTable(chamberName, vaultType, tableName)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("loot-set-success",
                    "type" to plugin.vaultTypeDisplay(vaultType),
                    "chamber" to chamberName,
                    "table" to tableName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("error-loot-set-failed"))
            }
        }
    }

    /** `/trial loot clear <chamber> [normal|ominous|all]` */
    private fun handleClear(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessageComponent("usage-loot-clear"))
            return
        }

        val chamberName = args[2]
        val typeStr = args.getOrNull(3)?.lowercase() ?: "all"

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            when (typeStr) {
                "normal" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.NORMAL, null)
                    sender.sendMessage(plugin.getMessageComponent("loot-clear-success", "chamber" to chamberName))
                }
                "ominous" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessageComponent("loot-clear-success", "chamber" to chamberName))
                }
                "all" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.NORMAL, null)
                    plugin.chamberManager.setLootTable(chamberName, VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessageComponent("loot-clear-success", "chamber" to chamberName))
                }
                else -> sender.sendMessage(plugin.getMessageComponent("error-invalid-type-loot-clear"))
            }
        }
    }

    /** `/trial loot info <chamber>` */
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessageComponent("usage-loot-info"))
            return
        }

        val chamberName = args[2]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("loot-info-header", "chamber" to chamberName))
            val normalTable = chamber.normalLootTable ?: plugin.rawMessage("loot-info-default")
            val ominousTable = chamber.ominousLootTable ?: plugin.rawMessage("loot-info-default")
            sender.sendMessage(plugin.getMessageComponent("loot-info-normal", "table" to normalTable))
            sender.sendMessage(plugin.getMessageComponent("loot-info-ominous", "table" to ominousTable))
        }
    }

    /** `/trial loot list` */
    private fun handleList(sender: CommandSender) {
        val tables = plugin.lootManager.getLootTableNames()
        if (tables.isEmpty()) {
            sender.sendMessage(plugin.getMessageComponent("error-no-loot-tables"))
            return
        }

        sender.sendMessage(plugin.getMessageComponent("loot-list-header"))
        tables.sorted().forEach { tableName ->
            sender.sendMessage(plugin.getMessageComponent("loot-list-item", "table" to tableName))
        }
    }
}

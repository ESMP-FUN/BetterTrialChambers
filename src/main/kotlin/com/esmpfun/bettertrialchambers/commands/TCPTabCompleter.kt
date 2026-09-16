package com.esmpfun.bettertrialchambers.commands

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.utils.WEVarStore
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Tab completion for /trial commands.
 */
class TCPTabCompleter(private val plugin: BetterTrialChambers) : TabCompleter {

    private val subcommands = listOf(
        "help", "reload", "generate", "paste", "scan", "setexit",
        "snapshot", "list", "info", "delete", "vault", "key",
        "stats", "leaderboard", "lb", "top", "reset", "menu", "loot", "mobs", "give",
        "pause", "resume", "rename", "dungeon", "container", "claims", "setup", "debug", "update"
    )
    /**
     * The permission each subcommand's own handler checks before doing anything.
     *
     * Used to keep the tab-completion list to the things the person typing can
     * actually run. Without it, every player pressing tab after `/trial` was
     * shown all thirty commands, nearly all of which answer "you don't have
     * permission", including `delete` and `reload`.
     *
     * **Anything missing from this map stays visible.** Taken from the checks in
     * the handlers themselves rather than written from memory, and a subcommand
     * left out of it should show up and be refused, which is the old behaviour,
     * rather than vanish for somebody who is allowed to use it.
     */
    private val subcommandPermissions = mapOf(
        "claims" to "btc.admin.reload",
        "debug" to "btc.admin.reload",
        "delete" to "btc.admin.create",
        "info" to "btc.admin",
        "key" to "btc.admin.menu",
        "leaderboard" to "btc.leaderboard",
        "lb" to "btc.leaderboard",
        "top" to "btc.leaderboard",
        "list" to "btc.admin",
        "menu" to "btc.admin.menu",
        "paste" to "btc.admin.generate",
        "pause" to "btc.admin.pause",
        "reload" to "btc.admin.reload",
        "rename" to "btc.admin.create",
        "reset" to "btc.admin.menu",
        "resume" to "btc.admin.pause",
        "scan" to "btc.admin.scan",
        "setexit" to "btc.admin.create",
        "snapshot" to "btc.admin.snapshot",
        "stats" to "btc.stats",
        "update" to "btc.admin",
        "vault" to "btc.admin.menu",
        // These are handled by their own classes rather than a method here;
        // same idea, the permission is the one that class checks first.
        "container" to "btc.admin.containers",
        "dungeon" to "btc.admin.generate",
        "generate" to "btc.admin.generate",
        "give" to "btc.give",
        "loot" to "btc.admin.loot",
        "mobs" to "btc.admin.mobs",
        "setup" to "btc.admin.setup",
    )

    private fun canSee(sender: CommandSender, subcommand: String): Boolean {
        val permission = subcommandPermissions[subcommand] ?: return true
        return sender.hasPermission(permission)
    }

    private val setupActions = listOf("start", "continue")

    private val claimsActions = listOf("scan")
    private val debugActions = listOf("schema")

    private val dungeonActions = listOf("pos1", "pos2", "capture", "generate", "list", "delete", "import")
    private val containerActions = listOf("list", "materialize", "reset", "clearcopies", "tp", "edit")

    private val snapshotActions = listOf("create", "update", "restore", "missing")
    private val updateActions = listOf("check", "download", "apply", "ignore", "unignore", "restore", "status")
    private val statTypes = listOf("chambers", "normal", "ominous", "mobs", "time")
    private val lootActions = listOf("set", "clear", "info", "list", "audit")
    private val vaultTypes = listOf("normal", "ominous")
    private val vaultActions = listOf("reset", "unlockall")
    private val mobsActions = listOf("provider", "add", "remove", "list")
    private val mobsWaveTypes = listOf("normal", "ominous")

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        return when (args.size) {
            1 -> {
                // First argument - subcommand, narrowed to what this person can run.
                subcommands.filter { it.startsWith(args[0].lowercase()) && canSee(sender, it) }
            }
            2 -> {
                // Second argument - depends on subcommand
                when (args[0].lowercase()) {
                    "snapshot" -> snapshotActions.filter { it.startsWith(args[1].lowercase()) }
                    "update" -> updateActions.filter { it.startsWith(args[1].lowercase()) }
                    "setup" -> setupActions.filter { it.startsWith(args[1].lowercase()) }
                    "generate" -> listOf("value", "coords", "wand", "blocks").filter { it.startsWith(args[1].lowercase()) }
                    "paste" -> {
                        // Schematic names
                        try {
                            plugin.schematicManager.listSchematics().filter { it.startsWith(args[1].lowercase()) }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    "scan" -> {
                        // `add` (grow bounds into missed sections) + chamber names.
                        (listOf("add") + getChamberNames(sender)).filter { it.startsWith(args[1].lowercase()) }
                    }
                    "setexit", "info", "delete", "pause", "resume", "rename", "menu" -> {
                        // Chamber names (menu: optional deep-link into the chamber's GUI)
                        getChamberNames(sender).filter { it.startsWith(args[1].lowercase()) }
                    }
                    "reset" -> {
                        // Queue actions + chamber names
                        (listOf("pending", "confirm") + getChamberNames(sender)).filter { it.startsWith(args[1].lowercase()) }
                    }
                    "stats" -> {
                        // Player names for stats
                        plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    "leaderboard", "lb", "top" -> {
                        // Stat types for leaderboard
                        statTypes.filter { it.startsWith(args[1].lowercase()) }
                    }
                    "loot" -> {
                        // Loot subcommands
                        lootActions.filter { it.startsWith(args[1].lowercase()) }
                    }
                    "mobs" -> {
                        // Chamber name or the literal "providers"
                        (getChamberNames(sender) + "providers").filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    "give" -> {
                        getPresetNames().filter { it.startsWith(args[1].lowercase()) }
                    }
                    "dungeon" -> dungeonActions.filter { it.startsWith(args[1].lowercase()) }
                    "container", "containers" -> containerActions.filter { it.startsWith(args[1].lowercase()) }
                    "claims" -> claimsActions.filter { it.startsWith(args[1].lowercase()) }
                    "debug" -> debugActions.filter { it.startsWith(args[1].lowercase()) }
                    "list" -> listOf("current").filter { it.startsWith(args[1].lowercase()) }
                    "vault" -> vaultActions.filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            3 -> {
                // Third argument
                when (args[0].lowercase()) {
                    "snapshot" -> when (args[1].lowercase()) {
                        // `create`/`update` also accept the literal `all` (bulk backfill).
                        "create", "update" -> (listOf("all") + getChamberNames(sender)).filter { it.startsWith(args[2].lowercase()) }
                        "missing" -> emptyList() // optional page number, no completion
                        else -> getChamberNames(sender).filter { it.startsWith(args[2].lowercase()) }
                    }
                    "container", "containers" -> getChamberNames(sender).filter { it.startsWith(args[2].lowercase()) }
                    "vault" -> when (args[1].lowercase()) {
                        // `unlockall` also accepts the literal `all` (every chamber at once).
                        "unlockall" -> (listOf("all") + getChamberNames(sender)).filter { it.startsWith(args[2].lowercase()) }
                        "reset" -> getChamberNames(sender).filter { it.startsWith(args[2].lowercase()) }
                        else -> emptyList()
                    }
                    "scan" -> if (args[1].equals("add", ignoreCase = true))
                        getChamberNames(sender).filter { it.startsWith(args[2].lowercase()) } else emptyList()
                    "dungeon" -> {
                        if (args[1].equals("delete", ignoreCase = true)) {
                            try { plugin.roomTemplateManager.list().filter { it.startsWith(args[2], ignoreCase = true) } }
                            catch (_: Exception) { emptyList() }
                        } else emptyList()
                    }
                    "reset" -> {
                        if (args[1].equals("confirm", ignoreCase = true)) {
                            try { (plugin.resetManager.pendingResetNames() + "all").filter { it.startsWith(args[2], ignoreCase = true) } }
                            catch (_: Exception) { emptyList() }
                        } else emptyList()
                    }
                    "generate" -> {
                        when (args[1].lowercase()) {
                            "value" -> {
                                val ops = listOf("save", "list", "delete")
                                val names = try { WEVarStore.list(plugin.dataFolder).map { it.first } } catch (_: Exception) { emptyList() }
                                (ops + names).filter { it.startsWith(args[2].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                    "loot" -> {
                        // Chamber names for set/clear/info
                        when (args[1].lowercase()) {
                            "set", "clear", "info" -> getChamberNames(sender).filter { it.startsWith(args[2].lowercase()) }
                            else -> emptyList()
                        }
                    }
                    "mobs" -> {
                        // /trial mobs <chamber> <action>
                        if (args[1].equals("providers", ignoreCase = true)) emptyList()
                        else mobsActions.filter { it.startsWith(args[2].lowercase()) }
                    }
                    "give" -> {
                        // /trial give <preset> <player>
                        plugin.server.onlinePlayers.map { it.name }
                            .filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                    else -> emptyList()
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "generate" -> {
                        when (args[1].lowercase()) {
                            "value" -> {
                                when (args[2].lowercase()) {
                                    "delete" -> {
                                        try { WEVarStore.list(plugin.dataFolder).map { it.first } } catch (_: Exception) { emptyList() }
                                            .filter { it.startsWith(args[3].lowercase()) }
                                    }
                                    else -> emptyList()
                                }
                            }
                            else -> emptyList()
                        }
                    }
                    "loot" -> {
                        when (args[1].lowercase()) {
                            "set" -> vaultTypes.filter { it.startsWith(args[3].lowercase()) }
                            "clear" -> (vaultTypes + "all").filter { it.startsWith(args[3].lowercase()) }
                            else -> emptyList()
                        }
                    }
                    "mobs" -> {
                        // /trial mobs <chamber> <action> <arg>
                        when (args[2].lowercase()) {
                            "provider" -> {
                                val providers = try {
                                    plugin.trialMobProviderRegistry.all().map { it.id }
                                } catch (_: Exception) { emptyList() }
                                (providers + listOf("vanilla", "none"))
                                    .filter { it.startsWith(args[3].lowercase()) }
                            }
                            "add", "remove" -> mobsWaveTypes.filter { it.startsWith(args[3].lowercase()) }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            5 -> {
                when (args[0].lowercase()) {
                    "loot" -> {
                        when (args[1].lowercase()) {
                            "set" -> {
                                // Loot table names
                                getLootTableNames().filter { it.startsWith(args[4].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    /**
     * v1.7.2: chamber names are only suggested to staff. Since 1.7.1 the base
     * /trial command is open to everyone (stats/leaderboard), which made
     * tab-completion leak every chamber name to regular players.
     */
    private fun getChamberNames(sender: org.bukkit.command.CommandSender): List<String> {
        if (!sender.hasPermission("btc.admin")) return emptyList()
        return try {
            val names = plugin.chamberManager.getCachedChamberNames()
            names.ifEmpty { emptyList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getPresetNames(): List<String> {
        return try {
            plugin.spawnerPresetManager.getNames().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getLootTableNames(): List<String> {
        return try {
            plugin.lootManager.getLootTableNames().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.commands.handlers.GenerateCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.GiveCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.LootCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.MobsCommand
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import io.github.darkstarworks.trialChamberPro.utils.RegionUtil
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main command dispatcher for `/tcp` subcommands. Large/self-contained handlers
 * (`generate`, `mobs`, the `loot` family) live in `commands/handlers/` as
 * dedicated [SubcommandHandler] classes — see v1.3.0 Phase 3 in the changelog.
 * Smaller handlers remain inline as private methods.
 */
class TCPCommand(private val plugin: TrialChamberPro) : CommandExecutor {

    private val generateHandler = GenerateCommand(plugin)
    private val lootHandler = LootCommand(plugin)
    private val mobsHandler = MobsCommand(plugin)
    private val giveHandler = GiveCommand(plugin)
    private val dungeonHandler = io.github.darkstarworks.trialChamberPro.commands.handlers.DungeonCommand(plugin)
    private val containerHandler = io.github.darkstarworks.trialChamberPro.commands.handlers.ContainerCommand(plugin)
    private val setupHandler = io.github.darkstarworks.trialChamberPro.commands.handlers.SetupCommand(plugin, plugin.setupController)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        // Guard commands until plugin finished async initialization, but allow help
        val sub = args[0].lowercase()
        if (!plugin.isReady && sub !in setOf("help")) {
            sender.sendMessage(plugin.getMessageComponent("plugin-starting-up"))
            return true
        }

        when (sub) {
            "help" -> sendHelp(sender)
            "reload" -> handleReload(sender)
            "scan" -> handleScan(sender, args)
            "setexit" -> handleSetExit(sender, args)
            "snapshot" -> handleSnapshot(sender, args)
            "reset" -> handleReset(sender, args)
            "list" -> handleList(sender, args)
            "info" -> handleInfo(sender, args)
            "delete" -> handleDelete(sender, args)
            "vault" -> handleVault(sender, args)
            "key" -> handleKey(sender, args)
            "stats" -> handleStats(sender, args)
            "leaderboard", "lb", "top" -> handleLeaderboard(sender, args)
            "generate" -> generateHandler.execute(sender, args)
            "dungeon" -> dungeonHandler.execute(sender, args)
            "paste" -> handlePaste(sender, args)
            "menu" -> handleMenu(sender, args)
            "loot" -> lootHandler.execute(sender, args)
            "mobs" -> mobsHandler.execute(sender, args)
            "give" -> giveHandler.execute(sender, args)
            "pause" -> handlePause(sender, args)
            "resume" -> handleResume(sender, args)
            "rename" -> handleRename(sender, args)
            "container", "containers" -> containerHandler.execute(sender, args)
            "claims" -> handleClaims(sender, args)
            "setup" -> setupHandler.execute(sender, args)
            "debug" -> handleDebug(sender, args)
            else -> sender.sendMessage(plugin.getMessageComponent("unknown-command"))
        }

        return true
    }

    private fun sendHelp(sender: CommandSender) {
        // Order mirrors the logical grouping in HelpMenuView: browse, create,
        // manage, loot/mobs, players, admin. Keep this list in sync with both
        // the GUI tiles and the messages.yml `help-*` keys.
        sender.sendMessage(plugin.getMessageComponent("help-header"))
        // Browse
        sender.sendMessage(plugin.getMessageComponent("help-list"))
        sender.sendMessage(plugin.getMessageComponent("help-info"))
        // Create
        sender.sendMessage(plugin.getMessageComponent("help-generate"))
        sender.sendRichMessage("<yellow>/tcp dungeon <create rooms & generate> <gray>- Procedural dungeon assembly")
        sender.sendMessage(plugin.getMessageComponent("help-paste"))
        // Manage
        sender.sendMessage(plugin.getMessageComponent("help-scan"))
        sender.sendMessage(plugin.getMessageComponent("help-setexit"))
        sender.sendMessage(plugin.getMessageComponent("help-snapshot"))
        sender.sendMessage(plugin.getMessageComponent("help-snapshot-bulk"))
        sender.sendMessage(plugin.getMessageComponent("help-snapshot-missing"))
        sender.sendMessage(plugin.getMessageComponent("help-reset"))
        sender.sendMessage(plugin.getMessageComponent("help-pause"))
        sender.sendMessage(plugin.getMessageComponent("help-resume"))
        sender.sendMessage(plugin.getMessageComponent("help-rename"))
        sender.sendMessage(plugin.getMessageComponent("help-delete"))
        // Loot & mobs
        sender.sendMessage(plugin.getMessageComponent("help-loot"))
        sender.sendMessage(plugin.getMessageComponent("help-mobs"))
        // Players & rewards
        sender.sendMessage(plugin.getMessageComponent("help-vault"))
        sender.sendMessage(plugin.getMessageComponent("help-key"))
        sender.sendMessage(plugin.getMessageComponent("help-give"))
        sender.sendMessage(plugin.getMessageComponent("help-stats"))
        sender.sendMessage(plugin.getMessageComponent("help-leaderboard"))
        // Admin
        sender.sendRichMessage("<yellow>/tcp setup <gray>- Friendly tour of the main settings (opt-in; Enable/Skip/Disable each)")
        sender.sendMessage(plugin.getMessageComponent("help-menu"))
        sender.sendRichMessage("<yellow>/tcp container <list|materialize|reset|clearcopies|tp|edit> <chamber> <gray>- Manage per-player container loot")
        sender.sendRichMessage("<yellow>/tcp claims scan <gray>- Find chambers overlapping land-claim plugin claims")
        sender.sendRichMessage("<yellow>/tcp debug schema <gray>- Print database table columns (diagnostics)")
        sender.sendMessage(plugin.getMessageComponent("help-reload"))
    }

    private fun handleDebug(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.reload")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.getOrNull(1)?.lowercase() != "schema") {
            sender.sendRichMessage("<yellow>/tcp debug schema <gray>- Print each database table's actual columns")
            return
        }
        sender.sendRichMessage("<gray>Reading database schema…")
        plugin.launchAsync {
            val schema = plugin.databaseManager.describeSchema()
            plugin.scheduler.runTask(Runnable {
                sender.sendRichMessage("<gold>TCP database schema <gray>(${plugin.databaseManager.databaseType}):")
                for ((table, cols) in schema) {
                    if (cols.isEmpty()) {
                        sender.sendRichMessage("<red>$table<gray>: (missing table)")
                    } else {
                        val flag = if (table == io.github.darkstarworks.trialChamberPro.database.DatabaseManager.STATS_TABLE && "player_uuid" !in cols) "<red>" else "<green>"
                        sender.sendRichMessage("$flag$table<gray>: ${cols.joinToString(", ")}")
                    }
                }
            })
        }
    }

    private fun handleClaims(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.reload")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.getOrNull(1)?.lowercase() != "scan") {
            sender.sendRichMessage("<yellow>/tcp claims scan <gray>- Scan registered chambers for land-claim conflicts")
            return
        }
        if (!plugin.claimIntegrationManager.hasActiveProvider()) {
            sender.sendRichMessage(
                "<yellow>No land-claim integration is active. Install Residence, Lands, or GriefPrevention " +
                    "and enable it under <gray>protection.*-integration<yellow> in config.yml."
            )
            return
        }
        sender.sendRichMessage("<gray>Scanning chambers for claim conflicts…")
        plugin.scheduler.runTask(Runnable {
            val conflicts = plugin.claimIntegrationManager.scanAndLog()
            sender.sendRichMessage(
                if (conflicts > 0) {
                    "<red>Found $conflicts chamber(s) overlapping existing claims — see the console for details."
                } else {
                    "<green>No claim conflicts found."
                }
            )
        })
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("tcp.admin.reload")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        plugin.reloadPluginConfig()
        plugin.chamberManager.clearCache()
        plugin.vaultManager.clearCache()
        sender.sendMessage(plugin.getMessageComponent("reload-success"))
    }



    private fun handleScan(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.scan")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-scan"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("scan-started", "chamber" to chamberName))
            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
            sender.sendMessage(plugin.getMessageComponent("scan-complete",
                "vaults" to vaults,
                "spawners" to spawners,
                "pots" to pots
            ))
        }
    }

    private fun handleSetExit(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-setexit"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.setExitLocation(chamberName, sender.location)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("exit-set", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleSnapshot(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.snapshot")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-snapshot"))
            return
        }

        val action = args[1].lowercase()

        when (action) {
            // `create`, `update` and `restore` all accept the chamber name as an
            // optional final arg — omit it while standing inside a chamber and the
            // command targets that one. `update` is the discoverable alias for
            // "save my edits". (`create` and `update` both (re)capture, overwriting.)
            "create", "update" -> {
                val target = args.getOrNull(2)
                if (target != null && target.equals("all", ignoreCase = true)) {
                    // `/tcp snapshot create all [force]` — backfill every chamber missing a
                    // snapshot (or re-capture all with `force`), staggered to protect TPS.
                    snapshotAll(sender, force = args.getOrNull(3)?.equals("force", ignoreCase = true) == true)
                } else {
                    val chamberName = resolveSnapshotChamberName(sender, target) ?: return
                    captureSnapshot(sender, chamberName)
                }
            }
            "missing" -> handleSnapshotMissing(sender, args.getOrNull(2))
            "restore" -> {
                val chamberName = resolveSnapshotChamberName(sender, args.getOrNull(2)) ?: return
                plugin.launchAsync {
                    val chamber = plugin.chamberManager.getChamber(chamberName)
                    if (chamber == null) {
                        sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                        return@launchAsync
                    }

                    sender.sendMessage(plugin.getMessageComponent("snapshot-restoring", "chamber" to chamberName))

                    // Pass player for WorldEdit undo support if sender is a player
                    val initiatingPlayer = sender as? Player
                    val success = plugin.resetManager.resetChamber(chamber, initiatingPlayer)
                    if (success) {
                        sender.sendMessage(plugin.getMessageComponent("snapshot-restored"))
                    } else {
                        sender.sendMessage(plugin.getMessageComponent("snapshot-failed", "error" to "Check console for details"))
                    }
                }
            }
            else -> {
                sender.sendMessage(plugin.getMessageComponent("usage-snapshot"))
            }
        }
    }

    /**
     * Resolve the chamber a snapshot subcommand should act on: the [explicit] name
     * if one was given, otherwise the chamber the sender is standing inside. Sends
     * the appropriate usage/error message and returns null when it can't resolve
     * (console without a name, or a player not inside any registered chamber).
     */
    private fun resolveSnapshotChamberName(sender: CommandSender, explicit: String?): String? {
        if (explicit != null) return explicit
        val player = sender as? Player
        if (player == null) {
            // No name + not a player → nothing to infer from. Show usage.
            sender.sendMessage(plugin.getMessageComponent("usage-snapshot"))
            return null
        }
        val chamber = plugin.chamberManager.getCachedChamberAt(player.location)
        if (chamber == null) {
            sender.sendMessage(plugin.getMessageComponent("snapshot-not-in-chamber"))
            return null
        }
        return chamber.name
    }

    /** True if the chamber has no usable on-disk snapshot (unset path, or the `.dat` is gone). */
    private fun isSnapshotMissing(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber): Boolean {
        val path = chamber.snapshotFile
        return path.isNullOrBlank() || !java.io.File(path).isFile
    }

    /** Suspends for [ticks] server ticks via the scheduler (tick-paced, so it scales with TPS). */
    private suspend fun waitTicks(ticks: Long) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            plugin.scheduler.runTaskLater(Runnable { cont.resume(Unit) {} }, ticks)
        }
    }

    /**
     * Bulk-capture snapshots for every registered chamber missing one (or *all* chambers when
     * [force]). Runs sequentially and waits 20 ticks **after each capture finishes** before the
     * next, because a capture is a single heavy main-thread pass over the whole chamber — firing
     * 60+ back-to-back would tank TPS. Progress is reported every 10 chambers, not per-chamber.
     */
    private fun snapshotAll(sender: CommandSender, force: Boolean) {
        plugin.launchAsync {
            val all = plugin.chamberManager.getAllChambers()
            val targets = (if (force) all else all.filter { isSnapshotMissing(it) }).sortedBy { it.name }
            if (targets.isEmpty()) {
                sender.sendRichMessage(
                    if (all.isEmpty()) "<yellow>No chambers are registered yet."
                    else "<green>All <yellow>${all.size}</yellow> registered chambers already have a snapshot. " +
                        "<gray>(use <yellow>/tcp snapshot create all force</yellow> to re-capture them)"
                )
                return@launchAsync
            }
            sender.sendRichMessage(
                "<gold>Snapshotting <yellow>${targets.size}</yellow> chamber(s)" +
                    (if (force) " <gray>(force — re-capturing all)</gray>" else " <gray>(missing only)</gray>") +
                    " <gray>— one every 20 ticks; this can take a while…"
            )
            var created = 0
            var failed = 0
            for ((index, chamber) in targets.withIndex()) {
                try {
                    val file = plugin.snapshotManager.createSnapshot(chamber)
                    plugin.chamberManager.setSnapshotFile(chamber.name, file.absolutePath)
                    created++
                } catch (e: Exception) {
                    failed++
                    plugin.logger.warning("Bulk snapshot failed for '${chamber.name}': ${e.message}")
                }
                val done = index + 1
                if (done % 10 == 0 && done < targets.size) {
                    sender.sendRichMessage("<gray>… <yellow>$done</yellow>/<yellow>${targets.size}</yellow> done")
                }
                if (done < targets.size) waitTicks(20)
            }
            sender.sendRichMessage(
                "<green>Snapshot backfill complete: <yellow>$created</yellow> created" +
                    (if (failed > 0) "<gray>, <red>$failed failed</red> (see console)" else "") + "<green>."
            )
        }
    }

    /**
     * Paginated list (10/page) of registered chambers with no snapshot, each row carrying a
     * clickable `[Create]` that runs `/tcp snapshot create <name>`, plus a `[Create all]` header
     * button. Mirrors `/tcp list`. Backs the `[list]` link in the join/periodic snapshot reminder.
     */
    private fun handleSnapshotMissing(sender: CommandSender, pageArg: String?) {
        val requestedPage = pageArg?.toIntOrNull() ?: 1
        plugin.launchAsync {
            val missing = plugin.chamberManager.getAllChambers().filter { isSnapshotMissing(it) }.sortedBy { it.name }
            if (missing.isEmpty()) {
                sender.sendRichMessage("<green>Every registered chamber has a snapshot. ✔")
                return@launchAsync
            }
            val pageSize = 10
            val maxPage = (missing.size + pageSize - 1) / pageSize
            val page = requestedPage.coerceIn(1, maxPage)
            val start = (page - 1) * pageSize
            val pageItems = missing.subList(start, minOf(start + pageSize, missing.size))

            sender.sendRichMessage(
                "<gold>Chambers missing a snapshot <gray>(page <yellow>$page</yellow>/<yellow>$maxPage</yellow>, " +
                    "<yellow>${missing.size}</yellow> total)</gray> " +
                    "<click:run_command:'/tcp snapshot create all'><hover:show_text:'<gray>Snapshot all of them now (staggered)'><green>[Create all]</green></hover></click>"
            )
            pageItems.forEach { chamber ->
                if (sender is Player) {
                    val name = chamber.name
                    sender.sendRichMessage(
                        "<gray>• <yellow>$name</yellow> <gray>— ${chamber.world}</gray> " +
                            "<click:run_command:'/tcp snapshot create $name'><hover:show_text:'<gray>Create snapshot for <yellow>$name'>" +
                            "<green>[Create]</green></hover></click>"
                    )
                } else {
                    sender.sendRichMessage("<gray>• <yellow>${chamber.name}</yellow> <gray>— ${chamber.world} <dark_gray>(/tcp snapshot create ${chamber.name})")
                }
            }
            if (maxPage > 1) {
                val prev = if (page > 1) "<click:run_command:'/tcp snapshot missing ${page - 1}'><green>« Prev</green></click>"
                else "<dark_gray>« Prev</dark_gray>"
                val next = if (page < maxPage) "<click:run_command:'/tcp snapshot missing ${page + 1}'><green>Next »</green></click>"
                else "<dark_gray>Next »</dark_gray>"
                sender.sendRichMessage("$prev <gray>|</gray> $next")
            }
        }
    }

    /** Capture (or re-capture) a chamber's snapshot, overwriting any existing one. Shared by create/update. */
    private fun captureSnapshot(sender: CommandSender, chamberName: String) {
        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("snapshot-creating", "chamber" to chamberName))
            val file = try {
                plugin.snapshotManager.createSnapshot(chamber)
            } catch (e: Exception) {
                plugin.logger.severe("Snapshot creation failed for chamber ${chamberName}: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                sender.sendMessage(plugin.getMessageComponent("snapshot-failed", "error" to (e.message ?: "see console")))
                return@launchAsync
            }

            // v1.5.1: verify the DB update before claiming success. Previously a failed
            // DB write left the chamber row with snapshot_file = NULL, so the *next* reset
            // logged "No snapshot found" and skipped restoration even though the .dat file
            // was on disk — and the user got a misleading "snapshot created" message.
            val linked = try {
                plugin.chamberManager.setSnapshotFile(chamberName, file.absolutePath)
            } catch (e: Exception) {
                plugin.logger.severe("Snapshot DB link failed for chamber ${chamberName}: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                false
            }

            if (!linked) {
                plugin.logger.severe(
                    "Snapshot file written to ${file.absolutePath} but the chamber DB row was NOT updated. " +
                        "The chamber will reset as if no snapshot exists. Re-run /tcp snapshot create."
                )
                sender.sendMessage(plugin.getMessageComponent("snapshot-failed",
                    "error" to "file saved but DB link failed — see console"))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("snapshot-created",
                "chamber" to chamberName,
                "blocks" to chamber.getVolume(),
                "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
            ))
        }
    }

    private fun handleList(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        val sub = args.getOrNull(1)?.lowercase()

        // /tcp list current|here|near[est] — find the chamber you're in, else the nearest.
        if (sub != null && sub in setOf("current", "here", "near", "nearest")) {
            val player = sender as? Player
            if (player == null) {
                sender.sendMessage(plugin.getMessageComponent("player-only"))
                return
            }
            val loc = player.location // snapshot on the command thread for async use
            plugin.launchAsync {
                val worldName = loc.world?.name
                val chambers = plugin.chamberManager.getAllChambers().filter { it.world == worldName }
                val inside = chambers.firstOrNull { it.contains(loc) }
                if (inside != null) {
                    sender.sendRichMessage(
                        "<green>You are inside <yellow>${inside.name}</yellow> <gray>(volume ${inside.getVolume()})"
                    )
                    return@launchAsync
                }
                val nearest = chambers.minByOrNull { centerDistanceSq(it, loc) }
                if (nearest == null) {
                    sender.sendRichMessage("<gray>No chambers found in this world.")
                } else {
                    val cx = (nearest.minX + nearest.maxX) / 2
                    val cy = (nearest.minY + nearest.maxY) / 2
                    val cz = (nearest.minZ + nearest.maxZ) / 2
                    val dist = kotlin.math.sqrt(centerDistanceSq(nearest, loc)).toInt()
                    sender.sendRichMessage(
                        "<gold>Nearest chamber: <yellow>${nearest.name}</yellow> <gray>~${dist}m at " +
                            "<white>$cx $cy $cz</white> <click:suggest_command:'/tp $cx $cy $cz'><aqua>[/tp]</aqua></click>"
                    )
                }
            }
            return
        }

        val requestedPage = sub?.toIntOrNull() ?: 1
        plugin.launchAsync {
            val chambers = plugin.chamberManager.getAllChambers().sortedBy { it.name }
            if (chambers.isEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("chamber-list-empty"))
                return@launchAsync
            }

            val pageSize = 10
            val maxPage = (chambers.size + pageSize - 1) / pageSize
            val page = requestedPage.coerceIn(1, maxPage)
            val start = (page - 1) * pageSize
            val pageItems = chambers.subList(start, minOf(start + pageSize, chambers.size))

            sender.sendRichMessage(
                "<gold>Chambers <gray>(page <yellow>$page</yellow>/<yellow>$maxPage</yellow>, " +
                    "<yellow>${chambers.size}</yellow> total) — <i>/tcp list current</i> to locate"
            )
            pageItems.forEach { chamber ->
                if (sender is Player) {
                    // Interactive line (v1.5.7): click the name to copy it,
                    // click [menu] to jump straight into the chamber's GUI.
                    val name = chamber.name
                    sender.sendRichMessage(
                        "<click:copy_to_clipboard:'$name'><hover:show_text:'<gray>Click to copy <yellow>$name'>" +
                            "<yellow>$name</yellow></hover></click> <gray>— ${chamber.world} " +
                            "(${chamber.getVolume()} blocks)</gray> " +
                            "<click:run_command:'/tcp menu $name'><hover:show_text:'<gray>Open <yellow>$name</yellow> in the GUI'>" +
                            "<aqua>[menu]</aqua></hover></click>"
                    )
                } else {
                    sender.sendMessage(plugin.getMessageComponent("chamber-list-item",
                        "chamber" to chamber.name,
                        "world" to chamber.world,
                        "volume" to chamber.getVolume()
                    ))
                }
            }
            if (maxPage > 1) {
                val prev = if (page > 1) "<click:run_command:'/tcp list ${page - 1}'><green>« Prev</green></click>"
                else "<dark_gray>« Prev</dark_gray>"
                val next = if (page < maxPage) "<click:run_command:'/tcp list ${page + 1}'><green>Next »</green></click>"
                else "<dark_gray>Next »</dark_gray>"
                sender.sendRichMessage("$prev <gray>|</gray> $next")
            }
        }
    }

    /** Squared distance from [loc] to the centre of chamber [c] (for nearest-chamber lookup). */
    private fun centerDistanceSq(c: io.github.darkstarworks.trialChamberPro.models.Chamber, loc: Location): Double {
        val dx = (c.minX + c.maxX) / 2.0 - loc.x
        val dy = (c.minY + c.maxY) / 2.0 - loc.y
        val dz = (c.minZ + c.maxZ) / 2.0 - loc.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        // If no chamber name provided, show plugin info
        if (args.size < 2) {
            showPluginInfo(sender)
            return
        }

        // Otherwise show chamber info
        val chamberName = args[1]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            val exitLoc = chamber.getExitLocation()
            // rawMessage (not getMessage): these are nested into info-exit's {exit} placeholder,
            // parsed once by the outer getMessageComponent below.
            val exitStr = if (exitLoc != null) {
                plugin.rawMessage("info-exit-location-set",
                    "x" to exitLoc.blockX,
                    "y" to exitLoc.blockY,
                    "z" to exitLoc.blockZ
                )
            } else {
                plugin.rawMessage("info-exit-location-not-set")
            }

            val lastResetStr = if (chamber.lastReset != null) {
                MessageUtil.formatRelativeTime(chamber.lastReset)
            } else {
                plugin.rawMessage("time-never")
            }

            sender.sendMessage(plugin.getMessageComponent("info-header", "chamber" to chamber.name))
            chamber.displayName?.let {
                sender.sendMessage(plugin.getMessageComponent("info-display-name", "name" to it))
            }
            sender.sendMessage(plugin.getMessageComponent("info-world", "world" to chamber.world))
            sender.sendMessage(plugin.getMessageComponent("info-bounds",
                "minX" to chamber.minX, "minY" to chamber.minY, "minZ" to chamber.minZ,
                "maxX" to chamber.maxX, "maxY" to chamber.maxY, "maxZ" to chamber.maxZ
            ))
            sender.sendMessage(plugin.getMessageComponent("info-volume", "volume" to chamber.getVolume()))
            sender.sendMessage(plugin.getMessageComponent("info-exit", "exit" to exitStr))
            sender.sendMessage(plugin.getMessageComponent("info-reset-interval",
                "interval" to MessageUtil.formatTimeSeconds(chamber.resetInterval)
            ))
            sender.sendMessage(plugin.getMessageComponent("info-last-reset", "time" to lastResetStr))

            val snapshotStatus = if (chamber.snapshotFile != null) {
                plugin.rawMessage("info-snapshot-created")
            } else {
                plugin.rawMessage("info-snapshot-not-created")
            }
            sender.sendMessage(plugin.getMessageComponent("info-snapshot", "status" to snapshotStatus))

            if (chamber.isPaused) {
                sender.sendMessage(plugin.getMessageComponent("info-paused"))
            }
        }
    }

    /**
     * Shows plugin information including version, authors, integrations, and status.
     */
    private fun showPluginInfo(sender: CommandSender) {
        val desc = plugin.description
        val version = desc.version
        val authors = desc.authors.joinToString(", ")

        // Check integrations
        val worldEdit = plugin.server.pluginManager.getPlugin("WorldEdit") != null ||
                        plugin.server.pluginManager.getPlugin("FastAsyncWorldEdit") != null
        val worldGuard = plugin.server.pluginManager.getPlugin("WorldGuard") != null
        val placeholderApi = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        val vault = plugin.server.pluginManager.getPlugin("Vault") != null

        // Get chamber count
        val chamberCount = plugin.chamberManager.getCachedChambers().size

        // Database type
        val dbType = plugin.databaseManager.databaseType

        // Folia detection
        val isFolia = try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        sender.sendMessage(plugin.getMessageComponent("plugin-info-header"))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-version", "version" to version))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-authors", "authors" to authors))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-database", "type" to dbType))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-chambers", "count" to chamberCount))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-platform",
            "platform" to if (isFolia) "Folia" else "Paper/Spigot"
        ))

        // Integrations
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integrations-header"))

        val weStatus = if (worldEdit) "&a✓" else "&c✗"
        val wgStatus = if (worldGuard) "&a✓" else "&c✗"
        val papiStatus = if (placeholderApi) "&a✓" else "&c✗"
        val vaultStatus = if (vault) "&a✓" else "&c✗"

        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-worldedit", "status" to weStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-worldguard", "status" to wgStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-papi", "status" to papiStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-vault", "status" to vaultStatus))

        // Config status
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-header"))
        val perPlayerLoot = if (plugin.config.getBoolean("vaults.per-player-loot", true)) "&a✓" else "&c✗"
        val spawnerWaves = if (plugin.config.getBoolean("spawner-waves.enabled", true)) "&a✓" else "&c✗"
        val spectatorMode = if (plugin.config.getBoolean("spectator-mode.enabled", true)) "&a✓" else "&c✗"
        val statistics = if (plugin.config.getBoolean("statistics.enabled", true)) "&a✓" else "&c✗"

        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-per-player", "status" to perPlayerLoot))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-spawner-waves", "status" to spawnerWaves))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-spectator", "status" to spectatorMode))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-statistics", "status" to statistics))
    }


    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-delete"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.deleteChamber(chamberName)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("chamber-deleted", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handlePause(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.pause")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-pause"))
            return
        }
        val chamberName = args[1]
        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }
            if (chamber.isPaused) {
                sender.sendMessage(plugin.getMessageComponent("chamber-already-paused", "chamber" to chamberName))
                return@launchAsync
            }
            val success = plugin.chamberManager.setPaused(chamber.id, true)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("chamber-paused", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleResume(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.pause")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-resume"))
            return
        }
        val chamberName = args[1]
        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }
            if (!chamber.isPaused) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-paused", "chamber" to chamberName))
                return@launchAsync
            }
            val success = plugin.chamberManager.setPaused(chamber.id, false)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("chamber-resumed", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleRename(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessageComponent("usage-rename"))
            return
        }
        val chamberName = args[1]
        // Everything after the chamber name is the display name. "none" / "reset" / "-"
        // clears it, reverting to the internal name.
        val rest = args.drop(2).joinToString(" ").trim()
        val newName = if (rest.equals("none", true) || rest.equals("reset", true) || rest == "-") null else rest
        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }
            val success = plugin.chamberManager.setDisplayName(chamber.id, newName)
            if (!success) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }
            if (newName == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-rename-cleared", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-renamed",
                    "chamber" to chamberName, "name" to newName))
            }
        }
    }

    private fun handleVault(sender: CommandSender, args: Array<out String>) {
        handleVault(plugin, sender, args)
    }

    private fun handleKey(sender: CommandSender, args: Array<out String>) {
        handleKey(plugin, sender, args)
    }

    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        handleReset(plugin, sender, args)
    }

    private fun handleMenu(sender: CommandSender, args: Array<out String> = emptyArray()) {
        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }
        if (!sender.hasPermission("tcp.admin.menu")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (!plugin.isReady) {
            sender.sendMessage(plugin.getMessageComponent("plugin-starting-up"))
            return
        }

        // v1.5.7: /tcp menu <chamber> deep-links straight into that chamber's
        // detail view — the [menu] button on /tcp list lines uses this.
        val chamberName = args.getOrNull(1)
        if (chamberName != null) {
            plugin.launchAsync {
                val chamber = plugin.chamberManager.getChamber(chamberName)
                if (chamber == null) {
                    sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                    return@launchAsync
                }
                plugin.scheduler.runAtEntity(sender, Runnable {
                    if (!sender.isOnline) return@Runnable
                    try {
                        plugin.menuService.openChamberDetail(sender, chamber)
                    } catch (e: Exception) {
                        sender.sendMessage(plugin.getMessageComponent("error-menu-failed", "error" to (e.message ?: "Unknown error")))
                        e.printStackTrace()
                    }
                })
            }
            return
        }

        try {
            plugin.menuService.openFor(sender)
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessageComponent("error-menu-failed", "error" to (e.message ?: "Unknown error")))
            e.printStackTrace()
        }
    }

    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessageComponent("statistics-disabled"))
            return
        }

        plugin.launchAsync {
            if (args.size < 2) {
                // Show own stats
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessageComponent("player-only"))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(sender.uniqueId)
                sender.sendMessage(plugin.getMessageComponent("stats-header", "player" to sender.name))
                sender.sendMessage(plugin.getMessageComponent("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessageComponent("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessageComponent("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessageComponent("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            } else {
                // Show another player's stats
                if (!sender.hasPermission("tcp.admin.stats")) {
                    sender.sendMessage(plugin.getMessageComponent("no-permission"))
                    return@launchAsync
                }

                val targetPlayer = plugin.server.getPlayer(args[1])
                if (targetPlayer == null) {
                    sender.sendMessage(plugin.getMessageComponent("player-not-found", "player" to args[1]))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(targetPlayer.uniqueId)
                sender.sendMessage(plugin.getMessageComponent("stats-header", "player" to targetPlayer.name))
                sender.sendMessage(plugin.getMessageComponent("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessageComponent("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessageComponent("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessageComponent("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            }
        }
    }

    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessageComponent("statistics-disabled"))
            return
        }

        plugin.launchAsync {
            // Determine which stat to show
            val statType = if (args.size >= 2) args[1].lowercase() else "chambers"
            val limit = plugin.config.getInt("statistics.top-players-count", 10)

            val statName = when (statType) {
                "chambers", "completions" -> "chambers"
                "normal", "normalvaults" -> "normal_vaults"
                "ominous", "ominousvaults" -> "ominous_vaults"
                "mobs", "kills" -> "mobs"
                "time", "playtime" -> "time"
                else -> {
                    sender.sendMessage(plugin.getMessageComponent("invalid-stat-type"))
                    return@launchAsync
                }
            }

            val displayName = when (statName) {
                "chambers" -> "Chambers Completed"
                "normal_vaults" -> "Normal Vaults Opened"
                "ominous_vaults" -> "Ominous Vaults Opened"
                "mobs" -> "Mobs Killed"
                "time" -> "Time Spent"
                else -> "Unknown"
            }

            val leaderboard = plugin.statisticsManager.getLeaderboard(statName, limit)

            sender.sendMessage(plugin.getMessageComponent("leaderboard-header", "stat" to displayName))

            if (leaderboard.isEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("leaderboard-empty"))
                return@launchAsync
            }

            leaderboard.forEachIndexed { index, (uuid, value) ->
                val playerName = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                val displayValue = if (statName == "time") {
                    plugin.statisticsManager.formatTime(value.toLong())
                } else {
                    value.toString()
                }

                sender.sendMessage(
                    plugin.getMessageComponent("leaderboard-entry",
                        "rank" to (index + 1),
                        "player" to playerName,
                        "value" to displayValue
                    )
                )
            }
        }
    }

    private fun handlePaste(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.generate")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }

        if (!plugin.schematicManager.isAvailable()) {
            sender.sendMessage(plugin.getMessageComponent("worldedit-not-available"))
            return
        }

        val availableList = plugin.schematicManager.listSchematics().joinToString(", ")
        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("schematic-usage-hint"))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("schematic-usage", "list" to availableList))
            } else {
                sender.sendMessage(plugin.getMessageComponent("schematic-no-schematics"))
            }
            return
        }

        val schematicName = args[1]

        // Validate schematic exists
        if (!plugin.schematicManager.schematicExists(schematicName)) {
            sender.sendMessage(plugin.getMessageComponent("schematic-not-found", "name" to schematicName))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("schematic-usage", "list" to availableList))
            }
            return
        }

        // Determine paste location
        val location = if (args.size >= 5) {
            // Coordinates provided
            val x = args[2].toIntOrNull()
            val y = args[3].toIntOrNull()
            val z = args[4].toIntOrNull()

            if (x == null || y == null || z == null) {
                sender.sendMessage(plugin.getMessageComponent("error-invalid-coordinates"))
                return
            }

            Location(sender.world, x.toDouble(), y.toDouble(), z.toDouble())
        } else {
            // Use player location
            sender.location
        }

        sender.sendMessage(plugin.getMessageComponent("paste-loading"))

        // Load schematic and calculate actual paste bounds
        plugin.launchAsync {
            val bounds = plugin.schematicManager.getSchematicBounds(schematicName, location)
            if (bounds == null) {
                sender.sendMessage(plugin.getMessageComponent("paste-failed"))
                return@launchAsync
            }

            val (min, max) = bounds
            
            // Calculate dimensions for display
            val width = (max.blockX - min.blockX + 1)
            val height = (max.blockY - min.blockY + 1)
            val length = (max.blockZ - min.blockZ + 1)

            // Create pending paste request
            plugin.pasteConfirmationManager.createPending(sender, schematicName, location)

            // Show visualization with correct bounds
            plugin.particleVisualizer.showBox(sender, min, max)

            // Notify player
            val remaining = plugin.pasteConfirmationManager.getPending(sender)?.getRemainingSeconds() ?: 300
            sender.sendMessage(plugin.getMessageComponent("paste-preview-shown",
                "schematic" to schematicName,
                "x" to location.blockX,
                "y" to location.blockY,
                "z" to location.blockZ,
                "width" to width,
                "height" to height,
                "length" to length,
                "time" to remaining
            ))
            sender.sendMessage(plugin.getMessageComponent("paste-confirm-hint", "time" to remaining))
        }
    }

}
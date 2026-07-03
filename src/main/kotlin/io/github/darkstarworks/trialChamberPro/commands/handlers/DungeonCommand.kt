package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.dungeon.StitchParams
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `/tcp dungeon ...` — author room templates and generate dungeons from them.
 *
 *   pos1 / pos2            mark the capture selection (your feet position)
 *   capture <id> [roles…]  save the selection as a room template (roles → tags)
 *   generate <name> [seed] stitch a dungeon at your feet, register it as a chamber
 *   list                   list saved room templates
 *   delete <id>            delete a room template
 *   import <file|folder|zip> [tags…]  import vanilla .nbt structure templates
 *                          (from plugins/TrialChamberPro/dungeon/import/) as rooms
 */
class DungeonCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    private val pos1 = ConcurrentHashMap<UUID, Location>()
    private val pos2 = ConcurrentHashMap<UUID, Location>()
    private val importer by lazy { io.github.darkstarworks.trialChamberPro.dungeon.StructureImporter(plugin) }

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.generate")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (args.size < 2) {
            usage(sender)
            return
        }
        when (args[1].lowercase()) {
            "pos1" -> setPos(sender, args, pos1, "1")
            "pos2" -> setPos(sender, args, pos2, "2")
            "capture" -> capture(sender, args)
            "generate" -> generate(sender, args)
            "list" -> list(sender)
            "delete" -> delete(sender, args)
            "import" -> import(sender, args)
            else -> usage(sender)
        }
    }

    private fun setPos(sender: CommandSender, args: Array<out String>, store: ConcurrentHashMap<UUID, Location>, label: String) {
        val player = sender as? Player ?: return sender.sendMessage(plugin.getMessageComponent("player-only"))
        val loc = player.location.block.location
        store[player.uniqueId] = loc
        sender.sendMessage(plugin.getMessageComponent("dungeon-pos-set",
            "label" to label, "x" to loc.blockX, "y" to loc.blockY, "z" to loc.blockZ))
    }

    private fun capture(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendMessage(plugin.getMessageComponent("player-only"))
        if (args.size < 3) return sender.sendMessage(plugin.getMessageComponent("dungeon-usage-capture"))
        val id = args[2]
        val c1 = pos1[player.uniqueId]
        val c2 = pos2[player.uniqueId]
        if (c1 == null || c2 == null || c1.world != c2.world) {
            return sender.sendMessage(plugin.getMessageComponent("dungeon-need-positions"))
        }
        val roles = args.drop(3).map { it.lowercase() }.toSet()
        val world = c1.world ?: return
        val wallFallback = wallFallbackBlockData()
        sender.sendMessage(plugin.getMessageComponent("dungeon-capturing", "id" to id))
        plugin.launchAsync {
            try {
                val tpl = plugin.roomTemplateManager.capture(world, c1, c2, id, roles, wallFallback)
                sender.sendMessage(plugin.getMessageComponent("dungeon-captured",
                    "id" to id, "blocks" to tpl.blocks.size, "connectors" to tpl.connectors.size, "tags" to tpl.tags))
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessageComponent("dungeon-capture-failed", "error" to e.message))
            }
        }
    }

    private fun generate(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendMessage(plugin.getMessageComponent("player-only"))
        if (args.size < 3) return sender.sendMessage(plugin.getMessageComponent("dungeon-usage-generate"))
        val name = args[2]
        val seed = args.getOrNull(3)?.toLongOrNull() ?: System.nanoTime()
        val cfg = dungeonConfig()
        val origin = player.location.block.location
        val params = StitchParams(
            originX = origin.blockX, originY = origin.blockY, originZ = origin.blockZ,
            maxRooms = cfg.getInt("max-rooms", 20),
            startTags = cfg.getStringList("start-tags").ifEmpty { listOf("entrance") }.map { it.lowercase() }.toSet(),
            requiredTags = (cfg.getConfigurationSection("required-tags")?.getKeys(false) ?: emptySet())
                .associateWith { cfg.getInt("required-tags.$it") },
        )
        val doorWidth = cfg.getInt("door.width", 3)
        val doorHeight = cfg.getInt("door.height", 3)
        val world = origin.world ?: return
        sender.sendMessage(plugin.getMessageComponent("dungeon-generating", "name" to name, "seed" to seed))
        plugin.launchAsync {
            try {
                val ok = plugin.dungeonGenerator.generate(world, origin, seed, name, params, doorWidth, doorHeight)
                if (ok) sender.sendMessage(plugin.getMessageComponent("dungeon-generated", "name" to name))
                else sender.sendMessage(plugin.getMessageComponent("dungeon-generate-failed-console"))
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessageComponent("dungeon-generate-failed", "error" to e.message))
            }
        }
    }

    private fun list(sender: CommandSender) {
        val ids = plugin.roomTemplateManager.list()
        if (ids.isEmpty()) sender.sendMessage(plugin.getMessageComponent("dungeon-list-empty"))
        else sender.sendMessage(plugin.getMessageComponent("dungeon-list-page-header", "templates" to ids.joinToString(", ")))
    }

    private fun delete(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) return sender.sendMessage(plugin.getMessageComponent("dungeon-usage-delete"))
        val ok = plugin.roomTemplateManager.delete(args[2])
        if (ok) sender.sendMessage(plugin.getMessageComponent("dungeon-deleted", "id" to args[2]))
        else sender.sendMessage(plugin.getMessageComponent("dungeon-delete-not-found", "id" to args[2]))
    }

    /**
     * `/tcp dungeon import <file|folder|zip> [tags…]` — v1.7.0. Reads from
     * `plugins/TrialChamberPro/dungeon/import/`. A loose `.nbt` imports one room; a folder
     * imports every `.nbt` inside (folder name auto-tagged); a datapack `.zip` imports every
     * `data/<ns>/structure(s)/**/*.nbt` entry (immediate parent folder auto-tagged).
     */
    private fun import(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) return sender.sendMessage(plugin.getMessageComponent("dungeon-usage-import"))
        val importDir = File(plugin.dataFolder, "dungeon/import").apply { mkdirs() }
        // Resolve inside the import dir only (no path traversal).
        val target = File(importDir, args[2]).canonicalFile
        if (!target.path.startsWith(importDir.canonicalFile.path)) {
            return sender.sendMessage(plugin.getMessageComponent("dungeon-import-bad-path"))
        }
        if (!target.exists()) {
            return sender.sendMessage(plugin.getMessageComponent("dungeon-import-not-found", "file" to args[2]))
        }
        val tags = args.drop(3).map { it.lowercase() }.toSet()
        val wallFallback = wallFallbackBlockData()
        sender.sendMessage(plugin.getMessageComponent("dungeon-importing", "file" to args[2]))
        plugin.launchAsync {
            try {
                val results = when {
                    target.isDirectory -> importer.importFolder(target, tags, wallFallback)
                    target.extension.equals("zip", true) -> importer.importZip(target, tags, wallFallback)
                    target.extension.equals("nbt", true) -> listOf(importer.importFile(target, tags, wallFallback))
                    else -> {
                        sender.sendMessage(plugin.getMessageComponent("dungeon-import-unsupported", "extension" to target.extension))
                        return@launchAsync
                    }
                }
                if (results.isEmpty()) {
                    sender.sendMessage(plugin.getMessageComponent("dungeon-import-empty", "file" to args[2]))
                } else {
                    val totalConnectors = results.sumOf { it.connectors }
                    sender.sendMessage(plugin.getMessageComponent("dungeon-import-complete",
                        "count" to results.size, "connectors" to totalConnectors))
                    results.take(10).forEach { r ->
                        sender.sendMessage(plugin.getMessageComponent("dungeon-import-list-item",
                            "id" to r.id, "blocks" to r.blocks, "connectors" to r.connectors, "tags" to r.tags))
                    }
                    if (results.size > 10) sender.sendMessage(plugin.getMessageComponent("dungeon-import-more-list-item", "count" to (results.size - 10)))
                    sender.sendMessage(plugin.getMessageComponent("dungeon-import-note"))
                }
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessageComponent("dungeon-import-failed", "error" to e.message))
            }
        }
    }

    private fun usage(sender: CommandSender) {
        sender.sendMessage(plugin.getMessageComponent("dungeon-usage"))
    }

    private fun dungeonConfig(): YamlConfiguration {
        plugin.saveResource("dungeon.yml", false)
        return YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "dungeon.yml"))
    }

    private fun wallFallbackBlockData(): String {
        val name = dungeonConfig().getString("wall-fallback-material", "TUFF_BRICKS") ?: "TUFF_BRICKS"
        val mat = Material.matchMaterial(name)
        return if (mat != null && mat.isBlock) mat.createBlockData().asString else "minecraft:stone_bricks"
    }
}

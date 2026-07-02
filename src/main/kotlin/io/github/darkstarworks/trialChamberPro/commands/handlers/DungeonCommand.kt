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
        sender.sendRichMessage("<green>Dungeon pos$label set to <yellow>${loc.blockX}, ${loc.blockY}, ${loc.blockZ}</yellow>.")
    }

    private fun capture(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendMessage(plugin.getMessageComponent("player-only"))
        if (args.size < 3) return sender.sendRichMessage("<red>Usage: /tcp dungeon capture <id> [roles…]")
        val id = args[2]
        val c1 = pos1[player.uniqueId]
        val c2 = pos2[player.uniqueId]
        if (c1 == null || c2 == null || c1.world != c2.world) {
            return sender.sendRichMessage("<red>Set pos1 and pos2 (same world) first.")
        }
        val roles = args.drop(3).map { it.lowercase() }.toSet()
        val world = c1.world ?: return
        val wallFallback = wallFallbackBlockData()
        sender.sendRichMessage("<gray>Capturing room <yellow>$id</yellow>…")
        plugin.launchAsync {
            try {
                val tpl = plugin.roomTemplateManager.capture(world, c1, c2, id, roles, wallFallback)
                sender.sendRichMessage("<green>Captured <yellow>$id</yellow>: ${tpl.blocks.size} blocks, ${tpl.connectors.size} connector(s), tags=${tpl.tags}.")
            } catch (e: Exception) {
                sender.sendRichMessage("<red>Capture failed: ${e.message}")
            }
        }
    }

    private fun generate(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendMessage(plugin.getMessageComponent("player-only"))
        if (args.size < 3) return sender.sendRichMessage("<red>Usage: /tcp dungeon generate <name> [seed]")
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
        sender.sendRichMessage("<gray>Generating dungeon <yellow>$name</yellow> (seed $seed)…")
        plugin.launchAsync {
            try {
                val ok = plugin.dungeonGenerator.generate(world, origin, seed, name, params, doorWidth, doorHeight)
                if (ok) sender.sendRichMessage("<green>Dungeon <yellow>$name</yellow> generated and registered as a chamber.")
                else sender.sendRichMessage("<red>Generation failed — see console (no templates / start room / required tags?).")
            } catch (e: Exception) {
                sender.sendRichMessage("<red>Generation failed: ${e.message}")
            }
        }
    }

    private fun list(sender: CommandSender) {
        val ids = plugin.roomTemplateManager.list()
        if (ids.isEmpty()) sender.sendRichMessage("<gray>No room templates captured yet.")
        else sender.sendRichMessage("<gold>Room templates:</gold> <yellow>${ids.joinToString(", ")}</yellow>")
    }

    private fun delete(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) return sender.sendRichMessage("<red>Usage: /tcp dungeon delete <id>")
        val ok = plugin.roomTemplateManager.delete(args[2])
        if (ok) sender.sendRichMessage("<green>Deleted room template <yellow>${args[2]}</yellow>.")
        else sender.sendRichMessage("<red>No such room template: ${args[2]}")
    }

    /**
     * `/tcp dungeon import <file|folder|zip> [tags…]` — v1.7.0. Reads from
     * `plugins/TrialChamberPro/dungeon/import/`. A loose `.nbt` imports one room; a folder
     * imports every `.nbt` inside (folder name auto-tagged); a datapack `.zip` imports every
     * `data/<ns>/structure(s)/**/*.nbt` entry (immediate parent folder auto-tagged).
     */
    private fun import(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) return sender.sendRichMessage("<red>Usage: /tcp dungeon import <file|folder|zip> [tags…]")
        val importDir = File(plugin.dataFolder, "dungeon/import").apply { mkdirs() }
        // Resolve inside the import dir only (no path traversal).
        val target = File(importDir, args[2]).canonicalFile
        if (!target.path.startsWith(importDir.canonicalFile.path)) {
            return sender.sendRichMessage("<red>Path must be inside dungeon/import/.")
        }
        if (!target.exists()) {
            return sender.sendRichMessage("<red>Not found: dungeon/import/${args[2]} <gray>(drop .nbt files, folders or datapack .zips there)")
        }
        val tags = args.drop(3).map { it.lowercase() }.toSet()
        val wallFallback = wallFallbackBlockData()
        sender.sendRichMessage("<gray>Importing <yellow>${args[2]}</yellow>…")
        plugin.launchAsync {
            try {
                val results = when {
                    target.isDirectory -> importer.importFolder(target, tags, wallFallback)
                    target.extension.equals("zip", true) -> importer.importZip(target, tags, wallFallback)
                    target.extension.equals("nbt", true) -> listOf(importer.importFile(target, tags, wallFallback))
                    else -> {
                        sender.sendRichMessage("<red>Unsupported file type '${target.extension}' — use .nbt, a folder, or a .zip.")
                        return@launchAsync
                    }
                }
                if (results.isEmpty()) {
                    sender.sendRichMessage("<red>No structure templates found in ${args[2]}.")
                } else {
                    val totalConnectors = results.sumOf { it.connectors }
                    sender.sendRichMessage("<green>Imported <yellow>${results.size}</yellow> room(s), $totalConnectors connector(s) total. <gray>Rooms without connectors can't be stitched.")
                    results.take(10).forEach { r ->
                        sender.sendRichMessage("<gray> - <yellow>${r.id}</yellow>: ${r.blocks} blocks, ${r.connectors} connector(s), tags=${r.tags}")
                    }
                    if (results.size > 10) sender.sendRichMessage("<gray> … and ${results.size - 10} more (see /tcp dungeon list)")
                    sender.sendRichMessage("<gray>Note: imported rooms are literal — worldgen processor randomizers aren't applied; vertical jigsaws are walls.")
                }
            } catch (e: Exception) {
                sender.sendRichMessage("<red>Import failed: ${e.message}")
            }
        }
    }

    private fun usage(sender: CommandSender) {
        sender.sendRichMessage("<gold>/tcp dungeon</gold> <gray>— pos1 | pos2 | capture <id> [roles…] | generate <name> [seed] | list | delete <id> | import <file|folder|zip> [tags…]</gray>")
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

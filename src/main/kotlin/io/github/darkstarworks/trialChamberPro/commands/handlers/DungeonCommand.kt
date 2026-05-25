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
 */
class DungeonCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    private val pos1 = ConcurrentHashMap<UUID, Location>()
    private val pos2 = ConcurrentHashMap<UUID, Location>()

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

    private fun usage(sender: CommandSender) {
        sender.sendRichMessage("<gold>/tcp dungeon</gold> <gray>— pos1 | pos2 | capture <id> [roles…] | generate <name> [seed] | list | delete <id></gray>")
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

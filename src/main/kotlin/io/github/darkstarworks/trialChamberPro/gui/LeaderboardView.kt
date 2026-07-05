package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class LeaderboardHolder : BaseHolder()

/**
 * Top-10 leaderboard view. Category switcher across the bottom row.
 * Migrated to VcGui in v1.5.0. `runBlocking` for the leaderboard fetch is
 * preserved unchanged (deferred refactor — see the project docs).
 */
class LeaderboardView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val viewer: Player,
    private val leaderboardType: String,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.leaderboard.title-" + leaderboardTypeOrDefault(leaderboardType)),
    holder = LeaderboardHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        // Header at (4, 0) = slot 4
        set(4, VcGuiItem.wrap(createHeaderItem()))

        val leaderboard = runBlocking { plugin.statisticsManager.getLeaderboard(leaderboardType, 10) }

        leaderboard.forEachIndexed { index, entry ->
            val row = 1 + (index / 2)
            val col = if (index % 2 == 0) 2 else 6
            if (row <= 4) {
                set(row * 9 + col, VcGuiItem.wrap(createLeaderboardEntry(index + 1, entry)) { ctx ->
                    menu.openPlayerStats(ctx.player, entry.first)
                })
            }
        }

        if (leaderboard.isEmpty()) {
            set(22, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.leaderboard.empty-name", "gui.leaderboard.empty-lore")
            ))
        }

        // Bottom row: back / switchers / close
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-stats") { ctx ->
            menu.openStatsMenu(ctx.player)
        })
        set(47, switcher("vaults", Material.VAULT))
        set(48, switcher("chambers", Material.LODESTONE))
        set(50, switcher("mobs", Material.IRON_SWORD))
        set(51, switcher("time", Material.CLOCK))
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun switcher(type: String, material: Material): VcGuiItem {
        val isCurrent = type == leaderboardType
        val stack = ItemStack(if (isCurrent) Material.LIME_DYE else material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.leaderboard.switcher-$type"))
                if (isCurrent) lore(listOf(plugin.getGuiText("gui.leaderboard.switcher-current")))
            }
        }
        return VcGuiItem.wrap(stack) { ctx ->
            if (!isCurrent) menu.openLeaderboard(ctx.player, type)
        }
    }

    private fun createHeaderItem(): ItemStack {
        val (material, nameKey) = when (leaderboardType) {
            "vaults" -> Material.VAULT to "gui.leaderboard.header-vaults-name"
            "chambers" -> Material.LODESTONE to "gui.leaderboard.header-chambers-name"
            "mobs" -> Material.IRON_SWORD to "gui.leaderboard.header-mobs-name"
            "time" -> Material.CLOCK to "gui.leaderboard.header-time-name"
            else -> Material.WRITABLE_BOOK to "gui.leaderboard.header-default-name"
        }
        return GuiComponents.infoItem(plugin, material, nameKey, "gui.leaderboard.header-lore")
    }

    private fun createLeaderboardEntry(rank: Int, entry: Pair<java.util.UUID, Int>): ItemStack {
        val (uuid, value) = entry
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        val playerName = offlinePlayer.name ?: "Unknown"
        val rankColor = when (rank) { 1 -> "6"; 2 -> "7"; 3 -> "4"; else -> "f" }
        val formattedValue = if (leaderboardType == "time") formatTime(value.toLong()) else value.toString()

        return GuiComponents.playerHead(
            plugin, offlinePlayer,
            "gui.leaderboard.entry-name", "gui.leaderboard.entry-lore",
            "rankColor" to rankColor,
            "rank" to rank,
            "player" to playerName,
            "value" to formattedValue
        )
    }

    private fun formatTime(seconds: Long): String {
        return io.github.darkstarworks.trialChamberPro.utils.MessageUtil.formatTimeSeconds(plugin, seconds)
    }
}

private fun leaderboardTypeOrDefault(type: String): String = when (type) {
    "vaults", "chambers", "mobs", "time" -> type
    else -> "default"
}

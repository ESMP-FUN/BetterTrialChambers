package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.Material
import java.util.UUID

class PlayerStatsHolder : BaseHolder()

/**
 * Player stats view — detailed statistics for a specific player.
 * v1.3.0; migrated to VcGui in v1.5.0. `runBlocking` preserved.
 */
class PlayerStatsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val targetUuid: UUID,
) : VcGui(
    rows = 5,
    title = plugin.getGuiText("gui.player-stats.title",
        "player" to (Bukkit.getOfflinePlayer(targetUuid).name ?: "Unknown")),
    holder = PlayerStatsHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        val offlinePlayer = Bukkit.getOfflinePlayer(targetUuid)
        val playerName = offlinePlayer.name ?: "Unknown"

        val stats = runBlocking { plugin.statisticsManager.getStats(targetUuid) }
        val totalVaultsOpened = stats.normalVaultsOpened + stats.ominousVaultsOpened

        // Row 0: player head at (4,0) = 4
        val headLoreKey = if (offlinePlayer.isOnline)
            "gui.player-stats.head-lore-online" else "gui.player-stats.head-lore-offline"
        set(4, VcGuiItem.wrap(
            GuiComponents.playerHead(
                plugin, offlinePlayer,
                "gui.player-stats.head-name", headLoreKey,
                "player" to playerName,
                "uuid" to offlinePlayer.uniqueId.toString()
            )
        ))

        // Row 1: stat tiles
        set(10, statItem(Material.VAULT, "gui.player-stats.stat-vaults-name", totalVaultsOpened))
        set(12, statItem(Material.GREEN_WOOL, "gui.player-stats.stat-normal-name", stats.normalVaultsOpened))
        set(14, statItem(Material.PURPLE_WOOL, "gui.player-stats.stat-ominous-name", stats.ominousVaultsOpened))
        set(16, statItem(Material.LODESTONE, "gui.player-stats.stat-chambers-name", stats.chambersCompleted))

        // Row 2: more stat tiles
        set(20, statItem(Material.IRON_SWORD, "gui.player-stats.stat-mobs-name", stats.mobsKilled))
        set(22, statItem(Material.SKELETON_SKULL, "gui.player-stats.stat-deaths-name", stats.deaths))
        set(24, timeStatItem(stats.timeSpent))

        // Row 3: summary at (4,3) = 31
        val kd = if (stats.deaths > 0)
            String.format("%.2f", stats.mobsKilled.toDouble() / stats.deaths)
        else "Perfect"
        val avg = if (stats.chambersCompleted > 0)
            String.format("%.1f", totalVaultsOpened.toDouble() / stats.chambersCompleted)
        else "N/A"
        set(31, VcGuiItem.wrap(
            GuiComponents.infoItem(
                plugin, Material.WRITABLE_BOOK,
                "gui.player-stats.summary-name", "gui.player-stats.summary-lore",
                "kd" to kd, "avg" to avg
            )
        ))

        // Row 4: nav
        set(36, GuiComponents.backVcItem(plugin, "gui.common.dest-stats") { ctx ->
            menu.openStatsMenu(ctx.player)
        })
        set(44, GuiComponents.closeVcItem(plugin))
    }

    private fun statItem(material: Material, nameKey: String, value: Int): VcGuiItem =
        VcGuiItem.wrap(GuiComponents.infoItem(
            plugin, material, nameKey, "gui.player-stats.stat-value-lore",
            "value" to value
        ))

    private fun timeStatItem(seconds: Long): VcGuiItem {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val formatted = when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
        return VcGuiItem.wrap(GuiComponents.infoItem(
            plugin, Material.CLOCK,
            "gui.player-stats.time-stat-name", "gui.player-stats.time-stat-lore",
            "formatted" to formatted, "seconds" to seconds
        ))
    }
}

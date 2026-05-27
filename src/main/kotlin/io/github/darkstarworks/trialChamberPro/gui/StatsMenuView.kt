package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class StatsMenuHolder : BaseHolder()

class StatsMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val viewer: Player,
) : VcGui(
    rows = 4,
    title = plugin.getGuiText("gui.stats-menu.title"),
    holder = StatsMenuHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        set(4, VcGuiItem.wrap(createHeaderItem()))
        set(10, VcGuiItem.wrap(categoryItem(Material.VAULT, "gui.stats-menu.category-vaults-name")) { ctx ->
            menu.openLeaderboard(ctx.player, "vaults")
        })
        set(12, VcGuiItem.wrap(categoryItem(Material.LODESTONE, "gui.stats-menu.category-chambers-name")) { ctx ->
            menu.openLeaderboard(ctx.player, "chambers")
        })
        set(14, VcGuiItem.wrap(categoryItem(Material.IRON_SWORD, "gui.stats-menu.category-mobs-name")) { ctx ->
            menu.openLeaderboard(ctx.player, "mobs")
        })
        set(16, VcGuiItem.wrap(categoryItem(Material.CLOCK, "gui.stats-menu.category-time-name")) { ctx ->
            menu.openLeaderboard(ctx.player, "time")
        })
        set(22, VcGuiItem.wrap(
            GuiComponents.playerHead(plugin, viewer.uniqueId,
                "gui.stats-menu.your-stats-name", "gui.stats-menu.your-stats-lore")
        ) { ctx -> menu.openPlayerStats(ctx.player, ctx.player.uniqueId) })
        set(27, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(35, GuiComponents.closeVcItem(plugin))
    }

    private fun createHeaderItem(): ItemStack {
        val on = plugin.config.getBoolean("statistics.enabled", true)
        val loreKey = if (on) "gui.stats-menu.header-lore-enabled" else "gui.stats-menu.header-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.WRITABLE_BOOK, "gui.stats-menu.header-name", loreKey)
    }

    private fun categoryItem(material: Material, nameKey: String): ItemStack =
        GuiComponents.infoItem(plugin, material, nameKey, "gui.stats-menu.category-lore")
}

package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MainMenuHolder : BaseHolder()

/**
 * Main menu view — central hub. v1.4.x flattened the old Settings sub-menu;
 * v1.5.0 migrated to VcGui.
 */
class MainMenuView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.main-menu.title"),
    holder = MainMenuHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        set(4, VcGuiItem.wrap(createHeaderItem()))
        set(10, VcGuiItem.wrap(createChambersItem()) { ctx -> menu.openChamberList(ctx.player) })
        set(12, VcGuiItem.wrap(createLootTablesItem()) { ctx -> menu.openLootTableList(ctx.player) })
        set(14, VcGuiItem.wrap(createGlobalSettingsItem()) { ctx -> menu.openGlobalSettings(ctx.player) })
        set(16, VcGuiItem.wrap(createProtectionItem()) { ctx -> menu.openProtectionMenu(ctx.player) })
        set(20, VcGuiItem.wrap(createHelpItem()) { ctx -> menu.openHelpMenu(ctx.player) })
        set(22, VcGuiItem.wrap(createPerformanceInfoItem()))
        set(24, VcGuiItem.wrap(createStatisticsItem()) { ctx -> menu.openStatsMenu(ctx.player) })
        set(40, VcGuiItem.wrap(createReloadItem()) { ctx ->
            if (ctx.event.isShiftClick) reloadConfig(ctx.player)
        })
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun createHeaderItem(): ItemStack {
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        val tableCount = plugin.lootManager.getLootTableNames().size
        return GuiComponents.infoItem(
            plugin, Material.TRIAL_KEY,
            "gui.main-menu.header-name", "gui.main-menu.header-lore",
            "version" to plugin.pluginMeta.version,
            "chambers" to chamberCount,
            "tables" to tableCount
        )
    }

    private fun createChambersItem(): ItemStack {
        val n = plugin.chamberManager.getCachedChambers().size
        return GuiComponents.infoItem(plugin, Material.LODESTONE,
            "gui.main-menu.chambers-name", "gui.main-menu.chambers-lore", "count" to n)
    }

    private fun createLootTablesItem(): ItemStack {
        val n = plugin.lootManager.getLootTableNames().size
        return GuiComponents.infoItem(plugin, Material.CHEST,
            "gui.main-menu.loot-name", "gui.main-menu.loot-lore", "count" to n)
    }

    private fun createStatisticsItem(): ItemStack {
        val on = plugin.config.getBoolean("statistics.enabled", true)
        val loreKey = if (on) "gui.main-menu.stats-lore-enabled" else "gui.main-menu.stats-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.WRITABLE_BOOK, "gui.main-menu.stats-name", loreKey)
    }

    private fun createGlobalSettingsItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
            "gui.main-menu.global-settings-name", "gui.main-menu.global-settings-lore")

    private fun createProtectionItem(): ItemStack {
        val on = plugin.config.getBoolean("protection.enabled", true)
        val loreKey = if (on) "gui.main-menu.protection-lore-enabled" else "gui.main-menu.protection-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.SHIELD, "gui.main-menu.protection-name", loreKey)
    }

    private fun createHelpItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.OAK_SIGN, "gui.main-menu.help-name", "gui.main-menu.help-lore")

    private fun createPerformanceInfoItem(): ItemStack {
        val dbType = plugin.config.getString("database.type", "SQLITE") ?: "SQLITE"
        val cacheEnabled = plugin.config.getBoolean("performance.cache-chamber-lookups", true)
        val cacheDuration = plugin.config.getInt("performance.cache-duration-seconds", 300)
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        return GuiComponents.infoItem(
            plugin, Material.REDSTONE,
            "gui.main-menu.performance-name", "gui.main-menu.performance-lore",
            "database" to dbType,
            "cache" to if (cacheEnabled) "Enabled" else "Disabled",
            "duration" to cacheDuration,
            "chambers" to chamberCount
        )
    }

    private fun createReloadItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.REPEATER,
            "gui.main-menu.reload-name", "gui.main-menu.reload-lore")

    private fun reloadConfig(player: Player) {
        player.sendMessage(plugin.getMessageComponent("config-reloading"))
        plugin.reloadPluginConfig()
        player.sendMessage(plugin.getMessageComponent("config-reloaded"))
    }
}

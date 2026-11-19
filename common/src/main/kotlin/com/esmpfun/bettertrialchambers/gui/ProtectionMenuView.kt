package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.ProtectionSetting
import org.bukkit.Material
import org.bukkit.entity.Player

class ProtectionMenuHolder : BaseHolder()

/**
 * Protection menu view. v1.3.0; migrated to VcGui in v1.5.0.
 */
class ProtectionMenuView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
) : VcGui(
    rows = 5,
    title = plugin.getGuiText("gui.protection-menu.title"),
    holder = ProtectionMenuHolder(),
) {
    private data class ToggleDef(
        val configPath: String,
        val labelKey: String,
        val descKey: String,
        val slot: Int,
        val default: Boolean = true,
    )

    private val toggles = buildList {
        add(ToggleDef("protection.enabled",
            "gui.protection-menu.system-label",
            "gui.protection-menu.system-desc", 1 * 9 + 4))
        val slots = (19..25) + (28..32)
        ProtectionSetting.entries.forEachIndexed { i, setting ->
            add(ToggleDef(setting.configPath, setting.labelKey, setting.descKey, slots[i], setting.default))
        }
        add(ToggleDef("protection.worldguard-integration",
            "gui.protection-menu.worldguard-label",
            "gui.protection-menu.worldguard-desc", 3 * 9 + 7))
    }

    init { layout() }

    private fun layout() {
        clear()
        val on = plugin.config.getBoolean("protection.enabled", true)
        val headerNameKey = if (on)
            "gui.protection-menu.header-name-enabled" else "gui.protection-menu.header-name-disabled"
        val headerLoreKey = if (on)
            "gui.protection-menu.header-lore-enabled" else "gui.protection-menu.header-lore-disabled"
        set(4, VcGuiItem.wrap(GuiComponents.infoItem(plugin, Material.SHIELD, headerNameKey, headerLoreKey)))

        for (def in toggles) {
            val enabled = plugin.config.getBoolean(def.configPath, def.default)
            set(def.slot, VcGuiItem.wrap(
                GuiComponents.toggleItem(plugin, enabled, def.labelKey, def.descKey)
            ) { ctx -> toggleSetting(def, ctx.player) })
        }

        set(36, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(44, GuiComponents.closeVcItem(plugin))
    }

    private fun toggleSetting(def: ToggleDef, player: Player) {
        val newValue = !plugin.config.getBoolean(def.configPath, def.default)
        plugin.config.set(def.configPath, newValue)
        plugin.saveConfig()

        val settingLabel = plugin.rawMessage(def.labelKey)  // nested into setting-toggled
        val valueText = plugin.rawMessage(
            if (newValue) "gui.common.setting-enabled" else "gui.common.setting-disabled"
        )
        player.sendMessage(plugin.getMessageComponent("gui.common.setting-toggled",
            "setting" to settingLabel, "value" to valueText))

        menu.openProtectionMenu(player)
    }
}

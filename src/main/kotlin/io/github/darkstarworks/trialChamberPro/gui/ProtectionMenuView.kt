package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import org.bukkit.Material
import org.bukkit.entity.Player

class ProtectionMenuHolder : BaseHolder()

/**
 * Protection menu view. v1.3.0; migrated to VcGui in v1.5.0.
 */
class ProtectionMenuView(
    private val plugin: TrialChamberPro,
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
    )

    private val toggles = listOf(
        ToggleDef("protection.enabled",
            "gui.protection-menu.system-label",
            "gui.protection-menu.system-desc", 1 * 9 + 4),       // (4,1) = 13
        ToggleDef("protection.prevent-block-break",
            "gui.protection-menu.block-break-label",
            "gui.protection-menu.block-break-desc", 2 * 9 + 1),  // (1,2) = 19
        ToggleDef("protection.prevent-block-place",
            "gui.protection-menu.block-place-label",
            "gui.protection-menu.block-place-desc", 2 * 9 + 3),  // (3,2) = 21
        ToggleDef("protection.prevent-container-access",
            "gui.protection-menu.containers-label",
            "gui.protection-menu.containers-desc", 2 * 9 + 5),   // (5,2) = 23
        ToggleDef("protection.prevent-mob-griefing",
            "gui.protection-menu.mob-griefing-label",
            "gui.protection-menu.mob-griefing-desc", 2 * 9 + 7), // (7,2) = 25
        ToggleDef("protection.allow-pvp",
            "gui.protection-menu.pvp-label",
            "gui.protection-menu.pvp-desc", 3 * 9 + 3),          // (3,3) = 30
        ToggleDef("protection.worldguard-integration",
            "gui.protection-menu.worldguard-label",
            "gui.protection-menu.worldguard-desc", 3 * 9 + 5),   // (5,3) = 32
    )

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
            val enabled = plugin.config.getBoolean(def.configPath, true)
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
        val newValue = !plugin.config.getBoolean(def.configPath, true)
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

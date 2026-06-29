package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import org.bukkit.Material
import org.bukkit.entity.Player

class GlobalSettingsHolder : BaseHolder()

/**
 * Global settings view. v1.3.0; migrated to VcGui in v1.5.0.
 */
class GlobalSettingsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.global-settings.title"),
    holder = GlobalSettingsHolder(),
) {
    private data class ToggleDef(
        val configPath: String,
        val labelKey: String,
        val descKey: String,
        val slot: Int,
    )

    private val toggles = listOf(
        ToggleDef("reset.clear-ground-items",
            "gui.global-settings.clear-ground-items-label",
            "gui.global-settings.clear-ground-items-desc", 1 * 9 + 1),  // (1,1) = 10
        ToggleDef("reset.remove-spawner-mobs",
            "gui.global-settings.remove-spawner-mobs-label",
            "gui.global-settings.remove-spawner-mobs-desc", 1 * 9 + 3), // (3,1) = 12
        ToggleDef("reset.reset-trial-spawners",
            "gui.global-settings.reset-trial-spawners-label",
            "gui.global-settings.reset-trial-spawners-desc", 1 * 9 + 5),// (5,1) = 14
        ToggleDef("reset.reset-vault-cooldowns",
            "gui.global-settings.reset-vault-cooldowns-label",
            "gui.global-settings.reset-vault-cooldowns-desc", 1 * 9 + 7),// (7,1) = 16
        ToggleDef("spawner-waves.enabled",
            "gui.global-settings.spawner-waves-label",
            "gui.global-settings.spawner-waves-desc", 2 * 9 + 1),       // (1,2) = 19
        ToggleDef("spawner-waves.show-boss-bar",
            "gui.global-settings.wave-boss-bar-label",
            "gui.global-settings.wave-boss-bar-desc", 2 * 9 + 3),       // (3,2) = 21
        ToggleDef("spectator-mode.enabled",
            "gui.global-settings.spectator-mode-label",
            "gui.global-settings.spectator-mode-desc", 2 * 9 + 5),      // (5,2) = 23
        ToggleDef("statistics.enabled",
            "gui.global-settings.statistics-label",
            "gui.global-settings.statistics-desc", 2 * 9 + 7),          // (7,2) = 25
        ToggleDef("loot.apply-luck-effect",
            "gui.global-settings.luck-effect-label",
            "gui.global-settings.luck-effect-desc", 3 * 9 + 3),         // (3,3) = 30
        ToggleDef("vaults.per-player-loot",
            "gui.global-settings.per-player-loot-label",
            "gui.global-settings.per-player-loot-desc", 3 * 9 + 5),     // (5,3) = 32
    )

    init { layout() }

    private fun layout() {
        clear()
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
                "gui.global-settings.header-name", "gui.global-settings.header-lore")
        ))

        for (def in toggles) {
            val enabled = plugin.config.getBoolean(def.configPath, true)
            set(def.slot, VcGuiItem.wrap(
                GuiComponents.toggleItem(plugin, enabled, def.labelKey, def.descKey)
            ) { ctx -> toggleSetting(def, ctx.player) })
        }

        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(53, GuiComponents.closeVcItem(plugin))
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

        menu.openGlobalSettings(player)
    }
}

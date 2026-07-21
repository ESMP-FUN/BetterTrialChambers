package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.VaultLootMode
import org.bukkit.Material
import org.bukkit.entity.Player

class GlobalSettingsHolder : BaseHolder()

/**
 * Global settings view. v1.3.0; migrated to VcGui in v1.5.0.
 */
class GlobalSettingsView(
    private val plugin: BetterTrialChambers,
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
        /** What the setting counts as when it's missing from config.yml. */
        val default: Boolean = true,
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
        ToggleDef("reset.spawner-cooldown-overrides-presets",
            "gui.global-settings.cooldown-overrides-presets-label",
            "gui.global-settings.cooldown-overrides-presets-desc", 3 * 9 + 7, default = false), // (7,3) = 34
    )

    /** Slot holding the three-way vault loot mode picker (5,3). */
    private val lootModeSlot = 3 * 9 + 5

    init { layout() }

    private fun layout() {
        clear()
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
                "gui.global-settings.header-name", "gui.global-settings.header-lore")
        ))

        for (def in toggles) {
            val enabled = plugin.config.getBoolean(def.configPath, def.default)
            set(def.slot, VcGuiItem.wrap(
                GuiComponents.toggleItem(plugin, enabled, def.labelKey, def.descKey)
            ) { ctx -> toggleSetting(def, ctx.player) })
        }

        layoutLootMode()

        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(53, GuiComponents.closeVcItem(plugin))
    }

    /**
     * Vault loot mode is three-way, not on/off, so it gets its own click-to-cycle
     * tile instead of a wool toggle. The icon colour matches how "shared" the loot
     * is: green = everyone gets their own, orange = first come first served,
     * gray = the plugin stays out of it. See [VaultLootMode].
     */
    private fun layoutLootMode() {
        val mode = VaultLootMode.resolve(plugin)
        val material = when (mode) {
            VaultLootMode.PER_PLAYER -> Material.LIME_WOOL
            VaultLootMode.SHARED -> Material.ORANGE_WOOL
            VaultLootMode.VANILLA -> Material.LIGHT_GRAY_WOOL
        }
        val currentLabel = plugin.rawMessage("gui.global-settings.loot-mode-${mode.name.lowercase()}")
        set(lootModeSlot, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, material,
                "gui.global-settings.loot-mode-label",
                "gui.global-settings.loot-mode-desc",
                "current" to currentLabel)
        ) { ctx -> cycleLootMode(ctx.player) })
    }

    private fun cycleLootMode(player: Player) {
        val next = when (VaultLootMode.resolve(plugin)) {
            VaultLootMode.PER_PLAYER -> VaultLootMode.SHARED
            VaultLootMode.SHARED -> VaultLootMode.VANILLA
            VaultLootMode.VANILLA -> VaultLootMode.PER_PLAYER
        }
        plugin.config.set("vaults.loot-mode", next.name)
        // Keep the pre-v2.0.8 key in step so a downgrade doesn't silently flip
        // behaviour, and so nothing reads a stale value from it.
        plugin.config.set("vaults.per-player-loot", next != VaultLootMode.VANILLA)
        plugin.saveConfig()

        player.sendMessage(plugin.getMessageComponent("gui.common.setting-toggled",
            "setting" to plugin.rawMessage("gui.global-settings.loot-mode-label"),
            "value" to plugin.rawMessage("gui.global-settings.loot-mode-${next.name.lowercase()}")))

        // Same trap as on reload: vaults players already opened stay shut under
        // plain Minecraft, so hand them the cleanup command up front.
        if (next == VaultLootMode.VANILLA) {
            player.sendMessage(plugin.getMessageComponent("vault-mode-vanilla-hint"))
        }

        menu.openGlobalSettings(player)
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

        menu.openGlobalSettings(player)
    }
}

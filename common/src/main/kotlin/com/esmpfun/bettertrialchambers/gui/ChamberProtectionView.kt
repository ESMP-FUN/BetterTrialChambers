package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.models.ProtectionSetting
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ChamberProtectionHolder : BaseHolder()

/**
 * One chamber's own protection switches. Left click sets a switch for this
 * chamber, right click hands it back to the server-wide value.
 */
class ChamberProtectionView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
    private val chamber: Chamber,
) : VcGui(
    rows = 5,
    title = plugin.getGuiText("gui.chamber-protection.title", "chamber" to chamber.name),
    holder = ChamberProtectionHolder(),
) {
    private val slots = (10..16) + (19..25)

    init { layout() }

    private fun layout() {
        clear()
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.SHIELD,
                "gui.chamber-protection.header-name", "gui.chamber-protection.header-lore",
                "chamber" to chamber.name)
        ))

        ProtectionSetting.entries.forEachIndexed { i, setting ->
            set(slots[i], VcGuiItem.wrap(settingItem(setting)) { ctx ->
                when {
                    ctx.event.isRightClick -> save(ctx.player, setting, null)
                    ctx.event.isLeftClick -> save(ctx.player, setting, !setting.valueFor(chamber, plugin.config))
                }
            })
        }

        if (chamber.protectionOverrides.isNotEmpty()) {
            set(31, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BOOK,
                    "gui.chamber-protection.clear-all-name", "gui.chamber-protection.clear-all-lore",
                    "count" to chamber.protectionOverrides.size)
            ) { ctx -> saveAll(ctx.player, emptyMap(), "chamber-protection-cleared-all") })
        }

        set(36, GuiComponents.backVcItem(plugin, "gui.common.dest-chamber-settings") { ctx ->
            val refreshed = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshed != null) menu.openChamberSettings(ctx.player, refreshed)
            else menu.openChamberList(ctx.player)
        })
        set(44, GuiComponents.closeVcItem(plugin))
    }

    private fun settingItem(setting: ProtectionSetting): ItemStack {
        val own = chamber.protectionOverrides[setting.key]
        val effective = own ?: setting.globalValue(plugin.config)
        val onOff = { on: Boolean -> plugin.rawMessage(if (on) "gui.chamber-protection.state-on" else "gui.chamber-protection.state-off") }
        val loreKey = if (own == null) "gui.chamber-protection.setting-lore-server" else "gui.chamber-protection.setting-lore-own"
        return ItemStack(if (effective) Material.LIME_WOOL else Material.RED_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText(
                    if (effective) "gui.common.toggle-name-enabled" else "gui.common.toggle-name-disabled",
                    "label" to plugin.rawMessage(setting.labelKey)))
                lore(plugin.getGuiLore(loreKey,
                    "description" to plugin.rawMessage(setting.descKey),
                    "value" to onOff(effective),
                    "next" to onOff(!effective),
                    "server" to onOff(setting.globalValue(plugin.config))))
                // A glint marks the switches this chamber has set for itself.
                setEnchantmentGlintOverride(own != null)
            }
        }
    }

    private fun save(player: Player, setting: ProtectionSetting, value: Boolean?) {
        val current = chamber.protectionOverrides
        if (value == null && setting.key !in current) return
        val updated = current.toMutableMap().apply {
            if (value == null) remove(setting.key) else put(setting.key, value)
        }
        val shown = value ?: setting.globalValue(plugin.config)
        saveAll(player, updated,
            if (value == null) "chamber-protection-follows-server" else "chamber-protection-set",
            "setting" to plugin.rawMessage(setting.labelKey),
            "value" to plugin.rawMessage(if (shown) "gui.chamber-protection.state-on" else "gui.chamber-protection.state-off"))
    }

    private fun saveAll(player: Player, overrides: Map<String, Boolean>, messageKey: String, vararg extra: Pair<String, Any?>) {
        plugin.launchAsync {
            val success = plugin.chamberManager.setProtectionOverrides(chamber.id, overrides)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent(messageKey, "chamber" to chamber.name, *extra))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberProtection(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("chamber-protection-failed"))
            })
        }
    }
}

package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.listeners.MobIdInputListener
import com.esmpfun.bettertrialchambers.models.Chamber
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class CustomMobProviderHolder : BaseHolder()

/**
 * Per-chamber Custom Mob Provider configuration (v1.3.0; v1.5.0 VcGui).
 */
class CustomMobProviderView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
    private val chamber: Chamber,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.custom-mob.title", "chamber" to chamber.name),
    holder = CustomMobProviderHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        // Row 0: back / header / close
        set(0, GuiComponents.backVcItem(plugin, "gui.common.dest-settings") { ctx ->
            val refreshed = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshed != null) menu.openChamberSettings(ctx.player, refreshed)
            else menu.openChamberList(ctx.player)
        })
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.SPAWNER,
                "gui.custom-mob.header-name", "gui.custom-mob.header-lore",
                "chamber" to chamber.name)
        ))
        set(8, GuiComponents.closeVcItem(plugin))

        // Row 1: provider toggle at (4,1) = 13
        set(13, VcGuiItem.wrap(createProviderItem()) { ctx ->
            when {
                ctx.event.isShiftClick -> clearProvider(ctx.player)
                ctx.event.isLeftClick -> cycleProvider(ctx.player, 1)
                ctx.event.isRightClick -> cycleProvider(ctx.player, -1)
            }
        })

        // Row 2: normal section header (0,2)=18 + add (8,2)=26
        set(18, VcGuiItem.wrap(sectionHeader(true, chamber.customMobIdsNormal.size)))
        set(26, VcGuiItem.wrap(addItem("normal")) { ctx ->
            promptAdd(ctx.player, MobIdInputListener.Section.NORMAL)
        })
        // Row 3: normal id row (slots 27..35)
        renderIdRow(rowStart = 27, ids = chamber.customMobIdsNormal,
            section = MobIdInputListener.Section.NORMAL)

        // Row 4: ominous section header (0,4)=36 + add (8,4)=44
        set(36, VcGuiItem.wrap(sectionHeader(false, chamber.customMobIdsOminous.size)))
        set(44, VcGuiItem.wrap(addItem("ominous")) { ctx ->
            promptAdd(ctx.player, MobIdInputListener.Section.OMINOUS)
        })
        // Row 5: ominous id row (slots 45..53)
        renderIdRow(rowStart = 45, ids = chamber.customMobIdsOminous,
            section = MobIdInputListener.Section.OMINOUS)
    }

    private fun renderIdRow(rowStart: Int, ids: List<String>, section: MobIdInputListener.Section) {
        if (ids.isEmpty()) {
            // Empty placeholder at center column of this row (rowStart + 4).
            set(rowStart + 4, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "gui.custom-mob.empty-name", "gui.custom-mob.empty-lore")
            ))
            return
        }
        val visible = ids.take(if (ids.size > 9) 8 else 9)
        visible.forEachIndexed { index, id ->
            set(rowStart + index, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.ZOMBIE_HEAD,
                    "gui.custom-mob.id-name", "gui.custom-mob.id-lore",
                    "id" to id)
            ) { ctx -> removeId(ctx.player, section, id) })
        }
        if (ids.size > 9) {
            set(rowStart + 8, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.PAPER,
                    "gui.custom-mob.overflow-name", "gui.custom-mob.overflow-lore",
                    "extra" to (ids.size - 9))
            ))
        }
    }

    private fun createProviderItem(): ItemStack {
        val current = chamber.customMobProvider ?: "vanilla"
        val registered = plugin.trialMobProviderRegistry.get(current)
        val available = registered?.isAvailable() ?: current.equals("vanilla", ignoreCase = true)
        val loreKey = if (available)
            "gui.custom-mob.provider-lore-available" else "gui.custom-mob.provider-lore-unavailable"
        return GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
            "gui.custom-mob.provider-name", loreKey,
            "provider" to current)
    }

    private fun sectionHeader(normal: Boolean, count: Int): ItemStack {
        val mat = if (normal) Material.GREEN_BANNER else Material.PURPLE_BANNER
        val nameKey = if (normal)
            "gui.custom-mob.section-normal-name" else "gui.custom-mob.section-ominous-name"
        return GuiComponents.infoItem(plugin, mat, nameKey, "gui.custom-mob.section-lore",
            "count" to count)
    }

    private fun addItem(section: String): ItemStack =
        GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
            "gui.custom-mob.add-name", "gui.custom-mob.add-lore",
            "section" to section)

    // ==================== Actions (verbatim) ====================

    private fun providerCycleList(): List<String> {
        val base = mutableListOf("vanilla")
        plugin.trialMobProviderRegistry.all()
            .map { it.id.lowercase() }
            .filter { it != "vanilla" }
            .sorted()
            .forEach { base += it }
        return base
    }

    private fun cycleProvider(player: Player, direction: Int) {
        val list = providerCycleList()
        val current = (chamber.customMobProvider ?: "vanilla").lowercase()
        val idx = list.indexOf(current).let { if (it == -1) 0 else it }
        val next = list[(idx + direction).mod(list.size)]
        applyProvider(player, next)
    }

    private fun clearProvider(player: Player) = applyProvider(player, "vanilla")

    private fun applyProvider(player: Player, providerId: String) {
        plugin.launchAsync {
            val stored = if (providerId.equals("vanilla", ignoreCase = true)) null else providerId
            val ok = plugin.chamberManager.updateCustomMobProvider(chamber.id, stored)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessageComponent("gui-provider-set", "id" to (stored ?: "vanilla")))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openCustomMobProvider(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-provider-failed"))
            })
        }
    }

    private fun promptAdd(player: Player, section: MobIdInputListener.Section) {
        MobIdInputListener.awaitInput(player.uniqueId, chamber.id, section)
        player.closeInventory()
        player.sendMessage(plugin.getMessageComponent("gui-mob-input-prompt",
            "section" to section.name.lowercase()))
    }

    private fun removeId(player: Player, section: MobIdInputListener.Section, id: String) {
        plugin.launchAsync {
            val normal = chamber.customMobIdsNormal.toMutableList()
            val ominous = chamber.customMobIdsOminous.toMutableList()
            val target = if (section == MobIdInputListener.Section.NORMAL) normal else ominous
            val removed = target.removeAll { it.equals(id, ignoreCase = true) }
            if (!removed) {
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessageComponent("gui-mob-remove-missing", "id" to id))
                })
                return@launchAsync
            }
            val ok = plugin.chamberManager.updateCustomMobProvider(
                chamber.id, chamber.customMobProvider, normal, ominous
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessageComponent("gui-mob-removed", "id" to id))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openCustomMobProvider(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-mob-remove-failed"))
            })
        }
    }
}

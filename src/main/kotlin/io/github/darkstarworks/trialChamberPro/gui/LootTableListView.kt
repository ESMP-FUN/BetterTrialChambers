package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import io.github.darkstarworks.trialChamberPro.models.LootTable
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class LootTableListHolder : BaseHolder()

/**
 * Loot table list view — browse all loot tables for direct editing.
 * All strings from `messages.yml` under `gui.loot-table-list.*` (v1.3.0;
 * migrated to VcGui in v1.5.0).
 */
class LootTableListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.loot-table-list.title"),
    holder = LootTableListHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        val tables = plugin.lootManager.getLootTableNames().sorted()

        // Header at (4, 0) = slot 4
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.CHEST,
                "gui.loot-table-list.header-name", "gui.loot-table-list.header-lore",
                "count" to tables.size)
        ))

        // Tables in rows 1-4, slots 9..44, left-to-right top-to-bottom
        tables.forEachIndexed { index, tableName ->
            val slot = 9 + index
            if (slot > 44) return@forEachIndexed
            val table = plugin.lootManager.getTable(tableName)
            set(slot, VcGuiItem.wrap(createTableItem(tableName, table)) { ctx ->
                if (table != null && !table.isLegacyFormat()) menu.openGlobalPoolSelect(ctx.player, tableName)
                else menu.openGlobalLootEditor(ctx.player, tableName)
            })
        }

        if (tables.isEmpty()) {
            set(22, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.loot-table-list.empty-name", "gui.loot-table-list.empty-lore")
            ))
        }

        // Bottom row controls
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(49, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
                "gui.loot-table-list.create-name", "gui.loot-table-list.create-lore")
        ))
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun createTableItem(name: String, table: LootTable?): ItemStack {
        val isOminous = name.startsWith("ominous")
        val material = if (isOminous) Material.PURPLE_WOOL else Material.GREEN_WOOL
        val nameKey = if (isOminous)
            "gui.loot-table-list.table-name-ominous" else "gui.loot-table-list.table-name-normal"

        val poolCount = table?.let { if (it.isLegacyFormat()) 1 else it.pools.size } ?: 0
        val itemCount = table?.let {
            if (it.isLegacyFormat()) it.guaranteedItems.size + it.weightedItems.size
            else it.pools.sumOf { p -> p.guaranteedItems.size + p.weightedItems.size }
        } ?: 0

        val loreKey = if (table?.isLegacyFormat() == true)
            "gui.loot-table-list.table-lore-legacy" else "gui.loot-table-list.table-lore-multi"

        return GuiComponents.infoItem(plugin, material, nameKey, loreKey,
            "name" to name, "pools" to poolCount, "items" to itemCount)
    }
}

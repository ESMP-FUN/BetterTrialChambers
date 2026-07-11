package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.models.LootPool
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class PoolSelectorHolder : BaseHolder()

/**
 * Pool selector view — pick which pool of a multi-pool loot table to edit.
 * v1.3.0; migrated to VcGui in v1.5.0.
 *
 * Edge case preserved: if the table is missing or legacy-format, the IF
 * version short-circuited to open the loot editor directly. We can't do
 * that pre-open from inside super(...), so the caller (MenuService) must
 * handle that branch — or this view opens, shows nothing, and the user
 * clicks back. To preserve behavior we just route Back to chamber detail
 * in those cases via empty layout.
 */
class PoolSelectorView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val globalTableName: String? = null,
) : VcGui(
    rows = 6,
    title = buildPoolSelectorTitle(plugin, chamber, kind, globalTableName),
    holder = PoolSelectorHolder(),
) {
    init {
        require(chamber != null || globalTableName != null) {
            "PoolSelectorView requires either a chamber or a globalTableName"
        }
        layout()
    }

    private fun layout() {
        clear()
        val tableName = getTableName()
        val table = plugin.lootManager.getTable(tableName)

        if (table == null || table.isLegacyFormat()) {
            // Short-circuit handled by the caller in MenuService.openPoolSelect /
            // openGlobalPoolSelect — they redirect to openLootEditor before this
            // view ever opens. As a safety net here, populate a back button only.
            set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-loot") { ctx ->
                if (chamber != null) {
                    @Suppress("DEPRECATION") menu.openLootKindSelect(ctx.player, chamber)
                } else menu.openLootTableList(ctx.player)
            })
            return
        }

        val pools = table.pools
        val maxPools = plugin.config.getInt("loot.max-pools-per-table", 5)

        pools.forEachIndexed { index, pool ->
            val row = 1 + (index / 7)
            val col = 1 + (index % 7)
            val slot = row * 9 + col
            set(slot, VcGuiItem.wrap(createPoolItem(pool)) { ctx ->
                if (chamber != null) menu.openLootEditor(ctx.player, chamber, kind, pool.name)
                else menu.openGlobalLootEditor(ctx.player, globalTableName!!, pool.name)
            })
        }

        if (pools.size < maxPools) {
            val index = pools.size
            val row = 1 + (index / 7)
            val col = 1 + (index % 7)
            val slot = row * 9 + col
            set(slot, VcGuiItem.wrap(createNewPoolItem(pools.size, maxPools)) { ctx ->
                ctx.player.sendMessage(plugin.getMessageComponent("gui-pool-create-hint"))
                ctx.player.sendMessage(plugin.getMessageComponent("gui-pool-create-coming-soon"))
            })
        }

        set(4, VcGuiItem.wrap(createInfoItem(pools.size, maxPools)))
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-loot") { ctx ->
            if (chamber != null) {
                @Suppress("DEPRECATION") menu.openLootKindSelect(ctx.player, chamber)
            } else menu.openLootTableList(ctx.player)
        })
    }

    private fun getTableName(): String {
        if (chamber == null) return globalTableName!!
        return when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }
    }

    private fun createPoolItem(pool: LootPool): ItemStack {
        val material = when {
            pool.name.contains("common", ignoreCase = true) -> Material.IRON_INGOT
            pool.name.contains("rare", ignoreCase = true) -> Material.DIAMOND
            pool.name.contains("unique", ignoreCase = true) -> Material.NETHER_STAR
            pool.name.contains("epic", ignoreCase = true) -> Material.NETHERITE_INGOT
            else -> Material.CHEST
        }
        val itemCount = pool.weightedItems.size + pool.guaranteedItems.size
        return GuiComponents.infoItem(plugin, material,
            "gui.pool-selector.pool-name", "gui.pool-selector.pool-lore",
            "name" to pool.name,
            "minRolls" to pool.minRolls, "maxRolls" to pool.maxRolls,
            "items" to itemCount,
            "weighted" to pool.weightedItems.size,
            "guaranteed" to pool.guaranteedItems.size)
    }

    private fun createNewPoolItem(currentPools: Int, maxPools: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.LIME_DYE,
            "gui.pool-selector.new-pool-name", "gui.pool-selector.new-pool-lore",
            "current" to currentPools, "max" to maxPools)

    private fun createInfoItem(poolCount: Int, maxPools: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.BOOK,
            "gui.pool-selector.info-name", "gui.pool-selector.info-lore",
            "count" to poolCount, "max" to maxPools)
}

private fun buildPoolSelectorTitle(
    plugin: BetterTrialChambers,
    chamber: Chamber?,
    kind: MenuService.LootKind,
    globalTableName: String?,
): Component {
    return if (chamber != null) {
        val key = when (kind) {
            MenuService.LootKind.NORMAL -> "gui.pool-selector.title-chamber-normal"
            MenuService.LootKind.OMINOUS -> "gui.pool-selector.title-chamber-ominous"
        }
        plugin.getGuiText(key, "chamber" to chamber.name)
    } else {
        plugin.getGuiText("gui.pool-selector.title-global", "table" to (globalTableName ?: ""))
    }
}

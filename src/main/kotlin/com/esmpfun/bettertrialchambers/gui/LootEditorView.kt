package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.ClickContext
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.models.LootEditorDraft
import com.esmpfun.bettertrialchambers.models.LootItem
import com.esmpfun.bettertrialchambers.models.LootTable
import com.esmpfun.bettertrialchambers.models.VaultType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * Holder for the loot-editor GUI. Empty payload — the editor's state
 * (draft, dirty flag, discard intent) lives on the [LootEditorView] itself.
 */
class LootEditorHolder : BaseHolder()

/**
 * Loot editor — edits a single loot table (or one pool of a multi-pool table).
 * All strings from `messages.yml` under `gui.loot-editor.*` (v1.3.0).
 *
 * v1.5.0 — migrated from InventoryFramework to the in-house VcGui framework.
 * Layout is identical (6 rows; rows 0–3 = loot entries, rows 4–5 = controls);
 * the change is purely in event handling (central [com.esmpfun.bettertrialchambers.gui.framework.VcGuiListener]
 * dispatch, partial-cancel for safe bottom-inventory actions).
 */
class LootEditorView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val poolName: String? = null,
    existingDraft: LootEditorDraft? = null,
    private val globalTableName: String? = null,
) : VcGui(
    rows = 6,
    title = buildTitle(plugin, chamber, kind, poolName, globalTableName),
    holder = LootEditorHolder(),
) {
    init {
        require(chamber != null || globalTableName != null) {
            "LootEditorView requires either a chamber or a globalTableName"
        }
    }

    private var draft: LootEditorDraft = existingDraft?.let { cloneDraft(it) } ?: loadInitialDraft()
    private var discardRequested: Boolean = false

    init {
        refreshContent()
        buildControls()
    }

    /**
     * Persist the draft to the session map on close, unless the user
     * explicitly hit Discard (in which case [discardRequested] is set
     * by the discard button's onClick and the close-save is skipped).
     */
    override fun handleClose(player: Player) {
        if (discardRequested) return
        if (chamber != null) menu.saveDraft(player, chamber, kind, poolName, draft)
        else menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
    }

    private fun cloneDraft(source: LootEditorDraft): LootEditorDraft = LootEditorDraft(
        tableName = source.tableName,
        guaranteed = source.guaranteed.toMutableList(),
        weighted = source.weighted.toMutableList(),
        minRolls = source.minRolls,
        maxRolls = source.maxRolls,
        dirty = source.dirty
    )

    private fun loadInitialDraft(): LootEditorDraft {
        val baseName: String
        val source: LootTable?
        if (chamber != null) {
            baseName = when (kind) {
                MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
                MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
            }
            val fallback = when (kind) {
                MenuService.LootKind.NORMAL -> "default"
                MenuService.LootKind.OMINOUS -> "ominous-default"
            }
            source = plugin.lootManager.getTable(baseName) ?: plugin.lootManager.getTable(fallback)
        } else {
            baseName = globalTableName!!
            source = plugin.lootManager.getTable(baseName)
        }

        if (poolName != null && source != null && !source.isLegacyFormat()) {
            val pool = source.pools.find { it.name == poolName }
            if (pool != null) {
                return LootEditorDraft(
                    tableName = baseName,
                    guaranteed = pool.guaranteedItems.toMutableList(),
                    weighted = pool.weightedItems.toMutableList(),
                    minRolls = pool.minRolls,
                    maxRolls = pool.maxRolls
                )
            }
        }

        val table = source ?: LootTable(baseName, 1, 3, emptyList(), emptyList(), emptyList())
        return LootEditorDraft(
            tableName = table.name,
            guaranteed = table.guaranteedItems.toMutableList(),
            weighted = table.weightedItems.toMutableList(),
            minRolls = table.minRolls,
            maxRolls = table.maxRolls
        )
    }

    /** Repaint the loot-entries region (rows 0–3, slots 0..35). */
    private fun refreshContent() {
        // Clear the entries region (rows 0..3 inclusive).
        for (s in 0..35) set(s, null)

        val totalWeight = draft.weighted.filter { it.enabled }.sumOf { it.weight }
        var slot = 0
        draft.weighted.forEachIndexed { idx, li ->
            if (slot > 35) return@forEachIndexed
            val item = renderLootItem(li, weighted = true, totalWeight)
            set(slot++, VcGuiItem.wrap(item) { ctx ->
                handleItemClick(idx, weighted = true, ctx.click, ctx.player)
            })
        }
        draft.guaranteed.forEachIndexed { idx, li ->
            if (slot > 35) return@forEachIndexed
            val item = renderLootItem(li, weighted = false, 0.0)
            set(slot++, VcGuiItem.wrap(item) { ctx ->
                handleItemClick(idx, weighted = false, ctx.click, ctx.player)
            })
        }

        if (draft.weighted.isEmpty() && draft.guaranteed.isEmpty()) {
            val empty = GuiComponents.infoItem(plugin, Material.BARRIER,
                "gui.loot-editor.empty-name", "gui.loot-editor.empty-lore")
            set(13, VcGuiItem.wrap(empty))
        }
    }

    private fun renderLootItem(li: LootItem, weighted: Boolean, totalWeight: Double): ItemStack {
        val nameKey = when {
            weighted && li.enabled -> "gui.loot-editor.item-name-weighted-enabled"
            weighted -> "gui.loot-editor.item-name-weighted-disabled"
            li.enabled -> "gui.loot-editor.item-name-guaranteed-enabled"
            else -> "gui.loot-editor.item-name-guaranteed-disabled"
        }

        val lore = mutableListOf<Component>()
        lore += plugin.getGuiText("gui.loot-editor.item-amount",
            "min" to li.amountMin, "max" to li.amountMax)
        val avgAmount = (li.amountMin + li.amountMax) / 2.0
        if (weighted) {
            if (totalWeight > 0.0 && li.enabled) {
                val chancePerDraw = li.weight / totalWeight
                val pct = String.format("%.1f", chancePerDraw * 100.0)
                lore += plugin.getGuiText("gui.loot-editor.item-chance", "percent" to pct)

                val avgDraws = (draft.minRolls + draft.maxRolls) / 2.0
                val expected = avgDraws * chancePerDraw * avgAmount
                lore += plugin.getGuiText("gui.loot-editor.item-expected",
                    "count" to formatExpected(expected))
            } else {
                lore += plugin.getGuiText("gui.loot-editor.item-weight",
                    "weight" to String.format("%.1f", li.weight))
            }
        } else if (li.enabled) {
            lore += plugin.getGuiText("gui.loot-editor.item-expected",
                "count" to formatExpected(avgAmount))
        }
        lore += plugin.getGuiText(if (li.enabled) "gui.loot-editor.item-enabled" else "gui.loot-editor.item-disabled")
        if (plugin.lootManager.isLegacy(li)) {
            lore += Component.empty()
            lore += Component.text("⚠ Legacy entry — re-add to capture NBT", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        }
        lore += Component.empty()
        lore += plugin.getGuiText("gui.loot-editor.item-controls-header")
        if (weighted) {
            lore += plugin.getGuiText("gui.loot-editor.item-controls-shift-left")
            lore += plugin.getGuiText("gui.loot-editor.item-controls-shift-right")
        }
        lore += plugin.getGuiText("gui.loot-editor.item-controls-right")
        lore += plugin.getGuiText("gui.loot-editor.item-controls-middle")
        lore += Component.text("Q / drop key: remove", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)

        val faithful = li.serializedItem?.let { plugin.lootManager.deserializeItem(it) }
        val item = faithful?.clone() ?: ItemStack(li.type)
        item.itemMeta = item.itemMeta?.apply {
            if (faithful == null) displayName(plugin.getGuiText(nameKey, "item" to li.type.name))
            val existing = lore() ?: emptyList()
            lore(if (existing.isEmpty()) lore else existing + Component.empty() + lore)
        }
        return item
    }

    private fun handleItemClick(index: Int, weighted: Boolean, clickType: ClickType, player: Player) {
        val list = if (weighted) draft.weighted else draft.guaranteed
        if (index !in list.indices) return
        val item = list[index]
        var modified = false

        when (clickType) {
            ClickType.DROP, ClickType.CONTROL_DROP -> {
                list.removeAt(index); modified = true
                player.sendMessage(plugin.getMessageComponent("gui-item-removed-from-loot", "item" to item.type.name))
            }
            ClickType.MIDDLE -> {
                list[index] = item.copy(enabled = !item.enabled); modified = true
            }
            ClickType.SHIFT_LEFT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0)); modified = true
            }
            ClickType.SHIFT_RIGHT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight - 1.0).coerceAtLeast(0.1)); modified = true
            }
            ClickType.LEFT, ClickType.WINDOW_BORDER_LEFT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0)); modified = true
            }
            ClickType.RIGHT, ClickType.WINDOW_BORDER_RIGHT -> {
                // Persist current draft so the AmountEditor sees the same state.
                if (chamber != null) {
                    menu.saveDraft(player, chamber, kind, poolName, draft)
                    menu.openAmountEditor(player, chamber, kind, index, weighted)
                } else {
                    menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
                    menu.openGlobalAmountEditor(player, globalTableName, poolName, index, weighted)
                }
                return
            }
            else -> return
        }

        if (modified) {
            draft.dirty = true
            refreshContent()
            buildControls()
            update()
        }
    }

    /** Repaint the controls region (rows 4–5, slots 36..53). */
    private fun buildControls() {
        // Clear controls region.
        for (s in 36..53) set(s, null)

        // Row 5 (slots 45..53) — bottom-area controls. Row 4 is left empty as a
        // visual gap between the loot-entry grid (rows 0-3) and the action row.
        // Save at (0, 5) = slot 45
        val saveLoreKey = if (draft.dirty) "gui.loot-editor.save-lore-dirty" else "gui.loot-editor.save-lore-clean"
        val save = GuiComponents.infoItem(plugin, Material.GREEN_CONCRETE,
            "gui.loot-editor.save-name", saveLoreKey)
        set(45, VcGuiItem.wrap(save) { ctx ->
            saveDraft(ctx.player)
            draft.dirty = false
            if (chamber != null) {
                menu.saveDraft(ctx.player, chamber, kind, poolName, draft)
                if (poolName != null) menu.openPoolSelect(ctx.player, chamber, kind)
                else @Suppress("DEPRECATION") menu.openLootKindSelect(ctx.player, chamber)
            } else {
                menu.saveGlobalDraft(ctx.player, globalTableName!!, poolName, draft)
                if (poolName != null) menu.openGlobalPoolSelect(ctx.player, globalTableName)
                else menu.openLootTableList(ctx.player)
            }
        })

        // Rolls at (2, 5) = slot 47
        val rolls = GuiComponents.infoItem(plugin, Material.PAPER,
            "gui.loot-editor.rolls-name", "gui.loot-editor.rolls-lore",
            "min" to draft.minRolls, "max" to draft.maxRolls)
        set(47, VcGuiItem.wrap(rolls) { ctx ->
            val left = ctx.click == ClickType.LEFT || ctx.click == ClickType.SHIFT_LEFT
            val right = ctx.click == ClickType.RIGHT || ctx.click == ClickType.SHIFT_RIGHT
            val shift = ctx.click == ClickType.SHIFT_LEFT || ctx.click == ClickType.SHIFT_RIGHT
            var changed = false
            if (!shift && left) { draft.minRolls = (draft.minRolls + 1).coerceAtMost(64); changed = true }
            if (!shift && right) { draft.minRolls = (draft.minRolls - 1).coerceAtLeast(0); changed = true }
            if (shift && left) { draft.maxRolls = (draft.maxRolls + 1).coerceAtMost(64); changed = true }
            if (shift && right) { draft.maxRolls = (draft.maxRolls - 1).coerceAtLeast(draft.minRolls); changed = true }
            if (draft.maxRolls < draft.minRolls) draft.maxRolls = draft.minRolls
            if (changed) {
                draft.dirty = true
                buildControls()
                update()
            }
        })

        // Add (from hand) at (4, 5) = slot 49
        val add = GuiComponents.infoItem(plugin, Material.LIME_DYE,
            "gui.loot-editor.add-name", "gui.loot-editor.add-lore")
        set(49, VcGuiItem.wrap(add) { ctx ->
            val hand = ctx.player.inventory.itemInMainHand
            if (hand.type == Material.AIR) {
                ctx.player.sendMessage(plugin.getMessageComponent("gui-hold-item-to-add"))
                return@wrap
            }
            val newItem = LootItem(
                type = hand.type,
                amountMin = 1,
                amountMax = hand.amount.coerceAtLeast(1),
                weight = 1.0,
                serializedItem = plugin.lootManager.serializeItem(hand),
                enabled = true
            )
            draft.weighted.add(newItem)
            draft.dirty = true
            ctx.player.sendMessage(plugin.getMessageComponent("gui-item-added-to-loot", "item" to hand.type.name))
            refreshContent()
            buildControls()
            update()
        })

        // Bulk add at (6, 5) = slot 51
        val bulkAdd = ItemStack(Material.CHEST).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Bulk add (drag items in)", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                )
                lore(listOf(
                    Component.text("Opens a chest — drag or shift-click", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("items in, then close to add them all", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("(enchants/potions/NBT preserved).", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                ))
            }
        }
        set(51, VcGuiItem.wrap(bulkAdd) { ctx ->
            // Persist current draft so the deposit appends to it, then open the chest.
            if (chamber != null) menu.saveDraft(ctx.player, chamber, kind, poolName, draft)
            else menu.saveGlobalDraft(ctx.player, globalTableName!!, poolName, draft)
            menu.openLootDeposit(ctx.player, chamber, kind, poolName, globalTableName)
        })

        // Discard at (8, 5) = slot 53
        val discard = GuiComponents.infoItem(plugin, Material.RED_CONCRETE,
            "gui.loot-editor.discard-name", "gui.loot-editor.discard-lore")
        set(53, VcGuiItem.wrap(discard) { ctx ->
            discardRequested = true
            if (chamber != null) {
                if (poolName != null) menu.openPoolSelect(ctx.player, chamber, kind)
                else @Suppress("DEPRECATION") menu.openLootKindSelect(ctx.player, chamber)
            } else {
                if (poolName != null) menu.openGlobalPoolSelect(ctx.player, globalTableName!!)
                else menu.openLootTableList(ctx.player)
            }
        })
    }

    private fun saveDraft(player: Player) {
        val existingTable = plugin.lootManager.getTable(draft.tableName)
        val table = if (poolName != null && existingTable != null && !existingTable.isLegacyFormat()) {
            val updatedPools = existingTable.pools.map { pool ->
                if (pool.name == poolName) {
                    pool.copy(
                        minRolls = draft.minRolls, maxRolls = draft.maxRolls,
                        guaranteedItems = draft.guaranteed.toList(),
                        weightedItems = draft.weighted.toList()
                    )
                } else pool
            }
            LootTable(name = draft.tableName, pools = updatedPools)
        } else {
            LootTable(
                name = draft.tableName,
                minRolls = draft.minRolls, maxRolls = draft.maxRolls,
                guaranteedItems = draft.guaranteed.toList(),
                weightedItems = draft.weighted.toList(),
                commandRewards = existingTable?.commandRewards ?: emptyList(),
                // Preserve economy rewards across a GUI edit (the editor only
                // touches items) — otherwise saving would silently drop them.
                economyRewards = existingTable?.economyRewards ?: emptyList()
            )
        }
        plugin.lootManager.updateTable(table)
        plugin.lootManager.saveAllToFile()

        if (chamber != null) {
            val type = if (kind == MenuService.LootKind.OMINOUS) VaultType.OMINOUS else VaultType.NORMAL
            plugin.launchAsync {
                try {
                    plugin.vaultManager.setLootTableForChamber(chamber.id, type, draft.tableName)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to update vault loot table: ${e.message}")
                }
            }
        }

        if (poolName != null) player.sendMessage(plugin.getMessageComponent("gui-loot-pool-saved", "pool" to poolName))
        else player.sendMessage(plugin.getMessageComponent("gui-loot-changes-saved"))
    }

    private fun formatExpected(v: Double): String = when {
        v >= 10.0 -> String.format("%.0f", v)
        v >= 1.0 -> String.format("%.1f", v)
        else -> String.format("%.2f", v)
    }
}

/**
 * Build the title Component for the editor. Pulled out of the class body so
 * we can pass it into `super(title = ...)` — Kotlin requires super-call args
 * be expressions, no class-member access. Primary-constructor parameters
 * (`plugin`, `chamber`, etc.) ARE in scope here, so the title pulls straight
 * from `gui.loot-editor.title-*` like every other localized string.
 */
private fun buildTitle(
    plugin: BetterTrialChambers,
    chamber: Chamber?,
    kind: MenuService.LootKind,
    poolName: String?,
    globalTableName: String?,
): Component {
    val base = if (chamber != null) {
        val key = when (kind) {
            MenuService.LootKind.NORMAL -> "gui.loot-editor.title-chamber-normal"
            MenuService.LootKind.OMINOUS -> "gui.loot-editor.title-chamber-ominous"
        }
        plugin.getGuiText(key, "chamber" to chamber.name)
    } else {
        plugin.getGuiText("gui.loot-editor.title-global", "table" to (globalTableName ?: ""))
    }
    return if (poolName != null) {
        base.append(plugin.getGuiText("gui.loot-editor.title-pool-suffix", "pool" to poolName))
    } else base
}

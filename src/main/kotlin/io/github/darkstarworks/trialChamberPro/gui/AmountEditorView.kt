package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootEditorDraft
import io.github.darkstarworks.trialChamberPro.models.LootItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class AmountEditorHolder : BaseHolder()

/**
 * Amount editor — adjusts min/max stack range for a single LootItem in a draft.
 * v1.3.0; migrated to VcGui in v1.5.0.
 */
class AmountEditorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val poolName: String? = null,
    private val itemIndex: Int,
    private val isWeighted: Boolean,
    private val draft: LootEditorDraft,
    private val globalTableName: String? = null,
) : VcGui(
    rows = 3,
    title = plugin.getGuiText("gui.amount-editor.title",
        "item" to (if (isWeighted) draft.weighted else draft.guaranteed)[itemIndex].type.name),
    holder = AmountEditorHolder(),
) {
    init {
        require(chamber != null || globalTableName != null) {
            "AmountEditorView requires either a chamber or a globalTableName"
        }
    }

    private var currentItem: LootItem = (if (isWeighted) draft.weighted else draft.guaranteed)[itemIndex].copy()

    init { layout() }

    private fun layout() {
        clear()
        // Row 0: display (4,0)=4
        set(4, VcGuiItem.wrap(createDisplayItem()))

        // Row 1: adjust buttons at (2,1)=11, (3,1)=12, (4,1)=13; reset at (6,1)=15
        listOf(1, 5, 10).forEachIndexed { i, amount ->
            set((1 * 9) + (2 + i), VcGuiItem.wrap(createAdjustButton(amount)) { ctx ->
                handleAdjustClick(amount, ctx.event.isLeftClick, ctx.event.isRightClick, ctx.event.isShiftClick)
            })
        }
        set(15, VcGuiItem.wrap(createResetButton()) { ctx ->
            handleResetClick(ctx.event.isLeftClick, ctx.event.isRightClick)
        })

        // Row 2: save (0,2)=18, discard (8,2)=26
        set(18, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.GREEN_CONCRETE,
                "gui.amount-editor.save-name", "gui.amount-editor.save-lore")
        ) { ctx -> saveAndReturn(ctx.player) })

        set(26, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.RED_CONCRETE,
                "gui.amount-editor.discard-name", "gui.amount-editor.discard-lore")
        ) { ctx -> discardAndReturn(ctx.player) })
    }

    private fun createDisplayItem(): ItemStack {
        val item = ItemStack(currentItem.type)
        val totalWeight = if (isWeighted) draft.weighted.filter { it.enabled }.sumOf { it.weight } else 0.0

        val lore = mutableListOf<Component>()
        lore += plugin.getGuiText("gui.amount-editor.display-amount",
            "min" to currentItem.amountMin, "max" to currentItem.amountMax)
        if (isWeighted && totalWeight > 0.0 && currentItem.enabled) {
            val percentage = (currentItem.weight / totalWeight) * 100.0
            lore += plugin.getGuiText("gui.amount-editor.display-chance",
                "percent" to String.format("%.1f", percentage))
        }
        lore += plugin.getGuiText(
            if (currentItem.enabled) "gui.amount-editor.display-enabled" else "gui.amount-editor.display-disabled"
        )

        item.itemMeta = item.itemMeta?.apply {
            displayName(plugin.getGuiText("gui.amount-editor.display-name", "item" to currentItem.type.name))
            lore(lore)
        }
        return item
    }

    private fun createAdjustButton(amount: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.YELLOW_CONCRETE,
            "gui.amount-editor.adjust-name", "gui.amount-editor.adjust-lore",
            "amount" to amount)

    private fun createResetButton(): ItemStack =
        GuiComponents.infoItem(plugin, Material.CYAN_CONCRETE,
            "gui.amount-editor.reset-name", "gui.amount-editor.reset-lore")

    private fun handleAdjustClick(amount: Int, left: Boolean, right: Boolean, shift: Boolean) {
        var min = currentItem.amountMin
        var max = currentItem.amountMax
        when {
            shift && left  -> max = (max + amount).coerceIn(1, 64)   // Maximum +N
            shift && right -> max = (max - amount).coerceIn(1, 64)   // Maximum -N
            left           -> min = (min + amount).coerceIn(1, 64)   // Minimum +N
            right          -> min = (min - amount).coerceIn(1, 64)   // Minimum -N
        }
        // Keep the range coherent (1 <= min <= max <= 64). Previously "Minimum +N"
        // was clamped to the current max, so on a freshly-added item (min=1, max=1)
        // it silently did nothing. Now a Minimum bump carries the Maximum up with it,
        // and a Maximum cut pulls the Minimum down — so every button visibly responds.
        if (min > max) {
            if (shift) min = max else max = min
        }
        if (min != currentItem.amountMin || max != currentItem.amountMax) {
            currentItem = currentItem.copy(amountMin = min, amountMax = max)
            layout()
            update()
        }
    }

    private fun handleResetClick(left: Boolean, right: Boolean) {
        when {
            left -> currentItem = currentItem.copy(amountMin = 1)
            right -> currentItem = currentItem.copy(
                amountMax = 1, amountMin = currentItem.amountMin.coerceAtMost(1))
        }
        layout()
        update()
    }

    private fun saveAndReturn(player: Player) {
        val list = if (isWeighted) draft.weighted else draft.guaranteed
        list[itemIndex] = currentItem
        draft.dirty = true
        if (chamber != null) {
            menu.saveDraft(player, chamber, kind, poolName, draft)
            menu.openLootEditor(player, chamber, kind, poolName)
        } else {
            menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
            menu.openGlobalLootEditor(player, globalTableName, poolName)
        }
    }

    private fun discardAndReturn(player: Player) {
        if (chamber != null) menu.openLootEditor(player, chamber, kind, poolName)
        else menu.openGlobalLootEditor(player, globalTableName!!, poolName)
    }
}

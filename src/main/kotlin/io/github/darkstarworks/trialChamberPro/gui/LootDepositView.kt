package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * Holder for the bulk loot-deposit chest. Stores the editor context
 * (chamber/kind/pool/globalTableName) so [LootDepositView.handleClose]
 * knows which draft to append to.
 */
class LootDepositHolder(
    val chamber: Chamber?,
    val kind: MenuService.LootKind,
    val poolName: String?,
    val globalTableName: String?,
) : BaseHolder()

/**
 * A fully-editable 54-slot chest. The admin drags / shift-clicks / hotbar-
 * swaps items in; on close every item is serialized into a weighted loot
 * entry on the draft (preserving all NBT) and handed back to the admin, then
 * the loot editor reopens with the new entries. Items are always returned
 * even if the draft is gone, so nothing is ever lost.
 *
 * v1.5.0 — migrated off the raw `Bukkit.createInventory` + standalone
 * `LootDepositListener` shape. Now uses the central VcGui framework with
 * [freelyEditable] = true so [io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiListener]
 * passes all clicks and drags through to vanilla Bukkit (the player IS the
 * source of truth — no slot wiring). The crash class from the previous
 * shape (`serializeAsBytes()` throwing mid-loop and stranding items in the
 * closing chest) is addressed here too: every item is wrapped in
 * try/catch, falling back to a material-only loot entry on failure, and
 * the return-items step runs in a try/finally so items are never lost.
 */
class LootDepositView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    chamber: Chamber?,
    kind: MenuService.LootKind,
    poolName: String?,
    globalTableName: String?,
) : VcGui(
    rows = 6,
    title = Component.text("Drag items to add — close to confirm", NamedTextColor.DARK_AQUA),
    holder = LootDepositHolder(chamber, kind, poolName, globalTableName),
) {
    override val freelyEditable: Boolean = true

    /**
     * Read every item the player deposited, serialize it into the draft, and
     * hand the items back so the admin never loses them. Reopens the loot
     * editor next tick so they can see the new entries.
     */
    override fun handleClose(player: Player) {
        val h = holder as LootDepositHolder
        val draft = if (h.chamber != null) {
            menu.getDraft(player, h.chamber, h.kind, h.poolName)
        } else {
            h.globalTableName?.let { menu.getGlobalDraft(player, it, h.poolName) }
        }

        var added = 0
        val itemsToReturn = mutableListOf<org.bukkit.inventory.ItemStack>()
        val contents = h.inventory.contents
        for (i in contents.indices) {
            val item = contents[i] ?: continue
            if (item.type.isAir) continue

            if (draft != null) {
                val serialized = try {
                    plugin.lootManager.serializeItem(item)
                } catch (e: Exception) {
                    // Some items (mod NBT, oversized component data) can't be
                    // round-tripped through serializeAsBytes. Don't lose them —
                    // store a material-only fallback and log so we can investigate.
                    plugin.logger.warning(
                        "Bulk-add: serializeItem failed for ${item.type} — falling back to material-only entry: ${e.message}"
                    )
                    null
                }
                draft.weighted.add(
                    LootItem(
                        type = item.type,
                        amountMin = 1,
                        amountMax = item.amount.coerceAtLeast(1),
                        weight = 1.0,
                        serializedItem = serialized,
                        enabled = true,
                    )
                )
                added++
            }
            itemsToReturn.add(item.clone())
            h.inventory.setItem(i, null)
        }

        if (added > 0 && draft != null) {
            draft.dirty = true
            if (h.chamber != null) menu.saveDraft(player, h.chamber, h.kind, h.poolName, draft)
            else menu.saveGlobalDraft(player, h.globalTableName!!, h.poolName, draft)
            player.sendRichMessage(
                "<green>Added <yellow>$added</yellow> item(s) to the loot draft — remember to <yellow>Save</yellow>."
            )
        }

        // Hand items back + reopen editor on the player's region thread
        // (Folia-correct; on Paper this is just the main thread).
        plugin.scheduler.runAtEntity(player, Runnable {
            try {
                for (item in itemsToReturn) {
                    val leftover = player.inventory.addItem(item)
                    leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
                }
            } finally {
                if (h.chamber != null) menu.openLootEditor(player, h.chamber, h.kind, h.poolName)
                else h.globalTableName?.let { menu.openGlobalLootEditor(player, it, h.poolName) }
            }
        })
    }
}

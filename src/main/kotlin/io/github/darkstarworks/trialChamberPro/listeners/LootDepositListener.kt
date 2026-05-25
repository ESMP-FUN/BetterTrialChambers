package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.LootDepositHolder
import io.github.darkstarworks.trialChamberPro.models.LootItem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent

/**
 * Captures items dropped into the bulk loot-deposit chest ([LootDepositHolder]).
 * On close, every item is serialized into a weighted loot entry on the draft
 * (preserving all NBT) and handed back to the admin, then the loot editor
 * reopens with the new entries. Items are always returned even if the draft is
 * gone, so nothing is ever lost.
 */
class LootDepositListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? LootDepositHolder ?: return
        val player = event.player as? Player ?: return
        val menu = plugin.menuService

        val draft = if (holder.chamber != null) {
            menu.getDraft(player, holder.chamber, holder.kind, holder.poolName)
        } else {
            holder.globalTableName?.let { menu.getGlobalDraft(player, it, holder.poolName) }
        }

        var added = 0
        val contents = event.inventory.contents
        for (i in contents.indices) {
            val item = contents[i] ?: continue
            if (item.type.isAir) continue

            if (draft != null) {
                draft.weighted.add(
                    LootItem(
                        type = item.type,
                        amountMin = 1,
                        amountMax = item.amount.coerceAtLeast(1),
                        weight = 1.0,
                        serializedItem = plugin.lootManager.serializeItem(item),
                        enabled = true,
                    )
                )
                added++
            }

            // Always hand the item back so the admin never loses it.
            val leftover = player.inventory.addItem(item.clone())
            leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            event.inventory.setItem(i, null)
        }

        if (added > 0 && draft != null) {
            draft.dirty = true
            if (holder.chamber != null) menu.saveDraft(player, holder.chamber, holder.kind, holder.poolName, draft)
            else menu.saveGlobalDraft(player, holder.globalTableName!!, holder.poolName, draft)
            player.sendRichMessage("<green>Added <yellow>$added</yellow> item(s) to the loot draft — remember to <yellow>Save</yellow>.")
        }

        // Reopen the editor next tick (can't open an inventory inside a close event).
        plugin.scheduler.runTask(Runnable {
            if (holder.chamber != null) menu.openLootEditor(player, holder.chamber, holder.kind, holder.poolName)
            else holder.globalTableName?.let { menu.openGlobalLootEditor(player, it, holder.poolName) }
        })
    }
}

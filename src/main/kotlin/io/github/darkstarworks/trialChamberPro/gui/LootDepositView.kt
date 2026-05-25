package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.models.Chamber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * Holder for the bulk loot-deposit chest. Carries the editor context so
 * [io.github.darkstarworks.trialChamberPro.listeners.LootDepositListener] can,
 * on close, append the deposited items to the right draft and reopen the editor.
 */
class LootDepositHolder(
    val chamber: Chamber?,
    val kind: MenuService.LootKind,
    val poolName: String?,
    val globalTableName: String?,
) : InventoryHolder {
    private lateinit var backing: Inventory
    fun attach(inv: Inventory) { backing = inv }
    override fun getInventory(): Inventory = backing
}

/**
 * A plain, fully-editable double chest. The admin drags/shift-clicks items in;
 * on close they're captured (faithfully, via the serialized-item layer) into the
 * loot draft and handed back, so nothing is lost. No click/drag cancelling here —
 * that's the whole point.
 */
object LootDepositView {
    fun open(
        player: Player,
        chamber: Chamber?,
        kind: MenuService.LootKind,
        poolName: String?,
        globalTableName: String?,
    ) {
        val holder = LootDepositHolder(chamber, kind, poolName, globalTableName)
        val title = Component.text("Drag items to add — close to confirm", NamedTextColor.DARK_AQUA)
        val inv = Bukkit.createInventory(holder, 54, title)
        holder.attach(inv)
        player.openInventory(inv)
    }
}

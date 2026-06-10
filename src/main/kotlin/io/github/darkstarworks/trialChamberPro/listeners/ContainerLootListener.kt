package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.managers.ContainerLootManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player chamber container loot (v1.5.7, opt-in via
 * `chests.per-player-loot`). Lootr-style: every player who opens a chamber's
 * chest/trapped-chest/barrel sees their own private copy of its contents,
 * so the second player into a chamber doesn't find gutted chests.
 *
 * How it works:
 *  - The real block's inventory is the pristine template and is never
 *    modified. Vanilla opening is cancelled; the player gets a virtual
 *    inventory seeded from their stored copy (or cloned from the template
 *    on first open) and persisted on close ([ContainerLootManager]).
 *  - Double chests are keyed by the left half's position so both halves
 *    share one 54-slot copy.
 *  - Containers placed by players inside a chamber (when protection allows
 *    it) are PDC-tagged at place time and keep vanilla behaviour.
 *  - Hopper/hopper-minecart movement in or out of an eligible container is
 *    cancelled — automation would drain or pollute the shared template.
 *  - Admins holding `tcp.admin.containers` open the REAL container by
 *    sneaking (template editing); a normal click still gets their copy, so
 *    ops experience the feature like players do (no silent bypass).
 *
 * Per-player copies reset with the chamber (ResetManager calls
 * [ContainerLootManager.clearChamber]).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContainerLootListener(private val plugin: TrialChamberPro) : Listener {

    private val playerPlacedKey = NamespacedKey(plugin, "player_placed_container")

    /** Anti double-fire: last virtual-open millis per player. */
    private val openDebounce = ConcurrentHashMap<UUID, Long>()

    /** Marks our virtual inventories and carries the persistence key. */
    class ContainerLootHolder(
        val chamberId: Int,
        val pos: ContainerLootManager.ContainerPos
    ) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private companion object {
        val ELIGIBLE = setOf(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL)
    }

    private fun enabled() = plugin.config.getBoolean("chests.per-player-loot", false)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerOpen(event: PlayerInteractEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (block.type !in ELIGIBLE) return

        val chamber = plugin.chamberManager.getCachedChamberAt(block.location) ?: return
        if (chamber.isPaused) return

        // Player-placed containers keep vanilla behaviour.
        val state = block.state
        if (state is TileState &&
            state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
        ) return

        val player = event.player

        // Sneak + admin permission = open the real container (template editing).
        if (player.isSneaking && player.hasPermission("tcp.admin.containers")) {
            player.sendMessage(plugin.getMessageComponent("container-template-open"))
            return
        }

        event.isCancelled = true

        val now = System.currentTimeMillis()
        val last = openDebounce[player.uniqueId]
        if (last != null && now - last < 700) return
        openDebounce[player.uniqueId] = now
        if (openDebounce.size > 100) openDebounce.entries.removeIf { now - it.value > 10_000 }

        // Snapshot the pristine template + normalized position SYNCHRONOUSLY
        // (we're on the block's region thread right now).
        val container = state as? Container ?: return
        val inv = container.inventory
        val holder = inv.holder
        val (keyBlock, template) = if (holder is DoubleChest) {
            val left = (holder.leftSide as? Chest)?.block ?: block
            left to holder.inventory.contents.map { it?.clone() }.toTypedArray()
        } else {
            block to inv.contents.map { it?.clone() }.toTypedArray()
        }
        val pos = ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z)
        val size = template.size

        plugin.launchAsync {
            val stored = plugin.containerLootManager.loadContents(chamber.id, pos, player.uniqueId)
            val contents = stored ?: template
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runAtEntity(player, Runnable {
                    try {
                        if (player.isOnline) {
                            val lootHolder = ContainerLootHolder(chamber.id, pos)
                            val virtual = plugin.server.createInventory(
                                lootHolder, size, plugin.getGuiText("gui.container-loot.title")
                            )
                            lootHolder.backing = virtual
                            // Tolerate size drift (e.g. template grew into a double
                            // chest after the copy was stored).
                            for (i in 0 until minOf(size, contents.size)) {
                                virtual.setItem(i, contents[i])
                            }
                            player.openInventory(virtual)
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[ContainerLoot] Failed to open copy for ${player.name}: ${e.message}")
                    }
                    continuation.resume(Unit) {}
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onContainerClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? ContainerLootHolder ?: return
        val player = event.player as? Player ?: return
        // Copy on the event thread; persist async.
        val contents = event.inventory.contents.map { it?.clone() }.toTypedArray()
        plugin.launchAsync {
            plugin.containerLootManager.saveContents(holder.chamberId, holder.pos, player.uniqueId, contents)
        }
    }

    /**
     * Tag containers players place inside chambers (only reachable when
     * protection allows placement) so they keep vanilla behaviour forever.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onContainerPlace(event: BlockPlaceEvent) {
        if (event.block.type !in ELIGIBLE) return
        if (plugin.chamberManager.getCachedChamberAt(event.block.location) == null) return
        val state = event.block.state as? TileState ?: return
        state.persistentDataContainer.set(playerPlacedKey, PersistentDataType.BYTE, 1)
        state.update()
    }

    /**
     * Block automation against the pristine template: hoppers draining it
     * would empty the shared source; hoppers feeding it would pollute every
     * player's first-open copy.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHopperMove(event: InventoryMoveItemEvent) {
        if (!enabled()) return
        if (isProtectedTemplate(event.source) || isProtectedTemplate(event.destination)) {
            event.isCancelled = true
        }
    }

    private fun isProtectedTemplate(inv: Inventory): Boolean {
        val block: Block = when (val h = inv.holder) {
            is DoubleChest -> (h.leftSide as? Chest)?.block ?: return false
            is Chest -> h.block
            is org.bukkit.block.Barrel -> h.block
            else -> return false
        }
        if (block.type !in ELIGIBLE) return false
        val chamber = plugin.chamberManager.getCachedChamberAt(block.location) ?: return false
        if (chamber.isPaused) return false
        val state = block.state as? TileState ?: return false
        return !state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
    }
}

package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.managers.ContainerLootManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockState
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
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.Lootable
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player chamber container loot (opt-in via `chests.per-player-loot`).
 * Lootr-style: every player who opens a chamber's chest/trapped-chest/barrel/
 * dispenser/dropper sees their own private copy of its contents, so the second
 * player into a chamber doesn't find gutted containers.
 *
 * How it works (v1.6.3 rework — UniqueLoot-style independent rolls):
 *  - On open, the player gets THEIR contents: (1) their saved copy if any, else
 *    (2) an OP **override** (a `container_template` row with `op_edited = 1`)
 *    cloned fresh, else (3) a **fresh, independent roll** of the block's vanilla
 *    loot table. Two players opening the same untouched container get different
 *    loot — true Lootr behaviour.
 *  - The block's loot table is NEVER consumed: the interact open is cancelled
 *    and [onLootGenerate] re-applies the table if anything tries to roll it. So
 *    after a reset clears per-player copies ([ContainerLootManager.clearChamber]),
 *    the next open rolls fresh again — "vanilla, but repeatable".
 *  - **No frozen auto-template.** `container_template` holds two kinds of row:
 *    `op_edited = 0` registry entries (so the container lists in the management
 *    GUI — they never affect loot) and `op_edited = 1` OP overrides (cloned to
 *    every player, still per-player, re-cloned each reset).
 *  - **Editing is GUI-only** (`/trial menu` → chamber → Container Loot, or
 *    `/trial container edit`). Editing a container saves an override; shift-left /
 *    `/trial container resetone` reverts it to vanilla. The old in-world sneak-edit
 *    is gone (it interfered with normal opening).
 *  - Per-player copies live in `player_container_loot` and reset with the chamber.
 *  - Double chests are keyed by the left half so both halves share one 54-slot copy.
 *  - Containers placed by players inside a chamber are PDC-tagged at place time
 *    and keep vanilla behaviour.
 *  - Hopper movement in/out of an eligible container is cancelled — automation
 *    would drain or pollute the source.
 *
 * Container loot tables are also captured/restored by the snapshot system
 * (`NBTUtil`) so a broken or reset container comes back with its loot table intact.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContainerLootListener(private val plugin: BetterTrialChambers) : Listener {

    private val playerPlacedKey = NamespacedKey("trialchamberpro", "player_placed_container")

    /** Anti double-fire: last virtual-open millis per player. */
    private val openDebounce = ConcurrentHashMap<UUID, Long>()

    /** Marks a player's private copy inventory (saved back to player_container_loot). */
    class CopyHolder(
        val chamberId: Int,
        val pos: ContainerLootManager.ContainerPos
    ) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    /**
     * Marks an op editing a container's override (saved back to container_template
     * with `op_edited = 1` via [onContainerClose]). [returnChamber] is set when the
     * editor was opened from the management GUI/command — closing it reopens the
     * Container Loot view (the "back" button a deposit-style inventory can't have).
     */
    class TemplateHolder(
        val chamberId: Int,
        val pos: ContainerLootManager.ContainerPos,
        val returnChamber: com.esmpfun.bettertrialchambers.models.Chamber? = null
    ) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private companion object {
        val ELIGIBLE = setOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER
        )
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

        val container = state as? Container ?: return
        val player = event.player

        // Editing is GUI-only now (no in-world sneak-edit). Every open gets the
        // player their own per-player copy.
        event.isCancelled = true

        val now = System.currentTimeMillis()
        val last = openDebounce[player.uniqueId]
        if (last != null && now - last < 700) return
        openDebounce[player.uniqueId] = now
        if (openDebounce.size > 100) openDebounce.entries.removeIf { now - it.value > 10_000 }

        // Resolve the normalized key block (left half of a double chest) and the
        // inventory size SYNCHRONOUSLY — we're on the block's region thread now.
        val inv = container.inventory
        val holder = inv.holder
        val keyBlock = if (holder is DoubleChest) (holder.leftSide as? Chest)?.block ?: block else block
        val size = if (holder is DoubleChest) 54 else inv.size
        val pos = ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z)
        val keyLoc = keyBlock.location
        // Read the block material now (on the region thread) — block access off
        // the region thread throws.
        val keyMaterial = keyBlock.type

        plugin.launchAsync {
            // Resolve THIS player's contents:
            //  1. an already-saved copy (their loot until the chamber resets), else
            //  2. an OP override (op_edited template) cloned fresh, else
            //  3. a fresh independent roll of the container's vanilla loot table.
            // The loot table is never consumed (block open is cancelled + the
            // LootGenerateEvent guard re-applies it), so after a reset clears the
            // copy, the next open rolls fresh again — "vanilla, but repeatable".
            val existing = plugin.containerLootManager.loadContents(chamber.id, pos, player.uniqueId)
            val contents = when {
                existing != null -> existing
                else -> plugin.containerLootManager.loadOverride(chamber.id, pos)
                    ?: materializeOnRegion(keyLoc, size)
            }

            // Ensure a registry row exists so the container shows up in the
            // management GUI. op_edited = 0 (auto) — this row never freezes loot;
            // it's a listing entry only. Skip if a row already exists so we never
            // clobber an existing override's op_edited flag.
            if (existing == null && !plugin.containerLootManager.hasTemplate(chamber.id, pos)) {
                plugin.containerLootManager.saveTemplate(chamber.id, pos, contents, keyMaterial)
            }

            openVirtual(
                player, CopyHolder(chamber.id, pos), size,
                plugin.getGuiText("gui.container-loot.title"), contents
            )
        }
    }

    /**
     * Bulletproofs the "loot table is never consumed" invariant: if anything
     * vanilla-rolls a chamber container's loot table (a path that bypasses our
     * cancelled interact open), cancel it and re-apply the table so the block
     * keeps re-rolling forever. Mirrors UniqueLoot's approach.
     *
     * Only acts on REAL block containers inside a non-paused, non-player-placed
     * chamber position — plugin rolls into virtual inventories (our own
     * per-player materialize) have a null/non-block holder and pass straight
     * through, so loot still generates for those.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onLootGenerate(event: org.bukkit.event.world.LootGenerateEvent) {
        if (!enabled()) return
        val holder = event.inventoryHolder
        val block: Block = when (holder) {
            is DoubleChest -> (holder.leftSide as? Chest)?.block ?: return
            is BlockInventoryHolder -> holder.block
            else -> return // virtual inventory (our materialize) or non-container — leave alone
        }
        if (block.type !in ELIGIBLE) return
        val chamber = plugin.chamberManager.getCachedChamberAt(block.location) ?: return
        if (chamber.isPaused) return
        val state = block.state
        if (state is TileState && state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)) return

        val table = event.lootTable
        event.isCancelled = true
        // Re-apply the table so the block entity keeps it (a cancelled generate
        // would otherwise leave it armed but this makes the intent explicit and
        // covers holders that clear on generate).
        fun reapply(h: InventoryHolder?) {
            when (h) {
                is DoubleChest -> { reapply(h.leftSide); reapply(h.rightSide) }
                is org.bukkit.loot.Lootable -> {
                    h.lootTable = table
                    (h as? TileState)?.update(true, false)
                }
                else -> {}
            }
        }
        reapply(holder)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onContainerClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val contents = event.inventory.contents.map { it?.clone() }.toTypedArray()
        when (val holder = event.inventory.holder) {
            is CopyHolder -> plugin.launchAsync {
                plugin.containerLootManager.saveContents(holder.chamberId, holder.pos, player.uniqueId, contents)
            }
            is TemplateHolder -> {
                plugin.launchAsync {
                    // Contents-only update so the stored container icon is preserved.
                    plugin.containerLootManager.updateTemplateContents(holder.chamberId, holder.pos, contents)
                }
                // Opened from the management GUI/command → land back in the
                // Container Loot view (the "back" affordance). One tick later so
                // we're not re-opening an inventory inside the close event.
                holder.returnChamber?.let { chamber ->
                    plugin.scheduler.runAtEntityLater(player, Runnable {
                        if (player.isOnline) plugin.menuService.openContainerLoot(player, chamber)
                    }, 1L)
                }
            }
            else -> return
        }
    }

    /**
     * v1.7.2: persists any private-copy / template-editor inventories still open
     * when the plugin disables. Previously `pluginScope.cancel()` could discard
     * the async close-save, silently losing whatever the player had taken out of
     * (or left in) their open copy. Saves synchronously (runBlocking — the DB
     * pool is still open at this point in onDisable), then closes the view.
     */
    fun shutdown() {
        plugin.server.onlinePlayers.forEach { player ->
            val top = player.openInventory.topInventory
            when (val holder = top.holder) {
                is CopyHolder -> {
                    val contents = top.contents.map { it?.clone() }.toTypedArray()
                    runCatching {
                        kotlinx.coroutines.runBlocking {
                            plugin.containerLootManager.saveContents(holder.chamberId, holder.pos, player.uniqueId, contents)
                        }
                    }.onFailure { plugin.logger.warning("[ContainerLoot] Disable-flush failed for ${player.name}: ${it.message}") }
                    player.closeInventory()
                }
                is TemplateHolder -> {
                    val contents = top.contents.map { it?.clone() }.toTypedArray()
                    runCatching {
                        kotlinx.coroutines.runBlocking {
                            plugin.containerLootManager.updateTemplateContents(holder.chamberId, holder.pos, contents)
                        }
                    }.onFailure { plugin.logger.warning("[ContainerLoot] Disable-flush failed for ${player.name}: ${it.message}") }
                    player.closeInventory()
                }
                else -> {}
            }
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

    // ==== Helpers ====

    /**
     * Materializes a container's template on the block's region thread: rolls
     * the vanilla loot table into a fresh inventory (the only way to get the
     * real generated loot — the live inventory is empty until vanilla opens
     * it), or falls back to the live contents when there is no loot table.
     * Handles single containers and double chests (each half rolled separately).
     */
    private suspend fun materializeOnRegion(keyLoc: Location, size: Int): Array<ItemStack?> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            plugin.scheduler.runAtLocation(keyLoc, Runnable {
                val result = try {
                    materializeTemplate(keyLoc.block, size)
                } catch (e: Exception) {
                    plugin.logger.warning("[ContainerLoot] Template materialize failed at ${keyLoc.blockX},${keyLoc.blockY},${keyLoc.blockZ}: ${e.message}")
                    arrayOfNulls<ItemStack?>(size)
                }
                cont.resume(result) {}
            })
        }

    private fun materializeTemplate(block: Block, size: Int): Array<ItemStack?> {
        val container = block.state as? Container ?: return arrayOfNulls(size)
        val holder = container.inventory.holder
        return if (holder is DoubleChest) {
            val out = arrayOfNulls<ItemStack?>(54)
            rollHalf(holder.leftSide as? Chest, out, 0)
            rollHalf(holder.rightSide as? Chest, out, 27)
            out
        } else {
            rollSingle(block.state, container.inventory, size)
        }
    }

    private fun rollSingle(state: BlockState, inv: Inventory, size: Int): Array<ItemStack?> {
        val lootable = state as? Lootable
        val source: Array<ItemStack?> = if (lootable?.lootTable != null && state is Container) {
            val temp = Bukkit.createInventory(null, size)
            lootable.lootTable!!.fillInventory(temp, java.util.Random(), LootContext.Builder(state.block.location).build())
            temp.contents
        } else {
            inv.contents
        }
        return Array(size) { source.getOrNull(it)?.clone() }
    }

    private fun rollHalf(chest: Chest?, out: Array<ItemStack?>, offset: Int) {
        chest ?: return
        val half: Array<ItemStack?> = if (chest.lootTable != null) {
            val temp = Bukkit.createInventory(null, 27)
            chest.lootTable!!.fillInventory(temp, java.util.Random(), LootContext.Builder(chest.block.location).build())
            temp.contents
        } else {
            chest.blockInventory.contents
        }
        for (i in 0 until 27) out[offset + i] = half.getOrNull(i)?.clone()
    }

    private suspend fun openVirtual(
        player: Player,
        holder: InventoryHolder,
        size: Int,
        title: Component,
        contents: Array<ItemStack?>
    ) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        plugin.scheduler.runAtEntity(player, Runnable {
            try {
                if (player.isOnline) {
                    val virtual = plugin.server.createInventory(holder, size, title)
                    when (holder) {
                        is CopyHolder -> holder.backing = virtual
                        is TemplateHolder -> holder.backing = virtual
                    }
                    for (i in 0 until minOf(size, contents.size)) {
                        virtual.setItem(i, contents[i])
                    }
                    player.openInventory(virtual)
                }
            } catch (e: Exception) {
                plugin.logger.warning("[ContainerLoot] Failed to open container for ${player.name}: ${e.message}")
            }
            cont.resume(Unit) {}
        })
    }

    /**
     * Scans every loaded/loadable chunk in [chamber]'s bounds for eligible
     * containers and materializes a shared template for any that don't have one
     * yet (rolling their loot table). Lets admins prep/edit all container loot
     * from the GUI/command without first opening each one in-world. Returns the
     * number of new templates created. Folia-safe (region-hops per chunk).
     */
    suspend fun materializeChamber(chamber: com.esmpfun.bettertrialchambers.models.Chamber): Int {
        val world = chamber.getWorld() ?: return 0
        var created = 0
        val minCX = chamber.minX shr 4; val maxCX = chamber.maxX shr 4
        val minCZ = chamber.minZ shr 4; val maxCZ = chamber.maxZ shr 4
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val rep = Location(world, (cx shl 4).toDouble(), chamber.minY.toDouble(), (cz shl 4).toDouble())
                val rolled = kotlinx.coroutines.suspendCancellableCoroutine<List<Triple<ContainerLootManager.ContainerPos, Array<ItemStack?>, Material>>> { cont ->
                    plugin.scheduler.runAtLocation(rep, Runnable {
                        val results = mutableListOf<Triple<ContainerLootManager.ContainerPos, Array<ItemStack?>, Material>>()
                        try {
                            if (!world.isChunkLoaded(cx, cz)) world.getChunkAt(cx, cz)
                            for (te in world.getChunkAt(cx, cz).tileEntities) {
                                if (te !is Container) continue
                                val b = te.block
                                if (b.type !in ELIGIBLE) continue
                                if (!chamber.contains(b.location)) continue
                                if ((b.state as? TileState)?.persistentDataContainer
                                        ?.has(playerPlacedKey, PersistentDataType.BYTE) == true) continue
                                val holder = te.inventory.holder
                                val keyBlock = if (holder is DoubleChest) (holder.leftSide as? Chest)?.block ?: b else b
                                if (holder is DoubleChest && keyBlock != b) continue // right half — handled by the left
                                val size = if (holder is DoubleChest) 54 else te.inventory.size
                                results.add(
                                    Triple(
                                        ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z),
                                        materializeTemplate(keyBlock, size),
                                        keyBlock.type
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("[ContainerLoot] Chamber scan failed in chunk $cx,$cz: ${e.message}")
                        }
                        cont.resume(results) {}
                    })
                }
                for ((pos, contents, material) in rolled) {
                    if (!plugin.containerLootManager.hasTemplate(chamber.id, pos)) {
                        plugin.containerLootManager.saveTemplate(chamber.id, pos, contents, material)
                        created++
                    }
                }
            }
        }
        return created
    }

    /**
     * Opens a container's shared template for editing (used by the GUI and the
     * `/trial container` command). Does nothing if no template exists yet — the
     * caller should materialize first. Saved back to `container_template` by the
     * existing [onContainerClose] handler.
     */
    suspend fun openTemplateEditor(
        player: Player,
        chamber: com.esmpfun.bettertrialchambers.models.Chamber,
        pos: ContainerLootManager.ContainerPos
    ): Boolean {
        val template = plugin.containerLootManager.loadTemplate(chamber.id, pos) ?: return false
        val size = if (template.size in intArrayOf(9, 18, 27, 36, 45, 54)) template.size else 27
        openVirtual(
            player, TemplateHolder(chamber.id, pos, returnChamber = chamber), size,
            plugin.getGuiText("gui.container-loot.template-title"), template
        )
        return true
    }

    private fun isProtectedTemplate(inv: Inventory): Boolean {
        val block: Block = when (val h = inv.holder) {
            is DoubleChest -> (h.leftSide as? Chest)?.block ?: return false
            is BlockInventoryHolder -> h.block
            else -> return false
        }
        if (block.type !in ELIGIBLE) return false
        val chamber = plugin.chamberManager.getCachedChamberAt(block.location) ?: return false
        if (chamber.isPaused) return false
        val state = block.state as? TileState ?: return false
        return !state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
    }
}

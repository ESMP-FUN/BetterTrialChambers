package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Enforces owner-only pickup for Trial Keys dropped by the plugin-driven key-drop
 * path in [com.esmpfun.bettertrialchambers.managers.SpawnerWaveManager] when a
 * wave was driven by a non-vanilla [com.esmpfun.bettertrialchambers.providers.TrialMobProvider].
 *
 * Sibling of [VaultDropOwnerListener]; intentionally duplicated rather than broadened so
 * each drop type keeps its own PDC namespace and grace-window config.
 *
 * Tag keys:
 *   - `tcp:spawner_key_owner` (STRING, UUID)
 *   - `tcp:spawner_key_dropped_at` (LONG, epoch millis)
 *
 * Bypass: `btc.bypass.droplock` (shared with vault drops).
 * Grace window: `reset.spawner-key-drop-owner-grace-seconds` (default 30; `0` = owner-locked
 * until the item despawns naturally).
 */
class SpawnerKeyDropOwnerListener(private val plugin: BetterTrialChambers) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val item = event.item
        val ownerId = readOwner(item) ?: return
        if (!stillOwned(item)) return

        // A mob can pick an item up too, and a zombie wandering off with someone's
        // reward is the same loss to them as another player taking it.
        val picker = event.entity as? Player
        if (picker == null) {
            event.isCancelled = true
            return
        }

        if (picker.uniqueId != ownerId && !picker.hasPermission("btc.bypass.droplock")) {
            event.isCancelled = true
        }
    }

    /**
     * A hopper, a hopper minecart or a chest minecart takes items without any
     * player being involved, and that never came through the handler above: a
     * hopper put down next to the drop collected everybody's trial key.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHopperPickup(event: org.bukkit.event.inventory.InventoryPickupItemEvent) {
        val item = event.item
        readOwner(item) ?: return
        if (stillOwned(item)) event.isCancelled = true
    }

    /** True while this drop is still held for whoever it belongs to. */
    private fun stillOwned(item: Item): Boolean {
        val graceSeconds = plugin.config.getLong("reset.spawner-key-drop-owner-grace-seconds", 30L)
        if (graceSeconds <= 0) return true // held until it despawns
        val droppedAt = readDropTime(item) ?: return false
        return System.currentTimeMillis() - droppedAt < graceSeconds * 1000L
    }

    private fun readOwner(item: Item): UUID? {
        val raw = item.persistentDataContainer.get(OWNER_KEY, PersistentDataType.STRING) ?: return null
        return try { UUID.fromString(raw) } catch (_: IllegalArgumentException) { null }
    }

    private fun readDropTime(item: Item): Long? {
        return item.persistentDataContainer.get(DROPPED_AT_KEY, PersistentDataType.LONG)
    }

    companion object {
        lateinit var OWNER_KEY: NamespacedKey
            private set
        lateinit var DROPPED_AT_KEY: NamespacedKey
            private set

        fun init(plugin: BetterTrialChambers) {
            OWNER_KEY = NamespacedKey("trialchamberpro", "spawner_key_owner")
            DROPPED_AT_KEY = NamespacedKey("trialchamberpro", "spawner_key_dropped_at")
        }
    }
}

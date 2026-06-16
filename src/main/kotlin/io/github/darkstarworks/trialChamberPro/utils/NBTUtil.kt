package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.block.BlockState
import org.bukkit.block.Container
import org.bukkit.block.DecoratedPot
import org.bukkit.block.TileState
import org.bukkit.block.TrialSpawner
import org.bukkit.block.Vault
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.Lootable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Utility class for handling NBT data from tile entities.
 * Captures and restores data for Trial Spawners, Vaults, Decorated Pots, and
 * loot containers (chests/barrels/dispensers/droppers).
 */
object NBTUtil {

    /**
     * Captures tile entity data from a block state.
     *
     * @param state The block state
     * @return Map of NBT data, or null if not a tile entity
     */
    fun captureTileEntity(state: BlockState): Map<String, Any>? {
        return when (state) {
            is TrialSpawner -> captureTrialSpawner(state)
            is Vault -> captureVault(state)
            is DecoratedPot -> captureDecoratedPot(state)
            is Container -> captureContainer(state)
            else -> null
        }
    }

    /**
     * Captures Trial Spawner NBT data.
     * Stores cooldown settings and ominous state for proper restoration.
     */
    private fun captureTrialSpawner(spawner: TrialSpawner): Map<String, Any> {
        return try {
            mapOf(
                "type" to "TRIAL_SPAWNER",
                "ominous" to spawner.isOminous,
                "cooldownLength" to spawner.cooldownLength,
                "requiredPlayerRange" to spawner.requiredPlayerRange
            )
        } catch (_: Exception) {
            // Fallback if API methods not available
            mapOf("type" to "TRIAL_SPAWNER")
        }
    }

    /**
     * Captures Vault NBT data.
     * Stores vault type and state information.
     */
    private fun captureVault(vault: Vault): Map<String, Any> {
        return mapOf(
            "type" to "VAULT",
            "material" to vault.block.type.name
            // Note: Vault state is handled by BlockData
        )
    }

    /**
     * Captures Decorated Pot NBT data.
     * Stores sherd information for all sides.
     */
    private fun captureDecoratedPot(pot: DecoratedPot): Map<String, Any> {
        val sherds = mutableMapOf<String, String>()

        DecoratedPot.Side.entries.forEach { side ->
            val sherd = pot.getSherd(side)
            sherds[side.name] = sherd.name
        }

        return mapOf(
            "type" to "DECORATED_POT",
            "sherds" to sherds
        )
    }

    /**
     * Captures a loot container (chest/barrel/dispenser/dropper).
     *
     * A naturally-generated trial-chamber container stores its loot as an
     * UNROLLED loot table (empty inventory + a `LootTable`/seed) until a player
     * first opens it. We capture the loot-table key + seed when present so the
     * container can be re-armed on restore (a plain BlockData restore wipes the
     * block entity, losing the loot table — which is why container loot did not
     * survive resets before v1.5.9). When no loot table is set (already rolled,
     * or admin-filled), we capture the literal contents instead.
     */
    private fun captureContainer(container: Container): Map<String, Any> {
        val lootable = container as? Lootable
        val table = lootable?.lootTable
        return if (table != null) {
            mapOf(
                "type" to "CONTAINER",
                "lootTable" to table.key.toString(),
                "seed" to lootable.seed.toString()
            )
        } else {
            mapOf(
                "type" to "CONTAINER",
                "items" to encodeItems(container.inventory.contents)
            )
        }
    }

    /**
     * Restores tile entity data to a block state.
     *
     * @param state The block state to restore to
     * @param data The NBT data map
     * @return True if restoration was successful
     */
    fun restoreTileEntity(state: BlockState, data: Map<String, Any>): Boolean {
        val type = data["type"] as? String ?: return false

        return when (type) {
            "TRIAL_SPAWNER" -> restoreTrialSpawner(state as? TrialSpawner ?: return false, data)
            "VAULT" -> restoreVault(state as? Vault ?: return false, data)
            "DECORATED_POT" -> restoreDecoratedPot(state as? DecoratedPot ?: return false, data)
            "CONTAINER" -> restoreContainer(state as? Container ?: return false, data)
            else -> false
        }
    }

    /**
     * Restores a loot container: re-arms its captured loot table (so it rolls
     * fresh loot when next accessed), or restores literal contents.
     */
    private fun restoreContainer(container: Container, data: Map<String, Any>): Boolean {
        return try {
            val lootable = container as? Lootable
            val key = data["lootTable"] as? String
            if (lootable != null && key != null) {
                val table = NamespacedKey.fromString(key)?.let { Bukkit.getLootTable(it) }
                if (table != null) {
                    container.inventory.clear()
                    val seed = (data["seed"] as? String)?.toLongOrNull() ?: 0L
                    lootable.setLootTable(table, seed)
                }
            } else {
                val items = data["items"] as? String
                if (items != null) {
                    val decoded = decodeItems(items)
                    val inv = container.inventory
                    inv.clear()
                    for (i in 0 until minOf(inv.size, decoded.size)) inv.setItem(i, decoded[i])
                }
            }
            (container as? TileState)?.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun encodeItems(items: Array<ItemStack?>): String {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(items.size)
            for (item in items) {
                if (item == null || item.type.isAir) {
                    out.writeInt(-1)
                } else {
                    val bytes = item.serializeAsBytes()
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun decodeItems(encoded: String): Array<ItemStack?> {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val size = input.readInt()
            require(size in 0..128) { "implausible container size $size" }
            return Array(size) {
                val len = input.readInt()
                if (len < 0) null
                else {
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    ItemStack.deserializeBytes(buf)
                }
            }
        }
    }

    /**
     * Restores Trial Spawner data and resets its state.
     * CRITICAL: This clears tracked players so the spawner can be reactivated
     * and will drop trial keys again when completed.
     *
     * NOTE: Cooldown length is NOT restored from snapshot - it's controlled by
     * the config setting (reset.spawner-cooldown-minutes) and applied in
     * ResetManager.resetTrialSpawners() which runs AFTER block restoration.
     */
    private fun restoreTrialSpawner(spawner: TrialSpawner, data: Map<String, Any>): Boolean {
        return try {
            // Clear all tracked players - this is the KEY fix for trial key drops!
            // Without this, the spawner "remembers" players who already completed it
            // and won't spawn mobs or drop keys for them.
            spawner.trackedPlayers.forEach { player ->
                spawner.stopTrackingPlayer(player)
            }

            // Clear all tracked entities (spawned mobs that haven't been killed)
            spawner.trackedEntities.forEach { entity ->
                spawner.stopTrackingEntity(entity)
            }

            // Restore ominous state from snapshot
            val wasOminous = data["ominous"] as? Boolean ?: false
            spawner.isOminous = wasOminous

            // NOTE: cooldownLength is intentionally NOT restored from snapshot.
            // The cooldown is controlled by config (reset.spawner-cooldown-minutes)
            // and set by ResetManager.resetTrialSpawners() after block restoration.
            // This ensures the config setting always takes precedence.

            // Restore required player range if captured
            val requiredRange = data["requiredPlayerRange"] as? Int
            if (requiredRange != null && requiredRange > 0) {
                spawner.requiredPlayerRange = requiredRange
            }

            // Commit the changes
            spawner.update(true, false)
            true
        } catch (e: Exception) {
            // Log but don't fail - BlockData restoration still works
            false
        }
    }

    /**
     * Restores Vault data.
     */
    private fun restoreVault(vault: Vault, data: Map<String, Any>): Boolean {
        try {
            vault.update(true, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Restores Decorated Pot data.
     */
    private fun restoreDecoratedPot(pot: DecoratedPot, data: Map<String, Any>): Boolean {
        try {
            @Suppress("UNCHECKED_CAST")
            val sherds = data["sherds"] as? Map<String, String> ?: return false

            sherds.forEach { (sideName, sherdName) ->
                val side = DecoratedPot.Side.valueOf(sideName)
                val material = try {
                    org.bukkit.Material.valueOf(sherdName)
                } catch (_: IllegalArgumentException) {
                    null
                }

                if (material != null) {
                    pot.setSherd(side, material)
                }
            }

            pot.update(true, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}

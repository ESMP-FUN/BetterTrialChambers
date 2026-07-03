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
            // v1.7.2: decoration/utility tile entities — before this, only the four
            // types above survived a reset; signs/heads/banners/etc. restored blank.
            // Note: Lectern/Jukebox/ChiseledBookshelf are TileStateInventoryHolder but
            // NOT org.bukkit.block.Container, so these branches don't shadow Container.
            is org.bukkit.block.Sign -> captureSign(state)
            is org.bukkit.block.Skull -> captureSkull(state)
            is org.bukkit.block.Banner -> captureBanner(state)
            is org.bukkit.block.Lectern -> captureLectern(state)
            is org.bukkit.block.Jukebox -> captureJukebox(state)
            is org.bukkit.block.ChiseledBookshelf -> captureChiseledBookshelf(state)
            is org.bukkit.block.BrushableBlock -> captureBrushableBlock(state)
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
     * Captures Decorated Pot NBT data: its sherds AND its loot table.
     *
     * Trial-chamber pots hold their break-loot as an UNROLLED `LootTable` (the
     * pot is `Lootable`); a plain BlockData restore wipes the block entity and
     * loses it, which is why pots came back empty after a reset before v1.5.11.
     * We capture the loot-table key + seed when present (re-armed on restore so
     * the pot drops fresh loot when next broken), and fall back to the literal
     * stored item otherwise.
     */
    private fun captureDecoratedPot(pot: DecoratedPot): Map<String, Any> {
        val sherds = mutableMapOf<String, String>()
        DecoratedPot.Side.entries.forEach { side ->
            sherds[side.name] = pot.getSherd(side).name
        }

        val map = mutableMapOf<String, Any>(
            "type" to "DECORATED_POT",
            "sherds" to sherds
        )
        val lootable = pot as? Lootable
        val table = lootable?.lootTable
        if (table != null) {
            map["lootTable"] = table.key.toString()
            map["seed"] = lootable.seed.toString()
        } else {
            pot.inventory.item?.takeUnless { it.type.isAir }?.let {
                map["item"] = Base64.getEncoder().encodeToString(it.serializeAsBytes())
            }
        }
        return map
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

    // ==================== v1.7.2 decoration tile entities ====================
    // Snapshot maps must hold only plain JDK-serializable values (String/Boolean/
    // Int/List/Map) — snapshot files are Java-serialized. Components are stored as
    // Gson-serialized JSON strings (lossless round-trip).

    private val gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()

    /** Sign: both sides' lines (Component JSON), glow + dye color per side, waxed state. */
    private fun captureSign(sign: org.bukkit.block.Sign): Map<String, Any> = try {
        val map = mutableMapOf<String, Any>("type" to "SIGN", "waxed" to sign.isWaxed)
        for (side in org.bukkit.block.sign.Side.entries) {
            val s = sign.getSide(side)
            map["${side.name}_lines"] = s.lines().map { gson.serialize(it) }
            map["${side.name}_glowing"] = s.isGlowingText
            s.color?.let { map["${side.name}_color"] = it.name }
        }
        map
    } catch (_: Exception) {
        mapOf("type" to "SIGN")
    }

    private fun restoreSign(sign: org.bukkit.block.Sign, data: Map<String, Any>): Boolean {
        return try {
            for (side in org.bukkit.block.sign.Side.entries) {
                val s = sign.getSide(side)
                @Suppress("UNCHECKED_CAST")
                (data["${side.name}_lines"] as? List<String>)?.forEachIndexed { i, json ->
                    runCatching { s.line(i, gson.deserialize(json)) }
                }
                (data["${side.name}_glowing"] as? Boolean)?.let { s.isGlowingText = it }
                (data["${side.name}_color"] as? String)?.let { name ->
                    runCatching { s.color = org.bukkit.DyeColor.valueOf(name) }
                }
            }
            (data["waxed"] as? Boolean)?.let { sign.isWaxed = it }
            sign.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Skull: owner profile as plain fields (uuid/name + the `textures` property's
     * value/signature) so player-head skins survive; profile objects themselves are
     * not JDK-serializable. Restored via Paper's Bukkit.createProfile.
     */
    private fun captureSkull(skull: org.bukkit.block.Skull): Map<String, Any> = try {
        val map = mutableMapOf<String, Any>("type" to "SKULL")
        skull.playerProfile?.let { profile ->
            profile.id?.let { map["uuid"] = it.toString() }
            profile.name?.let { map["name"] = it }
            profile.properties.firstOrNull { it.name == "textures" }?.let { prop ->
                map["textureValue"] = prop.value
                prop.signature?.let { map["textureSignature"] = it }
            }
        }
        runCatching { skull.noteBlockSound?.let { map["noteBlockSound"] = it.toString() } }
        map
    } catch (_: Exception) {
        mapOf("type" to "SKULL")
    }

    private fun restoreSkull(skull: org.bukkit.block.Skull, data: Map<String, Any>): Boolean {
        return try {
            val uuid = (data["uuid"] as? String)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            val name = data["name"] as? String
            if (uuid != null || name != null) {
                val profile = Bukkit.createProfile(uuid, name)
                (data["textureValue"] as? String)?.let { value ->
                    profile.setProperty(
                        com.destroystokyo.paper.profile.ProfileProperty(
                            "textures", value, data["textureSignature"] as? String
                        )
                    )
                }
                skull.setPlayerProfile(profile)
            }
            (data["noteBlockSound"] as? String)?.let { key ->
                runCatching { NamespacedKey.fromString(key)?.let { skull.noteBlockSound = it } }
            }
            skull.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val bannerPatternRegistry
        get() = io.papermc.paper.registry.RegistryAccess.registryAccess()
            .getRegistry(io.papermc.paper.registry.RegistryKey.BANNER_PATTERN)

    /** Banner: patterns as "DYE_COLOR|namespace:key" strings (PatternType is registry-keyed). */
    private fun captureBanner(banner: org.bukkit.block.Banner): Map<String, Any> = try {
        mapOf(
            "type" to "BANNER",
            "patterns" to banner.patterns.mapNotNull { p ->
                bannerPatternRegistry.getKey(p.pattern)?.let { key -> "${p.color.name}|$key" }
            }
        )
    } catch (_: Exception) {
        mapOf("type" to "BANNER")
    }

    private fun restoreBanner(banner: org.bukkit.block.Banner, data: Map<String, Any>): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST")
            val encoded = data["patterns"] as? List<String> ?: return false
            val patterns = encoded.mapNotNull { entry ->
                val split = entry.split('|', limit = 2)
                if (split.size != 2) return@mapNotNull null
                val color = runCatching { org.bukkit.DyeColor.valueOf(split[0]) }.getOrNull() ?: return@mapNotNull null
                val patternType = NamespacedKey.fromString(split[1])
                    ?.let { bannerPatternRegistry.get(it) } ?: return@mapNotNull null
                org.bukkit.block.banner.Pattern(color, patternType)
            }
            banner.patterns = patterns
            banner.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Lectern: the held book + the open page. */
    private fun captureLectern(lectern: org.bukkit.block.Lectern): Map<String, Any> = try {
        val map = mutableMapOf<String, Any>("type" to "LECTERN", "page" to lectern.page)
        lectern.inventory.getItem(0)?.takeUnless { it.type.isAir }?.let {
            map["book"] = Base64.getEncoder().encodeToString(it.serializeAsBytes())
        }
        map
    } catch (_: Exception) {
        mapOf("type" to "LECTERN")
    }

    private fun restoreLectern(lectern: org.bukkit.block.Lectern, data: Map<String, Any>): Boolean {
        return try {
            (data["book"] as? String)?.let { encoded ->
                lectern.inventory.setItem(0, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)))
            }
            (data["page"] as? Int)?.let { runCatching { lectern.page = it } }
            lectern.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Jukebox: the inserted record. */
    private fun captureJukebox(jukebox: org.bukkit.block.Jukebox): Map<String, Any> = try {
        val map = mutableMapOf<String, Any>("type" to "JUKEBOX")
        jukebox.record.takeUnless { it.type.isAir }?.let {
            map["record"] = Base64.getEncoder().encodeToString(it.serializeAsBytes())
        }
        map
    } catch (_: Exception) {
        mapOf("type" to "JUKEBOX")
    }

    private fun restoreJukebox(jukebox: org.bukkit.block.Jukebox, data: Map<String, Any>): Boolean {
        return try {
            (data["record"] as? String)?.let { encoded ->
                jukebox.setRecord(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)))
            }
            jukebox.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Chiseled bookshelf: its six book slots + last-interacted slot. */
    private fun captureChiseledBookshelf(shelf: org.bukkit.block.ChiseledBookshelf): Map<String, Any> = try {
        mapOf(
            "type" to "CHISELED_BOOKSHELF",
            "items" to encodeItems(shelf.inventory.contents),
            "lastInteractedSlot" to shelf.lastInteractedSlot
        )
    } catch (_: Exception) {
        mapOf("type" to "CHISELED_BOOKSHELF")
    }

    private fun restoreChiseledBookshelf(shelf: org.bukkit.block.ChiseledBookshelf, data: Map<String, Any>): Boolean {
        return try {
            (data["items"] as? String)?.let { encoded ->
                val decoded = decodeItems(encoded)
                val inv = shelf.inventory
                for (i in 0 until minOf(inv.size, decoded.size)) inv.setItem(i, decoded[i])
            }
            (data["lastInteractedSlot"] as? Int)?.let { runCatching { shelf.lastInteractedSlot = it } }
            shelf.update(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Brushable block (suspicious sand/gravel): mirrors the container pattern —
     * re-arm the unrolled loot table when present, else the literal buried item.
     */
    private fun captureBrushableBlock(brushable: org.bukkit.block.BrushableBlock): Map<String, Any> = try {
        val table = brushable.lootTable
        if (table != null) {
            mapOf("type" to "BRUSHABLE", "lootTable" to table.key.toString(), "seed" to brushable.seed.toString())
        } else {
            val map = mutableMapOf<String, Any>("type" to "BRUSHABLE")
            brushable.item?.takeUnless { it.type.isAir }?.let {
                map["item"] = Base64.getEncoder().encodeToString(it.serializeAsBytes())
            }
            map
        }
    } catch (_: Exception) {
        mapOf("type" to "BRUSHABLE")
    }

    private fun restoreBrushableBlock(brushable: org.bukkit.block.BrushableBlock, data: Map<String, Any>): Boolean {
        return try {
            val key = data["lootTable"] as? String
            if (key != null) {
                NamespacedKey.fromString(key)?.let { Bukkit.getLootTable(it) }?.let { table ->
                    // Seed 0 = fresh random roll each reset (same policy as containers/pots).
                    brushable.setLootTable(table, 0L)
                }
            } else {
                (data["item"] as? String)?.let { encoded ->
                    brushable.setItem(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)))
                }
            }
            brushable.update(true, false)
            true
        } catch (_: Exception) {
            false
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
            "SIGN" -> restoreSign(state as? org.bukkit.block.Sign ?: return false, data)
            "SKULL" -> restoreSkull(state as? org.bukkit.block.Skull ?: return false, data)
            "BANNER" -> restoreBanner(state as? org.bukkit.block.Banner ?: return false, data)
            "LECTERN" -> restoreLectern(state as? org.bukkit.block.Lectern ?: return false, data)
            "JUKEBOX" -> restoreJukebox(state as? org.bukkit.block.Jukebox ?: return false, data)
            "CHISELED_BOOKSHELF" -> restoreChiseledBookshelf(state as? org.bukkit.block.ChiseledBookshelf ?: return false, data)
            "BRUSHABLE" -> restoreBrushableBlock(state as? org.bukkit.block.BrushableBlock ?: return false, data)
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
                    // Re-arm with seed 0 (vanilla's "no fixed seed") rather than the
                    // captured worldgen seed. A fixed nonzero seed makes every reset
                    // re-roll IDENTICAL loot; seed 0 tells vanilla to use a fresh
                    // random source on next open — "vanilla, but repeatable". v1.6.3.
                    lootable.setLootTable(table, 0L)
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

            // Re-arm the break-loot the pot lost on a BlockData restore: its loot
            // table (so it rolls fresh loot when next broken) or its literal item.
            val lootable = pot as? Lootable
            val key = data["lootTable"] as? String
            if (lootable != null && key != null) {
                val table = NamespacedKey.fromString(key)?.let { Bukkit.getLootTable(it) }
                if (table != null) {
                    // Seed 0 = fresh random roll each reset (see restoreContainer). v1.6.3.
                    lootable.setLootTable(table, 0L)
                }
            } else {
                (data["item"] as? String)?.let { encoded ->
                    pot.inventory.item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded))
                }
            }

            pot.update(true, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}

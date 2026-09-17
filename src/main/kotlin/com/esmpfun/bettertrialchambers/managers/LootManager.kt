package com.esmpfun.bettertrialchambers.managers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.CommandReward
import com.esmpfun.bettertrialchambers.models.EconomyReward
import com.esmpfun.bettertrialchambers.models.LootItem
import com.esmpfun.bettertrialchambers.models.LootPool
import com.esmpfun.bettertrialchambers.models.LootTable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import kotlin.random.Random

/**
 * Manages loot table parsing and loot generation.
 * Handles weighted random selection, custom items, enchantments, and economy rewards.
 */
class LootManager(private val plugin: BetterTrialChambers) {

    private val lootTables = mutableMapOf<String, LootTable>()

    /**
     * Per-opening redeem tracking (v2.1.0). Passed into [generateLoot] when a real
     * player opens a vault so that capped entries (see [com.esmpfun.bettertrialchambers.models.RedeemScope])
     * they've already claimed are excluded from their roll, and freshly-earned ones
     * are collected in [newlyRedeemed] for the caller to persist after delivery.
     *
     * @param alreadyRedeemed redeemIds this player has already claimed that apply here
     *   (global ONCE claims + this chamber's PER_CHAMBER claims), loaded from the DB.
     */
    class RedeemContext(private val alreadyRedeemed: Set<String>) {
        /** Capped items that actually dropped this opening and must be recorded. */
        val newlyRedeemed = mutableListOf<LootItem>()
        private val consumedThisRoll = mutableSetOf<String>()

        /** True if [item] is capped and already claimed (in the DB, or earlier in this same opening). */
        fun isBlocked(item: LootItem): Boolean =
            item.isCapped() && (item.redeemId in alreadyRedeemed || item.redeemId in consumedThisRoll)

        /** Record that [item] dropped; capped items are queued for persistence and blocked from re-dropping. */
        fun claim(item: LootItem) {
            if (item.isCapped()) {
                val id = item.redeemId ?: return
                if (consumedThisRoll.add(id)) newlyRedeemed += item
            }
        }
    }

    /** One legacy (pre-1.5.0) loot entry that lost its NBT and needs to be re-added. */
    data class LegacyItemRef(
        val table: String,
        val pool: String,
        val kind: String, // "guaranteed" | "weighted"
        val index: Int,
        val material: Material,
        val reason: String
    )

    fun getTable(name: String): LootTable? = lootTables[name]


    fun updateTable(table: LootTable) {
        lootTables[table.name] = table
    }

    /**
     * Loads all loot tables from loot.yml.
     */
    fun loadLootTables() {
        lootTables.clear()

        val lootFile = File(plugin.dataFolder, "loot.yml")
        if (!lootFile.exists()) {
            plugin.logger.warning("loot.yml not found, using defaults")
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile)

        // Loot added through the editor is stored the way the game stores items,
        // so it can be read on this Minecraft version or any later one, but not
        // on an earlier one. If this file came from a newer version than the
        // server is running, take a copy and say so before anything fails to
        // load piecemeal. Files written before stamping existed report 0, which
        // is always older and so never trips this.
        com.esmpfun.bettertrialchambers.utils.SaveVersionStamp.warnIfFromNewerVersion(
            file = lootFile,
            stampedDataVersion = config.getInt(
                "${com.esmpfun.bettertrialchambers.utils.SaveVersionStamp.SECTION}.data-version", 0
            ),
            stampedMinecraftVersion = config.getString(
                "${com.esmpfun.bettertrialchambers.utils.SaveVersionStamp.SECTION}.minecraft-version"
            ),
            logger = plugin.logger,
        )

        val tablesSection = config.getConfigurationSection("loot-tables") ?: return

        tablesUnreadable.clear()
        tablesSection.getKeys(false).forEach { tableName ->
            val tableSection = tablesSection.getConfigurationSection(tableName)
            if (tableSection == null) {
                // Something is under this name that is not a table at all. Said
                // out loud rather than passed over: it used to be skipped in
                // silence, so nobody knew their table was not in use.
                plugin.logger.severe(
                    "loot.yml: '$tableName' does not look like a loot table and has been skipped. " +
                        "A table needs its settings indented underneath its name."
                )
                tablesUnreadable.add(tableName)
                return@forEach
            }

            try {
                val lootTable = parseLootTable(tableName, tableSection)
                lootTables[tableName] = lootTable
                plugin.logger.info("Loaded loot table: $tableName (${lootTable.weightedItems.size} weighted items)")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to parse loot table $tableName: ${e.message}")
                e.printStackTrace()
                tablesUnreadable.add(tableName)
            }
        }

        // v1.5.1: Surface pre-1.5.0 loot entries that lost their NBT (e.g. an
        // ENCHANTED_BOOK row without any enchant data). They still drop as the
        // bare material, re-add them through the editor to restore intent.
        val legacy = findLegacyItems()
        if (legacy.isNotEmpty()) {
            plugin.logger.warning(
                "${legacy.size} loot entr${if (legacy.size == 1) "y has" else "ies have"} no serialized NBT " +
                    "and look like pre-1.5.0 leftovers (e.g. enchanted book without enchantments). " +
                    "Run /trial loot audit to see which tables/pools they're in."
            )
        }
    }

    /**
     * Returns loot entries whose structured fields are obviously insufficient
     * to produce a meaningful item (the v1.5.0 faithful-loot fix only affects
     * NEW entries, older ones still need to be re-added by the admin).
     *
     * Heuristic, only flags rows where the *intended* item plainly differs
     * from what the bare material would produce:
     *  - ENCHANTED_BOOK with no enchant data
     *  - POTION / SPLASH_POTION / LINGERING_POTION / TIPPED_ARROW with no potionType + no customEffect
     *  - GOAT_HORN with no instrument
     * Plain "10 cobblestone" entries are NOT flagged.
     */
    fun findLegacyItems(): List<LegacyItemRef> {
        val out = mutableListOf<LegacyItemRef>()
        lootTables.values.forEach { table ->
            table.getEffectivePools().forEach { pool ->
                pool.guaranteedItems.forEachIndexed { idx, item ->
                    legacyReason(item)?.let { out += LegacyItemRef(table.name, pool.name, "guaranteed", idx, item.type, it) }
                }
                pool.weightedItems.forEachIndexed { idx, item ->
                    legacyReason(item)?.let { out += LegacyItemRef(table.name, pool.name, "weighted", idx, item.type, it) }
                }
            }
        }
        return out
    }

    /** Returns true (with reason) if [item] is a legacy entry that needs re-adding. */
    fun isLegacy(item: LootItem): Boolean = legacyReason(item) != null

    private fun legacyReason(item: LootItem): String? {
        if (item.serializedItem != null) return null
        val mat = item.type
        val name = mat.name
        return when {
            mat == Material.ENCHANTED_BOOK &&
                item.enchantments.isNullOrEmpty() &&
                item.enchantmentRanges.isNullOrEmpty() &&
                item.randomEnchantmentPool.isNullOrEmpty() ->
                "enchanted book without enchantments"
            (name == "POTION" || name == "SPLASH_POTION" || name == "LINGERING_POTION" || name == "TIPPED_ARROW") &&
                item.potionType == null &&
                item.customEffectType.isNullOrBlank() ->
                "${name.lowercase().replace('_', ' ')} without potion type"
            mat == Material.GOAT_HORN && item.instrument.isNullOrBlank() ->
                "goat horn without instrument"
            else -> null
        }
    }

    /**
     * Parses a loot table from configuration.
     * Supports both legacy single-pool and new multi-pool formats.
     */
    private fun parseLootTable(name: String, section: ConfigurationSection): LootTable {
        // Check if this is a multi-pool format
        val poolsSection = section.getList("pools")

        if (poolsSection != null && poolsSection.isNotEmpty()) {
            // New multi-pool format
            val pools = mutableListOf<LootPool>()
            val maxPools = plugin.config.getInt("loot.max-pools-per-table", 5)

            poolsSection.take(maxPools).forEach { poolData ->
                if (poolData is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootPool(poolData as Map<String, Any>)?.let { pools.add(it) }
                }
            }

            return LootTable(name = name, pools = pools)
        } else {
            // Legacy single-pool format (backwards compatible)
            val (minRolls, maxRolls) = sanitizeRolls(section.getInt("min-rolls", 3), section.getInt("max-rolls", 5), name)

            // Parse guaranteed items
            val guaranteedItems = mutableListOf<LootItem>()
            section.getList("guaranteed-items")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootItem(item as Map<String, Any>)?.let { guaranteedItems.add(it) }
                }
            }

            // Parse weighted items
            val weightedItems = mutableListOf<LootItem>()
            section.getList("weighted-items")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootItem(item as Map<String, Any>)?.let { weightedItems.add(it) }
                }
            }

            // Parse command rewards (optional)
            val commandRewards = mutableListOf<CommandReward>()
            section.getList("command-rewards")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseCommandReward(item as Map<String, Any>)?.let { commandRewards.add(it) }
                }
            }

            // Parse economy rewards (optional, paid via Vault)
            val economyRewards = mutableListOf<EconomyReward>()
            section.getList("economy-rewards")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseEconomyReward(item as Map<String, Any>)?.let { economyRewards.add(it) }
                }
            }

            val rollMode = parseRollMode(section.getString("mode"), name)
            val maxItems = section.getInt("max-items", 0).coerceAtLeast(0)

            return LootTable(name, minRolls, maxRolls, guaranteedItems, weightedItems,
                commandRewards, economyRewards, rollMode, maxItems)
        }
    }

    /**
     * Resolves a pool/table `mode:` string to a [LootRollMode]. Unknown values fall
     * back to WEIGHTED with a warning so a typo doesn't silently switch behaviour.
     */
    private fun parseRollMode(raw: String?, context: String): com.esmpfun.bettertrialchambers.models.LootRollMode {
        if (raw != null && !com.esmpfun.bettertrialchambers.models.LootRollMode.isKnown(raw)) {
            plugin.logger.warning("loot.yml: $context has unknown mode '$raw', using 'weighted'. Valid: weighted, independent.")
        }
        return com.esmpfun.bettertrialchambers.models.LootRollMode.fromConfig(raw)
    }

    /**
     * Parses a loot pool from configuration.
     */
    private fun parseLootPool(data: Map<String, Any>): LootPool? {
        val poolName = data["name"] as? String ?: return null
        val (minRolls, maxRolls) = sanitizeRolls(
            (data["min-rolls"] as? Number)?.toInt() ?: 1,
            (data["max-rolls"] as? Number)?.toInt() ?: 1,
            "pool '$poolName'"
        )

        // Parse guaranteed items
        val guaranteedItems = mutableListOf<LootItem>()
        @Suppress("UNCHECKED_CAST")
        (data["guaranteed-items"] as? List<Map<String, Any>>)?.forEach { item ->
            parseLootItem(item)?.let { guaranteedItems.add(it) }
        }

        // Parse weighted items
        val weightedItems = mutableListOf<LootItem>()
        @Suppress("UNCHECKED_CAST")
        (data["weighted-items"] as? List<Map<String, Any>>)?.forEach { item ->
            parseLootItem(item)?.let { weightedItems.add(it) }
        }

        // Parse command rewards
        val commandRewards = mutableListOf<CommandReward>()
        @Suppress("UNCHECKED_CAST")
        (data["command-rewards"] as? List<Map<String, Any>>)?.forEach { item ->
            parseCommandReward(item)?.let { commandRewards.add(it) }
        }

        // Parse economy rewards (paid via Vault)
        val economyRewards = mutableListOf<EconomyReward>()
        @Suppress("UNCHECKED_CAST")
        (data["economy-rewards"] as? List<Map<String, Any>>)?.forEach { item ->
            parseEconomyReward(item)?.let { economyRewards.add(it) }
        }

        val rollMode = parseRollMode(data["mode"] as? String, "pool '$poolName'")
        val maxItems = ((data["max-items"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val chance = parsePoolChance(data["chance"], poolName)

        return LootPool(poolName, minRolls, maxRolls, guaranteedItems, weightedItems,
            commandRewards, economyRewards, rollMode, maxItems, chance)
    }

    /**
     * Reads `enchant-with-levels-min` / `-max`: enchant this item the way an
     * enchanting table would, at a random cost between those two levels.
     *
     * This is what vanilla's chamber rewards actually do for their bows,
     * crossbows, axes and chestplates, and it gives a whole believable set of
     * enchantments rather than a single one picked off a list. Both values are
     * required together, because one on its own has no meaning.
     */
    private fun parseEnchantWithLevels(data: Map<String, Any>, typeStr: String): Pair<Int, Int>? {
        val min = (data["enchant-with-levels-min"] as? Number)?.toInt()
        val max = (data["enchant-with-levels-max"] as? Number)?.toInt()
        if (min == null && max == null) return null
        if (min == null || max == null) {
            plugin.logger.warning(
                "loot.yml: '$typeStr' sets only one of enchant-with-levels-min / " +
                    "enchant-with-levels-max. Both are needed, so this has been ignored."
            )
            return null
        }
        val lo = min.coerceAtLeast(0)
        val hi = max.coerceAtLeast(0)
        return if (lo > hi) hi to lo else lo to hi
    }

    /**
     * Reads a pool's `chance`: how often the whole pool runs, as a number from
     * 0 to 1. Accepts a percentage too, so both `chance: 0.25` and `chance: 25`
     * mean a quarter of the time, because a server owner writing this by hand
     * could reasonably reach for either.
     */
    private fun parsePoolChance(raw: Any?, poolName: String): Double {
        val value = (raw as? Number)?.toDouble() ?: return 1.0
        val normalised = if (value > 1.0) value / 100.0 else value
        if (normalised < 0.0 || normalised > 1.0) {
            plugin.logger.warning(
                "loot.yml: pool '$poolName' has chance $value, which is outside 0 to 1 " +
                    "(or 0 to 100 as a percentage). Treating it as always running."
            )
            return 1.0
        }
        return normalised
    }

    /**
     * Sanitizes a min/max roll pair: negatives coerced to 0, min > max swapped with a
     * warning. Pre-1.7.1 a reversed pair threw inside Random.nextInt at generation time.
     */
    private fun sanitizeRolls(min: Int, max: Int, context: String): Pair<Int, Int> {
        var lo = min.coerceAtLeast(0)
        var hi = max.coerceAtLeast(0)
        if (lo > hi) {
            plugin.logger.warning("loot.yml: $context has min-rolls ($lo) > max-rolls ($hi); swapped.")
            val t = lo; lo = hi; hi = t
        }
        return lo to hi
    }

    /**
     * Parses a loot item from configuration.
     * Supports advanced features: potions, tipped arrows, enchantment ranges, variable durability.
     */
    private fun parseLootItem(data: Map<String, Any>): LootItem? {
        val typeStr = data["type"] as? String ?: return null

        // Check for common mistake: using type: COMMAND instead of command-rewards
        if (typeStr.equals("COMMAND", ignoreCase = true)) {
            plugin.logger.severe("═══════════════════════════════════════════════════════════════════")
            plugin.logger.severe("ERROR: 'type: COMMAND' is NOT valid!")
            plugin.logger.severe("Command rewards use a different format:")
            plugin.logger.severe("")
            plugin.logger.severe("WRONG:")
            plugin.logger.severe("  weighted-items:")
            plugin.logger.severe("    - type: COMMAND   (do not use this)")
            plugin.logger.severe("")
            plugin.logger.severe("CORRECT:")
            plugin.logger.severe("  command-rewards:")
            plugin.logger.severe("    - weight: 25.0")
            plugin.logger.severe("      commands:")
            plugin.logger.severe("        - \"eco give {player} 1000\"")
            plugin.logger.severe("      display-name: \"&6+1000 Coins\"")
            plugin.logger.severe("")
            plugin.logger.severe("See loot.yml for full examples!")
            plugin.logger.severe("═══════════════════════════════════════════════════════════════════")
            return null
        }

        // Vanilla / datapack loot-table passthrough, type: VANILLA_TABLE with table field
        val vanillaTable = if (typeStr.equals("VANILLA_TABLE", ignoreCase = true)) {
            val tableId = data["table"] as? String
            if (tableId.isNullOrBlank()) {
                plugin.logger.warning(
                    "type: VANILLA_TABLE requires a 'table:' field. Example:\n" +
                    "  - type: VANILLA_TABLE\n" +
                    "    table: \"minecraft:chests/trial_chambers/reward\"\n" +
                    "    weight: 5.0"
                )
                return null
            }
            if (org.bukkit.NamespacedKey.fromString(tableId.lowercase()) == null) {
                plugin.logger.warning("Invalid loot table key '$tableId', expected namespace:path (e.g. minecraft:chests/trial_chambers/reward)")
                return null
            }
            tableId.lowercase()
        } else null

        // Custom item plugin support, type: CUSTOM_ITEM with plugin + item-id fields
        val customItemPlugin = data["plugin"] as? String
        val customItemId = data["item-id"] as? String

        if (typeStr.equals("CUSTOM_ITEM", ignoreCase = true) && customItemPlugin == null) {
            plugin.logger.warning(
                "type: CUSTOM_ITEM requires 'plugin:' and 'item-id:' fields. Example:\n" +
                "  - type: CUSTOM_ITEM\n" +
                "    plugin: Nexo\n" +
                "    item-id: \"my_item\"\n" +
                "    weight: 5.0"
            )
            return null
        }

        val material = if (typeStr.equals("CUSTOM_ITEM", ignoreCase = true) || customItemPlugin != null ||
            vanillaTable != null
        ) {
            Material.AIR // sentinel, real item(s) resolved at generation time
        } else {
            try {
                Material.valueOf(typeStr.uppercase())
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid material: $typeStr (if this is a custom item, use 'type: CUSTOM_ITEM' with 'plugin:' and 'item-id:')")
                return null
            }
        }

        val customModelData = (data["custom-model-data"] as? Number)?.toInt()

        // v1.7.1 validation: a bad amount range used to throw inside Random.nextInt at
        // GENERATION time, killing the whole vault roll. Sanitize at parse with a warning.
        var amountMin = ((data["amount-min"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
        var amountMax = ((data["amount-max"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
        if (amountMin > amountMax) {
            plugin.logger.warning("loot.yml: '$typeStr' has amount-min ($amountMin) > amount-max ($amountMax); swapped.")
            val t = amountMin; amountMin = amountMax; amountMax = t
        }
        val weight = (data["weight"] as? Number)?.toDouble() ?: 1.0
        if (weight <= 0.0) {
            plugin.logger.warning("loot.yml: '$typeStr' has weight $weight, entries with weight <= 0 never drop.")
        }

        val name = data["name"] as? String
        @Suppress("UNCHECKED_CAST")
        val lore = data["lore"] as? List<String>

        // Parse fixed enchantments (legacy format: "SHARPNESS:5")
        val enchantments = mutableMapOf<Enchantment, Int>()
        @Suppress("UNCHECKED_CAST")
        (data["enchantments"] as? List<String>)?.forEach { enchStr ->
            val parts = enchStr.split(":")
            // A namespaced id ("minecraft:sharpness" / "somepack:blaze") also splits on ':',
            // treat the last segment as the level and everything before it as the id.
            if (parts.size >= 2) {
                val enchantment = resolveEnchantment(parts.dropLast(1).joinToString(":"), enchStr)
                val level = parts.last().toIntOrNull()
                if (level == null) {
                    plugin.logger.warning("loot.yml: enchantment entry '$enchStr' has a non-numeric level; skipped.")
                } else if (enchantment != null) {
                    enchantments[enchantment] = level
                }
            } else {
                plugin.logger.warning("loot.yml: enchantment entry '$enchStr' isn't NAME:level; skipped.")
            }
        }

        // Parse enchantment ranges (new format: "SHARPNESS:1:5" for level 1-5)
        val enchantmentRanges = mutableMapOf<Enchantment, com.esmpfun.bettertrialchambers.models.EnchantmentRange>()
        @Suppress("UNCHECKED_CAST")
        (data["enchantment-ranges"] as? List<String>)?.forEach { enchStr ->
            parseEnchantmentRange(enchStr)?.let { enchantmentRanges[it.enchantment] = it }
        }

        // Parse random enchantment pool (pick one random enchantment from this list)
        val randomEnchantmentPool = mutableListOf<com.esmpfun.bettertrialchambers.models.EnchantmentRange>()
        @Suppress("UNCHECKED_CAST")
        (data["random-enchantment-pool"] as? List<String>)?.forEach { enchStr ->
            parseEnchantmentRange(enchStr)?.let { randomEnchantmentPool.add(it) }
        }

        // Parse potion type for potions and tipped arrows
        val potionTypeStr = data["potion-type"] as? String
        val potionType = potionTypeStr?.let {
            try {
                org.bukkit.potion.PotionType.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid potion type: $it")
                null
            }
        }

        var potionLevel = (data["potion-level"] as? Number)?.toInt()
        // v1.7.1: random amplifier range, matching vanilla's set_ominous_bottle_amplifier
        // (real vault bottles are ONE entry with amplifier 0-1 / 2-4, rolled per drop).
        // Also works for regular potion amplifiers.
        var potionLevelMin = (data["potion-level-min"] as? Number)?.toInt()
        var potionLevelMax = (data["potion-level-max"] as? Number)?.toInt()
        if ((potionLevelMin == null) != (potionLevelMax == null)) {
            plugin.logger.warning("loot.yml: '$typeStr' sets only one of potion-level-min/potion-level-max, both are required; ignored.")
            potionLevelMin = null; potionLevelMax = null
        }
        if (potionLevelMin != null && potionLevelMax != null && potionLevelMin > potionLevelMax) {
            plugin.logger.warning("loot.yml: '$typeStr' has potion-level-min > potion-level-max; swapped.")
            val t = potionLevelMin; potionLevelMin = potionLevelMax; potionLevelMax = t
        }
        val customEffectType = data["custom-effect-type"] as? String
        val isOminousPotion = (data["ominous-potion"] as? Boolean) ?: false
        val effectDuration = (data["effect-duration"] as? Number)?.toInt()

        // Ominous bottles only hold Bad Omen at amplifier 0-4 (Paper's OminousBottleMeta
        // throws outside that range, pre-1.7.1 a potion-level: 5 killed the whole roll).
        val isOminousBottle = material == Material.OMINOUS_BOTTLE || (isOminousPotion && material == Material.POTION)
        if (isOminousBottle) {
            if (data["potion-type"] != null) {
                plugin.logger.warning("loot.yml: '$typeStr' is an ominous bottle, 'potion-type' is ignored (ominous bottles can only hold Bad Omen).")
            }
            fun clampAmp(v: Int, field: String): Int {
                if (v !in 0..4) plugin.logger.warning("loot.yml: ominous bottle $field $v is out of range 0-4 (Bad Omen I-V); clamped.")
                return v.coerceIn(0, 4)
            }
            potionLevel = potionLevel?.let { clampAmp(it, "potion-level") }
            potionLevelMin = potionLevelMin?.let { clampAmp(it, "potion-level-min") }
            potionLevelMax = potionLevelMax?.let { clampAmp(it, "potion-level-max") }
        }

        // Parse variable durability
        val enchantLevels = parseEnchantWithLevels(data, typeStr)
        val durabilityMin = (data["durability-min"] as? Number)?.toInt()
        val durabilityMax = (data["durability-max"] as? Number)?.toInt()

        // Parse goat horn instrument (8 variants: PONDER, SING, SEEK, FEEL, ADMIRE, CALL, YEARN, DREAM)
        val instrument = data["instrument"] as? String

        val enabled = (data["enabled"] as? Boolean) ?: true

        // Per-player redeem cap (v2.1.0). `redeemable: once|per-chamber|per-reset`.
        val redeemScope = com.esmpfun.bettertrialchambers.models.RedeemScope
            .fromConfig((data["redeemable"] as? String)?.replace('-', '_'))
        var redeemId = data["redeem-id"] as? String
        // A capped entry needs a stable identity to track claims. If the config
        // author wrote `redeemable:` by hand without an id, synthesize a stable one
        // from the material so hand-edited entries still work (GUI edits assign a UUID).
        if (redeemScope != com.esmpfun.bettertrialchambers.models.RedeemScope.PER_RESET && redeemId.isNullOrBlank()) {
            // Prefer a distinguishing identity: VANILLA_TABLE/CUSTOM_ITEM entries are all
            // material AIR, so keying on the material alone would make two hand-capped
            // passthrough entries share one claim. Fall back to the table/item id first.
            redeemId = "auto_${vanillaTable ?: customItemId ?: material.name}"
        }

        return LootItem(
            type = material,
            amountMin = amountMin,
            amountMax = amountMax,
            weight = weight,
            name = name,
            lore = lore,
            enchantments = enchantments.takeIf { it.isNotEmpty() },
            enchantmentRanges = enchantmentRanges.takeIf { it.isNotEmpty() },
            randomEnchantmentPool = randomEnchantmentPool.takeIf { it.isNotEmpty() },
            potionType = potionType,
            potionLevel = potionLevel,
            potionLevelMin = potionLevelMin,
            potionLevelMax = potionLevelMax,
            customEffectType = customEffectType,
            isOminousPotion = isOminousPotion,
            effectDuration = effectDuration,
            durabilityMin = durabilityMin,
            durabilityMax = durabilityMax,
            enchantWithLevelsMin = enchantLevels?.first,
            enchantWithLevelsMax = enchantLevels?.second,
            enchantWithLevelsTreasure = data["enchant-with-levels-treasure"] as? Boolean ?: false,
            instrument = instrument,
            customItemPlugin = customItemPlugin,
            customItemId = customItemId,
            customModelData = customModelData,
            serializedItem = data["serialized-item"] as? String,
            vanillaTable = vanillaTable,
            redeemScope = redeemScope,
            redeemId = redeemId,
            enabled = enabled
        )
    }

    /**
     * Resolves an enchantment by Bukkit-style name ("SHARPNESS") or namespaced key
     * ("minecraft:sharpness", "somepack:blaze"). Warns and returns null when unknown,
     * pre-1.7.1 an unknown name was skipped silently, so typos lost enchants invisibly.
     */
    private fun resolveEnchantment(name: String, sourceEntry: String): Enchantment? {
        val key = org.bukkit.NamespacedKey.fromString(name.lowercase())
            ?: org.bukkit.NamespacedKey.minecraft(name.lowercase())
        val enchantment = com.esmpfun.bettertrialchambers.utils.Registries.enchantment(key)
        if (enchantment == null) {
            plugin.logger.warning("loot.yml: unknown enchantment '$name' in '$sourceEntry'; skipped. Use the in-game id (e.g. SHARPNESS or minecraft:sharpness).")
        }
        return enchantment
    }

    /** Parses "NAME:min:max" (name may itself be namespaced) into an EnchantmentRange, warning on problems. */
    private fun parseEnchantmentRange(enchStr: String): com.esmpfun.bettertrialchambers.models.EnchantmentRange? {
        val parts = enchStr.split(":")
        if (parts.size < 3) {
            plugin.logger.warning("loot.yml: enchantment range '$enchStr' isn't NAME:minLevel:maxLevel; skipped.")
            return null
        }
        val enchantment = resolveEnchantment(parts.dropLast(2).joinToString(":"), enchStr) ?: return null
        var min = parts[parts.size - 2].toIntOrNull()
        var max = parts.last().toIntOrNull()
        if (min == null || max == null) {
            plugin.logger.warning("loot.yml: enchantment range '$enchStr' has non-numeric levels; skipped.")
            return null
        }
        if (min > max) {
            plugin.logger.warning("loot.yml: enchantment range '$enchStr' has min > max; swapped.")
            val t = min; min = max; max = t
        }
        return com.esmpfun.bettertrialchambers.models.EnchantmentRange(enchantment, min, max)
    }

    /**
     * Parses a command reward from configuration.
     */
    private fun parseCommandReward(data: Map<String, Any>): CommandReward? {
        val weight = (data["weight"] as? Number)?.toDouble() ?: return null
        @Suppress("UNCHECKED_CAST")
        val commands = data["commands"] as? List<String> ?: return null
        val displayName = data["display-name"] as? String ?: "Command Reward"

        return CommandReward(weight, commands, displayName)
    }

    /**
     * Parses an economy (money) reward. Accepts either a fixed `amount:` or a
     * `min:`/`max:` range; `weight` is a 0-100 percentage chance. Returns null
     * on missing/invalid fields so a typo skips one reward rather than the whole
     * table.
     */
    private fun parseEconomyReward(data: Map<String, Any>): EconomyReward? {
        val weight = (data["weight"] as? Number)?.toDouble() ?: return null
        val min = (data["min"] as? Number)?.toDouble()
            ?: (data["amount"] as? Number)?.toDouble() ?: return null
        val max = (data["max"] as? Number)?.toDouble() ?: min
        if (min < 0.0 || max < 0.0) {
            plugin.logger.warning("loot.yml: economy-reward has a negative amount; skipped.")
            return null
        }
        val displayName = data["display-name"] as? String ?: ""
        return EconomyReward(weight, min, maxOf(min, max), displayName)
    }

    /**
     * Generates loot from a loot table.
     * Supports both legacy single-pool and new multi-pool formats.
     *
     * @param tableName The loot table name
     * @param player The player receiving the loot (for placeholders)
     * @return List of generated items
     */
    fun generateLoot(tableName: String, player: Player): List<ItemStack> =
        generateLoot(tableName, player, null)

    fun generateLoot(tableName: String, player: Player, redeem: RedeemContext?): List<ItemStack> {
        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("==== LOOT GENERATION DEBUG ====")
            plugin.logger.info("Requested table: '$tableName'")
            plugin.logger.info("Available tables: ${lootTables.keys.joinToString(", ")}")
            plugin.logger.info("Table exists: ${lootTables.containsKey(tableName)}")
        }

        val lootTable = lootTables[tableName]
        if (lootTable == null) {
            plugin.logger.warning("Loot table not found: $tableName (available: ${lootTables.keys.joinToString(", ")})")
            return emptyList()
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Using loot table: ${lootTable.name}")
            plugin.logger.info("Pools: ${lootTable.getEffectivePools().size}")
        }

        val items = mutableListOf<ItemStack>()

        // Get all effective pools (handles both legacy and new format)
        val pools = lootTable.getEffectivePools()

        // Generate loot from each pool independently (like vanilla)
        pools.forEach { pool ->
            items.addAll(generateLootFromPool(pool, player, redeem))
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Generated ${items.size} items from table '$tableName'")
            plugin.logger.info("================================")
        }

        return items
    }

    /**
     * Generates loot from a single pool.
     */
    private fun generateLootFromPool(pool: LootPool, player: Player, redeem: RedeemContext?): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        // A pool can be set to run only some of the time. Vanilla's vaults use
        // this for their best item: a quarter of the time for a normal vault,
        // three quarters for an ominous one. A pool that does not run this
        // opening contributes nothing at all, including its guaranteed items
        // and its command and economy rewards.
        if (pool.chance < 1.0 && Random.nextDouble() >= pool.chance) return items

        // Add all guaranteed items (respect enabled flag + already-claimed redeem cap)
        pool.guaranteedItems.filter { it.enabled && redeem?.isBlocked(it) != true }.forEach { lootItem ->
            items.addAll(expandLootItem(lootItem, player))
            redeem?.claim(lootItem)
        }

        // INDEPENDENT mode: each enabled weighted item rolls its own 0-100% chance;
        // every item that passes drops, optionally capped by max-items. min/max-rolls
        // and the LUCK bonus don't apply here (there are no "draws").
        if (pool.rollMode == com.esmpfun.bettertrialchambers.models.LootRollMode.INDEPENDENT) {
            var winners = pool.weightedItems.filter {
                it.enabled && redeem?.isBlocked(it) != true && Random.nextDouble() * 100.0 < it.weight
            }
            if (pool.maxItems in 1 until winners.size) {
                winners = winners.shuffled().take(pool.maxItems)
            }
            winners.forEach { items.addAll(expandLootItem(it, player)); redeem?.claim(it) }

            // Command + economy rewards still fire (they're independent Bernoulli rolls
            // by design in both modes).
            pool.commandRewards.forEach { reward ->
                if (Random.nextDouble() * 100.0 < reward.weight) executeCommandReward(reward, player)
            }
            pool.economyRewards.forEach { reward ->
                if (Random.nextDouble() * 100.0 < reward.weight) applyEconomyReward(reward, player)
            }
            return items
        }

        // WEIGHTED mode (default) ---------------------------------------------------
        // Calculate number of rolls (with optional LUCK bonus)
        var rolls = Random.nextInt(pool.minRolls, pool.maxRolls + 1)

        // Apply LUCK effect/attribute if enabled
        if (plugin.config.getBoolean("loot.apply-luck-effect", false)) {
            var totalLuckBonus = 0

            // Check for LUCK potion effect (temporary from potions, beacons, etc.)
            val luckEffect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.LUCK)
            if (luckEffect != null) {
                // Each level of LUCK adds +1 bonus roll
                val effectBonus = luckEffect.amplifier + 1
                totalLuckBonus += effectBonus

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Player ${player.name} has LUCK effect level ${luckEffect.amplifier + 1} (+$effectBonus rolls)")
                }
            }

            // Check for LUCK attribute (permanent from items/equipment)
            // Try to get the LUCK attribute - may not exist in all versions
            val luckAttribute = try {
                player.getAttribute(org.bukkit.attribute.Attribute.LUCK)
            } catch (e: Exception) {
                null
            }
            if (luckAttribute != null) {
                val baseLuck = luckAttribute.baseValue
                val totalLuck = luckAttribute.value
                val attributeLuck = totalLuck - baseLuck // Bonus from items

                if (attributeLuck > 0) {
                    // Each full point of luck attribute adds +1 bonus roll
                    val attributeBonus = attributeLuck.toInt()
                    totalLuckBonus += attributeBonus

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("Player ${player.name} has +${attributeLuck} LUCK attribute from items (+$attributeBonus rolls)")
                    }
                }
            }

            if (totalLuckBonus > 0) {
                rolls += totalLuckBonus

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Total LUCK bonus for ${player.name} in pool '${pool.name}': +$totalLuckBonus rolls (total: $rolls)")
                }
            }
        }

        // Roll for weighted items (respect enabled flag). The eligible list is
        // recomputed per roll so a capped item claimed on an earlier roll (or already
        // claimed in the DB) drops out for the remaining rolls, its weight is then
        // shared by the other items instead of it appearing twice.
        repeat(rolls) {
            val eligible = pool.weightedItems.filter { it.enabled && redeem?.isBlocked(it) != true }
            val selectedItem = selectWeightedItem(eligible)
            if (selectedItem != null) {
                items.addAll(expandLootItem(selectedItem, player))
                redeem?.claim(selectedItem)
            }
        }

        // Process command rewards
        pool.commandRewards.forEach { reward ->
            if (Random.nextDouble() * 100.0 < reward.weight) {
                executeCommandReward(reward, player)
            }
        }

        // Process economy (money) rewards, paid via Vault
        pool.economyRewards.forEach { reward ->
            if (Random.nextDouble() * 100.0 < reward.weight) {
                applyEconomyReward(reward, player)
            }
        }

        return items
    }

    /**
     * Expands one rolled loot entry into its item stacks: a regular entry
     * yields exactly [createItemStack]'s single stack; a `VANILLA_TABLE`
     * entry yields every stack the referenced server loot table generates.
     *
     * Empty stacks are dropped here rather than passed on. [createItemStack]
     * answers with an empty stack when an entry cannot be built at all (its
     * stored item will not read back, or a custom-item plugin does not know the
     * id any more), and nothing downstream was checking. Handing an empty stack
     * to the server's item-drop call does not fail loudly: it spawns an item
     * entity holding nothing, which vanishes on the next tick. So the player
     * quietly received less loot than the table promised and only the console
     * said why. Filtering here covers every caller and the vanilla-table path
     * as well.
     */
    private fun expandLootItem(lootItem: LootItem, player: Player): List<ItemStack> {
        val tableId = lootItem.vanillaTable
            ?: return splitIntoStacks(createItemStack(lootItem, player))
        return rollVanillaTable(tableId, player).filterNot { it.isEmpty }
    }

    /**
     * Splits an amount larger than the item's stack size into normal stacks, so
     * "3 totems" hands out three totems rather than one, or one stack of three.
     */
    private fun splitIntoStacks(stack: ItemStack): List<ItemStack> {
        if (stack.isEmpty) return emptyList()
        val max = stack.maxStackSize.coerceAtLeast(1)
        if (stack.amount <= max) return listOf(stack)
        val stacks = ArrayList<ItemStack>()
        var left = stack.amount
        while (left > 0) {
            val take = minOf(left, max)
            stacks += stack.clone().also { it.amount = take }
            left -= take
        }
        return stacks
    }

    /**
     * Populates a vanilla / datapack loot table via the Bukkit LootTable API.
     *
     * `populateLoot` builds NMS loot params against the live world, so it must
     * run on the player's region thread. Loot generation is invoked from an
     * async coroutine (see VaultInteractListener), so off-thread callers hop
     * via the scheduler and wait on a bounded future; on-thread callers roll
     * directly. Unknown keys log once per roll and yield no items, the rest
     * of the pool still drops.
     */
    private fun rollVanillaTable(tableId: String, player: Player): List<ItemStack> {
        val key = org.bukkit.NamespacedKey.fromString(tableId) ?: run {
            plugin.logger.warning("[VanillaTable] Invalid key '$tableId'")
            return emptyList()
        }
        val table = plugin.server.getLootTable(key) ?: run {
            plugin.logger.warning("[VanillaTable] Unknown loot table '$tableId' (not registered on this server; check the datapack is loaded)")
            return emptyList()
        }
        val roll = {
            val context = org.bukkit.loot.LootContext.Builder(player.location).build()
            table.populateLoot(java.util.Random(), context).toList()
        }
        if (org.bukkit.Bukkit.isPrimaryThread()) return roll()

        val future = java.util.concurrent.CompletableFuture<List<ItemStack>>()
        plugin.scheduler.runAtEntity(player, Runnable {
            try {
                future.complete(roll())
            } catch (e: Exception) {
                plugin.logger.warning("[VanillaTable] Failed to roll '$tableId': ${e.message}")
                future.complete(emptyList())
            }
        })
        return try {
            future.get(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            plugin.logger.warning("[VanillaTable] Timed out rolling '$tableId'")
            emptyList()
        }
    }

    /**
     * Selects a random item based on weights.
     */
    private fun selectWeightedItem(items: List<LootItem>): LootItem? {
        // Weight <= 0 means "never drops", pre-1.7.1, an all-zero-weight pool made the
        // FIRST zero-weight entry drop every roll (0 * random = 0, first subtraction hit 0).
        val eligible = items.filter { it.weight > 0.0 }
        if (eligible.isEmpty()) return null

        val totalWeight = eligible.sumOf { it.weight }
        var random = Random.nextDouble() * totalWeight

        eligible.forEach { item ->
            random -= item.weight
            if (random <= 0) {
                return item
            }
        }

        return eligible.lastOrNull()
    }

    /**
     * Renders a loot name/lore line to a Component through the plugin's standard text
     * pipeline (MessageParser: `&` codes, `&#RRGGBB` hex, and MiniMessage tags), with
     * Minecraft's forced-italic for custom names disabled, pre-1.7.1 these went through
     * raw `Component.text` with `§` codes, which rendered but left names italic and
     * supported neither hex nor MiniMessage.
     */
    private fun lootText(text: String, player: Player): net.kyori.adventure.text.Component =
        com.esmpfun.bettertrialchambers.utils.MessageParser.parse(replacePlaceholders(text, player))
            .decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE
            )

    /**
     * The effect amplifier for this entry: rolled uniformly from
     * potion-level-min/max when set, else the fixed potion-level, else null.
     */
    private fun rolledAmplifier(lootItem: LootItem): Int? {
        val min = lootItem.potionLevelMin
        val max = lootItem.potionLevelMax
        if (min != null && max != null) return Random.nextInt(min, max + 1)
        return lootItem.potionLevel
    }

    /**
     * Puts an enchantment on a loot item, in the right place for that item.
     *
     * An enchanted book does not work like an enchanted sword. The sword *is*
     * enchanted; the book *stores* an enchantment for someone to move onto
     * something else at an anvil, and the game keeps those two in different
     * places on the item.
     *
     * Everything before this went through `addUnsafeEnchantment` for both,
     * which on a book wrote to the "this item is enchanted" slot instead of the
     * "this book holds an enchantment" slot. The result looked completely
     * normal in the tooltip and was useless at an anvil, which is the only
     * thing anybody wants an enchanted book for. Enchanted books are also one
     * of the most common trial chamber rewards, so this was worth getting right.
     */
    private fun applyEnchantment(itemStack: ItemStack, enchantment: Enchantment, level: Int) {
        val meta = itemStack.itemMeta
        if (meta is org.bukkit.inventory.meta.EnchantmentStorageMeta) {
            meta.addStoredEnchant(enchantment, level, true)
            itemStack.itemMeta = meta
        } else {
            itemStack.addUnsafeEnchantment(enchantment, level)
        }
    }

    /**
     * Creates an ItemStack from a LootItem.
     * Applies potions, enchantments, random enchantments, and variable durability.
     */
    private fun createItemStack(lootItem: LootItem, player: Player): ItemStack {
        val amount = Random.nextInt(lootItem.amountMin, lootItem.amountMax + 1)

        // Faithful path: an item captured through the editor is rebuilt verbatim
        // (Paper applies datafixers on read), preserving enchantments/potions/NBT.
        lootItem.serializedItem?.let { encoded ->
            val base = deserializeItem(encoded)
                ?: run {
                    plugin.logger.warning("Skipping a loot item with unreadable serialized data.")
                    return ItemStack(Material.AIR, 0)
                }
            return base.clone().also { it.amount = amount }
        }

        // Resolve custom plugin item (Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible)
        if (lootItem.customItemPlugin != null && lootItem.customItemId != null) {
            val resolved = resolveCustomItem(lootItem.customItemPlugin, lootItem.customItemId)
            if (resolved == null) {
                plugin.logger.warning("Skipping unresolvable custom item '${lootItem.customItemId}' (plugin: ${lootItem.customItemPlugin})")
                return ItemStack(Material.AIR, 0)
            }
            val itemStack = resolved.clone().also { it.amount = amount }
            // Apply any name/lore/enchantments specified on top of the custom item base
            itemStack.itemMeta = itemStack.itemMeta?.apply {
                lootItem.name?.let { displayName(lootText(it, player)) }
                lootItem.lore?.let { loreLines -> lore(loreLines.map { line -> lootText(line, player) }) }
            }
            lootItem.enchantments?.forEach { (ench, level) -> itemStack.addUnsafeEnchantment(ench, level) }
            lootItem.enchantmentRanges?.values?.forEach { range ->
                itemStack.addUnsafeEnchantment(range.enchantment, Random.nextInt(range.minLevel, range.maxLevel + 1))
            }
            if (!lootItem.randomEnchantmentPool.isNullOrEmpty()) {
                val r = lootItem.randomEnchantmentPool.random()
                itemStack.addUnsafeEnchantment(r.enchantment, Random.nextInt(r.minLevel, r.maxLevel + 1))
            }
            return itemStack
        }

        // Determine actual material type (handle ominous potions)
        val actualMaterial = if (lootItem.isOminousPotion && lootItem.type == Material.POTION) {
            Material.OMINOUS_BOTTLE
        } else {
            lootItem.type
        }

        val itemStack = ItemStack(actualMaterial, amount)

        if (plugin.config.getBoolean("debug.verbose-logging", false) && lootItem.isOminousPotion) {
            plugin.logger.info("Creating OMINOUS_BOTTLE: potionLevel=${lootItem.potionLevel}, customEffectType=${lootItem.customEffectType}, effectDuration=${lootItem.effectDuration}")
            plugin.logger.info("ItemMeta type: ${itemStack.itemMeta?.javaClass?.simpleName}, is PotionMeta: ${itemStack.itemMeta is org.bukkit.inventory.meta.PotionMeta}")
        }

        itemStack.itemMeta = itemStack.itemMeta?.apply {
            // Set custom name
            lootItem.name?.let { displayName(lootText(it, player)) }

            // Set lore
            lootItem.lore?.let { loreLines -> lore(loreLines.map { line -> lootText(line, player) }) }

            // Apply custom model data. Deliberately the deprecated Int setter: the
            // CustomModelDataComponent replacement isn't stable across every API level
            // we target, and resource packs keyed on integer CMD expect this form.
            lootItem.customModelData?.let { @Suppress("DEPRECATION") setCustomModelData(it) }

            // Handle OMINOUS_BOTTLE separately (uses OminousBottleMeta, not PotionMeta)
            if (this is org.bukkit.inventory.meta.OminousBottleMeta) {
                // Bad Omen amplifier: rolled from potion-level-min/max when set (matches
                // vanilla's random-range set_ominous_bottle_amplifier), else potion-level.
                // Coerced 0..4 defensively, Paper's setAmplifier throws outside that range.
                val amplifier = (rolledAmplifier(lootItem) ?: 0).coerceIn(0, 4)

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Setting ominous bottle amplifier: $amplifier (Bad Omen ${amplifier + 1})")
                }

                this.amplifier = amplifier
            }
            // Apply potion effects for POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW
            else if (this is org.bukkit.inventory.meta.PotionMeta) {
                // Handle custom effect types (e.g., BAD_OMEN for ominous bottles)
                if (lootItem.customEffectType != null) {
                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("Processing custom effect type: ${lootItem.customEffectType}, potionLevel from config: ${lootItem.potionLevel}")
                    }

                    val effectType = try {
                        com.esmpfun.bettertrialchambers.utils.Registries
                            .potionEffect(lootItem.customEffectType.lowercase())
                    } catch (_: Exception) {
                        null
                    }

                    if (effectType != null) {
                        val duration = lootItem.effectDuration ?: when (lootItem.type) {
                            Material.POTION -> 120000 // 100 minutes for Bad Omen (matches vanilla)
                            Material.SPLASH_POTION -> 2700 // 2:15 for splash
                            Material.LINGERING_POTION -> 900 // 45 seconds for lingering cloud
                            Material.TIPPED_ARROW -> 400 // 20 seconds for arrows
                            else -> 120000
                        }

                        val amplifier = rolledAmplifier(lootItem) ?: 0

                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("Adding custom effect: type=${lootItem.customEffectType}, duration=$duration ticks (${duration/20}s), amplifier=$amplifier (level ${amplifier + 1})")
                        }

                        addCustomEffect(
                            org.bukkit.potion.PotionEffect(
                                effectType,
                                duration,
                                amplifier,
                                false, // ambient
                                true,  // particles
                                true   // icon
                            ),
                            true // overwrite existing effects
                        )

                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("Applied custom effect ${lootItem.customEffectType} level ${amplifier + 1} to ${lootItem.type}")
                        }
                    } else {
                        plugin.logger.warning("Invalid custom effect type: ${lootItem.customEffectType}")
                    }
                } else if (lootItem.potionType != null) {
                    // Handle standard potion types
                    // Use custom effects when a level (fixed or ranged) or duration is
                    // specified, otherwise the plain base potion type is fully vanilla.
                    val amplifierOverride = rolledAmplifier(lootItem)
                    val useCustomEffect = amplifierOverride != null ||
                        (lootItem.effectDuration != null && lootItem.effectDuration > 0)

                    if (useCustomEffect) {
                        // v1.7.1: apply EVERY effect the potion type carries, TURTLE_MASTER
                        // has two (Slowness + Resistance); the old firstOrNull() dropped one.
                        val baseEffects = lootItem.potionType.potionEffects

                        if (baseEffects.isNotEmpty()) {
                            val amplifier = amplifierOverride ?: 0
                            for (baseEffect in baseEffects) {
                                // Explicit duration wins; otherwise scale this effect's own
                                // vanilla duration by the item form's multiplier.
                                val calculatedDuration = if (lootItem.effectDuration != null && lootItem.effectDuration > 0) {
                                    lootItem.effectDuration
                                } else {
                                    val rawDuration = baseEffect.duration
                                    val baseDuration = if (rawDuration > 0) rawDuration else 3600

                                    // Vanilla duration multipliers per item form. Splash potions
                                    // last as long as drinkable ones in modern Minecraft (the old
                                    // 0.75x here was the pre-1.9 rule); lingering = 1/4, arrow = 1/8.
                                    when (lootItem.type) {
                                        Material.POTION, Material.SPLASH_POTION -> baseDuration
                                        Material.LINGERING_POTION -> baseDuration / 4
                                        Material.TIPPED_ARROW -> baseDuration / 8
                                        else -> baseDuration
                                    }
                                }

                                // Always enforce minimum duration (fixes 00:00 duration bug)
                                // Vanilla tipped arrows are typically 100-400 ticks (5-20 seconds)
                                val minDuration = when (lootItem.type) {
                                    Material.TIPPED_ARROW -> 100  // 5 seconds minimum
                                    Material.LINGERING_POTION -> 200  // 10 seconds minimum
                                    else -> 600  // 30 seconds minimum for other potions
                                }
                                val duration = maxOf(calculatedDuration, minDuration)

                                addCustomEffect(
                                    org.bukkit.potion.PotionEffect(
                                        baseEffect.type,
                                        duration,
                                        amplifier,
                                        false,
                                        true,
                                        true
                                    ),
                                    true
                                )
                            }
                        } else {
                            // No effects (e.g., AWKWARD, MUNDANE, THICK potions)
                            // Fall back to base potion type, but log a warning if duration was specified
                            if (lootItem.effectDuration != null && lootItem.effectDuration > 0) {
                                plugin.logger.warning(
                                    "Potion type ${lootItem.potionType} has no effect - effect-duration will be ignored. " +
                                    "Use custom-effect-type for potions without standard effects."
                                )
                            }
                            basePotionType = lootItem.potionType
                        }
                    } else {
                        // No custom level or duration specified - use default base potion type
                        basePotionType = lootItem.potionType
                    }
                }
            }

            // Apply variable durability (for tools, armor, etc.)
            if (lootItem.durabilityMin != null && lootItem.durabilityMax != null) {
                if (this is org.bukkit.inventory.meta.Damageable) {
                    val maxDurability = lootItem.type.maxDurability
                    if (maxDurability > 0) {
                        val damageValue = Random.nextInt(lootItem.durabilityMin, lootItem.durabilityMax + 1)
                        damage = damageValue.coerceIn(0, maxDurability.toInt())
                    }
                }
            }

            // Apply goat horn instrument (8 variants)
            if (lootItem.instrument != null && lootItem.type == Material.GOAT_HORN) {
                if (this is org.bukkit.inventory.meta.MusicInstrumentMeta) {
                    // Build the full instrument name (e.g., PONDER -> PONDER_GOAT_HORN)
                    val instrumentName = if (lootItem.instrument.endsWith("_GOAT_HORN", ignoreCase = true)) {
                        lootItem.instrument.uppercase()
                    } else {
                        "${lootItem.instrument.uppercase()}_GOAT_HORN"
                    }

                    // Resolve via the registry (v1.7.1, replaces Field reflection)
                    val instrument = com.esmpfun.bettertrialchambers.utils.Registries
                        .instrument(org.bukkit.NamespacedKey.minecraft(instrumentName.lowercase()))
                    if (instrument != null) {
                        setInstrument(instrument)
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("Applied goat horn instrument: $instrumentName")
                        }
                    } else {
                        plugin.logger.warning("Invalid goat horn instrument: ${lootItem.instrument}. " +
                            "Valid options: PONDER, SING, SEEK, FEEL, ADMIRE, CALL, YEARN, DREAM")
                    }
                }
            }
        }

        // Add fixed enchantments (legacy format)
        lootItem.enchantments?.forEach { (enchantment, level) ->
            applyEnchantment(itemStack, enchantment, level)
        }

        // Add enchantment ranges (random level within range)
        lootItem.enchantmentRanges?.values?.forEach { range ->
            val randomLevel = Random.nextInt(range.minLevel, range.maxLevel + 1)
            applyEnchantment(itemStack, range.enchantment, randomLevel)
        }

        // Pick one random enchantment from pool
        if (!lootItem.randomEnchantmentPool.isNullOrEmpty()) {
            val randomEnch = lootItem.randomEnchantmentPool.random()
            val randomLevel = Random.nextInt(randomEnch.minLevel, randomEnch.maxLevel + 1)
            applyEnchantment(itemStack, randomEnch.enchantment, randomLevel)
        }

        // Enchant it the way an enchanting table would. Vanilla's chamber
        // rewards do this for their bows, crossbows, axes and chestplates, and
        // it produces a whole believable set of enchantments rather than a
        // single one. Done last, and on the stack rather than the meta, because
        // the server hands back a fresh item.
        val enchantMin = lootItem.enchantWithLevelsMin
        val enchantMax = lootItem.enchantWithLevelsMax
        if (enchantMin != null && enchantMax != null) {
            val levels = Random.nextInt(enchantMin, enchantMax + 1)
            runCatching {
                Bukkit.getItemFactory().enchantWithLevels(
                    itemStack,
                    levels,
                    lootItem.enchantWithLevelsTreasure,
                    java.util.Random(),
                )
            }.onSuccess { return it }
                .onFailure {
                    plugin.logger.warning(
                        "loot.yml: could not enchant '${lootItem.type}' at level $levels " +
                            "(${it.message}). The item still drops, just unenchanted."
                    )
                }
        }

        return itemStack
    }

    /**
     * Resolves a custom item from a supported plugin (Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible).
     * Uses reflection so no compile-time dependency on any of these plugins is needed.
     *
     * Note: MythicCrucible is an addon of MythicMobs and registers its items into the Mythic item manager,
     * so the gate check uses "MythicMobs" rather than "MythicCrucible".
     */
    private fun resolveCustomItem(pluginName: String, itemId: String): ItemStack? {
        val gatePluginName = when (pluginName.lowercase()) {
            "mythiccrucible", "crucible" -> "MythicMobs"
            else -> pluginName
        }
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled(gatePluginName)) {
            plugin.logger.warning("Custom item plugin '$gatePluginName' is not enabled; cannot resolve item '$itemId'")
            return null
        }
        return when (pluginName.lowercase()) {
            "nexo" -> resolveNexoItem(itemId)
            "itemsadder" -> resolveItemsAdderItem(itemId)
            "oraxen" -> resolveOraxenItem(itemId)
            "craftengine" -> resolveCraftEngineItem(itemId)
            "mythiccrucible", "crucible" -> resolveMythicCrucibleItem(itemId)
            else -> {
                plugin.logger.warning(
                    "Unsupported custom item plugin: '$pluginName'. " +
                    "Supported: Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible"
                )
                null
            }
        }
    }

    /** Resolves a Nexo item via NexoItems.itemFromId(id).build() */
    private fun resolveNexoItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("com.nexomc.nexo.api.NexoItems")
        val wrapper = cls.getMethod("itemFromId", String::class.java).invoke(null, itemId)
        if (wrapper == null) {
            plugin.logger.warning("Nexo item not found: '$itemId'")
            null
        } else {
            com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(wrapper, "build") as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve Nexo item '$itemId': ${e.message}")
        null
    }

    /** Resolves an ItemsAdder item via CustomStack.getInstance(id).itemStack */
    private fun resolveItemsAdderItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("dev.lone.itemsadder.api.CustomStack")
        val instance = cls.getMethod("getInstance", String::class.java).invoke(null, itemId)
        if (instance == null) {
            plugin.logger.warning("ItemsAdder item not found: '$itemId'")
            null
        } else {
            com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(instance, "getItemStack") as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve ItemsAdder item '$itemId': ${e.message}")
        null
    }

    /** Resolves an Oraxen item via OraxenItems.getItemById(id).build() */
    private fun resolveOraxenItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
        val builder = cls.getMethod("getItemById", String::class.java).invoke(null, itemId)
        if (builder == null) {
            plugin.logger.warning("Oraxen item not found: '$itemId'")
            null
        } else {
            com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(builder, "build") as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve Oraxen item '$itemId': ${e.message}")
        null
    }

    /**
     * Resolves a CraftEngine item via CraftEngineItems.byId(Key) -> buildItemStack().
     *
     * Notes on the CraftEngine API (verified against Xiao-MoMi/craft-engine main):
     * - `CraftEngineItems.byId` takes a `net.momirealms.craftengine.core.util.Key`, not a String.
     *   We build the Key via its public `Key.from(String)` factory (accepts `"namespace:value"` or bare
     *   value, defaulting to the `minecraft` namespace, CraftEngine's own items use the `craftengine`
     *   namespace, so pack items should be referenced as e.g. `"my_pack:item_name"`).
     * - `CustomItem<ItemStack>` exposes a no-arg `buildItemStack()` which returns the Bukkit ItemStack
     *   directly. Player-context overloads exist but take CraftEngine's own `Player` abstraction, not
     *   Bukkit's, intentionally skipped here; static vault loot doesn't need player-context placeholders.
     */
    private fun resolveCraftEngineItem(itemId: String): ItemStack? = try {
        val keyCls = Class.forName("net.momirealms.craftengine.core.util.Key")
        val key = keyCls.getMethod("from", String::class.java).invoke(null, itemId)
        val itemsCls = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
        val customItem = itemsCls.getMethod("byId", keyCls).invoke(null, key)
        if (customItem == null) {
            plugin.logger.warning("CraftEngine item not found: '$itemId'")
            null
        } else {
            com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(customItem, "buildItemStack") as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve CraftEngine item '$itemId': ${e.message}")
        null
    }

    /**
     * Resolves a MythicCrucible item via the MythicMobs API chain:
     *   MythicBukkit.inst().getItemManager().getItem(String) -> Optional<MythicItem>
     *     -> MythicItem.generateItemStack(int) -> AbstractItemStack (BukkitItemStack at runtime)
     *     -> BukkitItemStack.getItemStack() -> org.bukkit.inventory.ItemStack
     *        (build() on 4.x and early 5.x; both are tried)
     *
     * MythicCrucible is an addon of MythicMobs, Crucible items are registered into the Mythic item
     * manager, so we query through the MythicBukkit API rather than a Crucible-specific entry point.
     * Amount is always generated as 1; the outer caller scales to the configured amount range.
     */
    private fun resolveMythicCrucibleItem(itemId: String): ItemStack? = try {
        val mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
        val instance = mythicBukkitCls.getMethod("inst").invoke(null)
        val itemManager = com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(instance, "getItemManager")
            ?: return null
        val optional = com.esmpfun.bettertrialchambers.utils.Reflect
            .method(itemManager.javaClass, "getItem", String::class.java)
            ?.invoke(itemManager, itemId) as? java.util.Optional<*> ?: return null
        val mythicItem = optional.orElse(null)
        if (mythicItem == null) {
            plugin.logger.warning("MythicCrucible item not found: '$itemId' (is the item defined in a Mythic/Crucible item file?)")
            null
        } else {
            // generateItemStack(int) -> AbstractItemStack; on Bukkit that is a BukkitItemStack
            val abstractStack = com.esmpfun.bettertrialchambers.utils.Reflect
                .method(mythicItem.javaClass, "generateItemStack", Int::class.javaPrimitiveType!!)
                ?.invoke(mythicItem, 1)
            if (abstractStack == null) {
                plugin.logger.warning("MythicCrucible.generateItemStack returned null for '$itemId'")
                null
            } else {
                // Mythic renamed this: 4.x and early 5.x built the Bukkit stack with
                // build(), current 5.x (checked against 5.13.1) exposes getItemStack()
                // instead. Asking for both keeps every version working.
                (com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(abstractStack, "getItemStack")
                    ?: com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(abstractStack, "build")) as? ItemStack
            }
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve MythicCrucible item '$itemId': ${e.message}")
        null
    }

    /**
     * Executes command rewards.
     * Folia compatible: Uses global scheduler for console command execution.
     */
    private fun executeCommandReward(reward: CommandReward, player: Player) {
        reward.commands.forEach { command ->
            val processedCommand = replacePlaceholders(command, player)
            plugin.scheduler.runTask(Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
            })
        }

        // Notify player
        player.sendMessage(plugin.getMessageComponent("loot-received", "item" to reward.displayName))
    }

    /**
     * Pays an economy reward via Vault. Rolls the amount in `[min, max]`, then
     * deposits + notifies on the main thread (economy providers expect it). A
     * no-op with a debug warning when no Vault economy provider is installed,
     * so a missing provider never silently swallows other loot.
     */
    private fun applyEconomyReward(reward: EconomyReward, player: Player) {
        if (!com.esmpfun.bettertrialchambers.utils.VaultEconomyHook.isAvailable(plugin)) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("Economy reward configured but no Vault economy provider is available, skipping.")
            }
            return
        }
        val rolled = if (reward.maxAmount > reward.minAmount) {
            reward.minAmount + Random.nextDouble() * (reward.maxAmount - reward.minAmount)
        } else reward.minAmount
        val amount = Math.round(rolled * 100.0) / 100.0
        if (amount <= 0.0) return

        plugin.scheduler.runTask(Runnable {
            if (com.esmpfun.bettertrialchambers.utils.VaultEconomyHook.deposit(plugin, player, amount)) {
                player.sendMessage(plugin.getMessageComponent(
                    "loot-money-received",
                    "amount" to com.esmpfun.bettertrialchambers.utils.VaultEconomyHook.format(plugin, amount)
                ))
            }
        })
    }

    /**
     * Replaces placeholders in strings.
     */
    private fun replacePlaceholders(text: String, player: Player): String {
        return text
            .replace("{player}", player.name)
            .replace("{uuid}", player.uniqueId.toString())
    }

    /**
     * Gets all loaded loot table names.
     */
    fun getLootTableNames(): Set<String> = lootTables.keys


    /**
     * Names of tables in `loot.yml` that would not load this time round.
     *
     * They are not in memory, and saving rewrites the file from memory, so
     * without this they would be quietly deleted from disk the first time
     * anybody edited any loot in the menu. See [saveAllToFile].
     */
    private val tablesUnreadable = mutableSetOf<String>()

    fun saveAllToFile() {
        try {
            val file = File(plugin.dataFolder, "loot.yml")

            // Saving rewrites the whole file from what is currently loaded. Any
            // table that would not load is therefore not in what gets written,
            // and would be gone for good. Somebody with a typo in one table who
            // then edits a different one in the menu would lose the first
            // outright, with no warning and no way back.
            //
            // A copy is kept first so nothing is ever actually lost, and the
            // console says which tables are affected and where the copy is.
            if (tablesUnreadable.isNotEmpty() && file.exists()) {
                val backup = com.esmpfun.bettertrialchambers.utils.SaveVersionStamp.backUp(file)
                plugin.logger.warning("=".repeat(72))
                plugin.logger.warning(
                    "These loot tables could not be read when the server started, so they " +
                        "are not part of what is about to be saved: " + tablesUnreadable.joinToString(", ")
                )
                if (backup != null) {
                    plugin.logger.warning("A copy of loot.yml exactly as it was has been saved as ${backup.name}.")
                    plugin.logger.warning("Fix the problem the console reported earlier, then copy those tables back.")
                }
                plugin.logger.warning("=".repeat(72))
            }
            val config = org.bukkit.configuration.file.YamlConfiguration()

            // Record which Minecraft wrote this, so that if the server is ever
            // moved back to an older version the load side can spot it and keep
            // a copy of the file before anything goes wrong. See SaveVersionStamp.
            val stamp = com.esmpfun.bettertrialchambers.utils.SaveVersionStamp
            config.createSection(stamp.SECTION).apply {
                set("minecraft-version", stamp.currentMinecraftVersion())
                set("data-version", stamp.currentDataVersion())
            }

            val root = config.createSection("loot-tables")
            lootTables.values.forEach { table ->
                val sec = root.createSection(table.name)

                if (table.isLegacyFormat()) {
                    // Save as legacy single-pool format
                    sec.set("min-rolls", table.minRolls)
                    sec.set("max-rolls", table.maxRolls)
                    // Only written when non-default so hand-authored weighted tables stay clean.
                    if (table.rollMode != com.esmpfun.bettertrialchambers.models.LootRollMode.WEIGHTED) {
                        sec.set("mode", table.rollMode.name.lowercase())
                    }
                    if (table.maxItems > 0) sec.set("max-items", table.maxItems)

                    // guaranteed-items
                    val guaranteedList = table.guaranteedItems.map { li -> serializeLootItem(li) }
                    if (guaranteedList.isNotEmpty()) {
                        sec.set("guaranteed-items", guaranteedList)
                    }

                    // weighted-items
                    val weightedList = table.weightedItems.map { li -> serializeLootItem(li) }
                    sec.set("weighted-items", weightedList)

                    // command-rewards
                    if (table.commandRewards.isNotEmpty()) {
                        val rewards = table.commandRewards.map { r -> serializeCommandReward(r) }
                        sec.set("command-rewards", rewards)
                    }
                    // economy-rewards
                    if (table.economyRewards.isNotEmpty()) {
                        sec.set("economy-rewards", table.economyRewards.map { serializeEconomyReward(it) })
                    }
                } else {
                    // Save as new multi-pool format
                    val poolsList = table.pools.map { pool ->
                        val poolMap = mutableMapOf<String, Any>()
                        poolMap["name"] = pool.name
                        poolMap["min-rolls"] = pool.minRolls
                        poolMap["max-rolls"] = pool.maxRolls
                        // Only written when it is actually doing something, so
                        // hand-authored pools are not littered with `chance: 1.0`.
                        if (pool.chance < 1.0) poolMap["chance"] = pool.chance
                        if (pool.rollMode != com.esmpfun.bettertrialchambers.models.LootRollMode.WEIGHTED) {
                            poolMap["mode"] = pool.rollMode.name.lowercase()
                        }
                        if (pool.maxItems > 0) poolMap["max-items"] = pool.maxItems

                        if (pool.guaranteedItems.isNotEmpty()) {
                            poolMap["guaranteed-items"] = pool.guaranteedItems.map { serializeLootItem(it) }
                        }

                        if (pool.weightedItems.isNotEmpty()) {
                            poolMap["weighted-items"] = pool.weightedItems.map { serializeLootItem(it) }
                        }

                        if (pool.commandRewards.isNotEmpty()) {
                            poolMap["command-rewards"] = pool.commandRewards.map { serializeCommandReward(it) }
                        }

                        if (pool.economyRewards.isNotEmpty()) {
                            poolMap["economy-rewards"] = pool.economyRewards.map { serializeEconomyReward(it) }
                        }

                        poolMap
                    }
                    sec.set("pools", poolsList)
                }
            }
            config.save(file)
            plugin.logger.info("Saved loot tables to loot.yml (${lootTables.size} tables)")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save loot.yml: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Serializes a LootItem to a map for saving.
     */
    /** Encode an ItemStack to a base64 string for faithful loot storage. */
    fun serializeItem(item: ItemStack): String =
        java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes())

    /** Decode a base64 loot item back to an ItemStack (Paper datafixes on read), or null on failure. */
    fun deserializeItem(encoded: String): ItemStack? = try {
        ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(encoded))
    } catch (e: Exception) {
        plugin.logger.warning("Failed to deserialize a stored loot item: ${e.message}")
        null
    }

    /**
     * Serializes a LootItem back to its loot.yml map form. Must round-trip EVERY field
     * [parseLootItem] reads: `saveAllToFile` rewrites the whole file on any GUI loot edit,
     * so a field missing here is silently stripped from every hand-written entry (the
     * pre-1.7.1 version dropped potion/ominous/duration/durability/instrument/range fields
     * and wrote VANILLA_TABLE entries back as `type: AIR`, destroying them).
     */
    private fun serializeLootItem(li: LootItem): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        if (li.customItemPlugin != null) {
            map["type"] = "CUSTOM_ITEM"
            map["plugin"] = li.customItemPlugin
            li.customItemId?.let { map["item-id"] = it }
        } else if (li.vanillaTable != null) {
            map["type"] = "VANILLA_TABLE"
            map["table"] = li.vanillaTable
        } else {
            map["type"] = li.type.name
        }
        map["amount-min"] = li.amountMin
        map["amount-max"] = li.amountMax
        map["weight"] = li.weight
        li.name?.let { map["name"] = it }
        li.lore?.let { map["lore"] = it }
        li.customModelData?.let { map["custom-model-data"] = it }
        li.enchantments?.let { ench ->
            map["enchantments"] = ench.map { (k, v) -> "${k.key.key.uppercase()}:$v" }
        }
        li.enchantmentRanges?.let { ranges ->
            map["enchantment-ranges"] = ranges.values.map { "${it.enchantment.key.key.uppercase()}:${it.minLevel}:${it.maxLevel}" }
        }
        li.randomEnchantmentPool?.let { pool ->
            map["random-enchantment-pool"] = pool.map { "${it.enchantment.key.key.uppercase()}:${it.minLevel}:${it.maxLevel}" }
        }
        li.potionType?.let { map["potion-type"] = it.name }
        li.potionLevel?.let { map["potion-level"] = it }
        li.potionLevelMin?.let { map["potion-level-min"] = it }
        li.potionLevelMax?.let { map["potion-level-max"] = it }
        li.customEffectType?.let { map["custom-effect-type"] = it }
        if (li.isOminousPotion) map["ominous-potion"] = true
        li.effectDuration?.let { map["effect-duration"] = it }
        li.durabilityMin?.let { map["durability-min"] = it }
        li.durabilityMax?.let { map["durability-max"] = it }
        li.enchantWithLevelsMin?.let { map["enchant-with-levels-min"] = it }
        li.enchantWithLevelsMax?.let { map["enchant-with-levels-max"] = it }
        if (li.enchantWithLevelsTreasure) map["enchant-with-levels-treasure"] = true
        li.instrument?.let { map["instrument"] = it }
        li.serializedItem?.let { map["serialized-item"] = it }
        // Redeem cap, only written when non-default so untouched tables stay clean.
        if (li.redeemScope != com.esmpfun.bettertrialchambers.models.RedeemScope.PER_RESET) {
            map["redeemable"] = li.redeemScope.name.lowercase().replace('_', '-')
            li.redeemId?.let { map["redeem-id"] = it }
        }
        map["enabled"] = li.enabled
        return map
    }

    /**
     * Serializes a CommandReward to a map for saving.
     */
    private fun serializeCommandReward(r: CommandReward): Map<String, Any> {
        return mapOf(
            "weight" to r.weight,
            "commands" to r.commands,
            "display-name" to r.displayName
        )
    }

    private fun serializeEconomyReward(r: EconomyReward): Map<String, Any> {
        return mapOf(
            "weight" to r.weight,
            "min" to r.minAmount,
            "max" to r.maxAmount,
            "display-name" to r.displayName
        )
    }
}

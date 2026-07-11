package com.esmpfun.bettertrialchambers.models

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionType

/**
 * Represents an enchantment with a level range for randomization.
 */
data class EnchantmentRange(
    val enchantment: Enchantment,
    val minLevel: Int,
    val maxLevel: Int
)

/**
 * Represents a loot item configuration from loot.yml.
 * Supports advanced features like tipped arrows, potions with levels,
 * enchantment randomization, and variable durability.
 */
data class LootItem(
    val type: Material,
    val amountMin: Int,
    val amountMax: Int,
    val weight: Double,
    val name: String? = null,
    val lore: List<String>? = null,

    // Fixed enchantments (legacy format, still supported)
    val enchantments: Map<Enchantment, Int>? = null,

    // Enchantment randomization (new features)
    val enchantmentRanges: Map<Enchantment, EnchantmentRange>? = null, // e.g., SHARPNESS with level 1-5
    val randomEnchantmentPool: List<EnchantmentRange>? = null, // Pick one random enchantment from this pool

    // Potion/Tipped Arrow support
    val potionType: PotionType? = null, // For potions and tipped arrows
    val potionLevel: Int? = null, // Potion effect amplifier (0 = level I, 1 = level II, etc.)
    // Random amplifier range (v1.7.1) — rolled per generation when both are set; overrides
    // potionLevel. Matches vanilla's set_ominous_bottle_amplifier uniform range (e.g. the
    // real ominous vault bottle is one entry with amplifier 2-4, not three split entries).
    val potionLevelMin: Int? = null,
    val potionLevelMax: Int? = null,
    val customEffectType: String? = null, // For custom effects like BAD_OMEN (ominous bottles)
    val isOminousPotion: Boolean = false, // Legacy alias: POTION entries with this flag become OMINOUS_BOTTLE
    val effectDuration: Int? = null, // Custom effect duration in ticks (20 ticks = 1 second). If null, uses defaults.

    // Variable durability
    val durabilityMin: Int? = null, // Minimum durability (as damage value)
    val durabilityMax: Int? = null, // Maximum durability (as damage value)

    // Goat Horn instrument support (8 variants)
    val instrument: String? = null, // PONDER, SING, SEEK, FEEL, ADMIRE, CALL, YEARN, DREAM

    // Custom item plugin support (Nexo, ItemsAdder, Oraxen)
    // When set, 'type' is ignored and the item is resolved from the named plugin.
    val customItemPlugin: String? = null,  // "Nexo", "ItemsAdder", or "Oraxen"
    val customItemId: String? = null,      // plugin-specific item ID (e.g. "trial_chamber:legendary_sword")

    // Custom model data for vanilla items
    val customModelData: Int? = null,

    // Faithful full-item storage: base64 of ItemStack.serializeAsBytes(). When set,
    // the item is rebuilt from this verbatim (preserving enchantments, potion data,
    // custom names/lore, custom-model-data and any NBT incl. third-party enchant
    // tags) and the structured fields above are ignored except the amount range.
    val serializedItem: String? = null,

    // Vanilla / datapack loot-table passthrough (v1.5.7). When set, 'type' is
    // ignored and rolling this entry populates the referenced server loot table
    // (e.g. "minecraft:chests/trial_chambers/reward" or any datapack key) via
    // the Bukkit LootTable API — every stack the table generates is added.
    val vanillaTable: String? = null,

    val enabled: Boolean = true
)

/**
 * Represents a command-based reward.
 */
data class CommandReward(
    val weight: Double,
    val commands: List<String>,
    val displayName: String
)

/**
 * Represents an economy (money) reward paid through Vault (v1.5.12).
 *
 * @property weight     0-100 percentage chance this reward fires on a roll.
 * @property minAmount  Minimum payout (inclusive).
 * @property maxAmount  Maximum payout; the amount is rolled uniformly in
 *                      `[minAmount, maxAmount]` (equal to minAmount when not
 *                      greater).
 * @property displayName Optional label used in the reward message (falls back
 *                      to the formatted amount when blank).
 */
data class EconomyReward(
    val weight: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val displayName: String = ""
)

/**
 * Represents a loot pool within a loot table.
 * Each pool rolls independently (like vanilla's common/rare/unique).
 */
data class LootPool(
    val name: String,
    val minRolls: Int,
    val maxRolls: Int,
    val guaranteedItems: List<LootItem> = emptyList(),
    val weightedItems: List<LootItem> = emptyList(),
    val commandRewards: List<CommandReward> = emptyList(),
    val economyRewards: List<EconomyReward> = emptyList()
)

/**
 * Represents a loot table configuration.
 * Can contain multiple pools (like vanilla: common, rare, unique).
 *
 * For backwards compatibility, supports both:
 * - Legacy single-pool format (minRolls, maxRolls, guaranteedItems, weightedItems)
 * - New multi-pool format (pools list)
 */
data class LootTable(
    val name: String,
    // Legacy format (backwards compatible)
    val minRolls: Int = 3,
    val maxRolls: Int = 5,
    val guaranteedItems: List<LootItem> = emptyList(),
    val weightedItems: List<LootItem> = emptyList(),
    val commandRewards: List<CommandReward> = emptyList(),
    val economyRewards: List<EconomyReward> = emptyList(),
    // New multi-pool format
    val pools: List<LootPool> = emptyList()
) {
    /**
     * Returns true if this table uses the legacy single-pool format.
     */
    fun isLegacyFormat(): Boolean = pools.isEmpty()

    /**
     * Gets all effective pools (converts legacy format to single pool if needed).
     */
    fun getEffectivePools(): List<LootPool> {
        return if (isLegacyFormat()) {
            // Convert legacy format to single pool
            listOf(
                LootPool(
                    name = "main",
                    minRolls = minRolls,
                    maxRolls = maxRolls,
                    guaranteedItems = guaranteedItems,
                    weightedItems = weightedItems,
                    commandRewards = commandRewards,
                    economyRewards = economyRewards
                )
            )
        } else {
            pools
        }
    }
}

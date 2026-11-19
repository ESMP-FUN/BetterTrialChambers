package com.esmpfun.bettertrialchambers.models

/**
 * Represents a vault in a Trial Chamber.
 *
 * @property id Database ID
 * @property chamberId Chamber ID this vault belongs to
 * @property x X coordinate
 * @property y Y coordinate
 * @property z Z coordinate
 * @property type Vault type (Normal or Ominous)
 * @property lootTable Name of the loot table to use
 * @property blockType What the vault block really is, as last scanned; null for rows saved before this was recorded
 */
data class VaultData(
    val id: Int,
    val chamberId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val type: VaultType,
    val lootTable: String,
    val blockType: VaultType? = null,
) {
    /**
     * Every vault is stored once per type so each type keeps its own cooldowns. This is the
     * row that matches the block in the world, the one to count and list.
     */
    val matchesBlock: Boolean get() = blockType == null || blockType == type
}

package com.esmpfun.bettertrialchambers.models

/**
 * Represents the type of trial key.
 */
enum class KeyType {
    NORMAL,
    OMINOUS;

    fun toVaultType(): VaultType = when (this) {
        NORMAL -> VaultType.NORMAL
        OMINOUS -> VaultType.OMINOUS
    }
}

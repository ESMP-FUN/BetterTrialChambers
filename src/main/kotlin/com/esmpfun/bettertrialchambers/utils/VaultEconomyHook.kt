package com.esmpfun.bettertrialchambers.utils

import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin

/**
 * Optional Vault economy integration (v1.5.12). Lets loot tables pay out money
 * (`economy-rewards`) through whatever economy plugin is registered with Vault
 * (EssentialsX, CMI, etc.) — provider-agnostic via the Vault API.
 *
 * Every Vault/Economy class reference lives inside a method that is reached only
 * after the `Vault` plugin-presence guard, so this class loads fine on servers
 * without Vault (the API is a `compileOnly` dependency). The provider is
 * resolved fresh from the ServicesManager on each call (a cheap map lookup),
 * which also means it tolerates the economy plugin loading after TCP.
 */
object VaultEconomyHook {

    /** Whether Vault is installed AND an economy provider is registered + enabled. */
    fun isAvailable(plugin: Plugin): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) return false
        return provider(plugin)?.isEnabled == true
    }

    /**
     * Deposits [amount] into [player]'s balance. Returns true on a successful
     * transaction. Economy operations can touch the main thread / user data, so
     * call this on the main (or the player's region) thread.
     */
    fun deposit(plugin: Plugin, player: OfflinePlayer, amount: Double): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) return false
        val econ = provider(plugin) ?: return false
        return try {
            if (!econ.hasAccount(player)) econ.createPlayerAccount(player)
            econ.depositPlayer(player, amount).transactionSuccess()
        } catch (e: Throwable) {
            plugin.logger.warning("[Economy] Deposit of $amount to ${player.name} failed: ${e.message}")
            false
        }
    }

    /** Formats [amount] using the economy provider's currency formatting, with a plain fallback. */
    fun format(plugin: Plugin, amount: Double): String {
        if (plugin.server.pluginManager.getPlugin("Vault") != null) {
            runCatching { provider(plugin)?.format(amount) }.getOrNull()?.let { return it }
        }
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else "%.2f".format(amount)
    }

    private fun provider(plugin: Plugin): net.milkbowl.vault.economy.Economy? =
        try {
            plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
        } catch (_: Throwable) {
            null
        }
}

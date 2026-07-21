package com.esmpfun.bettertrialchambers.models

import com.esmpfun.bettertrialchambers.BetterTrialChambers

/**
 * How vaults inside registered chambers hand out their loot (`vaults.loot-mode`).
 *
 * Added in v2.0.8. Before that there was only the `vaults.per-player-loot`
 * on/off switch, whose name suggested it could be turned off to get shared
 * loot — it couldn't. Off simply handed vaults back to plain Minecraft, which
 * is still one open per player.
 */
enum class VaultLootMode {
    /** Everyone who comes along gets their own loot from the same vault. */
    PER_PLAYER,

    /** First player to open a vault claims it; it stays shut for everybody else until the chamber resets. */
    SHARED,

    /** The plugin does not touch vaults at all — plain Minecraft behaviour, custom loot tables ignored. */
    VANILLA;

    companion object {
        /**
         * Reads the configured mode, falling back to the old
         * `vaults.per-player-loot` switch for configs written before v2.0.8
         * (true → [PER_PLAYER], false → [VANILLA], which is what that setting
         * actually did).
         */
        fun resolve(plugin: BetterTrialChambers): VaultLootMode {
            val raw = plugin.config.getString("vaults.loot-mode")?.trim()
            if (!raw.isNullOrEmpty()) {
                entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }
                plugin.logger.warning(
                    "config.yml: vaults.loot-mode is set to '$raw', which isn't one of " +
                        "PER_PLAYER, SHARED or VANILLA. Using PER_PLAYER for now."
                )
                return PER_PLAYER
            }
            return if (plugin.config.getBoolean("vaults.per-player-loot", true)) PER_PLAYER else VANILLA
        }
    }
}

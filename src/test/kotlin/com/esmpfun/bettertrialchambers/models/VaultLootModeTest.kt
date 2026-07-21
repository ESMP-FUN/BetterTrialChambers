package com.esmpfun.bettertrialchambers.models

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import io.mockk.every
import io.mockk.mockk
import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.logging.Logger

/**
 * Tests for [VaultLootMode.resolve] — the `vaults.loot-mode` setting plus its
 * fallback to the pre-v2.0.8 `vaults.per-player-loot` switch. v2.0.8.
 */
class VaultLootModeTest {

    /**
     * @param rawMode what `vaults.loot-mode` holds (null = key absent)
     * @param legacy what the old `vaults.per-player-loot` switch holds
     */
    private fun pluginWith(rawMode: String?, legacy: Boolean = true): BetterTrialChambers {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.getString("vaults.loot-mode") } returns rawMode
        every { config.getBoolean("vaults.per-player-loot", true) } returns legacy

        val plugin = mockk<BetterTrialChambers>(relaxed = true)
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getLogger("VaultLootModeTest")
        return plugin
    }

    @Test
    fun `resolve reads each of the three modes`() {
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith("PER_PLAYER")))
        assertEquals(VaultLootMode.SHARED, VaultLootMode.resolve(pluginWith("SHARED")))
        assertEquals(VaultLootMode.VANILLA, VaultLootMode.resolve(pluginWith("VANILLA")))
    }

    @Test
    fun `resolve accepts any casing and surrounding whitespace`() {
        // Server owners hand-edit this; "shared" and " Shared " must both work.
        assertEquals(VaultLootMode.SHARED, VaultLootMode.resolve(pluginWith("shared")))
        assertEquals(VaultLootMode.SHARED, VaultLootMode.resolve(pluginWith("  Shared  ")))
        assertEquals(VaultLootMode.VANILLA, VaultLootMode.resolve(pluginWith("vanilla")))
    }

    @Test
    fun `a missing loot-mode falls back to the old per-player-loot switch`() {
        // Pre-v2.0.8 configs have no loot-mode line at all.
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith(null, legacy = true)))
        assertEquals(VaultLootMode.VANILLA, VaultLootMode.resolve(pluginWith(null, legacy = false)))
    }

    @Test
    fun `an empty loot-mode falls back to the old switch rather than erroring`() {
        assertEquals(VaultLootMode.VANILLA, VaultLootMode.resolve(pluginWith("", legacy = false)))
        assertEquals(VaultLootMode.VANILLA, VaultLootMode.resolve(pluginWith("   ", legacy = false)))
    }

    @Test
    fun `loot-mode wins over the old switch when both are present`() {
        // The old key is left in shipped config.yml; it must not override the new one.
        assertEquals(VaultLootMode.SHARED, VaultLootMode.resolve(pluginWith("SHARED", legacy = true)))
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith("PER_PLAYER", legacy = false)))
    }

    @Test
    fun `a typo'd loot-mode falls back to PER_PLAYER instead of disabling vaults`() {
        // Failing "safe" here means the plugin keeps managing vaults. Falling back to
        // VANILLA would silently hand every vault to Minecraft over a typo.
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith("PERPLAYER")))
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith("first-come-first-served")))
        assertEquals(VaultLootMode.PER_PLAYER, VaultLootMode.resolve(pluginWith("true")))
    }
}

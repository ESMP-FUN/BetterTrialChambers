package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.managers.SpawnerPresetManager
import com.esmpfun.bettertrialchambers.models.SpawnerPreset
import io.mockk.every
import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [PresetCooldownPolicy] — who wins between a preset's own
 * `target-cooldown-length` and the server-wide spawner cooldown. v2.0.8.
 *
 * This is the rule users hit as "the global cooldown setting is being ignored",
 * so every branch of the truth table is pinned here.
 */
class PresetCooldownPolicyTest {

    private val presetKey = NamespacedKey("trialchamberpro", SpawnerPresetManager.PRESET_ID_KEY_NAME)

    private fun preset(cooldown: Int?) = SpawnerPreset(
        id = "super_zombie",
        normalConfig = "test:basic_zombie",
        ominousConfig = null,
        requiredPlayerRange = 14,
        targetCooldownLength = cooldown,
        totalMobs = null,
        simultaneousMobs = null,
        totalMobsAddedPerPlayer = null,
        simultaneousMobsAddedPerPlayer = null,
        ticksBetweenSpawn = null,
        spawnRange = null,
        displayName = null,
        lore = emptyList(),
    )

    /**
     * @param taggedPresetId the `tcp:preset_id` tag on the block (null = not preset-placed)
     * @param known the preset that id resolves to (null = preset since deleted from the YAML)
     * @param globalWins the `reset.spawner-cooldown-overrides-presets` setting
     */
    private fun setup(
        taggedPresetId: String?,
        known: SpawnerPreset?,
        globalWins: Boolean = false,
    ): Pair<BetterTrialChambers, TileState> {
        val pdc = mockk<PersistentDataContainer>(relaxed = true)
        every { pdc.get(presetKey, PersistentDataType.STRING) } returns taggedPresetId

        val state = mockk<TileState>(relaxed = true)
        every { state.persistentDataContainer } returns pdc

        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.getBoolean("reset.spawner-cooldown-overrides-presets", false) } returns globalWins

        val presetManager = mockk<SpawnerPresetManager>(relaxed = true)
        every { presetManager.get(any()) } returns known

        val plugin = mockk<BetterTrialChambers>(relaxed = true)
        every { plugin.config } returns config
        every { plugin.spawnerPresetManager } returns presetManager
        return plugin to state
    }

    @Test
    fun `an ordinary spawner follows the global setting`() {
        val (plugin, state) = setup(taggedPresetId = null, known = null)
        assertFalse(PresetCooldownPolicy.keepsOwnCooldown(plugin, state))
    }

    @Test
    fun `a preset spawner that states its own time keeps it`() {
        val (plugin, state) = setup("super_zombie", preset(cooldown = 36000))
        assertTrue(PresetCooldownPolicy.keepsOwnCooldown(plugin, state))
    }

    @Test
    fun `a preset spawner that omits the time follows the global setting`() {
        // The v2.0.8 fix: before, an omitted value silently became 36000 and the
        // spawner was exempt anyway, so there was no way to opt into the global.
        val (plugin, state) = setup("super_zombie", preset(cooldown = null))
        assertFalse(PresetCooldownPolicy.keepsOwnCooldown(plugin, state))
    }

    @Test
    fun `spawner-cooldown-overrides-presets forces even a stated time onto the global`() {
        val (plugin, state) = setup("super_zombie", preset(cooldown = 36000), globalWins = true)
        assertFalse(PresetCooldownPolicy.keepsOwnCooldown(plugin, state))
    }

    @Test
    fun `a spawner whose preset no longer exists follows the global setting`() {
        // Owner deleted the preset from spawner_presets.yml but the block is still
        // tagged. Falling back to the global beats leaving it on a time nobody can see.
        val (plugin, state) = setup("deleted_preset", known = null)
        assertFalse(PresetCooldownPolicy.keepsOwnCooldown(plugin, state))
    }

    @Test
    fun `readPresetId returns the tag when present and null otherwise`() {
        val (_, tagged) = setup("super_zombie", preset(36000))
        assertEquals("super_zombie", PresetCooldownPolicy.readPresetId(tagged))

        val (_, plain) = setup(null, null)
        assertNull(PresetCooldownPolicy.readPresetId(plain))
    }
}

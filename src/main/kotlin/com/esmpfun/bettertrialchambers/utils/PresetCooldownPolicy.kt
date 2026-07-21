package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.managers.SpawnerPresetManager
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.persistence.PersistentDataType

/**
 * Decides whether a trial spawner keeps the cooldown time written in its own
 * preset, or follows the server-wide / per-chamber cooldown setting instead.
 *
 * The rule (v2.0.8):
 *  - A spawner placed from a `spawner_presets.yml` preset that DECLARES
 *    `target-cooldown-length` keeps that time. Writing a number into a preset
 *    is taken as "I mean this number".
 *  - A preset that OMITS `target-cooldown-length` follows the global /
 *    per-chamber setting like any ordinary spawner. This is the recommended
 *    way to write presets now.
 *  - `reset.spawner-cooldown-overrides-presets: true` forces every spawner —
 *    presets included — onto the global setting.
 *
 * Before v2.0.8 ANY preset-sourced spawner was exempt and `target-cooldown-length`
 * silently defaulted to 36000 ticks, so there was no way to opt a preset into the
 * global setting. That was the behaviour users reported as "the global setting is
 * being ignored".
 */
object PresetCooldownPolicy {

    /**
     * Reads the `tcp:preset_id` tag a preset-placed spawner carries, or null
     * when the block is not preset-sourced.
     */
    fun readPresetId(state: TileState): String? {
        val key = NamespacedKey("trialchamberpro", SpawnerPresetManager.PRESET_ID_KEY_NAME)
        return state.persistentDataContainer.get(key, PersistentDataType.STRING)
    }

    /**
     * True when [state]'s own preset cooldown must be left alone — i.e. the
     * caller should NOT write the global / per-chamber cooldown onto it.
     */
    fun keepsOwnCooldown(plugin: BetterTrialChambers, state: TileState): Boolean {
        if (plugin.config.getBoolean("reset.spawner-cooldown-overrides-presets", false)) return false
        val presetId = readPresetId(state) ?: return false
        val preset = plugin.spawnerPresetManager.get(presetId) ?: return false
        return preset.targetCooldownLength != null
    }
}

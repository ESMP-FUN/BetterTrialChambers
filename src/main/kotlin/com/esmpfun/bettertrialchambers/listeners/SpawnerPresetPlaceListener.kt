package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.managers.SpawnerPresetManager
import com.esmpfun.bettertrialchambers.models.SpawnerPreset
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.block.TrialSpawner
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Copies the `tcp:preset_id` PDC tag from a placed trial-spawner item onto
 * the resulting block's [TileState]. Lets downstream code (notably the
 * premium [com.esmpfun.bettertrialchambers.api.WildSpawnerResolver])
 * identify which TCP preset a placed spawner came from, even after the
 * source ItemStack is gone.
 *
 * Also applies the preset's config-scoped overrides (`total-mobs`,
 * `simultaneous-mobs`, per-player scaling, `ticks-between-spawn`,
 * `spawn-range`) to the placed block. Vanilla only reads these fields from
 * INSIDE `normal_config`/`ominous_config`, and a preset's config is a
 * datapack *reference* string that can't carry per-field overrides — so the
 * item NBT cannot express them. Instead they're written here through Paper's
 * [org.bukkit.spawner.TrialSpawnerConfiguration] on top of the resolved
 * datapack config; `state.update()` bakes the merged config into the block.
 *
 * Only acts on `Material.TRIAL_SPAWNER` placements where the item carries
 * the tag. Vanilla `/give minecraft:trial_spawner` items have no tag and
 * are ignored. The tag survives chamber resets / chunk unloads / world
 * reloads via Minecraft's standard TileEntity persistence.
 *
 * Added in v1.4.0.
 */
class SpawnerPresetPlaceListener(private val plugin: BetterTrialChambers) : Listener {

    private val presetIdKey: NamespacedKey =
        NamespacedKey("trialchamberpro", SpawnerPresetManager.PRESET_ID_KEY_NAME)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event.blockPlaced.type != Material.TRIAL_SPAWNER) return

        val item = event.itemInHand
        val itemMeta = item.itemMeta ?: return
        val presetId = itemMeta.persistentDataContainer
            .get(presetIdKey, PersistentDataType.STRING)
            ?: return  // not a preset-sourced spawner, leave alone

        val state = event.blockPlaced.state as? TileState ?: return
        state.persistentDataContainer.set(presetIdKey, PersistentDataType.STRING, presetId)

        // Preset lookup by id: tolerates presets edited/removed since the item
        // was handed out (overrides are simply skipped then). Items given out
        // before this fix carry the tag too, so they heal on placement.
        val preset = plugin.spawnerPresetManager.get(presetId)
        if (preset != null && state is TrialSpawner) {
            applyConfigOverrides(state, preset)
        }
        state.update()

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            val loc = event.blockPlaced.location
            plugin.logger.info(
                "[SpawnerPreset] Tagged placed trial spawner at " +
                    "${loc.blockX},${loc.blockY},${loc.blockZ} with preset_id='$presetId'"
            )
        }
    }

    /**
     * Writes the preset's optional config-scoped fields into both the normal
     * and the ominous configuration of the placed spawner. Fields the preset
     * leaves unset keep whatever the (resolved) datapack config says.
     *
     * Bukkit API ↔ vanilla NBT mapping (verified against CraftBukkit's
     * `CraftTrialSpawnerConfiguration`):
     *  - baseSpawnsBeforeCooldown      ↔ total_mobs
     *  - baseSimultaneousEntities      ↔ simultaneous_mobs
     *  - additionalSpawnsBeforeCooldown↔ total_mobs_added_per_player
     *  - additionalSimultaneousEntities↔ simultaneous_mobs_added_per_player
     *  - delay                         ↔ ticks_between_spawn
     *  - spawnRange                    ↔ spawn_range
     */
    private fun applyConfigOverrides(spawner: TrialSpawner, preset: SpawnerPreset) {
        listOf(spawner.normalConfiguration, spawner.ominousConfiguration).forEach { cfg ->
            preset.totalMobs?.let { cfg.baseSpawnsBeforeCooldown = it.toFloat() }
            preset.simultaneousMobs?.let { cfg.baseSimultaneousEntities = it.toFloat() }
            preset.totalMobsAddedPerPlayer?.let { cfg.additionalSpawnsBeforeCooldown = it }
            preset.simultaneousMobsAddedPerPlayer?.let { cfg.additionalSimultaneousEntities = it }
            preset.ticksBetweenSpawn?.let { cfg.delay = it }
            preset.spawnRange?.let { cfg.spawnRange = it }
        }
    }
}

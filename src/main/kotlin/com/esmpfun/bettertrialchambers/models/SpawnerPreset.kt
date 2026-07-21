package com.esmpfun.bettertrialchambers.models

/**
 * A named template for a `minecraft:trial_spawner` item with preconfigured
 * `block_entity_data`. Loaded from `spawner_presets.yml` and handed out via
 * `/trial give <preset>`.
 *
 * The `normalConfig` / `ominousConfig` strings are *resource locations* that
 * point at a datapack-defined trial spawner config (e.g.
 * `namespace:basic_zombie`). The plugin doesn't validate that the
 * datapack exists — that's the server owner's responsibility — but the field
 * IS quoted as a string in the produced NBT so an inline-compound form will
 * not parse correctly here. Keep configs in datapacks.
 *
 * Introduced in v1.3.1.
 *
 * The optional config-scoped fields ([totalMobs], [simultaneousMobs],
 * [totalMobsAddedPerPlayer], [simultaneousMobsAddedPerPlayer],
 * [ticksBetweenSpawn], [spawnRange]) override the referenced datapack
 * config's values. Vanilla only reads these from inside the configuration
 * compound (not the block-entity top level), so they are NOT baked into the
 * item NBT — `SpawnerPresetPlaceListener` applies them to the block at place
 * time via Paper's `TrialSpawnerConfiguration` API.
 *
 * Note: the preset can ONLY produce `Material.TRIAL_SPAWNER` items by design;
 * there is no `material` field in the YAML schema. Other block types (vaults,
 * etc.) are out of scope for the free tier — they belong to a separate
 * "vault preset" / custom-key system in the planned premium module.
 */
data class SpawnerPreset(
    val id: String,
    val normalConfig: String?,
    val ominousConfig: String?,
    val requiredPlayerRange: Int,
    /**
     * How long this spawner waits before it can be used again, in ticks
     * (20 ticks = 1 second, so 36000 = 30 minutes).
     *
     * `null` means the preset did not set a time, in which case the spawner
     * follows the server-wide `reset.spawner-cooldown-minutes` (or the chamber's
     * own override) like any ordinary spawner. When a number IS set here it wins
     * over those settings, unless `reset.spawner-cooldown-overrides-presets` is
     * turned on. See [com.esmpfun.bettertrialchambers.utils.PresetCooldownPolicy].
     *
     * Optional since v2.0.8; before that it silently defaulted to 36000.
     */
    val targetCooldownLength: Int?,
    val totalMobs: Int?,
    val simultaneousMobs: Int?,
    val totalMobsAddedPerPlayer: Float?,
    val simultaneousMobsAddedPerPlayer: Float?,
    val ticksBetweenSpawn: Int?,
    val spawnRange: Int?,
    val displayName: String?,
    val lore: List<String>
)

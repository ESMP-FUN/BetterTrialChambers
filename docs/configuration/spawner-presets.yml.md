# spawner\_presets.yml

Define named `minecraft:trial_spawner` templates and hand them out with `/trial give <preset>`, producing a real, placeable trial-spawner block with a custom mob configuration baked in.

<div data-gb-custom-block data-tag="hint" data-style="info">

Use this when you define your own trial-spawner mob pools in a datapack and want a one-command way to deploy preconfigured spawners, instead of pasting a long `/give minecraft:trial_spawner[block_entity_data={...}]` string each time.

</div>

<div data-gb-custom-block data-tag="hint" data-style="warning">

Every preset always produces a `TRIAL_SPAWNER`. There is no `material:` field. Custom keys and vault crate items belong to the [Vault Crates](https://esmp.fun/) premium module, not this file.

</div>

---

## How to use it

1. Define the mob configurations in a Minecraft datapack. See the [Trial Spawner wiki](https://minecraft.wiki/w/Trial_Spawner#Spawner_configuration) for the JSON format. The plugin does not generate the datapack for you.
2. List named presets in `spawner_presets.yml`, each pointing at one of those datapack configs.
3. Run `/trial reload`.
4. Run `/trial give <preset>` to hand out the spawner item. Placing it produces a working spawner with the configured pools, cooldown, and player range.

---

## File structure

```yaml
presets:
  super_zombie:
    normal-config: "namespace:basic_zombie"
    ominous-config: "namespace:basic_zombie_o"
    required-player-range: 14
    target-cooldown-length: 36000
    display-name: "&6Super Zombie Spawner"
    lore:
      - "&7A custom trial spawner."
      - "&7Place to deploy."

  boss_arena:
    normal-config: "namespace:boss_pool"
    required-player-range: 24
    target-cooldown-length: 72000
    total-mobs: 12
    simultaneous-mobs: 4
    spawn-range: 6
    display-name: "&c&lBoss Arena Spawner"
```

Set at least one of `normal-config` or `ominous-config`, or the preset is skipped at load with a warning.

---

## Field reference

| Field | Type | Default | Meaning |
|---|---|---|---|
| `normal-config` | string | none | Datapack resource location for the spawner config used outside ominous mode. |
| `ominous-config` | string | none | Datapack resource location for the spawner config used during ominous mode. |
| `required-player-range` | int | `14` | How close a player must be, in blocks, for the spawner to activate. |
| `target-cooldown-length` | int | server-wide setting | Optional. How long the spawner rests after a wave is beaten, in ticks. 20 ticks is 1 second, so `36000` is 30 minutes. Leave it out to follow the server-wide setting instead. |
| `total-mobs` | int | datapack value | Total mobs spawned across the wave. Overrides the datapack. |
| `simultaneous-mobs` | int | datapack value | Most live mobs at once. Overrides the datapack. |
| `total-mobs-added-per-player` | number | datapack value | Extra total mobs per additional participating player. |
| `simultaneous-mobs-added-per-player` | number | datapack value | Extra concurrent cap per additional player. |
| `ticks-between-spawn` | int | datapack value | Tick delay between individual spawns. |
| `spawn-range` | int | datapack value | Radius in blocks around the spawner where mobs can appear. |
| `display-name` | string | none | Item name in the inventory. Supports `&` colour codes. |
| `lore` | list of strings | empty | Item lore lines. Supports `&` colour codes. |

`normal-config` and `ominous-config` must point at a config defined in a datapack on the server. Inline NBT is not supported. If the datapack is missing when a player places the spawner, the error shows in the console at activation time, not at `/trial give`.

<div data-gb-custom-block data-tag="hint" data-style="info">

The six datapack-override fields (`total-mobs` through `spawn-range`) are applied to the spawner block when it is placed. After editing them, run `/trial reload`, then break and re-place the spawner. Already-placed blocks keep their old values; a `/trial give` item obtained after the reload carries the new ones.

**Rest times, this file vs `config.yml`:**

* If a preset sets `target-cooldown-length`, its spawners use that value and ignore the server-wide `reset.spawner-cooldown-minutes` / `reset.wild-spawner-cooldown-minutes`.
* If a preset leaves the line out, its spawners follow the server-wide setting. This is usually easier: set the rest time once in `config.yml`.
* To force every spawner, presets included, onto the server-wide setting, set `reset.spawner-cooldown-overrides-presets: true` in `config.yml`.

If the server-wide setting looks ignored, check your presets for a `target-cooldown-length` line. `36000` is easy to miss: it is 30 minutes, the same as vanilla, so a preset and plain vanilla can look identical.

</div>

---

## The command

```
/trial give <preset> [player] [amount]
```

| Argument | Required? | Default |
|---|---|---|
| `<preset>` | yes | none |
| `[player]` | required only if the console runs the command | the sender |
| `[amount]` | no | `1` |

Permission: `btc.give` (default: op), included in `btc.admin.*`.

```
/trial give super_zombie                    # gives yourself 1 spawner
/trial give super_zombie Notch              # gives Notch 1 spawner
/trial give boss_arena Notch 5              # gives Notch 5 spawners
```

If the target's inventory is full, the overflow drops at their feet.

---

## Reloading

After editing `spawner_presets.yml`, run `/trial reload`. The preset list is swapped in atomically; `/trial give` calls already running are not affected.

Preset ids are case-insensitive in lookups. Tab completion after `/trial give` lists every loaded preset id.

---

## Limits

### Custom Mob Providers do not apply to preset spawners in the open world

[Custom Mob Providers](custom-mobs.md) only intercept trial-spawner spawns inside a registered chamber. A spawner placed from `/trial give` in the open world is a standalone block, so a datapack config always spawns vanilla entity types (heavily customizable through NBT, but always vanilla ids).

To get custom-plugin mobs from a preset spawner, place it inside a registered chamber and set that chamber's provider with `/trial mobs <chamber> provider <id>`. The chamber's provider then intercepts the preset spawner's waves.

<div data-gb-custom-block data-tag="hint" data-style="info">

To deploy placeable trial spawners that work with custom-plugin mobs anywhere on a survival map, use the [Wild Spawners](https://esmp.fun/) premium add-on.

</div>

---

## Troubleshooting

**Placed a preset spawner outside a chamber and can't mine it back.** Use a Silk Touch tool. Without Silk Touch the break is blocked and a hint appears. Mining without any tool requirement is a [Wild Spawners](https://esmp.fun/) feature.

**"Unknown preset" from `/trial give`.** The preset id did not load. Check the server log on startup or after `/trial reload`; parse failures and missing-config skips both log a warning.

**Spawner places fine but spawns nothing.** The datapack named by `normal-config` / `ominous-config` is not installed, or the resource location is mistyped. Check the console when a player approaches the spawner.

**"Failed to build item for preset".** The text built from the preset failed Paper's item parser, usually a stray quote or backslash in `normal-config` / `ominous-config`. Use plain `namespace:config_id` strings.

**File not appearing in the plugin folder.** `spawner_presets.yml` is created on first load. Make sure you ran the server at least once on a version that includes this feature, and check write permissions on `plugins/BetterTrialChambers/`.

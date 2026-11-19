# Settings the tour doesn't ask about

The tour and the admin menu cover what most servers change. These are the
next most asked-for settings. Each one is in
`plugins/BetterTrialChambers/config.yml`. Change the line, save the file, and
run `/trial reload`.

The lines below only show the part you change. Find the same name in your
file, under the heading it is under here.

## Let players open a vault again before the reset

Out of the box, a player opens each vault once, and it opens again for them
when the chamber resets. To let them open it again after a while instead:

```yaml
vaults:
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 24
```

That is six hours for normal vaults, a day for ominous ones. `0` means "wait
for the reset".

Or let them pay for it: with `reopen-cost-keys: 2` a player holding two keys
can open a used vault straight away.

## Only reset chambers that were used

```yaml
global:
  reset-after-first-loot: true
```

A chamber's timer only starts once someone opens a vault or chest in it. A
chamber nobody has touched never resets, which saves your server a lot of
work on big maps.

## Change the reset warnings

```yaml
global:
  reset-warning-times: [600, 60, 10]
```

The seconds before a reset when players inside are warned. This one warns at
ten minutes, one minute and ten seconds.

## Send players somewhere else on reset

```yaml
global:
  teleport-location: WORLD_SPAWN
```

`EXIT_POINT` uses the chamber's exit point (the default), `OUTSIDE_BOUNDS` a
safe spot just outside the walls, `WORLD_SPAWN` the world's spawn.

## Announce cleared chambers

```yaml
global:
  broadcast-chamber-cleared: true
```

When a group beats every spawner in a chamber in one run, the whole server
reads "Emerald Depths has been cleared by Steve, Alex!".

## Tell players when they walk in

```yaml
messages:
  chamber-entry-message: true
  chamber-exit-message: true
```

Players get "Entered chamber: Emerald Depths" as they walk in, and
"Left chamber" as they leave.

## Show a tick or a cross over the vault

```yaml
vaults:
  feedback:
    mode: HOLOGRAM
```

Instead of a line in chat, the player sees a green tick or a red cross float
over the vault for a moment. Only they can see it.

## Keep the plugin out of a world

```yaml
global:
  excluded-worlds:
    - creative
```

No chambers are found or registered in that world, and its spawners are left
to vanilla.

## Run several servers on one set of chambers

Switch `database.type` from `SQLITE` to `MYSQL` and fill in your MySQL details
under it. Every server pointed at the same database shares its chambers,
cooldowns and stats. Most servers never need this.

## Works with other plugins

None of these are needed. When one is installed, the plugin uses it by itself.

| Plugin | What you get |
| --- | --- |
| WorldEdit or FastAsyncWorldEdit | Mark out a chamber with the wand in [Register a chamber by hand](your-first-chamber.md). FastAsyncWorldEdit also makes big resets smoother (tour question 5). |
| WorldGuard | Your WorldGuard regions are respected inside chambers. |
| Lands, Residence, GriefPrevention | Players can't claim land over a chamber. `/trial claims scan` lists claims that already overlap one. |
| Vault (with an economy plugin) | Money as vault loot, set in [loot.yml](../configuration/loot.yml.md). |
| PlaceholderAPI | Stats on scoreboards and holograms, see [PlaceholderAPI](placeholders.md). |
| AdvancedEnchantments | Set `protection.block-advanced-enchantments: true` to stop mining enchants like Blast Mining digging into chambers. |
| MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens | Your own mobs in spawner waves, see [Custom Mobs](../configuration/custom-mobs.md). |
| Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible | Their items as vault loot, see [loot.yml](../configuration/loot.yml.md). |

{% hint style="info" %}
**Every setting, with what it does:** [config.yml](../configuration/config.yml.md).
The comments in your own `config.yml` say the same.
{% endhint %}

Next: [Example setups](example-setups.md).

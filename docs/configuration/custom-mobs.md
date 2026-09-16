# Custom Mobs

Make a chamber's trial spawner waves spawn mobs from another plugin (MythicMobs, EliteMobs, and others) instead of vanilla mobs, while keeping wave counters, boss bars, glow outlines, spawner cooldowns, Trial Key drops, and statistics working.

{% hint style="info" %}
When a spawner fires, vanilla spawns its mob, BetterTrialChambers removes it the same tick and asks the configured provider to spawn a replacement in the same spot. The vanilla spawner's own tracking of participants and completion is left alone, so waves and vanilla rewards still work.
{% endhint %}

***

## Built-in providers

| Provider id    | Backing plugin                                                                                 | Mob id format                                                     | Notes                                                                          |
| -------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `vanilla`      | none                                                                                         | n/a                                                              | Always available                                                             |
| `mythicmobs`   | [MythicMobs](https://www.mythiccraft.io/mythicmobs)                                          | mob internal name, e.g. `SkeletalKnight`                         |                                                                             |
| `elitemobs`    | [EliteMobs](https://www.magmaguy.com/)                                                       | custom-boss filename, e.g. `the_warden` or `the_warden.yml`      |                                                                             |
| `ecomobs`      | [EcoMobs](https://github.com/Auxilor/EcoMobs) (needs [Eco](https://github.com/Auxilor/eco))  | mob id from `plugins/EcoMobs/mobs/<id>.yml`                      |                                                                             |
| `levelledmobs` | [LevelledMobs](https://www.spigotmc.org/resources/14498/)                                    | `ENTITY_TYPE[:level]`, e.g. `ZOMBIE`, `ZOMBIE:15`, `HUSK:ominous` | `ominous` uses a higher tier on ominous waves                                |
| `infernalmobs` | [InfernalMobs](https://www.spigotmc.org/resources/34710/)                                    | `ENTITY_TYPE`, e.g. `BLAZE`, `WITHER_SKELETON`                   | Spawns a vanilla mob, then the plugin rolls its abilities                     |
| `citizens`     | [Citizens](https://www.spigotmc.org/resources/13811/)                                        | numeric NPC id, or exact NPC name                                | Clones the template NPC so the original is not moved                          |

***

## Set it up in the GUI

1. Run `/trial menu`, open **Chambers**, pick a chamber, then **Settings**.
2. Click the **Custom Mob Provider** button.
3. Use the **Provider** button to choose a provider: left-click to advance, right-click to go back, shift-click to reset to vanilla.
4. Click **Add normal mob id** or **Add ominous mob id**, then type the id in chat (or type `cancel`).
5. Click any listed mob id to remove it.

Changes save right away. If the backing plugin is not installed, the provider name shows in red and spawns fall back to vanilla with a console warning.

***

## Set it up with commands

```
/trial mobs providers                                  # list registered providers
/trial mobs <chamber> list                             # show a chamber's config
/trial mobs <chamber> provider <id | vanilla | none>
/trial mobs <chamber> add <normal | ominous> <mobId>
/trial mobs <chamber> remove <normal | ominous> <mobId>
```

Example, route the `main_spire` chamber through MythicMobs:

```
/trial mobs main_spire provider mythicmobs
/trial mobs main_spire add normal SkeletalKnight
/trial mobs main_spire add normal VoidHound
/trial mobs main_spire add ominous VoidLich
```

If the ominous list is empty, ominous waves use the normal list. If the normal list is also empty while a non-vanilla provider is selected, that wave falls back to vanilla mobs.

***

## Trial Key drops

Once a wave is driven by a custom provider, BetterTrialChambers hands out the keys itself, because the vanilla spawner only knows about vanilla mobs:

* One `TRIAL_KEY` per unique participating player on a normal wave
* One `OMINOUS_TRIAL_KEY` per unique participating player on an ominous wave
* Dropped at the spawner block with a small upward pop
* Owner-locked: only that participant can pick the key up during a grace window, then anyone can

{% hint style="info" %}
Set the grace window in `config.yml`:

```yaml
reset:
  spawner-key-drop-owner-grace-seconds: 30   # 0 = owner-locked until it despawns
```
{% endhint %}

Players with `btc.bypass.droplock` (default: op) can pick up any key.

***

## Check that a provider works

1. Set a provider on a chamber and add one well-known mob id (for example `SkeletalKnight` from a default MythicMobs pack).
2. Set `debug.verbose-logging: true` in `config.yml`, run `/trial reload`, and watch the console.
3. Trigger a trial spawner and watch: the vanilla mob flashes in and is removed, the custom mob appears in the same spot, the boss bar counts down as you kill it.
4. Finish the wave. Trial Keys should drop at the spawner block.

If the custom mob never appears and the console shows `[CustomProvider] ... falling back to vanilla`, check:

* The mob id is spelled right (matching is case-insensitive)
* The backing plugin is installed and enabled (`/plugins`)
* The provider id on the chamber matches one from `/trial mobs providers`

***

## Limits

* Wave state is not saved across restarts. A wave in progress reverts to vanilla behaviour if the server restarts mid-fight.
* Provider availability is checked at spawn time, but the provider list is built once at startup. Installing or removing a backing plugin needs a server restart before its provider registers.

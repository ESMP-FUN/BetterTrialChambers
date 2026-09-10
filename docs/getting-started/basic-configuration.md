# Basic Configuration

You have installed the plugin and created your first chamber. This page walks through the handful of settings most servers change on day one. For everything else, see the full [config.yml reference](../configuration/config.yml.md).

{% hint style="info" %}
**Config location:** `plugins/BetterTrialChambers/config.yml`

After editing, run `/trial reload` to apply your changes. Most settings take effect right away with no restart.
{% endhint %}

***

## Change how often chambers reset

Set the time between automatic resets, in seconds.

```yaml
global:
  default-reset-interval: 172800  # 48 hours
```

* **Key:** `global.default-reset-interval`
* **Default:** `172800` (48 hours)
* **Value:** a number of seconds. `0` turns automatic resets off completely, so chambers only reset when you run `/trial reset` or use the menu.

Handy values:

| Interval | Seconds |
| --- | --- |
| Every 6 hours | `21600` |
| Daily | `86400` |
| Twice daily | `43200` |
| Weekly | `604800` |

Busier servers want shorter intervals so loot comes back sooner. It is easier to shorten an interval later than to explain to players why chambers changed sooner than expected.

{% hint style="success" %}
**Start longer, shorten later.** Speeding resets up is painless. Slowing them down after players get used to fast loot causes complaints.
{% endhint %}

You can also give one chamber its own interval from the chamber settings menu (`/trial menu`, pick a chamber, Settings). That overrides the global value for that chamber only.

***

## Set a per-vault cooldown

Control how long a player must wait before looting the same vault again.

```yaml
vaults:
  normal-cooldown-hours: 0
  ominous-cooldown-hours: 0
```

* **Keys:** `vaults.normal-cooldown-hours`, `vaults.ominous-cooldown-hours`
* **Default:** `0` for both
* **Values:**
  * `0` - vanilla style. Once a player opens a vault it stays shut for them until the chamber resets.
  * any positive number - that many hours before the same player can open that vault again, even if the chamber has not reset.

Ominous vaults usually hold better loot, so most servers give them the longer wait.

Changing these values does not touch cooldowns that are already running. New cooldowns use the new numbers.

Example, a fast server where vaults reopen well before the chamber resets:

```yaml
global:
  default-reset-interval: 43200  # 12 hours
vaults:
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 12
```

***

## Choose who gets vault loot

Decide whether each player gets their own reward, or vaults are first come first served, or the plugin stays out of the way entirely.

```yaml
vaults:
  loot-mode: PER_PLAYER
```

* **Key:** `vaults.loot-mode`
* **Default:** `PER_PLAYER`
* **Values:**
  * `PER_PLAYER` - every player who reaches a vault gets their own roll from your loot tables. Ten players, ten rewards from the same vault.
  * `SHARED` - the first player to open a vault takes it, and it stays shut for everyone else until the chamber resets. One vault, one reward. Your loot tables still apply. Needs `reset.reset-vault-cooldowns` left on (it is by default), otherwise a claimed vault never reopens.
  * `VANILLA` - the plugin does not touch vaults at all. They behave exactly like a normal Minecraft world (crossbows, wind charges, and so on) and **your loot tables are ignored**. This is still one open per player, not shared.

{% hint style="warning" %}
After switching to `SHARED` or `VANILLA`, run `/trial vault unlockall all` once. Minecraft keeps its own record of who opened each vault, so players who already opened one will still find it shut until you clear it.
{% endhint %}

***

## Turn on griefing protection

Stop players changing chambers between resets.

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  prevent-container-access: false
  allow-pvp: true
  prevent-mob-griefing: true
```

* **Keys sit under `protection:`.** All of them only apply inside a registered chamber. Anyone with the `btc.bypass.protection` permission (operators included) ignores them.

| Key | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Master switch. `false` turns off every protection below. |
| `prevent-block-break` | `true` | Players cannot break blocks in a chamber. |
| `prevent-block-place` | `true` | Players cannot place blocks in a chamber. |
| `prevent-container-access` | `false` | Players cannot open chests, barrels, and similar. Leave off unless you have a reason. |
| `allow-pvp` | `true` | `true` lets players fight inside chambers. `false` blocks player-on-player damage there. |
| `prevent-mob-griefing` | `true` | Stops creepers, endermen, and the like from damaging chamber blocks. |

Survival server that wants peaceful farming:

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false
```

{% hint style="warning" %}
**Do not disable protection on a public survival server.** Players will grief your chambers. Give staff the `btc.bypass.protection` permission instead.
{% endhint %}

There are more protection toggles for liquids, fire, pistons, decorations, and land-claim plugins. They are all on sensible defaults. See the [protection config reference](../configuration/config.yml.md#protection-settings) if you need to change them.

***

## Control who can get into a chamber

Two off-by-default toggles stop players skipping the intended entrance.

```yaml
protection:
  prevent-teleport-into-chamber: false
  prevent-entry-without-permission: false
```

* **`prevent-teleport-into-chamber`** - set to `true` to block every kind of teleport into a chamber (`/tpa`, `/home`, `/warp`, `/tp`, ender pearls, chorus fruit, and so on). Walking in is unaffected.
* **`prevent-entry-without-permission`** - set to `true` to make chambers rank-only. Only players with `btc.bypass.entry` (default: operators) can walk in. Use this for rank-gated chambers.

Spectators and creative-mode players are exempt from both. See the [protection config reference](../configuration/config.yml.md#prevent-teleport-into-chamber) for details.

***

## Stop mass block-breakers

Vein miners are blocked inside chambers automatically. Effect-based mining enchants (for example AdvancedEnchantments Blast Mining) break blocks through a different path and need their own switch.

```yaml
protection:
  block-advanced-enchantments: false
```

Set to `true` (with AdvancedEnchantments installed) to cancel those enchant activations for players standing in a chamber. See [that section](../configuration/config.yml.md#block-advanced-enchantments) for the details.

***

## Adjust how much loot vaults give

Loot tables live in `loot.yml`, not `config.yml`. Each table has one or more pools, and each pool decides how many items it hands out.

```yaml
loot-tables:
  default:
    pools:
      - name: "main-reward"
        min-rolls: 1
        max-rolls: 1
```

* **`min-rolls` / `max-rolls`** - the number of items that pool gives, picked at random between the two each time.
* Raise both for a more generous table, lower both for a leaner one.

For anything more than this, see the [loot.yml reference](../configuration/loot.yml.md).

***

## Speed up or slow down resets

Control how fast blocks are placed back during a reset.

```yaml
global:
  blocks-per-tick: 500
```

* **Key:** `global.blocks-per-tick`
* **Default:** `500`
* **Values:** a number of blocks per game tick (there are 20 ticks per second).
  * Lower (250 to 500) - slower resets, gentler on the server.
  * Higher (1000 or more) - faster resets, more chance of a lag spike.

Raise it on a dedicated server with a good CPU. Keep it low on shared hosting, a budget VPS, or for very large chambers.

To test, run `/trial reset YourChamber`, watch for lag, then adjust and try again.

***

## Change the reset warnings

Players inside a chamber get warned before it resets.

```yaml
global:
  reset-warning-times: [300, 60, 30]
```

* **Key:** `global.reset-warning-times`
* **Default:** `[300, 60, 30]` (5 minutes, 1 minute, 30 seconds before reset)
* **Value:** a list of times in seconds. An empty list `[]` means no warnings at all.

More warnings:

```yaml
reset-warning-times: [600, 300, 120, 60, 30, 10]
```

Just one:

```yaml
reset-warning-times: [60]
```

{% hint style="info" %}
**These numbers are seconds.** `300` = 5 minutes, `60` = 1 minute, `30` = 30 seconds.
{% endhint %}

***

## Choose where players go on reset

When a chamber resets, players inside it are moved out.

```yaml
global:
  teleport-players-on-reset: true
  teleport-location: EXIT_POINT
```

* **`teleport-players-on-reset`** (default `true`) - move players out before the reset. Turning this off risks players being trapped in restored walls.
* **`teleport-location`** (default `EXIT_POINT`) - where they land:
  * `EXIT_POINT` - the chamber's set exit point.
  * `OUTSIDE_BOUNDS` - just outside the chamber walls.
  * `WORLD_SPAWN` - the world spawn point.

***

## Add particle and sound cues on vaults

Make vault state easier to read at a glance.

```yaml
vaults:
  show-cooldown-particles: true
  particles:
    normal-available: VILLAGER_HAPPY
    normal-cooldown: SMOKE_NORMAL
    ominous-available: SOUL_FIRE_FLAME
    ominous-cooldown: SOUL
  play-sound-on-open: true
  sounds:
    normal-open: BLOCK_VAULT_OPEN_SHUTTER
    ominous-open: BLOCK_VAULT_OPEN_SHUTTER
    cooldown: BLOCK_NOTE_BLOCK_BASS
```

* **`show-cooldown-particles`** (default `true`) - particles around vaults showing whether they are ready or resting.
* **`play-sound-on-open`** (default `true`) - a sound when a vault is opened or refused.

Turn both off for a quieter look:

```yaml
vaults:
  show-cooldown-particles: false
  play-sound-on-open: false
```

Subtler particles:

```yaml
particles:
  normal-available: END_ROD
  normal-cooldown: SMOKE_NORMAL
  ominous-available: PORTAL
  ominous-cooldown: ASH
```

See [Spigot's Particle list](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html) for every option.

***

## Turn statistics tracking on or off

Track player activity for leaderboards and placeholders.

```yaml
statistics:
  enabled: true
  track-time-spent: true
  track-chamber-completion: true
  top-players-count: 10
```

* **`enabled`** (default `true`) - master switch for all stat tracking.
* **`track-time-spent`** (default `true`) - record how long players spend in chambers.
* **`track-chamber-completion`** (default `true`) - count a completion for every player when a chamber is fully cleared.
* **`top-players-count`** (default `10`) - how many players show on leaderboards.

Turn tracking off entirely:

```yaml
statistics:
  enabled: false
```

***

## Pick a database

Where the plugin stores its data.

```yaml
database:
  type: SQLITE
```

* **`database.type`** (default `SQLITE`)
  * `SQLITE` - a single file in the plugin folder. Nothing to install. Right for almost every server.
  * `MYSQL` - your own MySQL or MariaDB server, using the settings below. Worth it only if you run several servers that should share one set of chambers and stats.

MySQL settings (only used when `type` is `MYSQL`):

```yaml
database:
  type: MYSQL
  host: localhost
  port: 3306
  database: trialchamberpro
  username: root
  password: ""
  pool-size: 10
```

{% hint style="warning" %}
**Database changes need a full server restart,** not just `/trial reload`.
{% endhint %}

***

## Quick config templates

<details>

<summary><strong>Casual survival server</strong></summary>

```yaml
global:
  default-reset-interval: 86400  # Daily
  reset-warning-times: [300, 60]
  teleport-players-on-reset: true

vaults:
  loot-mode: PER_PLAYER
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 12
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

statistics:
  enabled: true
```

Generous cooldowns, peaceful gameplay, daily resets.

</details>

<details>

<summary><strong>Competitive / PvP server</strong></summary>

```yaml
global:
  default-reset-interval: 43200  # Twice daily
  reset-warning-times: [120, 60, 30, 10]
  teleport-players-on-reset: true

vaults:
  loot-mode: PER_PLAYER
  normal-cooldown-hours: 12
  ominous-cooldown-hours: 24
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: true

statistics:
  enabled: true
  track-time-spent: true
```

Frequent resets, PvP enabled, competitive stat tracking.

</details>

<details>

<summary><strong>High-activity server</strong></summary>

```yaml
global:
  default-reset-interval: 21600  # Every 6 hours
  reset-warning-times: [60, 30]
  teleport-players-on-reset: true
  blocks-per-tick: 1000

vaults:
  loot-mode: PER_PLAYER
  normal-cooldown-hours: 3
  ominous-cooldown-hours: 6
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

performance:
  cache-duration-seconds: 600
```

Fast resets, short cooldowns, tuned for many players.

</details>

<details>

<summary><strong>Roleplay / immersive server</strong></summary>

```yaml
global:
  default-reset-interval: 604800  # Weekly
  reset-warning-times: [300, 60]
  teleport-players-on-reset: true

vaults:
  loot-mode: PER_PLAYER
  normal-cooldown-hours: 168   # Weekly
  ominous-cooldown-hours: 336  # Every two weeks
  show-cooldown-particles: false
  play-sound-on-open: false

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

statistics:
  enabled: false
```

Scarce resources, minimal on-screen feedback, long cooldowns for loot that means something.

</details>

***

## Apply your changes

1. Edit `plugins/BetterTrialChambers/config.yml`.
2. Save the file.
3. Run `/trial reload`.

Most settings apply immediately. Exceptions:

* Database settings need a full restart.
* A changed reset interval affects the next reset, not a timer already counting down.

{% hint style="success" %}
**Back up first.** Copy your `plugins/BetterTrialChambers/` folder before making big changes.
{% endhint %}

{% hint style="info" %}
YAML needs spaces, never tab characters, for indentation. One tab and the file fails to load.
{% endhint %}

***

## Common questions

**Do I need to restart after editing config?** Usually no. `/trial reload` covers most settings. Only database changes need a restart.

**Can different chambers have different reset intervals?** Yes. The global `default-reset-interval` is the fallback. Set a per-chamber interval from the chamber settings menu (`/trial menu`, pick a chamber, Settings).

**What happens to running cooldowns if I change the cooldown hours?** Nothing. Cooldowns already counting down keep their old value. New cooldowns use the new number.

**Can a vault give one reward to whoever gets there first?** Yes. Set `vaults.loot-mode: SHARED`. The first player to open it takes it and it stays shut for everyone else until the chamber resets. Your loot tables still apply. Run `/trial vault unlockall all` once after switching.

**Can I turn the plugin's vault handling off entirely?** Set `vaults.loot-mode: VANILLA`. Vaults then behave like a normal Minecraft world and your loot tables are ignored. This is not the same as shared loot, plain Minecraft is still one open per player. Run `/trial vault unlockall all` afterwards or vaults players already opened stay shut.

**How do I make chambers reset faster without lag?** Raise `global.blocks-per-tick` in steps (500, then 750, then 1000) and test for lag between each change.

***

## Next steps

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

The complete reference for every config option.

{% content-ref url="../configuration/loot.yml.md" %}
[loot.yml.md](../configuration/loot.yml.md)
{% endcontent-ref %}

Customize what players get from vaults.

{% content-ref url="../configuration/messages.yml.md" %}
[messages.yml.md](../configuration/messages.yml.md)
{% endcontent-ref %}

Change every player-facing message to match your server's style.

***

## Pro tips

{% hint style="success" %}
**Test in a dev environment first.** Make a test chamber and try settings there before touching production.
{% endhint %}

{% hint style="info" %}
**Start conservative with cooldowns and intervals.** Shortening them later based on player feedback is easy. Going the other way causes complaints.
{% endhint %}

{% hint style="warning" %}
**Watch your server TPS during resets.** If it drops, lower `global.blocks-per-tick` or spread resets out with more `reset-warning-times`.
{% endhint %}

{% hint style="success" %}
**Join the discussion.** Check [GitHub Issues](https://github.com/ESMP-FUN/BetterTrialChambers/issues) for community config tips and common setups.
{% endhint %}

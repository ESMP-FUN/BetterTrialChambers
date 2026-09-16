# config.yml

`config.yml` controls how chambers behave, when they reset, and how much protection is applied.

{% hint style="info" %}
**Location:** `plugins/BetterTrialChambers/config.yml`

After editing, run `/trial reload`. Database changes need a full server restart.
{% endhint %}

{% hint style="success" %}
**Your config auto-updates (1.5.19+).** On update, new options are merged into your existing file with their comments; your current values are left alone. The old file is saved as `config.yml.bak` first. `messages.yml` updates the same way.
{% endhint %}

***

## Reading config paths in this guide

Settings are written here as dotted paths like `global.reset-complete-alert`. That is the lookup form, not the YAML form. In the file the keys are nested:

<table data-header-hidden><thead><tr><th></th><th></th></tr></thead><tbody><tr><td>Documented path</td><td>What it looks like in <code>config.yml</code></td></tr><tr><td><code>global.reset-complete-alert: false</code></td><td><pre><code>global:
  reset-complete-alert: false
</code></pre></td></tr><tr><td><code>vaults.normal-cooldown-hours: 1</code></td><td><pre><code>vaults:
  normal-cooldown-hours: 1
</code></pre></td></tr></tbody></table>

When a setting is documented as `foo.bar.baz`, put `baz` inside the existing `foo:` section, nested under `bar:`. Do not paste `foo.bar.baz: value` as a flat line. If an edited setting seems to do nothing, check this first.

{% hint style="warning" %}
YAML requires spaces, never TAB characters. One TAB breaks the whole file silently.
{% endhint %}

***

## Database Settings

Chooses where BetterTrialChambers stores its data.

```yaml
database:
  type: SQLITE
  host: localhost
  port: 3306
  database: trialchamberpro
  username: root
  password: ""
  pool-size: 10
  table-prefix: "tcp_"
```

### `type`

Storage backend. **Options:** `SQLITE`, `MYSQL`. **Default:** `SQLITE`.

- `SQLITE`: one file in the plugin folder, nothing to install. Use this unless you run several servers that must share chambers.
- `MYSQL`: your own MySQL/MariaDB server, using the settings below. For networks that share one set of chambers and stats.

Everything needed for both is built into the plugin. (Before 2.0.8 the MySQL part was missing from the download.)

### MySQL options (only used when `type: MYSQL`)

| Setting | Description |
| ------- | ----------- |
| `host` | Database server address |
| `port` | Usually `3306` |
| `database` | Database name |
| `username` | Database user |
| `password` | User's password |
| `pool-size` | Number of pooled connections (**default `10`**, fine for most setups) |

### `table-prefix` (v1.7.0)

Prefix on every BTC table name, so BTC can share a database with other plugins. **Default:** `tcp_`. Letters, digits and underscores only, max 16 characters; an invalid value logs a warning and falls back to `tcp_`.

Change it only if another plugin already uses `tcp_` tables. On the first 1.7.0 start, existing unprefixed tables (`chambers`, etc.) are renamed to `tcp_` automatically, keeping all data. A same-named table owned by another plugin is detected and left alone.

***

## Global Chamber Settings

Reset timing, reset messages, teleport-on-reset, and the reset throttle.

```yaml
global:
  excluded-worlds: []
  default-reset-interval: 172800
  reset-warning-times: [300, 60, 30]
  reset-complete-alert: true
  reset-complete-audience: chamber
  reset-complete-permission: ""
  broadcast-chamber-cleared: false
  teleport-players-on-reset: true
  teleport-location: EXIT_POINT
  blocks-per-tick: 500
  auto-snapshot-on-register: true
  auto-scan-on-register: true
  max-concurrent-resets: 1
  reset-stagger-seconds: 5
  reset-require-confirmation: false
  suppress-trial-spawner-spam: true
  use-fawe: false
```

<details>

<summary><code>excluded-worlds</code> (v1.8.1)</summary>

Worlds where BetterTrialChambers stays inactive. **Default:** `[]` (none). World names are case-insensitive.

```yaml
excluded-worlds:
  - second_overworld
```

In an excluded world: auto-discovery never registers chambers, `/trial generate` and `/trial dungeon generate` are refused, and wild trial spawners get no boss bars or wave tracking. Chambers registered before the world was excluded keep working; remove them with `/trial delete <name>`.

</details>

<details>

<summary><code>default-reset-interval</code></summary>

Seconds before a chamber automatically resets. **Default:** `172800` (48 hours). This is the default for every chamber unless overridden per-chamber.

- `0` or negative: automatic resets off, reset only via `/trial reset` or the GUI.
- Daily: `86400`. Twice daily: `43200`. Weekly: `604800`.

</details>

<details>

<summary><code>reset-warning-times</code></summary>

Seconds-before-reset at which players inside a chamber are warned. **Default:** `[300, 60, 30]` (5 min, 1 min, 30 s).

```yaml
reset-warning-times: [60]   # only warn 1 minute before
```

</details>

<details>

<summary><code>reset-complete-alert</code></summary>

Whether the "chamber has been reset" message is sent at all. **Default:** `true`. Set `false` to suppress it everywhere. Can also be overridden per-chamber from the GUI. Use `reset-complete-audience` to control who receives it.

</details>

<details>

<summary><code>reset-complete-audience</code> (2.0.4+)</summary>

Who receives the reset-complete message. **Default:** `chamber`.

| Value | Who sees it |
| ----- | ----------- |
| `chamber` | Only players who were inside that chamber when it reset |
| `server` | Every online player (the pre-2.0.4 behaviour) |

The shipped message does not name the chamber. If you set this to `server`, add `{chamber}` to the `chamber-reset-complete` entry in `messages.yml` so the line says which chamber reset.

</details>

<details>

<summary><code>reset-complete-permission</code> (2.0.4+)</summary>

Optional permission node required to receive the reset-complete message. **Default:** `""` (no gating). Applies in both audience modes; with `chamber` a player must have been inside and hold the node. Any node works (e.g. `btc.notify.reset`), granted through your permission plugin.

</details>

<details>

<summary><code>broadcast-chamber-cleared</code></summary>

Server-wide broadcast when every trial spawner in a chamber finishes its wave in one run (e.g. "Bastion has been cleared by Steve, Alex!"). **Default:** `false`. Separate from `reset-complete-alert`, which fires on reset, not clear.

</details>

<details>

<summary><code>teleport-players-on-reset</code></summary>

Move players out of a chamber when it resets. **Default:** `true`. If `false`, players stay inside during the reset and may suffocate.

</details>

<details>

<summary><code>teleport-location</code></summary>

Where evicted players go. **Options:** `EXIT_POINT`, `OUTSIDE_BOUNDS`, `WORLD_SPAWN`. **Default:** `EXIT_POINT`.

- `EXIT_POINT`: the chamber's `/trial setexit` location (recommended).
- `OUTSIDE_BOUNDS`: just outside the chamber wall.
- `WORLD_SPAWN`: server spawn.

</details>

<details>

<summary><code>blocks-per-tick</code></summary>

How many blocks are placed per game tick during a reset. **Default:** `500` (about 10,000 per second). Higher is faster but heavier on the server; lower is slower but smoother. Range enforced 1 to 50000.

</details>

<details>

<summary><code>auto-snapshot-on-register</code></summary>

Take a snapshot automatically when a chamber is registered with `/trial generate`. **Default:** `true`. A chamber cannot be reset without a snapshot. Uses disk space.

</details>

<details>

<summary><code>auto-scan-on-register</code></summary>

Scan for vaults and trial spawners immediately after `/trial generate`, so you do not have to run `/trial scan` yourself. **Default:** `true`. Turn off if you register very large regions and prefer to scan them by hand later.

</details>

<details>

<summary><code>max-concurrent-resets</code> / <code>reset-stagger-seconds</code></summary>

Throttle for automatic resets, so many chambers coming due at once do not all restore together and drop the server's tick rate. **Defaults:** `1` chamber at a time, `5` seconds minimum gap between one reset finishing and the next starting. Confirmed and queued resets respect these too.

</details>

<details>

<summary><code>reset-require-confirmation</code></summary>

When `true`, a chamber that becomes due is parked in a queue instead of resetting, and online admins are notified. **Default:** `false`. List the queue with `/trial reset pending`, release with `/trial reset confirm <chamber|all>` (still staggered).

</details>

<details>

<summary><code>suppress-trial-spawner-spam</code></summary>

Mute the vanilla console line `Trial Spawner at BlockPos{...} has no detected players`. **Default:** `true`. New occurrences are already prevented by the reset fixes; this hides the line for spawners broken before you updated, until their chamber next resets.

</details>

<details>

<summary><code>use-fawe</code></summary>

Place blocks through FastAsyncWorldEdit during scheduled (automatic) resets, to smooth out lag on large chambers. **Default:** `false`. Needs FastAsyncWorldEdit installed. Paper only (ignored on Folia). Manual `/trial reset` keeps the normal path so WorldEdit `//undo` still works. Falls back automatically if FAWE is missing.

</details>

***

## Chamber Display Names

Gives each chamber a friendly name shown in reset and clear broadcasts and `/trial info`. The internal name used in commands never changes. Rename any chamber with `/trial rename <chamber> <name>` or the GUI.

```yaml
naming:
  auto-assign: true
  name-pool:
    - Bastion
    - Ashfall Hollow
    - Solasta
    # ...
```

<details>

<summary><code>auto-assign</code></summary>

Give each newly registered chamber (manual or auto-discovered) a random unused name from `name-pool`. **Default:** `true`. Set `false` to leave new chambers nameless until you rename them.

</details>

<details>

<summary><code>name-pool</code></summary>

The list of display names to draw from. A name already used by another chamber is skipped; when all are taken, new chambers use their internal name until you add more. The bundled list is a starting set; add your own.

</details>

***

## Extra Messages

Optional player-facing messages, all off by default.

```yaml
messages:
  chamber-entry-message: false
  chamber-exit-message: false
  custom-death-message: false
```

| Setting | Effect when `true` |
| ------- | ------------------ |
| `chamber-entry-message` | Tell a player when they walk into one of your chambers. Crossing straight from one chamber into another does not re-trigger it. |
| `chamber-exit-message` | Tell a player when they leave one. |
| `custom-death-message` | Use the plugin's wording when a player dies inside a chamber, instead of the server's usual death message. |

***

## Generation Settings

Limits applied when registering or generating a chamber region.

```yaml
generation:
  max-volume: 750000
  blocks:
    rounding-allowance: 1000
```

- `max-volume`: largest region (in blocks) allowed for `/trial generate`. **Default:** `750000`. Range enforced 1 to 5,000,000. For datapack-enlarged discovered chambers, `discovery.structure-max-volume` is used instead.
- `blocks.rounding-allowance`: with `/trial generate blocks <amount>`, the most extra blocks allowed beyond the requested amount when rounding up to a solid box. **Default:** `1000`.

Notes:

- Hard minimum region size is 31 x 15 x 31.
- The `blocks` generator places the region in front of the player, based on facing.
- A requested amount below the minimum is rounded up to the minimum.

***

## Vault Settings

Who gets vault loot, vault cooldowns, particles, sounds, and how a vault result is shown.

```yaml
vaults:
  loot-mode: PER_PLAYER
  per-player-loot: true
  normal-cooldown-hours: 0
  ominous-cooldown-hours: 0
  reopen-cost-keys: 0
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
  feedback:
    mode: TEXT
    hologram:
      duration-ticks: 30
      y-offset: 1.4
      scale: 1.5
      see-through: true
      success-text: "&a✔"
      fail-text: "&c✘"
    sounds:
      success: ENTITY_PILLAGER_CELEBRATE
      fail: ENTITY_PILLAGER_AMBIENT
  drop-loot-at-vault: false
  drop-loot-owner-only: true
  drop-loot-owner-grace-seconds: 30
```

<details>

<summary><code>loot-mode</code></summary>

Who gets the loot from a vault inside your chambers. **Default:** `PER_PLAYER`.

- `PER_PLAYER`: every player gets their own reward from the same vault, from your custom loot tables, with their own cooldown. What most servers want.
- `SHARED`: whoever opens a vault takes it; it stays shut for everyone else until the chamber resets. Your custom loot tables still apply. Other players are told who got there first. Needs `reset.reset-vault-cooldowns` left on (it is by default), or a claimed vault never reopens.
- `VANILLA`: the plugin leaves vaults alone. Plain Minecraft loot, your loot tables ignored, no per-player cooldowns.

{% hint style="danger" %}
**Getting vanilla items instead of your loot table?** `loot-mode` is set to `VANILLA`. Set it back to `PER_PLAYER` and run `/trial reload`. Check the current value with `/trial info`.
{% endhint %}

{% hint style="warning" %}
**Switching to `VANILLA`:** Minecraft keeps its own record of who opened each vault. Players who already opened one will find it shut with no loot. Run `/trial vault unlockall all` after switching to reopen them.
{% endhint %}

</details>

<details>

<summary><code>per-player-loot</code> (old setting)</summary>

The older on/off switch that `loot-mode` replaced. **Default:** `true`. Read only when `loot-mode` is missing (`true` behaves like `PER_PLAYER`, `false` like `VANILLA`), so old configs keep working. If `loot-mode` is present, this line does nothing; no need to add it to a new config.

</details>

<details>

<summary><code>normal-cooldown-hours</code> / <code>ominous-cooldown-hours</code></summary>

Hours before a player can loot the same vault again. Separate values for normal and ominous. **Default:** `0` for both.

- `0` or negative: locked until the chamber resets (vanilla behaviour).
- positive `N`: the vault reopens `N` hours after that player last opened it. A player who tries too early is shown the remaining time.

A chamber reset always unlocks every vault regardless of a timed cooldown. So a 6-hour cooldown on a chamber that resets every 48 hours lets a player loot the same vault about 8 times per cycle. Timed cooldowns were only actually implemented from 1.5.12.

</details>

<details>

<summary><code>reopen-cost-keys</code> (1.5.7+)</summary>

Let a player reopen an already-used vault by paying this many matching trial keys in total (a fresh open costs 1). **Default:** `0` (off). `1` = reopen at the same price, `2` = one extra key, and so on. Keys must be held in the main hand. Works alongside timed cooldowns as an instant paid alternative to waiting. With trial-key farms, `2` to `3` is a reasonable price.

</details>

<details>

<summary><code>show-cooldown-particles</code> / <code>particles</code></summary>

Show status particles above vaults. **Default:** `true`.

| Key | Meaning |
| --- | ------- |
| `normal-available` | Normal vault ready |
| `normal-cooldown` | Normal vault on cooldown |
| `ominous-available` | Ominous vault ready |
| `ominous-cooldown` | Ominous vault on cooldown |

Values come from [Spigot's Particle enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html).

</details>

<details>

<summary><code>play-sound-on-open</code> / <code>sounds</code></summary>

Play a sound when a vault opens (`normal-open`, `ominous-open`) or when someone tries during cooldown (`cooldown`). **Default:** `true`. Values come from [Spigot's Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html).

</details>

<details>

<summary><code>feedback</code> (1.5.8+)</summary>

How a vault interaction is reported to the player. **Default:** `mode: TEXT`.

- `TEXT`: chat lines ("You opened a Normal Vault!", "You need a Trial Key...", etc.).
- `HOLOGRAM`: a floating green tick or red cross above the vault, visible only to that player, plus a sound. Replaces the chat line for every outcome. Particles and advancements still fire.

```yaml
vaults:
  feedback:
    mode: HOLOGRAM
    hologram:
      duration-ticks: 30      # how long the mark stays (20 ticks = 1 second)
      y-offset: 1.4           # height above the vault block
      scale: 1.5              # text size multiplier
      see-through: true       # render through blocks
      success-text: "&a✔"
      fail-text: "&c✘"
    sounds:
      success: ENTITY_PILLAGER_CELEBRATE
      fail: ENTITY_PILLAGER_AMBIENT
```

`duration-ticks` range enforced 1 to 1200. `success` / `fail` accept any value from [Spigot's Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html) or a `namespace:path` key. The two defaults are random-variant sounds, so a specific clip cannot be pinned without a resource pack.

</details>

<details>

<summary><code>drop-loot-at-vault</code> / <code>drop-loot-owner-only</code> / <code>drop-loot-owner-grace-seconds</code></summary>

```yaml
vaults:
  drop-loot-at-vault: false
  drop-loot-owner-only: true
  drop-loot-owner-grace-seconds: 30
```

- `drop-loot-at-vault` (**default `false`**): when `true`, vault loot pops out of the block like vanilla instead of going straight into the opener's inventory. Command rewards and status effects still apply directly to the player.
- `drop-loot-owner-only` (**default `true`**): only the opener can pick up the dropped items.
- `drop-loot-owner-grace-seconds` (**default `30`**): how long owner-only lasts. After this, anyone can pick the items up so they do not linger if the opener logs off. `0` = owner-locked until the item despawns.

Players with `btc.bypass.droplock` (default: op) can always pick up dropped loot.

</details>

***

## Protection Settings

Stops griefing and preserves chamber structures. Every setting here applies only inside a registered chamber, and anyone with `btc.bypass.protection` (includes operators) is exempt.

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  tunnel-breaking:
    enabled: false
    blocks: [TUFF_BRICKS, TUFF_BRICK_SLAB, TUFF_BRICK_STAIRS, TUFF_BRICK_WALL, CHISELED_TUFF, CHISELED_TUFF_BRICKS, POLISHED_TUFF, POLISHED_TUFF_SLAB, POLISHED_TUFF_STAIRS, POLISHED_TUFF_WALL]
    shell-depth: 3
  prevent-container-access: false
  message-cooldown-ms: 1500
  block-advanced-enchantments: false
  advanced-enchantments-allowlist: []
  advanced-enchantments-block-radius: 2
  prevent-teleport-into-chamber: false
  prevent-entry-without-permission: false
  allow-pvp: true
  prevent-mob-griefing: true
  prevent-liquid-flow: true
  prevent-fire: true
  prevent-piston-movement: true
  protect-decorations: true
  prevent-natural-decay: false
  worldguard-integration: true
  residence-integration: true
  lands-integration: true
  griefprevention-integration: true
  claim-conflict-scan-on-startup: true
  block-wild-vault-placement: true
  auto-pause-on-destruction: false
  auto-pause-threshold: 6
```

<details>

<summary><code>enabled</code></summary>

Master toggle for chamber protection. **Default:** `true`. Turn off to rely entirely on WorldGuard or another plugin.

</details>

<details>

<summary><code>prevent-block-break</code> / <code>prevent-block-place</code></summary>

Stop players breaking or placing blocks inside chambers. **Default:** `true` for both.

</details>

<details>

<summary><code>tunnel-breaking</code> (v1.7.0)</summary>

Lets players mine into a walled natural chamber without being able to farm it. **Default:** `enabled: false`.

When enabled, players may break the listed materials (default: the tuff-brick shell family) inside a registered chamber, but the broken blocks drop nothing (no items, no XP) and are restored on the next reset. Everything else stays protected.

`shell-depth` (**default `3`**, range 0 to 64) limits how far in from the outer wall this works: `3` = only the outer 3-block shell; `0` = anywhere in the chamber.

</details>

<details>

<summary><code>prevent-container-access</code></summary>

Block players taking from or adding to anything inside a chamber that holds items: chests, barrels, hoppers, shelves, lecterns, jukeboxes and decorated pots. Vaults are never affected, so vault loot still works. **Default:** `false`, because you usually want players opening chests for loot. Set `true` if you keep your own containers inside chambers.

</details>

<details>

<summary><code>message-cooldown-ms</code> (1.5.18+)</summary>

Milliseconds to wait before re-showing a "you can't do that here" denial to the same player. **Default:** `1500`. Stops chat flooding when one action (a mining enchant, a vein miner, rapid clicking) hits many blocks. `0` disables the throttle.

</details>

<details id="block-advanced-enchantments">

<summary><code>block-advanced-enchantments</code></summary>

If [AdvancedEnchantments](https://www.spigotmc.org/resources/76519/) is installed, its custom enchants (e.g. Blast Mining) can break chamber blocks through their own path that ignores the normal block-break block. **Default:** `false`. When `true`, BTC cancels AE enchant activations that affect a registered chamber. Enchants in `advanced-enchantments-allowlist` are still allowed. Nothing happens on servers without AE.

Since 1.5.21 this also catches mining a chamber wall from just outside it; see `advanced-enchantments-block-radius`.

{% hint style="info" %}
**Vein miners and hotkey area-miners usually do not need this.** They simulate a normal block break per block, which the standard block protection already cancels. This setting is only for enchant or skill engines that break blocks through their own pipeline.
{% endhint %}

</details>

<details>

<summary><code>advanced-enchantments-allowlist</code></summary>

Enchant names (lowercase, matching the AE name) that stay allowed inside chambers even when `block-advanced-enchantments` is on. **Default:** `[]`. Use it to keep combat enchants working while blocking block-breakers.

```yaml
advanced-enchantments-allowlist:
  - lifesteal
  - poisoned
```

</details>

<details>

<summary><code>advanced-enchantments-block-radius</code></summary>

How far (in blocks) an AE mining blast can reach, used to catch mining a chamber wall from just outside. **Default:** `2` (covers a 5x5 area-miner). Range enforced 0 to 64 (practically capped at 16). The check is centred on the block being mined, expanded by this radius; if it touches a chamber, the enchant is blocked.

- `0`: block only when the mined block is itself inside a chamber (no outside margin).
- Higher: covers bigger area enchants, but also denies mining a little further out from chamber walls.

</details>

<details id="prevent-teleport-into-chamber">

<summary><code>prevent-teleport-into-chamber</code></summary>

Stop players teleporting into a registered chamber from outside, so they cannot skip the entrance. **Default:** `false`. Hooks the teleport itself, so it catches `/tpa`, `/home`, `/warp`, `/tp`, ender pearls, chorus fruit, and plugin teleports. Walking in is unaffected. Players with `btc.bypass.entry` (default: op), spectators, and creative-mode players are exempt.

</details>

<details>

<summary><code>prevent-entry-without-permission</code> (1.5.20+)</summary>

Gate walking into a chamber by rank. **Default:** `false`. When `true`, only players with `btc.bypass.entry` (default: op) can enter on foot; everyone else is stopped at the boundary. Pair with `prevent-teleport-into-chamber` to close the teleport route too. Spectators and creative-mode players are exempt.

</details>

<details>

<summary><code>allow-pvp</code> (1.5.19+)</summary>

Allow player-vs-player combat inside registered chambers. **Default:** `true` (PvP follows your world and server rules). When `false`, BTC cancels player-on-player melee and player-shot projectile damage inside a chamber. Mob damage and self-damage are never affected.

</details>

<details>

<summary><code>prevent-mob-griefing</code></summary>

Stop mobs changing chamber blocks (creeper explosions, endermen picking up blocks, etc.). **Default:** `true`.

</details>

<details>

<summary><code>prevent-liquid-flow</code></summary>

Stop water and lava getting into a chamber. **Default:** `true`. Covers emptying a bucket inside one, pouring it just outside so it flows in, and a dispenser firing a bucket through the wall. Liquid that was part of the build keeps flowing.

</details>

<details>

<summary><code>prevent-fire</code></summary>

Stop fire being lit inside a chamber and stop it spreading if something catches. **Default:** `true`.

</details>

<details>

<summary><code>prevent-piston-movement</code></summary>

Stop pistons moving blocks into or out of a chamber. **Default:** `true`. A piston reaches twelve blocks, so without this someone can push blocks in or pull them out from outside the wall. A piston inside the same chamber as the blocks it moves is left alone, so redstone built into a chamber still works.

</details>

<details>

<summary><code>protect-decorations</code></summary>

Protect item frames, paintings, and armour stands. **Default:** `true`. These are not blocks, so the block settings never applied to them. Turning a frame or swapping its item is still allowed.

</details>

<details>

<summary><code>prevent-natural-decay</code></summary>

Stop the world slowly changing a chamber on its own: leaves dropping, grass and sculk spreading, ice and snow melting, crops and trees growing, sponges drying. **Default:** `false`, because a reset puts it back anyway. Turn on for a build that must stay exactly as placed between resets.

</details>

<details>

<summary><code>worldguard-integration</code> (1.5.9+)</summary>

If WorldGuard is installed, respect its regions. **Default:** `true`. Where a WorldGuard region grants a player build rights inside a chamber (membership, a `build` flag allow, or WG bypass), BTC skips its own block-break, block-place, and container protection there. Elsewhere BTC protection applies as normal. Set `false` to ignore WorldGuard.

</details>

<details>

<summary><code>residence-integration</code> / <code>lands-integration</code> / <code>griefprevention-integration</code></summary>

If the matching land-claim plugin is installed, stop players claiming a registered chamber. **Default:** `true` for each. BTC cancels that plugin's claim-create and claim-expand when the area overlaps a chamber. Each has its own bypass permission (`btc.bypass.residence` / `.lands` / `.griefprevention`, default: op). Servers without the plugin are unaffected. Existing claims are not removed; see the conflict scan below.

</details>

<details>

<summary><code>claim-conflict-scan-on-startup</code></summary>

On startup, check every registered chamber against existing claims from the enabled land-claim plugins and log a warning for each overlap (chamber name, location, claim owner). **Default:** `true`. Re-run any time with `/trial claims scan`. Set `false` to skip only the automatic startup scan.

</details>

<details>

<summary><code>block-wild-vault-placement</code> (1.5.7+)</summary>

Cancel placing working VAULT blocks outside registered chambers. **Default:** `true`. A wild vault is a permanent vanilla loot dispenser the plugin cannot manage. Players with `btc.bypass.vaultplace` (default: op) can always place them.

</details>

<details>

<summary><code>auto-pause-on-destruction</code> / <code>auto-pause-threshold</code></summary>

If enough vaults or trial spawners inside a chamber are destroyed, automatically pause the chamber: its database record stays, but resets, protection, tracking, and vault interactions are suspended. Resume with `/trial resume <name>` or the GUI. **Defaults:** `auto-pause-on-destruction: false`, `auto-pause-threshold: 6`.

`auto-pause-threshold` is the combined vault + trial spawner destruction count that triggers the pause (minimum effective value `1`). The counter resets on every pause, resume, or chamber reset.

- `1` to `2`: catches individual mischief.
- `6` (default): targets deliberate demolition.
- `10+`: only near-complete destruction.

Intended for hardcore or anarchy servers where `prevent-block-break` is deliberately off. Redundant when protection is on.

</details>

***

## Trial Key Settings

```yaml
trial-keys:
  validate-key-type: true
```

<details>

<summary><code>validate-key-type</code></summary>

Enforce that normal keys only open normal vaults and ominous keys only open ominous vaults. **Default:** `true`.

</details>

{% hint style="info" %}
BTC does not provide trial-key dupe protection. Use a dedicated plugin such as [AntiDupePro](https://modrinth.com/plugin/AntiDupePro).
{% endhint %}

***

## Reset Settings

What a reset cleans up, and trial spawner rest times.

```yaml
reset:
  clear-ground-items: true
  remove-spawner-mobs: true
  remove-non-chamber-mobs: false
  clear-added-blocks: true
  reset-trial-spawners: true
  reset-ominous-spawners: true
  clear-trial-omen-effect: true
  reset-vault-cooldowns: true
  spawner-cooldown-minutes: -1
  spawner-cooldown-overrides-presets: false
  wild-spawner-cooldown-minutes: -1
  spawner-key-drop-owner-grace-seconds: 30
```

<details>

<summary><code>clear-ground-items</code></summary>

Delete dropped items inside the chamber on reset. **Default:** `true`.

</details>

<details>

<summary><code>remove-spawner-mobs</code></summary>

Kill mobs spawned by trial spawners during the reset. **Default:** `true`.

</details>

<details>

<summary><code>remove-non-chamber-mobs</code></summary>

Kill all mobs in the chamber, including ones not from spawners (such as player pets). **Default:** `false`.

</details>

<details>

<summary><code>clear-added-blocks</code></summary>

Snapshots skip air, so on their own they cannot undo blocks a player added into empty cells (lava, cobble, etc.). **Default:** `true`. When `true`, reset also clears any block inside the chamber that is not part of the snapshot back to air. Set `false` only if you deliberately let players build inside chambers.

</details>

<details>

<summary><code>reset-trial-spawners</code></summary>

Reset each trial spawner's internal record of which players completed it. **Default:** `true`. Required for trial keys to drop again after a reset. With it `false`, spawners permanently remember who completed them and stop giving keys (not recommended).

</details>

<details>

<summary><code>reset-ominous-spawners</code></summary>

Convert ominous trial spawners back to normal during the reset. **Default:** `true`. Set `false` to keep ominous spawners ominous.

</details>

<details>

<summary><code>clear-trial-omen-effect</code> (1.5.19+)</summary>

On reset, remove Trial Omen and Bad Omen from players who were inside the chamber, so leftover omen does not carry into the next cycle. **Default:** `true`.

</details>

<details>

<summary><code>reset-vault-cooldowns</code></summary>

Forget who has opened each vault when the chamber resets, so everyone can use them again. **Default:** `true`. With it off, a vault someone opened stays shut for them forever; with `vaults.loot-mode: SHARED` and this off, a claimed vault never reopens for anybody.

</details>

<details>

<summary><code>spawner-cooldown-minutes</code></summary>

How long a trial spawner rests after its wave is beaten, before it can be used again. **Default:** `-1`. Range: `0` or more minutes, or the sentinel `-1`.

- `-1`: leave it as Minecraft has it (30 minutes).
- `0`: no rest, usable again straight away.
- any other number: that many minutes.

Per-chamber override available via the GUI (Chamber Settings, then Spawner Cooldown) or database.

Does not apply to spawners placed from a preset (`spawner_presets.yml`) that sets its own `target-cooldown-length`, unless `spawner-cooldown-overrides-presets` is `true`. Note the example preset's `36000` ticks is 30 minutes, the same as vanilla, so a preset can look identical to vanilla while ignoring this setting.

</details>

<details>

<summary><code>spawner-cooldown-overrides-presets</code></summary>

Decides who wins when a preset spawner states its own rest time and you also have a server-wide rest time set. **Default:** `false`.

- `false`: the preset's own `target-cooldown-length` wins for spawners placed from it. Presets without a time follow the server-wide setting.
- `true`: the server-wide value (or a chamber's override) applies to every spawner, presets included.

</details>

<details>

<summary><code>wild-spawner-cooldown-minutes</code></summary>

The same rest time, for trial spawners out in the world rather than in a registered chamber. **Default:** `-1` (same value meanings as `spawner-cooldown-minutes`). The preset rule applies here too.

When this is not `-1`, wave tracking and boss bars also work in wild Trial Chambers.

</details>

<details>

<summary><code>spawner-key-drop-owner-grace-seconds</code> (1.3.0+)</summary>

When a wave is driven by a non-vanilla mob provider (e.g. MythicMobs), vanilla cannot issue trial keys, so BTC drops them itself: one per participating player, above the spawner. **Default:** `30`. This is how long the drops stay owner-locked before anyone can pick them up. `0` = owner-locked until they despawn. `tcp.bypass.droplock` can always pick up.

</details>

***

## Performance Settings

```yaml
performance:
  cache-chamber-lookups: true
  cache-duration-seconds: 300
  time-tracking-interval: 300
```

<details>

<summary><code>cache-chamber-lookups</code></summary>

Remember which chamber a block belongs to instead of recalculating each time. **Default:** `true`. Large performance gain; only disable for debugging.

</details>

<details>

<summary><code>cache-duration-seconds</code></summary>

How long a remembered lookup is kept, in seconds. **Default:** `300` (5 minutes). Higher performs better but changes take longer to take effect.

</details>

<details>

<summary><code>time-tracking-interval</code></summary>

How often, in seconds, "time spent in chamber" stats are written to the database. **Default:** `300` (5 minutes). More frequent means more accurate stats but more database writes.

</details>

{% hint style="info" %}
**Removed in 1.5.12:** `async-database-operations` and `use-folialib` were no-op toggles (database work is always asynchronous, Folia is auto-detected). Leftover entries in an existing config are ignored.
{% endhint %}

***

## Statistics Settings

```yaml
statistics:
  enabled: true
  track-time-spent: true
  track-chamber-completion: true
  top-players-count: 10
```

| Setting | Effect | Default |
| ------- | ------ | ------- |
| `enabled` | Track player stats (vaults opened, chambers completed, time spent). Required for leaderboards. | `true` |
| `track-time-spent` | Track how long players spend inside chambers. | `true` |
| `track-chamber-completion` | Credit a "chamber completed" to every participant when a chamber is fully cleared. Drives the chambers leaderboard and the `%btc_chambers_completed%` / `%btc_top_chambers_*%` placeholders. | `true` |
| `top-players-count` | How many players a leaderboard shows. | `10` |

***

## Loot Settings

```yaml
loot:
  apply-luck-effect: false
  max-pools-per-table: 5
```

<details>

<summary><code>apply-luck-effect</code></summary>

Let the LUCK effect add loot rolls. **Default:** `false`. When on, each point of LUCK adds +1 roll to each loot pool (weighted items only; guaranteed items are unaffected). Counts both potion LUCK (potions, beacons, suspicious stew) and item-attribute LUCK from armour or items.

**Example** with `min-rolls: 3`, `max-rolls: 5`:

- No LUCK: 3-5 items.
- LUCK I: 4-6 items.
- LUCK II: 5-7 items.

Test with your loot tables so it does not break your economy.

</details>

<details>

<summary><code>max-pools-per-table</code></summary>

Maximum pools per loot table in the [multi-pool format](loot.yml.md#multi-pool-format). **Default:** `5`. Pools beyond this are ignored at load time. The GUI limits pool editing to this number.

</details>

***

## Spawner Wave System

Tracks trial spawner waves with a boss bar and completion messages.

```yaml
spawner-waves:
  enabled: true
  show-boss-bar: true
  detection-radius: 20
  remove-distance: 32
  award-stats: true
  completion-message: true
  prevent-infighting: true
  glow-active-spawners: false
  glow-color-normal: "#FFFF55"
  glow-color-ominous: "#A020F0"
  glow-mode: "wave-active"
```

<details>

<summary><code>enabled</code></summary>

Enable wave progress tracking for trial spawners. **Default:** `true`.

</details>

<details>

<summary><code>show-boss-bar</code></summary>

Show a boss bar with wave progress (mobs killed / total) to nearby players. **Default:** `true`. Normal spawners show yellow, ominous purple.

</details>

<details>

<summary><code>detection-radius</code></summary>

How far, in blocks, a player can be from a trial spawner and still see the boss bar and count as a participant. **Default:** `20`.

</details>

<details>

<summary><code>remove-distance</code> (1.2.26+)</summary>

Distance, in blocks, at which a player is removed from a spawner's boss bar. **Default:** `32`. Should be larger than `detection-radius` so the bar does not flicker at the edge.

</details>

<details>

<summary><code>award-stats</code></summary>

Count wave mob kills in player statistics. **Default:** `true`.

</details>

<details>

<summary><code>completion-message</code></summary>

Send a chat message with kill count and duration when a wave is complete. **Default:** `true`.

</details>

<details>

<summary><code>prevent-infighting</code></summary>

Stop wave mobs fighting each other. **Default:** `true`. In vanilla a stray skeleton arrow can start mob-vs-mob brawls that complete the wave without the player. When `true`, friendly fire and target-locking strictly between two wave mobs is suppressed. Player-vs-mob combat is never affected.

</details>

<details>

<summary><code>glow-active-spawners</code> / <code>glow-color-normal</code> / <code>glow-color-ominous</code></summary>

Draw a glowing outline around active trial spawners, visible through walls, to help players find them in a big chamber. **Defaults:** `glow-active-spawners: false`, colours `#FFFF55` (yellow, normal) and `#A020F0` (purple, ominous), as hex RGB. Minecraft only draws an outline in one of its own sixteen colours, so the colour you set is matched to the closest of those. The outline is an invisible, invulnerable marker entity that cannot be hit or farmed, removed when the wave completes or the chamber resets.

Update to a recent build before enabling; older builds did not render the outline correctly.

</details>

<details>

<summary><code>glow-mode</code> (1.5.4+)</summary>

Which spawners glow when `glow-active-spawners` is on. **Default:** `wave-active`.

| Value | Behaviour |
| ----- | --------- |
| `wave-active` | Only the spawner whose wave is currently running glows. |
| `chamber-remaining` | When any wave starts, every uncleared spawner in that chamber glows until its own wave completes. Solves "which spawner did I miss?" in large chambers. |

</details>

***

## Spectator Mode

Lets players spectate a chamber after dying in it.

```yaml
spectator-mode:
  enabled: true
  offer-timeout: 30
  restrict-to-chamber: true
  boundary-buffer: 10
  allow-solo-spectate: false
```

| Setting | Effect | Default |
| ------- | ------ | ------- |
| `enabled` | Offer spectating to a player who dies in a chamber while others are inside. | `true` |
| `offer-timeout` | Seconds the offer lasts. The player types "spectate" or "no" in chat. | `30` |
| `restrict-to-chamber` | Keep spectators within chamber bounds. | `true` |
| `boundary-buffer` | Extra blocks outside the boundary spectators may still fly. | `10` |
| `allow-solo-spectate` | Allow spectating a chamber with nobody else inside. | `false` |

***

## Auto-Discovery of Natural Trial Chambers

_Added in 1.2.25, opt-in._ Automatically registers naturally-generated Trial Chambers the first time their chunks load. No `/trial generate` needed. Chambers are named like `auto_world_123_456`. On plugin enable, a startup sweep also scans already-loaded Overworld chunks.

To keep discovery (and BTC) out of a world, list it under `global.excluded-worlds`.

```yaml
discovery:
  enabled: false
  use-structure-bounds: true
  structure-max-volume: 15000000
  max-radius-xz: 60
  max-radius-y: 45
  min-vaults-plus-spawners: 2
  max-center-y: 10
  auto-snapshot: false
  notify-ops: true
  cooldown-seconds: 300
  pending-retry-seconds: 30
  merge-distance-blocks: 250
  max-merged-volume: 1500000
  structure-merge-distance-blocks: 16
  expand-on-discover: true
  expand-delay-seconds: 10
  expand-force-load: false
  snapshot-reminder:
    enabled: true
    on-join: true
    interval-minutes: 30
```

<details>

<summary><code>enabled</code></summary>

Master switch. **Default:** `false`. Off by default because old worlds with player-built tuff or copper structures can look like chambers to the detector. Turn on for a freshly generated world, or once you have checked the guards below are tight enough.

</details>

<details>

<summary><code>use-structure-bounds</code> / <code>structure-max-volume</code> (v1.7.0)</summary>

When a seed block sits inside a naturally-generated `minecraft:trial_chambers` structure, ask the game for its exact bounds instead of scanning blocks. **Defaults:** `true`, `structure-max-volume: 15000000` (`-1` = uncapped). This registers the chamber at its full correct size in one pass and handles datapack-enlarged chambers. Structure-bounds discoveries skip the size and depth checks and the auto-expand pass.

Player-built chambers are not generated structures, so they still use the block scan.

For very large datapack chambers, raise `global.blocks-per-tick` and consider `global.use-fawe` for faster resets. Snapshots and resets stream to and from disk (since 2.0.9), so memory stays flat regardless of chamber size.

</details>

<details>

<summary><code>max-radius-xz</code> / <code>max-radius-y</code></summary>

Caps on the block scan, in blocks. **Defaults:** `60`, `45`. Prevents a runaway scan if the match rule catches something larger than a vanilla chamber. Lower if you see over-registration.

</details>

<details>

<summary><code>min-vaults-plus-spawners</code></summary>

A candidate region must contain at least this many vaults + trial spawners combined, or it is rejected. **Default:** `2`. Stops single-vault structures registering as chambers.

</details>

<details>

<summary><code>max-center-y</code></summary>

Reject a candidate whose centre Y is above this. **Default:** `10`. Trial chambers generate deep underground; anything near the surface is almost certainly a player build. Range enforced -256 to 320.

</details>

<details>

<summary><code>auto-snapshot</code></summary>

Take a snapshot when a chamber is auto-registered. **Default:** `false`, because snapshotting a large chamber costs a few seconds each time, which adds up during world pregeneration. **Enable this if you want auto-discovered chambers to be resettable.** Without a snapshot they still get per-player loot and protection, but resets cannot rebuild broken blocks.

</details>

<details>

<summary><code>notify-ops</code></summary>

Broadcast a message to holders of `btc.discovery.notify` when a chamber is registered. **Default:** `true`.

</details>

<details>

<summary><code>cooldown-seconds</code> / <code>pending-retry-seconds</code></summary>

Internal debounce and retry timers, in seconds. **Defaults:** `300`, `30`. `cooldown-seconds` stops re-scanning the same area right after a discovery attempt; `pending-retry-seconds` is how long the plugin waits for neighbouring chunks to load before finalising a partly-loaded chamber. Defaults are fine for almost everyone.

</details>

<details>

<summary><code>merge-distance-blocks</code> / <code>max-merged-volume</code> (1.4.1+)</summary>

When a newly discovered region sits within `merge-distance-blocks` of an already-registered chamber, the two are merged instead of registering a duplicate (vanilla chambers often load in pieces as chunks stream in). **Defaults:** `250`, `max-merged-volume: 1500000` blocks. A region that would push the merged box past `max-merged-volume` stays separate. Set `merge-distance-blocks` to `-1` to disable merging.

A merge re-captures the snapshot automatically (since 1.5.6). If a console warning says a post-merge snapshot failed, run `/trial snapshot create <chamber>` before the next reset.

</details>

<details>

<summary><code>structure-merge-distance-blocks</code> (v2.0.9)</summary>

The merge distance used when a discovery came from the game's exact structure bounds. **Default:** `16`. Some datapacks build one chamber out of several trial-chamber structures placed next to each other; without merging, each piece registers separately and their resets fight over shared walls. A newly-found structure within this many blocks (edge-to-edge) of an existing chamber grows that chamber instead. Normal worlds place separate chambers hundreds of blocks apart, so `16` is safe. Set `0` to merge only truly overlapping boxes, `-1` to never merge structure discoveries. Merged size is capped by `structure-max-volume`.

Already have duplicates from an earlier version? Delete them with `/trial delete <name>`, walk through the chamber once, and it re-registers as one.

</details>

<details>

<summary><code>expand-on-discover</code> / <code>expand-delay-seconds</code> / <code>expand-force-load</code> (1.6.3+)</summary>

The block scan can be clipped if neighbouring chunks were still loading, leaving part of a chamber outside the registered region. **Defaults:** `expand-on-discover: true`, `expand-delay-seconds: 10`, `expand-force-load: false`.

With `expand-on-discover` on, one expand pass runs `expand-delay-seconds` after registering a chamber: a re-scan from all of its vaults that grows the bounds to cover what the first pass missed, then re-snapshots. It is best-effort; a wing nobody has visited may still be missed. Repair on demand by standing in the chamber and running `/trial scan add <chamber>`, or using the GUI's Travel & Expand button.

`expand-force-load: true` (opt-in, Paper only) lets both the auto pass and `/trial scan add` load unvisited chunks on demand, at the cost of a disk-read spike.

</details>

<details>

<summary><code>snapshot-reminder</code> (1.5.1+)</summary>

Auto-discovered chambers without a snapshot cannot be reset, so this pings holders of `btc.admin.snapshot`. **Defaults:** `enabled: true`, `on-join: true` (ping an admin when they log in), `interval-minutes: 30` (periodic console and chat summary; `0` disables only the periodic ping).

{% hint style="info" %}
**Plug-and-play:** to have the plugin work on every chamber with no commands, set:

```yaml
discovery:
  enabled: true
  auto-snapshot: true
```

Then move around the world and chambers register themselves as their chunks load.
{% endhint %}

</details>

***

## Per-Player Chamber Container Loot

```yaml
chests:
  per-player-loot: false
```

_(Added in 1.5.7, opt-in. Reworked in 1.6.3.)_ Lootr-style container loot. **Default:** `false`. When enabled, every player who opens a chest, trapped chest, barrel, dispenser, or dropper that has a loot table inside a registered chamber gets their own freshly-rolled copy.

How it behaves:

- Each player rolls their own loot independently from the container's vanilla loot table; two players can get different items.
- The real container is never opened, so every chamber reset clears the per-player copies and the next open rolls fresh again ("vanilla, but repeatable").
- Double chests share one copy (keyed by the left half). Dispensers and droppers use their 9 slots.
- Hopper automation into or out of chamber containers is blocked while this is on.
- Containers placed by players inside a chamber keep vanilla behaviour.
- Decorated pots are excluded (their loot is break-based and already renews on reset).

**Customising a container's loot:** GUI only, no in-world editing. Open `/trial menu <chamber>`, then Container Loot:

- **Scan Containers** lists every container so you can pick one (listing only, does not freeze loot).
- **Click a container to edit** its contents. Saving makes it an override: every player then gets a copy of that loot (still per-player, re-cloned each reset). Overrides persist across resets.
- **Revert to vanilla** with shift-left-click, or `/trial container resetone <chamber> <#>`.

Toggle the whole feature from the same screen or `/trial container` on the command line.

{% hint style="warning" %}
Turn this on only after your chambers are registered. Containers inside chamber bounds that players used as storage will start serving per-player loot. Player-placed containers from before 1.5.7 cannot be told apart from chamber loot containers.
{% endhint %}

***

## Setup Tour

```yaml
setup:
  reminder:
    enabled: true
```

_(Added in 1.6.0.)_ Controls the reminder for the opt-in `/trial setup` tour. **Default:** `enabled: true`. When on, an operator with `tcp.admin.setup` who has not run the tour gets a one-line nudge on join, at most once a week and three times total, which stops for good once they run `/trial setup`. Set `false` to never show it. The tour is always available on demand; BTC runs fine on defaults without it.

***

## Updates

_(Added in 1.8.0.)_ BetterTrialChambers can check for new releases and, if you let it, download and install them. Checked on Modrinth first, then GitHub Releases. Check state is cached under `plugins/BetterTrialChambers/pluginpulse/`.

```yaml
update:
  mode: notify
  check-interval-hours: 6
  require-hash: true
  allow-hot-reload: false
```

<details>

<summary><code>mode</code></summary>

How far the updater goes on its own. **Default:** `notify`. Each mode includes the ones above it.

| Value | Behaviour |
| ----- | --------- |
| `check-only` | Check silently; results only via `/trial update status`. |
| `notify` | Also announce updates in the console and to admins (`btc.admin`) on join. Nothing is written to disk. |
| `download` | Also allow `/trial update download` to fetch, verify, and stage a release for the next restart. |
| `auto-stage` | Automatically download, verify, and stage new releases as they appear. |

</details>

<details>

<summary><code>check-interval-hours</code></summary>

Hours between automatic update checks. **Default:** `6`, minimum `1`.

</details>

<details>

<summary><code>require-hash</code></summary>

Refuse to install a downloaded jar unless its checksum matches the one the source published. **Default:** `true`. Leave on unless a release is genuinely missing hashes and you accept the risk.

</details>

<details>

<summary><code>allow-hot-reload</code></summary>

Let `/trial update apply` swap a staged update into place without a server restart. **Default:** `false`. If the new version fails to load, BTC rolls back to the backup it made before staging. Refused on Folia and while other plugins depend on BTC. Restarting is always safer.

</details>

See [Commands](../reference/commands.md) for the `/trial update` subcommands.

***

## Metrics

```yaml
metrics:
  enabled: true
  error-reporting: true
```

_(Added in 1.5.7. Provider switched from bStats to [FastStats](https://faststats.dev) in 2.0.5.)_ Anonymous aggregate usage metrics. No player data is ever collected. What is sent: server software and Minecraft version, database backend, which features are on (auto-discovery, per-player container loot, spawner glow mode, custom mob provider, reset interval band), rough chamber counts and sizes, how many chambers lack a snapshot, which other plugins BTC can hook, which premium modules are installed, and how many resets, vault opens, and chamber clears happened since the last report.

- `enabled` (**default `true`**): set `false` to stop this plugin sending anything. To disable metrics for every FastStats plugin on the server, set `enabled=false` in `plugins/faststats/config.properties` (the replacement for the old `plugins/bStats/config.yml`).
- Nothing is sent on the first run: FastStats writes the opt-out file and waits for the next restart, so you get a chance to opt out first.

<details>

<summary><code>error-reporting</code></summary>

Automatically report BetterTrialChambers' own errors so bugs get fixed without you filing a ticket. **Default:** `true`. Set to `false` to turn it off.

- Only this plugin's errors are captured, never another plugin's.
- Before anything is sent, IP addresses, file paths containing your username, database credentials, and player UUIDs are replaced with placeholders. Player names, chat, inventories, coordinates, and world data are never included.
- Each report also carries the plugin version, your Minecraft version, your database type (sqlite or mysql), whether the server runs Folia, a rough chamber-count band, and which BTC operation was running, so a fix can target the right setup.
- Routine shutdown and reload cancellations are filtered out.

To turn error reports off across every FastStats plugin, set `submitErrors=false` in `plugins/faststats/config.properties`.

</details>

***

## Debug Mode

```yaml
debug:
  verbose-logging: false
  skip-messages-schema-check: false
```

<details>

<summary><code>verbose-logging</code></summary>

Log detailed decisions to the console, including why a protection action was or was not taken. **Default:** `false`. If a toggle seems not to work, turn this on and watch the log; the usual cause is the player being exempt through a `tcp.bypass.*` permission (operators have all of them, so test with a non-op account). Very noisy; leave off in production. When on, a startup banner confirms it loaded.

</details>

<details>

<summary><code>skip-messages-schema-check</code></summary>

Skip the startup check that compares your `messages.yml` against the bundled defaults. **Default:** `false`. Leave `false` unless the warning is noisy and you have a reason to ignore it; missing keys make GUI tooltips and chat render as literal `<missing: key.name>` text.

</details>

***

## Quick Configs for Common Setups

<details>

<summary>Casual survival server</summary>

```yaml
global:
  default-reset-interval: 86400   # daily resets
vaults:
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 12
protection:
  allow-pvp: false
```

</details>

<details>

<summary>Competitive / PvP server</summary>

```yaml
global:
  default-reset-interval: 43200   # twice daily
vaults:
  normal-cooldown-hours: 12
  ominous-cooldown-hours: 24
protection:
  allow-pvp: true
  prevent-block-place: true
```

</details>

<details>

<summary>High-activity server</summary>

```yaml
global:
  default-reset-interval: 21600   # every 6 hours
  blocks-per-tick: 1000
vaults:
  normal-cooldown-hours: 3
  ominous-cooldown-hours: 6
performance:
  cache-duration-seconds: 600
```

</details>

<details>

<summary>Roleplay / lore server</summary>

```yaml
global:
  default-reset-interval: 604800   # weekly resets
vaults:
  normal-cooldown-hours: 168
  show-cooldown-particles: false
  play-sound-on-open: false
```

</details>

***

## Applying Changes

After editing `config.yml`, run `/trial reload`. Most settings apply immediately. Chamber reset intervals only affect the next reset, not a chamber mid-cycle. Database changes need a full server restart.

***

## Common Questions

**Can different chambers have different reset intervals?** Yes. `default-reset-interval` is only the default; override per-chamber from the GUI or database.

**What happens if I change cooldowns while players have active cooldowns?** Existing cooldowns are not changed retroactively. New values apply to future vault interactions.

**Can I disable statistics for performance?** Yes, set `statistics.enabled: false`. You lose leaderboards.

**MySQL or SQLite?** SQLite unless you run multiple servers that need shared data.

***

{% content-ref url="loot.yml.md" %}
[loot.yml.md](loot.yml.md)
{% endcontent-ref %}

{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}

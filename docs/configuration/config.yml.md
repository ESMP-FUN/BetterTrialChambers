# config.yml

Your `config.yml` is the control panel for TrialChamberPro. It's where you decide how chambers behave, when they reset, and how much protection you want. Let's break it down section by section.

{% hint style="info" %}
**Location:** `plugins/TrialChamberPro/config.yml`

After making changes, reload with `/tcp reload`
{% endhint %}

{% hint style="success" %}
**Your config auto-updates (1.5.19+).** When you update TrialChamberPro, any new options added in the release are merged into your existing `config.yml` on startup — **with their comments** — while your current values are left untouched. The previous file is saved as `config.yml.bak` first. So you'll always see new settings without having to delete and regenerate the file. (`messages.yml` updates the same way.)
{% endhint %}

***

## Reading config paths in this guide

This guide refers to settings as **dotted paths** like `global.reset-complete-alert` or `vaults.normal-cooldown-hours`. That's the _display form_ — it's how the value is looked up from code, not how it's written in YAML. The actual `config.yml` is **nested**:

<table data-header-hidden><thead><tr><th></th><th></th></tr></thead><tbody><tr><td>Documented path</td><td>What it looks like in <code>config.yml</code></td></tr><tr><td><code>global.reset-complete-alert: false</code></td><td><pre><code>global:
  reset-complete-alert: false
</code></pre></td></tr><tr><td><code>vaults.normal-cooldown-hours: 1</code></td><td><pre><code>vaults:
  normal-cooldown-hours: 1
</code></pre></td></tr><tr><td><code>protection.prevent-block-break: true</code></td><td><pre><code>protection:
  prevent-block-break: true
</code></pre></td></tr></tbody></table>

So when a setting is documented as `foo.bar.baz`, add `baz` **inside** the existing `foo:` → `bar:` block. Don't paste `foo.bar.baz: value` as a flat line at the top level — YAML treats the dots as part of the key name, the plugin's `getBoolean("foo.bar.baz")` lookup never resolves it, and the change silently has no effect.

{% hint style="warning" %}
If you edit `config.yml` and a setting "doesn't seem to do anything," the first thing to check is whether you wrote it as a flat dotted key (`global.something: false`) instead of nesting it under the right section (`global:` → `something: false`).
{% endhint %}

***

## Database Settings

```yaml
database:
  type: SQLITE # SQLITE or MYSQL
  host: localhost
  port: 3306
  database: trialchamberpro
  username: root
  password: ""
  pool-size: 10
```

### `type`

**Options:** `SQLITE` or `MYSQL` **Default:** `SQLITE`

SQLite is perfect for single servers—no setup required, everything in one file. MySQL is for networks where multiple servers need to share chamber data.

{% hint style="success" %}
**Stick with SQLite unless** you're running a network with BungeeCord/Velocity and need multiple servers to share chambers.
{% endhint %}

### MySQL Options (only used when `type: MYSQL`)

| Setting     | Description                                       |
| ----------- | ------------------------------------------------- |
| `host`      | Database server address                           |
| `port`      | Usually `3306`                                    |
| `database`  | Database name                                     |
| `username`  | Database user                                     |
| `password`  | User's password                                   |
| `pool-size` | Connection pool size (10 is fine for most setups) |

***

## Global Chamber Settings

```yaml
global:
  default-reset-interval: 172800
  reset-warning-times: [300, 60, 30]
  reset-complete-alert: true
  teleport-players-on-reset: true
  teleport-location: EXIT_POINT
  async-block-placement: true
  blocks-per-tick: 500
  auto-snapshot-on-register: true
```

<details>

<summary><code>default-reset-interval</code></summary>

**Default:** `172800` (48 hours) **Unit:** Seconds

How long before chambers automatically reset. This is the default for ALL chambers unless overridden per-chamber.

**Values:**

* `0` or negative = **Disabled** (no automatic resets)
* Daily: `86400`
* Twice daily: `43200`
* Weekly: `604800`

{% hint style="info" %}
**Disable automatic resets:** Set to `0` to disable automatic resets entirely. Chambers will only reset when manually triggered via `/tcp reset <chamber>` or the GUI.
{% endhint %}

</details>

<details>

<summary><code>reset-warning-times</code></summary>

**Default:** `[300, 60, 30]` **Unit:** Seconds before reset

Players inside a chamber get warnings at these intervals. Default sends warnings at 5 minutes, 1 minute, and 30 seconds before reset.

Remove entries to reduce spam:

```yaml
reset-warning-times: [60] # Only warn 1 minute before
```

</details>

<details>

<summary><code>teleport-players-on-reset</code></summary>

**Default:** `true`

Kick players out when the chamber resets? If `false`, players stay inside (probably not what you want—spawners respawn, blocks reset, they might suffocate).

</details>

<details>

<summary><code>teleport-location</code></summary>

**Options:** `EXIT_POINT`, `OUTSIDE_BOUNDS`, `WORLD_SPAWN` **Default:** `EXIT_POINT`

Where players go when kicked out:

* **EXIT\_POINT:** Your `/tcp setexit` location (recommended)
* **OUTSIDE\_BOUNDS:** Just outside the chamber boundary
* **WORLD\_SPAWN:** Server spawn point

</details>

<details>

<summary><code>async-block-placement</code></summary>

**Default:** `true`

Place blocks asynchronously during resets? Keeps the server smooth during big chamber resets. Only turn this off if you're having issues.

</details>

<details>

<summary><code>blocks-per-tick</code></summary>

**Default:** `500`

How many blocks to place per tick during resets. Higher = faster resets but more lag. Lower = slower but smoother.

Adjust based on your server hardware:

* Beefy dedicated server: `1000+`
* Shared hosting: `250-500`

</details>

<details>

<summary><code>auto-snapshot-on-register</code></summary>

**Default:** `true`

Automatically create a snapshot when registering a new chamber? Super convenient, but uses disk space. You can disable this if you want manual control.

</details>

<details>

<summary><code>spawner-cooldown-minutes</code></summary>

**Default:** `-1` (vanilla behavior) **Unit:** Minutes

> **Note:** in `config.yml` this key lives under the **`reset:`** section (`reset.spawner-cooldown-minutes`), not `global:`.

Control how long trial spawners stay in cooldown after being completed. This affects when spawners can be reactivated after players defeat all mobs.

**Values:**

* `-1` = Use vanilla default (30 minutes / 36,000 ticks)
* `0` = No cooldown (spawners reactivate immediately when players approach)
* `1-60` = Custom cooldown in minutes

**Use Cases:**

* **Quick farming:** Set to `0` for instant reactivation—great for mob farms or high-activity servers
* **Balanced gameplay:** Set to `5-15` minutes for faster resets than vanilla
* **Vanilla experience:** Keep at `-1` for authentic Trial Chamber timing

**Per-Chamber Override:** You can set different cooldowns for individual chambers via the GUI (Chamber Settings → Spawner Cooldown) or database. Per-chamber settings override this global value.

</details>

<details>

<summary><code>wild-spawner-cooldown-minutes</code></summary>

**Default:** `-1` (vanilla behavior) **Unit:** Minutes

> **Note:** in `config.yml` this key lives under the **`reset:`** section (`reset.wild-spawner-cooldown-minutes`), not `global:`.

Control cooldown for trial spawners **outside** of registered chambers (wild/unregistered Trial Chambers). This is a server-wide setting that affects all spawners not managed by TrialChamberPro.

**Values:**

* `-1` = Use vanilla default (30 minutes)
* `0` = No cooldown (spawners reactivate immediately)
* `1-60` = Custom cooldown in minutes

**Use Cases:**

* **Server-wide fast farming:** Set to `0` for all wild spawners to reactivate instantly
* **Consistent experience:** Match wild spawner behavior to your chamber settings
* **Vanilla purists:** Keep at `-1` to leave unregistered chambers untouched

**Bonus Feature:** When this setting is enabled (not -1), spawner wave tracking (boss bars) will also work in wild Trial Chambers, giving players progress feedback even in unregistered chambers!

</details>

***

### Reset throttle, confirmation & FAWE

These `global:` keys control how automatic resets are scheduled and placed — important on servers with many (e.g. auto-discovered) chambers that come due at the same time.

```yaml
global:
  max-concurrent-resets: 1        # how many chambers may reset simultaneously
  reset-stagger-seconds: 5        # minimum gap between one reset finishing and the next starting
  reset-require-confirmation: false  # park due chambers; an operator confirms them
  use-fawe: false                 # place blocks via FastAsyncWorldEdit on scheduled resets
  suppress-trial-spawner-spam: true  # mute vanilla "Trial Spawner ... has no detected players"
```

<details>

<summary><code>max-concurrent-resets</code> / <code>reset-stagger-seconds</code></summary>

Stop a wave of due chambers from all restoring at once and cratering TPS. Confirmed/queued resets respect these too.

</details>

<details>

<summary><code>reset-require-confirmation</code></summary>

When `true`, a chamber that becomes due is **not** reset automatically; it's queued and online admins are notified. List with `/tcp reset pending`, fire with `/tcp reset confirm <chamber|all>` (they then run staggered).

</details>

<details>

<summary><code>use-fawe</code></summary>

When `true` and FastAsyncWorldEdit is installed, **scheduled** resets place blocks through one FAWE EditSession to smooth out lag on large chambers. **Paper-only** (ignored on Folia); manual `/tcp reset` keeps the batched path so WorldEdit `//undo` still works; falls back automatically if FAWE is missing.

</details>

<details>

<summary><code>suppress-trial-spawner-spam</code></summary>

Mutes the vanilla console line `Trial Spawner at BlockPos{...} has no detected players`. New occurrences are prevented by the reset fixes; this hides the line for chambers still broken until their next reset.

</details>

***

## Vault Settings

```yaml
vaults:
  per-player-loot: true
  normal-cooldown-hours: 24
  ominous-cooldown-hours: 48
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
```

### `per-player-loot`

**Default:** `true`

The big one! Each player gets their own loot and cooldowns. If `false`, vaults work like vanilla (one-time use, everyone shares).

{% hint style="warning" %}
**Setting this to false** removes per-player cooldowns. Vaults become first-come-first-served. Usually not what you want for managed chambers!
{% endhint %}

### `normal-cooldown-hours` / `ominous-cooldown-hours`

**Default:** `0` (permanent until reset)

How long before a player can loot the same vault again. Separate cooldowns for normal and ominous vaults.

**Values:**

* `0` (or negative) = Permanent lock until chamber reset (vanilla behavior)
* positive `N` = Timed cooldown — the vault reopens **N hours** after that player last opened it _(actually implemented since 1.5.12)_

When a timed cooldown is set and a player tries to reopen too early, they're shown the remaining time. The open timestamp is tracked per player in the database; a **chamber reset is always a full unlock** regardless of the timed cooldown.

{% hint style="info" %}
**v1.2.21+:** Permanent (non-timed) vault locks use Paper's native Vault API (`hasRewardedPlayer`/`addRewardedPlayer`) for tracking:

* Uses vanilla Minecraft's built-in player tracking
* Locks automatically clear when the chamber is restored from snapshot
* No database sync issues

Timed cooldowns layer a per-player timestamp (in `player_vaults`) on top of that flag.

**Note:** A timed cooldown only governs _reopening before a reset_ — the chamber's own reset interval still fully unlocks every vault when it fires. So a 6-hour cooldown on a chamber that resets every 48 hours lets a player loot the same vault up to \~8 times per cycle.
{% endhint %}

**Ideas:**

* Vanilla behavior: `0` (permanent until chamber reset)
* Short cooldowns: `1` or `6` hours (for active servers)
* Long cooldowns: `72` or `168` hours (weekly)
* Match chamber resets: `48` hours (synchronized gameplay)

### `reopen-cost-keys`

**Default:** `0` (disabled) _(added in 1.5.7)_

Key-to-reopen: when set to `N ≥ 1`, a player who has already opened a vault can open it **again** by paying `N` matching trial keys in total (a fresh open costs 1 key, so `1` = reopen at the same price, `2` = one extra key, and so on). Keys must be held in the main hand; when the player can't afford it, the locked message is replaced by a price hint.

`0` keeps the standard behaviour: opened vaults stay locked until the cooldown elapses or the chamber resets. Time-based cooldowns and key-to-reopen can coexist — reopening is simply an instant paid alternative to waiting.

{% hint style="warning" %}
**Balance note:** with farms supplying trial keys, low reopen costs effectively turn vaults into key-powered crates. `2`–`3` is a reasonable starting price on survival servers.
{% endhint %}

### `show-cooldown-particles`

**Default:** `true`

Show particles above vaults to indicate status? Super helpful for players to see what's available.

### `particles`

Visual effects shown above vaults:

* **normal-available:** Green sparkles = ready to open
* **normal-cooldown:** Gray smoke = on cooldown
* **ominous-available:** Soul flames = ominous vault ready
* **ominous-cooldown:** Soul particles = ominous on cooldown

See [Spigot's Particle enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html) for all options.

### `play-sound-on-open` / `sounds`

**Default:** `true`

Play sounds when vaults open or when someone tries to open during cooldown.

Change sounds if you want custom feedback. See [Spigot's Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html).

### `feedback`

**Default:** `mode: TEXT` _(added in 1.5.8)_

Chooses how a vault interaction is reported to the player.

* **`TEXT`** — the classic chat lines (`You opened a Normal Vault!`, `You need a Trial Key to open this vault!`, etc.). Unchanged from earlier versions.
* **`HOLOGRAM`** — replaces those chat lines with a floating **green ✔ / red ✘** above the vault, visible **only to the player who interacted**, plus a sound. A tick on a successful open; a cross on every failure (no key, wrong key type, on cooldown/locked, can't afford a reopen). Cooldown/success particles and vanilla advancements still fire — only the chat line and the open/error sound are swapped for the hologram and its sound.

```yaml
vaults:
  feedback:
    mode: HOLOGRAM
    hologram:
      duration-ticks: 30      # how long the ✔/✘ stays (20 ticks = 1 second)
      y-offset: 1.4           # height above the vault block
      scale: 1.5              # text size multiplier
      see-through: true       # render through blocks
      success-text: "&a✔"     # shown on a successful open
      fail-text: "&c✘"        # shown on any failure
    sounds:
      success: ENTITY_PILLAGER_CELEBRATE
      fail: ENTITY_PILLAGER_AMBIENT
```

{% hint style="info" %}
`ENTITY_PILLAGER_CELEBRATE` and `ENTITY_PILLAGER_AMBIENT` are _random-variant_ sound events — Minecraft picks one of the bundled `.ogg` clips each time, so an exact variant (e.g. "celebrate2") can't be pinned without a resource pack. Set `success` / `fail` to any value from [Spigot's Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html) (or a `namespace:path` key) to use your own.
{% endhint %}

***

## Protection Settings

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  prevent-container-access: false
  allow-pvp: true
  prevent-mob-griefing: true
  worldguard-integration: true
  residence-integration: true
  lands-integration: true
  griefprevention-integration: true
  claim-conflict-scan-on-startup: true
  message-cooldown-ms: 1500
  block-advanced-enchantments: false
  advanced-enchantments-allowlist: []
  advanced-enchantments-block-radius: 2
  prevent-teleport-into-chamber: false
  prevent-entry-without-permission: false
  auto-pause-on-destruction: false
  auto-pause-threshold: 6
```

### `enabled`

**Default:** `true`

Master toggle for chamber protection. Turn this off to rely entirely on WorldGuard or other plugins.

### `prevent-block-break` / `prevent-block-place`

**Default:** `true`

Stop players from breaking or placing blocks inside chambers. Prevents griefing and preserves your carefully-designed structures.

### `prevent-container-access`

**Default:** `false`

Block access to chests, barrels, hoppers, etc. Usually `false` because you WANT players opening decorated pots and chests for loot.

Set to `true` if you have admin chests inside chambers that shouldn't be touched.

### `allow-pvp`

**Default:** `true` _(implemented in 1.5.19)_

Allow player-vs-player combat inside registered chambers. When `false`, TCP cancels player-on-player damage — both melee and player-shot projectiles — inside a chamber, and tells the attacker. `true` keeps vanilla behaviour (PvP follows your world/server rules). Players with `tcp.bypass.protection` are exempt; mob damage and self-damage are never affected.

### `prevent-mob-griefing`

**Default:** `true`

Stop mobs from breaking blocks (creeper explosions, endermen picking up blocks, etc.). Highly recommended unless you want chaos.

### `worldguard-integration`

**Default:** `true` _(functional since 1.5.9)_

If WorldGuard is installed, **respect its regions**: when a WorldGuard region covers a spot inside a chamber and grants the player build rights — region membership, an explicit `build` flag allow, or WorldGuard bypass — TrialChamberPro yields and skips its own block-break / block-place / container-access protection there. This lets region owners and staff work inside chambers that overlap their regions without disabling TCP protection elsewhere. Where there is no WG region (or the player has no build rights), TCP protection applies as normal. Set to `false` to ignore WorldGuard entirely.

### `residence-integration` / `lands-integration` / `griefprevention-integration`

**Default:** `true` _(added in 1.5.15)_

If the matching land-claim plugin is installed, **stop players claiming a registered chamber**: TCP cancels that plugin's claim-create and claim-expand actions when the claimed area overlaps a chamber, and tells the player. This prevents someone fencing off a chamber and changing its claim flags to interfere with resets, loot, or protection. Each plugin has its own toggle and its own bypass permission (`tcp.bypass.residence` / `tcp.bypass.lands` / `tcp.bypass.griefprevention`, default op). Servers without the plugin are unaffected — the integration only activates when the plugin is present. Existing claims aren't removed; see the conflict scan below.

### `claim-conflict-scan-on-startup`

**Default:** `true` _(added in 1.5.15)_

On startup, check every registered chamber against existing claims from the enabled land-claim plugins and log a warning for each overlap — including the chamber name, its location, and the claim owner. This surfaces pre-existing conflicts (e.g. a chamber registered on top of a claim made earlier) so you can resolve them. Re-run the scan at any time with **`/tcp claims scan`**. Set to `false` to skip the automatic startup scan (the command still works).

### `message-cooldown-ms`

**Default:** `1500` _(added in 1.5.18)_

How long (in milliseconds) to wait before re-showing a "you can't break / place / access here" denial to the **same** player. A single action that affects many blocks at once — an AdvancedEnchantments _Blast Mining_ enchant, a vein miner, rapid clicking — used to print the message once per block and flood chat. The throttle collapses that to one line per window. Set `0` to disable throttling (every denied action messages again).

### `block-advanced-enchantments`

**Default:** `false` _(added in 1.5.18)_

If [AdvancedEnchantments](https://www.spigotmc.org/resources/76519/) is installed, its custom enchants (e.g. _Blast Mining_) break blocks through their own effect path that **ignores TCP's block-break cancel** — so they can bypass chamber protection. Turn this on to make TCP cancel AE enchant activations that affect a registered chamber, stopping the effect (no break, no spam). Players with `tcp.bypass.protection` are exempt, and enchants in `advanced-enchantments-allowlist` are still permitted. Off by default, so AE behaves normally until you opt in. Reflection-based — nothing happens on servers without AE.

Since **1.5.21** this also catches mining a chamber wall from **just outside** it (Blast Mining the wall, AoE reaching across the boundary), not only when the player stands inside — see [`advanced-enchantments-block-radius`](config.yml.md#advanced-enchantments-block-radius) to tune the reach.

{% hint style="info" %}
**Do I need this for vein miners / hotkey area-miners?** Usually **no**. The real question for _any_ mass-breaker is whether it fires a normal, cancellable block-break for each block it removes:

* **Hotkey-style area miners** (e.g. [VeinMiner](https://modrinth.com/plugin/veinminer)) simulate a player breaking each block — they fire a per-block break event, so TCP's standard block protection already cancels the ones inside a chamber. Nothing to enable; the only visible effect is the per-block denial, which `message-cooldown-ms` collapses to one line.
* **Enchantment / skill effect engines** (AdvancedEnchantments and similar) often break blocks through their own pipeline that _doesn't_ fire that event — those are what `block-advanced-enchantments` is for.

So: if a tool fires an ordinary block-break per block, it's covered automatically; if it has its own effect pipeline that skips block-break events, it needs an explicit guard like this one.
{% endhint %}

### `advanced-enchantments-allowlist`

**Default:** `[]` _(added in 1.5.18)_

Enchant names (lowercase, matching the AE enchant name) that stay **allowed** inside chambers even when `block-advanced-enchantments` is on. Use it to keep combat enchants working during a trial while still blocking block-breakers:

```yaml
advanced-enchantments-allowlist:
  - lifesteal
  - poisoned
```

### `advanced-enchantments-block-radius`

**Default:** `2` _(added in 1.5.21)_

How far (in blocks) an AE mining enchant's blast can reach — used to catch a player mining a chamber wall from **just outside** it. AE's event carries no block, so TCP ray-traces the block the player is looking at and checks a cube of that block **± this radius** against chamber bounds; if it touches a chamber, the enchant is blocked. The expansion is centred on the **mined block, not the player**.

Set it to your **largest** blast enchant's radius:

* `0` — block only when the mined block is _itself_ inside a chamber (no outside margin; zero over-blocking).
* `2` _(default)_ — covers a 5×5 area-miner.
* Higher — covers bigger area enchants, at the cost of also denying mining a little further out from chamber walls.

Note this only affects the thin shell of blocks just outside a chamber; normal mining elsewhere is never touched. (Capped at 16.)

### `prevent-teleport-into-chamber`

**Default:** `false` _(added in 1.5.20)_

Stop players **teleporting into** a registered chamber from outside it, so they can't skip the intended entrance. Because it hooks the teleport itself rather than specific commands, it catches every method — `/tpa`, `/tpahere`, `/home`, `/warp`, `/tp`, ender pearls, chorus fruit, plugin teleports — in one place. Walking in on foot is unaffected. Players with **`tcp.bypass.entry`** (default: op), spectators, and creative-mode players are exempt (the gamemode exemption also covers TCP's own spectator-mode entry teleport). Off by default.

### `prevent-entry-without-permission`

**Default:** `false` _(added in 1.5.20)_

Gate **walking into** a chamber by rank: when `true`, only players with **`tcp.bypass.entry`** (default: op) can enter a chamber on foot — everyone else is stopped at the boundary. Use it to make chambers rank-restricted (e.g. a VIP dungeon). Pair it with `prevent-teleport-into-chamber` to close the teleport route as well. Spectators and creative-mode players are exempt. Off by default. _(This check runs on player movement; it's lightweight — only firing on a block change while the toggle is on — but only enable it if you actually need rank-gated entry.)_

### `block-wild-vault-placement`

**Default:** `true` _(added in 1.5.7)_

Cancels placing functioning VAULT blocks **outside** registered chambers. A wild vault is a permanent vanilla loot dispenser the plugin can't manage — no per-player tracking, no resets, no loot tables. Players holding `tcp.bypass.vaultplace` (default: op) can always place them, so creative builds and crate setups are unaffected. Inside registered chambers the normal protection rules apply instead.

### `auto-pause-on-destruction`

**Default:** `false`

When `true`, a MONITOR-priority observer counts vault and trial spawner destructions inside each chamber. Once the count reaches `auto-pause-threshold`, the chamber is automatically paused and all ops with `tcp.discovery.notify` permission receive a notification. The counter resets every time the chamber is paused, resumed, or reset.

Designed for **hardcore/anarchy servers** where `prevent-block-break` is intentionally disabled. On servers with protection enabled, this setting is redundant (blocks can't be broken in the first place).

### `auto-pause-threshold`

**Default:** `6`

How many combined vault + trial spawner destructions inside a chamber must occur before `auto-pause-on-destruction` fires. Minimum effective value is `1`.

* **1-2** — catches individual mischief or accidents
* **6 (default)** — targets systematic demolition (breaking 6 critical blocks suggests deliberate intent)
* **10+** — only triggers on near-complete chamber destruction

The counter resets to zero on every pause/resume cycle and on chamber reset, so a resumed chamber always starts fresh.

***

## Trial Key Settings

```yaml
trial-keys:
  validate-key-type: true
```

{% hint style="info" %}
**Trial-key dupe protection isn't handled by TCP** — for that, use a dedicated plugin such as [AntiDupePro](https://modrinth.com/plugin/AntiDupePro).
{% endhint %}

### `validate-key-type`

**Default:** `true`

Enforce that normal keys only open normal vaults, ominous keys only open ominous vaults. Recommended to prevent exploits.

***

## Reset Settings

```yaml
reset:
  clear-ground-items: true
  remove-spawner-mobs: true
  remove-non-chamber-mobs: false
  reset-trial-spawners: true
  reset-ominous-spawners: true
  clear-trial-omen-effect: true
  reset-vault-cooldowns: true
```

### `clear-ground-items`

**Default:** `true`

Delete dropped items on reset? Prevents loot/trash buildup. Usually `true` for cleanliness.

### `clear-added-blocks`

**Default:** `true`

Snapshots skip air to save space, so on their own they can't undo blocks a player **added** into empty cells (lava, cobble, etc.). When `true`, reset also clears any block inside the chamber that isn't part of the snapshot back to air, so player additions never persist. Set `false` only if you intentionally let players build inside chambers.

### `remove-spawner-mobs`

**Default:** `true`

Kill mobs spawned by trial spawners during reset. Keeps chambers clean.

### `remove-non-chamber-mobs`

**Default:** `false`

Kill ALL mobs in the chamber, even those not from spawners (like player pets). Usually `false` to avoid accidents.

### `reset-trial-spawners`

**Default:** `true`

**CRITICAL for trial key drops!** Reset trial spawner state when the chamber resets. This clears the spawner's internal tracking of which players have completed it.

{% hint style="warning" %}
**Why this matters:** Trial spawners store which players have "completed" them. Without clearing this data, spawners won't spawn mobs or drop keys for returning players. This setting ensures spawners work like vanilla after each reset.
{% endhint %}

**Vanilla behavior when enabled:**

* Spawners forget which players completed them
* Players can reactivate spawners after chamber reset
* Spawners drop trial keys (50% chance per player) when all mobs are defeated

Set to `false` if you want spawners to permanently remember who completed them (not recommended).

### `reset-ominous-spawners`

**Default:** `true`

Convert ominous trial spawners back to normal during reset. Set to `false` if you want ominous spawners to stay ominous.

### `clear-trial-omen-effect`

**Default:** `true` _(implemented in 1.5.19)_

On reset, remove Trial Omen and Bad Omen from players who were inside the chamber, so leftover omen doesn't carry into the next cycle. Set to `false` to leave omen untouched.

### `reset-vault-cooldowns`

**Default:** `true`

Reset all player vault cooldowns when the chamber resets. This is the vanilla behavior—vaults reset when the chamber resets.

Set to `false` if you want personal cooldowns independent of chamber state (players must wait their individual cooldown time even after chamber resets).

***

## Performance Settings

```yaml
performance:
  cache-chamber-lookups: true
  cache-duration-seconds: 300
  time-tracking-interval: 300
```

### `cache-chamber-lookups`

**Default:** `true`

Cache which chamber a block belongs to. Huge performance boost. Only disable for debugging.

### `cache-duration-seconds`

**Default:** `300` (5 minutes)

How long to cache lookups. Higher = better performance, but changes take longer to propagate.

### `time-tracking-interval`

**Default:** `300` (5 minutes)

How often to save "time spent in chamber" stats to the database. More frequent = more accurate stats but more database writes.

{% hint style="info" %}
**Removed in 1.5.12:** `async-database-operations` and `use-folialib` were no-op toggles — database operations are always asynchronous, and Folia is auto-detected at startup. They've been removed from the default config; leftover entries in an existing `config.yml` are simply ignored.
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

### `enabled`

**Default:** `true`

Track player statistics (vaults opened, chambers completed, time spent)? Required for leaderboards.

### `track-time-spent`

**Default:** `true`

Track how long players spend inside chambers. Disable if you don't care about time-based stats.

### `track-chamber-completion`

**Default:** `true` _(added in 1.5.12)_

Credit a "chamber completed" to every participant when a chamber is fully cleared (all its trial spawners finish their waves in one run). Drives the chambers leaderboard and the `%tcp_chambers_completed%` / `%tcp_leaderboard_chambers%` / `%tcp_top_chambers_*%` placeholders. Disable to leave chamber-completion stats untracked.

### `top-players-count`

**Default:** `10`

How many players to show on leaderboards. Increase to 25 or 50 if you want bigger boards.

***

## Loot Settings

```yaml
loot:
  apply-luck-effect: false
  max-pools-per-table: 5
```

### `max-pools-per-table`

**Default:** `5`

Maximum number of pools allowed per loot table when using the [multi-pool format](loot.yml.md#-multi-pool-loot-system-new). Pools beyond this limit are silently ignored at load time. Increase it if you need more pools per table; lower it to cap table complexity.

### `apply-luck-effect`

**Default:** `false`

Enable LUCK to influence loot generation. When enabled, players receive bonus loot rolls based on their LUCK sources.

**How it works:**

* Checks **both** potion effects AND item attributes:
  * **Potion Effect**: Temporary LUCK from potions, beacons, suspicious stew
  * **Attribute**: Permanent LUCK from armor/items with luck modifiers
* Each point of LUCK adds +1 bonus roll to each loot pool
* Applies to weighted items only (guaranteed items are always given)
* Works with both normal and ominous vaults

**Example:** If a loot table has `min-rolls: 3` and `max-rolls: 5`:

* No LUCK: Player gets 3-5 items
* LUCK I potion: Player gets 4-6 items (+1 roll)
* LUCK II potion: Player gets 5-7 items (+2 rolls)
* LUCK I potion + 2 luck from items: Player gets 6-8 items (+3 rolls)

**Sources of LUCK:**

* Potion of Luck (temporary)
* Beacons with Luck effect
* Suspicious Stew made with dandelions
* Custom items/armor with `Attribute.GENERIC_LUCK` modifiers

{% hint style="info" %}
**Tip:** This is great for rewarding players who bring LUCK potions to chambers, or for special events where you want to boost loot!
{% endhint %}

{% hint style="warning" %}
**Balance Warning:** LUCK can significantly increase loot output. Test with your loot tables to ensure it doesn't break your economy.
{% endhint %}

***

## Spawner Wave System

```yaml
spawner-waves:
  enabled: true
  show-boss-bar: true
  detection-radius: 20
  remove-distance: 32
  award-stats: true
  completion-message: true
  glow-active-spawners: false
  glow-color-normal: "#FFFF55"
  glow-color-ominous: "#A020F0"
  glow-mode: "wave-active"
```

### `enabled`

**Default:** `true`

Enable wave progress tracking for trial spawners. Shows boss bar and sends completion messages.

### `show-boss-bar`

**Default:** `true`

Display a boss bar showing wave progress (mobs killed / total mobs) to nearby players. Ominous spawners show purple, normal spawners show yellow.

### `detection-radius`

**Default:** `20` blocks

How far from a trial spawner players can be and still see the boss bar / be considered participants.

### `remove-distance`

**Default:** `32` blocks _(added in 1.2.26)_

Distance at which a player is **removed** from a spawner's boss bar. Acts as hysteresis above `detection-radius`: players are added to the bar at 20 blocks, removed at 32. Prevents the bar flickering on/off when players walk near the edge, and fixes the old behaviour where boss bars lingered after leaving a chamber.

### `award-stats`

**Default:** `true`

Track mob kills from waves in player statistics. Used for leaderboards.

### `completion-message`

**Default:** `true`

Send a chat message when a wave is complete showing kill count and duration.

### `glow-active-spawners`

**Default:** `false` _(works correctly since 1.5.6 — see note below)_

Draw a glowing outline around the active trial spawner while a wave is running, visible through walls. Helps players find the spawner that's still firing in a big chamber. The outline is an invisible, invulnerable, non-colliding marker entity — it can't be hit, killed, or farmed, and it's removed the moment the wave completes or the chamber resets.

### `glow-color-normal` / `glow-color-ominous`

**Defaults:** `"#FFFF55"` (yellow) / `"#A020F0"` (purple)

Outline colors as hex RGB, per wave type.

### `glow-mode`

**Default:** `"wave-active"` _(added in 1.5.4)_

* `wave-active` — only the spawner whose wave is currently running glows.
* `chamber-remaining` — when any wave starts in a chamber, **every uncleared spawner** in that chamber glows until its own wave completes. Solves "which spawner did I miss?" navigation on large chambers. Pairs well with TCP-MythicTrials' HUD.

{% hint style="warning" %}
**Update to 1.5.6+ before enabling the glow.** On older builds the outline either didn't render at all (pre-1.5.4), floated one block above the spawner (1.5.4), or could be punched out and farmed for shulker shells (1.5.4–1.5.5).
{% endhint %}

{% hint style="info" %}
**How it works:** When mobs spawn from a trial spawner, the plugin tracks them. As players kill mobs, the boss bar updates. When all mobs are dead, players get a completion message.
{% endhint %}

***

## Spectator Mode

```yaml
spectator-mode:
  enabled: true
  offer-timeout: 30
  restrict-to-chamber: true
  boundary-buffer: 10
  allow-solo-spectate: false
```

### `enabled`

**Default:** `true`

Enable spectator mode. When players die in a chamber, they're offered the chance to spectate teammates.

### `offer-timeout`

**Default:** `30` seconds

How long the spectate offer lasts before expiring. Players type "spectate" or "no" in chat to respond.

### `restrict-to-chamber`

**Default:** `true`

Keep spectators within chamber bounds. They can fly around but not leave the area.

### `boundary-buffer`

**Default:** `10` blocks

Extra space outside the chamber boundary where spectators can still fly. Allows viewing from slightly outside.

### `allow-solo-spectate`

**Default:** `false`

Allow spectating empty chambers (when no other players are inside). Usually `false`—spectating is for watching teammates.

{% hint style="info" %}
**Spectator Mode Flow:**

1. Player dies in chamber
2. After respawn, offered to spectate (if other players are inside)
3. Type "spectate" to accept → GameMode.SPECTATOR, teleport to center
4. Type "exit" to leave → restored to previous game mode, teleported to exit
{% endhint %}

***

## Auto-Discovery of Natural Trial Chambers

_Added in 1.2.25 — opt-in._

Automatically registers naturally-generated Trial Chambers the first time anyone loads their chunks. No `/tcp generate` needed — the plugin detects vaults and spawners as chunks enter memory, flood-fills the chamber's structural blocks (tuff/copper variants) to compute a bounding box, and registers it under a deterministic auto-name like `auto_world_123_456`.

On plugin enable, a startup sweep also scans every already-loaded Overworld chunk, so chambers in pre-loaded spawn regions get picked up on restart.

```yaml
discovery:
  enabled: false                   # Master switch — opt-in
  max-radius-xz: 60                # BFS expansion cap on horizontal axes
  max-radius-y: 45                 # BFS expansion cap on vertical axis
  min-vaults-plus-spawners: 2      # Reject regions with fewer total vaults + spawners
  max-center-y: 10                 # Reject if AABB center Y > this (chambers gen deep underground)
  auto-snapshot: false             # Snapshot on registration (expensive; enable if you want resets to work)
  notify-ops: true                 # Broadcast registration to tcp.discovery.notify holders
  cooldown-seconds: 300            # Per-region debounce after a successful or failed discovery
  pending-retry-seconds: 30        # How long to keep a partial-load seed pending while adjacent chunks load
  merge-distance-blocks: 250       # Merge a new region into an existing chamber within this distance
  max-merged-volume: 1500000       # Hard cap on the post-merge bounding-box volume (blocks)
  snapshot-reminder:
    enabled: true
    on-join: true                  # Ping an admin individually when they log in
    interval-minutes: 30           # Periodic console summary + admin chat ping (0 disables periodic only)
```

### `enabled`

**Default:** `false`

Master switch. Off by default because it's a behaviour change — old worlds with player-built tuff/copper structures can look like chambers to the detector. Turn on if your world is freshly generated, or if you've verified the false-positive guards below are tight enough for your server.

### `max-radius-xz` / `max-radius-y`

**Defaults:** `60` / `45` blocks

Hard caps on the flood-fill. Prevents a runaway scan if the predicate accidentally matches a structure larger than any vanilla chamber. Tune down if you're seeing over-registration; leave alone otherwise.

### `min-vaults-plus-spawners`

**Default:** `2`

A candidate region must contain at least this many vaults + trial spawners combined, or it's rejected. Stops single-vault structures from getting registered as full chambers.

### `max-center-y`

**Default:** `10`

Reject if the AABB's center Y is above this. Trial chambers generate deep underground, so anything near the surface is almost certainly a player build. Raise this only if you have a modded world that generates chambers higher up.

### `auto-snapshot`

**Default:** `false`

Snapshot blocks on registration. Disabled by default because snapshotting a large chamber costs a few seconds of I/O per registration, which adds up if you're running a world pregenerator. **Enable this if you want auto-discovered chambers to be restorable on reset** — without a snapshot, the chamber gets per-player loot and protection, but resets can't rebuild broken blocks.

Requires **1.5.6+** to function: older builds wrote the snapshot file but never linked it to the chamber row, so resets still reported "No snapshot found" with this enabled.

### `notify-ops`

**Default:** `true`

Broadcast a message to anyone with the `tcp.discovery.notify` permission when a chamber is registered.

### `cooldown-seconds` / `pending-retry-seconds`

**Defaults:** `300` / `30`

Internal debounce and retry timers. `cooldown-seconds` prevents re-scanning the same 128-block region right after a successful or failed discovery. `pending-retry-seconds` is how long the plugin waits for neighbouring chunks to load before finalizing a partial-load chamber. Defaults are fine for almost everyone.

### `merge-distance-blocks` / `max-merged-volume`

**Defaults:** `250` / `1500000` _(added in 1.4.1)_

When a newly discovered region's bounding box sits within `merge-distance-blocks` (Chebyshev distance) of an already-registered chamber, the two are merged into one chamber instead of registering a duplicate — vanilla chambers often load in pieces as their chunks stream in. `max-merged-volume` hard-caps the post-merge bounding box so pathological geometry can't swallow half the world into one logical chamber; regions that would exceed it stay separate.

Since **1.5.6**, a merge automatically re-captures the chamber's snapshot whenever one exists (a pre-merge snapshot covers the old, smaller bounds and is unsafe to restore). If you see a console warning that a post-merge snapshot failed, run `/tcp snapshot create <chamber>` before the next reset.

### `snapshot-reminder`

**Defaults:** `enabled: true`, `on-join: true`, `interval-minutes: 30` _(added in 1.5.1)_

Auto-discovered chambers without a snapshot silently can't be reset. This pings admins holding `tcp.admin.snapshot` — once on login and as a periodic coalesced console/chat summary — whenever any chamber is missing its snapshot. Set `interval-minutes: 0` to disable only the periodic ping.

{% hint style="info" %}
**Plug-and-play setup:** if you want the plugin to "just work" on every chamber in your world without running any commands, set:

```yaml
discovery:
  enabled: true
  auto-snapshot: true
```

That's it. Walk/fly around the world and chambers will register themselves as you load their chunks.
{% endhint %}

***

## Per-Player Chamber Container Loot

```yaml
chests:
  per-player-loot: false
```

_(Added in 1.5.7 — opt-in. Substantially fixed in 1.5.9.)_ Lootr-style container loot: when enabled, every player who opens a **chest, trapped chest, barrel, dispenser, or dropper** inside a registered chamber gets their **own private copy** of its contents. The second player into a chamber no longer finds gutted containers — together with per-player vaults, the entire chamber becomes per-player.

How it behaves:

* Each container has a shared **template** — the contents every player's first-open copy is cloned from. For a naturally-generated chamber the template is **materialized by rolling the container's vanilla loot table** the first time it's accessed (a trial-chamber container is empty until something rolls its loot table, which is why earlier versions showed empty copies).
* Per-player copies persist across restarts (database) and **reset with the chamber**, so every cycle is fresh loot for everyone. The shared template, by contrast, **persists across resets** — so edits stick.
* Double chests share one copy (keyed by the left half). Dispensers/droppers use their 9 slots.
* **Hopper automation is blocked** in/out of chamber containers while enabled — it would drain or pollute the shared template.
* Containers **placed by players** inside a chamber keep vanilla behaviour (tagged at place time).
* **Template editing:** admins with `tcp.admin.containers` (default op) **sneak-click** to open the shared template and edit it; changes affect every player's first-open loot and persist across resets. A normal click gives them their own copy like any player.
* **Management:** the chamber GUI (`/tcp menu <chamber>` → **Container Loot**) and the [`/tcp container`](../reference/commands.md#tcp-container-action-chamber) command both let you list templates, **materialize all** containers at once (roll templates without opening each in-world), edit/teleport to a template, clear player copies, or reset templates.
* Decorated pots are excluded by design: their loot is break-based and already renews via chamber resets.

{% hint style="info" %}
**Upgrading to 1.5.9:** container loot tables are now captured by snapshots (so breaking or resetting a container restores its loot). Re-run `/tcp snapshot create` on existing chambers so their containers are captured under the new format. Already-materialized templates persist regardless.
{% endhint %}

{% hint style="warning" %}
Turn this on only after your chambers are registered — containers inside chamber bounds that players were already using as storage will start serving per-player copies (their real contents stay safe in the block, but players can't withdraw them without an admin sneak-click). Player-placed containers _from before 1.5.7_ can't be distinguished from chamber loot containers.
{% endhint %}

***

## Setup Tour

```yaml
setup:
  reminder:
    enabled: true
```

_(Added in 1.6.0.)_ Controls the opt-in [`/tcp setup`](../reference/commands.md) tour's gentle reminder. When `enabled`, an operator who hasn't run the tour yet gets a one-line nudge on join — **at most once a week, three times total** — which stops permanently the moment they run `/tcp setup` (with a single follow-up if they start but don't finish). Set to `false` to never show it. The tour itself is always available on demand regardless of this setting; TCP runs perfectly on its defaults without ever opening it.

***

## Metrics

```yaml
metrics:
  enabled: true
```

_(Added in 1.5.7.)_ Anonymous aggregate usage metrics via [bStats](https://bstats.org) — database backend, whether discovery is enabled, glow mode, chamber-count bucket, and which premium modules are installed alongside TCP. **No player data is ever collected.** Disable here or server-wide in `plugins/bStats/config.yml`.

***

## Debug Mode

```yaml
debug:
  verbose-logging: false
```

### `verbose-logging`

**Default:** `false`

Enable detailed logging for debugging. Logs include:

* Vault type detection (normal vs ominous)
* Key validation checks
* LUCK effect calculations
* Block state information
* Vault saving and updates
* Loot roll calculations
* Spawner wave tracking (mob spawns, deaths, wave completion)
* **Spawner cooldown configuration** (v1.2.15+): Shows config values, old→new cooldown for each spawner, and verification that changes were applied

**Version 1.1.9+:** When enabled, displays a prominent startup banner on server start:

```
═══════════════════════════════════════
   DEBUG MODE ENABLED
   Verbose logging is active
   Expect detailed console output
═══════════════════════════════════════
```

This helps verify that debug mode is actually loaded from your config file.

{% hint style="info" %}
**Troubleshooting:** If you don't see the startup banner after enabling debug mode:

1. Make sure you're editing the config in `plugins/TrialChamberPro/config.yml` (not the default in the plugin jar)
2. Fully restart your server (not just `/tcp reload`)
3. Check for YAML syntax errors in your config file
{% endhint %}

{% hint style="warning" %}
Debug mode generates TONS of console output. Don't leave it on in production!
{% endhint %}

***

## Quick Configs for Common Setups

### Casual Survival Server

```yaml
global:
  default-reset-interval: 86400  # Daily resets
vaults:
  normal-cooldown-hours: 6  # Generous cooldowns
  ominous-cooldown-hours: 12
protection:
  allow-pvp: false  # No PvP
```

### Competitive/PvP Server

```yaml
global:
  default-reset-interval: 43200  # Twice daily
vaults:
  normal-cooldown-hours: 12
  ominous-cooldown-hours: 24
protection:
  allow-pvp: true
  prevent-block-place: true
```

### High-Activity Server

```yaml
global:
  default-reset-interval: 21600  # Every 6 hours
  blocks-per-tick: 1000  # Fast resets
vaults:
  normal-cooldown-hours: 3
  ominous-cooldown-hours: 6
performance:
  cache-duration-seconds: 600  # Longer cache
```

### Roleplay/Lore Server

```yaml
global:
  default-reset-interval: 604800  # Weekly resets
vaults:
  normal-cooldown-hours: 168  # Once per week
  show-cooldown-particles: false  # Less immersion-breaking
  play-sound-on-open: false
```

***

## Applying Changes

After editing `config.yml`:

```
/tcp reload
```

Most settings apply immediately. Chamber-specific settings (like `default-reset-interval`) only affect new resets, not chambers mid-cycle.

{% hint style="info" %}
**Database changes** require a full restart, not just a reload.
{% endhint %}

***

## Pro Tips

{% hint style="success" %}
**Start conservative:** Use longer cooldowns and reset intervals initially. It's easier to shorten them later than deal with player complaints about too-frequent changes.
{% endhint %}

{% hint style="info" %}
**Test in dev:** Create a test chamber to experiment with settings. Use `/tcp reset TestChamber` to see how changes affect gameplay.
{% endhint %}

{% hint style="warning" %}
**Backup before tuning:** Changing database settings wrong can corrupt data. Always backup `plugins/TrialChamberPro/` before major config changes.
{% endhint %}

***

## Common Questions

**"Can different chambers have different reset intervals?"** Yes! The `default-reset-interval` is just the default. You can override per-chamber using database edits or (in future versions) per-chamber configs.

**"What happens if I change cooldowns while players have active cooldowns?"** Existing cooldowns aren't retroactively changed. New cooldowns apply to future vault interactions.

**"Can I disable statistics for performance?"** Yes, set `statistics.enabled: false`. You'll lose leaderboards, but save a tiny bit of database overhead.

**"Should I use MySQL or SQLite?"** SQLite unless you're running multiple servers that need shared data. SQLite is faster for single-server setups.

***

Need help with something specific? Check out the other configuration pages!

{% content-ref url="loot.yml.md" %}
[loot.yml.md](loot.yml.md)
{% endcontent-ref %}

{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}

***

## Generation Settings

These settings control constraints when registering or generating chamber regions.

```yaml
generation:
  # Maximum number of blocks allowed when generating/registering a chamber region
  # Keep this manageable for your server hardware
  max-volume: 750000
  blocks:
    # When using /tcp generate blocks <amount>, we may need to round up to reach
    # minimum viable dimensions (31x15x31). This is the maximum number of extra
    # blocks allowed beyond the requested amount.
    rounding-allowance: 1000
```

Notes:

* The plugin enforces a hard minimum region size of 31x15x31.
* The `blocks` generator places the region in front of the player based on their facing.
* If your requested amount is below minimum, it will be rounded up to that minimum.
* If your requested amount is slightly above minimum or not factorizable cleanly, the plugin rounds up to form a solid box within the rounding allowance.

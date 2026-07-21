<center>

<img width="750" alt="Better Trial Chambers Banner" src="https://cdn.modrinth.com/data/cached_images/deb2866a173c3b758b82a8c950ea2269d93bcd78_0.webp" />

# BetterTrialChambers

### The most complete Trial Chamber plugin... that's free!?

**Please share your "If it would do [THING], I would use it" feedback!** [<img src="https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/dc.png" width="30" alt="Join Discord Server">](https://discord.gg/qwYcTpHsNC)

<br></center>

### Recently Added

- **The whole chamber is per-player** — vaults *and* chests / barrels / dispensers / droppers each hand every player their own Lootr-style copy that resets with the chamber (decorated pots refill too). The second player in no longer finds an empty room.
- **…or first come, first served** — *new:* one setting decides who gets a vault's loot. Everyone gets their own copy, or the first player to reach a vault claims it and it stays shut for everybody else until the chamber resets. Your custom loot either way.
- **A guided setup tour** — run `/trial setup` for a friendly, one-setting-at-a-time walkthrough of the main options (**Enable / Skip / Disable**, nothing forced). Native Dialog UI on Paper 1.21.7+, with an automatic clickable-chat fallback on older servers.

  <img src="https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/setup-chat.png" alt="The /trial setup clickable-chat prompt in-game" width="800" />

  <details>
  <summary>See the native Dialog UI (Paper 1.21.7+)</summary>
  <img src="https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/setup-dialog.png" alt="The /trial setup tour rendered as a native Dialog popup" width="800" />
  </details>

- **Build your own chambers** — a procedural dungeon engine stitches rooms you design into a fresh chamber, and `/trial dungeon import` turns vanilla `.nbt` structure templates (the format "crazy chambers"-style datapacks use) into rooms automatically. Generated dungeons get resets, loot and protection like any other chamber.
- **Loot you actually control** — build tables by **weight** or by **plain % drop chance** per item, hand out **economy rewards** through Vault, let players **pay keys to reopen** a vault, and support custom items from Nexo / ItemsAdder / Oraxen / MythicMobs / CraftEngine.

*This plugin is updated almost daily — and many of these features came straight from a server owner's suggestion. Got an idea? Share it (Discord above) and there's a good chance it ships within days. Thank you!*

<br>

**Full documentation:** https://esmp-fun.gitbook.io/plugins/better-trial-chambers — most questions are answered there.

---

## Why BetterTrialChambers?

Vanilla Trial Chambers weren't designed for multiplayer. The first player takes everything, vaults stay locked forever, and griefers destroy spawners. **BetterTrialChambers fixes all of that.**

| Problem | Solution |
|---------|----------|
| First player gets all loot | Per-player **vaults** with individual cooldowns — and per-player **chests/barrels** too |
| No way to reset chambers | Automatic scheduled resets with player warnings |
| Vaults locked forever once opened | Time-based cooldowns, reset-based unlocks, or pay-keys-to-reopen |
| Griefers break spawners | Full protection system with optional WorldGuard support |
| Players can't get into a sealed chamber | Tunnel-breaking: dig in through the shell, farm nothing |
| Paper trial key bugs | Built-in fixes for known Paper issues |
| No progression tracking | Statistics, leaderboards, and PlaceholderAPI support |
| Setup overhead per chamber | **Auto-discovery** — chambers register themselves |

---

## Two-Line Plug-and-Play Setup

For most servers, the only thing you need to configure is this:

```yaml
# plugins/BetterTrialChambers/config.yml
discovery:
  enabled: true        # find natural trial chambers automatically
  auto-snapshot: true  # capture blocks so resets can restore them
```

Restart once. Fly or walk through your world — every natural trial chamber registers itself as its chunks load, with per-player loot, protection, and automatic resets already active. Done.

> Why it's opt-in: on **old** worlds that pre-date 1.21, players sometimes build decorative structures out of tuff and copper blocks. The auto-detector could register those as chambers. On fresh worlds there's no risk. [More details in the docs →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/config.yml)

Prefer manual control? You can still register chambers with WorldEdit (`/trial generate wand MyChamber`) or by coordinates — see [Your First Chamber](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/getting-started/your-first-chamber). And `/trial setup` walks you through the main settings either way.

---

## Features

### Core Systems

- **Auto-Discovery** — natural chambers register themselves on chunk load, at their exact structure bounds. Works with datapack-enlarged chambers.
- **Automatic Resets** — chambers restore on schedule with configurable warnings, or set the interval to `0` for manual-only.
- **Per-Player Vaults** — everyone gets their own loot with separate cooldowns, plus optional pay-keys-to-reopen.
- **Per-Player Chests & Barrels** *(opt-in)* — Lootr-style private container copies that re-roll fresh after every reset, so the whole chamber is per-player.
- **Tunnel-Breaking** *(opt-in)* — players can mine into a sealed chamber through the shell; broken blocks drop nothing and heal on reset.
- **Procedural Dungeons** — build modular rooms with jigsaw-block doorways (or import them from datapacks) and `/trial dungeon generate` stitches them into brand-new chambers.
- **Full Protection** — block break/place, container access, mob griefing, claim conflicts, wild-vault placement. WorldGuard-aware.
- **Statistics & Leaderboards** — vaults opened, mobs killed, time spent, with PlaceholderAPI support.
- **Admin GUI** — `/trial menu` does everything. No YAML editing required — and `/trial list` deep-links into it.

<details>

<summary><strong>Advanced Loot</strong> — multi-pool tables, custom plugin items, command & vanilla-table rewards</summary>

- **Multi-Pool Tables** — common / rare / unique pools like vanilla, fully configurable. [Docs →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/loot.yml)
- **Per-Chamber Overrides** — assign a different loot table to any specific chamber.
- **GUI Editing** — open `/trial menu` → Loot Tables, click a table, and edit it. Changes save to `loot.yml` instantly.
- **Custom Plugin Items** — drop Nexo, ItemsAdder, or Oraxen items directly from vaults:
  ```yaml
  - type: CUSTOM_ITEM
    plugin: nexo        # or itemsadder / oraxen / craftengine / mythiccrucible
    item-id: mythic_sword
    weight: 5
  ```
- **Custom Model Data** — set `custom-model-data` on any vanilla item for resource-pack integration.
- **Command Rewards** — run any console command as loot (economy deposits, permission grants, XP).
- **Vanilla & Datapack Tables** — reference any registered loot table directly:
  ```yaml
  - type: VANILLA_TABLE
    table: "minecraft:chests/trial_chambers/reward"
    weight: 30
  ```
- **Potion & Tipped Arrows** — full attribute support including Bad Omen III–V ominous bottles.
- **LUCK Integration** — optional bonus rolls for players with the LUCK effect.

</details>

<details>

<summary><strong>Multiplayer Enhancements</strong> — wave tracking, spawner glow, spectator mode, PlaceholderAPI</summary>

- **Spawner Wave Tracking** — boss bar shows wave progress as players fight, and disappears when you leave the area.
- **No Wave Infighting** — spawner mobs no longer kill each other and clear the wave for you.
- **Spawner Glow Outline** *(opt-in)* — active spawners glow through walls; `chamber-remaining` mode lights up every uncleared spawner so nobody hunts for the one they missed.
- **Spectator Mode** — dead players can watch teammates complete the challenge, bounded to the chamber.
- **PlaceholderAPI** — 30+ placeholders for scoreboards, holograms, tab lists.

</details>

<details>

<summary><strong>Technical</strong> — Folia-native, async architecture, dual database, translatable</summary>

- **Folia Native** — full support for regionized multithreading; also runs on Paper, Purpur, and Pufferfish.
- **Async Architecture** — Kotlin coroutines, zero main-thread blocking; big chambers scan and snapshot in tick-friendly batches.
- **Dual Database** — SQLite (default) or MySQL with connection pooling and a configurable table prefix for shared databases.
- **Self-Updating Configs** — new options and message keys merge into your existing files on every update (with a backup).
- **Fully Translatable** — every player-facing string lives in `messages.yml`. Edit one file, `/trial reload`, done.
- **WorldEdit / FAWE** — optional; used for selection-based chamber creation and near-instant resets.

</details>

---

## Premium Add-on Modules

BetterTrialChambers is, and always will be, **completely free**. For servers that want to push further, three optional paid modules are available at **[esmp.fun/plugins](https://esmp.fun/plugins)**:

- **Mythic Trials** — per-player chamber progression. Every clear bumps each participant's personal difficulty tier (T1–T20, then Mythic M1–M5): mobs scale in stats, gear, and tactics while rewards scale with them. HUD, leaderboards, and seasons included.
- **Wild Spawners** — place BTC preset spawners anywhere on the map, no Trial Chamber required. Hand them out via shops or crates; includes a GUI editor, holograms, and griefing protection.
- **Vault Crates** — a crate plugin that uses vanilla Trial Vaults instead of chests: built-in per-player state, two visual tiers, a key mechanic, and a dramatic open animation with no resource pack.

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ (use the `-mc26` JAR for 26.x) |
| **Server** | Paper, Folia, Purpur, or Pufferfish |
| **Java** | 21+ |

<details>

<summary><strong>Optional Dependencies</strong></summary>

- **WorldEdit / FastAsyncWorldEdit** — selection-based chamber creation, fast resets.
- **WorldGuard** — additional region protection.
- **PlaceholderAPI** — scoreboard / hologram placeholders.
- **Vault** — economy command rewards.
- **Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible** — custom items in loot tables.
- **LuckPerms** — permission command rewards.

</details>

---

## Quick Start & Reference

<details>

<summary><strong>Quick Start (manual mode)</strong> — the classic WorldEdit workflow</summary>

Prefer not to use auto-discovery? The classic workflow still works:

```
1. Drop the JAR in your plugins folder
2. Start your server
3. Select a Trial Chamber with WorldEdit (//wand)
4. Run: /trial generate wand MyChamber
5. Run: /trial snapshot create MyChamber   (enables auto-reset)
6. Done!
```

</details>

<details>

<summary><strong>Commands</strong> — the ones you'll actually use</summary>

| Command | Description |
|---------|-------------|
| `/trial setup` | Guided, opt-in settings tour (native Dialog UI, or clickable chat on older servers) |
| `/trial menu [chamber]` | Open the admin GUI (does everything); with a name, jump straight to that chamber |
| `/trial list [page\|current]` | Interactive chamber list — click to copy names or open the GUI |
| `/trial generate wand <name>` | Register chamber from WorldEdit selection |
| `/trial reset <chamber>` | Force immediate reset |
| `/trial snapshot create [chamber]` | Enable automatic resets (omit the name while standing inside) |
| `/trial dungeon generate <name>` | Stitch a procedural chamber from your room templates |
| `/trial dungeon import <file>` | Import `.nbt` structure templates (or a datapack zip) as rooms |
| `/trial loot set <chamber> <normal\|ominous> <table>` | Override loot for a chamber |
| `/trial stats [player]` | View statistics |
| `/trial leaderboard <type>` | View top players |
| `/trial reload` | Reload config & loot tables |

[Full command reference →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/reference/commands)

</details>

<details>

<summary><strong>Permissions</strong> — the essentials</summary>

| Permission | Description | Default |
|------------|-------------|---------|
| `btc.admin` | Full admin access | OP |
| `btc.stats` · `btc.leaderboard` | View own stats / leaderboards | Everyone |
| `btc.spectate` | Use spectator mode after death | Everyone |
| `btc.bypass.cooldown` | Ignore vault cooldowns (careful: removes progression!) | OP |
| `btc.bypass.protection` | Build in protected chambers | OP |
| `btc.discovery.notify` | Get notified when auto-discovery registers a chamber | OP |

> **Heads up:** `btc.bypass.cooldown` is granted to OPs by default. If you're testing cooldowns as an OP, they'll appear broken — either test as a non-OP or explicitly negate the permission.

[Full permissions guide with rank examples →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/reference/permissions)

</details>

<details>

<summary><strong>Essential Configuration</strong> — the settings most servers tweak</summary>

Sensible defaults work out of the box. The settings most servers actually tweak:

```yaml
global:
  default-reset-interval: 172800   # 48 hours. Use 0 for manual-only resets.

vaults:
  normal-cooldown-hours: 0         # 0 = vanilla (locked until chamber reset)
  ominous-cooldown-hours: 0        # Set a positive number for per-player time cooldown.
  reopen-cost-keys: 0              # N = open an already-used vault again for N keys. 0 = off.

chests:
  per-player-loot: false           # true = every player gets their own chest loot (Lootr-style)

protection:
  tunnel-breaking:
    enabled: false                 # true = players can mine in through the shell (no drops)

discovery:
  enabled: true                    # Auto-register natural chambers. Opt-in.
  auto-snapshot: true              # Allow auto-discovered chambers to restore on reset.
```

[Full config.yml reference →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/config.yml) · [loot.yml reference →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/loot.yml)

</details>

<details>

<summary><strong>PlaceholderAPI</strong> — 30+ placeholders</summary>

Placeholders for player stats (`%btc_vaults_opened%`, `%btc_mobs_killed%`, `%btc_kdr%`, `%btc_time_spent%`), current state (`%btc_current_chamber%`, `%btc_current_chamber_reset%`, `%btc_chamber_count%`), leaderboard rank (`%btc_leaderboard_vaults%`), and top-10 boards (`%btc_top_vaults_1_name%` … `_value%` for vaults/chambers/time/mobs). Built-in caching (stats 30 s, leaderboards 60 s).

[Full placeholder list →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/getting-started/placeholders)

</details>

---

## Support

- **[Documentation](https://esmp-fun.gitbook.io/plugins/better-trial-chambers)** — setup guides, configuration reference, troubleshooting. **Please check here first!** Most questions are already answered.
- **[Discord](https://discord.gg/qwYcTpHsNC)** — community support, announcements, feature requests. Not everyone's a reader — that's fine, come chat.
- **[GitHub Issues](https://github.com/ESMP-FUN/BetterTrialChambers/issues)** — bug reports.
- **[Source Code](https://github.com/ESMP-FUN/BetterTrialChambers)** — source-available (free to use, no redistribution).

---

## Target Audience

- **Survival Servers** — renewable endgame content that keeps players engaged.
- **SMP Networks** — fair loot distribution across your playerbase.
- **Minigame Servers** — competitive Trial Chamber runs with leaderboards.
- **Adventure / RP Servers** — protected dungeons with custom rewards.

---

<div align="center">

**Paper 1.21.1+ / 26.1.2+** · **Folia Native** · **Java 21+**

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks)

---

This plugin is free forever and actively maintained.

If you have questions or would like to just say Hi, come [join the Discord](http://discord.gg/qwYcTpHsNC).

Rather stay silent? (Anonymous) donations are also **VERY** welcome: https://ko-fi.com/darkstarworks

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-trial-chambers%3Fmetric%3Dservers%26color%3Dorange%26icon%3D1&style=flat)](https://faststats.dev/project/better-trial-chambers)

</div>
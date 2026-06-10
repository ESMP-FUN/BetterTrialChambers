<img width="723" height="133" alt="TrialChamberPro Banner" src="https://github.com/user-attachments/assets/7be34fce-1bfc-4639-bd34-5fe417e43610" />

# TrialChamberPro

### What's new in 1.5.7

- **Per-player chamber chest loot** *(opt-in)* — every player who opens a chest or barrel inside a registered chamber now gets their **own copy of its contents**, Lootr-style. No more gutted chests for the second player in. Copies reset with the chamber, hopper-draining is blocked, and player-placed containers keep vanilla behaviour. Combined with per-player vaults, the **entire chamber is now per-player**. One config line: `chests.per-player-loot: true`.
- **Key-to-reopen vaults** *(opt-in)* — set `vaults.reopen-cost-keys` and players can open an already-used vault again by paying that many matching trial keys, instead of waiting for the cooldown or reset.
- **Vanilla & datapack loot tables in loot.yml** — a pool entry of `type: VANILLA_TABLE` with `table: "minecraft:chests/trial_chambers/reward"` (or any datapack key) rolls the real server loot table straight into the drop.
- **Clickable `/tcp list`** — click a chamber name to copy it, or the **[menu]** button to jump straight into that chamber's GUI (`/tcp menu <chamber>` deep-link).
- **Wild vault protection** *(on by default)* — players can no longer place functioning VAULT blocks outside registered chambers (a permanent loot dispenser the plugin can't manage). Ops are exempt.
- **Spawner glow outline finally works** *(1.5.4–1.5.6)* — the opt-in glow around active trial spawners now actually renders, sits flush on the block, and can't be punched out or farmed. New `glow-mode: chamber-remaining` lights up every uncleared spawner in the chamber — no more hunting the one you missed.
- **Critical reset & discovery fixes** *(1.5.6)* — chamber resets can no longer clear terrain beyond what their snapshot covers, discovery auto-snapshots are properly linked, and merged chambers re-capture their snapshot automatically. If a pre-1.5.6 reset damaged one of your chambers: `/tcp delete <chamber>` removes the broken registration and its stale snapshot in one step.
- **QoL** — `/tcp snapshot create|update|restore` work without a chamber name when you're standing inside one; new `ChamberEnteredEvent`/`ChamberExitedEvent` for plugin developers.

Plus the 1.5.0 foundation: **vanilla-loot-fallback fix on 100+ chamber servers**, **faithful loot NBT + bulk drag-in editor**, **procedural dungeon generation** (`/tcp dungeon`), **hardened throttled resets**, and **`ChamberClearedEvent`**. And the 1.3.x–1.4.x line: **custom mobs** (MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens), **fully translatable GUI**, **Bukkit events API**, **spawner presets**, **chamber pause state**, **MiniMessage everywhere**, and **Minecraft 26.x support** via the `-mc26` build.

📘 **Full documentation:** https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation — most questions are answered there, and every section below links to its own detailed page.

---

## Premium Add-on Modules

TrialChamberPro is, and always will be, **completely free**. But after a lot of requests from server owners wanting to push things further, we now offer optional premium modules for servers that need more.

### 🔷 TCP-WildSpawners
Place TCP preset spawners **anywhere on your survival map** — no Trial Chamber required. Hand them out via shops, crates, or staff commands. Wherever a player puts one down, it summons your custom mobs. Includes an in-game GUI editor, per-preset griefing protection, holograms, mob tether, creative pick-block, and state-preserving mining so players can relocate a live spawner without losing cooldown progress.

Requires TrialChamberPro v1.4.0+ (free).

👉 **[esmp.fun/plugins](https://esmp.fun/plugins)** — purchase & download

### 🔶 TCP-VaultCrates
The **first crate plugin that doesn't use chests.** Vanilla Trial Vaults are the only Minecraft block with built-in per-player loot state, two visual tiers (normal + ominous), a key mechanic, and a dramatic open animation that doesn't need a resource pack. TCP-VaultCrates lets you register vaults anywhere on your map as crates with configurable loot pools and per-tier keys. Hand keys out via shops, missions, or — when paired with TCP-WildSpawners — drop them from spawner mobs.

Requires TrialChamberPro v1.4.0+ (free).

👉 **[esmp.fun](https://esmp.fun/plugins)** — purchase & download

### 🔺 TCP-MythicTrials
**Per-player chamber progression with Mythic difficulty tiers.** Every chamber clear bumps each participant's personal tier (T1–T20, then opt-in Mythic M1–M5): mobs scale in health, damage, speed, armor, gear and tactics — gear-adaptive AI, themed rooms, anti-heal, true damage — while rewards scale with them. In-chamber HUD, per-chamber leaderboards, seasons with seasonal loot, and full custom-mob-provider support. Built on TCP's `ChamberClearedEvent`, so it works on every registered chamber automatically.

Requires TrialChamberPro v1.5.4+ (free).

👉 **[esmp.fun](https://esmp.fun/plugins)** — pre-order & download

---

**The definitive Trial Chamber management plugin for multiplayer servers.**

Transform Minecraft's Trial Chambers from single-use dungeons into renewable, multiplayer-ready content. Automatic resets, per-player loot, custom rewards, griefing protection — and it all works out of the box.

---

## Why TrialChamberPro?

Vanilla Trial Chambers weren't designed for multiplayer. The first player takes everything, vaults stay locked forever, and griefers destroy spawners. **TrialChamberPro fixes all of that.**

| Problem | Solution |
|---------|----------|
| First player gets all loot | Per-player **vaults** with individual cooldowns — and per-player **chests/barrels** too *(new in 1.5.7)* |
| No way to reset chambers | Automatic scheduled resets with player warnings |
| Vaults locked forever once opened | Time-based cooldowns, reset-based unlocks, or pay-keys-to-reopen *(new in 1.5.7)* |
| Griefers break spawners | Full protection system with optional WorldGuard support |
| Paper trial key bugs | Built-in fixes for known Paper issues |
| No progression tracking | Statistics, leaderboards, and PlaceholderAPI support |
| Setup overhead per chamber | **Auto-discovery** — chambers register themselves |

---

## Two-Line Plug-and-Play Setup

For most servers, the only thing you need to configure is this:

```yaml
# plugins/TrialChamberPro/config.yml
discovery:
  enabled: true        # find natural trial chambers automatically
  auto-snapshot: true  # capture blocks so resets can restore them
```

Restart once. Fly or walk through your world — every natural trial chamber registers itself as its chunks load, with per-player loot, protection, and automatic resets already active. Done.

> Why it's opt-in: on **old** worlds that pre-date 1.21, players sometimes build decorative structures out of tuff and copper blocks. The auto-detector could register those as chambers. On fresh worlds there's no risk. [More detail in the docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml#auto-discovery-of-natural-trial-chambers)

Prefer manual control? You can still register chambers with WorldEdit (`/tcp generate wand MyChamber`) or by coordinates — see [Your First Chamber](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/getting-started/your-first-chamber).

---

## Features

### Core Systems
- **Auto-Discovery** — natural chambers register themselves on chunk load + startup sweep. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml#auto-discovery-of-natural-trial-chambers)
- **Automatic Resets** — chambers restore on schedule with configurable warnings, or set interval to `0` for manual-only.
- **Per-Player Vaults** — everyone gets their own loot with separate cooldowns, plus optional pay-keys-to-reopen.
- **Per-Player Chests & Barrels** *(new in 1.5.7, opt-in)* — Lootr-style private container copies, so the whole chamber is per-player.
- **Procedural Dungeon Generation** — build modular rooms with jigsaw-block doorways; `/tcp dungeon generate` stitches them into brand-new chambers.
- **Full Protection** — block break/place, container access, mob griefing, wild-vault placement. WorldGuard-aware.
- **Statistics & Leaderboards** — vaults opened, mobs killed, time spent. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/guides/statistics)
- **Admin GUI** — `/tcp menu` does everything. No YAML editing required — and `/tcp list` deep-links into it.

### Advanced Loot
- **Multi-Pool Tables** — common / rare / unique pools like vanilla, fully configurable. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/loot.yml)
- **Per-Chamber Overrides** — assign a different loot table to any specific chamber.
- **GUI Editing** — open `/tcp menu` → Loot Tables, click a table, and edit it. Changes save to `loot.yml` instantly.
- **Custom Plugin Items** — drop Nexo, ItemsAdder, or Oraxen items directly from vaults:
  ```yaml
  - type: CUSTOM_ITEM
    plugin: nexo        # or itemsadder / oraxen / craftengine / mythiccrucible
    item-id: mythic_sword
    weight: 5
  ```
- **Custom Model Data** — set `custom-model-data` on any vanilla item for resource-pack integration.
- **Command Rewards** — run any console command as loot (economy deposits, permission grants, XP).
- **Vanilla & Datapack Tables** *(new in 1.5.7)* — reference any registered loot table directly:
  ```yaml
  - type: VANILLA_TABLE
    table: "minecraft:chests/trial_chambers/reward"
    weight: 30
  ```
- **Potion & Tipped Arrows** — full attribute support including Bad Omen III–V ominous bottles.
- **LUCK Integration** — optional bonus rolls for players with the LUCK effect.

### Multiplayer Enhancements
- **Spawner Wave Tracking** — boss bar shows wave progress as players fight. Hysteresis-based despawn means the bar disappears when you leave the area.
- **Spawner Glow Outline** *(opt-in)* — active spawners glow through walls; `chamber-remaining` mode lights up every uncleared spawner so nobody hunts for the one they missed.
- **Spectator Mode** — dead players can watch teammates complete the challenge, bounded to the chamber.
- **PlaceholderAPI** — 20+ placeholders for scoreboards, holograms, tab lists.

### Technical Excellence
- **Folia Native** — full support for regionized multithreading.
- **Paper / Purpur / Pufferfish** — works on all major Paper forks.
- **Async Architecture** — Kotlin coroutines, zero main-thread blocking.
- **Dual Database** — SQLite (default) or MySQL with connection pooling.
- **WorldEdit / FAWE** — optional, used only if you want selection-based chamber creation.

---

## Fully Translatable

Every user-facing string lives in `plugins/TrialChamberPro/messages.yml`. Want your server in French, Chinese, Spanish, or Klingon? Edit one file, `/tcp reload`, done.

Supports `&`-style color codes, `{placeholder}` substitution, and Adventure Components for boss bars.

📘 [Full message reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/messages.yml)

---

## Quick Start (manual mode)

Prefer not to use auto-discovery? Classic workflow still works:

```
1. Drop the JAR in your plugins folder
2. Start your server
3. Select a Trial Chamber with WorldEdit (//wand)
4. Run: /tcp generate wand MyChamber
5. Run: /tcp snapshot create MyChamber   (enables auto-reset)
6. Done!
```

---

## Commands (at a glance)

| Command | Description |
|---------|-------------|
| `/tcp menu [chamber]` | Open the admin GUI (does everything); with a name, jump straight to that chamber |
| `/tcp list [page\|current]` | Interactive chamber list — click to copy names or open the GUI |
| `/tcp generate wand <name>` | Register chamber from WorldEdit selection |
| `/tcp reset <chamber>` | Force immediate reset |
| `/tcp snapshot create [chamber]` | Enable automatic resets (omit the name while standing inside) |
| `/tcp dungeon generate <name>` | Stitch a procedural chamber from your room templates |
| `/tcp loot set <chamber> <normal\|ominous> <table>` | Override loot for a chamber |
| `/tcp stats [player]` | View statistics |
| `/tcp leaderboard <type>` | View top players |
| `/tcp reload` | Reload config & loot tables |

📘 [Full command reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/reference/commands)

---

## Permissions (essentials)

| Permission | Description | Default |
|------------|-------------|---------|
| `tcp.admin` | Full admin access | OP |
| `tcp.stats` · `tcp.leaderboard` | View own stats / leaderboards | Everyone |
| `tcp.spectate` | Use spectator mode after death | Everyone |
| `tcp.bypass.cooldown` | Ignore vault cooldowns (careful: removes progression!) | OP |
| `tcp.bypass.protection` | Build in protected chambers | OP |
| `tcp.discovery.notify` | Get notified when auto-discovery registers a chamber | OP |

> **Heads up:** `tcp.bypass.cooldown` is granted to OPs by default. If you're testing cooldowns as an OP, they'll appear broken — either test as a non-OP or explicitly negate the permission.

📘 [Full permissions guide with rank examples →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/reference/permissions)

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ (use `-mc26` JAR for 26.x) |
| **Server** | Paper, Folia, Purpur, or Pufferfish |
| **Java** | 21+ |

### Optional Dependencies
- **WorldEdit / FastAsyncWorldEdit** — selection-based chamber creation.
- **WorldGuard** — additional region protection.
- **PlaceholderAPI** — scoreboard / hologram placeholders.
- **Vault** — economy command rewards.
- **Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible** — custom items in loot tables.
- **LuckPerms** — permission command rewards.

---

## Essential Configuration

Sensible defaults work out of the box. The three settings most servers actually tweak:

```yaml
global:
  default-reset-interval: 172800   # 48 hours. Use 0 for manual-only resets.

vaults:
  normal-cooldown-hours: 0         # 0 = vanilla (locked until chamber reset)
  ominous-cooldown-hours: 0        # Set a positive number for per-player time cooldown.
  reopen-cost-keys: 0              # N = open an already-used vault again for N keys. 0 = off.

chests:
  per-player-loot: false           # true = every player gets their own chest loot (Lootr-style)

discovery:
  enabled: true                    # Auto-register natural chambers. Opt-in.
  auto-snapshot: true              # Allow auto-discovered chambers to restore on reset.
```

📘 [Full config.yml reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml) · [loot.yml reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/loot.yml)

---

## PlaceholderAPI (summary)

20+ placeholders for player stats (`%tcp_vaults_opened%`, `%tcp_mobs_killed%`, `%tcp_time_spent%`), current state (`%tcp_current_chamber%`), and leaderboards (`%tcp_top_vaults_1_name%` through `_10_`). Built-in 60-second cache.

📘 [Full placeholder list →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/guides/statistics#placeholderapi)

---

## Support

- 📘 **[Documentation](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation)** — setup guides, configuration reference, troubleshooting. **Please check here first!** Most questions are already answered.
- 💬 **[Discord](https://discord.gg/qwYcTpHsNC)** — community support, announcements, feature requests. Not everyone's a reader — that's fine, come chat.
- 🐛 **[GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues)** — bug reports.
- ⭐ **[Source Code](https://github.com/darkstarworks/TrialChamberPro)** — open source under CC-BY-NC-ND 4.0.

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

</div>

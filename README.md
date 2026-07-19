# BetterTrialChambers

Transform Minecraft's Trial Chambers from single-use dungeons into renewable, multiplayer-ready content. Automatic resets, per-player vault loot, custom rewards, griefing protection — and it works on every natural chamber in your world without any setup per-chamber.

---

## The problem it solves

Vanilla Trial Chambers weren't designed for multiplayer. The first player takes everything, vaults stay locked forever, and griefers can destroy spawners. On a server with more than one player, chambers become single-use content almost immediately.

BetterTrialChambers fixes all of that: every player gets their own loot roll, chambers reset on a schedule, spawners are protected, and progression is tracked. The plugin can find and manage natural chambers automatically — no WorldEdit, no commands per chamber.

---

## What you can do

* **Automatic resets** — chambers restore on schedule, with warnings before the reset fires.
* **Per-player vaults** — every player gets their own loot roll with their own cooldown.
* **Full protection** — block break / place, container access, mob griefing, WorldGuard-aware, and claim-plugin-aware (Residence / Lands / GriefPrevention can't claim a chamber).
* **Statistics & leaderboards** — track vaults opened, mobs killed, chambers completed, time spent.
* **Custom loot** — multi-pool tables, command rewards, potions, tipped arrows, custom plugin items (Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible), resource-pack items via `custom-model-data`.
* **Auto-discovery** — opt-in; the plugin finds and registers every natural chamber on its own.
* **Admin GUI** — `/trial menu` handles everything. No YAML editing required.
* **Spawner wave tracking** — boss bar shows progress as players fight.
* **Spectator mode** — dead players can watch teammates finish the chamber.
* **PlaceholderAPI** — 20+ placeholders for scoreboards, holograms, tab lists.
* **Full translation support** — every user-facing string lives in `messages.yml`.

---

## Requirements

* **Minecraft 1.21.1+** (use the `-mc26` build for Minecraft 26.x)
* **Paper, Folia, Purpur, or Pufferfish**
* **Java 21+**
* *Optional:* WorldEdit / FAWE, WorldGuard, Residence / Lands / GriefPrevention, PlaceholderAPI, Vault, LuckPerms, Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible

---

## Where to go next

[installation.md](getting-started/installation.md) -> Install the JAR and get the server running. Two minutes.

[your-first-chamber.md](getting-started/your-first-chamber.md) -> Manually register and configure a chamber. Recommended if you want fine control over specific chambers.

[basic-configuration.md](getting-started/basic-configuration.md) -> Walk through the config settings most servers actually tweak.

[config.yml.md](configuration/config.yml.md) -> Full `config.yml` reference. Includes the auto-discovery plug-and-play setup.

[loot.yml.md](configuration/loot.yml.md) -> Everything about loot tables — pools, custom items, command rewards.

[troubleshooting.md](troubleshooting.md) -> Something not working? Most issues have a known cause. Check here first.

---

## Premium add-ons

BetterTrialChambers is the free foundation. Two premium modules extend it further:

**[Wild Spawners](https://esmp.fun/plugins)** — Place trial spawners anywhere on the survival map, not just inside registered chambers. Players receive spawner items via shop plugins or staff commands, place them wherever they like, and custom-plugin mobs (MythicMobs, EliteMobs, etc.) spawn correctly. Includes configurable mining-and-redeploy (no Silk Touch required), per-spawner holograms, griefing protection, and a full in-game preset editor.

**[Vault Crates](https://esmp.fun/plugins)** — Turn any vanilla Vault block into a loot crate. Players open crates with keys earned in-game or purchased in a shop. Supports two-tier crates (normal / ominous), weighted loot pools, per-player and server-wide reset modes, and crate-key drops from Wild Spawners mob kills. Full in-game editor included.

**[Mythic Trials](https://esmp.fun/plugins)**
Per-player chamber progression with Mythic difficulty tiers. Every chamber clear bumps each participant's personal tier (T1–T20, then opt-in Mythic M1–M5): mobs scale in health, damage, speed, armor, gear and tactics — gear-adaptive AI, themed rooms, anti-heal, true damage — while rewards scale with them. In-chamber HUD, per-chamber leaderboards, seasons with seasonal loot, and full custom-mob-provider support. Built on BTC's ChamberClearedEvent, so it works on every registered chamber automatically.

All modules require BetterTrialChambers and are available [esmp.fun](https://esmp.fun/) (Stripe + crypto, for regions where certain payment processors don't operate).

---

## Support

* **[GitHub Issues](https://github.com/ESMP-FUN/BetterTrialChambers/issues)** — bug reports, feature requests
* **[Discord](https://discord.gg/qwYcTpHsNC)** — community support, announcements
* **[Modrinth](https://modrinth.com/plugin/trialchamberpro)** — downloads and release notes

Source-available — free to use, no redistribution (see [LICENSE](https://github.com/ESMP-FUN/BetterTrialChambers/blob/master/LICENSE)). Made with Kotlin by [darkstarworks](https://github.com/darkstarworks).

---

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-trial-chambers%3Fmetric%3Dservers)](https://faststats.dev/project/better-trial-chambers)

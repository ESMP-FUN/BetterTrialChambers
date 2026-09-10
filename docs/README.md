# Welcome

BetterTrialChambers makes Minecraft's Trial Chambers reusable on a multiplayer server: every player gets their own vault loot, chambers reset on a schedule, and spawners are protected from griefing. It can find and manage every natural chamber in your world with no per-chamber setup.

***

## Features

* **Automatic resets.** Chambers restore on a schedule, with warnings before each reset.
* **Per-player vaults.** Every player gets their own loot roll and cooldown. Optional `SHARED` mode for one reward per vault, server-wide.
* **Protection.** Block break/place, container access, mob griefing, PvP. WorldGuard-aware and claim-plugin-aware (Residence, Lands, GriefPrevention).
* **Statistics and leaderboards.** Vaults opened, mobs killed, chambers completed, time spent.
* **Custom loot.** Multi-pool tables, command rewards, potions, tipped arrows, custom-plugin items (Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible), resource-pack items via `custom-model-data`.
* **Auto-discovery.** Opt-in. Finds and registers natural chambers automatically, no WorldEdit needed.
* **Admin GUI.** `/trial menu` covers everything without editing YAML.
* **Spawner wave tracking.** Boss bar shows progress during a fight.
* **Spectator mode.** Dead players can watch teammates finish.
* **Custom mob providers.** MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens.
* **PlaceholderAPI.** 20+ placeholders for scoreboards, holograms, tab lists.
* **Full translation.** Every user-facing string is in `messages.yml`.

***

## Requirements

* **Minecraft 1.21.1+.** Use the `-mc26` build for Minecraft 26.x, or the `-mc263` build for 26.3+.
* **Paper, Folia, Purpur, or Pufferfish.**
* **Java 21+** (Minecraft 26.3 servers run on Java 25).
* _Optional:_ WorldEdit / FAWE, WorldGuard, Residence / Lands / GriefPrevention, AdvancedEnchantments, PlaceholderAPI, Vault, LuckPerms, Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible.

***

## Where to go next

[installation.md](getting-started/installation.md) - Install the JAR and start the server.

[quick-start.md](getting-started/quick-start.md) - Run `/trial setup`, turn on auto-discovery, done.

[basic-configuration.md](getting-started/basic-configuration.md) - The settings most servers change.

[config.yml.md](configuration/config.yml.md) - Full `config.yml` reference.

[loot.yml.md](configuration/loot.yml.md) - Loot tables: pools, custom items, command rewards.

[your-first-chamber.md](getting-started/your-first-chamber.md) - Register a chamber by hand (superflat / custom worldgen).

[troubleshooting.md](troubleshooting.md) - Known issues and their fixes.

***

## Premium add-ons

Both require BetterTrialChambers and are sold on [Voxel.shop](https://voxel.shop/) and [esmp.fun](https://esmp.fun/).

[**Wild Spawners**](https://esmp.fun/) - Place trial spawners anywhere on the map, not just inside registered chambers. Players get spawner items from a shop or staff, place them freely, and custom-plugin mobs spawn correctly. Includes mining-and-redeploy without Silk Touch, per-spawner holograms, griefing protection, and an in-game preset editor.

[**Vault Crates**](https://esmp.fun/) - Turn any vanilla Vault block into a key-opened loot crate. Two-tier crates (normal / ominous), weighted loot pools, per-player and server-wide reset modes, and crate-key drops from Wild Spawners mob kills. In-game editor included.

***

## Support

* [**GitHub Issues**](https://github.com/ESMP-FUN/BetterTrialChambers/issues) - bug reports, feature requests
* [**Discord**](https://dc.esmp.fun) - community support, announcements
* [**Modrinth**](https://modrinth.com/plugin/trialchamberpro) - downloads and release notes

Source-available, free to use, no redistribution (see [LICENSE](https://github.com/ESMP-FUN/BetterTrialChambers/blob/master/LICENSE)).

***

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-trial-chambers%3Fmetric%3Dservers%26color%3Dorange%26icon%3D1&style=flat)](https://faststats.dev/project/better-trial-chambers)

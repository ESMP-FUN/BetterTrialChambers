<center>

<img width="750" alt="Better Trial Chambers Banner" src="https://cdn.modrinth.com/data/cached_images/deb2866a173c3b758b82a8c950ea2269d93bcd78_0.webp" /><br>

Free forever. Updated almost daily.<br>

[![Discord](https://img.shields.io/badge/join_%E2%86%92_-_Discord-gray?style=flat&logo=discord&logoSize=amd)](http://discord.gg/qwYcTpHsNC)
[![Ko-Fi](https://img.shields.io/badge/support_%E2%86%92_-_KoFi-gray?style=flat&logo=kofi&logoSize=amd)](https://ko-fi.com/darkstarworks)
[![Patreon](https://img.shields.io/badge/support_%E2%86%92_-_Patreon-gray?style=flat&logo=patreon&logoSize=amd)](https://patreon.com/cw/darkstarworks)

Turn one-time Trial Chambers into replayable multiplayer dungeons.

<br>

| **Vanilla** | **With this plugin** |
|---------|------------------|
| First player gets everything | Every player gets their own loot |
| Chamber stays empty forever | Resets on a timer |
| Vault opens once | Cooldown, reset, or pay keys to reopen |
| Spawners get griefed | Protected |
| One chamber layout | Build your own / Datapack |
| Nothing is tracked | Stats and leaderboards |
| Set up each chamber by hand | Found automatically |

</center>

# Better Trial Chambers

### Setup

1. Drop the JAR in `/plugins`
2. Start the server
3. Type `/trial setup`
4. Answer the questions
5. Walk around your world & see the magic happen

Chambers register themselves as you find them. Done.

<img src="https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/setup-chat.png" alt="The /trial setup prompt in-game" width="800" /><br>

<details>
<summary>Screenshot: native Dialog UI on Paper 1.21.7+</summary>
<img src="https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/setup-dialog.png" alt="The /trial setup tour as a native Dialog popup" width="800" />
</details>

<details>
<summary>Old world, or you want to pick the chambers yourself?</summary>

> Auto-discovery is off by default on purpose, because on established worlds, player-built stuff made of tuff and copper, which in rare cases the plugin mistakes for a trial chamber.

Select it with WorldEdit instead:

```
//wand
/trial generate wand MyChamber
/trial snapshot create MyChamber
```

Same features either way. [Docs →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/getting-started/your-first-chamber)

</details>

<br>

### What it does

**Loot**
- Every player gets their own vault loot
- Chests, barrels, droppers and pots too, if you want
- Or make it a race: first player claims the vault, it stays shut for everyone else
- Build your own loot tables. By weight, or by plain % chance
- Different loot per chamber
- Edit it all in a menu. No YAML

**Resets**
- Chambers restore on a timer you set
- Players get a warning first
- Set it to `0` to only reset by hand

**Protection**
- Nobody breaks blocks or spawners inside a chamber
- Optional: let players tunnel in through the wall. They get no drops, the wall heals on reset

**Custom chambers**
- Use a datapack, such as [Crazy Chambers](https://modrinth.com/datapack/crazy-chambers)
- Or make rooms yourself, the plugin stitches them into a new chamber

**During a fight**
- Boss bar shows wave progress
- Spawner mobs stop killing each other
- Leftover spawners glow so nobody hunts for the last one
- Dead players can spectate their team

**After completion**
- Statistics; Vaults opened, mobs killed, chambers cleared, time spent
- Leaderboards
- 30+ PlaceholderAPI placeholders

**Under the hood**
- Paper, Purpur, Pufferfish and Folia
- Won't lag your server! Big chambers load in small pieces (smooth even with only 4GB ram)
- SQLite or MySQL
- Every message is editable in one file (fully translatable)
- Config updates itself, keeping your settings.

<br>

### Reference

<details>
<summary><strong>Commands</strong></summary>

| Command | What it does |
|---------|-------------|
| `/trial setup` | Walks you through the settings |
| `/trial menu` | Everything, in a GUI |
| `/trial list` | All your chambers |
| `/trial reset <chamber>` | Reset it now |
| `/trial snapshot create` | Turn on auto-reset for the chamber you're standing in |
| `/trial generate wand <name>` | Register your WorldEdit selection |
| `/trial dungeon generate <name>` | Build a chamber from your rooms |
| `/trial dungeon import <file>` | Import `.nbt` rooms or a datapack |
| `/trial loot set <chamber> <normal\|ominous> <table>` | Change a chamber's loot |
| `/trial stats [player]` | Stats |
| `/trial leaderboard <type>` | Top players |
| `/trial reload` | Reload config |

[All commands →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/reference/commands)

</details>

<details>
<summary><strong>Permissions</strong></summary>

| Permission | What it does | Default |
|------------|--------------|---------|
| `btc.admin` | Everything | OP |
| `btc.stats` · `btc.leaderboard` | See stats | Everyone |
| `btc.spectate` | Spectate after dying | Everyone |
| `btc.bypass.cooldown` | Skip vault cooldowns | OP |
| `btc.bypass.protection` | Build inside chambers | OP |
| `btc.discovery.notify` | Get told when a chamber is found | OP |

> Cooldowns look broken when you test as OP! Set `btc.bypass.cooldown` to `false` for yourself during testing or `deop` yourself

[All permissions →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/reference/permissions)

</details>

<details>
<summary><strong>Settings people change</strong></summary>

```yaml
global:
  default-reset-interval: 172800   # 48 hours. 0 = manual only.

vaults:
  normal-cooldown-hours: 0         # 0 = stays shut until the chamber resets
  ominous-cooldown-hours: 0
  reopen-cost-keys: 0              # keys to reopen a used vault. 0 = off

chests:
  per-player-loot: false           # true = everyone gets their own chest loot

protection:
  tunnel-breaking:
    enabled: false                 # true = players can mine in

discovery:
  enabled: true                    # find chambers automatically
  auto-snapshot: true              # let them reset
```

[config.yml →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/config.yml) · [loot.yml →](https://esmp-fun.gitbook.io/plugins/better-trial-chambers/configuration/loot.yml)

</details>

<details>
<summary><strong>Works with</strong></summary>

> None of these are required!

- WorldEdit or FAWE
- WorldGuard
- PlaceholderAPI
- Vault
- LuckPerms
- Nexo
- ItemsAdder
- Oraxen
- CraftEngine
- MythicCrucible

</details>

<br>

### Want more?

This plugin stays free. Three paid add-ons, if you want them — [esmp.fun/plugins](https://esmp.fun/plugins):

- **Mythic Trials** — chambers get harder every time a player clears them -> 25 difficulty tiers with custom tweaked smarter vanilla MobAI, better rewards and optional seasons
- **Wild Spawners** — (custom-mob) trial spawners, anywhere on the map and/or in your shops
- **Vault Crates** — crates that use real Trial Vaults instead of chests (no resource packs!)

<br>

### Help

- **[Docs](https://esmp-fun.gitbook.io/plugins/better-trial-chambers)** — start here
- **[Discord](https://discord.gg/qwYcTpHsNC)** — ask me directly. Suggestions often ship the same week (more like hours, in most cases)
- **[Bug reports](https://github.com/ESMP-FUN/BetterTrialChambers/issues)** · **[Source](https://github.com/ESMP-FUN/BetterTrialChambers)**

<br>

<div align="center">

![JDK21](https://img.shields.io/badge/Paper_%7C_Folia_%7C_Purpur_%C2%BB_1.21.x_-_Java_21%2B-lavender?style=flat&logoSize=amd) ![JDK25](https://img.shields.io/badge/Paper_%7C_Folia_%7C_Purpur_%C2%BB_26.x_-_Java_25%2B-royalblue?style=flat&logoSize=amd)

Made with Kotlin by [darkstarworks](https://github.com/ESMP-FUN)<br>

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-trial-chambers%3Fmetric%3Dservers%26color%3Dorange%26icon%3D1&style=flat)](https://faststats.dev/project/better-trial-chambers)<br>

</div>

# Permissions

## How to grant a permission

1. Install a permissions plugin (LuckPerms is the common choice).
2. Grant a node to a player or group. With LuckPerms:
   - One player: `/lp user Steve permission set btc.admin.reset true`
   - A group: `/lp group builder permission set btc.bypass.protection true`
   - Everything for admins: `/lp group admin permission set btc.admin.* true`
3. To take a node away, set it to `false` (this also overrides a wildcard: `btc.admin.*` true plus `btc.admin.reload` false gives every admin node except reload).

**Defaults if you grant nothing:** operators get all `btc.admin.*` nodes. Every player gets `btc.stats`, `btc.leaderboard`, and `btc.spectate`.

{% hint style="info" %}
Since 1.7.1 each `/trial` subcommand checks its own node, so a normal player can use `/trial stats` and `/trial leaderboard` without any admin access.
{% endhint %}

***

## Legacy `tcp.*` names

Every permission below is a `btc.*` node. The old `tcp.*` names from before v2.0 still work: each one is an alias that grants its `btc.*` counterpart one-to-one (for example `tcp.admin.reset` grants `btc.admin.reset`). The aliases default to `false`, so they only do anything if you explicitly grant them. Use the `btc.*` names for anything new.

***

## All permission nodes

### Admin

| Node | What it grants | Default |
| --- | --- | --- |
| `btc.admin.*` | Every admin node in this table, including `btc.admin`, `btc.admin.setup` and `btc.give`. Not the bypass or player nodes. | op |
| `btc.admin` | `/trial list`, `/trial info`, `/trial update`, and update notices on join. Not a wildcard. | op |
| `btc.admin.setup` | `/trial setup` and `/trial setup continue` (the guided settings tour), plus the op-join setup reminder. | op |
| `btc.admin.generate` | `/trial generate <mode>` and `/trial dungeon ...` (register chambers, procedural assembly). | op |
| `btc.admin.create` | `/trial delete <chamber>` and `/trial setexit <chamber>`. | op |
| `btc.admin.scan` | `/trial scan <chamber>` and `/trial scan add`. | op |
| `btc.admin.snapshot` | `/trial snapshot create / update / restore / missing`. | op |
| `btc.admin.reset` | `/trial reset <chamber>` and the reset-confirmation commands. | op |
| `btc.admin.pause` | `/trial pause <chamber>` and `/trial resume <chamber>`. | op |
| `btc.admin.key` | `/trial key give` and `/trial key check`. | op |
| `btc.admin.vault` | `/trial vault reset` and `/trial vault unlockall`. | op |
| `btc.admin.loot` | `/trial loot set / clear / info / list / audit` (per-chamber loot tables). | op |
| `btc.admin.containers` | The Container Loot GUI view and `/trial container ...` (per-player container loot). Only matters when `chests.per-player-loot` is on. | op |
| `btc.admin.mobs` | `/trial mobs providers` and `/trial mobs <chamber> provider / add / remove / list` (custom mob providers). | op |
| `btc.admin.menu` | `/trial menu` (opens the admin GUI). GUI actions still check the feature node behind them. | op |
| `btc.admin.reload` | `/trial reload`. | op |
| `btc.admin.stats` | `/trial stats <player>` (view anyone's stats). | op |
| `btc.give` | `/trial give <preset> [player] [amount]` (hand out preconfigured trial-spawner items). | op |

### Player

| Node | What it grants | Default |
| --- | --- | --- |
| `btc.stats` | `/trial stats` (your own stats). | all players |
| `btc.leaderboard` | `/trial leaderboard <type>` and its aliases `lb` and `top`. | all players |
| `btc.spectate` | After dying inside a chamber, accept the offer to spectate it: switched to spectator mode, teleported to the chamber centre, kept inside its bounds, and restored to your normal mode on exit. | all players |

### Bypass

| Node | What it grants | Default |
| --- | --- | --- |
| `btc.bypass.cooldown` | Open vaults with no personal cooldown. Removes vault progression, so hand out with care. | op |
| `btc.bypass.protection` | Break and place blocks, and open containers, inside protected chambers. Do not give to regular players. | op |
| `btc.bypass.vaultplace` | Place VAULT blocks outside a registered chamber (exempt from `protection.block-wild-vault-placement`). Needed for creative builds and out-of-chamber crate setups. | op |
| `btc.bypass.droplock` | Pick up another player's dropped vault loot and spawner key drops during the owner-only grace window. Only relevant with `vaults.drop-loot-at-vault: true`. | op |
| `btc.bypass.residence` | Create or expand a Residence claim that overlaps a chamber (exempt from that plugin's claim shield). | op |
| `btc.bypass.lands` | Same, for Lands claims. | op |
| `btc.bypass.griefprevention` | Same, for GriefPrevention claims. | op |
| `btc.bypass.entry` | Enter chambers when you have turned on `protection.prevent-teleport-into-chamber` or `protection.prevent-entry-without-permission`. Both are off by default, so this only matters once you enable one (for example, to gate a premium dungeon to a `vip` group). | op |

### Notifications

| Node | What it grants | Default |
| --- | --- | --- |
| `btc.discovery.notify` | A chat message whenever auto-discovery (`discovery.enabled: true`) registers a natural chamber, showing its auto-name and centre. Also needs `discovery.notify-ops: true` (the default). | op |

***

## Permission hierarchy

```
btc.admin.*                (wildcard, grants every child below)
  ├─ btc.admin.setup       (/trial setup tour)
  ├─ btc.admin.generate    (register chambers, /trial dungeon)
  ├─ btc.admin.create      (delete chambers, set exit)
  ├─ btc.admin.scan        (scan chambers)
  ├─ btc.admin.snapshot    (manage snapshots)
  ├─ btc.admin.reset       (force resets)
  ├─ btc.admin.pause       (pause / resume chambers)
  ├─ btc.admin.key         (manage keys)
  ├─ btc.admin.vault       (manage vault cooldowns)
  ├─ btc.admin.loot        (per-chamber loot tables)
  ├─ btc.admin.containers  (per-player container loot)
  ├─ btc.admin.mobs        (custom mob providers)
  ├─ btc.admin.menu        (open the GUI)
  ├─ btc.admin.reload      (reload config)
  ├─ btc.admin.stats       (view others' stats)
  ├─ btc.admin             (view chambers: /trial list, /trial info, /trial update)
  └─ btc.give              (/trial give spawner presets)

btc.stats                  (view own stats)          default: all
btc.leaderboard            (view leaderboards)        default: all
btc.spectate               (spectate after death)     default: all

btc.bypass.cooldown        (no vault cooldowns)
btc.bypass.protection      (build in protected chambers)
btc.bypass.vaultplace      (place wild vault blocks)
btc.bypass.droplock        (pick up others' loot / key drops)
btc.bypass.residence       (claim over chambers with Residence)
btc.bypass.lands           (claim over chambers with Lands)
btc.bypass.griefprevention (claim over chambers with GriefPrevention)
btc.bypass.entry           (enter entry/teleport-locked chambers)

btc.discovery.notify       (auto-discovery notifications)
```

`btc.admin` on its own is only the read-only view access; it is not a wildcard, but `btc.admin.*` includes it.

***

## Ready-made groups

<details>

<summary><strong>Player (the default)</strong></summary>

```
btc.stats
btc.leaderboard
btc.spectate
```

</details>

<details>

<summary><strong>VIP (optional faster loot)</strong></summary>

```
btc.stats
btc.leaderboard
btc.spectate
btc.bypass.cooldown
```

Giving `btc.bypass.cooldown` removes vault cooldowns entirely. Weigh that against your economy.

</details>

<details>

<summary><strong>Helper / moderator</strong></summary>

```
btc.admin              (view chamber info)
btc.admin.stats        (view player stats)
btc.admin.key          (give keys)
btc.admin.vault        (reset vault cooldowns)
btc.admin.reset        (force resets)
```

Cannot create or delete chambers, change config, or make snapshots.

</details>

<details>

<summary><strong>Builder</strong></summary>

```
btc.admin.generate
btc.admin.create
btc.admin.scan
btc.admin.snapshot
btc.admin.reset
btc.bypass.protection
```

Registers, scans, snapshots and builds inside chambers. Cannot manage keys, vaults, or config.

</details>

<details>

<summary><strong>Admin</strong></summary>

```
btc.admin.*
```

</details>

***

## LuckPerms quick reference

```
/lp user Steve permission set btc.admin.* true
/lp creategroup vip
/lp group vip permission set btc.bypass.cooldown true
/lp user Bob parent add vip
/lp user Diana permission settemp btc.admin.* true 24h
/lp user Steve permission set btc.admin.scan true world=world_nether
```

Negative permissions work too: grant `btc.admin.*` then set `btc.admin.reload` to `false` to keep reload away from a group.

***

## Cheat sheet

| Role | Nodes | Purpose |
| --- | --- | --- |
| Player | `btc.stats`, `btc.leaderboard`, `btc.spectate` | View stats, compete, spectate (default) |
| VIP | above + `btc.bypass.cooldown` | Optional faster loot |
| Helper | `btc.admin`, `btc.admin.key`, `btc.admin.vault`, `btc.admin.reset` | Help players, run events |
| Mod | Helper + `btc.admin.stats` | Monitor players |
| Builder | `btc.admin.generate`, `btc.admin.create`, `btc.admin.scan`, `btc.admin.snapshot`, `btc.bypass.protection` | Build and register chambers |
| Admin | `btc.admin.*` | Everything |

***

## Related pages

{% content-ref url="commands.md" %}
[commands.md](commands.md)
{% endcontent-ref %}

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

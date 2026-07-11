# Permissions

BetterTrialChambers uses a hierarchical permission system. Grant broad access with wildcards or fine-tune with specific permissions.

{% hint style="info" %}
**Permission Plugin Required:** Use LuckPerms, PermissionsEx, or any Bukkit-compatible permissions plugin.

**Default OP Permissions:** All `tcp.admin.*` permissions default to operators. Players get `tcp.stats` and `tcp.leaderboard` by default.
{% endhint %}

<div data-gb-custom-block data-tag="hint" data-style="warning">

**Fixed in 1.7.1:** Prior versions gated the entire `/trial` command behind `tcp.admin`, so non-OP players could not use `/trial stats` or `/trial leaderboard` even though those default to everyone. As of 1.7.1 each subcommand checks its own permission, so the defaults documented on this page actually apply. Admins see no change; regular players gain access to the stats and leaderboard commands as intended.

</div>

***

## Quick Setup

### Full Admin Access

```yaml
permissions:
  - tcp.admin.*
```

Grants ALL admin permissions (add, scan, reset, manage keys, etc.)

### Player Access (View Stats Only)

```yaml
permissions:
  - tcp.stats
  - tcp.leaderboard
```

Players can view their own stats and leaderboards. **This is default!**

### Moderator Access (Manage Chambers, No Config)

```yaml
permissions:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.stats
```

Can reset chambers, manage keys/vaults, view player stats, but can't modify chamber structure or reload config.

***

## Complete Permission List

### Admin Permissions

<details>

<summary><code>tcp.admin.*</code></summary>

**Description:** All admin permissions (wildcard) **Default:** Operators only **Grants access to:**

* All commands listed below
* Complete control over chambers
* Configuration management

**Use this for:** Server owners, head admins

</details>

<details>

<summary><code>tcp.admin.setup</code></summary>

**Description:** Use the guided settings tour **Default:** Operators only **Allows:**

* `/trial setup` / `/trial setup continue` - Walk through the main settings (also gates the op-join setup reminder)

**Use this for:** Anyone you trust to change plugin settings

</details>

<details>

<summary><code>tcp.admin.generate</code></summary>

**Description:** Register chambers **Default:** Operators only **Allows:**

* `/trial generate <mode> <args>` - Register chambers from WorldEdit selection, coordinates, or blocks
* `/trial dungeon …` - Procedural dungeon assembly (also `tcp.admin.generate`)

**Use this for:** Staff who register chamber infrastructure

</details>

<details>

<summary><code>tcp.admin.create</code></summary>

**Description:** Delete chambers and set exit points **Default:** Operators only **Allows:**

* `/trial delete <chamber>` - Delete a chamber and its data
* `/trial setexit <chamber>` - Set a chamber's exit location

**Use this for:** Staff who manage chamber lifecycle. _(Note: `/trial delete` and `/trial setexit` require this node, **not**_ _`tcp.admin.generate`.)_

</details>

<details>

<summary><code>tcp.admin.scan</code></summary>

**Description:** Scan chambers for vaults and spawners **Default:** Operators only **Allows:**

* `/trial scan <chamber>` - Detect vaults/spawners

**Use this for:** Staff who set up chambers (usually paired with `tcp.admin.create`)

</details>

<details>

<summary><code>tcp.admin.snapshot</code></summary>

**Description:** Manage chamber snapshots **Default:** Operators only **Allows:**

* `/trial snapshot create <chamber>` - Create snapshots
* `/trial snapshot restore <chamber>` - Restore from snapshots

**Use this for:** Staff who maintain chambers and handle resets

</details>

<details>

<summary><code>tcp.admin.reset</code></summary>

**Description:** Force chamber resets **Default:** Operators only **Allows:**

* `/trial reset <chamber>` - Immediately reset a chamber

**Use this for:** Moderators who manage events and handle player issues

</details>

<details>

<summary><code>tcp.admin.key</code></summary>

**Description:** Manage trial keys **Default:** Operators only **Allows:**

* `/trial key give <player> <amount> [type]` - Give keys to players
* `/trial key check <player>` - Check player's key count

**Use this for:** Staff who run events or compensate players

</details>

<details>

<summary><code>tcp.admin.vault</code></summary>

**Description:** Manage vault cooldowns **Default:** Operators only **Allows:**

* `/trial vault reset <chamber> <player> [type]` - Reset vault cooldowns

**Use this for:** Staff who handle player support and bug compensation

</details>

<details>

<summary><code>tcp.admin.reload</code></summary>

**Description:** Reload plugin configuration **Default:** Operators only **Allows:**

* `/trial reload` - Reload config files

**Use this for:** Admins who tune configuration

</details>

<details>

<summary><code>tcp.admin.mobs</code></summary>

**Description:** Configure per-chamber custom mob providers _(1.3.0+)_ **Default:** Operators only **Allows:**

* `/trial mobs providers` - List registered providers and availability
* `/trial mobs <chamber> provider <id|vanilla|none>` - Set the provider for a chamber
* `/trial mobs <chamber> add normal|ominous <mobId>` - Add a mob id to the chamber's pool
* `/trial mobs <chamber> remove normal|ominous <mobId>` - Remove a mob id
* `/trial mobs <chamber> list` - Show the chamber's current config

**Use this for:** Admins who manage MythicMobs / EliteMobs / EcoMobs / LevelledMobs / InfernalMobs / Citizens integrations on their chambers. See [Custom Mobs](../configuration/custom-mobs.md) for the per-provider mob-id format.

</details>

<details>

<summary><code>tcp.admin.loot</code></summary>

**Description:** Manage per-chamber loot tables **Default:** Operators only **Allows:**

* `/trial loot set <chamber> <normal|ominous> <table>` - Override a chamber's loot table
* `/trial loot clear <chamber> [normal|ominous|all]` - Remove a per-chamber override
* `/trial loot audit` - List pre-1.5.0 loot entries that lost their NBT

**Use this for:** Admins curating per-chamber loot.

</details>

<details>

<summary><code>tcp.admin.containers</code></summary>

**Description:** Manage per-player container loot _(1.5.7+, reworked 1.6.3)_ **Default:** Operators only **Allows:**

* The chamber GUI's **Container Loot** view — edit a container to create a loot **override** (every player then receives a copy of it), or revert it to vanilla per-player rolls
* `/trial container <list|materialize|edit|resetone|reset|clearcopies|tp> <chamber>` - the same management from the CLI

Only relevant when `chests.per-player-loot` is enabled. Editing is GUI/command-only — there is no in-world editing. Without an override, every player gets their own freshly-rolled loot.

</details>

<details>

<summary><code>tcp.give</code></summary>

**Description:** Hand out preconfigured trial-spawner items _(1.3.1+)_ **Default:** Operators only **Allows:**

* `/trial give <preset> [player] [amount]` - Receive (or send) a `trial_spawner` item with `block_entity_data` baked in from a named preset

**Use this for:** Admins who deploy custom trial-spawner pools defined in datapacks. See [spawner\_presets.yml](../configuration/spawner-presets.yml.md) for the preset file format.

</details>

<details>

<summary><code>tcp.admin.pause</code></summary>

**Description:** Pause and resume chambers _(1.4.3+)_ **Default:** Operators only **Allows:**

* `/trial pause <chamber>` - Suspend all active chamber behavior while preserving the DB record
* `/trial resume <chamber>` - Restore a paused chamber to normal operation

**Use this for:** Admins managing chambers on hardcore/anarchy servers where griefing protection is intentionally off. Pausing freezes the chamber's record without deleting any data.

</details>

<details>

<summary><code>tcp.admin.menu</code></summary>

**Description:** Open the in-game admin GUI **Default:** Operators only **Allows:**

* `/trial menu` - Open the full admin GUI (chambers, loot editing, stats, settings)

**Use this for:** Staff who manage chambers through the GUI instead of commands. Note that GUI actions still respect the underlying feature permissions.

</details>

<details>

<summary><code>tcp.admin.stats</code></summary>

**Description:** View other players' statistics **Default:** Operators only **Allows:**

* `/trial stats <player>` - View any player's stats

**Use this for:** Staff who monitor player activity

</details>

<details>

<summary><code>tcp.admin</code></summary>

**Description:** View chamber information **Default:** Operators only **Allows:**

* `/trial list` - List all chambers
* `/trial info <chamber>` - View chamber details
* `/trial update …` - Check for and install plugin updates (see [Commands](commands.md))

Also receives plugin update notifications on join.

**Use this for:** Read-only access to chamber data

{% hint style="info" %}
**Note:** `tcp.admin` is NOT a wildcard. Use `tcp.admin.*` for all admin permissions (since 1.7.1 the wildcard also grants `tcp.admin` itself).
{% endhint %}

</details>

***

### Player Permissions

<details>

<summary><code>tcp.stats</code></summary>

**Description:** View own statistics **Default:** **All players** (true) **Allows:**

* `/trial stats` - View your own stats

**Typical usage:** Default permission for all players

</details>

<details>

<summary><code>tcp.leaderboard</code></summary>

**Description:** View leaderboards **Default:** **All players** (true) **Allows:**

* `/trial leaderboard <type>` - View top players
* `/trial lb <type>` - Alias
* `/trial top <type>` - Alias

**Typical usage:** Default permission for all players

</details>

<details>

<summary><code>tcp.spectate</code></summary>

**Description:** Use spectator mode after death in chambers **Default:** **All players** (true) **Allows:**

* Spectate a chamber after dying inside it
* Type "spectate" in chat to accept spectator mode offer
* Type "exit" to leave spectator mode

**Effect:**

* Switched to GameMode.SPECTATOR on acceptance
* Teleported to chamber center
* Confined to chamber boundaries (with configurable buffer)
* Restored to previous game mode on exit

**Typical usage:** Default permission for all players (enhances multiplayer experience)

{% hint style="info" %}
**Spectator Mode Feature:** When a player dies in a chamber, they're offered the chance to spectate instead of immediately respawning. This lets them watch teammates complete the challenge.
{% endhint %}

</details>

***

### Bypass Permissions

<details>

<summary><code>tcp.bypass.cooldown</code></summary>

**Description:** Bypass vault cooldowns **Default:** Operators only **Effect:**

* Open vaults regardless of personal cooldown
* No waiting period between loot

**Use this for:**

* VIP ranks (be careful with balance!)
* Staff testing chambers
* Special event participants

{% hint style="warning" %}
**Balance Warning:** Giving this to regular players removes cooldown mechanics entirely. Consider carefully!
{% endhint %}

</details>

<details>

<summary><code>tcp.bypass.protection</code></summary>

**Description:** Bypass chamber protection **Default:** Operators only **Effect:**

* Break blocks in protected chambers
* Place blocks in protected chambers
* Access protected containers

**Use this for:**

* Staff building/modifying chambers
* WorldEdit operations inside chambers

{% hint style="danger" %}
**DO NOT give to regular players!** This allows complete modification of protected chambers.
{% endhint %}

</details>

<details>

<summary><code>tcp.bypass.vaultplace</code></summary>

**Description:** Place VAULT blocks outside registered chambers _(1.5.7+)_ **Default:** Operators only **Effect:**

* Exempt from `protection.block-wild-vault-placement`
* Needed for creative builds using vault blocks and for setting up out-of-chamber crates (e.g. Vault Crates)

</details>

<details>

<summary><code>tcp.bypass.droplock</code></summary>

**Description:** Bypass owner-only pickup on dropped vault loot _(1.2.28+)_ **Default:** Operators only **Effect:**

* Pick up another player's vault loot drops during the owner-only grace window (`vaults.drop-loot-owner-grace-seconds`)
* Only relevant when `vaults.drop-loot-at-vault: true`

**Use this for:** Staff cleaning up dropped items or investigating loot disputes.

</details>

<details>

<summary><code>tcp.bypass.residence</code> / <code>tcp.bypass.lands</code> / <code>tcp.bypass.griefprevention</code></summary>

**Description:** Create or expand a land claim that overlaps a registered Trial Chamber, with the matching plugin _(1.5.15+)_ **Default:** Operators only **Effect:**

* Exempt from the corresponding `protection.<plugin>-integration` claim shield
* Each permission is scoped to one plugin (Residence / Lands / GriefPrevention), so you can allow staff to claim near chambers with one plugin without affecting the others

**Use this for:** Trusted staff who legitimately need to claim land that touches a chamber.

{% hint style="warning" %}
Giving this to regular players defeats the owner-only drop protection — they can snatch other players' vault loot.
{% endhint %}

</details>

<details>

<summary><code>tcp.bypass.entry</code></summary>

**Description:** Enter chambers when entry/teleport restrictions are on _(1.5.20+)_ **Default:** Operators only **Effect:**

* Exempt from `protection.prevent-teleport-into-chamber` (can teleport into chambers via `/tpa`, `/home`, ender pearls, etc.)
* Exempt from `protection.prevent-entry-without-permission` (can walk into rank-gated chambers)
* Both restrictions are off by default; this permission only matters when you enable one

**Use this for:** Granting a rank access to chambers when you've made them teleport-locked or rank-restricted — e.g. give `tcp.bypass.entry` to a `vip` group so only VIPs can enter a premium dungeon.

</details>

***

### Notification Permissions

<details>

<summary><code>tcp.discovery.notify</code></summary>

**Description:** Receive broadcast when auto-discovery registers a new chamber _(1.2.25+)_ **Default:** Operators only **Effect:**

* Get a chat message whenever `discovery.enabled: true` causes a natural chamber to be auto-registered
* Shows the chamber's auto-generated name and center coordinates

**Use this for:**

* Admins monitoring auto-discovery rollout on existing worlds
* Catching false-positive registrations early so you can fine-tune `discovery.*` thresholds

{% hint style="info" %}
Only fires when `discovery.notify-ops: true` (the default) in config.yml. You can also see every registration in the server log if `debug.verbose-logging: true`.
{% endhint %}

</details>

***

## Permission Groups Examples

<details>

<summary><strong>Example 1: Player Rank</strong></summary>

```yaml
group: default
permissions:
  - tcp.stats
  - tcp.leaderboard
```

Players can view stats and compete on leaderboards. This is the default!

</details>

<details>

<summary><strong>Example 2: VIP Rank</strong></summary>

```yaml
group: vip
permissions:
  - tcp.stats
  - tcp.leaderboard
  - tcp.bypass.cooldown  # Optional: No vault cooldowns
```

VIPs get instant vault access (no cooldowns). **Balance carefully!**

</details>

<details>

<summary><strong>Example 3: Helper/Moderator Rank</strong></summary>

```yaml
group: helper
permissions:
  - tcp.admin
  - tcp.admin.stats
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.reset
```

Helpers can:

* View chamber info
* View player stats
* Give keys to players
* Reset vault cooldowns (for bug compensation)
* Force chamber resets

**Cannot:**

* Create/delete chambers
* Modify configuration
* Create snapshots

</details>

<details>

<summary><strong>Example 4: Builder Rank</strong></summary>

```yaml
group: builder
permissions:
  - tcp.admin.generate
  - tcp.admin.scan
  - tcp.admin.snapshot
  - tcp.admin.reset
  - tcp.bypass.protection
```

Builders can:

* Register and delete chambers
* Scan for vaults/spawners
* Create/restore snapshots
* Build inside protected chambers

**Cannot:**

* Manage keys or vaults
* Reload configuration

</details>

<details>

<summary><strong>Example 5: Admin Rank</strong></summary>

```yaml
group: admin
permissions:
  - tcp.admin.*
```

Full access to everything. Simple!

</details>

***

## LuckPerms Examples

<details>

<summary><strong>Grant Full Admin Access</strong></summary>

```
/lp user Steve permission set tcp.admin.*
```

</details>

<details>

<summary><strong>Grant Moderator Access</strong></summary>

```
/lp user Alex permission set tcp.admin.reset
/lp user Alex permission set tcp.admin.key
/lp user Alex permission set tcp.admin.vault
/lp user Alex permission set tcp.admin.stats
```

</details>

<details>

<summary><strong>Create a VIP Group (No Cooldowns)</strong></summary>

```
/lp creategroup vip
/lp group vip permission set tcp.bypass.cooldown true
/lp user Bob parent add vip
```

</details>

<details>

<summary><strong>Create a Builder Group</strong></summary>

```
/lp creategroup builder
/lp group builder permission set tcp.admin.generate
/lp group builder permission set tcp.admin.scan
/lp group builder permission set tcp.admin.snapshot
/lp group builder permission set tcp.bypass.protection
/lp user Charlie parent add builder
```

</details>

<details>

<summary><strong>Grant Temporary Admin Access (24 hours)</strong></summary>

```
/lp user Diana permission settemp tcp.admin.* true 24h
```

</details>

***

## Per-World Permissions

Want staff to manage chambers only in certain worlds?

<details>

<summary><strong>LuckPerms Example</strong></summary>

```
/lp user Steve permission set tcp.admin.create true world=world_nether
/lp user Steve permission set tcp.admin.scan true world=world_nether
```

Steve can only create/scan chambers in the nether.

</details>

***

## Permission Hierarchy

```
tcp.admin.*                (wildcard — grants the children below)
  ├─ tcp.admin.generate    (Register chambers, /trial dungeon)
  ├─ tcp.admin.create      (Delete chambers, set exit)
  ├─ tcp.admin.scan        (Scan chambers)
  ├─ tcp.admin.snapshot    (Manage snapshots)
  ├─ tcp.admin.reset       (Force resets)
  ├─ tcp.admin.pause       (Pause / resume chambers)
  ├─ tcp.admin.key         (Manage keys)
  ├─ tcp.admin.vault       (Manage vaults)
  ├─ tcp.admin.menu        (Open the GUI)
  ├─ tcp.admin.reload      (Reload config)
  ├─ tcp.admin.stats       (View others' stats)
  ├─ tcp.admin.loot        (Per-chamber loot tables)
  ├─ tcp.admin.containers  (Per-player container loot templates)
  ├─ tcp.admin.mobs        (Per-chamber custom mob providers)
  ├─ tcp.admin             (View chambers, /trial list + /trial info)
  └─ tcp.give              (/trial give spawner presets)

tcp.admin                  (View chambers - NOT a wildcard, but granted by tcp.admin.*)

tcp.stats                  (View own stats)
tcp.leaderboard            (View leaderboards)
tcp.spectate               (Spectate after death)

tcp.bypass.cooldown        (No vault cooldowns)
tcp.bypass.protection      (Build in protected chambers)
tcp.bypass.vaultplace      (Place wild vaults)
tcp.bypass.droplock        (Bypass owner-only loot pickup)
tcp.bypass.residence       (Claim chambers with Residence)
tcp.bypass.lands           (Claim chambers with Lands)
tcp.bypass.griefprevention (Claim chambers with GriefPrevention)
tcp.discovery.notify       (Auto-discovery notifications)
```

***

## Common Setups

<details>

<summary><strong>Survival Server</strong></summary>

```yaml
default:
  - tcp.stats
  - tcp.leaderboard

vip:
  - tcp.stats
  - tcp.leaderboard
  # No bypass perms for balance

moderator:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.stats

admin:
  - tcp.admin.*
```

**Philosophy:** Keep VIPs balanced, give mods tools to help players, admins control everything.

</details>

<details>

<summary><strong>Creative/Building Server</strong></summary>

```yaml
default:
  - tcp.stats
  - tcp.leaderboard

builder:
  - tcp.admin.generate
  - tcp.admin.scan
  - tcp.admin.snapshot
  - tcp.bypass.protection

admin:
  - tcp.admin.*
```

**Philosophy:** Let builders register chambers freely, admins handle config/keys.

</details>

<details>

<summary><strong>Event/Minigame Server</strong></summary>

```yaml
default:
  - tcp.stats
  - tcp.leaderboard

event-participant:
  - tcp.bypass.cooldown  # Fast loot for events

event-host:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault

admin:
  - tcp.admin.*
```

**Philosophy:** Event participants get faster loot, hosts can reset chambers and give rewards.

</details>

***

## FAQ

**"Do I need `tcp.admin` for `tcp.admin.create`?"** No! Specific permissions (like `tcp.admin.create`) work independently. `tcp.admin` only grants access to `/trial list` and `/trial info`.

**"What's the difference between `tcp.admin` and `tcp.admin.*`?"**

* `tcp.admin` = View chambers (`/trial list`, `/trial info`)
* `tcp.admin.*` = ALL admin permissions (wildcard)

**"Can I use negative permissions to remove specific access?"** Yes! With LuckPerms or similar plugins:

```
/lp user Steve permission set tcp.admin.* true
/lp user Steve permission set tcp.admin.reload false
```

Steve gets all admin perms EXCEPT reload.

**"Does `tcp.bypass.cooldown` work retroactively?"** Yes! If a player has this permission, they can open vaults immediately regardless of existing cooldowns.

**"Can I give permissions temporarily?"** Yes, with LuckPerms:

```
/lp user Steve permission settemp tcp.admin.* true 1d
```

**"Do these permissions work with permission inheritance?"** Yes! If a group has `tcp.admin.*`, all members inherit all child permissions automatically.

***

## Related Pages

{% content-ref url="commands.md" %}
[commands.md](commands.md)
{% endcontent-ref %}

See which commands require which permissions.

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

Protection settings and their interaction with bypass permissions.

***

## Security Best Practices

{% hint style="success" %}
**Use the least privilege principle:** Only grant permissions users actually need. Don't give `tcp.admin.*` to everyone.
{% endhint %}

{% hint style="warning" %}
**Be careful with bypass permissions:** `tcp.bypass.cooldown` removes progression. `tcp.bypass.protection` allows chamber destruction.
{% endhint %}

{% hint style="info" %}
**Separate builder and admin permissions:** Builders need `tcp.admin.create` + `tcp.bypass.protection`. They don't need `tcp.admin.reload` or key management.
{% endhint %}

{% hint style="danger" %}
**Never give regular players `tcp.bypass.protection`** unless you want them modifying chambers freely!
{% endhint %}

***

## Quick Permission Cheat Sheet

| Role        | Permissions                                                                           | Why                           |
| ----------- | ------------------------------------------------------------------------------------- | ----------------------------- |
| **Player**  | `tcp.stats`, `tcp.leaderboard`, `tcp.spectate`                                        | View stats, compete, spectate |
| **VIP**     | Same + `tcp.bypass.cooldown`                                                          | Faster loot (optional)        |
| **Helper**  | `tcp.admin.key`, `tcp.admin.vault`, `tcp.admin.reset`                                 | Help players, manage events   |
| **Mod**     | Helper + `tcp.admin.stats`                                                            | Monitor players               |
| **Builder** | `tcp.admin.generate`, `tcp.admin.scan`, `tcp.admin.snapshot`, `tcp.bypass.protection` | Register chambers             |
| **Admin**   | `tcp.admin.*`                                                                         | Full control                  |

Use this as a starting point and adjust to your server's needs!

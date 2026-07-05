# Commands

All commands start with `/tcp` (short for TrialChamberPro). Most require specific permissions—check the [Permissions](permissions.md) page for details.

{% hint style="info" %}
**Aliases:** `/tcp`, `/trialchamberpro`, `/tcpro`

**Tab completion:** Available for all commands! Press `Tab` while typing for suggestions.
{% endhint %}

***

## Quick Reference

| Command                                                                          | Description                                                                                                                                      | Permission                      |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| `/tcp help`                                                                      | Show command list                                                                                                                                | None                            |
| `/tcp setup`                                                                     | Guided, opt-in tour of the main settings (Dialog UI, or clickable chat on older servers)                                                         | `tcp.admin.setup`               |
| `/tcp menu [chamber]`                                                            | Open admin GUI (with a chamber name: jump straight to that chamber's detail view)                                                                | `tcp.admin.menu`                |
| `/tcp generate <value\|coords\|wand\|blocks>`                                    | Register chamber from saved var, coords, WE wand, or by block amount                                                                             | `tcp.admin.generate`            |
| `/tcp scan <chamber>`                                                            | Scan for vaults/spawners                                                                                                                         | `tcp.admin.scan`                |
| `/tcp scan add <chamber>`                                                        | Grow a chamber's bounds into sections discovery clipped, then re-scan                                                                            | `tcp.admin.scan`                |
| `/tcp setexit <chamber>`                                                         | Set exit location                                                                                                                                | `tcp.admin.create`              |
| `/tcp snapshot <create\|update\|restore> [chamber]`                              | Manage snapshots (omit the name to target the chamber you're standing in)                                                                        | `tcp.admin.snapshot`            |
| `/tcp snapshot create all [force]`                                               | Backfill snapshots for all chambers missing one (staggered); `force` re-does all                                                                 | `tcp.admin.snapshot`            |
| `/tcp snapshot missing [page]`                                                   | List chambers with no snapshot, with clickable `[Create]`                                                                                        | `tcp.admin.snapshot`            |
| `/tcp reset <chamber>`                                                           | Force chamber reset                                                                                                                              | `tcp.admin.reset`               |
| `/tcp reset pending`                                                             | List chambers awaiting reset confirmation                                                                                                        | `tcp.admin.reset`               |
| `/tcp reset confirm <chamber\|all>`                                              | Confirm queued reset(s) (when confirmation mode is on)                                                                                           | `tcp.admin.reset`               |
| `/tcp list [page\|current]`                                                      | List chambers (paginated, interactive: click a name to copy it, click `[menu]` to open its GUI); `current` finds the chamber you're in / nearest | `tcp.admin`                     |
| `/tcp dungeon <pos1\|pos2\|capture\|generate\|list\|delete>`                     | Procedural dungeon generation from room templates                                                                                                | `tcp.admin.generate`            |
| `/tcp info [chamber]`                                                            | Show plugin info, or chamber details if specified                                                                                                | `tcp.admin`                     |
| `/tcp delete <chamber>`                                                          | Delete a chamber                                                                                                                                 | `tcp.admin.create`              |
| `/tcp loot set <chamber> <normal\|ominous> <table>`                              | Override a chamber's loot table                                                                                                                  | `tcp.admin.loot`                |
| `/tcp loot clear <chamber> [normal\|ominous\|all]`                               | Remove per-chamber loot override                                                                                                                 | `tcp.admin.loot`                |
| `/tcp loot audit`                                                                | List pre-1.5.0 loot entries that lost their NBT                                                                                                  | `tcp.admin.loot`                |
| `/tcp container <list\|materialize\|reset\|resetone\|clearcopies\|tp\|edit> <chamber> [#]` | Manage per-player container loot (list, edit overrides, revert to vanilla)                                                                | `tcp.admin.containers`          |
| `/tcp mobs providers`                                                            | List registered mob providers and their availability                                                                                             | `tcp.admin.mobs`                |
| `/tcp mobs <chamber> provider <id\|vanilla\|none>`                               | Set a chamber's custom mob provider                                                                                                              | `tcp.admin.mobs`                |
| `/tcp mobs <chamber> add normal\|ominous <mobId>`                                | Add a mob id to a chamber's pool                                                                                                                 | `tcp.admin.mobs`                |
| `/tcp mobs <chamber> remove normal\|ominous <mobId>`                             | Remove a mob id from a chamber's pool                                                                                                            | `tcp.admin.mobs`                |
| `/tcp mobs <chamber> list`                                                       | Show a chamber's mob provider config                                                                                                             | `tcp.admin.mobs`                |
| `/tcp give <preset> [player] [amount]`                                           | Give a preconfigured trial-spawner item — see [spawner\_presets.yml](../configuration/spawner-presets.yml.md)                                    | `tcp.give`                      |
| `/tcp pause <chamber>`                                                           | Pause a chamber (suspends resets, protection, vault interactions)                                                                                | `tcp.admin.pause`               |
| `/tcp resume <chamber>`                                                          | Resume a paused chamber                                                                                                                          | `tcp.admin.pause`               |
| `/tcp vault reset <chamber> <player>`                                            | Reset vault cooldowns                                                                                                                            | `tcp.admin.vault`               |
| `/tcp key give <player> <amount>`                                                | Give trial keys                                                                                                                                  | `tcp.admin.key`                 |
| `/tcp key check <player>`                                                        | Check player's keys                                                                                                                              | `tcp.admin.key`                 |
| `/tcp stats [player]`                                                            | View statistics                                                                                                                                  | `tcp.stats` / `tcp.admin.stats` |
| `/tcp leaderboard <type>`                                                        | View leaderboards                                                                                                                                | `tcp.leaderboard`               |
| `/tcp claims scan`                                                               | Log chambers that overlap existing land-claim plugin claims                                                                                      | `tcp.admin.reload`              |
| `/tcp debug schema`                                                              | Print each database table's actual columns (diagnostics)                                                                                         | `tcp.admin.reload`              |
| `/tcp reload`                                                                    | Reload configuration                                                                                                                             | `tcp.admin.reload`              |
| `/tcp update [check\|download\|apply\|restore\|status]`                          | Check for and install plugin updates                                                                                                             | `tcp.admin`                     |

***

## Command Details

<details>

<summary><code>/tcp help</code></summary>

Shows a list of all available commands.

**Usage:**

```
/tcp help
```

**Permission:** None (everyone can use this)

**Example:**

```
/tcp help
```

{% hint style="info" %}
Only shows commands you have permission to use!
{% endhint %}

</details>

<details>

<summary><code>/tcp setup</code></summary>

A friendly, **opt-in** tour of the major settings — built for operators who install the plugin and never open a YAML file. It walks the main options **one at a time**, each with a plain-English explanation, its current state, and **Enable / Skip / Disable** buttons (plus **← Prev**, **Pause Setup** and **Stop Setup**). Nothing is forced and no default is changed; you only apply what you choose.

On Paper **1.21.7+** the tour renders in the native **Dialog** UI. On older or non-Paper servers it falls back automatically to a **clickable-chat** version with exactly the same content — no configuration needed.

**Usage:**

```
/tcp setup           # start (or restart) the tour
/tcp setup continue  # resume a tour you paused
```

**Permission:** `tcp.admin.setup` (OP by default)

**Good to know:**

* Settings that ship **off** but are worth a look (auto-discovery, auto-snapshot, …) lead the tour.
* A few steps show a **CPU impact** badge (`tiny` / `little` / `medium` / `high`) so you know the cost at a glance.
* Choice steps (like how often chambers reset) show the **current value** as a real duration, and let you pick a preset or point you to `config.yml` for a custom value.
* A gentle reminder appears for ops who haven't run setup yet — at most once a week, three times — and stops for good once you've taken the tour.

{% hint style="info" %}
The reminder can be turned off with `setup.reminder.enabled: false` in `config.yml`. Running the tour is always optional — TCP works fine on its defaults.
{% endhint %}

</details>

<details>

<summary><code>/tcp menu [chamber]</code></summary>

Opens the admin GUI for managing all aspects of TrialChamberPro without command line. With a chamber name _(1.5.7+)_, jumps straight into that chamber's detail view — this is what the `[menu]` button on `/tcp list` lines uses.

**Usage:**

```
/tcp menu
/tcp menu <chamber_name>
```

**Permission:** `tcp.admin.menu`

**Example:**

```
/tcp menu
```

**GUI Screens (v1.2.8+):**

The admin GUI provides 14 different views organized into categories:

**Main Menu** - Central hub with 6 category buttons:

* **Chambers** - List and manage all registered chambers
* **Loot Tables** - Browse available loot tables
* **Statistics** - View leaderboards and player stats
* **Settings** - Configure plugin settings in real-time
* **Protection** - Toggle protection features
* **Help** - Command reference and permissions

**Chamber Management:**

* **Chamber List** - Paginated list (36 per page) with quick actions
* **Chamber Detail** - Full management hub (loot, vaults, settings, actions)
* **Chamber Settings** - Per-chamber reset interval, exit location, loot overrides
* **Vault Management** - View/reset player vault cooldowns

**Settings:**

* **Global Settings** - Toggle 13 config options without editing YAML
* **Protection Menu** - Enable/disable protection features instantly

**Statistics:**

* **Stats Menu** - Overview with leaderboard shortcuts
* **Leaderboards** - Top 10 players by category
* **Player Stats** - Individual player statistics with K/D ratio

**Key Features:**

* **Runtime Config Editing** - Changes save immediately to config.yml
* **Pagination** - Handle unlimited chambers
* **Navigation** - Consistent back/close buttons throughout
* **Session Restoration** - Return to previous screens automatically

{% hint style="success" %}
**No YAML editing required!** Most configuration can now be done entirely through the GUI.
{% endhint %}

</details>

<details>

<summary><code>/tcp generate &lt;value|coords|wand|blocks&gt;</code></summary>

Registers a chamber using either a saved WorldEdit variable (named region), your current WorldEdit selection, explicit coordinates, or by a desired block amount at your current facing.

**Usage:**

```
/tcp generate value save <varName>
/tcp generate value list
/tcp generate value delete <varName>
/tcp generate value <varName> [chamberName]
/tcp generate coords <x1,y1,z1> <x2,y2,z2> [world] <chamberName>
/tcp generate wand <chamberName>
/tcp generate blocks <amount> [chamberName] [roundingAllowance]
```

**Permission:** `tcp.admin.generate`

**Behavior:**

* **value save**: Saves your current WorldEdit selection to a named variable for later use.
* **value list**: Shows all saved region variables.
* **value delete**: Removes a saved region by name.
* **value \[chamberName]**: Generates a chamber from the saved region. If chamberName is omitted, is used as the chamber name. If no saved var exists and the sender is a player with a WorldEdit selection, falls back to using the selection.
* **coords**: Generates a chamber from two corners specified as either `<x1,y1,z1> <x2,y2,z2>` or legacy `<x1,y1,z1-x2,y2,z2>`. From console, you must also provide `[world]`.
* **wand**: Generates a chamber from your current WorldEdit selection. Handy shortcut for admins.
* **blocks**: Generates a chamber at your current location and facing, sized to approximately `<amount>` blocks. The plugin enforces a minimum of 31x15x31 and will round up by at most `generation.blocks.rounding-allowance` (default 1000) to form a clean region.

**Notes:**

* Minimum size enforced: 31x15x31 (width x height x depth)
* Maximum volume limited by `generation.max-volume` in config.yml
* WorldEdit must be installed for the `value` and `wand` operations
* Auto-scans for vaults/spawners and creates snapshots based on config settings

</details>

<details>

<summary><code>/tcp scan &lt;chamber&gt;</code></summary>

Scans a chamber to detect vaults, trial spawners, and decorated pots within its **current bounds**.

**Usage:**

```
/tcp scan <chamber_name>
/tcp scan add <chamber_name>
```

**Permission:** `tcp.admin.scan`

**Arguments:**

* `<chamber_name>` - Name of the chamber to scan
* `add` - Grow the chamber's bounds before scanning (see below)

**Examples:**

```
/tcp scan MainChamber
/tcp scan add auto_world_5503_1336
```

**What it finds:**

* **Vaults** (normal and ominous)
* **Trial Spawners** (normal and ominous)
* **Decorated Pots**

**Output example:**

```
[TCP] Scanning chamber MainChamber...
[TCP] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

**`/tcp scan add` — repair a clipped chamber** _(added in 1.6.3)_

Auto-discovery floods outward from a vault/spawner. If neighbouring chunks were still loading when the chamber was first detected (or the chamber was big enough to hit the flood's node cap), the bounding box can be **clipped at a chunk boundary** — leaving part of the chamber, and its chests/vaults/spawners, outside the registered region. A plain `/tcp scan` only looks **inside the existing bounds**, so it can't recover the missing part.

`/tcp scan add <chamber>` re-floods from the chamber's known vaults (now that chunks are loaded), **grows the bounds** to absorb the missed sections, then re-scans and re-snapshots — the same merge auto-discovery uses, just operator-triggered. **Stand inside the chamber** when you run it so the relevant chunks are loaded. New chambers also get one automatic expand pass on discovery (`discovery.expand-on-discover`), and the chamber GUI offers a one-time **Travel & Expand** button that teleports you there first. To reach a wing nobody has visited without travelling, enable `discovery.expand-force-load` (opt-in, Paper-only).

{% hint style="warning" %}
**Re-scanning overwrites previous data!** If you modified your chamber and re-scan, old vault/spawner data is replaced.
{% endhint %}

</details>

<details>

<summary><code>/tcp setexit &lt;chamber&gt;</code></summary>

Sets the exit location for a chamber. Players inside when the chamber resets will teleport here.

**Usage:**

```
/tcp setexit <chamber_name>
```

**Permission:** `tcp.admin.create`

**Requirements:**

* Must be a player (not console)

**Arguments:**

* `<chamber_name>` - Name of the chamber

**Examples:**

```
/tcp setexit MainChamber
```

Stand where you want players to teleport (usually just outside the entrance), then run the command. Your exact position and look direction are saved.

**Tips:**

* Set the exit OUTSIDE the chamber boundaries
* Face the direction you want players to look when teleported
* Test it with `/tcp reset <chamber>` to see where players go

</details>

<details>

<summary><code>/tcp snapshot &lt;action&gt; [chamber]</code></summary>

Manage chamber snapshots (saved states for resets).

**Usage:**

```
/tcp snapshot create [chamber_name]
/tcp snapshot update [chamber_name]
/tcp snapshot restore [chamber_name]
/tcp snapshot create all [force]
/tcp snapshot missing [page]
```

The chamber name is **optional on `create` / `update` / `restore`** _(1.5.5+; previously only `update`)_: omit it while standing inside a registered chamber and the command targets that chamber. Handy on servers with many chambers.

**Permission:** `tcp.admin.snapshot`

**`create all` / `missing` — Backfill missing snapshots _(1.5.22+)_**

If you registered chambers with `global.auto-snapshot-on-register` turned off, they have no snapshot and **can't be reset** until one is captured. These two commands fix a backlog without doing it one-by-one:

* **`/tcp snapshot create all`** captures a snapshot for every registered chamber that's _missing_ one. It runs them **sequentially, waiting 20 ticks after each finishes** before the next — a single capture is one heavy main-thread pass over the whole chamber, so this stagger keeps TPS healthy on a big backlog. Progress is reported every 10 chambers. Add **`force`** (`/tcp snapshot create all force`) to re-capture **all** chambers, including ones that already have a snapshot.
* **`/tcp snapshot missing`** lists the chambers with no snapshot, 10 per page, each with a clickable **`[Create]`** button (and a **`[Create all]`** header button). This is also where the periodic "chambers have no snapshot" reminder's `[list]` link now points.

```
/tcp snapshot create all          # snapshot the 62 chambers missing one, staggered
/tcp snapshot create all force     # re-snapshot every chamber
/tcp snapshot missing 2            # page 2 of the missing list
```

**Actions:**

**`create` - Create Snapshot**

Scans the chamber and saves every block to a compressed snapshot file.

**Example:**

```
/tcp snapshot create MainChamber
```

**What happens:**

1. Scans all blocks in chamber boundaries
2. Saves block types, orientations, tile entity data
3. Compresses and stores in `snapshots/<chamber>.dat`

**Time:** 5-30 seconds depending on chamber size

{% hint style="success" %}
**Update snapshots anytime!** Made changes to your chamber? Run `/tcp snapshot create` again to update.
{% endhint %}

**`restore` - Restore Snapshot**

Immediately resets the chamber from its snapshot (same as `/tcp reset`).

**Example:**

```
/tcp snapshot restore MainChamber
```

Useful for testing or forcing manual resets.

</details>

<details>

<summary><code>/tcp dungeon &lt;pos1|pos2|capture|generate|list|delete|import&gt;</code></summary>

Assembles chambers on demand from modular room pieces you build yourself. Configure in [dungeon.yml](../configuration/dungeon.yml.md).

**Authoring a room:** build it in WorldEdit with **complete, solid walls**. At each spot a doorway could be, place a `minecraft:jigsaw` block flush in the wall with its **front facing outward** (orientations `north_up` / `east_up` / `south_up` / `west_up`). Don't pre-cut the opening — the generator carves a standard doorway only where two rooms actually join, and leaves unused connectors as walls. Standardise your door size across rooms.

```
/tcp dungeon pos1                     # stand at one corner
/tcp dungeon pos2                     # stand at the opposite corner
/tcp dungeon capture <id> [roles…]    # save the selection (roles → tags, e.g. entrance / vault / boss)
/tcp dungeon generate <name> [seed]   # stitch a dungeon at your feet, registered as a chamber
/tcp dungeon list                     # list saved room templates
/tcp dungeon delete <id>              # delete a room template
/tcp dungeon import <file|folder|zip> [tags…]  # v1.7.0: import vanilla .nbt structure templates
```

**Importing (v1.7.0):** drop `.nbt` structure templates — or a whole datapack `.zip` (e.g. a "crazy chambers"-style pack) — into `plugins/TrialChamberPro/dungeon/import/` and import them as rooms; jigsaw blocks become connectors automatically. See [dungeon.yml](../configuration/dungeon.yml.md#importing-datapack-rooms-v170) for details and limits.

Rooms are matched on opposite-facing connectors across all four rotations, placed without overlap, and the result is snapshotted + registered like any other chamber (so resets, loot and protection all apply). `required-tags` in `dungeon.yml` guarantee e.g. one entrance and at least one vault.

</details>

<details>

<summary><code>/tcp reset &lt;chamber&gt;</code></summary>

Forces an immediate chamber reset.

**Usage:**

```
/tcp reset <chamber_name>
```

**Permission:** `tcp.admin.reset`

**Arguments:**

* `<chamber_name>` - Name of the chamber to reset

**Examples:**

```
/tcp reset MainChamber
/tcp reset NetherChamber1
```

**What it does:**

1. Teleports all players inside to the exit location
2. Restores all blocks from snapshot
3. Resets vault states (clears native `rewarded_players`)
4. Removes spawned mobs (configurable)
5. Clears ground items (configurable)

**Use cases:**

* Testing chamber functionality
* Manual reset for events
* Fixing a broken chamber

{% hint style="info" %}
**Vault cooldowns:** When `reset-vault-cooldowns: true` in config.yml (default), vault cooldowns are cleared both in the database AND via Paper's native Vault API. This ensures players can truly loot vaults again after a reset.
{% endhint %}

</details>

<details>

<summary><code>/tcp list</code></summary>

Lists all registered chambers.

**Usage:**

```
/tcp list
```

**Permission:** `tcp.admin`

**Example output:**

```
[TCP] === Registered Chambers ===
[TCP] MainChamber - world (12,847 blocks)
[TCP] NetherChamber1 - world_nether (8,521 blocks)
[TCP] OceanChamber - world (15,392 blocks)
```

Shows chamber name, world, and total block count.

</details>

<details>

<summary><code>/tcp info [chamber]</code></summary>

Shows plugin information (when used without arguments) or detailed chamber information (when a chamber name is provided).

**Usage:**

```
/tcp info
/tcp info <chamber_name>
```

**Permission:** `tcp.admin`

**Arguments:**

* `[chamber_name]` - Optional: Name of the chamber to show details for

**Examples:**

```
/tcp info
/tcp info MainChamber
```

**Plugin Info (no arguments)**

When used without arguments, shows plugin-wide information:

**Example output:**

```
[TCP] === TrialChamberPro Plugin Info ===
[TCP] Version: 1.2.22
[TCP] Authors: DarkStarWorks
[TCP] Database: SQLITE
[TCP] Registered Chambers: 5
[TCP] Platform: Paper/Spigot
[TCP] --- Integrations ---
[TCP]   WorldEdit/FAWE: ✓
[TCP]   WorldGuard: ✗
[TCP]   PlaceholderAPI: ✓
[TCP]   Vault: ✗
[TCP] --- Features ---
[TCP]   Per-Player Loot: ✓
[TCP]   Spawner Waves: ✓
[TCP]   Spectator Mode: ✓
[TCP]   Statistics: ✓
```

**Info shown:**

* Plugin version and authors
* Database type (SQLite/MySQL)
* Number of registered chambers
* Server platform (Paper/Spigot or Folia)
* Integration status (WorldEdit, WorldGuard, PlaceholderAPI, Vault)
* Feature status (Per-Player Loot, Spawner Waves, Spectator Mode, Statistics)

**Chamber Info (with argument)**

When used with a chamber name, shows detailed chamber information:

**Example output:**

```
[TCP] === Chamber Info: MainChamber ===
[TCP] World: world
[TCP] Bounds: -150,-20,400 to -50,40,500
[TCP] Volume: 12,847 blocks
[TCP] Exit: -145, 65, 395
[TCP] Reset Interval: 48 hours
[TCP] Last Reset: 2 hours ago
[TCP] Snapshot: Created
```

**Info shown:**

* Chamber name and world
* Boundary coordinates
* Total block volume
* Exit location (or "Not set")
* Reset interval
* Last reset time
* Snapshot status

</details>

<details>

<summary><code>/tcp pause &lt;chamber&gt;</code> / <code>/tcp resume &lt;chamber&gt;</code></summary>

Pause or resume a registered chamber.

**Usage:**

```
/tcp pause <chamber_name>
/tcp resume <chamber_name>
```

**Permission:** `tcp.admin.pause`

**What pausing does:**

* DB record, stats, vault history, and snapshot are fully preserved — nothing is deleted.
* Automatic resets stop scheduling for the chamber.
* Protection events (block break/place, container access, mob griefing) are skipped.
* Vault interactions are blocked with a player-visible message.
* Player entry/exit tracking and spawner wave tracking are silenced.

**What pausing does NOT do:**

* Does not delete any data.
* Does not remove mobs or items currently inside the chamber.
* Does not prevent players from physically entering the region.

**Use case:** Hardcore/anarchy servers where griefing protection is intentionally disabled. If enough critical blocks are demolished you can pause the chamber to freeze its record while the world state reflects the damage, then resume or delete once you decide what to do.

{% hint style="info" %}
**Auto-pause:** Enable `protection.auto-pause-on-destruction: true` in config.yml to let the plugin pause chambers automatically once a configurable number of vaults or trial spawners are destroyed. See [config.yml](../configuration/config.yml.md) → Protection Settings.
{% endhint %}

</details>

<details id="tcp-container-action-chamber">

<summary><code>/tcp container &lt;action&gt; &lt;chamber&gt; [#]</code></summary>

Manage per-player container loot ([`chests.per-player-loot`](../configuration/config.yml.md#per-player-chamber-container-loot)) for a chamber. CLI parity with the chamber GUI's **Container Loot** screen (`/tcp menu <chamber>` → Container Loot). _(Added in 1.5.9; reworked in 1.6.3.)_

Untouched containers roll **fresh loot per player** on every open (and again after each reset). Editing a container creates an **override** that all players then receive a copy of; reverting drops the override. There is no in-world editing — use this command or the GUI.

**Permission:** `tcp.admin.containers`

| Action                    | Effect                                                                                                                            |
| ------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `list <chamber>`          | Show whether per-player loot is on, plus listed-container and player-copy counts, with each container's index + position.          |
| `materialize <chamber>`   | Scan the chamber and **list** every container so you can edit it. Listing only — it does not freeze loot.                          |
| `edit <chamber> <#>`      | Open a container to edit it; saving creates an **override** (every player gets a copy of it, re-cloned each reset).                |
| `resetone <chamber> <#>`  | Revert one container to vanilla — it goes back to fresh per-player rolls.                                                          |
| `reset <chamber>`         | Remove every container from the list, including overrides (containers re-list as they're opened or re-scanned).                    |
| `clearcopies <chamber>`   | Drop every player's private copies (they roll fresh loot next open). Overrides are kept.                                          |
| `tp <chamber> <#>`        | Teleport to a container (index from `list`).                                                                                       |

```
/tcp container list MainChamber
/tcp container materialize MainChamber
/tcp container edit MainChamber 3
/tcp container resetone MainChamber 3
```

</details>

<details id="tcp-delete-chamber">

<summary><code>/tcp delete &lt;chamber&gt;</code></summary>

Permanently deletes a chamber and all associated data.

**Usage:**

```
/tcp delete <chamber_name>
```

**Permission:** `tcp.admin.create`

**Arguments:**

* `<chamber_name>` - Name of the chamber to delete

**Examples:**

```
/tcp delete OldChamber
/tcp delete TestChamber
```

{% hint style="danger" %}
** PERMANENT ACTION!** This deletes:

* Chamber boundaries and settings
* All vault data
* All spawner data
* Player vault cooldowns for this chamber
* The snapshot file is NOT deleted (manual cleanup required)

**Cannot be undone!**
{% endhint %}

</details>

<details>

<summary><code>/tcp vault reset &lt;chamber&gt; &lt;player&gt; [type]</code></summary>

Resets a player's vault cooldowns for a specific chamber.

**Usage:**

```
/tcp vault reset <chamber_name> <player_name> [normal|ominous]
```

**Permission:** `tcp.admin.vault`

**Arguments:**

* `<chamber_name>` - Chamber name
* `<player_name>` - Player name (can be offline)
* `[type]` - Optional: `normal` or `ominous` (resets all if not specified)

**Examples:**

```
/tcp vault reset MainChamber Steve
/tcp vault reset MainChamber Alex normal
/tcp vault reset OceanChamber Bob ominous
```

**What it does:**

* Resets cooldowns for ALL vaults in the chamber
* Clears both database tracking AND native Vault block state (v1.2.21+)
* Player can immediately loot vaults again
* Filters by vault type if specified

**Use cases:**

* Compensate for server issues/bugs
* Event rewards ("free vault access!")
* Testing vault mechanics

{% hint style="info" %}
**Per-vault cooldowns:** This resets cooldowns for every vault in the chamber individually, not just one vault.
{% endhint %}

{% hint style="success" %}
**v1.2.21+:** This command now properly clears Paper's native Vault `rewarded_players` list in addition to database tracking. This ensures players can truly loot vaults again immediately.
{% endhint %}

</details>

<details>

<summary><code>/tcp key give &lt;player&gt; &lt;amount&gt; [type]</code></summary>

Gives trial keys to a player.

**Usage:**

```
/tcp key give <player_name> <amount> [normal|ominous]
```

**Permission:** `tcp.admin.key`

**Arguments:**

* `<player_name>` - Player to give keys to (must be online)
* `<amount>` - Number of keys (positive integer)
* `[type]` - Optional: `normal` or `ominous` (default: `normal`)

**Examples:**

```
/tcp key give Steve 5
/tcp key give Alex 10 normal
/tcp key give Bob 3 ominous
```

**Use cases:**

* Rewards for events/competitions
* Compensation for bugs
* Sell keys in-game shop (via command blocks or other plugins)
* Testing vault mechanics

{% hint style="warning" %}
**Player must be online!** Offline players can't receive items. The command will fail if the player isn't online.
{% endhint %}

</details>

<details>

<summary><code>/tcp key check &lt;player&gt;</code></summary>

Checks how many trial keys a player has.

**Usage:**

```
/tcp key check <player_name>
```

**Permission:** `tcp.admin.key`

**Arguments:**

* `<player_name>` - Player to check (must be online)

**Examples:**

```
/tcp key check Steve
```

**Example output:**

```
[TCP] Steve has 5 Normal Key(s) and 2 Ominous Key(s).
```

Counts ALL keys in the player's inventory (all slots combined).

</details>

<details>

<summary><code>/tcp stats [player]</code></summary>

View player statistics for Trial Chamber activity.

**Usage:**

```
/tcp stats
/tcp stats <player_name>
```

**Permission:**

* `tcp.stats` - View own stats
* `tcp.admin.stats` - View other players' stats

**Arguments:**

* `[player_name]` - Optional: Player to view stats for (requires admin permission)

**Examples:**

```
/tcp stats
/tcp stats Steve
```

**Example output:**

```
[TCP] === Statistics for Steve ===
[TCP] Chambers Completed: 12
[TCP] Normal Vaults Opened: 45
[TCP] Ominous Vaults Opened: 18
[TCP] Mobs Killed: 324
[TCP] Deaths: 7
[TCP] Time Spent: 5h 32m
```

{% hint style="info" %}
**Requires statistics to be enabled** in config.yml (`statistics.enabled: true`)
{% endhint %}

**Tracked stats:**

* **Chambers Completed** - How many times player completed a chamber
* **Normal Vaults Opened** - Total normal vaults looted
* **Ominous Vaults Opened** - Total ominous vaults looted
* **Mobs Killed** - Mobs killed inside managed chambers
* **Deaths** - Deaths inside managed chambers
* **Time Spent** - Total time spent inside chambers

</details>

<details>

<summary><code>/tcp leaderboard &lt;type&gt;</code></summary>

View top players for a specific statistic.

**Usage:**

```
/tcp leaderboard <type>
/tcp lb <type>
/tcp top <type>
```

**Permission:** `tcp.stats`

**Arguments:**

* `<type>` - Stat type to display

**Stat types:**

* `chambers` or `completions` - Chambers completed
* `normal` or `normalvaults` - Normal vaults opened
* `ominous` or `ominousvaults` - Ominous vaults opened
* `mobs` or `kills` - Mobs killed
* `time` or `playtime` - Time spent in chambers

**Examples:**

```
/tcp leaderboard chambers
/tcp lb normal
/tcp top time
```

**Example output:**

```
[TCP] === Top Players - Chambers Completed ===
[TCP] #1 Steve: 47
[TCP] #2 Alex: 42
[TCP] #3 Bob: 38
[TCP] #4 Charlie: 35
[TCP] #5 Diana: 31
```

**Configuration:**

* Number of players shown: `statistics.top-players-count` in config.yml (default: 10)
* Update frequency: `statistics.leaderboard-update-interval` in config.yml (default: 1 hour)

{% hint style="info" %}
**Leaderboards are cached** to prevent database lag. They update on the interval specified in config, not in real-time.
{% endhint %}

</details>

<details>

<summary><code>/tcp claims scan</code></summary>

Checks every registered chamber against existing claims from any installed land-claim plugin (**Residence**, **Lands**, **GriefPrevention**) and logs a warning to the console for each overlap. Use it to find chambers that were registered on top of — or had a claim made inside them before — the [claim integrations](../configuration/config.yml.md#residence-integration-lands-integration-griefprevention-integration) existed. _(Added in 1.5.15.)_

**Usage:**

```
/tcp claims scan
```

**Permission:** `tcp.admin.reload`

**What it does:**

* For each enabled integration, walks that plugin's claims once and reports any that overlap a chamber's bounds.
*   Logs one line per conflict to the **server console**, e.g.:

    ```
    [TCP] Claim conflict: chamber 'arena3' (world 120,-44,310) overlaps GriefPrevention claim(s): Steve
    ```
* Replies in chat with the total number of conflicting chambers (or "No claim conflicts found.").

This also runs automatically on startup unless you set [`protection.claim-conflict-scan-on-startup: false`](../configuration/config.yml.md#claim-conflict-scan-on-startup).

**How to resolve a reported conflict:**

1. Note the chamber name, the location, and the claim owner from the log line.
2. Decide which should win that space:
   * **Keep the chamber:** remove or resize the claim in the claim plugin (e.g. Residence `/res remove`, Lands `/unclaim`, GriefPrevention claim resize/abandon), then re-run `/tcp claims scan` to confirm it's clear.
   * **Keep the claim:** [delete the chamber](#tcp-delete-chamber) (`/tcp delete <chamber>`) or move/re-register it elsewhere.
3. New claims can no longer be made into chambers, so once existing conflicts are cleared they won't reappear (except for players with the relevant `tcp.bypass.*` permission).

</details>

<details>

<summary><code>/tcp reload</code></summary>

Reloads the plugin configuration without restarting the server.

**Usage:**

```
/tcp reload
```

**Permission:** `tcp.admin.reload`

**Example:**

```
/tcp reload
```

**What gets reloaded:**

* `config.yml` settings
* `loot.yml` loot tables
* `messages.yml` messages
* Chamber lookup cache is cleared

**What DOESN'T reload:**

* Database connections (requires full restart)
* Existing chamber data in memory
* Active reset timers (they continue with old intervals until next reset)

{% hint style="warning" %}
**Database changes require restart!** If you changed database settings in config.yml, you MUST restart the server, not just reload.
{% endhint %}

</details>

<details>

<summary><code>/tcp update</code></summary>

_(Added in 1.8.0.)_ Check for, download, and install TrialChamberPro updates. Updates are looked up on Modrinth (with GitHub Releases as a fallback).

**Usage:**

```
/tcp update [check|download|apply|ignore <version>|unignore <version>|restore|status]
```

**Permission:** `tcp.admin`

**Subcommands:**

* `check` (default when no argument is given) — check now and report the result.
* `status` — show the last known result without making a network call.
* `download` — download the latest release, verify its checksum, back up the current jar, and stage the new one for install on the **next restart**. Requires `update.mode: download` or `auto-stage`.
* `apply` — hot-swap a staged update into place **without a restart**. Requires `update.allow-hot-reload: true` (and is refused on Folia or when other plugins depend on TCP). See below.
* `restore` — stage the most recent backup for install on the next restart, rolling back a bad update.
* `ignore <version>` — stop notifications for a specific version until a newer one is released.
* `unignore <version>` — undo `ignore` for a version.

**Behaviour is set in config.yml** under [`update`](../configuration/config.yml.md#updates). In the default `notify` mode the plugin only tells you an update exists — `download` and `apply` are rejected until you raise the mode.

**Typical restart-based flow:**

```
/tcp update check       # see what's available
/tcp update download    # verify + stage it
# restart the server — the new jar installs on boot
```

{% hint style="info" %}
**Hot reload (`apply`)** is an opt-in convenience for small updates. It unloads the running plugin, swaps the jar, and re-enables the new version live, rolling back to the automatic backup if the new version fails to load. Restarting the server is always the safer choice — leave `allow-hot-reload` off unless you specifically want in-place reloads.
{% endhint %}

</details>

***

## Common Command Sequences

<details>

<summary><strong>Registering an Existing Chamber (Full Process)</strong></summary>

```bash
# 1. Select chamber with WorldEdit
/wand
# (left-click + right-click corners)

# 2. Register chamber
/tcp generate wand MyChamber

# 3. Scan is automatic, but you can re-scan if needed
/tcp scan MyChamber

# 4. Set exit location (stand outside chamber)
/tcp setexit MyChamber

# 5. Snapshot is automatic, but you can update it
/tcp snapshot create MyChamber

# 6. Check chamber info
/tcp info MyChamber

# 7. Test the reset
/tcp reset MyChamber
```

</details>

<details>

<summary><strong>Managing Player Issues</strong></summary>

```bash
# Player accidentally used all keys
/tcp key give Steve 5 normal

# Player's vaults stuck on cooldown (bug)
/tcp vault reset MainChamber Steve

# Check player's statistics
/tcp stats Steve

# Check player's key inventory
/tcp key check Steve
```

</details>

<details>

<summary><strong>Event Setup</strong></summary>

```bash
# Give all online players keys for event
/tcp key give Player1 10
/tcp key give Player2 10
/tcp key give Player3 10

# After event, check leaderboards
/tcp leaderboard chambers
/tcp leaderboard time

# Reset chamber immediately for next group
/tcp reset EventChamber
```

</details>

<details>

<summary><strong>Maintenance Tasks</strong></summary>

```bash
# List all chambers
/tcp list

# Check each chamber's status
/tcp info MainChamber
/tcp info NetherChamber

# Update snapshots after building changes
/tcp snapshot create MainChamber
/tcp snapshot create NetherChamber

# Reload config after edits
/tcp reload
```

</details>

***

## Pro Tips

{% hint style="success" %}
**Use tab completion!** Press `Tab` while typing commands to autocomplete chamber names, player names, and arguments.
{% endhint %}

{% hint style="info" %}
**Aliases:** All leaderboard commands work with `/tcp lb` and `/tcp top` for quick access.
{% endhint %}

{% hint style="warning" %}
**Chamber names are case-sensitive** in some commands. Use tab completion to ensure correct capitalization.
{% endhint %}

{% hint style="info" %}
**Offline player support:** Most commands work with offline players (like `/tcp vault reset`), but `/tcp key give` requires the player to be online.
{% endhint %}

***

## Command Permissions

For a complete list of all permissions (including per-command permissions), see the [Permissions](permissions.md) page.

**Quick permission groups:**

**Full Admin:**

```yaml
tcp.admin
tcp.admin.*
```

**Statistics Access:**

```yaml
tcp.stats
tcp.admin.stats
```

**Read-only Access:**

```yaml
tcp.admin       # Can view chambers
tcp.stats       # Can view own stats
```

***

## Troubleshooting

**"Unknown subcommand"**

* Check spelling (use tab completion!)
* Verify you have permission for that command
* Run `/tcp help` to see available commands

**"You don't have permission to use this command"**

* Check with your server admin for permissions
* See [Permissions](permissions.md) for the full list

**"Chamber not found"**

* Use `/tcp list` to see all chambers
* Chamber names are case-sensitive
* Use tab completion to avoid typos

**"No WorldEdit selection found"**

* Make sure WorldEdit is installed
* Use `/wand` and select two corners
* Your selection must have volume (not flat)

**"Player not found or not online"**

* Player must be online for `/tcp key give` and `/tcp key check`
* For offline players, use `/tcp vault reset` (works offline)

**"Snapshot operation failed"**

* Check console for detailed error
* Ensure disk space is available
* Verify file permissions on `plugins/TrialChamberPro/snapshots/` folder

***

## Related Pages

{% content-ref url="permissions.md" %}
[permissions.md](permissions.md)
{% endcontent-ref %}

Complete permission nodes for all commands and features.

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

Settings that affect command behavior (auto-scan, auto-snapshot, etc.)

{% content-ref url="../getting-started/your-first-chamber.md" %}
[your-first-chamber.md](../getting-started/your-first-chamber.md)
{% endcontent-ref %}

Step-by-step guide using these commands to set up your first chamber.

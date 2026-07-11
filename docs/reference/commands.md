# Commands

All commands start with `/trial` (short for BetterTrialChambers). Most require specific permissions—check the [Permissions](permissions.md) page for details.

{% hint style="info" %}
**Aliases:** `/btc`, `/tcp`, `/bettertrialchambers`

**Tab completion:** Available for all commands! Press `Tab` while typing for suggestions.
{% endhint %}

***

## Quick Reference

| Command                                                                          | Description                                                                                                                                      | Permission                      |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| `/trial help`                                                                      | Show command list                                                                                                                                | None                            |
| `/trial setup`                                                                     | Guided, opt-in tour of the main settings (Dialog UI, or clickable chat on older servers)                                                         | `tcp.admin.setup`               |
| `/trial menu [chamber]`                                                            | Open admin GUI (with a chamber name: jump straight to that chamber's detail view)                                                                | `tcp.admin.menu`                |
| `/trial generate <value\|coords\|wand\|blocks>`                                    | Register chamber from saved var, coords, WE wand, or by block amount                                                                             | `tcp.admin.generate`            |
| `/trial scan <chamber>`                                                            | Scan for vaults/spawners                                                                                                                         | `tcp.admin.scan`                |
| `/trial scan add <chamber>`                                                        | Grow a chamber's bounds into sections discovery clipped, then re-scan                                                                            | `tcp.admin.scan`                |
| `/trial setexit <chamber>`                                                         | Set exit location                                                                                                                                | `tcp.admin.create`              |
| `/trial snapshot <create\|update\|restore> [chamber]`                              | Manage snapshots (omit the name to target the chamber you're standing in)                                                                        | `tcp.admin.snapshot`            |
| `/trial snapshot create all [force]`                                               | Backfill snapshots for all chambers missing one (staggered); `force` re-does all                                                                 | `tcp.admin.snapshot`            |
| `/trial snapshot missing [page]`                                                   | List chambers with no snapshot, with clickable `[Create]`                                                                                        | `tcp.admin.snapshot`            |
| `/trial reset <chamber>`                                                           | Force chamber reset                                                                                                                              | `tcp.admin.reset`               |
| `/trial reset pending`                                                             | List chambers awaiting reset confirmation                                                                                                        | `tcp.admin.reset`               |
| `/trial reset confirm <chamber\|all>`                                              | Confirm queued reset(s) (when confirmation mode is on)                                                                                           | `tcp.admin.reset`               |
| `/trial list [page\|current]`                                                      | List chambers (paginated, interactive: click a name to copy it, click `[menu]` to open its GUI); `current` finds the chamber you're in / nearest | `tcp.admin`                     |
| `/trial dungeon <pos1\|pos2\|capture\|generate\|list\|delete>`                     | Procedural dungeon generation from room templates                                                                                                | `tcp.admin.generate`            |
| `/trial info [chamber]`                                                            | Show plugin info, or chamber details if specified                                                                                                | `tcp.admin`                     |
| `/trial delete <chamber>`                                                          | Delete a chamber                                                                                                                                 | `tcp.admin.create`              |
| `/trial loot set <chamber> <normal\|ominous> <table>`                              | Override a chamber's loot table                                                                                                                  | `tcp.admin.loot`                |
| `/trial loot clear <chamber> [normal\|ominous\|all]`                               | Remove per-chamber loot override                                                                                                                 | `tcp.admin.loot`                |
| `/trial loot audit`                                                                | List pre-1.5.0 loot entries that lost their NBT                                                                                                  | `tcp.admin.loot`                |
| `/trial container <list\|materialize\|reset\|resetone\|clearcopies\|tp\|edit> <chamber> [#]` | Manage per-player container loot (list, edit overrides, revert to vanilla)                                                                | `tcp.admin.containers`          |
| `/trial mobs providers`                                                            | List registered mob providers and their availability                                                                                             | `tcp.admin.mobs`                |
| `/trial mobs <chamber> provider <id\|vanilla\|none>`                               | Set a chamber's custom mob provider                                                                                                              | `tcp.admin.mobs`                |
| `/trial mobs <chamber> add normal\|ominous <mobId>`                                | Add a mob id to a chamber's pool                                                                                                                 | `tcp.admin.mobs`                |
| `/trial mobs <chamber> remove normal\|ominous <mobId>`                             | Remove a mob id from a chamber's pool                                                                                                            | `tcp.admin.mobs`                |
| `/trial mobs <chamber> list`                                                       | Show a chamber's mob provider config                                                                                                             | `tcp.admin.mobs`                |
| `/trial give <preset> [player] [amount]`                                           | Give a preconfigured trial-spawner item — see [spawner\_presets.yml](../configuration/spawner-presets.yml.md)                                    | `tcp.give`                      |
| `/trial pause <chamber>`                                                           | Pause a chamber (suspends resets, protection, vault interactions)                                                                                | `tcp.admin.pause`               |
| `/trial resume <chamber>`                                                          | Resume a paused chamber                                                                                                                          | `tcp.admin.pause`               |
| `/trial vault reset <chamber> <player>`                                            | Reset vault cooldowns                                                                                                                            | `tcp.admin.vault`               |
| `/trial key give <player> <amount>`                                                | Give trial keys                                                                                                                                  | `tcp.admin.key`                 |
| `/trial key check <player>`                                                        | Check player's keys                                                                                                                              | `tcp.admin.key`                 |
| `/trial stats [player]`                                                            | View statistics                                                                                                                                  | `tcp.stats` / `tcp.admin.stats` |
| `/trial leaderboard <type>`                                                        | View leaderboards                                                                                                                                | `tcp.leaderboard`               |
| `/trial claims scan`                                                               | Log chambers that overlap existing land-claim plugin claims                                                                                      | `tcp.admin.reload`              |
| `/trial debug schema`                                                              | Print each database table's actual columns (diagnostics)                                                                                         | `tcp.admin.reload`              |
| `/trial reload`                                                                    | Reload configuration                                                                                                                             | `tcp.admin.reload`              |
| `/trial update [check\|download\|apply\|restore\|status]`                          | Check for and install plugin updates                                                                                                             | `tcp.admin`                     |

***

## Command Details

<details>

<summary><code>/trial help</code></summary>

Shows a list of all available commands.

**Usage:**

```
/trial help
```

**Permission:** None (everyone can use this)

**Example:**

```
/trial help
```

{% hint style="info" %}
Only shows commands you have permission to use!
{% endhint %}

</details>

<details>

<summary><code>/trial setup</code></summary>

A friendly, **opt-in** tour of the major settings — built for operators who install the plugin and never open a YAML file. It walks the main options **one at a time**, each with a plain-English explanation, its current state, and **Enable / Skip / Disable** buttons (plus **← Prev**, **Pause Setup** and **Stop Setup**). Nothing is forced and no default is changed; you only apply what you choose.

On Paper **1.21.7+** the tour renders in the native **Dialog** UI. On older or non-Paper servers it falls back automatically to a **clickable-chat** version with exactly the same content — no configuration needed.

**Usage:**

```
/trial setup           # start (or restart) the tour
/trial setup continue  # resume a tour you paused
```

**Permission:** `tcp.admin.setup` (OP by default)

**Good to know:**

* Settings that ship **off** but are worth a look (auto-discovery, auto-snapshot, …) lead the tour.
* A few steps show a **CPU impact** badge (`tiny` / `little` / `medium` / `high`) so you know the cost at a glance.
* Choice steps (like how often chambers reset) show the **current value** as a real duration, and let you pick a preset or point you to `config.yml` for a custom value.
* A gentle reminder appears for ops who haven't run setup yet — at most once a week, three times — and stops for good once you've taken the tour.

{% hint style="info" %}
The reminder can be turned off with `setup.reminder.enabled: false` in `config.yml`. Running the tour is always optional — BTC works fine on its defaults.
{% endhint %}

</details>

<details>

<summary><code>/trial menu [chamber]</code></summary>

Opens the admin GUI for managing all aspects of BetterTrialChambers without command line. With a chamber name _(1.5.7+)_, jumps straight into that chamber's detail view — this is what the `[menu]` button on `/trial list` lines uses.

**Usage:**

```
/trial menu
/trial menu <chamber_name>
```

**Permission:** `tcp.admin.menu`

**Example:**

```
/trial menu
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

<summary><code>/trial generate &lt;value|coords|wand|blocks&gt;</code></summary>

Registers a chamber using either a saved WorldEdit variable (named region), your current WorldEdit selection, explicit coordinates, or by a desired block amount at your current facing.

**Usage:**

```
/trial generate value save <varName>
/trial generate value list
/trial generate value delete <varName>
/trial generate value <varName> [chamberName]
/trial generate coords <x1,y1,z1> <x2,y2,z2> [world] <chamberName>
/trial generate wand <chamberName>
/trial generate blocks <amount> [chamberName] [roundingAllowance]
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

<summary><code>/trial scan &lt;chamber&gt;</code></summary>

Scans a chamber to detect vaults, trial spawners, and decorated pots within its **current bounds**.

**Usage:**

```
/trial scan <chamber_name>
/trial scan add <chamber_name>
```

**Permission:** `tcp.admin.scan`

**Arguments:**

* `<chamber_name>` - Name of the chamber to scan
* `add` - Grow the chamber's bounds before scanning (see below)

**Examples:**

```
/trial scan MainChamber
/trial scan add auto_world_5503_1336
```

**What it finds:**

* **Vaults** (normal and ominous)
* **Trial Spawners** (normal and ominous)
* **Decorated Pots**

**Output example:**

```
[BTC] Scanning chamber MainChamber...
[BTC] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

**`/trial scan add` — repair a clipped chamber** _(added in 1.6.3)_

Auto-discovery floods outward from a vault/spawner. If neighbouring chunks were still loading when the chamber was first detected (or the chamber was big enough to hit the flood's node cap), the bounding box can be **clipped at a chunk boundary** — leaving part of the chamber, and its chests/vaults/spawners, outside the registered region. A plain `/trial scan` only looks **inside the existing bounds**, so it can't recover the missing part.

`/trial scan add <chamber>` re-floods from the chamber's known vaults (now that chunks are loaded), **grows the bounds** to absorb the missed sections, then re-scans and re-snapshots — the same merge auto-discovery uses, just operator-triggered. **Stand inside the chamber** when you run it so the relevant chunks are loaded. New chambers also get one automatic expand pass on discovery (`discovery.expand-on-discover`), and the chamber GUI offers a one-time **Travel & Expand** button that teleports you there first. To reach a wing nobody has visited without travelling, enable `discovery.expand-force-load` (opt-in, Paper-only).

{% hint style="warning" %}
**Re-scanning overwrites previous data!** If you modified your chamber and re-scan, old vault/spawner data is replaced.
{% endhint %}

</details>

<details>

<summary><code>/trial setexit &lt;chamber&gt;</code></summary>

Sets the exit location for a chamber. Players inside when the chamber resets will teleport here.

**Usage:**

```
/trial setexit <chamber_name>
```

**Permission:** `tcp.admin.create`

**Requirements:**

* Must be a player (not console)

**Arguments:**

* `<chamber_name>` - Name of the chamber

**Examples:**

```
/trial setexit MainChamber
```

Stand where you want players to teleport (usually just outside the entrance), then run the command. Your exact position and look direction are saved.

**Tips:**

* Set the exit OUTSIDE the chamber boundaries
* Face the direction you want players to look when teleported
* Test it with `/trial reset <chamber>` to see where players go

</details>

<details>

<summary><code>/trial snapshot &lt;action&gt; [chamber]</code></summary>

Manage chamber snapshots (saved states for resets).

**Usage:**

```
/trial snapshot create [chamber_name]
/trial snapshot update [chamber_name]
/trial snapshot restore [chamber_name]
/trial snapshot create all [force]
/trial snapshot missing [page]
```

The chamber name is **optional on `create` / `update` / `restore`** _(1.5.5+; previously only `update`)_: omit it while standing inside a registered chamber and the command targets that chamber. Handy on servers with many chambers.

**Permission:** `tcp.admin.snapshot`

**`create all` / `missing` — Backfill missing snapshots _(1.5.22+)_**

If you registered chambers with `global.auto-snapshot-on-register` turned off, they have no snapshot and **can't be reset** until one is captured. These two commands fix a backlog without doing it one-by-one:

* **`/trial snapshot create all`** captures a snapshot for every registered chamber that's _missing_ one. It runs them **sequentially, waiting 20 ticks after each finishes** before the next — a single capture is one heavy main-thread pass over the whole chamber, so this stagger keeps TPS healthy on a big backlog. Progress is reported every 10 chambers. Add **`force`** (`/trial snapshot create all force`) to re-capture **all** chambers, including ones that already have a snapshot.
* **`/trial snapshot missing`** lists the chambers with no snapshot, 10 per page, each with a clickable **`[Create]`** button (and a **`[Create all]`** header button). This is also where the periodic "chambers have no snapshot" reminder's `[list]` link now points.

```
/trial snapshot create all          # snapshot the 62 chambers missing one, staggered
/trial snapshot create all force     # re-snapshot every chamber
/trial snapshot missing 2            # page 2 of the missing list
```

**Actions:**

**`create` - Create Snapshot**

Scans the chamber and saves every block to a compressed snapshot file.

**Example:**

```
/trial snapshot create MainChamber
```

**What happens:**

1. Scans all blocks in chamber boundaries
2. Saves block types, orientations, tile entity data
3. Compresses and stores in `snapshots/<chamber>.dat`

**Time:** 5-30 seconds depending on chamber size

{% hint style="success" %}
**Update snapshots anytime!** Made changes to your chamber? Run `/trial snapshot create` again to update.
{% endhint %}

**`restore` - Restore Snapshot**

Immediately resets the chamber from its snapshot (same as `/trial reset`).

**Example:**

```
/trial snapshot restore MainChamber
```

Useful for testing or forcing manual resets.

</details>

<details>

<summary><code>/trial dungeon &lt;pos1|pos2|capture|generate|list|delete|import&gt;</code></summary>

Assembles chambers on demand from modular room pieces you build yourself. Configure in [dungeon.yml](../configuration/dungeon.yml.md).

**Authoring a room:** build it in WorldEdit with **complete, solid walls**. At each spot a doorway could be, place a `minecraft:jigsaw` block flush in the wall with its **front facing outward** (orientations `north_up` / `east_up` / `south_up` / `west_up`). Don't pre-cut the opening — the generator carves a standard doorway only where two rooms actually join, and leaves unused connectors as walls. Standardise your door size across rooms.

```
/trial dungeon pos1                     # stand at one corner
/trial dungeon pos2                     # stand at the opposite corner
/trial dungeon capture <id> [roles…]    # save the selection (roles → tags, e.g. entrance / vault / boss)
/trial dungeon generate <name> [seed]   # stitch a dungeon at your feet, registered as a chamber
/trial dungeon list                     # list saved room templates
/trial dungeon delete <id>              # delete a room template
/trial dungeon import <file|folder|zip> [tags…]  # v1.7.0: import vanilla .nbt structure templates
```

**Importing (v1.7.0):** drop `.nbt` structure templates — or a whole datapack `.zip` (e.g. a "crazy chambers"-style pack) — into `plugins/BetterTrialChambers/dungeon/import/` and import them as rooms; jigsaw blocks become connectors automatically. See [dungeon.yml](../configuration/dungeon.yml.md#importing-datapack-rooms-v170) for details and limits.

Rooms are matched on opposite-facing connectors across all four rotations, placed without overlap, and the result is snapshotted + registered like any other chamber (so resets, loot and protection all apply). `required-tags` in `dungeon.yml` guarantee e.g. one entrance and at least one vault.

</details>

<details>

<summary><code>/trial reset &lt;chamber&gt;</code></summary>

Forces an immediate chamber reset.

**Usage:**

```
/trial reset <chamber_name>
```

**Permission:** `tcp.admin.reset`

**Arguments:**

* `<chamber_name>` - Name of the chamber to reset

**Examples:**

```
/trial reset MainChamber
/trial reset NetherChamber1
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

<summary><code>/trial list</code></summary>

Lists all registered chambers.

**Usage:**

```
/trial list
```

**Permission:** `tcp.admin`

**Example output:**

```
[BTC] === Registered Chambers ===
[BTC] MainChamber - world (12,847 blocks)
[BTC] NetherChamber1 - world_nether (8,521 blocks)
[BTC] OceanChamber - world (15,392 blocks)
```

Shows chamber name, world, and total block count.

</details>

<details>

<summary><code>/trial info [chamber]</code></summary>

Shows plugin information (when used without arguments) or detailed chamber information (when a chamber name is provided).

**Usage:**

```
/trial info
/trial info <chamber_name>
```

**Permission:** `tcp.admin`

**Arguments:**

* `[chamber_name]` - Optional: Name of the chamber to show details for

**Examples:**

```
/trial info
/trial info MainChamber
```

**Plugin Info (no arguments)**

When used without arguments, shows plugin-wide information:

**Example output:**

```
[BTC] === BetterTrialChambers Plugin Info ===
[BTC] Version: 1.2.22
[BTC] Authors: DarkStarWorks
[BTC] Database: SQLITE
[BTC] Registered Chambers: 5
[BTC] Platform: Paper/Spigot
[BTC] --- Integrations ---
[BTC]   WorldEdit/FAWE: ✓
[BTC]   WorldGuard: ✗
[BTC]   PlaceholderAPI: ✓
[BTC]   Vault: ✗
[BTC] --- Features ---
[BTC]   Per-Player Loot: ✓
[BTC]   Spawner Waves: ✓
[BTC]   Spectator Mode: ✓
[BTC]   Statistics: ✓
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
[BTC] === Chamber Info: MainChamber ===
[BTC] World: world
[BTC] Bounds: -150,-20,400 to -50,40,500
[BTC] Volume: 12,847 blocks
[BTC] Exit: -145, 65, 395
[BTC] Reset Interval: 48 hours
[BTC] Last Reset: 2 hours ago
[BTC] Snapshot: Created
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

<summary><code>/trial pause &lt;chamber&gt;</code> / <code>/trial resume &lt;chamber&gt;</code></summary>

Pause or resume a registered chamber.

**Usage:**

```
/trial pause <chamber_name>
/trial resume <chamber_name>
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

<summary><code>/trial container &lt;action&gt; &lt;chamber&gt; [#]</code></summary>

Manage per-player container loot ([`chests.per-player-loot`](../configuration/config.yml.md#per-player-chamber-container-loot)) for a chamber. CLI parity with the chamber GUI's **Container Loot** screen (`/trial menu <chamber>` → Container Loot). _(Added in 1.5.9; reworked in 1.6.3.)_

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
/trial container list MainChamber
/trial container materialize MainChamber
/trial container edit MainChamber 3
/trial container resetone MainChamber 3
```

</details>

<details id="tcp-delete-chamber">

<summary><code>/trial delete &lt;chamber&gt;</code></summary>

Permanently deletes a chamber and all associated data.

**Usage:**

```
/trial delete <chamber_name>
```

**Permission:** `tcp.admin.create`

**Arguments:**

* `<chamber_name>` - Name of the chamber to delete

**Examples:**

```
/trial delete OldChamber
/trial delete TestChamber
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

<summary><code>/trial vault reset &lt;chamber&gt; &lt;player&gt; [type]</code></summary>

Resets a player's vault cooldowns for a specific chamber.

**Usage:**

```
/trial vault reset <chamber_name> <player_name> [normal|ominous]
```

**Permission:** `tcp.admin.vault`

**Arguments:**

* `<chamber_name>` - Chamber name
* `<player_name>` - Player name (can be offline)
* `[type]` - Optional: `normal` or `ominous` (resets all if not specified)

**Examples:**

```
/trial vault reset MainChamber Steve
/trial vault reset MainChamber Alex normal
/trial vault reset OceanChamber Bob ominous
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

<summary><code>/trial key give &lt;player&gt; &lt;amount&gt; [type]</code></summary>

Gives trial keys to a player.

**Usage:**

```
/trial key give <player_name> <amount> [normal|ominous]
```

**Permission:** `tcp.admin.key`

**Arguments:**

* `<player_name>` - Player to give keys to (must be online)
* `<amount>` - Number of keys (positive integer)
* `[type]` - Optional: `normal` or `ominous` (default: `normal`)

**Examples:**

```
/trial key give Steve 5
/trial key give Alex 10 normal
/trial key give Bob 3 ominous
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

<summary><code>/trial key check &lt;player&gt;</code></summary>

Checks how many trial keys a player has.

**Usage:**

```
/trial key check <player_name>
```

**Permission:** `tcp.admin.key`

**Arguments:**

* `<player_name>` - Player to check (must be online)

**Examples:**

```
/trial key check Steve
```

**Example output:**

```
[BTC] Steve has 5 Normal Key(s) and 2 Ominous Key(s).
```

Counts ALL keys in the player's inventory (all slots combined).

</details>

<details>

<summary><code>/trial stats [player]</code></summary>

View player statistics for Trial Chamber activity.

**Usage:**

```
/trial stats
/trial stats <player_name>
```

**Permission:**

* `tcp.stats` - View own stats
* `tcp.admin.stats` - View other players' stats

**Arguments:**

* `[player_name]` - Optional: Player to view stats for (requires admin permission)

**Examples:**

```
/trial stats
/trial stats Steve
```

**Example output:**

```
[BTC] === Statistics for Steve ===
[BTC] Chambers Completed: 12
[BTC] Normal Vaults Opened: 45
[BTC] Ominous Vaults Opened: 18
[BTC] Mobs Killed: 324
[BTC] Deaths: 7
[BTC] Time Spent: 5h 32m
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

<summary><code>/trial leaderboard &lt;type&gt;</code></summary>

View top players for a specific statistic.

**Usage:**

```
/trial leaderboard <type>
/trial lb <type>
/trial top <type>
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
/trial leaderboard chambers
/trial lb normal
/trial top time
```

**Example output:**

```
[BTC] === Top Players - Chambers Completed ===
[BTC] #1 Steve: 47
[BTC] #2 Alex: 42
[BTC] #3 Bob: 38
[BTC] #4 Charlie: 35
[BTC] #5 Diana: 31
```

**Configuration:**

* Number of players shown: `statistics.top-players-count` in config.yml (default: 10)
* Update frequency: `statistics.leaderboard-update-interval` in config.yml (default: 1 hour)

{% hint style="info" %}
**Leaderboards are cached** to prevent database lag. They update on the interval specified in config, not in real-time.
{% endhint %}

</details>

<details>

<summary><code>/trial claims scan</code></summary>

Checks every registered chamber against existing claims from any installed land-claim plugin (**Residence**, **Lands**, **GriefPrevention**) and logs a warning to the console for each overlap. Use it to find chambers that were registered on top of — or had a claim made inside them before — the [claim integrations](../configuration/config.yml.md#residence-integration-lands-integration-griefprevention-integration) existed. _(Added in 1.5.15.)_

**Usage:**

```
/trial claims scan
```

**Permission:** `tcp.admin.reload`

**What it does:**

* For each enabled integration, walks that plugin's claims once and reports any that overlap a chamber's bounds.
*   Logs one line per conflict to the **server console**, e.g.:

    ```
    [BTC] Claim conflict: chamber 'arena3' (world 120,-44,310) overlaps GriefPrevention claim(s): Steve
    ```
* Replies in chat with the total number of conflicting chambers (or "No claim conflicts found.").

This also runs automatically on startup unless you set [`protection.claim-conflict-scan-on-startup: false`](../configuration/config.yml.md#claim-conflict-scan-on-startup).

**How to resolve a reported conflict:**

1. Note the chamber name, the location, and the claim owner from the log line.
2. Decide which should win that space:
   * **Keep the chamber:** remove or resize the claim in the claim plugin (e.g. Residence `/res remove`, Lands `/unclaim`, GriefPrevention claim resize/abandon), then re-run `/trial claims scan` to confirm it's clear.
   * **Keep the claim:** [delete the chamber](#tcp-delete-chamber) (`/trial delete <chamber>`) or move/re-register it elsewhere.
3. New claims can no longer be made into chambers, so once existing conflicts are cleared they won't reappear (except for players with the relevant `tcp.bypass.*` permission).

</details>

<details>

<summary><code>/trial reload</code></summary>

Reloads the plugin configuration without restarting the server.

**Usage:**

```
/trial reload
```

**Permission:** `tcp.admin.reload`

**Example:**

```
/trial reload
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

<summary><code>/trial update</code></summary>

_(Added in 1.8.0.)_ Check for, download, and install BetterTrialChambers updates. Updates are looked up on Modrinth (with GitHub Releases as a fallback).

**Usage:**

```
/trial update [check|download|apply|ignore <version>|unignore <version>|restore|status]
```

**Permission:** `tcp.admin`

**Subcommands:**

* `check` (default when no argument is given) — check now and report the result.
* `status` — show the last known result without making a network call.
* `download` — download the latest release, verify its checksum, back up the current jar, and stage the new one for install on the **next restart**. Requires `update.mode: download` or `auto-stage`.
* `apply` — hot-swap a staged update into place **without a restart**. Requires `update.allow-hot-reload: true` (and is refused on Folia or when other plugins depend on BTC). See below.
* `restore` — stage the most recent backup for install on the next restart, rolling back a bad update.
* `ignore <version>` — stop notifications for a specific version until a newer one is released.
* `unignore <version>` — undo `ignore` for a version.

**Behaviour is set in config.yml** under [`update`](../configuration/config.yml.md#updates). In the default `notify` mode the plugin only tells you an update exists — `download` and `apply` are rejected until you raise the mode.

**Typical restart-based flow:**

```
/trial update check       # see what's available
/trial update download    # verify + stage it
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
/trial generate wand MyChamber

# 3. Scan is automatic, but you can re-scan if needed
/trial scan MyChamber

# 4. Set exit location (stand outside chamber)
/trial setexit MyChamber

# 5. Snapshot is automatic, but you can update it
/trial snapshot create MyChamber

# 6. Check chamber info
/trial info MyChamber

# 7. Test the reset
/trial reset MyChamber
```

</details>

<details>

<summary><strong>Managing Player Issues</strong></summary>

```bash
# Player accidentally used all keys
/trial key give Steve 5 normal

# Player's vaults stuck on cooldown (bug)
/trial vault reset MainChamber Steve

# Check player's statistics
/trial stats Steve

# Check player's key inventory
/trial key check Steve
```

</details>

<details>

<summary><strong>Event Setup</strong></summary>

```bash
# Give all online players keys for event
/trial key give Player1 10
/trial key give Player2 10
/trial key give Player3 10

# After event, check leaderboards
/trial leaderboard chambers
/trial leaderboard time

# Reset chamber immediately for next group
/trial reset EventChamber
```

</details>

<details>

<summary><strong>Maintenance Tasks</strong></summary>

```bash
# List all chambers
/trial list

# Check each chamber's status
/trial info MainChamber
/trial info NetherChamber

# Update snapshots after building changes
/trial snapshot create MainChamber
/trial snapshot create NetherChamber

# Reload config after edits
/trial reload
```

</details>

***

## Pro Tips

{% hint style="success" %}
**Use tab completion!** Press `Tab` while typing commands to autocomplete chamber names, player names, and arguments.
{% endhint %}

{% hint style="info" %}
**Aliases:** All leaderboard commands work with `/trial lb` and `/trial top` for quick access.
{% endhint %}

{% hint style="warning" %}
**Chamber names are case-sensitive** in some commands. Use tab completion to ensure correct capitalization.
{% endhint %}

{% hint style="info" %}
**Offline player support:** Most commands work with offline players (like `/trial vault reset`), but `/trial key give` requires the player to be online.
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
* Run `/trial help` to see available commands

**"You don't have permission to use this command"**

* Check with your server admin for permissions
* See [Permissions](permissions.md) for the full list

**"Chamber not found"**

* Use `/trial list` to see all chambers
* Chamber names are case-sensitive
* Use tab completion to avoid typos

**"No WorldEdit selection found"**

* Make sure WorldEdit is installed
* Use `/wand` and select two corners
* Your selection must have volume (not flat)

**"Player not found or not online"**

* Player must be online for `/trial key give` and `/trial key check`
* For offline players, use `/trial vault reset` (works offline)

**"Snapshot operation failed"**

* Check console for detailed error
* Ensure disk space is available
* Verify file permissions on `plugins/BetterTrialChambers/snapshots/` folder

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

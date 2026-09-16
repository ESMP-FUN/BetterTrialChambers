# Commands

Every command starts with `/trial`. It also answers to `/btc`, `/tcp`, `/bettertrialchambers`, and `/chamber`.

Press `Tab` while typing for suggestions (chamber names, player names, sub-actions). You only see commands you have permission for.

**Argument style:** `<name>` is required, `[name]` is optional. `a|b` means pick one of the listed words.

**Chamber names:** letters, numbers, `-` and `_`, up to 32 characters. The name is also the name of the chamber's save file, which is why it has to stay simple. For a prettier name in announcements, use `/trial rename` to set a display name, which can be anything you like.

For the full permission list, see the [Permissions](permissions.md) page.

***

## Command list

### Setup and information

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial help` | Show the command list | None |
| `/trial setup [start\|continue]` | Guided, optional walk-through of the main settings. `continue` resumes a paused tour | `btc.admin.setup` |
| `/trial menu [chamber]` | Open the admin GUI. With a chamber name, open that chamber's page directly | `btc.admin.menu` |
| `/trial list [page]` | List registered chambers, 10 per page | `btc.admin` |
| `/trial list current` | Report the chamber you are standing in, or the nearest one. Aliases: `here`, `near`, `nearest` | `btc.admin` |
| `/trial info` | Show plugin version, database, platform, and integration status | `btc.admin` |
| `/trial info <chamber>` | Show one chamber's world, bounds, size, exit, reset interval, last reset, and snapshot status | `btc.admin` |
| `/trial debug schema` | Print each database table's real columns | `btc.admin.reload` |
| `/trial debug structure` | Test that this server can save a block with its contents and put it back unchanged | `btc.admin.reload` |
| `/trial claims scan` | List chambers that overlap an existing land claim (Residence, Lands, GriefPrevention) | `btc.admin.reload` |
| `/trial reload` | Reload `config.yml`, `loot.yml`, `messages.yml`, and `spawner_presets.yml` | `btc.admin.reload` |
| `/trial update [check\|status\|download\|apply\|restore\|ignore <version>\|unignore <version>]` | Check for and install plugin updates | `btc.admin` |

### Chambers

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial generate wand <chamber>` | Register a chamber from your WorldEdit selection | `btc.admin.generate` |
| `/trial generate coords <corner1> <corner2> [world] <chamber>` | Register a chamber from two corner coordinates. `[world]` is required from console | `btc.admin.generate` |
| `/trial generate blocks <amount> [chamber] [roundingAllowance]` | Register a chamber in front of you sized to roughly `<amount>` blocks | `btc.admin.generate` |
| `/trial generate value save <name>` | Save your WorldEdit selection under a name for later | `btc.admin.generate` |
| `/trial generate value list` | List saved selections | `btc.admin.generate` |
| `/trial generate value delete <name>` | Delete a saved selection | `btc.admin.generate` |
| `/trial generate value <name> [chamber]` | Register a chamber from a saved selection | `btc.admin.generate` |
| `/trial paste <schematic> [x y z]` | Paste a schematic (preview first, then confirm). Defaults to your position | `btc.admin.generate` |
| `/trial scan <chamber>` | Find vaults, trial spawners, and decorated pots inside the chamber's current bounds | `btc.admin.scan` |
| `/trial scan add <chamber>` | Grow the chamber's bounds to take in sections that auto-discovery missed, then re-scan. Stand inside the chamber | `btc.admin.scan` |
| `/trial setexit <chamber>` | Save your position and facing as the chamber's exit point. Player only | `btc.admin.create` |
| `/trial rename <chamber> <display name>` | Set a friendly display name. Use `none`, `reset`, or `-` to clear it | `btc.admin.create` |
| `/trial pause <chamber>` | Pause a chamber: stop resets, protection, and vault use, keep all data | `btc.admin.pause` |
| `/trial resume <chamber>` | Resume a paused chamber | `btc.admin.pause` |
| `/trial delete <chamber>` | Permanently delete a chamber and its data (the snapshot file stays on disk) | `btc.admin.create` |

### Snapshots

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial snapshot create [chamber]` | Save the chamber's current blocks as its reset state. Omit the name while standing inside it | `btc.admin.snapshot` |
| `/trial snapshot update [chamber]` | Same as `create`; overwrites the existing snapshot | `btc.admin.snapshot` |
| `/trial snapshot restore [chamber]` | Reset the chamber from its snapshot now (same as `/trial reset`) | `btc.admin.snapshot` |
| `/trial snapshot create all [force]` | Snapshot every chamber that has none, one at a time. `force` re-snapshots all of them | `btc.admin.snapshot` |
| `/trial snapshot missing [page]` | List chambers with no snapshot, each with a clickable `[Create]` | `btc.admin.snapshot` |

### Resets

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial reset <chamber>` | Force an immediate reset | `btc.admin.reset` |
| `/trial reset pending` | List chambers waiting for reset confirmation (when confirmation mode is on) | `btc.admin.reset` |
| `/trial reset confirm <chamber\|all>` | Release a queued reset | `btc.admin.reset` |

### Vaults and keys

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial vault reset <chamber> <player> [normal\|ominous]` | Clear one player's vault cooldowns in a chamber. Works for offline players | `btc.admin.vault` |
| `/trial vault unlockall <chamber\|all>` | Re-open every vault in a chamber for everyone | `btc.admin.vault` |
| `/trial key give <player> <amount> [normal\|ominous]` | Give trial keys. Player must be online. Defaults to normal keys | `btc.admin.key` |
| `/trial key check <player>` | Count the trial keys in a player's inventory. Player must be online | `btc.admin.key` |

### Loot

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial loot set <chamber> <normal\|ominous> <table>` | Give a chamber its own loot table for that vault type | `btc.admin.loot` |
| `/trial loot clear <chamber> [normal\|ominous\|all]` | Remove a chamber's loot override. Defaults to `all` | `btc.admin.loot` |
| `/trial loot info <chamber>` | Show which loot tables a chamber uses | `btc.admin.loot` |
| `/trial loot list` | List every loot table from `loot.yml` | `btc.admin.loot` |
| `/trial loot audit` | List old loot entries that lost their custom data before v1.5.0 | `btc.admin.loot` |

### Containers

Per-player container loot ([`chests.per-player-loot`](../configuration/config.yml.md#per-player-chamber-container-loot)). The `#` is a container number from `list`.

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial container list <chamber>` | Show whether the feature is on, plus template and player-copy counts, with each container's number and position | `btc.admin.containers` |
| `/trial container materialize <chamber>` | Scan the chamber and list every container so you can edit it | `btc.admin.containers` |
| `/trial container edit <chamber> <#>` | Open a container to edit. Saving creates an override every player then receives | `btc.admin.containers` |
| `/trial container resetone <chamber> <#>` | Send one container back to fresh per-player loot | `btc.admin.containers` |
| `/trial container reset <chamber>` | Drop every container from the list, overrides included. Alias: `cleartemplates` | `btc.admin.containers` |
| `/trial container clearcopies <chamber>` | Drop every player's private copies, keep overrides | `btc.admin.containers` |
| `/trial container tp <chamber> <#>` | Teleport to a container | `btc.admin.containers` |

`/trial containers` also works as the command name.

### Mobs

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial mobs providers` | List mob providers and whether each is available | `btc.admin.mobs` |
| `/trial mobs <chamber> list` | Show a chamber's mob provider and mob lists | `btc.admin.mobs` |
| `/trial mobs <chamber> provider <id\|vanilla\|none>` | Set the chamber's mob provider | `btc.admin.mobs` |
| `/trial mobs <chamber> add <normal\|ominous> <mobId>` | Add a mob id to a wave pool | `btc.admin.mobs` |
| `/trial mobs <chamber> remove <normal\|ominous> <mobId>` | Remove a mob id from a wave pool | `btc.admin.mobs` |

### Dungeon

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial dungeon pos1` | Mark one corner of a room selection at your feet | `btc.admin.generate` |
| `/trial dungeon pos2` | Mark the opposite corner | `btc.admin.generate` |
| `/trial dungeon capture <id> [roles...]` | Save the selection as a room template. Roles become tags (for example `entrance`, `vault`, `boss`) | `btc.admin.generate` |
| `/trial dungeon generate <name> [seed]` | Stitch a dungeon at your feet and register it as a chamber | `btc.admin.generate` |
| `/trial dungeon list` | List saved room templates | `btc.admin.generate` |
| `/trial dungeon delete <id>` | Delete a room template | `btc.admin.generate` |
| `/trial dungeon import <file\|folder\|zip> [tags...]` | Import `.nbt` structure templates from `plugins/BetterTrialChambers/dungeon/import/` | `btc.admin.generate` |

### Spawner items

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial give <preset> [player] [amount]` | Give a preconfigured trial-spawner item from [spawner_presets.yml](../configuration/spawner-presets.yml.md). Player defaults to you, amount to 1 | `btc.give` |

### Statistics

| Command | What it does | Permission |
| --- | --- | --- |
| `/trial stats` | Show your own statistics | `btc.stats` |
| `/trial stats <player>` | Show another player's statistics | `btc.admin.stats` |
| `/trial leaderboard <type>` | Show the top players for a statistic. Aliases: `lb`, `top` | `btc.leaderboard` |

Leaderboard types: `chambers` (or `completions`), `normal` (or `normalvaults`), `ominous` (or `ominousvaults`), `mobs` (or `kills`), `time` (or `playtime`).

***

## Procedures

### Register an existing chamber

1. Select the chamber with the WorldEdit wand: `/wand`, then left-click one corner and right-click the opposite corner.
2. Register it: `/trial generate wand MyChamber`.
3. Scanning and snapshotting run automatically. Re-run `/trial scan MyChamber` if you change the build.
4. Stand just outside the entrance and run `/trial setexit MyChamber`.
5. Check the result: `/trial info MyChamber`.
6. Test a reset: `/trial reset MyChamber`.

Minimum size is 31 wide, 15 tall, 31 deep. Maximum size is set by `generation.max-volume` in `config.yml`.

### Set up a dungeon

1. Build each room in WorldEdit with complete, solid walls.
2. At every spot a doorway could open, place a `minecraft:jigsaw` block flush in the wall with its front facing outward (`north_up`, `east_up`, `south_up`, or `west_up`). Do not cut the opening yourself. Keep the door size the same across all rooms.
3. Stand at one corner of a room and run `/trial dungeon pos1`, then the opposite corner and `/trial dungeon pos2`.
4. Save it: `/trial dungeon capture <id> <roles...>`, for example `/trial dungeon capture hall entrance`.
5. Repeat for every room. Confirm with `/trial dungeon list`.
6. Stand where the dungeon should start and run `/trial dungeon generate <name>`. Add a number on the end for a repeatable layout.

The generator matches rooms on opposite-facing connectors across all four rotations, places them without overlap, carves a doorway only where two rooms join, walls off unused connectors, then snapshots and registers the result as a normal chamber. `required-tags` in [dungeon.yml](../configuration/dungeon.yml.md) guarantee things like one entrance and at least one vault.

To import rooms instead of building them, drop `.nbt` structure templates (or a datapack `.zip`) into `plugins/BetterTrialChambers/dungeon/import/` and run `/trial dungeon import <file>`. Jigsaw blocks become connectors automatically. See [dungeon.yml](../configuration/dungeon.yml.md#import-datapack-rooms).

***

## Command details

<details>

<summary><code>/trial setup</code></summary>

An optional tour of the major settings, one at a time, each with a plain-English explanation, its current state, and Enable / Skip / Disable buttons. Nothing is changed unless you choose it.

On Paper 1.21.7 and newer the tour uses the native Dialog window. On older servers it falls back to clickable chat with the same content.

**Usage:**

```
/trial setup
/trial setup continue
```

**Permission:** `btc.admin.setup`

A reminder appears for operators who have not run the tour, at most once a week and three times total. Turn it off with `setup.reminder.enabled: false` in `config.yml`.

</details>

<details>

<summary><code>/trial menu [chamber]</code></summary>

Opens the admin GUI. With a chamber name, opens that chamber's detail page directly.

**Usage:**

```
/trial menu
/trial menu MainChamber
```

**Permission:** `btc.admin.menu`

The GUI covers chamber management, loot tables, statistics, protection toggles, and most `config.yml` settings, so no YAML editing is required for day-to-day work.

</details>

<details>

<summary><code>/trial generate</code></summary>

Registers a chamber from a WorldEdit selection, saved selection, coordinates, or a target block count.

**Usage:**

```
/trial generate wand <chamber>
/trial generate coords <x1,y1,z1> <x2,y2,z2> [world] <chamber>
/trial generate blocks <amount> [chamber] [roundingAllowance]
/trial generate value save <name>
/trial generate value list
/trial generate value delete <name>
/trial generate value <name> [chamber]
```

**Permission:** `btc.admin.generate`

**Notes:**

* `coords` also accepts the legacy `<x1,y1,z1-x2,y2,z2>` form. From console you must give `[world]`.
* `blocks` builds in front of you at your facing, rounding up by at most `generation.blocks.rounding-allowance` (default 1000) to make a clean box.
* `value <name>` uses the chamber name if you omit it. If no saved selection exists and you have a live WorldEdit selection, that is used instead.
* WorldEdit is required for `wand` and `value`.
* Minimum size 31 x 15 x 31. Maximum size from `generation.max-volume`.
* Scanning and snapshotting run automatically based on `config.yml`.

</details>

<details id="tcp-scan-chamber">

<summary><code>/trial scan &lt;chamber&gt;</code></summary>

Finds vaults (normal and ominous), trial spawners (normal and ominous), and decorated pots inside the chamber's current bounds.

**Usage:**

```
/trial scan <chamber>
/trial scan add <chamber>
```

**Permission:** `btc.admin.scan`

**Example output:**

```
[BTC] Scanning chamber MainChamber...
[BTC] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

**`/trial scan add`**

Auto-discovery floods outward from a vault or spawner. If nearby chunks were still loading when the chamber was first found, the bounding box can stop at a chunk edge, leaving part of the chamber outside the registered region. A plain `/trial scan` only looks inside the current bounds, so it cannot recover the missing part.

`/trial scan add <chamber>` re-floods from the chamber's known vaults, grows the bounds to take in the missed sections, then re-scans and re-snapshots. Stand inside the chamber so the chunks are loaded.

Re-scanning replaces the old vault and spawner data.

</details>

<details>

<summary><code>/trial setexit &lt;chamber&gt;</code></summary>

Saves your exact position and look direction as the chamber's exit point. Players still inside when the chamber resets teleport here.

**Usage:**

```
/trial setexit <chamber>
```

**Permission:** `btc.admin.create` (player only)

Stand just outside the entrance, facing the way you want players to look, then run the command.

</details>

<details>

<summary><code>/trial rename &lt;chamber&gt; &lt;display name&gt;</code></summary>

Sets a friendly display name for a chamber. The internal name (used in commands) does not change.

**Usage:**

```
/trial rename MainChamber The Copper Vaults
/trial rename MainChamber none
```

**Permission:** `btc.admin.create`

`none`, `reset`, or `-` clears the display name.

Colours and formatting work here (`&6The Copper Vaults` or `<gold>The Copper Vaults</gold>`). Anything that would make the name clickable or hoverable is removed, because the name goes into announcements everyone sees.

</details>

<details>

<summary><code>/trial snapshot</code></summary>

Manages the saved block state a chamber resets to.

**Usage:**

```
/trial snapshot create [chamber]
/trial snapshot update [chamber]
/trial snapshot restore [chamber]
/trial snapshot create all [force]
/trial snapshot missing [page]
```

**Permission:** `btc.admin.snapshot`

* Omit the chamber name on `create`, `update`, and `restore` while standing inside a chamber to target that one.
* `create` and `update` do the same thing: scan every block in the bounds, save types, orientations, and container contents, compress it to `snapshots/<chamber>.dat`, and overwrite any existing snapshot. Takes a few seconds to about a minute depending on size.
* `restore` resets the chamber from its snapshot immediately.
* `create all` snapshots every chamber that has none, one at a time with a short pause between each, reporting progress every 10 chambers. Add `force` to re-snapshot every chamber.
* `missing` lists chambers with no snapshot, 10 per page, each with a clickable `[Create]` and a `[Create all]` header button. A chamber with no snapshot cannot be reset until one is captured.

```
/trial snapshot create MainChamber
/trial snapshot create all
/trial snapshot create all force
/trial snapshot missing 2
```

</details>

<details>

<summary><code>/trial reset &lt;chamber&gt;</code></summary>

Forces an immediate reset: teleports players inside to the exit, restores blocks from the snapshot, clears vault cooldowns, and removes spawned mobs and ground items (both configurable).

**Usage:**

```
/trial reset <chamber>
/trial reset pending
/trial reset confirm <chamber|all>
```

**Permission:** `btc.admin.reset`

`pending` and `confirm` are only used when `global.reset-require-confirmation` is on. Due chambers wait until an operator releases them with `confirm`.

When `reset-vault-cooldowns: true` (default), cooldowns are cleared in the database and in Minecraft's own vault block state, so players can loot vaults again straight away.

</details>

<details>

<summary><code>/trial list</code></summary>

Lists registered chambers, 10 per page, with Prev and Next buttons. Each line shows the chamber name, world, and block count; click a name to copy it or `[menu]` to open its GUI page.

**Usage:**

```
/trial list
/trial list 2
/trial list current
```

**Permission:** `btc.admin`

`current` (also `here`, `near`, `nearest`) reports the chamber you are standing in, or the nearest one in your world with its distance and center.

</details>

<details>

<summary><code>/trial info [chamber]</code></summary>

With no argument, shows plugin version, authors, database type, chamber count, server platform, integration status (WorldEdit, WorldGuard, PlaceholderAPI, Vault), and feature status.

With a chamber name, shows that chamber's world, bounds, block volume, exit location, reset interval, last reset time, snapshot status, and whether it is paused.

**Usage:**

```
/trial info
/trial info MainChamber
```

**Permission:** `btc.admin`

</details>

<details id="tcp-delete-chamber">

<summary><code>/trial delete &lt;chamber&gt;</code></summary>

Permanently deletes a chamber, its vault and spawner data, and its player cooldowns. The snapshot file on disk is not deleted.

**Usage:**

```
/trial delete OldChamber
```

**Permission:** `btc.admin.create`

This cannot be undone.

</details>

<details>

<summary><code>/trial pause &lt;chamber&gt;</code> / <code>/trial resume &lt;chamber&gt;</code></summary>

**Usage:**

```
/trial pause <chamber>
/trial resume <chamber>
```

**Permission:** `btc.admin.pause`

While paused:

* All data (record, stats, vault history, snapshot) is kept.
* Automatic resets stop.
* Protection events (block break and place, container access, mob griefing) are skipped.
* Vault use is blocked with a message.
* Player entry/exit and spawner wave tracking are silent.

Pausing does not delete data, remove mobs or items already inside, or stop players entering the region.

Set `protection.auto-pause-on-destruction: true` in `config.yml` to pause chambers automatically once a set number of vaults or trial spawners are destroyed.

</details>

<details id="tcp-container-action-chamber">

<summary><code>/trial container &lt;action&gt; &lt;chamber&gt; [#]</code></summary>

Manages per-player container loot ([`chests.per-player-loot`](../configuration/config.yml.md#per-player-chamber-container-loot)). Same features as the chamber GUI's Container Loot screen.

Untouched containers roll fresh loot per player on every open. Editing a container creates an override that all players then receive a copy of; reverting drops the override.

**Permission:** `btc.admin.containers`

| Action | Effect |
| --- | --- |
| `list <chamber>` | Show whether the feature is on, plus template and player-copy counts and each container's number and position |
| `materialize <chamber>` | Scan the chamber and list every container. Listing only; does not freeze loot |
| `edit <chamber> <#>` | Open a container to edit. Saving creates an override, re-cloned to every player each reset |
| `resetone <chamber> <#>` | Send one container back to fresh per-player rolls |
| `reset <chamber>` | Remove every container from the list, overrides included. Alias: `cleartemplates` |
| `clearcopies <chamber>` | Drop every player's private copies. Overrides kept |
| `tp <chamber> <#>` | Teleport to a container |

```
/trial container list MainChamber
/trial container materialize MainChamber
/trial container edit MainChamber 3
/trial container resetone MainChamber 3
```

</details>

<details>

<summary><code>/trial vault reset &lt;chamber&gt; &lt;player&gt; [normal|ominous]</code></summary>

Clears one player's cooldowns for every vault in a chamber, in the database and in Minecraft's own vault block state. Works for offline players. With `normal` or `ominous`, only that vault type is cleared.

**Usage:**

```
/trial vault reset MainChamber Steve
/trial vault reset MainChamber Alex normal
```

**Permission:** `btc.admin.vault`

</details>

<details>

<summary><code>/trial vault unlockall &lt;chamber|all&gt;</code></summary>

Re-opens every vault in a chamber for everyone: clears the plugin's record and Minecraft's "already rewarded" list on each vault block. Use `all` for every registered chamber.

**Usage:**

```
/trial vault unlockall MainChamber
/trial vault unlockall all
```

**Permission:** `btc.admin.vault`

Mainly needed after switching `vaults.loot-mode` to `VANILLA`: Minecraft keeps its own record of who opened each vault, so without this command players who already opened a vault find it shut. A normal reset already does this for one chamber.

</details>

<details>

<summary><code>/trial key give &lt;player&gt; &lt;amount&gt; [normal|ominous]</code></summary>

Gives trial keys to an online player. Defaults to normal keys.

**Usage:**

```
/trial key give Steve 5
/trial key give Bob 3 ominous
```

**Permission:** `btc.admin.key`

</details>

<details>

<summary><code>/trial key check &lt;player&gt;</code></summary>

Counts every trial key in an online player's inventory.

**Usage:**

```
/trial key check Steve
```

**Permission:** `btc.admin.key`

**Example output:**

```
[BTC] Steve has 5 Normal Key(s) and 2 Ominous Key(s).
```

</details>

<details>

<summary><code>/trial loot</code></summary>

**Usage:**

```
/trial loot set <chamber> <normal|ominous> <table>
/trial loot clear <chamber> [normal|ominous|all]
/trial loot info <chamber>
/trial loot list
/trial loot audit
```

**Permission:** `btc.admin.loot`

* `set` points one vault type in a chamber at a named table from `loot.yml`.
* `clear` removes the override. With no type, clears both.
* `info` shows which tables a chamber currently uses.
* `list` shows every table name in `loot.yml`.
* `audit` lists loot entries added before v1.5.0 that lost their enchantments, potion types, or custom names. Re-add those through the loot editor.

</details>

<details>

<summary><code>/trial mobs</code></summary>

**Usage:**

```
/trial mobs providers
/trial mobs <chamber> list
/trial mobs <chamber> provider <id|vanilla|none>
/trial mobs <chamber> add <normal|ominous> <mobId>
/trial mobs <chamber> remove <normal|ominous> <mobId>
```

**Permission:** `btc.admin.mobs`

`providers` lists every mob provider (vanilla, MythicMobs, EliteMobs, and so on) and whether it is available. `provider vanilla` and `provider none` both return the chamber to vanilla mobs. Adding mob ids without setting a non-vanilla provider has no effect and warns you.

</details>

<details>

<summary><code>/trial dungeon</code></summary>

**Usage:**

```
/trial dungeon pos1
/trial dungeon pos2
/trial dungeon capture <id> [roles...]
/trial dungeon generate <name> [seed]
/trial dungeon list
/trial dungeon delete <id>
/trial dungeon import <file|folder|zip> [tags...]
```

**Permission:** `btc.admin.generate`

See [Set up a dungeon](#set-up-a-dungeon) above and [dungeon.yml](../configuration/dungeon.yml.md).

`import` reads from `plugins/BetterTrialChambers/dungeon/import/`. A loose `.nbt` imports one room, a folder imports every `.nbt` inside, and a datapack `.zip` imports every structure it contains.

</details>

<details>

<summary><code>/trial give &lt;preset&gt; [player] [amount]</code></summary>

Gives a preconfigured `trial_spawner` item built from a named entry in [spawner_presets.yml](../configuration/spawner-presets.yml.md). Player defaults to you (required from console), amount defaults to 1.

**Usage:**

```
/trial give ominous_zombies
/trial give ominous_zombies Steve 2
```

**Permission:** `btc.give`

Run `/trial reload` after editing the preset file. If a player's inventory is full the overflow drops at their feet.

</details>

<details>

<summary><code>/trial stats [player]</code></summary>

Shows chambers completed, normal and ominous vaults opened, mobs killed, deaths, and time spent in chambers.

**Usage:**

```
/trial stats
/trial stats Steve
```

**Permission:** `btc.stats` for your own, `btc.admin.stats` for another player.

Requires `statistics.enabled: true` in `config.yml`.

</details>

<details>

<summary><code>/trial leaderboard &lt;type&gt;</code></summary>

Shows the top players for one statistic. Also `/trial lb` and `/trial top`.

**Usage:**

```
/trial leaderboard chambers
/trial lb normal
/trial top time
```

**Permission:** `btc.leaderboard`

Types: `chambers` (`completions`), `normal` (`normalvaults`), `ominous` (`ominousvaults`), `mobs` (`kills`), `time` (`playtime`).

Player count comes from `statistics.top-players-count` (default 10). Results are cached and refresh on `statistics.leaderboard-update-interval` (default 1 hour).

</details>

<details>

<summary><code>/trial claims scan</code></summary>

Checks every chamber against claims from any installed land-claim plugin (Residence, Lands, GriefPrevention) and logs one console line per overlap, then replies with the total.

**Usage:**

```
/trial claims scan
```

**Permission:** `btc.admin.reload`

Also runs on startup unless `protection.claim-conflict-scan-on-startup: false`.

To fix a reported overlap, decide which should keep the space: remove or resize the claim in the claim plugin, or [delete the chamber](#tcp-delete-chamber). New claims can no longer be made inside chambers, so cleared conflicts stay cleared.

</details>

<details>

<summary><code>/trial reload</code></summary>

Reloads `config.yml`, `loot.yml`, `messages.yml`, and `spawner_presets.yml`, and clears the chamber lookup cache.

**Usage:**

```
/trial reload
```

**Permission:** `btc.admin.reload`

Database settings and running reset timers do not reload. Change database settings, then restart the server.

</details>

<details>

<summary><code>/trial update</code></summary>

Checks for and installs BetterTrialChambers updates, looked up on Modrinth with GitHub Releases as a fallback.

**Usage:**

```
/trial update [check|status|download|apply|restore|ignore <version>|unignore <version>]
```

**Permission:** `btc.admin`

* `check` (the default) checks now and reports.
* `status` shows the last result without a network call.
* `download` downloads the latest release, verifies its checksum, backs up the current jar, and stages the new one for the next restart. Needs `update.mode: download` or `auto-stage`.
* `apply` swaps a staged update in without a restart. Needs `update.allow-hot-reload: true`; refused on Folia or when other plugins depend on BTC.
* `restore` stages the most recent backup for the next restart.
* `ignore <version>` / `unignore <version>` mute or unmute notifications for one version.

In the default `notify` mode the plugin only reports that an update exists. Behaviour is set under [`update`](../configuration/config.yml.md#updates) in `config.yml`.

</details>

<details>

<summary><code>/trial debug</code></summary>

**Usage:**

```
/trial debug schema
/trial debug structure
```

**Permission:** `btc.admin.reload`

* `schema` prints each database table's real columns.
* `structure` tests, in empty air high above the world, whether this server can save a block with everything it holds and put it back unchanged, then reports one line per check.

</details>

***

## Common command sequences

<details>

<summary><strong>Register an existing chamber</strong></summary>

```bash
/wand
# left-click one corner, right-click the opposite corner
/trial generate wand MyChamber
/trial scan MyChamber
/trial setexit MyChamber
/trial snapshot create MyChamber
/trial info MyChamber
/trial reset MyChamber
```

</details>

<details>

<summary><strong>Handle player issues</strong></summary>

```bash
/trial key give Steve 5 normal
/trial vault reset MainChamber Steve
/trial stats Steve
/trial key check Steve
```

</details>

<details>

<summary><strong>Run an event</strong></summary>

```bash
/trial key give Player1 10
/trial key give Player2 10
/trial leaderboard chambers
/trial leaderboard time
/trial reset EventChamber
```

</details>

<details>

<summary><strong>Routine maintenance</strong></summary>

```bash
/trial list
/trial info MainChamber
/trial snapshot create MainChamber
/trial reload
```

</details>

***

## Troubleshooting

**"Unknown subcommand"** - check spelling with tab completion, and check you have permission. Run `/trial help`.

**"You don't have permission"** - see the [Permissions](permissions.md) page.

**"Chamber not found"** - run `/trial list`. Names are case-sensitive; use tab completion.

**"No WorldEdit selection found"** - install WorldEdit, run `/wand`, and select two corners that enclose a volume.

**"Player not found or not online"** - `/trial key give` and `/trial key check` need the player online. `/trial vault reset` works offline.

**"Snapshot operation failed"** - check the console, confirm disk space, and check file permissions on `plugins/BetterTrialChambers/snapshots/`.

***

## Related pages

{% content-ref url="permissions.md" %}
[permissions.md](permissions.md)
{% endcontent-ref %}

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

{% content-ref url="../getting-started/your-first-chamber.md" %}
[your-first-chamber.md](../getting-started/your-first-chamber.md)
{% endcontent-ref %}

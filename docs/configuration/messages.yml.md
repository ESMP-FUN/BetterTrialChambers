# messages.yml

Every message players and admins see comes from `plugins/BetterTrialChambers/messages.yml`. Edit the text, keep the `{placeholders}`, reload.

## How to change a message

1. Open `plugins/BetterTrialChambers/messages.yml` in a plain-text editor.
2. Find the key you want (use the tables below, or search the file).
3. Change the text after the colon. Keep the quotes, and keep any `{placeholder}` that was already there, it gets filled in with a real value.
4. Save the file. Use spaces only, never a TAB character, or the whole file fails to load.
5. Run `/trial reload` in-game or from console. Changes apply immediately, no restart.

{% hint style="info" %}
**Back up first.** Copy `messages.yml` somewhere safe before a big edit. If the file has a YAML mistake the plugin logs an error and falls back to built-in wording.
{% endhint %}

***

## Text formatting

You can use either colour style, or mix both on the same line.

### Legacy `&` codes

```yaml
example: "&6&lGold bold text &r&7then gray"
```

`&0`-`&f` are colours, `&l` bold, `&o` italic, `&n` underline, `&m` strikethrough, `&k` magic, `&r` reset. Hex colours: `&#FF5500`.

### MiniMessage tags

```yaml
example: "<gold><bold>Gold bold text</bold></gold> <gray>then gray"
```

Named colours like `<gold>`, plus `<bold>`, `<italic>`, `<underlined>`, `<strikethrough>`, `<obfuscated>`, `<reset>`, and hex `<#FF5500>`. MiniMessage also does things `&` codes cannot:

```yaml
gradient:  "<gradient:#ff0000:#00ff00>red to green</gradient>"
clickable: "<click:run_command:'/trial menu'><yellow>[Open Menu]</yellow></click>"
hover:     "<hover:show_text:'Extra info'><gold>hover me</gold></hover>"
```

### Mixing

```yaml
mixed: "&aHello <gradient:#ff0000:#0000ff>world</gradient>"
```

Both hex forms (`&#RRGGBB` and `<#RRGGBB>`) and gradients are supported since v1.4.0. Put `&r` or `<reset>` before a `{player}` or `{chamber}` placeholder so the name does not inherit the previous colour.

{% hint style="info" %}
GUI item names and lore, boss bars, and chat all render full MiniMessage (gradients, click, hover).
{% endhint %}

***

## Keeping messages.yml up to date after upgrades

Automatic since v1.5.19. On startup BetterTrialChambers adds any message keys introduced in the new version to your existing file, keeping their comments, and leaves your edits untouched. The old file is saved as `messages.yml.bak` first. Console shows:

```
[BTC] messages.yml: added 4 new key(s) introduced in this version
[BTC] (your existing entries are kept; previous file saved as messages.yml.bak).
```

Anything your file is still missing falls back to the plugin's built-in wording at runtime, so nothing ever shows as blank. A startup schema check (from v1.4.1) stays as a backstop and lists missing keys in console if the merge ever fails; silence it with `debug.skip-messages-schema-check: true`.

***

## Message keys

Every entry below exists in the shipped `messages.yml` with that exact spelling. Placeholders are shown in `{braces}`. Most messages get the `prefix` automatically; keys whose name contains `boss-bar`, `header`, `help-`, or `list-item` are shown without it.

<details>

<summary><strong>General</strong></summary>

| Key | What it is |
| --- | --- |
| `prefix` | Text put in front of most messages. Set to `""` for none. |
| `reload-success` | Config reloaded (older wording, still present). |
| `config-reloading` / `config-reloaded` | Shown while and after `/trial reload` runs. |
| `no-permission` | Player lacks the permission for a command. |
| `player-only` | Command was run from console but needs a player. |
| `unknown-command` | Unrecognised `/trial` subcommand. |
| `plugin-starting-up` | Command used before the plugin finished loading. |

</details>

<details>

<summary><strong>Chamber management and auto-discovery</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `chamber-created` | Chamber registered. `{chamber}` |
| `chamber-not-found` | No chamber by that name. `{chamber}` |
| `chamber-deleted` | Chamber unregistered. `{chamber}` |
| `chamber-list-item` | One row of `/trial list` (plain). `{chamber}` `{world}` `{volume}` |
| `chamber-list-empty` | Nothing registered yet. |
| `chamber-renamed` | Display name set. `{chamber}` `{name}` |
| `chamber-rename-cleared` | Display name removed. `{chamber}` |
| `no-selection` | You have no WorldEdit selection. |
| `worldedit-not-found` | WorldEdit missing or disabled. |
| `generation-cancelled-name-in-use` | A chamber already uses that name. `{name}` |
| `error-chamber-creation-failed` | Registration failed, see console. |
| `discovery-registered` | Auto-discovery registered a natural chamber. `{name}` `{vaults}` `{spawners}` |
| `discovery-merged` | Auto-discovery grew a chamber into an adjacent region. `{name}` `{vaults}` `{spawners}` |
| `chamber-created-rollback-tip` | Tip shown after generate: how to undo. `{chamber}` |
| `undo-cleanup-hint` | Tip shown after `//undo` inside a chamber. `{chamber}` |

</details>

<details>

<summary><strong>Chamber pause and resume</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `chamber-paused` | Chamber paused, record kept. `{chamber}` |
| `chamber-resumed` | Chamber resumed. `{chamber}` |
| `chamber-already-paused` | Already paused. `{chamber}` |
| `chamber-not-paused` | Not currently paused. `{chamber}` |
| `chamber-is-paused` | Player tried to interact with a paused chamber. |
| `chamber-auto-paused` | Auto-pause tripped by block destruction. `{chamber}` `{count}` `{block}` |

</details>

<details>

<summary><strong>Scanning</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `scan-started` | Scan begun. `{chamber}` |
| `scan-complete` | Scan done. `{chamber}` `{vaults}` `{spawners}` `{pots}` |
| `scan-add-started` | `/trial scan add` re-flood begun. `{chamber}` |
| `scan-add-grown` | Bounds grew into missed sections. `{chamber}` `{added}` `{vaults}` `{spawners}` |
| `scan-add-none` | Nothing extra found. `{chamber}` `{reason}` |
| `usage-scan` | Wrong usage of `/trial scan`. |

</details>

<details>

<summary><strong>Snapshots</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `snapshot-creating` | Snapshot starting. `{chamber}` |
| `snapshot-created` | Snapshot saved. `{blocks}` `{size}` |
| `snapshot-restoring` | Restore starting. `{chamber}` |
| `snapshot-restored` | Restore finished. |
| `snapshot-failed` | Snapshot or restore error. `{error}` |
| `snapshot-not-in-chamber` | Stand in a chamber or name one. |
| `usage-snapshot` | Wrong usage of `/trial snapshot`. |
| `gui-creating-snapshot` / `gui-snapshot-created` / `gui-snapshot-create-failed` | Same, from the GUI. `{chamber}` `{error}` |
| `gui-no-snapshot-exists` / `gui-restoring-snapshot` / `gui-snapshot-restored` / `gui-restore-failed` | Restore, from the GUI. `{chamber}` `{error}` |

**Bulk snapshot (`/trial snapshot create all` and `missing`):**

| Key | What it is / placeholders |
| --- | --- |
| `snapshot-all-none-registered` | No chambers registered. |
| `snapshot-all-covered` | Every chamber already has one. `{count}` |
| `snapshot-all-start` | Backfill starting. `{count}` `{mode}` |
| `snapshot-all-mode-force` / `snapshot-all-mode-missing` | Fills the `{mode}` slot above. |
| `snapshot-all-progress-list-item` | Progress line. `{done}` `{total}` |
| `snapshot-all-complete` | Backfill done. `{created}` `{failed}` |
| `snapshot-all-failed-part` | Appended when some failed. `{failed}` |
| `snapshot-missing-none` | Nothing missing a snapshot. |
| `snapshot-missing-page-header` | Header of the missing list. `{page}` `{maxPage}` `{total}` |
| `snapshot-missing-list-item` | One missing chamber (clickable). `{chamber}` `{world}` |
| `snapshot-missing-list-item-console` | Same, plain, for console. `{chamber}` `{world}` |
| `snapshot-reminder` | Join reminder about un-snapshotted chambers. `{count}` `{chambers}` |

</details>

<details>

<summary><strong>Chamber resets</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `chamber-reset-warning` | Countdown warning before a reset. `{chamber}` `{time}` |
| `chamber-reset-complete` | Reset finished (generic broadcast). |
| `chamber-cleared-broadcast` | Announced when players clear every spawner. `{chamber}` `{players}` |
| `chamber-resetting` | Reset in progress. `{chamber}` |
| `reset-success` | Reset done. `{chamber}` |
| `reset-failed` | Reset error. `{error}` |
| `usage-reset` | Wrong usage of `/trial reset`. |
| `gui-forcing-reset` / `gui-chamber-reset-complete` / `gui-reset-failed` | Reset from the GUI. `{chamber}` `{error}` |
| `gui-reset-scheduled` | Reset scheduled from the GUI. `{chamber}` `{seconds}` |
| `gui-exit-scheduled` / `gui-exit-warning` / `gui-player-ejected` | Players being cleared out before a reset. `{chamber}` `{seconds}` |
| `gui-no-players-in-chamber` / `gui-players-ejected` | Eject action result. `{chamber}` `{count}` |

**Reset confirmation queue (`global.reset-require-confirmation`):**

| Key | What it is / placeholders |
| --- | --- |
| `reset-pending-empty` | Nothing awaiting confirmation. |
| `reset-pending-page-header` | Header of the pending list. `{count}` |
| `reset-pending-list-item` | One chamber awaiting confirmation. `{chamber}` |
| `reset-confirm-usage` | Wrong usage of `/trial reset confirm`. |
| `reset-confirm-all` | Confirmed a batch. `{count}` |
| `reset-confirm-all-none` | Nothing was pending. |
| `reset-confirmed` | Confirmed one chamber. `{chamber}` |
| `reset-confirm-not-pending` | That chamber was not waiting. `{chamber}` |
| `reset-ready-notify` | Operator notice that a chamber is ready to reset. `{chamber}` |

</details>

<details>

<summary><strong>Vaults</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `vault-opened` | Vault opened. `{type}` |
| `vault-cooldown` | Vault on personal cooldown. `{type}` `{time}` |
| `vault-locked` | Already opened, unlocks on reset. `{type}` |
| `vault-locked-shared` | Someone else opened this shared vault first. `{player}` `{type}` |
| `vault-cooldown-shared` | Shared vault, reopens later. `{player}` `{type}` `{time}` |
| `vault-reopened` | Paid keys to reopen a vault. `{type}` `{cost}` |
| `vault-reopen-need` | Hold matching keys to reopen. `{type}` `{cost}` |
| `vault-reset` | An admin cleared your cooldown. `{player}` |
| `wrong-key-type` | Wrong key in hand. `{required_type}` |
| `no-key` | No trial key at all. |
| `vault-not-found` | No vault at that spot. |
| `vault-loot-table-missing` | Configured loot table does not exist (key kept). |
| `vault-no-loot-generated` | Loot table produced nothing (key kept). |
| `vault-error` | Vault block could not be updated (key kept). |
| `wild-vault-place-blocked` / `orphan-spawner-needs-silk-touch` | Placing a vault outside a chamber; retrieving a lone spawner needs Silk Touch. |

**Vault cooldown admin (`/trial vault reset` and `unlockall`):**

| Key | What it is / placeholders |
| --- | --- |
| `vault-reset-usage-hint` | Usage hint. `{chamber}` |
| `vault-reset-all-start` / `vault-reset-all-complete` | Resetting a whole chamber. `{chamber}` `{count}` |
| `vault-reset-single-start` / `vault-reset-single-complete` | Resetting one vault. `{id}` |
| `vault-unlockall-complete` | Reopened vaults for everyone. `{count}` `{chamber}` |
| `vault-unlockall-every-chamber` | Fills `{chamber}` when the target is `all`. |
| `vault-mode-vanilla-hint` | Note shown when vault handling is left to vanilla. |
| `usage-vault-reset` / `usage-vault-unlockall` | Wrong usage. |

</details>

<details>

<summary><strong>Trial keys and preset spawner items</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `key-given` | Keys handed out. `{amount}` `{type}` `{player}` |
| `key-invalid-amount` | Amount was not a positive number. |
| `key-check` | Key counts for a player. `{player}` `{normal}` `{ominous}` |
| `usage-key` / `usage-key-give` / `usage-key-check` | Wrong usage of `/trial key`. |
| `error-ominous-key-unavailable` | This server version has no Ominous Trial Keys. |
| `give-received` / `give-sent` | `/trial give` result. `{amount}` `{preset}` `{player}` |
| `give-usage` / `give-available` / `give-no-presets` / `give-unknown-preset` / `give-bad-amount` / `give-needs-target-from-console` / `give-build-failed` / `give-inventory-full` | `/trial give` errors and hints. `{presets}` `{preset}` `{value}` `{error}` |

</details>

<details>

<summary><strong>Statistics and leaderboards</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `stats-header` | Stats title. `{player}` |
| `stats-chambers` / `stats-normal-vaults` / `stats-ominous-vaults` / `stats-mobs` / `stats-deaths` | One stat line. `{count}` |
| `stats-time` | Time spent line. `{time}` |
| `statistics-disabled` | Stats turned off in config. |
| `invalid-stat-type` | Bad stat name given. |
| `leaderboard-header` | Leaderboard title. `{stat}` |
| `leaderboard-entry` | One leaderboard row. `{rank}` `{player}` `{value}` |
| `leaderboard-empty` | No stats recorded yet. |

</details>

<details>

<summary><strong>Entering, leaving, dying, protection</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `chamber-entered` | Player entered a chamber. `{chamber}` |
| `chamber-exited` | Player left a chamber. |
| `player-died-in-chamber` | Death notice. `{player}` `{chamber}` |
| `teleported-to-exit` | Sent to a chamber's exit point. `{chamber}` |
| `cannot-break-blocks` / `cannot-place-blocks` / `cannot-access-container` | Protection blocked the action. |
| `tunnel-only-outer-wall` | Only the outer wall may be tunnelled. |
| `cannot-claim-in-chamber` | Land claim overlaps a chamber. `{chamber}` |
| `cannot-use-enchant-in-chamber` | That enchant is disabled in chambers. |
| `pvp-disabled-in-chamber` | PvP is off in chambers. |
| `cannot-teleport-into-chamber` | Teleport into a chamber blocked. |
| `cannot-enter-chamber` | No permission to enter this chamber. |

</details>

<details>

<summary><strong>Type labels (used inside other messages)</strong></summary>

| Key | Default | Used for |
| --- | --- | --- |
| `vault-type-normal` / `vault-type-ominous` | `Normal` / `Ominous` | The `{type}` in vault messages. |
| `wave-type-normal` / `wave-type-ominous` | `Trial` / `Ominous` | The `{type}` in `spawner-wave-complete`. |
| `wave-boss-type-normal` / `wave-boss-type-ominous` | `Trial Spawner` / `Ominous Trial` | Spawner boss bar labels. |

</details>

<details>

<summary><strong>Chamber info (`/trial info &#x3C;chamber>`)</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `info-header` | Title. `{chamber}` |
| `info-display-name` | Display name line. `{name}` |
| `info-world` | World line. `{world}` |
| `info-bounds` | Corner coordinates. `{minX}` `{minY}` `{minZ}` `{maxX}` `{maxY}` `{maxZ}` |
| `info-volume` | Block count. `{volume}` |
| `info-exit` | Exit line. `{exit}` |
| `info-reset-interval` | Reset interval. `{interval}` |
| `info-last-reset` | Last reset time. `{time}` |
| `info-snapshot` | Snapshot status. `{status}` |
| `info-paused` | Shown when the chamber is paused. |
| `info-exit-location-set` / `info-exit-location-not-set` | Fills `{exit}` above. `{x}` `{y}` `{z}` |
| `info-snapshot-created` / `info-snapshot-not-created` | Fills `{status}` above. |
| `exit-set` | Exit point saved. `{chamber}` |
| `usage-setexit` | Wrong usage of `/trial setexit`. |

</details>

<details>

<summary><strong>Time formatting</strong></summary>

| Key | Default | Notes |
| --- | --- | --- |
| `time-days` / `time-hours` / `time-minutes` / `time-seconds` | `{days}d` etc. | Non-zero units are joined with spaces, e.g. `2d 5h 30m`. |
| `time-never` | `Never` | No such event yet. |
| `time-now` | `Just now` | Durations under a minute. |
| `time-ago` | `{time} ago` | Wraps a formatted duration for relative timestamps. |

Used for cooldowns, reset warnings, `/trial info`, stats and leaderboards.

</details>

<details>

<summary><strong>Help menu (`/trial help`)</strong></summary>

One line per command. All start with `help-`:

`help-header`, `help-list`, `help-info`, `help-generate`, `help-paste`, `help-scan`, `help-scan-add`, `help-setexit`, `help-snapshot`, `help-snapshot-bulk`, `help-snapshot-missing`, `help-reset`, `help-pause`, `help-resume`, `help-rename`, `help-delete`, `help-loot`, `help-mobs`, `help-vault`, `help-vault-unlockall`, `help-key`, `help-give`, `help-stats`, `help-leaderboard`, `help-menu`, `help-dungeon`, `help-setup`, `help-container`, `help-claims`, `help-debug`, `help-update`, `help-reload`.

</details>

<details>

<summary><strong>Saved selection regions (`/trial generate value`)</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `wevar-saved` / `wevar-save-failed` | Saving your selection under a name. `{name}` |
| `wevar-deleted` / `wevar-not-found` | Deleting or missing a saved region. `{name}` |
| `wevar-list-header` / `wevar-list-item` / `wevar-list-empty` | The saved-region list. `{name}` `{world}` `{minX}`..`{maxZ}` |

</details>

<details>

<summary><strong>Schematic paste (`/trial paste`)</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `paste-loading` | Reading schematic size. |
| `paste-preview-shown` | Preview particles shown. `{schematic}` `{x}` `{y}` `{z}` `{width}` `{height}` `{length}` |
| `paste-confirm-hint` | Type confirm or cancel. `{time}` |
| `paste-confirming` / `paste-success` / `paste-failed` | Paste running / done / failed. `{schematic}` `{x}` `{y}` `{z}` |
| `paste-cancelled` / `paste-timeout` | Cancelled or expired. |
| `paste-undo-hint` | Reminder that `//undo` reverses it. |
| `worldedit-not-available` | WorldEdit or FAWE missing. |
| `schematic-usage` / `schematic-usage-hint` / `schematic-not-found` / `schematic-no-schematics` | Usage and missing-file messages. `{list}` `{name}` |
| `error-invalid-coordinates` | Bad `x y z` given to `/trial paste`. |

</details>

<details>

<summary><strong>Spawner waves</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `spawner-wave-complete` | Wave cleared (chat). `{type}` `{killed}` `{duration}` |
| `spawner-wave-boss-bar-complete` | Boss bar text when a wave is done. |
| `spawner-wave-boss-bar-ominous` / `spawner-wave-boss-bar-normal` | Boss bar title per spawner type. `{wave}` |
| `spawner-wave-boss-bar-progress` | Boss bar progress text. `{type}` `{killed}` `{total}` |

</details>

<details>

<summary><strong>Spectator mode</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `spectate-offer` / `spectate-hint` | Offer shown after dying in a chamber. `{chamber}` |
| `spectate-offer-expired` | Offer timed out. |
| `spectate-started` / `spectate-exit-hint` / `spectate-exited` / `spectate-declined` | Entering, using, leaving spectator mode. `{chamber}` |
| `spectate-boundary-warning` | Tried to leave the chamber while spectating. |
| `spectate-no-players` | No one to watch. |
| `spectate-chamber-not-found` | The chamber is gone. |

</details>

<details>

<summary><strong>Per-chamber loot overrides and audit</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `loot-set-success` | Override set. `{type}` `{chamber}` `{table}` |
| `loot-clear-success` | Override cleared. `{chamber}` |
| `loot-table-not-found` | Table name not in loot.yml. `{table}` |
| `loot-info-header` / `loot-info-normal` / `loot-info-ominous` / `loot-info-default` | `/trial loot info` output. `{chamber}` `{table}` |
| `loot-list-header` / `loot-list-item` | `/trial loot list` output. `{table}` |
| `usage-loot` / `usage-loot-set` / `usage-loot-clear` / `usage-loot-info` | Wrong usage. |
| `error-loot-set-failed` / `error-no-loot-tables` / `error-invalid-type-loot-clear` | Loot command errors. |
| `loot-audit-clean` / `loot-audit-found` / `loot-audit-group-list-item` / `loot-audit-entry-list-item` / `loot-audit-more-list-item` / `loot-audit-hint` | `/trial loot audit` (pre-1.5.0 entries that lost NBT). `{count}` `{group}` `{kind}` `{index}` `{material}` `{reason}` |
| `inventory-full` / `loot-received` / `loot-money-received` | Loot delivery to a player. `{item}` `{amount}` |

</details>

<details>

<summary><strong>Per-player container loot (`/trial container`)</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `container-tp` / `container-teleported` | Teleported to a container template. `{x}` `{y}` `{z}` `{index}` |
| `container-template-reset` / `container-template-reset-none` | Revert a container to vanilla per-player rolls. `{x}` `{y}` `{z}` |
| `container-materialize-start` / `container-materialize-done` | Scanning a chamber for containers. `{chamber}` `{count}` |
| `container-cleared-copies` / `container-cleared-templates` | Clearing player copies or templates. `{count}` |
| `container-usage` / `container-usage-action` / `container-usage-index` | Usage strings. `{action}` |
| `container-list-page-header` / `container-list-stats` / `container-list-item` / `container-list-more` / `container-list-empty-hint` | The container list. `{chamber}` `{state}` `{templates}` `{copies}` `{index}` `{x}` `{y}` `{z}` `{items}` `{count}` |
| `container-state-on` / `container-state-off` | Fills `{state}` above. |
| `container-no-template` / `container-resetone-success` / `container-resetone-failed` | Acting on one container by number. `{index}` `{count}` |
| `container-world-not-loaded` | Chamber world is not loaded. |

</details>

<details>

<summary><strong>Pagination and list locator</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `pagination-list-item` | Prev/Next row template. `{prev}` `{next}` |
| `pagination-prev-active` / `pagination-prev-disabled` / `pagination-next-active` / `pagination-next-disabled` | The clickable arrows. `{command}` |
| `list-page-header` | `/trial list` page header. `{page}` `{maxPage}` `{total}` |
| `chamber-list-item-interactive` | Clickable `/trial list` row. `{chamber}` `{world}` `{volume}` |
| `list-current-inside` / `list-current-none` / `list-current-nearest` | `/trial list current`. `{chamber}` `{volume}` `{distance}` `{x}` `{y}` `{z}` |

</details>

<details>

<summary><strong>Debug and land-claim scan</strong></summary>

| Key | What it is / placeholders |
| --- | --- |
| `debug-usage` | `/trial debug` usage. |
| `debug-structure-no-world` / `debug-structure-running` / `debug-structure-list-item` / `debug-structure-summary` | Structure self-check output. `{world}` `{y}` `{flag}` `{verdict}` `{what}` `{detail}` `{passed}` `{total}` |
| `debug-schema-reading` / `debug-schema-page-header` / `debug-schema-missing-list-item` / `debug-schema-table-list-item` | `/trial debug schema` output. `{type}` `{table}` `{columns}` `{flag}` |
| `claims-usage` / `claims-no-integration` / `claims-scanning` / `claims-conflicts-found` / `claims-no-conflicts` | `/trial claims scan`. `{count}` |

</details>

<details>

<summary><strong>Procedural dungeon assembly (`/trial dungeon`)</strong></summary>

All keys start with `dungeon-`:

`dungeon-usage`, `dungeon-pos-set` (`{label}` `{x}` `{y}` `{z}`), `dungeon-usage-capture`, `dungeon-need-positions`, `dungeon-capturing` / `dungeon-captured` / `dungeon-capture-failed` (`{id}` `{blocks}` `{connectors}` `{tags}` `{error}`), `dungeon-usage-generate`, `dungeon-generating` / `dungeon-generated` / `dungeon-generate-failed` / `dungeon-generate-failed-console` (`{name}` `{seed}` `{error}`), `dungeon-list-empty` / `dungeon-list-page-header` (`{templates}`), `dungeon-usage-delete` / `dungeon-deleted` / `dungeon-delete-not-found` (`{id}`), and the import family `dungeon-usage-import`, `dungeon-import-bad-path`, `dungeon-import-not-found`, `dungeon-importing`, `dungeon-import-unsupported`, `dungeon-import-empty`, `dungeon-import-complete`, `dungeon-import-list-item`, `dungeon-import-more-list-item`, `dungeon-import-note`, `dungeon-import-failed` (`{file}` `{extension}` `{count}` `{connectors}` `{id}` `{blocks}` `{tags}` `{error}`).

</details>

<details>

<summary><strong>Custom mob providers (`/trial mobs`)</strong></summary>

Command output starts with `mobs-`:

`mobs-usage-root`, `mobs-usage-providers`, `mobs-usage-chamber`, `mobs-usage-provider`, `mobs-usage-addremove`, `mobs-providers-header`, `mobs-providers-entry` (`{id}` `{name}` `{status}`), `mobs-status-available` / `mobs-status-unavailable`, `mobs-list-provider` / `mobs-list-normal` / `mobs-list-ominous` (`{chamber}` `{provider}` `{mobs}`), `mobs-list-empty`, `mobs-list-falls-back`, `mobs-provider-unknown` / `mobs-provider-set` / `mobs-provider-update-failed` (`{provider}`), `mobs-bad-wave-type`, `mobs-already-present` / `mobs-not-present` / `mobs-added` / `mobs-removed` / `mobs-update-failed` (`{id}` `{wave}` `{chamber}`), `mobs-vanilla-warning` (`{chamber}`), `mobs-unknown-action` (`{action}`).

GUI equivalents start with `gui-provider-` and `gui-mob-`: `gui-provider-set` / `gui-provider-failed`, `gui-mob-input-prompt` / `gui-mob-input-cancelled` / `gui-mob-input-no-chamber` / `gui-mob-input-duplicate` / `gui-mob-input-added` / `gui-mob-input-failed`, `gui-mob-remove-missing` / `gui-mob-removed` / `gui-mob-remove-failed` (`{section}` `{id}`).

</details>

<details>

<summary><strong>GUI action messages</strong></summary>

Feedback shown in chat after a click in the admin GUI. All start with `gui-`:

| Key | What it is / placeholders |
| --- | --- |
| `gui-chamber-world-not-loaded` | Chamber's world is not loaded. |
| `gui-teleport-to-center` / `gui-teleport-to-exit` | Teleport buttons. `{chamber}` |
| `gui-reset-interval-set` / `gui-reset-interval-failed` | Change reset interval. `{value}` |
| `gui-exit-location-set` / `gui-exit-location-failed` / `gui-no-exit-location` | Set or use the exit point. |
| `gui-spawner-cooldown-set` / `gui-spawner-cooldown-failed` / `gui-spawner-cooldown-reset` / `gui-spawner-cooldown-reset-failed` | Per-chamber spawner cooldown. `{value}` |
| `gui-broadcast-reset-enabled` / `gui-broadcast-reset-disabled` / `gui-broadcast-reset-failed` / `gui-broadcast-reset-global-override` | Per-chamber reset-broadcast toggle. |
| `gui-no-loot-tables` / `gui-loot-table-set` / `gui-loot-table-failed` / `gui-loot-table-cleared` / `gui-loot-clear-failed` | Per-chamber loot table override. `{type}` `{table}` |
| `gui-hold-item-to-add` / `gui-item-added-to-loot` / `gui-item-removed-from-loot` | Loot editor add and remove. `{item}` |
| `gui-loot-changes-saved` / `gui-loot-pool-saved` / `gui-loot-pool-vanished` | Loot editor save. `{pool}` |
| `gui-loot-deposit-added` | Bulk-add deposit chest closed. `{count}` |
| `gui-pool-create-hint` / `gui-pool-create-coming-soon` | Pool creation is loot.yml only for now. |
| `gui-expand-teleported` / `gui-expand-running` / `gui-expand-grown` / `gui-expand-none` | Travel and Expand button. `{chamber}` `{added}` `{vaults}` `{spawners}` |
| `gui-rename-input-prompt` / `gui-rename-input-cancelled` / `gui-rename-input-set` / `gui-rename-input-failed` | Rename via the GUI chat prompt. `{chamber}` `{name}` |
| `error-menu-failed` | GUI failed to open. `{error}` |

</details>

<details>

<summary><strong>Command usage and error strings</strong></summary>

Wrong-usage hints all start with `usage-`, general errors with `error-`. Present keys:

`usage-scan`, `usage-setexit`, `usage-snapshot`, `usage-delete`, `usage-pause`, `usage-resume`, `usage-rename`, `usage-reset`, `usage-generate` and the `usage-generate-*` family (`value`, `value-save`, `value-delete`, `value-extra`, `coords`, `coords-legacy`, `wand`, `blocks`, `help-value`, `help-or-coords`, `help-or-wand`, `help-or-blocks`), `usage-vault-reset`, `usage-vault-unlockall`, `usage-key`, `usage-key-give`, `usage-key-check`, `usage-loot`, `usage-loot-set`, `usage-loot-clear`, `usage-loot-info`.

`error-chamber-creation-failed`, `error-world-not-loaded` (`{world}` `{name}`), `error-region-too-small` (`{minXZ}` `{minY}` `{dx}` `{dy}` `{dz}`), `error-region-too-large` (`{maxVolume}` `{volume}`), `error-invalid-type`, `error-invalid-type-loot-clear`, `error-player-not-found`, `player-not-found` (`{player}`), `error-ominous-key-unavailable`, `error-menu-failed` (`{error}`), `error-loot-set-failed`, `error-no-loot-tables`, `error-invalid-coordinates`.

`generate-rounding-note` / `generate-rounding-info` (`{requested}` `{actual}` `{overhead}` `{volume}`) explain block-count rounding for `/trial generate blocks`.

</details>

<details>

<summary><strong>Plugin info (`/trial info` with no chamber)</strong></summary>

All start with `plugin-info-`:

`plugin-info-header`, `plugin-info-version` (`{version}`), `plugin-info-authors` (`{authors}`), `plugin-info-database` (`{type}`), `plugin-info-chambers` (`{count}`), `plugin-info-platform` (`{platform}`), `plugin-info-integrations-header`, `plugin-info-integration-worldedit` / `-worldguard` / `-papi` / `-vault` (`{status}`), `plugin-info-config-header`, `plugin-info-config-loot-mode` (`{mode}`), `plugin-info-config-spawner-waves` / `-spectator` / `-statistics` (`{status}`).

</details>

<details>

<summary><strong>Admin GUI text and the setup tour</strong></summary>

Every item name, lore line and button label in the admin GUI lives under the nested `gui:` section (roughly 330 keys), and the `/trial setup` tour text lives under `setup:`. These are grouped one section per view. Because there are so many and they follow their own conventions, they are covered in the [Localization](localization.md) guide rather than listed here.

</details>

***

## Notes on coverage

- Keys under `gui:` and `setup:` are real and translatable but not enumerated on this page (see [Localization](localization.md)).
- `reload-success` still ships alongside the newer `config-reloaded`; both are harmless to keep.
- Leftover keys from removed features do no harm. The startup merge only adds keys, it never deletes yours.

***

## Applying changes

```
/trial reload
```

All messages update immediately, no restart needed.

## Related pages

{% content-ref url="loot.yml.md" %}
[loot.yml.md](loot.yml.md)
{% endcontent-ref %}

{% content-ref url="config.yml.md" %}
[config.yml.md](config.yml.md)
{% endcontent-ref %}

{% content-ref url="localization.md" %}
[localization.md](localization.md)
{% endcontent-ref %}

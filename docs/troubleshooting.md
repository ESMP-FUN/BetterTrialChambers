# Common Issues

Scan the headings, click the one that matches your symptom for the fix steps.

If nothing here matches, jump to [Reporting Bugs](troubleshooting.md#reporting-bugs) or ask in [Discord](https://dc.esmp.fun).

{% hint style="info" %}
**Looking for specific text?** Browser **Ctrl/⌘ + F won't find text inside collapsed sections** on this page. Use the documentation **search bar at the top of the page** (or press **Ctrl/⌘ + K**) instead.
{% endhint %}

***

<details>

<summary><strong>Vault cooldowns don't work, players open the same vault instantly</strong></summary>

1. Test with a non-OP account. Cooldowns then apply normally.
2. Or negate the permission on your own account:

   ```
   /lp user <yourname> permission set btc.bypass.cooldown false
   ```
3. Or deop yourself while testing: `/deop <yourname>`, test, then `/op <yourname>`.

To confirm this is the cause: set `debug.verbose-logging: true`, `/trial reload`, open a vault, and look for `[Vault API] Player X has btc.bypass.cooldown permission - SKIPPING cooldown check`.

Cause: OPs have every permission by default, including `btc.bypass.cooldown`, which intentionally skips the cooldown check.

</details>

<details>

<summary><strong>Vaults stopped working completely: they don't open, don't give loot, and don't even take the key</strong></summary>

1. Run:

   ```
   /trial vault unlockall all
   ```

   This clears the "already opened" record on every vault in every chamber.
2. Or wait for a chamber reset, which clears the record for that one chamber.

{% hint style="warning" %}
**If you wanted one reward per vault for the whole server**, use `loot-mode: SHARED`, not `VANILLA`. Plain Minecraft is still one open per player, never shared. See [loot-mode](configuration/config.yml.md).
{% endhint %}

{% hint style="info" %}
Before v1.7.3, opening vaults with `btc.bypass.cooldown` still wrote you into Minecraft's record, so admin testing could lock you out under `VANILLA` mode. Bypassing players now leave no trace.
{% endhint %}

Cause: you switched `vaults.loot-mode` to `VANILLA` (or, on pre-2.0.8 configs, set `vaults.per-player-loot` to `false`). Minecraft then reads the record BTC wrote and refuses to open for anyone on it.

</details>

<details>

<summary><strong>My spawner rest time (cooldown) setting seems to be ignored</strong></summary>

1. Delete the `target-cooldown-length` line from the preset in `spawner_presets.yml`. Spawners from it then follow `reset.spawner-cooldown-minutes` like any other.
2. Or set `reset.spawner-cooldown-overrides-presets: true` in `config.yml` to force every spawner, presets included, onto the server-wide setting.
3. Or edit the number in the preset if you want it to keep its own time.
4. `/trial reload`. Already-placed spawners keep what they were given; break and re-place them, or wait for a chamber reset.

{% hint style="info" %}
`36000` ticks is 30 minutes, which is also Minecraft's default, so a preset spawner and an ordinary one can behave identically and hide the problem.
{% endhint %}

Cause: a preset with a `target-cooldown-length` line uses that time and ignores the config value.

</details>

<details>

<summary><strong>"Loot table not found" / vault opens but no loot drops</strong></summary>

1. Open `plugins/BetterTrialChambers/loot.yml` in a real text editor (VS Code, Notepad++, Sublime).
2. Enable "Show whitespace" so you can see tab characters.
3. Replace every tab with 2 spaces (or use "Convert Indentation to Spaces").
4. `/trial reload`.
5. Open `/trial menu`, then Loot Tables, and confirm your tables are listed.

The same rule applies to `config.yml` and `messages.yml`.

Cause: a tab character anywhere in the YAML makes the whole file fail to parse silently.

</details>

<details>

<summary><strong>Vaults give plain vanilla loot and ignore my custom loot table</strong></summary>

1. Open `plugins/BetterTrialChambers/config.yml`.
2. Under `vaults:`, set `loot-mode: PER_PLAYER` (or `SHARED` for one reward per vault, server-wide).
3. `/trial reload`.
4. Check the value in-game with `/trial info`, on the **Vault Loot** line.

{% hint style="info" %}
If `config.yml` has no `loot-mode` line, the plugin falls back to the old `vaults.per-player-loot` switch, where `false` does the same thing. Adding a `loot-mode` line is clearer and gives you the shared option.
{% endhint %}

Cause: `vaults.loot-mode` is `VANILLA`, so BTC does not touch vaults and every custom loot table is ignored. Full detail: [config.yml loot-mode](configuration/config.yml.md#vault-settings).

</details>

<details>

<summary><strong>I picked a different loot table for a chamber, but my edits don't show up</strong></summary>

1. Update to 2.0.3 or newer. Nothing to reconfigure.
2. If you cannot update yet: your edits went into `chamber-<name>`, which still exists in `loot.yml`, so nothing was lost. Either clear the override with `/trial loot clear <chamber> all` to start using that table, or copy your edits into the table you pointed the chamber at.

**The two buttons do different things:**

* **Loot Table Overrides** picks _which_ table the chamber's vaults use.
* **Normal Loot** / **Ominous Loot** edit _the contents_ of whichever table is currently in use. Point a chamber at a shared table and editing it there changes it for every chamber using it. Leave the override on `(default)` for a private `chamber-<name>` table.

Cause: a bug in 2.0.2 and earlier where the Normal/Ominous Loot buttons ignored the override and always opened `chamber-<name>`. Fixed in 2.0.3.

</details>

<details>

<summary><strong>Do I need WorldEdit? Does this work without manually registering every chamber?</strong></summary>

1. Set in `config.yml`:

   ```yaml
   discovery:
     enabled: true
     auto-snapshot: true    # so resets can restore blocks
   ```
2. Restart once.
3. Walk or fly around your world. Chambers register themselves as their chunks load; pre-loaded chunks are picked up by a one-time startup sweep.

For manual control (custom names, only specific chambers), the WorldEdit workflow still works: `/trial generate wand MyChamber`. See [Manual Chamber Setup](getting-started/your-first-chamber.md).

Full details: [Auto-Discovery config](configuration/config.yml.md#auto-discovery-of-natural-trial-chambers).

</details>

<details>

<summary><strong>Chamber resets don't restore broken blocks</strong></summary>

**Manually-registered chambers:**

1. Run `/trial snapshot create <chamber>` while the chamber is intact.

**Auto-discovered chambers:**

1. Set `discovery.auto-snapshot: true` in `config.yml`, then `/trial reload`. New discoveries snapshot on registration.
2. For chambers already discovered without a snapshot, run `/trial snapshot create <chamber>` (use `/trial list` for the name, or stand inside and run it with no name).

{% hint style="info" %}
`discovery.auto-snapshot` needs **1.5.6+** to work. Older builds saved the file but never linked it, so resets still reported "No snapshot found".
{% endhint %}

Cause: resets need a snapshot taken while the chamber was intact to rebuild blocks.

</details>

<details>

<summary><strong>After a reset, my signs / banners / player heads / lecterns came back blank</strong></summary>

1. Update to 1.7.2 or newer.
2. Re-capture the snapshot while the chamber is in a known-good state:

   ```
   /trial snapshot create <chamber>
   ```

Cause: before 1.7.2 snapshots only captured spawners, vaults, decorated pots, and chests. Every other block with stored data was restored blank. A snapshot taken on an older build still has no decoration data.

</details>

<details>

<summary><strong>A reset deleted blocks around the chamber / the chamber came back broken</strong></summary>

1. Update to 1.5.6 or newer.
2. Run `/trial delete <chamber>`. This removes the broken registration and its stale snapshot.
3. With discovery enabled, the chamber re-registers cleanly next time its chunks load. Otherwise re-register with `/trial generate`.

Terrain an affected reset already deleted cannot be restored by the plugin (it was never in the snapshot). Restore that area from a world backup.

Cause: before 1.5.6, an auto-discovered chamber whose bounding box grew after its snapshot could have a reset wipe the grown bounds while only restoring the old, smaller region.

</details>

<details>

<summary><strong>The same chamber is registered twice (two names, two reset timers)</strong></summary>

1. Update to 2.0.9 or newer.
2. Run `/trial delete <name>` for every registration of that chamber.
3. Walk through it once with discovery enabled. It re-registers as one chamber with one timer.
4. If duplicates still appear, raise `discovery.structure-merge-distance-blocks` (16 by default) and try again.

Cause: some datapacks build one chamber from several adjacent structures. Before 2.0.9 each piece registered separately, and their resets overwrote each other.

</details>

<details>

<summary><strong>Memory keeps climbing as chambers are discovered</strong></summary>

1. Update to 2.0.9 or newer. The plugin now releases each chamber's map region shortly after registration completes.
2. If memory is still tight on a small server, leave headroom for the server software itself. On a 2 GB machine, `-Xmx1200M` is safer than `-Xmx1500M`.

Cause: before 2.0.9 the server kept each discovered chamber's region in memory when no player was nearby, so memory stepped up and never came back down (often crashing with `pthread_create failed` or out-of-memory).

</details>

<details>

<summary><strong>Boss bars don't go away when I leave a chamber</strong></summary>

1. Update to 1.2.26 or newer.
2. If still seeing it, check `spawner-waves.remove-distance` in `config.yml` (default 32). Do not set it below `detection-radius`, or the hysteresis breaks.

</details>

<details>

<summary><strong>Server lags when chambers reset or snapshot</strong></summary>

1. Tune `config.yml`:

   ```yaml
   global:
     blocks-per-tick: 500            # Lower to 100-200 on low-spec servers.

   performance:
     cache-duration-seconds: 300
   ```
2. Do not reset multiple large chambers at the same clock minute. Stagger the reset intervals (one at `172800`, another at `172900`).
3. Folia support is auto-detected; no flag needed.

{% hint style="info" %}
Since **2.0.9** snapshots stream to and from disk, so the memory a snapshot or reset needs no longer scales with chamber size. The time still does, so the tuning above still applies.
{% endhint %}

Cause: snapshot and restore work scales with block count. A 100x50x100 chamber is 500,000 blocks.

</details>

<details>

<summary><strong>Cooldowns work for some players but not others</strong></summary>

1. Check whether the affected player or group has `btc.bypass.cooldown` (often inherited from a permission pack or copied group):

   ```
   /lp user <player> permission check btc.bypass.cooldown
   ```
2. Check whether the player is in creative or spectator. Creative players bypass cooldowns regardless of permissions (vanilla vault behaviour).
3. If you recently cleared `player_vault_data` in the database but not the native `rewarded_players` on the vault block, run `/trial vault reset <chamber> <player>`, which clears both.

</details>

<details>

<summary><strong>A protection toggle isn't blocking anyone (entry / teleport / PvP / AdvancedEnchantments)</strong></summary>

1. **Test with a non-OP account.** OPs have every `btc.bypass.*` permission by default, including `btc.bypass.entry` and `btc.bypass.protection`. Or negate it:

   ```
   /lp user <yourname> permission set btc.bypass.entry false
   ```
2. Set `debug.verbose-logging: true`, `/trial reload`, and reproduce. Read the `[Protection]` lines:
   * `... allowed for Steve: has btc.bypass.entry` means a permission exemption.
   * `... allowed for Steve: SPECTATOR mode is exempt` means spectator/creative are always exempt.
   * `BLOCKED teleport into 'X' ...` means it is working.
   * **No `[Protection]` line at all** means the destination is not inside a registered chamber (wrong world, not registered, or bounds too small). Check `/trial list` / `/trial info <chamber>`.
3. Confirm the config key is nested under `protection:` (not a flat `protection.prevent-teleport-into-chamber:` line) and that you ran `/trial reload`.
4. For AdvancedEnchantments: check the startup log for `AdvancedEnchantments integration: ready`. `block-advanced-enchantments` covers effect-based enchants only; ordinary vein miners go through normal block protection. For mining a wall from outside, make sure `advanced-enchantments-block-radius` covers the blast size.

</details>

<details>

<summary><strong>MySQL connection errors on startup</strong></summary>

Match the error text:

| Error contains           | Meaning                    | Fix                                                     |
| ------------------------ | -------------------------- | ------------------------------------------------------- |
| `Access denied for user` | Wrong username or password | Verify `database.username` / `password` in config.yml   |
| `Unknown database`       | Database doesn't exist     | `CREATE DATABASE trialchamberpro;` on your MySQL server |
| `Connection refused`     | Host unreachable           | Check `database.host` and `port`; is MySQL running?     |
| `timeout after 30000ms`  | Connection pool exhausted  | Increase `database.pool-size` from 10 to 20             |

If you are not using MySQL but the plugin still tries to connect, set `database.type: SQLITE` (case-sensitive).

</details>

<details>

<summary><strong>Auto-discovery registered something that isn't a chamber</strong></summary>

1. Delete the false registration:

   ```
   /trial list                  # find the auto_world_X_Z name
   /trial delete <name>
   ```
2. Tighten the thresholds in `config.yml`:

   ```yaml
   discovery:
     min-vaults-plus-spawners: 3    # Up from 2
     max-center-y: 5                # Down from 10
   ```
3. Raise `discovery.cooldown-seconds` if the same false region keeps re-triggering.
4. On an old world with a high false-positive rate, consider `discovery.enabled: false` and manual registration.

Cause: player-built structures using tuff bricks or copper blocks can match the structural detector, mostly on pre-1.21 worlds.

</details>

<details>

<summary><strong>Custom items from another plugin don't drop</strong></summary>

1. Confirm the custom-item plugin is installed and loaded (`/plugins` shows it green).
2. Confirm the item ID matches that plugin's docs exactly, including case.
3. Confirm the `plugin:` field is one of `nexo`, `itemsadder`, `oraxen`, `craftengine` or `mythiccrucible` (case does not matter; anything else is named in the console).
4. Watch the console when a vault opens: a missing item id is reported by name.

Example that works:

```yaml
- type: CUSTOM_ITEM
  plugin: oraxen
  item-id: amethyst_blade
  weight: 5
```

Cause: the item is fetched from the other plugin when the vault is opened, so a wrong id means that entry is skipped and the rest of the loot still rolls. The console says which id could not be found.

</details>

<details>

<summary><strong>I changed messages.yml but nothing changed in-game</strong></summary>

1. Run `/trial reload` (or restart).
2. Confirm the key you edited is the one actually displayed. Search `messages.yml` for the exact text you see in-game.
3. Check for tab characters (see the "Loot table not found" section).
4. If it is a boss bar message, note those keys contain `boss-bar` and use MiniMessage tags, not `&` color codes.

Full reference: [messages.yml](configuration/messages.yml.md).

</details>

***

## Performance Tips

* **`blocks-per-tick`** is the single most important knob. Default 500 is conservative; lower on low-spec hardware, raise on servers with headroom.
* **`cache-duration-seconds: 300`** (5 min) suits most servers. Bump to 600+ with hundreds of chambers and rare edits.
* **MySQL** outperforms SQLite past roughly 50 concurrent players. Below that, SQLite is simpler and fast enough.
* **Snapshot files** live in `plugins/BetterTrialChambers/snapshots/`. Gzip-compressed, but a 500k-block chamber can still be 20+ MB. Monitor disk with many large chambers.
* **Skip discovery during world pregen.** Set `discovery.enabled: false`, run Chunky, then re-enable.

***

## Reporting Bugs

A good bug report includes:

1. **Plugin version** (`/trial info`).
2. **Server type and version** (Paper 1.21.4, Folia 26.1.2, etc.).
3. **Steps to reproduce:** exact commands, exact actions.
4. **What you expected** vs **what happened**.
5. **Log excerpt:** run with `debug.verbose-logging: true`, then paste the lines around the error.
6. **Your config.yml and loot.yml** if relevant (redact MySQL credentials first).

File at [GitHub Issues](https://github.com/ESMP-FUN/BetterTrialChambers/issues) or the `#support` channel on [Discord](https://dc.esmp.fun).

# Common Issues

Most problems fall into one of the buckets below. Scan headings first — if you recognise the symptom, click it to expand the fix.

If nothing here matches, jump to [Reporting Bugs](troubleshooting.md#reporting-bugs) or ask in [Discord](https://dc.esmp.fun).

{% hint style="info" %}
**Looking for specific text?** Browser **Ctrl/⌘ + F won't find text inside collapsed sections** on this page. Use the documentation **search bar at the top of the page** ↗ (or press **Ctrl/⌘ + K**) instead — it searches the full text of every section, expanded or not.
{% endhint %}

***

<details>

<summary><strong>Vault cooldowns don't work — players open the same vault instantly</strong></summary>

**The likely cause:** you're testing as an OP.

Operators have **every** permission by default, including `btc.bypass.cooldown`. That permission intentionally skips cooldown checks so staff can test chambers. It's working as designed — but it's confusing the first time you hit it.

**Fix one of three ways:**

1. Test with a non-OP account. Cooldowns will apply normally.
2.  Explicitly negate the permission on your OP user:

    ```
    /lp user <yourname> permission set btc.bypass.cooldown false
    ```
3. Temporarily deop yourself: `/deop <yourname>`, test, re-op with `/op <yourname>`.

**To confirm this is your issue:** set `debug.verbose-logging: true` in `config.yml`, `/trial reload`, then open a vault. If the log shows `[Vault API] Player X has btc.bypass.cooldown permission - SKIPPING cooldown check!` — that's it.

</details>

<details>

<summary><strong>Vaults stopped working completely — they don't open, don't give loot, and don't even take the key</strong></summary>

**The likely cause:** you switched `vaults.loot-mode` to `VANILLA` (or, on older setups, set `vaults.per-player-loot` to `false`).

Minecraft keeps its **own** record of who has opened each vault. While BetterTrialChambers is managing your vaults it writes into that same record. The moment you hand vaults back to Minecraft, it reads that record and refuses to open for anyone who was on it — silently, without consuming the key. Nothing is actually broken; the vaults just think everyone has already been.

**The fix:**

```
/trial vault unlockall all
```

That clears the record on every vault in every chamber and opens them all up again. A normal chamber reset does the same thing for that one chamber, so waiting for a reset also works.

{% hint style="warning" %}
**If what you actually wanted was one reward per vault for the whole server**, `VANILLA` is not the setting for it — plain Minecraft is still one open **per player**, never shared. Use `loot-mode: SHARED` instead. See [loot-mode](configuration/config.yml.md).
{% endhint %}

{% hint style="info" %}
**Admins used to trip this by accident.** Before v1.7.3, opening vaults with `btc.bypass.cooldown` (which every OP has) still wrote you into that record, so a few minutes of testing could quietly lock you out of those vaults under plain Minecraft. Bypassing players now leave no trace at all.
{% endhint %}

</details>

<details>

<summary><strong>My spawner rest time (cooldown) setting seems to be ignored</strong></summary>

**The likely cause:** the spawners came from a preset that sets its own rest time.

If a preset in `spawner_presets.yml` has a `target-cooldown-length` line, spawners placed from it use **that** time and ignore `reset.spawner-cooldown-minutes` in `config.yml`. The reasoning is that if you wrote a number into a preset, you meant it.

**Three ways to sort it:**

1. **Delete the `target-cooldown-length` line** from the preset. Spawners from it will then follow your server-wide setting like any other spawner. This is usually the tidiest answer.
2. **Set `reset.spawner-cooldown-overrides-presets: true`** in `config.yml` to force every spawner, presets included, onto the server-wide setting.
3. **Edit the number in the preset** if you want that preset to keep its own time.

Then `/trial reload`. Note that already-placed spawners keep what they were given — break and re-place them, or wait for the chamber to reset.

{% hint style="info" %}
**Easy to miss:** `36000` ticks is 30 minutes, which is also Minecraft's own default. So a preset spawner and an ordinary one can behave identically and hide the fact that your setting isn't reaching them.
{% endhint %}

</details>

<details>

<summary><strong>"Loot table not found" / vault opens but no loot drops</strong></summary>

**The likely cause:** TAB characters in your `loot.yml`.

YAML is strict about indentation. Mixing tabs and spaces — or using tabs at all — breaks the parser silently. The entire file fails to load, which is why `/trial loot set` reports "Available tables:" as empty and vaults generate nothing.

**Fix:**

1. Open `plugins/BetterTrialChambers/loot.yml` in a real text editor (VS Code, Notepad++, Sublime).
2. Enable "Show whitespace" / "View invisible characters" so you can see tabs.
3. Replace every tab with 2 spaces (or use your editor's "Convert Indentation to Spaces" command).
4. `/trial reload`.
5. `/trial menu` → Loot Tables — your tables should be listed.

Same rule applies to `config.yml` and `messages.yml`. If any of the three go quiet after an edit, tabs are the first suspect.

</details>

<details>

<summary><strong>Vaults give plain vanilla loot and ignore my custom loot table</strong></summary>

**The symptom:** loot tables load fine (no console errors, vaults register correctly), but opening a vault gives vanilla Minecraft items — crossbows, poison arrows, wind charges — instead of your custom loot.

**The likely cause:** `vaults.loot-mode` is set to `VANILLA` in `config.yml`.

That setting decides who gets the loot from a vault. On `VANILLA`, BetterTrialChambers doesn't touch vaults at all — they open with pure vanilla loot, and every custom loot table is ignored.

**Fix:**

1. Open `plugins/BetterTrialChambers/config.yml`.
2. Under `vaults:`, set `loot-mode: PER_PLAYER` (or `SHARED` if you want one reward per vault for the whole server).
3. `/trial reload`.

You can check the current value in-game with `/trial info` — look at the **Vault Loot** line. Full explanation: [config.yml → loot-mode](configuration/config.yml.md#vault-settings).

{% hint style="info" %}
**On an older config?** If your `config.yml` has no `loot-mode` line, the plugin falls back to the old `vaults.per-player-loot` switch, where `false` does the same thing. Setting it to `true` works, but adding a `loot-mode` line is clearer and gives you the shared option too.
{% endhint %}

</details>

<details>

<summary><strong>I picked a different loot table for a chamber, but my edits don't show up</strong></summary>

**The symptom:** you set a chamber's loot table to another table, but the chamber's **Normal Loot** button keeps showing `chamber-<name>`, and anything you edit through it has no effect in-game. It looks like the plugin has glued a loot table to the chamber based on its name and won't let you switch.

**The cause:** a bug in 2.0.2 and earlier — **fixed in 2.0.3**. The Normal/Ominous Loot buttons ignored the override and always opened the chamber's own `chamber-<name>` table, so your edits were saved into a table the vaults weren't reading.

**Fix:** update to 2.0.3 or newer. Nothing to reconfigure — the buttons now follow the override, and the table shown on the button is the one the vaults actually hand out.

**While you're here — the two buttons do different things:**

* **Loot Table Overrides** picks _which_ table the chamber's vaults use.
* **Normal Loot** / **Ominous Loot** edit _the contents_ of whichever table it's currently using.

So if you point a chamber at a shared table, editing it from that chamber changes it for **every** chamber using it — the button now warns you when that's the case. Want a chamber to have loot nobody else shares? Leave its override on `(default)`; it then gets its own private `chamber-<name>` table.

If you're on 2.0.2 or older and can't update yet, edits made through the button went into `chamber-<name>` — that table still exists in `loot.yml`, so nothing was lost. Either clear the override (`/trial loot clear <chamber> all`) to start using it, or copy what you wrote into the table you actually pointed the chamber at.

</details>

<details>

<summary><strong>Do I need WorldEdit? Does this work without manually registering every chamber?</strong></summary>

**No, you don't need WorldEdit — and yes, it works automatically.**

Enable auto-discovery and the plugin finds every natural chamber by itself:

```yaml
discovery:
  enabled: true
  auto-snapshot: true    # so resets can restore blocks
```

Restart once. Walk or fly around your world — chambers register themselves as their chunks load. Pre-loaded chunks (spawn region on server start, worlds that were already loaded) are picked up by a one-time startup sweep.

Full details: [Auto-Discovery config →](configuration/config.yml.md#auto-discovery-of-natural-trial-chambers)

If you prefer manual control (e.g. you want custom names or only specific chambers to use the plugin), the classic WorldEdit workflow still works: `/trial generate wand MyChamber`. See [Manual Chamber Setup](getting-started/your-first-chamber.md).

</details>

<details>

<summary><strong>Chamber resets don't restore broken blocks</strong></summary>

**The likely cause:** the chamber has no snapshot.

Resets clear entities, restart spawners, and clear vault cooldowns — but to rebuild blocks they need a **snapshot** taken while the chamber was intact.

**Fix for manually-registered chambers:**

```
/trial snapshot create <chamber>
```

Take this **before** players start breaking things. You can also take a fresh snapshot any time the chamber is in a known-good state and use it as the new baseline.

**Fix for auto-discovered chambers:**

Set `discovery.auto-snapshot: true` in `config.yml` and `/trial reload`. New chambers discovered from that point forward will snapshot on registration. For chambers already auto-discovered without a snapshot, create one manually with `/trial snapshot create <chamber>` (use `/trial list` to find the auto-generated name, or just stand inside it and run `/trial snapshot create` with no name).

**Note:** `discovery.auto-snapshot` requires **1.5.6+** to actually work — older builds saved the snapshot file but never linked it to the chamber, so resets still reported "No snapshot found" even with the option enabled.

</details>

<details>

<summary><strong>After a reset, my signs / banners / player heads / lecterns came back blank</strong></summary>

**Fixed in 1.7.2 — update, then re-snapshot.** Older snapshots only captured spawners, vaults, decorated pots, and chests; every other block with stored data — sign text, player-head skins, banner patterns, lectern books, jukebox discs, chiseled-bookshelf contents, and suspicious-block items — was restored as a blank block on reset. Since 1.7.2 all of these are captured and restored faithfully (in chamber snapshots and in captured/imported dungeon rooms).

**A snapshot taken on an older build still has no decoration data** — re-capture it once on 1.7.2+ while the chamber is in a known-good state:

```
/trial snapshot create <chamber>
```

</details>

<details>

<summary><strong>A reset deleted blocks around the chamber / the chamber came back broken</strong></summary>

**Fixed in 1.5.6 — update first.** On older versions, an auto-discovered chamber whose bounding box _grew_ after its snapshot was taken (discovery merges adjacent regions as their chunks load) could have its reset wipe everything inside the grown bounds while only restoring the old, smaller region. Since 1.5.6 the reset can never clear ground its snapshot doesn't cover, and merges automatically re-capture the snapshot.

**If one of your chambers was already affected:**

```
/trial delete <chamber>
```

That single command is the complete plugin-side fix — it removes the broken registration **and** deletes its stale snapshot. If discovery is enabled, the chamber re-registers cleanly the next time its chunks load (with a fresh snapshot if `discovery.auto-snapshot: true`); otherwise re-register it manually with `/trial generate`.

Terrain that an affected reset already deleted **cannot be restored by the plugin** — the snapshot never contained those blocks. Restore that area from a world backup, or let it regenerate if it was untouched wilderness.

</details>

<details>

<summary><strong>Boss bars don't go away when I leave a chamber</strong></summary>

**Fixed in 1.2.26.** Update to the latest version.

If you're already on 1.2.26+ and still seeing this, check `spawner-waves.remove-distance` in `config.yml` — default is 32. Players outside this range get removed from the bar. If you've lowered it below `detection-radius`, the hysteresis breaks.

</details>

<details>

<summary><strong>Server lags when chambers reset or snapshot</strong></summary>

Snapshot and restore operations scale with chamber size. A 100×50×100 chamber is 500,000 blocks — even streaming to disk, that's work.

**Tune these in `config.yml`:**

```yaml
global:
  blocks-per-tick: 500            # Lower this to 100-200 on low-spec servers.

performance:
  async-database-operations: true
  cache-duration-seconds: 300
```

Also: don't reset multiple large chambers at the same clock minute. Stagger their reset intervals (one at `172800`, another at `172900`, etc.) so they don't overlap.

**If you're on Folia,** confirm `performance.use-folialib: true`. The plugin detects Folia automatically, but this flag is required.

</details>

<details>

<summary><strong>Cooldowns work for some players but not others</strong></summary>

Usually a permission inheritance problem. Check:

1.  **Does the affected player / group have `btc.bypass.cooldown`?** Often picked up via a default permission pack or a copy-pasted permission group.

    ```
    /lp user <player> permission check btc.bypass.cooldown
    ```
2. **Is the player in creative or spectator?** Creative players bypass cooldowns regardless of permissions (vanilla vault behaviour).
3. **Did you recently clear vault data in the database?** If you wiped `player_vault_data` but not the native `rewarded_players` on the vault block (v1.2.21+ stores both), the native block state still remembers them. Use `/trial vault reset <chamber> <player>` — it clears both.

</details>

<details>

<summary><strong>A protection toggle isn't blocking anyone (entry / teleport / PvP / AdvancedEnchantments)</strong></summary>

You enabled `prevent-teleport-into-chamber`, `prevent-entry-without-permission`, `allow-pvp: false`, or `block-advanced-enchantments`, but players (or you) still get through. Work down this list:

1.  **Are you testing as an OP?** This is the #1 cause. OPs have **every** `btc.bypass.*` permission by default — including `btc.bypass.entry` and `btc.bypass.protection` — so you exempt yourself without realising. **Test with a non-OP account**, or negate the permission:

    ```
    /lp user <yourname> permission set btc.bypass.entry false
    ```
2. **Turn on `debug.verbose-logging: true`** and `/trial reload`, then reproduce. The console tells you exactly what happened, e.g.:
   * `[Protection] teleport into 'X' allowed for Steve: has btc.bypass.entry (note: OPs have this by default)` → permission exemption (see #1).
   * `[Protection] teleport into 'X' allowed for Steve: SPECTATOR mode is exempt` → spectators/creative are always exempt.
   * `[Protection] BLOCKED teleport into 'X' for Steve (cause COMMAND)` → it **is** working.
   * **No `[Protection]` line at all** when teleporting in → the destination isn't inside a _registered_ chamber (wrong world, chamber not registered, or bounds don't reach where you landed). Check `/trial list` / `/trial info <chamber>`.
3. **Did the config actually apply?** Confirm the key is nested under `protection:` (not pasted as a flat `protection.prevent-teleport-into-chamber:` line) and that you ran `/trial reload` after editing.
4. **AdvancedEnchantments specifically:** the `[AE]` debug lines tell you if the enchant was allowlisted, bypassed, or blocked. If you see **no `[AE]` lines at all** when an enchant procs, check the startup log for `AdvancedEnchantments integration: ready` — if it's missing, AE isn't being detected. Also remember `block-advanced-enchantments` is for effect-based enchants; ordinary vein miners are handled by normal block protection instead. For mining a wall from _outside_, make sure `advanced-enchantments-block-radius` covers your blast size.

</details>

<details>

<summary><strong>MySQL connection errors on startup</strong></summary>

Usually a credentials or host issue. Full error text tells you which:

| Error contains           | Meaning                    | Fix                                                     |
| ------------------------ | -------------------------- | ------------------------------------------------------- |
| `Access denied for user` | Wrong username or password | Verify `database.username` / `password` in config.yml   |
| `Unknown database`       | Database doesn't exist     | `CREATE DATABASE trialchamberpro;` on your MySQL server |
| `Connection refused`     | Host unreachable           | Check `database.host` and `port`; is MySQL running?     |
| `timeout after 30000ms`  | Connection pool exhausted  | Increase `database.pool-size` from 10 to 20             |

If you're not actually using MySQL and the plugin is still trying to connect to it, check `database.type: SQLITE` (case-sensitive).

</details>

<details>

<summary><strong>Auto-discovery registered something that isn't a chamber</strong></summary>

On worlds that existed before 1.21, players sometimes build structures out of tuff bricks or copper blocks. The auto-detector's structural predicate can match these.

**Short-term fix:** delete the false registration.

```
/trial list                  # find the auto_world_X_Z name
/trial delete <name>
```

**Long-term fix:** tighten the detection thresholds in `config.yml`:

```yaml
discovery:
  min-vaults-plus-spawners: 3    # Up from 2 — chambers almost always have ≥3
  max-center-y: 5                # Down from 10 — natural chambers gen quite deep
```

Then bump `discovery.cooldown-seconds` higher if the same false region keeps re-triggering.

If you're on an old world and the false-positive rate is high, it may be easier to leave `discovery.enabled: false` and register chambers manually — the trade-off is up to you.

</details>

<details>

<summary><strong>Nexo / ItemsAdder / Oraxen items don't drop</strong></summary>

The `CUSTOM_ITEM` loot type uses reflection, so it's safe if the custom-item plugin isn't installed — but that also means the item silently skips if anything's off. Check:

1. **The plugin is installed and loaded.** `/plugins` should show it green.
2. **The item ID is correct and lowercase where required.** Each plugin has its own case rules — match exactly how their docs write it.
3. **The `plugin:` field is spelled correctly:** `nexo`, `itemsadder`, or `oraxen`. Case-insensitive, but typos are silent failures.
4. **`debug.verbose-logging: true`** will log the attempted resolution — watch the console when a vault opens.

Example that works:

```yaml
- type: CUSTOM_ITEM
  plugin: oraxen
  item-id: amethyst_blade
  weight: 5
```

</details>

<details>

<summary><strong>I changed messages.yml but nothing changed in-game</strong></summary>

1. Did you run `/trial reload`? Config and message edits require a reload (or a restart).
2. Is the key you edited the one actually being displayed? Some messages look similar. Search `messages.yml` for the exact text you see in-game.
3. TAB characters? (See the **"Loot table not found"** section above — same rule.)
4. Are you sure it's not a boss bar? Boss bar messages are in `messages.yml` under keys containing `boss-bar` — they use MiniMessage tags, different from regular color codes.

Full messages reference: [messages.yml →](configuration/messages.yml.md)

</details>

***

## Performance Tips

General-purpose tuning guidance.

* **`blocks-per-tick`** is the single most important knob. Lower on low-spec hardware, raise on beefy servers with headroom. Default 500 is conservative.
* **Cache durations** — `cache-duration-seconds: 300` (5 min) is fine for most servers. Bump to 600+ if you have hundreds of registered chambers and rare modifications.
* **MySQL** outperforms SQLite past \~50 concurrent players. Below that, SQLite is simpler and plenty fast.
* **Snapshot files** live in `plugins/BetterTrialChambers/snapshots/`. They're gzip-compressed but a 500k-block chamber can still be 20+ MB. Monitor disk if you have many large chambers.
* **Skip discovery on world pregen.** If you're running Chunky to pre-generate your world, temporarily set `discovery.enabled: false`, run the pregen, then re-enable. Discovery + chunk-load storm adds up.

***

## Reporting Bugs

If you've hit something not covered here, a good bug report includes:

1. **Plugin version** (`/trial info` shows it).
2. **Server type and version** (Paper 1.21.4, Folia 26.1.2, etc.).
3. **Steps to reproduce** — exact commands, exact actions.
4. **What you expected** vs **what actually happened**.
5. **Relevant log excerpt** — run with `debug.verbose-logging: true` to get detailed output, then paste the lines around the error.
6. **Your config.yml and loot.yml** (redact MySQL credentials first!) if they're relevant.

File at [GitHub Issues](https://github.com/ESMP-FUN/BetterTrialChambers/issues) or post in the `#support` channel on [Discord](https://dc.esmp.fun).

"Can't reproduce" is a real answer — sometimes a bug depends on state that's hard to observe. If we ask for more detail, it's because we're trying to track it down, not brushing it off.

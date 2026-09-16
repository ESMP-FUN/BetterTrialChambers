# Localization

Translate or reword every user-visible string in BetterTrialChambers, including the whole admin GUI, by editing one file.

{% hint style="info" %}
Edit `messages.yml`, run `/trial reload`, done. The plugin never overwrites this file once it exists, so your changes survive plugin updates.
{% endhint %}

## Edit and reload

1. Open `plugins/BetterTrialChambers/messages.yml`. It is created from the bundled defaults on first run and never touched again.
2. Change the values you want. Keep the keys and the YAML indentation intact. Use spaces, never tabs.
3. Run `/trial reload`. This rereads `config.yml`, `loot.yml`, and `messages.yml` together. No restart needed.

## File layout

```yaml
prefix: "&8[&6BTC&8]&r "

# Per-feature keys (chat output, command help, and so on)
chamber-not-found: "&cChamber '{chamber}' not found."
no-permission: "&cYou don't have permission to do that."

# Every admin GUI string lives under one nested gui.* tree
gui:
  common:
    back-button: "&e< Back to {destination}"
    close-button: "&cClose"

  main-menu:
    title: "BetterTrialChambers Admin"
    header-name: "&6&lBetterTrialChambers"
    header-lore:
      - "&7Version &f{version}"
      - ""
      - "&eRegistered Chambers: &f{chambers}"
      - "&eLoot Tables: &f{tables}"
```

## Formatting text

You can use either style, or mix both in the same line:

* **Legacy `&` codes:** `&aHello &lworld&r!`. `&0`-`&9` and `&a`-`&f` are colours; `&l` bold, `&n` underline, `&o` italic, `&m` strikethrough, `&k` obfuscated, `&r` resets.
* **MiniMessage tags:** `<green>Hello <bold>world</bold>!</green>`, plus `<gradient:...>`, `<click:...>`, and `<hover:...>`.

Hex colours work in both styles: `&#FF5500` or `<#FF5500>`.

If a MiniMessage tag is written wrong, that one line shows as plain text so you can spot and fix it.

{% hint style="info" %}
GUI item names show non-italic even though Minecraft normally italicizes renamed items. This is on purpose. To force italic, start the value with `&o`.
{% endhint %}

## Placeholders

Messages can contain `{placeholder}` tokens that the plugin fills in when the message is sent:

```yaml
chamber-created: "&aChamber &f{chamber}&a created with volume {volume}."
```

The placeholders each key accepts are listed next to that key in the bundled `messages.yml`. An unknown placeholder is left as literal `{token}` text rather than causing an error, which helps you spot typos.

### Translatable `{type}` labels

The words plugged into `{type}` are looked up from `messages.yml` too, so "Normal" / "Ominous" and similar translate everywhere they appear. Translate each key once and every `{type}` that uses it follows.

| Key                                               | Default                           | Used in                                                          |
| ------------------------------------------------- | --------------------------------- | -------------------------------------------------------------- |
| `vault-type-normal` / `vault-type-ominous`         | `Normal` / `Ominous`              | vault messages (open / locked / reopen) and `/trial key give`  |
| `wave-type-normal` / `wave-type-ominous`           | `Trial` / `Ominous`               | the "{type} Spawner wave complete!" message                    |
| `wave-boss-type-normal` / `wave-boss-type-ominous` | `Trial Spawner` / `Ominous Trial` | the spawner-wave boss-bar progress line                        |

## Multi-line lore

GUI lore, and any other list value, is a YAML list. Each entry is one line. A blank entry (`""`) renders as a separator. Placeholders work in every line.

```yaml
gui:
  main-menu:
    header-lore:
      - "&7Version &f{version}"
      - ""
      - "&eRegistered Chambers: &f{chambers}"
```

## The `gui.*` section

All GUI strings sit under one nested `gui:` tree, one sub-section per view.

| Section                                                                               | Owns                                                              |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `gui.common`                                                                          | Back / close / prev / next buttons, destination labels, toggle templates |
| `gui.main-menu`                                                                       | Main hub                                                          |
| `gui.chamber-list` / `gui.chamber-detail` / `gui.chamber-settings`                    | Chamber browse and manage                                        |
| `gui.vault-management`                                                                | Vault cooldown management                                        |
| `gui.custom-mob`                                                                      | Per-chamber mob provider config                                  |
| `gui.loot-table-list` / `gui.pool-selector` / `gui.loot-editor` / `gui.amount-editor` | Loot editing flow                                                |
| `gui.stats-menu` / `gui.leaderboard` / `gui.player-stats`                             | Statistics views                                                 |
| `gui.global-settings` / `gui.protection-menu`                                         | Toggle screens                                                   |
| `gui.help-menu`                                                                       | In-GUI command reference                                         |

Key suffixes within a section:

* `<thing>-name`: item display name (single string)
* `<thing>-lore`: item lore (list of strings)
* `<thing>-lore-<state>`: state-dependent lore (e.g. `protection-lore-enabled` / `protection-lore-disabled`)
* `empty-name` / `empty-lore`: shown when a list or grid view has no entries
* `title`: the chest window title

Keys under `gui.*` never get the chat prefix. Neither do keys whose name contains `header`, `list-item`, `help-`, or `boss-bar`.

## Shared toggle templates

On/off toggle screens all reuse the same templates so you translate "Enabled / Disabled / Click to toggle" once:

```yaml
gui:
  common:
    toggle-name-enabled: "&a&l{label}"
    toggle-name-disabled: "&c&l{label}"
    toggle-lore-enabled:
      - "&7{description}"
      - ""
      - "&aStatus: Enabled"
      - ""
      - "&eClick to toggle"
    toggle-lore-disabled:
      - "&7{description}"
      - ""
      - "&cStatus: Disabled"
      - ""
      - "&eClick to toggle"
```

Each toggle then supplies only its own label and description:

```yaml
gui:
  protection-menu:
    block-break-label: "Prevent Block Break"
    block-break-desc: "Stop players from breaking blocks"
```

## What is translatable

Everything a player or admin sees:

* Chat messages, command output, and error messages, including every admin command, the reset-confirmation queue, snapshot listings, `/trial list`, the loot audit, and the shared Prev/Next line
* Time durations ("2d 3h 5m", "5m ago") via the `time-*` keys
* Boss bar text on trial spawner waves
* Every name, lore line, and button label in the admin GUI
* `/trial help` and `/trial info`

Server console log lines stay in English on purpose, since they are read by admins and pasted into bug reports.

## Restore the defaults

There is no reset command. Rename or delete `plugins/BetterTrialChambers/messages.yml` and run `/trial reload`. A fresh copy is written from the JAR.

If you have translations to keep, rename the file to `messages.yml.bak` first, let the plugin regenerate a fresh one, then copy your lines back in.

## Report a translation problem

If a placeholder is missing, a key is absent from the bundled defaults, or a string cannot be translated cleanly because of fixed word order, [open an issue](https://github.com/ESMP-FUN/BetterTrialChambers/issues). Contributions are welcome.

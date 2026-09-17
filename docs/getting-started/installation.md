# Installation

Install BetterTrialChambers on your server.

## Before you start

You need:

* **Paper**, or a fork (Purpur, Pufferfish, Folia). Pick the download that matches your Minecraft version: the plain jar for 1.21.x, `-mc26` for 26.0 to 26.2, and `-mc263` for 26.3 (both 26.x downloads need Java 25).
* **Java 21** or newer.
* **WorldEdit** (optional, only needed for the manual chamber setup path).

{% hint style="info" %}
Spigot and CraftBukkit will not work. BetterTrialChambers uses Paper-only features.
{% endhint %}

## 1. Download the plugin

Get the latest `BetterTrialChambers-<version>.jar` from one of these:

* [Modrinth](https://modrinth.com/plugin/trialchamberpro) (recommended)
* [GitHub Releases](https://github.com/ESMP-FUN/BetterTrialChambers/releases)

## 2. Install

1. Stop your server with `/stop`.
2. Move `BetterTrialChambers-<version>.jar` into your server's `plugins/` folder.

```
your-server/
├── plugins/
│   ├── BetterTrialChambers-<version>.jar  <-- put this one here
│   ├── WorldEdit.jar
│   └── ... other plugins
└── ...
```

3. Start your server.
4. Watch the console for these lines:

```
[BetterTrialChambers] Enabling BetterTrialChambers v<version>
[BetterTrialChambers] Database connected successfully
[BetterTrialChambers] Loaded 2 loot tables
[BetterTrialChambers] BetterTrialChambers enabled successfully!
```

{% hint style="success" %}
Seeing errors instead? Check the [Troubleshooting](../troubleshooting.md) page.
{% endhint %}

The plugin creates these files in `plugins/BetterTrialChambers/`:

```
plugins/BetterTrialChambers/
├── config.yml      # Main settings
├── loot.yml        # Loot tables
├── messages.yml    # Editable messages
├── data.db         # Plugin data (SQLite)
└── snapshots/      # Chamber backups (created later)
```

Leave them alone for now. The next page sets everything up.

## 3. Verify

Run this in-game:

```
/trial help
```

If you see a list of commands, the plugin is installed.

{% hint style="success" %}
**Next: [Quick Start](quick-start.md).** Run `/trial setup`, turn on auto-discovery and snapshots, and BTC finds and manages every natural Trial Chamber on your server by itself. For most servers that is the whole setup.
{% endhint %}

## Optional dependencies

BetterTrialChambers runs fine on its own. These plugins add extra features if you already use them.

| Plugin | What it adds | Get it |
| --- | --- | --- |
| WorldEdit / FAWE | Select a chamber with a wand instead of typing coordinates. Needed for the manual setup path. | [WorldEdit](https://dev.bukkit.org/projects/worldedit) |
| Vault | Pay players money as vault loot. Works with any economy plugin. | [Vault](https://www.spigotmc.org/resources/vault.34315/) |
| PlaceholderAPI | Show player stats and leaderboards in scoreboards, chat, and other plugins. | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |
| WorldGuard | Use your existing WorldGuard regions for chamber protection. | [WorldGuard](https://dev.bukkit.org/projects/worldguard) |
| Residence / Lands / GriefPrevention | Block players from claiming a registered chamber. On by default per plugin; scan for existing overlaps with `/trial claims scan`. | [Residence](https://www.spigotmc.org/resources/11480/), [Lands](https://www.spigotmc.org/resources/53313/), [GriefPrevention](https://github.com/GriefPrevention/GriefPrevention) |
| AdvancedEnchantments | Stop AE mining enchants (Blast Mining, etc.) breaking blocks inside chambers. Off by default; set `protection.block-advanced-enchantments: true`. | [AdvancedEnchantments](https://www.spigotmc.org/resources/76519/) |

AE mining enchants break blocks through their own path that skips the normal block-break check, so without this toggle they can dig into a chamber. Ordinary vein miners (VeinMiner and similar) fire a normal break per block, so standard chamber protection already stops them. See the [protection config](../configuration/config.yml.md#block-advanced-enchantments).

## Updating

1. Stop your server.
2. Back up your `plugins/BetterTrialChambers/` folder.
3. Replace the old JAR with the new one.
4. Start your server.
5. Check the console for "Successfully updated database to version X".

{% hint style="warning" %}
Always back up `plugins/BetterTrialChambers/` before updating. It holds your player data.
{% endhint %}

## What's next

{% content-ref url="quick-start.md" %}
[quick-start.md](quick-start.md)
{% endcontent-ref %}

***

## Good to know

{% hint style="info" %}
**Folia:** BetterTrialChambers works on Folia with no configuration. Folia is detected automatically.
{% endhint %}

{% hint style="info" %}
**MySQL:** The plugin uses SQLite by default, which needs nothing installed. If you run several servers that should share one set of chambers and stats, switch to MySQL in `config.yml`.
{% endhint %}

{% hint style="warning" %}
**Java:** Java 21 is the minimum. On Java 17 or older the plugin will not load.
{% endhint %}

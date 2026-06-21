# Installation

Getting TrialChamberPro up and running is easier than finding diamonds at Y=11. Let's do this!

## Prerequisites

Before you start, make sure you have:

* **Paper 1.21.x** (or forks like Purpur, Pufferfish, Folia)
* **Java 21** or newer
* **WorldEdit** (optional but highly recommended for chamber creation)

<div data-gb-custom-block data-tag="hint" data-style="info">

**Why Paper?** TrialChamberPro uses Paper-specific APIs for better performance and features. Spigot and CraftBukkit won't work!

</div>

## Download

Grab the latest version from one of these sources:

* [GitHub Releases](https://github.com/darkstarworks/TrialChamberPro/releases) (recommended)
* [Modrinth](https://modrinth.com/plugin/trialchamberpro)
* [SpigotMC](https://www.spigotmc.org/resources/trialchamberpro)

Look for the file named something like `TrialChamberPro-<version>.jar`.

## Installation Steps

### 1. Stop Your Server

Yeah, yeah, you know the drill. Shut it down properly with `/stop`.

### 2. Drop the JAR

Move `TrialChamberPro-<version>.jar` into your server's `plugins/` folder.

```
your-server/
├── plugins/
│   ├── TrialChamberPro-<version>.jar  ← Put it here!
│   ├── WorldEdit.jar
│   └── ... other plugins
└── ...
```

### 3. Start Your Server

Fire it back up! Watch the console for this beautiful message:

```
[TrialChamberPro] Enabling TrialChamberPro v<version>
[TrialChamberPro] Database connected successfully
[TrialChamberPro] Loaded 2 loot tables
[TrialChamberPro] TrialChamberPro enabled successfully!
```

<div data-gb-custom-block data-tag="hint" data-style="success">

**Seeing errors?** Check the [Troubleshooting](../troubleshooting.md) page!

</div>

### 4. Check the Config Files

The plugin creates several files in `plugins/TrialChamberPro/`:

```
plugins/TrialChamberPro/
├── config.yml      # Main configuration
├── loot.yml        # Loot table definitions
├── messages.yml    # Customizable messages
├── data.db         # SQLite database
└── snapshots/      # Block snapshots (created later)
```

Don't touch anything yet! We'll configure everything in the next section.

## Optional Dependencies

TrialChamberPro works great on its own, but these plugins add extra functionality:

### WorldEdit / FAWE

**Purpose:** Makes chamber creation 10x easier
**Required?** No, but seriously get it
**Download:** [WorldEdit](https://dev.bukkit.org/projects/worldedit)

With WorldEdit, you can select chamber boundaries with your wand instead of typing coordinates. Life-changing stuff.

### Vault

**Purpose:** Economy integration for loot rewards
**Required?** Only if you want to give coins/money as loot
**Download:** [Vault](https://www.spigotmc.org/resources/vault.34315/)

Lets you give players money when they open vaults. Works with every economy plugin ever made.

### PlaceholderAPI

**Purpose:** Show stats in chat, scoreboards, etc.
**Required?** Only if you want player stat placeholders
**Download:** [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

Display things like "Chambers Completed: 5" in your custom UIs.

### WorldGuard

**Purpose:** Enhanced region protection
**Required?** No, built-in protection works fine
**Download:** [WorldGuard](https://dev.bukkit.org/projects/worldguard)

If you already use WorldGuard, TrialChamberPro can integrate with it for protection.

### Residence / Lands / GriefPrevention

**Purpose:** Stop players claiming registered Trial Chambers *(1.5.15+)*
**Required?** No — only matters if you run one of these land-claim plugins
**Download:** [Residence](https://www.spigotmc.org/resources/11480/) · [Lands](https://www.spigotmc.org/resources/53313/) · [GriefPrevention](https://github.com/GriefPrevention/GriefPrevention)

If any of these is installed, TrialChamberPro blocks claim creation/expansion that overlaps a chamber and can scan for pre-existing conflicts (`/tcp claims scan`). Enabled by default per plugin; see [protection config](../configuration/config.yml.md) and the `tcp.bypass.*` [permissions](../reference/permissions.md).

### AdvancedEnchantments

**Purpose:** Stop AE enchants (e.g. Blast Mining) breaking blocks inside chambers *(1.5.18+)*
**Required?** No — only matters if you run AdvancedEnchantments
**Download:** [AdvancedEnchantments](https://www.spigotmc.org/resources/76519/)

AE custom enchants break blocks through their own effect path that ignores TCP's block-break cancel, so they can bypass chamber protection. Set `protection.block-advanced-enchantments: true` to make TCP cancel AE enchant activations for players inside a registered chamber (with an allowlist for enchants you want to keep working). **Off by default.** See [protection config](../configuration/config.yml.md#block-advanced-enchantments).

## Verify Installation

Run this command in-game:

```
/tcp help
```

You should see a list of commands. If you do, congrats! You're ready to create your first chamber.

## Updating

Updating is just as easy:

1. Stop your server
2. Replace the old JAR with the new one
3. Start your server
4. Check console for "Successfully updated database to version X"

<div data-gb-custom-block data-tag="hint" data-style="warning">

**Always backup first!** Before updating, copy your `plugins/TrialChamberPro/` folder somewhere safe. Better safe than sorry when dealing with player data.

</div>

## What's Next?

Now that you're installed, let's create your first managed chamber!

<div data-gb-custom-block data-tag="content-ref" data-url="your-first-chamber.md">

[your-first-chamber.md](your-first-chamber.md)

</div>

---

## Quick Tips

<div data-gb-custom-block data-tag="hint" data-style="info">

**Folia Support:** TrialChamberPro works on Folia out of the box! Just make sure `use-folialib: true` in config.yml.

</div>

<div data-gb-custom-block data-tag="hint" data-style="info">

**MySQL Support:** By default, the plugin uses SQLite (easy mode). For networks with multiple servers, you can switch to MySQL in config.yml.

</div>

<div data-gb-custom-block data-tag="hint" data-style="warning">

**Java Version:** Java 21 is required. If you're on Java 17 or older, the plugin won't even load. Time to upgrade!

</div>

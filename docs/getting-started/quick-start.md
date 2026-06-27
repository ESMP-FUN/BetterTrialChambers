# 🚀 Quick Start

The fastest way to get TrialChamberPro running — and for **most servers, the only setup you need.**

TrialChamberPro can find and manage every natural Trial Chamber on your server **automatically**. No WorldEdit, no commands per chamber, nothing to repeat for each one. Run one guided tour, flip two switches, and you're done.

## 1. Run the setup tour

Hop in-game as an operator and run:

```
/tcp setup
```

This opens a friendly, **opt-in** walkthrough of TCP's main settings — one at a time, in plain English, with **Enable / Skip / Disable** for each. Nothing is forced, and you can pause or stop at any point. On Paper 1.21.7+ it's a clean dialog UI; on older servers it's a clickable-chat version with the same content.

<!-- SCREENSHOT: the /tcp setup welcome + first step (Auto-discover Trial Chambers) -->

## 2. Turn on the two that matter most

As the tour walks you through the settings, **Enable** these two — they're what make everything hands-off:

- **Auto-discover Trial Chambers** — TCP finds naturally-generated chambers by itself as players explore, and registers each one for you. This is the magic.
- **Snapshot discovered chambers** — automatically saves a backup of each chamber the moment it's found, so it's reset-ready instantly (a chamber needs a snapshot before it can reset).

<!-- SCREENSHOT: the Auto-discover step with the [Enable] button -->

<div data-gb-custom-block data-tag="hint" data-style="success">

**That's the whole setup.** With those two on, every Trial Chamber your players walk into is registered, snapshotted, and put on an automatic reset schedule — no per-chamber work, ever.

</div>

## 3. You're done

From here on, TCP runs itself:

- Players explore → chambers get **found and snapshotted automatically**.
- Each chamber **resets on a schedule** (default every 2 days — change it in the tour or in [Basic Configuration](basic-configuration.md)).
- Every player gets **their own loot** with their own cooldown, so the second person in doesn't find empty vaults.

Want to tune loot, reset timing, or protection? Head to **[Basic Configuration](basic-configuration.md)** — but you don't have to touch a thing to have a working, multiplayer-friendly Trial Chamber server.

## Good to know

<div data-gb-custom-block data-tag="hint" data-style="info">

**One caveat for very old worlds.** Auto-discovery looks for the blocks a Trial Chamber is built from (tuff bricks, copper, vaults, trial spawners). On worlds that pre-date 1.21, players occasionally build decorative structures out of those same blocks, which the detector *could* mistake for a chamber. On fresh or normal worlds there's no risk — and if a wrong one ever slips through, a quick `/tcp delete <name>` removes it. The setup tour explains this when you reach the step.

</div>

<div data-gb-custom-block data-tag="hint" data-style="info">

**Re-run it anytime.** `/tcp setup` is always available, and a gentle reminder nudges operators who haven't run it yet. Prefer doing it by hand? Every setting lives in `config.yml` too — the tour just makes the good options easy to find.

</div>

## No natural chambers on your server?

A few servers have **no naturally-generated Trial Chambers at all** — superflat / one-block worlds, custom world generation, or `generate-structures` turned off. If that's you, auto-discovery has nothing to find, and you'll register chambers by hand instead. Here's how:

<div data-gb-custom-block data-tag="content-ref" data-url="your-first-chamber.md">

[your-first-chamber.md](your-first-chamber.md)

</div>

For everyone else — you're already finished. 🎉

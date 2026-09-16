# Manual Chamber Setup

<div data-gb-custom-block data-tag="hint" data-style="info">

**Most servers don't need this page.** If your world has naturally-generated Trial Chambers (the default on normal worlds), use **[Quick Start](quick-start.md)** instead: `/trial setup` turns on auto-discovery and BTC manages every chamber for you.

This page is for servers with **no natural chambers**: superflat and one-block worlds, custom world generation, or `generate-structures: false`. There you register a chamber by hand.

</div>

Register and configure one Trial Chamber by hand so BTC can reset it and hand out per-player loot.

## Step 1: Find a Trial Chamber

You need an existing Trial Chamber to manage. To find one:

```
/locate structure trial_chambers
```

Teleport to it and walk the whole structure so you know where its outer walls are. You register the entire chamber, not one room.

<div data-gb-custom-block data-tag="hint" data-style="info">

Trial Chambers are large, sometimes over 100 blocks across. Carry spare blocks to mark the corners.

</div>

## Step 2: Select the chamber

### With WorldEdit (recommended)

1. Get the WorldEdit wand: `//wand`
2. Left-click the lowest corner of the chamber.
3. Right-click the opposite, highest corner.

The selection must cover the whole chamber: every room, hallway, spawner, and vault, plus a few blocks of padding on all sides.

<div data-gb-custom-block data-tag="hint" data-style="warning">

Include a bit of padding around the structure. A vault or spawner sitting exactly on the selection edge may be missed by the scan.

</div>

Then register it:

```
/trial generate wand <name>
```

Use a short name with no spaces, for example `MainChamber`. You will see:

```
[BTC] Chamber MainChamber created successfully!
[BTC] Next step: Run /trial scan MainChamber to detect vaults and spawners
```

<div data-gb-custom-block data-tag="hint" data-style="info">

`/trial generate wand` does not place or change any blocks. It just tells the plugin to start managing the region you selected.

</div>

### Without WorldEdit

```
/trial generate coords <x1,y1,z1> <x2,y2,z2> [world] <name>
```

`world` is optional and defaults to the world you are standing in.

```
/trial generate coords -150,-20,400 -50,40,500 MainChamber
```

The region must be at least 31 blocks wide, 15 tall, and 31 deep, and no larger than `generation.max-volume` (default 750,000 blocks).

## Step 3: Scan for vaults and spawners

```
/trial scan <name>
```

This finds the vaults, trial spawners, and decorated pots in the region:

```
[BTC] Scanning chamber MainChamber...
[BTC] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

<div data-gb-custom-block data-tag="hint" data-style="info">

**Found 0 vaults?** Your selection did not reach them. Redo the selection larger and scan again. If your selection was fine but a wing was still missing, run `/trial scan add <name>` to grow the bounds into the missed section.

</div>

## Step 4: Create a snapshot

A snapshot is the saved "perfect" state the chamber resets back to. Make it while the chamber is untouched.

```
/trial snapshot create <name>
```

You can leave the name off to snapshot the chamber you are standing in. It saves every block, its orientation, and its contents (spawner and vault state, container loot, decorated pots, sign text, player-head skins, banner patterns, lectern books, jukebox discs, chiseled-bookshelf contents, and suspicious-block items), then compresses it to `snapshots/<name>.dat`.

This takes a few seconds to about half a minute depending on size:

```
[BTC] Creating snapshot for MainChamber...
[BTC] Snapshot created successfully! (12,847 blocks, 1.2 MB)
```

<div data-gb-custom-block data-tag="hint" data-style="info">

**Upgrading from before 1.7.2?** Older snapshots restored signs, heads, and banners as blank blocks. Run `/trial snapshot create <name>` once more on decorated chambers to capture them properly.

</div>

<div data-gb-custom-block data-tag="hint" data-style="warning">

Do not delete the `.dat` files in `snapshots/` by hand. Without its snapshot a chamber cannot reset.

</div>

## Step 5: Set an exit point

When a chamber resets, any players inside are moved out. Stand where you want them to land (usually just outside the entrance) and run:

```
/trial setexit <name>
```

This saves your exact position and facing.

<div data-gb-custom-block data-tag="hint" data-style="info">

If you skip this, players are moved to a safe spot just outside the chamber walls instead. A set exit point is tidier.

</div>

## Step 6: Test it

Check the chamber:

```
/trial info <name>
```

This shows the bounds, vault and spawner counts, snapshot status, reset interval, and last reset time.

Force a reset:

```
/trial reset <name>
```

You should be teleported to the exit point, see the chamber fully restored, and find every vault locked again.

<div data-gb-custom-block data-tag="hint" data-style="success">

The chamber is now on automatic resets using the interval in your config (default 48 hours).

</div>

## Step 7: Change the reset interval (optional)

To change the default for every chamber, edit `plugins/BetterTrialChambers/config.yml`:

```yaml
global:
  default-reset-interval: 172800  # seconds; 172800 = 48 hours
```

Run `/trial reload` after editing.

| Interval | `default-reset-interval` |
| --- | --- |
| Every 12 hours | `43200` |
| Daily | `86400` |
| Every 48 hours (default) | `172800` |
| Weekly | `604800` |
| Manual only (no automatic resets) | `0` |

To override the interval for one chamber, open `/trial menu`, pick the chamber, then **Settings**, then **Reset Interval**.

## Command summary

```bash
# 1. Select with WorldEdit
//wand

# 2. Register
/trial generate wand MainChamber

# 3. Scan for vaults and spawners
/trial scan MainChamber

# 4. Create the snapshot
/trial snapshot create MainChamber

# 5. Set the exit point
/trial setexit MainChamber

# 6. Check it
/trial info MainChamber

# 7. Test a reset
/trial reset MainChamber
```

## What's next

<div data-gb-custom-block data-tag="content-ref" data-url="../configuration/loot.yml.md">

[loot.yml.md](../configuration/loot.yml.md)

</div>

Replace vanilla vault loot with custom items, economy payouts, and command rewards.

<div data-gb-custom-block data-tag="content-ref" data-url="basic-configuration.md">

[basic-configuration.md](basic-configuration.md)

</div>

Reset schedules, warning times, and protection.

<div data-gb-custom-block data-tag="content-ref" data-url="../configuration/config.yml.md">

[config.yml.md](../configuration/config.yml.md)

</div>

The full config reference, including per-player vaults and cooldowns.

---

## Notes

<div data-gb-custom-block data-tag="hint" data-style="info">

**More chambers:** Repeat the steps. Each chamber is independent, with its own loot tables, settings, and reset schedule.

</div>

<div data-gb-custom-block data-tag="hint" data-style="info">

**Changed a chamber?** Run `/trial snapshot create <name>` again to update its snapshot. The old one is overwritten.

</div>

## If something goes wrong

**"No WorldEdit selection found"** - Select both corners with `//wand` first.

**"Scan found 0 vaults"** - The selection does not reach the vaults. Redo it larger, or run `/trial scan add <name>`.

**"Snapshot creation failed"** - Usually low disk space or a file permission problem. Check the console.

**Players not teleporting on reset** - Set an exit point with `/trial setexit <name>`.

Still stuck? See the [troubleshooting guide](../troubleshooting.md).

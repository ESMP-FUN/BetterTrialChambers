# 🏗️ dungeon.yml

Controls **procedural dungeon generation** — assembling chambers on demand from modular room pieces you build yourself. See the [`/tcp dungeon` command](../reference/commands.md) for the full workflow.

## Authoring rooms

Build each room in WorldEdit with **complete, solid walls**. At each spot a doorway could be, place a `minecraft:jigsaw` block flush in the wall with its **front facing outward** (orientations `north_up` / `east_up` / `south_up` / `west_up`). Do **not** pre-cut the opening — the generator carves a standard doorway only where two rooms actually join, and leaves unused connectors as walls. Keep your door size consistent across all rooms.

Capture rooms with `/tcp dungeon pos1`, `/tcp dungeon pos2`, then `/tcp dungeon capture <id> [roles…]` (roles become tags, e.g. `entrance`, `vault`, `boss`). Generate with `/tcp dungeon generate <name> [seed]`.

## Options

```yaml
# Maximum rooms in a generated dungeon (including the start room).
max-rooms: 20

# Room tags eligible to be the start/entrance room.
start-tags:
  - entrance

# Tags a finished layout MUST contain, with minimum counts. The stitcher retries
# with bumped seeds until these are met (or it gives up).
required-tags:
  entrance: 1
  vault: 1

# The opening carved at each joined doorway. Place jigsaw markers at the
# BOTTOM-CENTRE of the intended doorway; the carve goes up by `height` and spans
# `width` along the wall. Keep these consistent with how you build your rooms.
door:
  width: 3
  height: 3

# Block used to fill a jigsaw cell when the surrounding wall can't be sampled
# (so unconnected doors still look like wall). Match your rooms' wall material.
wall-fallback-material: TUFF_BRICKS
```

## How it works

The stitcher picks a start room, then repeatedly attaches rooms whose connectors face back into an open doorway — trying all four rotations — rejecting any placement whose bounding box overlaps an already-placed room. Each join carves a doorway on both sides; unused connectors stay walls (no open holes). The finished layout is snapshotted and registered as a normal chamber, so resets, loot, protection and (with the premium module) tier scaling all apply to it unchanged.

Generation is deterministic per `seed`, so the same seed produces the same layout. Block placement reuses TCP's Folia-safe restore pipeline; directional blocks (stairs, walls, fences, logs, signs) are rotated correctly without NMS.

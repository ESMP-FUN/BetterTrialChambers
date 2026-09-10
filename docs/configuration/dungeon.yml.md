# dungeon.yml

Controls procedural dungeon generation: assembling a chamber on demand from modular room pieces you build or import. See the [`/trial dungeon` command](../reference/commands.md) for the full workflow.

## Build a room

1. Build the room in WorldEdit with complete, solid walls. Do not pre-cut any openings.
2. At each spot a doorway could go, place a `minecraft:jigsaw` block flush in the wall with its front facing outward. Use one of these orientations: `north_up`, `east_up`, `south_up`, `west_up`. Vertical orientations are treated as plain wall.
3. Keep the doorway size the same across every room.

The generator carves a standard doorway only where two rooms actually join, and leaves unused jigsaw spots as wall.

## Capture a room

1. Stand at one corner, run `/trial dungeon pos1`.
2. Stand at the opposite corner, run `/trial dungeon pos2`.
3. Run `/trial dungeon capture <id> [roles...]`. Roles become tags, for example `entrance`, `vault`, `boss`.

Generate a dungeon with `/trial dungeon generate <name> [seed]`. The same seed always produces the same layout.

## Import datapack rooms

Instead of building in-world, import vanilla `.nbt` structure templates (the format datapacks use for jigsaw rooms).

1. Drop a loose `.nbt` file, a folder of them, or a whole datapack `.zip` into `plugins/BetterTrialChambers/dungeon/import/`.
2. Run `/trial dungeon import <file|folder|zip> [tags...]`.

* A `.nbt` file imports one room. The id is the filename.
* A folder imports every `.nbt` inside. The folder name becomes an extra tag.
* A datapack `.zip` imports every `data/<namespace>/structure/**/*.nbt` entry. Each room is tagged with its immediate parent folder name (for example `spawner`, `large`), so you can point `start-tags` and `required-tags` straight at the pack's folder names.

Jigsaw blocks in the templates become door connectors just like in-world capture. Known limits: rooms import exactly as saved (random block-swap processors are not run), only the first block palette is read, vertical jigsaw connectors become wall, and tile-entity contents (vault and spawner configs, container items) import best-effort.

## Options

```yaml
# Most rooms in a generated dungeon, counting the start room.
max-rooms: 20

# Room tags allowed to be the start/entrance room.
start-tags:
  - entrance

# Tags a finished layout must contain, with minimum counts. The generator
# retries with new seeds until these are met, or it gives up.
required-tags:
  entrance: 1
  vault: 1

# The opening carved at each joined doorway. Place jigsaw markers at the
# bottom-centre of the intended doorway. The carve goes up by `height` and
# spans `width` along the wall. Match how you build your rooms.
door:
  width: 3
  height: 3

# Block used to fill a jigsaw spot when the surrounding wall can't be read,
# so unconnected doors still look like wall. Match your rooms' wall material.
wall-fallback-material: TUFF_BRICKS
```

## How it works

The generator picks a start room, then repeatedly attaches rooms whose connectors face into an open doorway, trying all four rotations and rejecting any placement that overlaps a room already placed. Each join carves a doorway on both sides; unused connectors stay wall. The finished layout is snapshotted and registered as a normal chamber, so resets, loot, protection, and (with the premium module) tier scaling all apply to it. Block placement reuses the Folia-safe restore pipeline, and directional blocks (stairs, walls, fences, logs, signs) are rotated correctly.

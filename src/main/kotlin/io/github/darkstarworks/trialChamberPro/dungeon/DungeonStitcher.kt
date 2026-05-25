package io.github.darkstarworks.trialChamberPro.dungeon

import kotlin.random.Random

/**
 * Assembles a dungeon layout from room shapes — the pure, deterministic core
 * (no Bukkit world access; [org.bukkit.block.BlockFace] is just an enum).
 *
 * Model: rooms are placed **walled**; the stitcher records the doorway cells to
 * carve open for each successful join, so any connector left unused simply stays
 * a wall (no separate capping step). Two rooms joined at a connector are placed
 * face-adjacent (their inclusive boxes never overlap), and the two doorway cells
 * sit either side of the shared wall plane → a clean passage once carved.
 *
 * Same [seed] ⇒ identical layout.
 */
class DungeonStitcher {

    private data class OpenConn(val x: Int, val y: Int, val z: Int, val outward: org.bukkit.block.BlockFace)
    private data class Attach(
        val shape: RoomShape, val rot: Rotation,
        val offX: Int, val offY: Int, val offZ: Int, val usedConnector: Int,
    )

    /** Assemble once; returns an empty result if no start room exists or required tags aren't met. */
    fun assemble(seed: Long, shapes: List<RoomShape>, params: StitchParams): StitchResult {
        if (shapes.isEmpty()) return EMPTY
        val rng = Random(seed)
        val placements = mutableListOf<Placement>()
        val doorways = mutableListOf<Doorway>()
        val boxes = mutableListOf<IntBox>()
        val open = mutableListOf<OpenConn>()

        val start = pickStart(shapes, params, rng) ?: return EMPTY
        placeRoom(start, Rotation.NONE, params.originX, params.originY, params.originZ, -1, placements, boxes, open)
        var roomCount = 1

        while (open.isNotEmpty() && roomCount < params.maxRooms) {
            val conn = open.removeAt(rng.nextInt(open.size))
            val a = tryAttach(conn, shapes, boxes, rng) ?: continue // no fit → connector stays a wall
            placeRoom(a.shape, a.rot, a.offX, a.offY, a.offZ, a.usedConnector, placements, boxes, open)
            // Carve both sides of the join: the open room's cell and the new room's cell across the wall.
            doorways.add(Doorway(conn.x, conn.y, conn.z, conn.outward))
            doorways.add(Doorway(conn.x + conn.outward.modX, conn.y, conn.z + conn.outward.modZ, conn.outward.oppositeFace))
            roomCount++
        }

        if (!satisfiesRequired(placements, shapes, params)) return EMPTY
        return StitchResult(placements, doorways)
    }

    /** Assemble, retrying with bumped seeds until required tags are met (or [attempts] exhausted). */
    fun assembleWithRetry(seed: Long, shapes: List<RoomShape>, params: StitchParams, attempts: Int = 8): StitchResult {
        for (i in 0 until attempts) {
            val result = assemble(seed + i, shapes, params)
            if (result.placements.isNotEmpty()) return result
        }
        return EMPTY
    }

    private fun tryAttach(open: OpenConn, shapes: List<RoomShape>, boxes: List<IntBox>, rng: Random): Attach? {
        val need = open.outward.oppositeFace
        for (shape in shapes.shuffled(rng)) {
            for (rot in Rotation.ALL.shuffled(rng)) {
                for ((ci, c) in shape.connectors.withIndex()) {
                    if (rot.rotate(c.facing) != need) continue
                    val (rx, rz) = rot.rotate(c.x, c.z, shape.sizeX, shape.sizeZ)
                    val offX = (open.x + open.outward.modX) - rx
                    val offY = open.y - c.y
                    val offZ = (open.z + open.outward.modZ) - rz
                    val box = boxOf(shape, rot, offX, offY, offZ)
                    if (boxes.any { it.overlaps(box) }) continue
                    return Attach(shape, rot, offX, offY, offZ, ci)
                }
            }
        }
        return null
    }

    private fun placeRoom(
        shape: RoomShape, rot: Rotation, offX: Int, offY: Int, offZ: Int, usedConnector: Int,
        placements: MutableList<Placement>, boxes: MutableList<IntBox>, open: MutableList<OpenConn>,
    ) {
        placements.add(Placement(shape.id, rot, offX, offY, offZ))
        boxes.add(boxOf(shape, rot, offX, offY, offZ))
        shape.connectors.forEachIndexed { i, c ->
            if (i == usedConnector) return@forEachIndexed
            val (rx, rz) = rot.rotate(c.x, c.z, shape.sizeX, shape.sizeZ)
            open.add(OpenConn(rx + offX, c.y + offY, rz + offZ, rot.rotate(c.facing)))
        }
    }

    private fun boxOf(shape: RoomShape, rot: Rotation, offX: Int, offY: Int, offZ: Int): IntBox = IntBox(
        offX, offY, offZ,
        offX + rot.sizeX(shape.sizeX, shape.sizeZ) - 1,
        offY + shape.sizeY - 1,
        offZ + rot.sizeZ(shape.sizeX, shape.sizeZ) - 1,
    )

    private fun pickStart(shapes: List<RoomShape>, params: StitchParams, rng: Random): RoomShape? {
        val starts = shapes.filter { s -> s.tags.any { it in params.startTags } }
        return (if (starts.isNotEmpty()) starts else shapes).random(rng)
    }

    private fun satisfiesRequired(placements: List<Placement>, shapes: List<RoomShape>, params: StitchParams): Boolean {
        if (params.requiredTags.isEmpty()) return true
        val byId = shapes.associateBy { it.id }
        val counts = mutableMapOf<String, Int>()
        placements.forEach { p -> byId[p.roomId]?.tags?.forEach { t -> counts[t] = (counts[t] ?: 0) + 1 } }
        return params.requiredTags.all { (tag, min) -> (counts[tag] ?: 0) >= min }
    }

    companion object {
        private val EMPTY = StitchResult(emptyList(), emptyList())
    }
}

package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.block.BlockFace

/** Inclusive integer AABB. Face-adjacent boxes (a.max+1 == b.min) do NOT overlap. */
data class IntBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    fun overlaps(o: IntBox): Boolean =
        minX <= o.maxX && o.minX <= maxX &&
            minY <= o.maxY && o.minY <= maxY &&
            minZ <= o.maxZ && o.minZ <= maxZ
}

/** One placed room: which template, at what rotation, translated by an offset. */
data class Placement(
    val roomId: String,
    val rotation: Rotation,
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int,
)

/** A doorway cell to carve open after placement (one per side of a joined pair). */
data class Doorway(
    val x: Int,
    val y: Int,
    val z: Int,
    val facing: BlockFace,
)

/** Output of the stitcher: rooms to place + doorways to carve. Pure data. */
data class StitchResult(
    val placements: List<Placement>,
    val doorways: List<Doorway>,
)

/**
 * Inputs controlling assembly. [origin] is where the start room's local (0,0,0)
 * lands. [startTags] selects start rooms; [requiredTags] are minimums that must
 * be satisfied or the seed is rejected.
 */
data class StitchParams(
    val originX: Int,
    val originY: Int,
    val originZ: Int,
    val maxRooms: Int,
    val startTags: Set<String> = setOf("entrance"),
    val requiredTags: Map<String, Int> = emptyMap(),
)

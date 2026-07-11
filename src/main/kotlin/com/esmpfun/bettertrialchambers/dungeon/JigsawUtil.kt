package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.block.BlockFace
import org.bukkit.block.Orientation

/**
 * Shared jigsaw-marker helpers used by both in-world room capture
 * ([RoomTemplateManager]) and `.nbt` structure import ([StructureImporter]),
 * so orientation handling stays one implementation.
 *
 * Only horizontal jigsaw fronts become connectors — the stitcher joins rooms
 * through horizontal doorways only. Jigsaw pool/name/final_state NBT is not
 * exposed by the Bukkit API, so connectors carry position + facing only.
 */
object JigsawUtil {

    /** Horizontal outward facing of a jigsaw orientation, or null for vertical/unsupported. */
    fun orientationToFace(o: Orientation?): BlockFace? = when (o) {
        Orientation.NORTH_UP -> BlockFace.NORTH
        Orientation.EAST_UP -> BlockFace.EAST
        Orientation.SOUTH_UP -> BlockFace.SOUTH
        Orientation.WEST_UP -> BlockFace.WEST
        else -> null // UP_*/DOWN_* = vertical front, unsupported in v1
    }

    /** The two horizontal faces perpendicular to [facing] (wall-sampling preference order). */
    fun perpendicular(facing: BlockFace): List<BlockFace> = when (facing) {
        BlockFace.EAST, BlockFace.WEST -> listOf(BlockFace.NORTH, BlockFace.SOUTH)
        else -> listOf(BlockFace.EAST, BlockFace.WEST)
    }

    /** Neighbour-sampling order for filling a jigsaw cell with wall: sides first, then all faces. */
    fun sampleOrder(facing: BlockFace?): List<BlockFace> = buildList {
        if (facing != null) {
            addAll(perpendicular(facing))
            add(facing.oppositeFace)
        }
        add(BlockFace.UP); add(BlockFace.DOWN)
        add(BlockFace.NORTH); add(BlockFace.SOUTH); add(BlockFace.EAST); add(BlockFace.WEST)
    }
}

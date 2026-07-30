package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData

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

    // Resolved against the Bukkit interface, not the CraftBukkit implementation
    // class — the latter is remapped per server build and may not be accessible.
    private val jigsawInterface: Class<*>? =
        runCatching { Class.forName("org.bukkit.block.data.type.Jigsaw") }.getOrNull()

    private val getOrientation =
        runCatching { jigsawInterface?.getMethod("getOrientation") }.getOrNull()

    /**
     * Outward facing of a jigsaw block, or null when it points up/down (or the
     * block isn't a jigsaw).
     *
     * Read reflectively and matched on the enum constant's name because
     * `Jigsaw.getOrientation()` changed return type in 1.21.5 — it was
     * `org.bukkit.block.data.type.Jigsaw.Orientation` and became the top-level
     * `org.bukkit.block.Orientation`. A direct call compiled against either one
     * throws `NoSuchMethodError` on the other, so naming the type at all would
     * pin us to a single server version. The constant names are identical
     * across both, which is what makes the name match safe.
     */
    fun orientationToFace(data: BlockData?): BlockFace? {
        if (data == null || jigsawInterface?.isInstance(data) != true) return null
        val orientation = runCatching { getOrientation?.invoke(data) }.getOrNull()
        return when ((orientation as? Enum<*>)?.name) {
            "NORTH_UP" -> BlockFace.NORTH
            "EAST_UP" -> BlockFace.EAST
            "SOUTH_UP" -> BlockFace.SOUTH
            "WEST_UP" -> BlockFace.WEST
            else -> null // UP_*/DOWN_* = vertical front, unsupported in v1
        }
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

package io.github.darkstarworks.trialChamberPro.dungeon

import org.bukkit.block.BlockFace

/**
 * The four yaw rotations a room piece can be placed at. All maths is pure
 * (ints + [BlockFace] enum) so it's unit-testable without a server.
 *
 * Coordinate and facing rotation are derived from the SAME linear map
 * (clockwise about +Y), so a connector's position and the facing it points
 * stay consistent after rotation — which is what lets the stitcher align two
 * pieces reliably.
 */
enum class Rotation {
    NONE, CW90, CW180, CW270;

    /** Footprint X size after rotation (X/Z swap on the 90° rotations). */
    fun sizeX(sizeX: Int, sizeZ: Int): Int = if (this == CW90 || this == CW270) sizeZ else sizeX

    /** Footprint Z size after rotation. */
    fun sizeZ(sizeX: Int, sizeZ: Int): Int = if (this == CW90 || this == CW270) sizeX else sizeZ

    /** Map a local cell in a [sizeX]×[sizeZ] footprint to its rotated cell (Y unchanged). */
    fun rotate(x: Int, z: Int, sizeX: Int, sizeZ: Int): Pair<Int, Int> = when (this) {
        NONE -> x to z
        CW90 -> (sizeZ - 1 - z) to x
        CW180 -> (sizeX - 1 - x) to (sizeZ - 1 - z)
        CW270 -> z to (sizeX - 1 - x)
    }

    /** Rotate a horizontal [BlockFace] (non-horizontal faces pass through). */
    fun rotate(face: BlockFace): BlockFace = when (this) {
        NONE -> face
        CW90 -> when (face) {
            BlockFace.NORTH -> BlockFace.EAST
            BlockFace.EAST -> BlockFace.SOUTH
            BlockFace.SOUTH -> BlockFace.WEST
            BlockFace.WEST -> BlockFace.NORTH
            else -> face
        }
        CW180 -> when (face) {
            BlockFace.NORTH -> BlockFace.SOUTH
            BlockFace.EAST -> BlockFace.WEST
            BlockFace.SOUTH -> BlockFace.NORTH
            BlockFace.WEST -> BlockFace.EAST
            else -> face
        }
        CW270 -> when (face) {
            BlockFace.NORTH -> BlockFace.WEST
            BlockFace.WEST -> BlockFace.SOUTH
            BlockFace.SOUTH -> BlockFace.EAST
            BlockFace.EAST -> BlockFace.NORTH
            else -> face
        }
    }

    companion object {
        val ALL: List<Rotation> = listOf(NONE, CW90, CW180, CW270)
    }
}

package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.type.Wall

/**
 * Rotates a block-data string by a [Rotation] about Y, transforming the common
 * directional blockstate interfaces (stairs/doors/furnaces = [Directional];
 * logs/chains = [Orientable]; signs/banners/heads = [Rotatable];
 * fences/panes/bars = [MultipleFacing]; walls = [Wall]). Best-effort: anything
 * it doesn't recognise (or that throws) passes through unrotated.
 *
 * No-NMS — uses only the Bukkit BlockData API, so it's stable across versions.
 */
object BlockDataRotator {

    fun rotate(blockData: String, rot: Rotation): String {
        if (rot == Rotation.NONE) return blockData
        val data = try {
            Bukkit.createBlockData(blockData)
        } catch (e: IllegalArgumentException) {
            return blockData
        }
        try {
            if (data is Directional) {
                val rotated = rot.rotate(data.facing)
                if (rotated in data.faces) data.facing = rotated
            }
            if (data is Orientable) {
                val rotated = rotateAxis(data.axis, rot)
                if (rotated in data.axes) data.axis = rotated
            }
            if (data is Rotatable) data.rotation = rotate16(data.rotation, rot)
            if (data is MultipleFacing) rotateMultiFacing(data, rot)
            if (data is Wall) rotateWall(data, rot)
        } catch (e: Exception) {
            return blockData
        }
        return data.asString
    }

    private fun rotateAxis(axis: Axis, rot: Rotation): Axis =
        if ((rot == Rotation.CW90 || rot == Rotation.CW270) && axis != Axis.Y) {
            if (axis == Axis.X) Axis.Z else Axis.X
        } else {
            axis
        }

    private fun rotate16(face: BlockFace, rot: Rotation): BlockFace {
        val i = WHEEL16.indexOf(face)
        if (i < 0) return face
        val steps = when (rot) {
            Rotation.CW90 -> 4
            Rotation.CW180 -> 8
            Rotation.CW270 -> 12
            Rotation.NONE -> 0
        }
        return WHEEL16[(i + steps) % WHEEL16.size]
    }

    private fun rotateMultiFacing(data: MultipleFacing, rot: Rotation) {
        val current = data.faces.toSet()
        val allowed = data.allowedFaces
        allowed.forEach { data.setFace(it, false) }
        current.forEach { f ->
            val nf = if (isHorizontal(f)) rot.rotate(f) else f
            if (nf in allowed) data.setFace(nf, true)
        }
    }

    private fun rotateWall(data: Wall, rot: Rotation) {
        val old = mapOf(
            BlockFace.NORTH to data.getHeight(BlockFace.NORTH),
            BlockFace.EAST to data.getHeight(BlockFace.EAST),
            BlockFace.SOUTH to data.getHeight(BlockFace.SOUTH),
            BlockFace.WEST to data.getHeight(BlockFace.WEST),
        )
        old.forEach { (f, h) -> data.setHeight(rot.rotate(f), h) }
    }

    private fun isHorizontal(f: BlockFace): Boolean =
        f == BlockFace.NORTH || f == BlockFace.EAST || f == BlockFace.SOUTH || f == BlockFace.WEST

    private val WHEEL16: List<BlockFace> = listOf(
        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
        BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
    )
}

package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Rail
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.type.RedstoneWire
import org.bukkit.block.data.type.Wall

/**
 * Rotates a block-data string by a [Rotation] about Y, transforming the common
 * directional blockstate interfaces (stairs/doors/furnaces = [Directional];
 * logs/chains = [Orientable]; signs/banners/heads = [Rotatable];
 * fences/panes/bars = [MultipleFacing]; walls = [Wall]). Best-effort: anything
 * it doesn't recognise (or that throws) passes through unrotated.
 *
 * No-NMS, uses only the Bukkit BlockData API, so it's stable across versions.
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
            if (data is Rail) rotateRail(data, rot)
            if (data is RedstoneWire) rotateRedstoneWire(data, rot)
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

    /**
     * Rails describe which way they run rather than which way they face, so
     * they need turning by name. Straight rails swap between running
     * north-south and east-west, a sloped rail's uphill direction turns with
     * everything else, and a corner turns to the next corner round.
     *
     * Without this a rotated room's track came out running the wrong way, which
     * is both visible and broken to ride.
     */
    private fun rotateRail(data: Rail, rot: Rotation) {
        val steps = when (rot) {
            Rotation.NONE -> return
            Rotation.CW90 -> 1
            Rotation.CW180 -> 2
            Rotation.CW270 -> 3
        }
        var shape = data.shape
        repeat(steps) { shape = RAIL_QUARTER_TURN[shape] ?: shape }
        if (shape in data.shapes) data.shape = shape
    }

    /**
     * One quarter turn clockwise, for every shape a rail can take.
     *
     * Not private so the tests can check it turns the same way everything
     * else here does. Getting this backwards would lay perfectly ordinary
     * looking track that runs the wrong way, which nothing else would catch.
     */
    internal val RAIL_QUARTER_TURN: Map<Rail.Shape, Rail.Shape> = mapOf(
        Rail.Shape.NORTH_SOUTH to Rail.Shape.EAST_WEST,
        Rail.Shape.EAST_WEST to Rail.Shape.NORTH_SOUTH,
        Rail.Shape.ASCENDING_NORTH to Rail.Shape.ASCENDING_EAST,
        Rail.Shape.ASCENDING_EAST to Rail.Shape.ASCENDING_SOUTH,
        Rail.Shape.ASCENDING_SOUTH to Rail.Shape.ASCENDING_WEST,
        Rail.Shape.ASCENDING_WEST to Rail.Shape.ASCENDING_NORTH,
        Rail.Shape.NORTH_EAST to Rail.Shape.SOUTH_EAST,
        Rail.Shape.SOUTH_EAST to Rail.Shape.SOUTH_WEST,
        Rail.Shape.SOUTH_WEST to Rail.Shape.NORTH_WEST,
        Rail.Shape.NORTH_WEST to Rail.Shape.NORTH_EAST,
    )

    /**
     * Redstone dust remembers which neighbours it reaches towards, and those
     * directions have to turn with the room. The game does work this out again
     * on its own, but only once something nudges the block, and a room is placed
     * without those updates, so until then the dust is drawn joined up the wrong
     * way.
     */
    private fun rotateRedstoneWire(data: RedstoneWire, rot: Rotation) {
        val allowed = data.allowedFaces
        val old = allowed.associateWith { data.getFace(it) }
        old.forEach { (face, connection) ->
            val turned = if (isHorizontal(face)) rot.rotate(face) else face
            if (turned in allowed) data.setFace(turned, connection)
        }
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

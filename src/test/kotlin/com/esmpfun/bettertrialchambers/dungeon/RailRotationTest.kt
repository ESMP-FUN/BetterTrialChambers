package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.block.BlockFace
import org.bukkit.block.data.Rail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that rails turn the same way as everything else in a rotated room.
 *
 * A rail says which way it runs rather than which way it faces, so it cannot be
 * turned by the same code as a staircase and needs its own table. If that table
 * were built backwards the result would be perfectly ordinary looking track that
 * runs the wrong way, which no other check here would notice.
 */
class RailRotationTest {

    private val turn = BlockDataRotator.RAIL_QUARTER_TURN

    @Test
    fun `every rail shape knows what it turns into`() {
        for (shape in Rail.Shape.entries) {
            assertNotNull(turn[shape], "$shape has no quarter turn")
        }
    }

    @Test
    fun `four quarter turns come back to where they started`() {
        for (shape in Rail.Shape.entries) {
            var current = shape
            repeat(4) { current = turn.getValue(current) }
            assertEquals(shape, current, "$shape did not come back after four turns")
        }
    }

    @Test
    fun `no two shapes turn into the same one`() {
        assertEquals(
            Rail.Shape.entries.size, turn.values.toSet().size,
            "two different shapes turn into the same shape, so a turn loses information"
        )
    }

    @Test
    fun `a sloped rail rises the same way a block would face`() {
        // A rail rising towards the north should, after a quarter turn, rise
        // towards whatever north itself turns into. That keeps it consistent
        // with how Rotation turns a plain facing.
        val slopes = mapOf(
            BlockFace.NORTH to Rail.Shape.ASCENDING_NORTH,
            BlockFace.EAST to Rail.Shape.ASCENDING_EAST,
            BlockFace.SOUTH to Rail.Shape.ASCENDING_SOUTH,
            BlockFace.WEST to Rail.Shape.ASCENDING_WEST,
        )
        for ((face, shape) in slopes) {
            val expected = slopes.getValue(Rotation.CW90.rotate(face))
            assertEquals(expected, turn.getValue(shape), "$shape turned the wrong way")
        }
    }

    @Test
    fun `a straight rail alternates between the two directions it can run`() {
        assertEquals(Rail.Shape.EAST_WEST, turn.getValue(Rail.Shape.NORTH_SOUTH))
        assertEquals(Rail.Shape.NORTH_SOUTH, turn.getValue(Rail.Shape.EAST_WEST))
    }

    @Test
    fun `a corner turns into the next corner round`() {
        // A corner joining north and east becomes one joining east and south,
        // because north turns into east and east turns into south.
        val corners = mapOf(
            Rail.Shape.NORTH_EAST to setOf(BlockFace.NORTH, BlockFace.EAST),
            Rail.Shape.SOUTH_EAST to setOf(BlockFace.SOUTH, BlockFace.EAST),
            Rail.Shape.SOUTH_WEST to setOf(BlockFace.SOUTH, BlockFace.WEST),
            Rail.Shape.NORTH_WEST to setOf(BlockFace.NORTH, BlockFace.WEST),
        )
        for ((shape, faces) in corners) {
            val turnedFaces = faces.map { Rotation.CW90.rotate(it) }.toSet()
            val expected = corners.entries.first { it.value == turnedFaces }.key
            assertEquals(expected, turn.getValue(shape), "$shape turned the wrong way")
        }
    }

    @Test
    fun `straight rails never turn into corners or slopes`() {
        val straight = setOf(Rail.Shape.NORTH_SOUTH, Rail.Shape.EAST_WEST)
        for (shape in straight) {
            assertTrue(
                turn.getValue(shape) in straight,
                "a flat straight rail should stay a flat straight rail"
            )
        }
    }
}

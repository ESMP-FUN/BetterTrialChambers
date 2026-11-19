package com.esmpfun.bettertrialchambers.dungeon

import org.bukkit.block.BlockFace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class RotationTest {

    @Test
    fun `footprint swaps on 90 and 270`() {
        assertEquals(3, Rotation.CW90.sizeX(5, 3))
        assertEquals(5, Rotation.CW90.sizeZ(5, 3))
        assertEquals(5, Rotation.CW180.sizeX(5, 3))
        assertEquals(3, Rotation.CW180.sizeZ(5, 3))
        assertEquals(3, Rotation.CW270.sizeX(5, 3))
        assertEquals(5, Rotation.CW270.sizeZ(5, 3))
    }

    @Test
    fun `cell rotation maps corners correctly in a 5x3 footprint`() {
        // CW90: (x,z) -> (sizeZ-1-z, x)
        assertEquals(2 to 0, Rotation.CW90.rotate(0, 0, 5, 3))
        assertEquals(0 to 4, Rotation.CW90.rotate(4, 2, 5, 3))
        // CW180: (x,z) -> (4-x, 2-z)
        assertEquals(4 to 2, Rotation.CW180.rotate(0, 0, 5, 3))
        // CW270: (x,z) -> (z, 4-x)
        assertEquals(0 to 4, Rotation.CW270.rotate(0, 0, 5, 3))
        assertEquals(2 to 0, Rotation.CW270.rotate(4, 2, 5, 3))
    }

    @Test
    fun `face rotation is clockwise about Y`() {
        assertEquals(BlockFace.EAST, Rotation.CW90.rotate(BlockFace.NORTH))
        assertEquals(BlockFace.SOUTH, Rotation.CW90.rotate(BlockFace.EAST))
        assertEquals(BlockFace.WEST, Rotation.CW90.rotate(BlockFace.SOUTH))
        assertEquals(BlockFace.NORTH, Rotation.CW90.rotate(BlockFace.WEST))

        assertEquals(BlockFace.SOUTH, Rotation.CW180.rotate(BlockFace.NORTH))
        assertEquals(BlockFace.WEST, Rotation.CW270.rotate(BlockFace.NORTH))
    }

    @Test
    fun `none is identity`() {
        assertEquals(3 to 4, Rotation.NONE.rotate(3, 4, 9, 9))
        assertEquals(BlockFace.SOUTH, Rotation.NONE.rotate(BlockFace.SOUTH))
    }
}

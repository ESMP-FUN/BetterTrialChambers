package io.github.darkstarworks.trialChamberPro.dungeon

import org.bukkit.block.BlockFace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class DungeonStitcherTest {

    private val stitcher = DungeonStitcher()

    private fun room(id: String, vararg connectors: Connector, tags: Set<String> = emptySet()) =
        RoomShape(id, 3, 3, 3, connectors.toList(), tags)

    private fun boxOf(p: Placement, shapes: List<RoomShape>): IntBox {
        val s = shapes.first { it.id == p.roomId }
        return IntBox(
            p.offsetX, p.offsetY, p.offsetZ,
            p.offsetX + p.rotation.sizeX(s.sizeX, s.sizeZ) - 1,
            p.offsetY + s.sizeY - 1,
            p.offsetZ + p.rotation.sizeZ(s.sizeX, s.sizeZ) - 1,
        )
    }

    // ---- IntBox ----

    @Test
    fun `face-adjacent boxes do not overlap`() {
        val a = IntBox(0, 0, 0, 4, 4, 4)
        val b = IntBox(5, 0, 0, 9, 4, 4)
        assertFalse(a.overlaps(b))
    }

    @Test
    fun `intersecting boxes overlap, separated boxes do not`() {
        assertTrue(IntBox(0, 0, 0, 4, 4, 4).overlaps(IntBox(4, 0, 0, 8, 4, 4)))
        assertFalse(IntBox(0, 0, 0, 4, 4, 4).overlaps(IntBox(6, 0, 0, 9, 4, 4)))
    }

    // ---- stitching ----

    @Test
    fun `joins two rooms face-adjacent with two carved doorways`() {
        val shapes = listOf(
            room("entry", Connector(2, 1, 1, BlockFace.EAST), tags = setOf("entrance")),
            room("east", Connector(0, 1, 1, BlockFace.WEST)),
        )
        val params = StitchParams(0, 0, 0, maxRooms = 2)
        val r = stitcher.assemble(seed = 1L, shapes = shapes, params = params)

        assertEquals(2, r.placements.size)
        // The start ('entrance'-tagged) is placed unrotated at the origin.
        assertTrue(r.placements.any { it == Placement("entry", Rotation.NONE, 0, 0, 0) })
        // The attached room sits face-adjacent to the east. (Which room/rotation is
        // chosen can vary — a single-connector room matches any facing via rotation —
        // but the offset is the cell across the shared wall regardless.)
        assertTrue(r.placements.any { it.offsetX == 3 && it.offsetY == 0 && it.offsetZ == 0 })
        assertEquals(2, r.doorways.size)
        assertTrue(r.doorways.any { it.x == 2 && it.y == 1 && it.z == 1 && it.facing == BlockFace.EAST })
        assertTrue(r.doorways.any { it.x == 3 && it.y == 1 && it.z == 1 && it.facing == BlockFace.WEST })
    }

    @Test
    fun `rotates a candidate so its connector faces back`() {
        // open connector faces EAST → candidate must present a WEST-facing door.
        // 'turny' only has a SOUTH connector, which becomes WEST under CW90.
        val shapes = listOf(
            room("entry", Connector(2, 1, 1, BlockFace.EAST), tags = setOf("entrance")),
            room("turny", Connector(1, 1, 2, BlockFace.SOUTH)),
        )
        val r = stitcher.assemble(seed = 1L, shapes = shapes, params = StitchParams(0, 0, 0, maxRooms = 2))
        // Neither candidate has a WEST connector at NONE, so the attached room must be
        // rotated to present its door back toward the opening.
        val attached = r.placements.first { it.offsetX == 3 }
        assertNotEquals(Rotation.NONE, attached.rotation)
    }

    @Test
    fun `same seed is deterministic`() {
        val shapes = listOf(
            room("hub", Connector(2, 1, 1, BlockFace.EAST), Connector(0, 1, 1, BlockFace.WEST), tags = setOf("entrance")),
            room("cap", Connector(0, 1, 1, BlockFace.WEST)),
            room("cap2", Connector(2, 1, 1, BlockFace.EAST)),
        )
        val params = StitchParams(0, 0, 0, maxRooms = 6)
        assertEquals(stitcher.assemble(42L, shapes, params), stitcher.assemble(42L, shapes, params))
    }

    @Test
    fun `unmet required tags yields empty`() {
        val shapes = listOf(room("entry", Connector(2, 1, 1, BlockFace.EAST), tags = setOf("entrance")))
        val params = StitchParams(0, 0, 0, maxRooms = 4, requiredTags = mapOf("vault" to 1))
        assertTrue(stitcher.assembleWithRetry(1L, shapes, params).placements.isEmpty())
    }

    @Test
    fun `no two placed rooms overlap`() {
        val shapes = listOf(
            room(
                "hub",
                Connector(2, 1, 1, BlockFace.EAST), Connector(0, 1, 1, BlockFace.WEST),
                Connector(1, 1, 2, BlockFace.SOUTH), Connector(1, 1, 0, BlockFace.NORTH),
                tags = setOf("entrance"),
            ),
            room("corr", Connector(0, 1, 1, BlockFace.WEST), Connector(2, 1, 1, BlockFace.EAST)),
            room("cap", Connector(0, 1, 1, BlockFace.WEST)),
        )
        val params = StitchParams(0, 0, 0, maxRooms = 20)
        repeat(25) { seed ->
            val r = stitcher.assemble(seed.toLong(), shapes, params)
            val boxes = r.placements.map { boxOf(it, shapes) }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                assertFalse(boxes[i].overlaps(boxes[j]), "overlap at seed=$seed between $i and $j")
            }
        }
    }
}

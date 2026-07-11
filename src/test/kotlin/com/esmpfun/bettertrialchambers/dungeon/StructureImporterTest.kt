package com.esmpfun.bettertrialchambers.dungeon

import com.esmpfun.bettertrialchambers.dungeon.StructureImporter.Companion.sanitizeId
import com.esmpfun.bettertrialchambers.dungeon.StructureImporter.Companion.zipEntryInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pure-logic tests for the v1.7.0 `.nbt` importer (zip path filter + id sanitization). */
class StructureImporterTest {

    @Test
    fun `zipEntryInfo parses datapack structure paths`() {
        val e = zipEntryInfo("data/crazy_chambers/structure/spawner/small_1.nbt")!!
        assertEquals("crazy_chambers_spawner_small_1", e.id)
        assertEquals("spawner", e.autoTag)
    }

    @Test
    fun `zipEntryInfo accepts legacy plural structures folder`() {
        val e = zipEntryInfo("data/mansion/structures/large/room.nbt")!!
        assertEquals("mansion_large_room", e.id)
        assertEquals("large", e.autoTag)
    }

    @Test
    fun `zipEntryInfo handles top-level nbt with no subfolder`() {
        val e = zipEntryInfo("data/ns/structure/start.nbt")!!
        assertEquals("ns_start", e.id)
        assertNull(e.autoTag)
    }

    @Test
    fun `zipEntryInfo rejects non-structure entries`() {
        assertNull(zipEntryInfo("pack.mcmeta"))
        assertNull(zipEntryInfo("data/ns/worldgen/structure/trial_chambers.json"))
        assertNull(zipEntryInfo("data/ns/structure/room.json"))
        assertNull(zipEntryInfo("assets/ns/structure/room.nbt"))
    }

    @Test
    fun `zipEntryInfo normalizes backslashes and leading slash`() {
        val e = zipEntryInfo("/data\\ns\\structure\\hall\\a.nbt")!!
        assertEquals("ns_hall_a", e.id)
        assertEquals("hall", e.autoTag)
    }

    @Test
    fun `sanitizeId lowercases and squashes invalid runs`() {
        assertEquals("my_room_2", sanitizeId("My Room (2)"))
        assertEquals("a_b", sanitizeId("a--b"))
        assertEquals("room", sanitizeId("_room_"))
    }
}

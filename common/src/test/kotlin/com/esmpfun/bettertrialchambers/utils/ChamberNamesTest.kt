package com.esmpfun.bettertrialchambers.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChamberNamesTest {

    @Test
    fun `plain names are accepted`() {
        listOf("chamber", "Chamber_1", "east-wing", "a", "auto_world_128_256").forEach {
            assertTrue(ChamberNames.isValid(it), "$it should be a usable name")
        }
    }

    @Test
    fun `names that would escape or break the snapshot file are rejected`() {
        listOf(
            "../../plugins/evil", "east/wing", "east\\wing", "c:name", "dot.name",
            "with space", "", "x".repeat(33), "emoji😀",
        ).forEach {
            assertFalse(ChamberNames.isValid(it), "$it should be refused")
        }
    }

    @Test
    fun `sanitize turns anything into a usable name`() {
        assertEquals("auto_My_World_10_20", ChamberNames.sanitize("auto_My World_10_20"))
        assertEquals("chamber", ChamberNames.sanitize(""))
        assertTrue(ChamberNames.isValid(ChamberNames.sanitize("../../x")))
        assertTrue(ChamberNames.isValid(ChamberNames.sanitize("w".repeat(80))))
    }
}

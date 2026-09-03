package com.esmpfun.bettertrialchambers.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Pins the rule that decides where a room template's file is allowed to live.
 *
 * A template's name is typed on the end of a command and becomes a filename. It
 * used to go in unchecked, so a name with `..` or a slash in it reached outside
 * the rooms folder: `/trial dungeon delete` would remove a file elsewhere on the
 * server and capturing would write one there.
 *
 * The rule itself is one line in `RoomTemplateManager`, but it is a boundary,
 * and boundaries are worth a test that states what they let through. The same
 * check is reproduced here rather than reaching into the class, because
 * constructing that class needs a running server.
 */
class TemplatePathContainmentTest {

    @TempDir
    lateinit var roomsDir: File

    /** The rule under test, matching `RoomTemplateManager.templateFile`. */
    private fun templateFile(dir: File, id: String): File? {
        if (id.isBlank()) return null
        val candidate = File(dir, "$id.dat").canonicalFile
        return if (candidate.parentFile == dir.canonicalFile) candidate else null
    }

    @Test
    fun `an ordinary name is accepted exactly as written`() {
        for (id in listOf("corridor", "big_room_2", "Room With Spaces", "räum-1")) {
            val file = templateFile(roomsDir, id)
            assertEquals("$id.dat", file?.name, "'$id' should be usable as a room name")
        }
    }

    @Test
    fun `climbing out of the rooms folder is refused`() {
        for (id in listOf(
            "../escaped",
            "../../escaped",
            "../../../../../../etc/passwd",
            "sub/../../escaped",
        )) {
            assertNull(templateFile(roomsDir, id), "'$id' should not be allowed to leave the folder")
        }
    }

    @Test
    fun `a name pointing into a subfolder is refused`() {
        // Even staying underneath the rooms folder, the file has to sit directly
        // in it, because that is where listing and loading look.
        assertNull(templateFile(roomsDir, "nested/room"))
    }

    @Test
    fun `a blank name is refused`() {
        assertNull(templateFile(roomsDir, ""))
        assertNull(templateFile(roomsDir, "   "))
    }

    @Test
    fun `a refused name cannot reach a real file outside the folder`() {
        // The point of the whole thing: prove the escape would otherwise have
        // landed on something real.
        val outside = File(roomsDir.parentFile, "important.dat")
        outside.writeText("do not delete")
        val escape = "../important"

        assertNull(templateFile(roomsDir, escape))
        // And confirm the unguarded form really would have found it, so this
        // test fails loudly if the containment rule is ever quietly dropped.
        val unguarded = File(roomsDir, "$escape.dat").canonicalFile
        assertEquals(outside.canonicalFile, unguarded)
        assertTrue(outside.exists())
        assertFalse(unguarded.parentFile == roomsDir.canonicalFile)
    }
}

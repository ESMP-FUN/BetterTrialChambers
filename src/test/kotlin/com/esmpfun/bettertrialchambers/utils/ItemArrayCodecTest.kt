package com.esmpfun.bettertrialchambers.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Checks that one unreadable item does not take the rest of the container with it.
 *
 * That is the whole point of this class. Both of the copies of this code it
 * replaced abandoned the entire container the moment a single item would not
 * read, so a player lost everything over one entry, and the version change that
 * makes an item unreadable is exactly when a container is most likely to have
 * one in it.
 *
 * These tests do not need a running server, because an item that will not read
 * is precisely the case under test: away from a server no stored item reads, so
 * every non-empty slot here takes the failure path on purpose.
 */
class ItemArrayCodecTest {

    /** Builds the stored form by hand: a slot count, then a length per slot. */
    private fun blob(vararg slots: ByteArray?): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(slots.size)
            for (slot in slots) {
                if (slot == null) {
                    out.writeInt(-1)
                } else {
                    out.writeInt(slot.size)
                    out.write(slot)
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decode(encoded: String): Pair<Array<org.bukkit.inventory.ItemStack?>?, List<String>> {
        val problems = mutableListOf<String>()
        val result = ItemArrayCodec.decode(encoded) { problems.add(it) }
        return result to problems
    }

    @Test
    fun `an empty container round-trips`() {
        val (slots, problems) = decode(blob())
        assertEquals(0, slots?.size)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `empty slots stay empty and are not reported as problems`() {
        val (slots, problems) = decode(blob(null, null, null))
        assertEquals(3, slots?.size)
        slots?.forEach { assertNull(it) }
        assertTrue(problems.isEmpty(), "empty slots are normal, not a problem: $problems")
    }

    @Test
    fun `an unreadable item leaves its own slot empty and keeps the container's shape`() {
        val junk = byteArrayOf(1, 2, 3, 4)
        val (slots, problems) = decode(blob(null, junk, null))
        assertEquals(3, slots?.size, "the container should keep all of its slots")
        assertNull(slots?.get(1), "the unreadable item's slot should be empty")
        assertEquals(1, problems.size, "one unreadable item should be reported once")
    }

    @Test
    fun `later slots survive an unreadable item earlier in the container`() {
        // The important one. Every slot's bytes are taken off the stream before
        // anything tries to make sense of them, so a bad item cannot knock the
        // reader out of step and destroy everything after it.
        val junk = byteArrayOf(9, 9, 9)
        val (slots, _) = decode(blob(junk, null, junk, null, junk))
        assertEquals(5, slots?.size, "all five slots should still be accounted for")
        assertNull(slots?.get(1))
        assertNull(slots?.get(3))
    }

    @Test
    fun `several unreadable items are reported together, not one message each`() {
        val junk = byteArrayOf(7)
        val (_, problems) = decode(blob(junk, junk, junk, junk))
        assertEquals(1, problems.size, "one summary, not one line per item")
        assertTrue(problems.first().contains("4"), "the summary should say how many: ${problems.first()}")
    }

    @Test
    fun `nonsense text is refused outright rather than half-read`() {
        val (slots, problems) = decode("this is not a container")
        assertNull(slots)
        assertEquals(1, problems.size)
    }

    @Test
    fun `a container claiming an absurd number of slots is refused`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeInt(Int.MAX_VALUE) }
        val (slots, problems) = decode(Base64.getEncoder().encodeToString(bytes.toByteArray()))
        assertNull(slots, "junk should not be able to ask for a huge array")
        assertEquals(1, problems.size)
    }
}

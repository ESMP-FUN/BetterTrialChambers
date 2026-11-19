package com.esmpfun.bettertrialchambers.utils

import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Turns the contents of a container into text and back again.
 *
 * Used for both of the places the plugin has to remember what was in a chest:
 * the copy each player gets of a container inside a chamber, and the contents
 * captured when a chamber is snapshotted. Those two grew separate copies of the
 * same code, and this is the one both now share.
 *
 * Each slot keeps its position, so an item that was in the third slot comes back
 * in the third slot, and an empty slot is written as one number rather than an
 * item. Items themselves are stored exactly as the game stores them, so
 * enchantments, custom names and everything else survive; the game upgrades
 * those on the way back in, which means anything saved by an older Minecraft
 * still reads.
 *
 * ### One bad item does not lose the rest
 *
 * Every slot's bytes are read before anything tries to make sense of them, so a
 * single item that will not read leaves an empty slot and every other slot in
 * the container still comes back. Both of the older copies of this code did the
 * opposite: reading one bad item abandoned the whole container, and a player
 * lost the lot over a single entry. That matters most in exactly the situation
 * where it is most likely, which is a server that has just moved between
 * Minecraft versions.
 */
object ItemArrayCodec {

    /** Anything past this is not a real container and the text is treated as junk. */
    private const val MAX_SLOTS = 128

    /** Marks an empty slot. */
    private const val EMPTY = -1

    fun encode(contents: Array<ItemStack?>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(contents.size)
            for (item in contents) {
                if (item == null || item.type.isAir) {
                    out.writeInt(EMPTY)
                } else {
                    val encoded = item.serializeAsBytes()
                    out.writeInt(encoded.size)
                    out.write(encoded)
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /**
     * Reads contents back.
     *
     * @param onProblem called with a readable explanation when something cannot
     *   be read: once per unreadable item, and once more if the whole thing is
     *   unreadable.
     * @return the contents, with an empty slot wherever an item would not read,
     *   or null when the text as a whole makes no sense.
     */
    fun decode(encoded: String, onProblem: (String) -> Unit): Array<ItemStack?>? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val size = input.readInt()
            require(size in 0..MAX_SLOTS) { "container claims to have $size slots" }
            var unreadable = 0
            val slots = Array<ItemStack?>(size) {
                val length = input.readInt()
                if (length == EMPTY) {
                    null
                } else {
                    require(length in 0..MAX_ITEM_BYTES) { "item claims to be $length bytes" }
                    // Read first, understand second. Taking the bytes off the
                    // stream even when the item will not read is what keeps every
                    // later slot in the container readable.
                    val buffer = ByteArray(length)
                    input.readFully(buffer)
                    runCatching { ItemStack.deserializeBytes(buffer) }
                        .getOrElse {
                            unreadable++
                            null
                        }
                }
            }
            if (unreadable > 0) {
                onProblem(
                    "$unreadable item(s) could not be read and have been left out; " +
                        "everything else in the container is intact"
                )
            }
            slots
        }
    } catch (e: Exception) {
        onProblem("the saved contents could not be read at all (${e.message})")
        null
    }

    /**
     * A single item's stored form is a few hundred bytes at most. The cap is
     * generous, and only exists so that junk text cannot ask for an enormous
     * array before anything has had a chance to notice.
     */
    private const val MAX_ITEM_BYTES = 2 * 1024 * 1024
}

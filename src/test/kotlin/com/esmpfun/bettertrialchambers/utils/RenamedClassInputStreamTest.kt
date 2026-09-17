package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.dungeon.Connector
import com.esmpfun.bettertrialchambers.dungeon.RoomTemplate
import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import org.bukkit.block.BlockFace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class RenamedClassInputStreamTest {

    @Test
    fun `room template saved under the old package name still loads`() {
        val template = RoomTemplate(
            id = "hall",
            sizeX = 3, sizeY = 2, sizeZ = 3,
            blocks = mapOf(
                Triple(0, 0, 0) to BlockSnapshot("minecraft:tuff_bricks"),
                Triple(1, 0, 0) to BlockSnapshot("minecraft:chest[facing=north]", mapOf("LootTable" to "x")),
            ),
            connectors = listOf(Connector(1, 0, 2, BlockFace.SOUTH)),
            tags = setOf("start"),
        )
        val saved = CompressionUtil.compressObject(template)
        val oldSave = CompressionUtil.compress(toOldPackage(CompressionUtil.decompress(saved)))

        assertThrows(ClassNotFoundException::class.java) {
            java.io.ObjectInputStream(CompressionUtil.decompress(oldSave).inputStream()).readObject()
        }
        assertEquals(template, CompressionUtil.decompressObject<RoomTemplate>(oldSave))
    }

    /** Rewrites every length-prefixed class name in a serialized stream to the pre-rebrand package. */
    private fun toOldPackage(bytes: ByteArray): ByteArray {
        val newDotted = RenamedClassInputStream.NEW_PACKAGE.toByteArray()
        val out = ByteArrayOutputStream()
        var i = 0
        var renamed = 0
        while (i < bytes.size) {
            if (i + 2 + newDotted.size <= bytes.size && startsAt(bytes, i + 2, newDotted)) {
                val len = ByteBuffer.wrap(bytes, i, 2).short.toInt() and 0xFFFF
                val name = String(bytes, i + 2, len)
                val old = (RenamedClassInputStream.OLD_PACKAGE + name.removePrefix(RenamedClassInputStream.NEW_PACKAGE)).toByteArray()
                out.write(ByteBuffer.allocate(2).putShort(old.size.toShort()).array())
                out.write(old)
                i += 2 + len
                renamed++
            } else {
                out.write(bytes[i].toInt())
                i++
            }
        }
        check(renamed >= 3) { "expected to rename the template, snapshot and connector classes, renamed $renamed" }
        return out.toByteArray()
    }

    private fun startsAt(bytes: ByteArray, at: Int, prefix: ByteArray): Boolean =
        prefix.indices.all { bytes[at + it] == prefix[it] }
}

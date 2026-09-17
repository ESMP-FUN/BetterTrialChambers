package com.esmpfun.bettertrialchambers.utils

import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * Saves a block along with everything it is holding, and puts it back.
 *
 * ### What this replaces
 *
 * Chambers used to be remembered one block type at a time, by hand: a chest read
 * this way, a sign that way, a decorated pot another. That list knew about
 * eleven kinds of block out of the forty-nine that can hold anything, and even
 * for the ones it knew it only read part of what they held. A chamber that was
 * not a stock Trial Chamber therefore lost pieces of itself on every reset:
 *
 *  - command blocks came back without their commands, which is most of what a
 *    hand-built minigame area is made of
 *  - ordinary mob spawners came back blank
 *  - shelves and campfires came back empty
 *  - beacons lost their effects, end gateways their destination
 *  - every container lost its name and its lock
 *  - anything another plugin had stored on a block was wiped
 *
 * and the list fell further behind with every Minecraft release.
 *
 * This uses the game's own saving instead, the same machinery a structure block
 * uses. It covers every kind of block, holds all of what each one contains, and
 * needs nothing added to it when Minecraft adds a new one. It is also versioned
 * by the game, so a block saved by an older Minecraft is upgraded on the way
 * back in rather than misread.
 *
 * Confirmed on a real server before being relied on: see
 * [StructureRoundTripProbe] and `/trial debug structure`.
 *
 * ### Threading
 *
 * Both calls read or write the world and must run on the thread that owns that
 * location: the main thread on Paper, the region's thread on Folia.
 */
object BlockEntityCapture {

    /**
     * Saves [block] and everything it holds, or returns null when there is
     * nothing to save (an ordinary block with no contents) or when saving fails.
     *
     * Plain blocks deliberately return null: their shape is already kept as a
     * block description, which is far smaller than a saved structure, and there
     * would be nothing extra to gain.
     */
    fun capture(server: Server, block: Block): ByteArray? {
        if (block.state !is TileState) return null
        return runCatching {
            val structure = server.structureManager.createStructure()
            val corner = block.location
            structure.fill(corner, corner.clone().add(1.0, 1.0, 1.0), false)
            ByteArrayOutputStream().also { server.structureManager.saveStructure(it, structure) }.toByteArray()
        }.getOrNull()
    }

    /**
     * Puts a saved block back at [at], contents and all.
     *
     * This writes the block itself as well as its contents, so a caller does not
     * need to place the block first.
     *
     * @return true when it was put back.
     */
    fun restore(server: Server, bytes: ByteArray, at: Location): Boolean = runCatching {
        val structure = server.structureManager.loadStructure(ByteArrayInputStream(bytes))
        structure.place(
            at,
            // Entities are handled separately, so never brought back here: a
            // block's saved region would otherwise duplicate anything that
            // happened to be standing in the same cell every time it restored.
            false,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            1.0f,
            Random(),
        )
        true
    }.getOrDefault(false)
}

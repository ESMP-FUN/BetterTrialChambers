package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.Location
import org.bukkit.World

/**
 * Utility class for region and bounding box operations.
 */
object RegionUtil {

    /**
     * Represents a 3D bounding box.
     */
    data class BoundingBox(
        val world: World,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int
    ) {
        /**
         * Checks if a location is within this bounding box.
         */
        fun contains(location: Location): Boolean {
            if (location.world != world) return false
            val x = location.blockX
            val y = location.blockY
            val z = location.blockZ
            return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
        }

        /**
         * Gets the volume (number of blocks) in this bounding box.
         */
        fun getVolume(): Int {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
        }

        /**
         * Iterates through all block locations in this bounding box.
         */
        fun forEach(action: (Location) -> Unit) {
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        action(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
                    }
                }
            }
        }

        /**
         * Gets all chunk coordinates that intersect with this bounding box.
         */
        fun getChunks(): Set<Pair<Int, Int>> {
            val chunks = mutableSetOf<Pair<Int, Int>>()
            val minChunkX = minX shr 4
            val maxChunkX = maxX shr 4
            val minChunkZ = minZ shr 4
            val maxChunkZ = maxZ shr 4

            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    chunks.add(Pair(chunkX, chunkZ))
                }
            }

            return chunks
        }

        /**
         * Expands the bounding box by a certain amount in all directions.
         */
        fun expand(amount: Int): BoundingBox {
            return copy(
                minX = minX - amount,
                minY = (minY - amount).coerceAtLeast(world.minHeight),
                minZ = minZ - amount,
                maxX = maxX + amount,
                maxY = (maxY + amount).coerceAtMost(world.maxHeight - 1),
                maxZ = maxZ + amount
            )
        }
    }

    /**
     * Creates a bounding box from two locations.
     */
    fun createBoundingBox(loc1: Location, loc2: Location): BoundingBox? {
        if (loc1.world != loc2.world) return null

        val minX = minOf(loc1.blockX, loc2.blockX)
        val minY = minOf(loc1.blockY, loc2.blockY)
        val minZ = minOf(loc1.blockZ, loc2.blockZ)
        val maxX = maxOf(loc1.blockX, loc2.blockX)
        val maxY = maxOf(loc1.blockY, loc2.blockY)
        val maxZ = maxOf(loc1.blockZ, loc2.blockZ)

        return BoundingBox(loc1.world!!, minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * Checks if two bounding boxes intersect.
     */
    fun intersects(box1: BoundingBox, box2: BoundingBox): Boolean {
        if (box1.world != box2.world) return false

        return box1.minX <= box2.maxX && box1.maxX >= box2.minX &&
               box1.minY <= box2.maxY && box1.maxY >= box2.minY &&
               box1.minZ <= box2.maxZ && box1.maxZ >= box2.minZ
    }

    /**
     * Asks the server to unload the chunks covering a chamber, once the plugin has
     * finished working on it.
     *
     * Reading or rewriting every block in a chamber pulls that whole part of the map
     * into memory. Without this nudge the server can keep it there long after the job
     * is done, which on a machine with little memory looks like memory that goes up
     * after a discovery or a reset and never comes back down.
     *
     * This is only ever a hint. The server ignores it for any chunk still in use —
     * players nearby, force-loaded, or spawn chunks — so it can never pull the map out
     * from under anyone.
     */
    fun releaseChunks(
        plugin: BetterTrialChambers,
        world: World,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
    ) {
        for (cx in (minX shr 4)..(maxX shr 4)) {
            for (cz in (minZ shr 4)..(maxZ shr 4)) {
                val loc = Location(world, (cx shl 4) + 8.0, 64.0, (cz shl 4) + 8.0)
                plugin.scheduler.runAtLocation(loc, Runnable {
                    if (world.isChunkLoaded(cx, cz)) {
                        world.unloadChunkRequest(cx, cz)
                    }
                })
            }
        }
    }
}

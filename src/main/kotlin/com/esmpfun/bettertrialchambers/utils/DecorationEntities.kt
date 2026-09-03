package com.esmpfun.bettertrialchambers.utils

import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Hanging
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.Random
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The things standing in a chamber that are part of the build rather than part
 * of the game: item frames, paintings, armour stands, display entities, and the
 * cushions added in 26.3.
 *
 * ### Why these and not everything
 *
 * A snapshot has never included anything that is not a block, so none of this
 * came back after a reset. Worse for armour stands in particular: an armour
 * stand counts as a living thing to the server, so the reset's mob clearing
 * removed every one of them, and nothing put them back. A decorated chamber
 * quietly lost its decorations the first time it reset.
 *
 * Mobs, dropped items, projectiles, minecarts and boats are deliberately left
 * out. Bringing those back would resurrect mobs players had killed and duplicate
 * loot lying on the floor, which is worse than losing them.
 *
 * ### What is deliberately skipped
 *
 * Anything the server would not save with the world is skipped, which is the
 * game's own answer to "is this part of the build". That covers the plugin's own
 * markers for free: the glowing outline on an active spawner and the tick of
 * feedback above an opened vault are both created as not-to-be-saved, so a
 * snapshot taken while a fight is happening cannot bake one of them in
 * permanently. The spawner marker is also tagged, and that tag is checked as
 * well, in case one is ever made persistent by mistake.
 *
 * ### How one is remembered
 *
 * Each one is saved the same way a block's contents are: by handing the game a
 * small pocket of space around it and letting the game describe what is there,
 * in the same form a structure block uses. Writing that description out by hand
 * was tried first and quietly lost anything holding an item, so an item frame
 * came back empty. This way there is nothing to keep up to date and nothing to
 * lose.
 *
 * The pocket reaches one block past the decoration on every side, because the
 * game leaves out anything sitting exactly on the edge of what it is asked for,
 * and a painting or a floating label very often sits exactly on an edge. It
 * never reaches outside the chamber, so nothing a player built next door is
 * touched. The blocks inside that pocket are the chamber's own, already put back
 * a moment earlier, and any the pocket would change are put straight back.
 *
 * Because the game hands back everything touching that pocket, a second thing
 * standing nearby comes along with it. Only the one that was asked for is kept;
 * anything else that appears alongside it is taken straight back out, so nothing
 * is ever duplicated and no mob is brought back to life.
 */
object DecorationEntities {

    /** The tag [com.esmpfun.bettertrialchambers.managers.SpawnerWaveManager] puts on its markers. */
    private val glowMarkerKey = org.bukkit.NamespacedKey("trialchamberpro", "glow_marker")

    /**
     * How far a re-created decoration may land from where it stood and still
     * count as the one asked for. Generous on purpose: some kinds settle
     * against the block they hang on rather than landing on the exact spot
     * they were recorded at, and anything else that turned up alongside them
     * is told apart by what kind of thing it is, not by a hair's width.
     */
    private const val POSITION_TOLERANCE = 2.0

    /**
     * How far past the decoration the saved pocket reaches, in blocks. One is
     * enough: the game only leaves out what sits exactly on the edge.
     */
    private const val MARGIN = 1

    /**
     * One saved decoration: the pocket of space around it exactly as the game
     * describes it, plus which kind of thing it was and where it stood, so it
     * can be told apart from anything else sharing that pocket.
     */
    data class Captured(
        val type: String,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        val bytes: ByteArray,
    ) : Serializable {

        // A ByteArray compares by identity, so these are written by hand.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Captured) return false
            return type == other.type &&
                minX == other.minX && minY == other.minY && minZ == other.minZ &&
                sizeX == other.sizeX && sizeY == other.sizeY && sizeZ == other.sizeZ &&
                x == other.x && y == other.y && z == other.z &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            for (part in intArrayOf(minX, minY, minZ, sizeX, sizeY, sizeZ)) {
                result = 31 * result + part
            }
            result = 31 * result + x.hashCode()
            result = 31 * result + y.hashCode()
            result = 31 * result + z.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }

        companion object {
            private const val serialVersionUID = 3L
        }
    }

    /** True when [entity] is part of the build and should come back after a reset. */
    fun isDecoration(entity: Entity): Boolean {
        if (!entity.isPersistent) return false
        if (entity.persistentDataContainer.has(glowMarkerKey, PersistentDataType.STRING)) return false
        return when {
            entity is Hanging -> true      // item frames, glow item frames, paintings
            entity is ArmorStand -> true
            entity is Display -> true      // text, item and block displays
            // Cushions arrived in 26.3, so there is no name for them in the
            // older versions this also has to build against. Asked for by id
            // instead, which works on any version and simply never matches on
            // one that has no cushions.
            entity.type.key.value().endsWith("cushion") -> true
            else -> false
        }
    }

    /**
     * Every decoration currently standing inside the given box.
     *
     * The chamber's own edges are passed in so the pocket saved around each one
     * never reaches outside them.
     *
     * Must run on the thread that owns that part of the world.
     */
    fun capture(
        server: Server,
        entitiesInside: Collection<Entity>,
        minX: Int, minY: Int, minZ: Int,
        maxX: Int, maxY: Int, maxZ: Int,
    ): List<Captured> =
        entitiesInside.filter { isDecoration(it) }.mapNotNull { entity ->
            runCatching {
                val loc = entity.location
                val world = loc.world ?: return@runCatching null
                val lowX = max(minX, floor(loc.x).toInt() - MARGIN)
                val lowY = max(minY, floor(loc.y).toInt() - MARGIN)
                val lowZ = max(minZ, floor(loc.z).toInt() - MARGIN)
                val highX = min(maxX, floor(loc.x).toInt() + MARGIN)
                val highY = min(maxY, floor(loc.y).toInt() + MARGIN)
                val highZ = min(maxZ, floor(loc.z).toInt() + MARGIN)

                val structure = server.structureManager.createStructure()
                structure.fill(
                    Location(world, lowX.toDouble(), lowY.toDouble(), lowZ.toDouble()),
                    Location(world, (highX + 1).toDouble(), (highY + 1).toDouble(), (highZ + 1).toDouble()),
                    true,
                )
                val bytes = ByteArrayOutputStream()
                    .also { server.structureManager.saveStructure(it, structure) }
                    .toByteArray()
                Captured(
                    type = entity.type.key.toString(),
                    minX = lowX, minY = lowY, minZ = lowZ,
                    sizeX = highX - lowX + 1, sizeY = highY - lowY + 1, sizeZ = highZ - lowZ + 1,
                    x = loc.x, y = loc.y, z = loc.z,
                    bytes = bytes,
                )
            }.getOrNull()
        }

    /**
     * Removes the decorations currently inside the box and puts the saved ones
     * back in their place.
     *
     * Clearing first is what stops them multiplying: without it, every reset
     * would add another copy of every item frame on top of the one already
     * there. Only decorations are touched, so mobs, dropped items and anything
     * a player is carrying are left exactly as they are.
     *
     * Must run on the thread that owns that part of the world, and after the
     * blocks have been put back, because an item frame needs the wall it hangs
     * on to already exist.
     *
     * @return how many were put back.
     */
    fun restore(
        server: Server,
        world: World,
        entitiesInside: Collection<Entity>,
        saved: List<Captured>,
        logger: java.util.logging.Logger? = null,
    ): Int {
        entitiesInside.filter { isDecoration(it) }.forEach { runCatching { it.remove() } }

        var placed = 0
        for (item in saved) {
            if (runCatching { placeOne(server, world, item, logger) }.getOrDefault(false)) placed++
        }
        return placed
    }

    /**
     * Puts one decoration back, keeping the blocks that are there and taking out
     * anything that came along for the ride.
     */
    private fun placeOne(
        server: Server,
        world: World,
        item: Captured,
        logger: java.util.logging.Logger?,
    ): Boolean {
        val corner = Location(world, item.minX.toDouble(), item.minY.toDouble(), item.minZ.toDouble())
        val box = BoundingBox(
            item.minX.toDouble(), item.minY.toDouble(), item.minZ.toDouble(),
            (item.minX + item.sizeX).toDouble(),
            (item.minY + item.sizeY).toDouble(),
            (item.minZ + item.sizeZ).toDouble(),
        )
        val before: Set<UUID> = world.getNearbyEntities(box).map { it.uniqueId }.toSet()

        // The saved pocket holds blocks as well as the decoration, and those are
        // the chamber's own blocks, already put back correctly a moment ago. They
        // should come out identical, and any that do not are put straight back,
        // so nothing a reset just restored can be disturbed here.
        val blocksBefore = HashMap<Block, BlockData>()
        forEachCell(world, item) { block -> blocksBefore[block] = block.blockData.clone() }

        val structure = server.structureManager.loadStructure(ByteArrayInputStream(item.bytes))
        structure.place(corner, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, Random())

        for ((block, data) in blocksBefore) {
            if (block.blockData != data) block.setBlockData(data, false)
        }

        // Everything that turned up is new, because the pocket was emptied of
        // decorations first. The one that was asked for is the nearest of its
        // own kind; everything else came along for the ride and goes straight
        // back out, so nothing is duplicated and no mob is brought back.
        val arrived = world.getNearbyEntities(box).filter { it.uniqueId !in before }
        val wanted = arrived
            .filter { it.type.key.toString() == item.type }
            .filter { distanceTo(it, item) <= POSITION_TOLERANCE }
            .minByOrNull { distanceTo(it, item) }
        arrived.forEach { if (it !== wanted) runCatching { it.remove() } }

        if (wanted == null) {
            logger?.warning(
                "One decoration (${item.type}) could not be put back at " +
                    "${item.x.toInt()}, ${item.y.toInt()}, ${item.z.toInt()}" +
                    if (arrived.isEmpty()) "" else
                        " (what came back instead: ${arrived.joinToString { it.type.key.toString() }})"
            )
        }
        return wanted != null
    }

    /** How far [entity] landed from where [item] was recorded standing. */
    private fun distanceTo(entity: Entity, item: Captured): Double {
        val loc = entity.location
        return maxOf(
            abs(loc.x - item.x),
            abs(loc.y - item.y),
            abs(loc.z - item.z),
        )
    }

    /** Runs [action] for every block in the pocket [item] covers. */
    private inline fun forEachCell(world: World, item: Captured, action: (Block) -> Unit) {
        for (dx in 0 until item.sizeX) {
            for (dy in 0 until item.sizeY) {
                for (dz in 0 until item.sizeZ) {
                    action(world.getBlockAt(item.minX + dx, item.minY + dy, item.minZ + dz))
                }
            }
        }
    }
}

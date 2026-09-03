package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Container
import org.bukkit.block.CreatureSpawner
import org.bukkit.block.Sign
import org.bukkit.block.data.type.Slab
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BlockVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Checks whether this server can save one block, complete with everything the
 * block is holding, and put it back exactly as it was.
 *
 * ### Why this exists
 *
 * The plugin currently remembers the contents of a chamber by hand, type by
 * type: a chest this way, a sign that way, a decorated pot another way. That
 * list covers a fraction of the blocks that can hold anything, so a chamber
 * built out of anything unusual loses parts of itself every time it resets, and
 * the list falls further behind with each Minecraft release.
 *
 * The game already knows how to do this properly, through the same machinery
 * structure blocks use. Before rebuilding the plugin's saving on top of that, it
 * is worth confirming on a real server that it round-trips the things that
 * matter, rather than assuming it does and finding out later.
 *
 * Run with `/trial debug structure`. It works in a small patch of air well above
 * the world, puts each test block there in turn, saves it, wipes it, restores
 * it, then reports what came back. Everything it touches is cleared afterwards.
 */
object StructureRoundTripProbe {

    /** One thing that was checked, and whether it survived. */
    data class Result(val what: String, val passed: Boolean, val detail: String)

    /** Every cell the check writes to, so they can all be confirmed empty first. */
    private fun workingCells(origin: Location): List<Location> = buildList {
        for (dx in listOf(0, 2, 4, 6, 8)) add(origin.clone().add(dx.toDouble(), 0.0, 0.0))
        add(origin.clone().add(2.0, -1.0, 0.0)) // the sign's support block
    }

    /**
     * Runs the whole check at [origin]. Must be called on the thread that owns
     * that location.
     */
    fun run(plugin: BetterTrialChambers, origin: Location): List<Result> {
        val results = mutableListOf<Result>()
        val world = origin.world ?: return listOf(Result("world", false, "no world"))

        // Every cell this touches gets cleared to air on the way out, so refuse
        // outright unless the whole working area is already empty. Somebody with
        // a build up at this height near the world origin would otherwise lose
        // part of it to a diagnostic command, which is not a trade any check is
        // worth. Includes the cell under the sign, which gets a block to stand on.
        val occupied = workingCells(origin).filter { !it.block.type.isAir }
        if (occupied.isNotEmpty()) {
            val where = occupied.first()
            return listOf(
                Result(
                    "space to work in", false,
                    "there is a ${where.block.type} at ${where.blockX},${where.blockY},${where.blockZ}. " +
                        "This check needs empty air and will not build over anything.",
                ),
            )
        }

        results += checkChest(plugin, world, origin)
        results += checkSign(plugin, world, origin)
        results += checkSpawner(plugin, world, origin)
        results += checkBlockData(plugin, world, origin)
        results += checkEntities(plugin, world, origin)
        return results
    }

    /**
     * A chest with a named, enchanted item in a specific slot. Stands in for
     * every container: the plugin's own handling reads the inventory and nothing
     * else, so this also shows whether the container's other details survive.
     */
    private fun checkChest(plugin: BetterTrialChambers, world: World, origin: Location): Result {
        val at = origin.clone().add(0.0, 0.0, 0.0)
        return try {
            at.block.type = Material.CHEST
            // The name goes through the block state and needs update() to stick.
            (at.block.state as Container).apply {
                customName(net.kyori.adventure.text.Component.text("Prize Chest"))
                update(true, false)
            }
            // The item goes into the live inventory afterwards. Doing it the other
            // way round, then calling update(), writes the state's own snapshot
            // back over the block, and that snapshot was taken before the item
            // went in, so it quietly undoes it.
            (at.block.state as Container).inventory.setItem(
                4,
                ItemStack(Material.DIAMOND_SWORD).apply {
                    addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 4)
                },
            )

            val saved = save(plugin, at) ?: return Result("chest", false, "could not save")
            at.block.type = Material.AIR
            place(plugin, saved, at)

            val after = at.block.state as? Container
                ?: return Result("chest", false, "came back as ${at.block.type}")
            val item = after.inventory.getItem(4)
            val name = runCatching { after.customName() }.getOrNull()
            val ok = item?.type == Material.DIAMOND_SWORD &&
                item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS) == 4
            Result(
                "chest", ok,
                "slot 4=${item?.type} sharpness=${item?.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS)} " +
                    "name=${name?.let { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }}",
            )
        } catch (e: Exception) {
            Result("chest", false, "threw: ${e.message}")
        } finally {
            at.block.type = Material.AIR
        }
    }

    /** A sign with text on both sides, glow and a dye colour. */
    private fun checkSign(plugin: BetterTrialChambers, world: World, origin: Location): Result {
        val at = origin.clone().add(2.0, 0.0, 0.0)
        val support = at.clone().add(0.0, -1.0, 0.0)
        return try {
            // A standing sign needs something underneath or it drops the moment
            // it is placed, which is the world behaving normally and nothing to
            // do with whether saving it works.
            support.block.type = Material.STONE
            at.block.type = Material.OAK_SIGN
            (at.block.state as Sign).apply {
                getSide(org.bukkit.block.sign.Side.FRONT).apply {
                    line(0, net.kyori.adventure.text.Component.text("front one"))
                    isGlowingText = true
                    color = org.bukkit.DyeColor.LIME
                }
                getSide(org.bukkit.block.sign.Side.BACK)
                    .line(0, net.kyori.adventure.text.Component.text("back one"))
                update(true, false)
            }

            val saved = save(plugin, at) ?: return Result("sign", false, "could not save")
            at.block.type = Material.AIR
            place(plugin, saved, at)

            val after = at.block.state as? Sign ?: return Result("sign", false, "came back as ${at.block.type}")
            val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            val front = plain.serialize(after.getSide(org.bukkit.block.sign.Side.FRONT).line(0))
            val back = plain.serialize(after.getSide(org.bukkit.block.sign.Side.BACK).line(0))
            val glow = after.getSide(org.bukkit.block.sign.Side.FRONT).isGlowingText
            val colour = after.getSide(org.bukkit.block.sign.Side.FRONT).color
            Result(
                "sign", front == "front one" && back == "back one" && glow,
                "front='$front' back='$back' glowing=$glow colour=$colour",
            )
        } catch (e: Exception) {
            Result("sign", false, "threw: ${e.message}")
        } finally {
            at.block.type = Material.AIR
            support.block.type = Material.AIR
        }
    }

    /**
     * An ordinary mob spawner. This is one of the twenty-five kinds the plugin
     * does not currently save at all, so it is the clearest single example of
     * what the rewrite would gain.
     */
    private fun checkSpawner(plugin: BetterTrialChambers, world: World, origin: Location): Result {
        val at = origin.clone().add(4.0, 0.0, 0.0)
        return try {
            at.block.type = Material.SPAWNER
            (at.block.state as CreatureSpawner).apply {
                spawnedType = org.bukkit.entity.EntityType.BLAZE
                delay = 137
                update(true, false)
            }

            val saved = save(plugin, at) ?: return Result("mob spawner", false, "could not save")
            at.block.type = Material.AIR
            place(plugin, saved, at)

            val after = at.block.state as? CreatureSpawner
                ?: return Result("mob spawner", false, "came back as ${at.block.type}")
            Result(
                "mob spawner", after.spawnedType == org.bukkit.entity.EntityType.BLAZE,
                "mob=${after.spawnedType} delay=${after.delay}",
            )
        } catch (e: Exception) {
            Result("mob spawner", false, "threw: ${e.message}")
        } finally {
            at.block.type = Material.AIR
        }
    }

    /** A block with no contents, just a shape, to confirm plain blocks are fine too. */
    private fun checkBlockData(plugin: BetterTrialChambers, world: World, origin: Location): Result {
        val at = origin.clone().add(6.0, 0.0, 0.0)
        return try {
            at.block.type = Material.OAK_SLAB
            at.block.blockData = (at.block.blockData as Slab).apply { type = Slab.Type.TOP }

            val saved = save(plugin, at) ?: return Result("slab shape", false, "could not save")
            at.block.type = Material.AIR
            place(plugin, saved, at)

            val half = (at.block.blockData as? Slab)?.type
            Result("slab shape", half == Slab.Type.TOP, "half=$half")
        } catch (e: Exception) {
            Result("slab shape", false, "threw: ${e.message}")
        } finally {
            at.block.type = Material.AIR
        }
    }

    /**
     * Whether saving a region can carry the decorations standing in it, which is
     * the other half of what a chamber loses on reset today.
     */
    private fun checkEntities(plugin: BetterTrialChambers, world: World, origin: Location): Result {
        val at = origin.clone().add(8.0, 0.0, 0.0)
        var stand: org.bukkit.entity.ArmorStand? = null
        return try {
            stand = world.spawn(at.clone().add(0.5, 0.0, 0.5), org.bukkit.entity.ArmorStand::class.java) {
                it.isPersistent = true
                it.customName(net.kyori.adventure.text.Component.text("Statue"))
                it.isCustomNameVisible = true
            }

            val manager = plugin.server.structureManager
            val structure = manager.createStructure()
            structure.fill(at, at.clone().add(1.0, 1.0, 1.0), true)
            val saved = structure.entityCount

            stand.remove()
            stand = null

            val bytes = ByteArrayOutputStream()
            manager.saveStructure(bytes, structure)
            val reread = manager.loadStructure(ByteArrayInputStream(bytes.toByteArray()))
            reread.place(at, true, org.bukkit.block.structure.StructureRotation.NONE,
                org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, java.util.Random())

            val found = world.getNearbyEntities(at.clone().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0)
                .filterIsInstance<org.bukkit.entity.ArmorStand>()
            found.forEach { it.remove() }
            Result(
                "armour stand", saved > 0 && found.isNotEmpty(),
                "saved=$saved restored=${found.size}",
            )
        } catch (e: Exception) {
            Result("armour stand", false, "threw: ${e.message}")
        } finally {
            runCatching { stand?.remove() }
            at.block.type = Material.AIR
        }
    }

    /**
     * Saves the single block at [at] the way a structure block would, and hands
     * back the bytes. Going out to bytes and back in is deliberate: that is what
     * the real thing would store on disk, so anything lost in the writing shows
     * up here rather than later.
     */
    private fun save(plugin: BetterTrialChambers, at: Location): ByteArray? = runCatching {
        val manager = plugin.server.structureManager
        val structure = manager.createStructure()
        structure.fill(at, at.clone().add(1.0, 1.0, 1.0), false)
        ByteArrayOutputStream().also { manager.saveStructure(it, structure) }.toByteArray()
    }.getOrNull()

    private fun place(plugin: BetterTrialChambers, bytes: ByteArray, at: Location) {
        val manager = plugin.server.structureManager
        val structure = manager.loadStructure(ByteArrayInputStream(bytes))
        structure.place(
            at, false, org.bukkit.block.structure.StructureRotation.NONE,
            org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, java.util.Random(),
        )
    }

    /** Unused, but keeps the import honest if the probe grows a vector-based place. */
    @Suppress("unused")
    private fun zero() = BlockVector(0, 0, 0)
}

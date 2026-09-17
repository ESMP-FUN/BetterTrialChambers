package com.esmpfun.bettertrialchambers.utils

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag

/**
 * Decides whether a block is a safe place to put a player down.
 *
 * Used when a chamber resets and everyone inside has to be moved out. The
 * question being answered is always the same two-part one: is the block under
 * their feet something they can stand on, and is it something that will hurt
 * them the moment they land on it.
 *
 * ### Why this exists as its own file
 *
 * 26.3 added two vanilla block tags that answer exactly those two questions,
 * and the game now uses them itself (`/spreadplayers` picks its destinations
 * with `#entities_can_teleport_to`). Using the same tags means BTC agrees with
 * vanilla about what "safe" means, and picks up any future block Mojang adds
 * without anyone having to remember to edit a list here.
 *
 * - `#minecraft:dangerous_for_teleportation` - fire, soul fire, lava cauldron,
 *   campfire, soul campfire, cactus, magma block, sweet berry bush, wither rose,
 *   pointed dripstone, powder snow. Verified against the 26.3 Pre-Release 1
 *   server jar; it is BTC's old hand-written list almost exactly.
 * - `#minecraft:entities_can_teleport_to` - currently defined as
 *   `#minecraft:blocks_motion`, which is the game's own notion of "something
 *   with a collision box you can stand on top of".
 *
 * ### Falling back
 *
 * Both tags are **new in 26.3** and do not exist on 26.2 or earlier (confirmed
 * by diffing the two servers' tag folders). A missing tag is therefore expected
 * rather than an error, and each lookup falls back to the hand-written behaviour
 * this class replaced, so the same code is correct on either server.
 */
object TeleportSafety {

    /**
     * The hand-written hazard list BTC used before 26.3, kept as the fallback.
     *
     * `LAVA` is deliberately absent: lava is not a solid block, so the footing
     * check below rules it out before this list is ever consulted. The old list
     * carried it anyway, where it could never match.
     */
    private val FALLBACK_HAZARDS = setOf(
        Material.MAGMA_BLOCK, Material.POINTED_DRIPSTONE, Material.CACTUS,
        Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW,
        Material.LAVA_CAULDRON,
    )

    /**
     * Tags are resolved on first use rather than at class-load, because
     * [Bukkit.getTag] needs a running server. [runCatching] covers the unit-test
     * case where there is no server at all.
     */
    private val dangerousTag: Tag<Material>? by lazy { lookup("dangerous_for_teleportation") }
    private val canStandOnTag: Tag<Material>? by lazy { lookup("entities_can_teleport_to") }

    private fun lookup(tagName: String): Tag<Material>? = runCatching {
        Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(tagName), Material::class.java)
    }.getOrNull()

    /** True when landing on top of [material] would immediately hurt the player. */
    fun isDangerousToLandOn(material: Material): Boolean =
        dangerousTag?.isTagged(material) ?: (material in FALLBACK_HAZARDS)

    /** True when [material] has enough of a collision box to stand on. */
    fun isSolidFooting(material: Material): Boolean =
        canStandOnTag?.isTagged(material) ?: material.isSolid

    /**
     * True when a player can be safely dropped with their feet at the given
     * three blocks: solid footing below, and two open non-liquid cells for the
     * body and head.
     */
    fun isSafeStandingSpot(below: Material, feetPassable: Boolean, feetLiquid: Boolean, headPassable: Boolean, headLiquid: Boolean): Boolean =
        isSolidFooting(below) && !isDangerousToLandOn(below) &&
            feetPassable && !feetLiquid && headPassable && !headLiquid

    /**
     * Whether this server exposes the 26.3 teleport-safety tags. Logged once at
     * startup so a support thread can tell at a glance which path was taken.
     */
    fun usingVanillaTags(): Boolean = dangerousTag != null && canStandOnTag != null
}

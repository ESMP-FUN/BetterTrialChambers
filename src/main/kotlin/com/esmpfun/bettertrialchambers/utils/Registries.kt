package com.esmpfun.bettertrialchambers.utils

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.MusicInstrument
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionEffectType

/**
 * One place to look things up by name in the game's registries.
 *
 * Minecraft keeps its content (sounds, potion effects, enchantments and so on)
 * in registries that a datapack can add to, which is why these are looked up by
 * name at runtime rather than written into the code as fixed constants.
 *
 * Everything here goes through Paper's `RegistryAccess`, which is the current
 * way to reach those registries. The older `org.bukkit.Registry.SOMETHING`
 * constants still work today but are the previous generation of the same API,
 * and having both spread across the codebase made it hard to tell which sites
 * had already been looked at. Every lookup returns `null` rather than throwing
 * when the name does not exist, because these names usually come from a config
 * file a server owner edits by hand, and a typo there should produce a clear
 * warning rather than break the feature.
 */
object Registries {

    fun sound(key: NamespacedKey): Sound? =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(key)

    fun potionEffect(key: NamespacedKey): PotionEffectType? =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key)

    /** Convenience for the vanilla effects BTC names directly, e.g. `"bad_omen"`. */
    fun potionEffect(vanillaName: String): PotionEffectType? =
        potionEffect(NamespacedKey.minecraft(vanillaName))

    fun enchantment(key: NamespacedKey): Enchantment? =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key)

    fun instrument(key: NamespacedKey): MusicInstrument? =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT).get(key)

    // Banner patterns deliberately are not here: NBTUtil needs to go both ways
    // (name to pattern when restoring, pattern to name when capturing), so it
    // holds the whole registry rather than a one-way lookup.
}

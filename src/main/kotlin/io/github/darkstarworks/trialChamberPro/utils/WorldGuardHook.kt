package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Optional WorldGuard integration (v1.5.9). When `protection.worldguard-integration`
 * is on and WorldGuard is installed, TCP **respects WG regions**: if a WG region
 * covers the spot and grants the player build rights (region membership, an
 * explicit `build` flag, or WG bypass), TCP skips its own chamber protection so
 * region owners/staff can work inside chambers that overlap their regions.
 *
 * All WorldGuard/WorldEdit classes are referenced only inside method bodies and
 * are reached only after [isAvailable] — so the class loads fine on servers
 * without WorldGuard (the API is a `compileOnly` dependency).
 */
object WorldGuardHook {

    /** Whether WorldGuard is installed and enabled. */
    fun isAvailable(plugin: Plugin): Boolean {
        val wg = plugin.server.pluginManager.getPlugin("WorldGuard") ?: return false
        return wg.isEnabled
    }

    /**
     * True when TCP should DEFER to WorldGuard at [location] for [player] — i.e.
     * a WG region covers the point and grants this player build rights (or they
     * hold WG bypass). When true, the caller skips TCP's own protection.
     *
     * Returns false when WG is absent/errors, there is no region at the point,
     * or the player has no build rights there — so TCP protection applies as
     * normal. **Only call after [isAvailable] returns true.**
     */
    fun grantsBuild(player: Player, location: Location): Boolean {
        val world = location.world ?: return false
        return try {
            val localPlayer = com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player)
            val platform = com.sk89q.worldguard.WorldGuard.getInstance().platform
            val weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world)
            // Staff with WG bypass build anywhere.
            if (platform.sessionManager.hasBypass(localPlayer, weWorld)) return true
            val weLoc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location)
            val query = platform.regionContainer.createQuery()
            // No WG region here → don't disable TCP protection at this spot.
            if (query.getApplicableRegions(weLoc).size() == 0) return false
            // A region covers it: defer only if WG would let this player build
            // (membership or an explicit allow on the BUILD flag).
            query.testBuild(weLoc, localPlayer, com.sk89q.worldguard.protection.flags.Flags.BUILD)
        } catch (_: Throwable) {
            false
        }
    }
}

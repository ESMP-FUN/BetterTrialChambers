package com.esmpfun.bettertrialchambers.integrations.claims

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.Chamber
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * GriefPrevention integration. Guards `ClaimCreatedEvent` (new claim) and `ClaimResizeEvent`
 * (footprint grow/shrink). The claimed area is the `Claim` from `getTo()` (resize) or
 * `getClaim()` (create), via `getLesserBoundaryCorner()` / `getGreaterBoundaryCorner()`.
 * The actor is `getCreator()` (create) or `getModifier()` (resize); a non-player (console)
 * source is left alone. `ClaimExtendEvent` (vertical auto-extend) is intentionally not
 * guarded — it isn't a territorial claim of new ground.
 */
class GriefPreventionClaimProvider : ClaimProvider {
    override val id = "griefprevention"
    override val pluginName = "GriefPrevention"
    override val configKey = "protection.griefprevention-integration"
    override val bypassPermission = "btc.bypass.griefprevention"
    override val eventClassNames = listOf(
        "me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent",
        "me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent",
    )

    override fun parseAttempt(event: Any): ClaimAttempt? {
        // Resize exposes the new claim via getTo(); create via getClaim().
        val claim = Refl.call(event, "getTo") ?: Refl.call(event, "getClaim") ?: return null
        val lesser = Refl.call(claim, "getLesserBoundaryCorner") as? Location ?: return null
        val greater = Refl.call(claim, "getGreaterBoundaryCorner") as? Location ?: return null
        val worldName = lesser.world?.name ?: greater.world?.name ?: return null
        val actor = (Refl.call(event, "getCreator") ?: Refl.call(event, "getModifier")) as? Player
        return ClaimAttempt(
            worldName,
            lesser.blockX, lesser.blockY, lesser.blockZ,
            greater.blockX, greater.blockY, greater.blockZ,
            actor,
        )
    }

    override fun findConflicts(plugin: BetterTrialChambers, chambers: List<Chamber>): Map<Chamber, List<String>> {
        val claims = Refl.call(dataStore(), "getClaims") as? Collection<*> ?: return emptyMap()
        val result = linkedMapOf<Chamber, MutableSet<String>>()
        // Walk every claim once, testing it against every chamber.
        for (claim in claims) {
            claim ?: continue
            val lesser = Refl.call(claim, "getLesserBoundaryCorner") as? Location ?: continue
            val greater = Refl.call(claim, "getGreaterBoundaryCorner") as? Location ?: continue
            val worldName = lesser.world?.name ?: continue
            val owner = Refl.call(claim, "getOwnerName") as? String ?: "unknown"
            for (chamber in chambers) {
                if (chamber.intersects(
                        worldName,
                        lesser.blockX, lesser.blockY, lesser.blockZ,
                        greater.blockX, greater.blockY, greater.blockZ,
                    )
                ) {
                    result.getOrPut(chamber) { linkedSetOf() } += owner
                }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    private fun dataStore(): Any? = try {
        val instance = Refl.classOrNull("me.ryanhamshire.GriefPrevention.GriefPrevention")
            ?.getField("instance")?.get(null)
        Refl.call(instance, "getDataStore") ?: instance?.javaClass?.getField("dataStore")?.get(instance)
    } catch (_: Throwable) {
        null
    }
}

package io.github.darkstarworks.trialChamberPro.integrations.claims

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Residence integration. Guards `ResidenceCreationEvent` / `ResidenceAreaAddEvent` /
 * `ResidenceSubzoneCreationEvent` — each exposes `getPlayer()` and a `getPhysicalArea()`
 * `CuboidArea` (`getLowLocation()` / `getHighLocation()`).
 */
class ResidenceClaimProvider : ClaimProvider {
    override val id = "residence"
    override val pluginName = "Residence"
    override val configKey = "protection.residence-integration"
    override val bypassPermission = "tcp.bypass.residence"
    override val eventClassNames = listOf(
        "com.bekvon.bukkit.residence.event.ResidenceCreationEvent",
        "com.bekvon.bukkit.residence.event.ResidenceAreaAddEvent",
        "com.bekvon.bukkit.residence.event.ResidenceSubzoneCreationEvent",
    )

    override fun parseAttempt(event: Any): ClaimAttempt? {
        val area = Refl.call(event, "getPhysicalArea") ?: return null
        val low = Refl.call(area, "getLowLocation") as? Location ?: return null
        val high = Refl.call(area, "getHighLocation") as? Location ?: return null
        val worldName = (Refl.call(area, "getWorld") as? World)?.name ?: low.world?.name ?: return null
        val actor = Refl.call(event, "getPlayer") as? Player
        return ClaimAttempt(
            worldName,
            low.blockX, low.blockY, low.blockZ,
            high.blockX, high.blockY, high.blockZ,
            actor,
        )
    }

    override fun findConflicts(plugin: TrialChamberPro, chambers: List<Chamber>): Map<Chamber, List<String>> {
        val manager = Refl.call(residenceInstance(), "getResidenceManager") ?: return emptyMap()
        val byLoc = try {
            manager.javaClass.getMethod("getByLoc", Location::class.java)
        } catch (_: Throwable) {
            return emptyMap()
        }
        val result = linkedMapOf<Chamber, List<String>>()
        for (chamber in chambers) {
            val world = chamber.getWorld() ?: continue
            // Best-effort: sample on a 16-block grid across the chamber box, always including
            // the far edges so a residence clipping the max corner isn't missed.
            val names = linkedSetOf<String>()
            for (x in gridSamples(chamber.minX, chamber.maxX)) {
                for (z in gridSamples(chamber.minZ, chamber.maxZ)) {
                    for (y in gridSamples(chamber.minY, chamber.maxY)) {
                        val res = try {
                            byLoc.invoke(manager, Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
                        } catch (_: Throwable) {
                            null
                        }
                        if (res != null) (Refl.call(res, "getName") as? String)?.let { names += it }
                    }
                }
            }
            if (names.isNotEmpty()) result[chamber] = names.toList()
        }
        return result
    }

    /** Sample points from [min]..[max] stepping by 16, always including [max] itself. */
    private fun gridSamples(min: Int, max: Int): List<Int> {
        val points = (min..max step 16).toMutableList()
        if (points.isEmpty() || points.last() != max) points += max
        return points
    }

    private fun residenceInstance(): Any? = try {
        Refl.classOrNull("com.bekvon.bukkit.residence.Residence")?.getMethod("getInstance")?.invoke(null)
    } catch (_: Throwable) {
        null
    }
}

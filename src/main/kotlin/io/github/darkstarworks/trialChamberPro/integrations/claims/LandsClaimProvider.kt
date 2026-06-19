package io.github.darkstarworks.trialChamberPro.integrations.claims

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Lands integration. Lands is chunk-based: `ChunkPreClaimEvent` exposes `getX()`/`getZ()`
 * (chunk coords), `getWorldName()`, and `getPlayerUUID()` (there is no `getPlayer()`).
 * A claimed chunk is treated as a full-height 16×16 column, so a chunk that overlaps a
 * chamber's X/Z footprint is denied.
 */
class LandsClaimProvider : ClaimProvider {
    override val id = "lands"
    override val pluginName = "Lands"
    override val configKey = "protection.lands-integration"
    override val bypassPermission = "tcp.bypass.lands"
    override val eventClassNames = listOf("me.angeschossen.lands.api.events.ChunkPreClaimEvent")

    override fun parseAttempt(event: Any): ClaimAttempt? {
        val cx = Refl.call(event, "getX") as? Int ?: return null
        val cz = Refl.call(event, "getZ") as? Int ?: return null
        val worldName = Refl.call(event, "getWorldName") as? String ?: return null
        val actor = (Refl.call(event, "getPlayerUUID") as? UUID)?.let { Bukkit.getPlayer(it) }
        return ClaimAttempt(
            worldName,
            cx shl 4, FULL_HEIGHT_MIN, cz shl 4,
            (cx shl 4) + 15, FULL_HEIGHT_MAX, (cz shl 4) + 15,
            actor,
        )
    }

    override fun findConflicts(plugin: TrialChamberPro, chambers: List<Chamber>): Map<Chamber, List<String>> {
        val integration = landsIntegration(plugin) ?: return emptyMap()
        val byChunk = try {
            integration.javaClass.getMethod(
                "getLandByChunk", World::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
        } catch (_: Throwable) {
            return emptyMap()
        }
        val result = linkedMapOf<Chamber, List<String>>()
        for (chamber in chambers) {
            val world = chamber.getWorld() ?: continue
            val names = linkedSetOf<String>()
            for (cx in (chamber.minX shr 4)..(chamber.maxX shr 4)) {
                for (cz in (chamber.minZ shr 4)..(chamber.maxZ shr 4)) {
                    val land = try {
                        byChunk.invoke(integration, world, cx, cz)
                    } catch (_: Throwable) {
                        null
                    }
                    if (land != null) (Refl.call(land, "getName") as? String)?.let { names += it }
                }
            }
            if (names.isNotEmpty()) result[chamber] = names.toList()
        }
        return result
    }

    private fun landsIntegration(plugin: TrialChamberPro): Any? = try {
        Refl.classOrNull("me.angeschossen.lands.api.LandsIntegration")
            ?.getMethod("of", Plugin::class.java)?.invoke(null, plugin)
    } catch (_: Throwable) {
        null
    }

    companion object {
        // Chunk claims span the whole column; bounds beyond any world height so the
        // overlap test reduces to the X/Z footprint.
        private const val FULL_HEIGHT_MIN = -2032
        private const val FULL_HEIGHT_MAX = 2032
    }
}

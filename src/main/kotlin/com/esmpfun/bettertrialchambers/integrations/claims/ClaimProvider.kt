package com.esmpfun.bettertrialchambers.integrations.claims

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.Chamber
import org.bukkit.entity.Player

/**
 * A normalized "a player is trying to claim this box" extracted from one of a land-claim
 * plugin's events. Coordinates are inclusive block coords; [actor] is null when the source
 * isn't a player (e.g. an admin console claim), in which case enforcement is skipped.
 */
data class ClaimAttempt(
    val worldName: String,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
    val actor: Player?,
)

/**
 * One land-claim plugin integration (Residence / Lands / GriefPrevention).
 *
 * Implementations talk to their plugin **purely via reflection**, so TCP carries no
 * compile-time dependency on, or version pin to, any of them. The integration binds
 * to whatever version the server actually runs; if a method is missing the call simply
 * yields null and that event/scan is skipped rather than throwing.
 */
interface ClaimProvider {
    /** Stable short id, e.g. `"residence"`. */
    val id: String
    /** Bukkit plugin name used for the availability check, e.g. `"Residence"`. */
    val pluginName: String
    /** config.yml key gating this integration, e.g. `"protection.residence-integration"`. */
    val configKey: String
    /** Permission that exempts a player, e.g. `"btc.bypass.residence"`. */
    val bypassPermission: String
    /** Fully-qualified names of the cancellable claim-create/expand events to guard. */
    val eventClassNames: List<String>

    fun isAvailable(plugin: BetterTrialChambers): Boolean =
        plugin.server.pluginManager.getPlugin(pluginName)?.isEnabled == true

    /** Reflectively normalize a guarded event into a [ClaimAttempt], or null to ignore it. */
    fun parseAttempt(event: Any): ClaimAttempt?

    /**
     * Best-effort: for each chamber overlapped by an existing claim, the descriptions of the
     * overlapping claim(s). Bulk by design so a provider can walk its claim set **once**
     * (important on servers with very many claims) rather than per chamber. Chambers with no
     * conflict are simply absent from the result.
     */
    fun findConflicts(plugin: BetterTrialChambers, chambers: List<Chamber>): Map<Chamber, List<String>>
}

/** Reflection helpers shared by the providers, every call is null-safe. */
internal object Refl {
    /** Invoke a no-arg public method [name] on [target], or null on any failure. */
    fun call(target: Any?, name: String): Any? = try {
        target?.let { reachable(it.javaClass, name)?.invoke(it) }
    } catch (_: Throwable) {
        null
    }

    /**
     * A no-arg [name] method on [type] that can actually be invoked.
     *
     * These plugins hand back objects typed as a public API interface whose
     * implementing class is not itself public. Asking the object's own class for
     * the method finds it, and then invoking it is refused, so the integration
     * quietly did nothing on exactly the plugins it was written for. Looking for
     * the same method on a public interface or parent class first is what makes
     * the call go through.
     */
    private fun reachable(type: Class<*>, name: String): java.lang.reflect.Method? {
        val direct = try { type.getMethod(name) } catch (_: Throwable) { return null }
        if (java.lang.reflect.Modifier.isPublic(direct.declaringClass.modifiers)) return direct

        var c: Class<*>? = type
        while (c != null) {
            for (iface in c.interfaces) {
                if (!java.lang.reflect.Modifier.isPublic(iface.modifiers)) continue
                val m = try { iface.getMethod(name) } catch (_: Throwable) { null }
                if (m != null) return m
            }
            if (java.lang.reflect.Modifier.isPublic(c.modifiers)) {
                val m = try { c.getMethod(name) } catch (_: Throwable) { null }
                if (m != null && java.lang.reflect.Modifier.isPublic(m.declaringClass.modifiers)) return m
            }
            c = c.superclass
        }
        // Nothing public declares it; ask for access to the one we found.
        return try { direct.apply { isAccessible = true } } catch (_: Throwable) { null }
    }

    /** Resolve a class by name, or null if it isn't on the runtime classpath. */
    fun classOrNull(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Throwable) {
        null
    }
}

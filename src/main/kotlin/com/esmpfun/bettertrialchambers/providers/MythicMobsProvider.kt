package com.esmpfun.bettertrialchambers.providers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import java.util.Optional

/**
 * MythicMobs integration via reflection. No compile-time dependency, all calls
 * are resolved against the live MythicBukkit singleton so TCP can load on
 * servers that don't have MythicMobs installed.
 *
 * API path (MythicMobs 5.x):
 *   MythicBukkit.inst().mobManager.spawnMob(mobId: String, location: Location, level: Double)
 *     -> io.lumine.mythic.api.mobs.entities.ActiveMob
 *   ActiveMob.entity.bukkitEntity -> org.bukkit.entity.Entity
 *
 * Fallback path if `mobManager` lookup shape differs across versions is wrapped
 * in try/catch, provider degrades to "unavailable" rather than crashing.
 */
class MythicMobsProvider(private val plugin: BetterTrialChambers) : TrialMobProvider {

    override val id: String = "mythicmobs"
    override val displayName: String = "MythicMobs"

    /** Whether this plugin's classes are on the server; fixed for the run. */
    @Volatile private var classPresent: Boolean? = null

    override fun isAvailable(): Boolean {
        // Asked of the plugin manager every time on purpose: it is a map lookup,
        // and caching the answer meant a plugin that starts after this one was
        // written off for the rest of the server's run. Only whether its classes
        // are there is remembered, which cannot change while the server is up.
        if (Bukkit.getPluginManager().getPlugin("MythicMobs")?.isEnabled != true) return false
        classPresent?.let { return it }
        return try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            classPresent = true
            true
        } catch (_: Throwable) {
            classPresent = false
            false
        }
    }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        if (!isAvailable()) return null
        return try {
            val mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val inst = mythicBukkitCls.getMethod("inst").invoke(null)
            val mobManager = mythicBukkitCls.getMethod("getMobManager").invoke(inst)

            // Signature: spawnMob(String, Location, double)
            val spawnMethod = com.esmpfun.bettertrialchambers.utils.Reflect.method(
                mobManager.javaClass,
                "spawnMob",
                String::class.java,
                Location::class.java,
                java.lang.Double.TYPE
            ) ?: return null
            val activeMob = spawnMethod.invoke(mobManager, mobId, location, 1.0) ?: return null

            // ActiveMob may return Optional in some minor versions; handle both.
            val resolved: Any = if (activeMob is Optional<*>) activeMob.orElse(null) ?: return null else activeMob
            val entityWrapper = com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(resolved, "getEntity") ?: return null
            val bukkit = com.esmpfun.bettertrialchambers.utils.Reflect.callNoArg(entityWrapper, "getBukkitEntity") as? Entity

            if (bukkit == null && plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[MythicMobsProvider] spawnMob '$mobId' returned entity wrapper with no Bukkit entity")
            }
            bukkit
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[MythicMobsProvider] Failed to spawn '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean {
        if (mobId.isBlank()) return false
        if (!isAvailable()) return true // optimistic, can't validate without API
        return try {
            val mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val inst = mythicBukkitCls.getMethod("inst").invoke(null)
            val mobManager = mythicBukkitCls.getMethod("getMobManager").invoke(inst)
            // getMythicMob(String) typically returns Optional<MythicMob>
            val getMethod = com.esmpfun.bettertrialchambers.utils.Reflect.method(mobManager.javaClass, "getMythicMob", String::class.java) ?: return true
            val result = getMethod.invoke(mobManager, mobId)
            when (result) {
                is Optional<*> -> result.isPresent
                null -> false
                else -> true
            }
        } catch (_: Throwable) {
            true // if reflection shape changes, don't falsely reject the id
        }
    }
}

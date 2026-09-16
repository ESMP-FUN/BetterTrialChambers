package com.esmpfun.bettertrialchambers.providers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * EliteMobs integration via reflection (v1.3.0).
 *
 * Mob id is an EliteMobs custom-boss filename (e.g. `"the_warden"` or
 * `"the_warden.yml"`). EliteMobs stores bosses as yml configs under
 * `plugins/EliteMobs/custombosses/`.
 *
 * Upstream API (verified against magmaguy/EliteMobs):
 *   class CustomBossEntity(String fileName)
 *     fun spawn(Location loc, int level, boolean silent)   // void
 *     fun getLivingEntity(): LivingEntity
 */
class EliteMobsProvider(private val plugin: BetterTrialChambers) : TrialMobProvider {

    override val id: String = "elitemobs"
    override val displayName: String = "EliteMobs"

    /** Whether this plugin's classes are on the server; fixed for the run. */
    @Volatile private var classPresent: Boolean? = null

    override fun isAvailable(): Boolean {
        // Asked of the plugin manager every time on purpose: it is a map lookup,
        // and caching the answer meant a plugin that starts after this one was
        // written off for the rest of the server's run. Only whether its classes
        // are there is remembered, which cannot change while the server is up.
        if (Bukkit.getPluginManager().getPlugin("EliteMobs")?.isEnabled != true) return false
        classPresent?.let { return it }
        return try {
            Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity")
            classPresent = true
            true
        } catch (_: Throwable) {
            classPresent = false
            false
        }
    }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        if (!isAvailable()) return null
        val filename = if (mobId.endsWith(".yml", ignoreCase = true)) mobId else "$mobId.yml"
        return try {
            val cls = Class.forName("com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity")
            val ctor = cls.getConstructor(String::class.java)
            val inst = ctor.newInstance(filename)

            val spawn = cls.getMethod(
                "spawn",
                Location::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            spawn.invoke(inst, location, 0, true)

            cls.getMethod("getLivingEntity").invoke(inst) as? Entity
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[EliteMobsProvider] Failed to spawn '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}

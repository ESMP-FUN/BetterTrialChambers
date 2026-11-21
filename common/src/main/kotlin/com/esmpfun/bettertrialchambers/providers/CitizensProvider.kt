package com.esmpfun.bettertrialchambers.providers

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.utils.Reflect
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Citizens integration via reflection (v1.3.0).
 *
 * Mob id is either a numeric Citizens NPC id (preferred, stable across
 * renames) or an exact NPC name. The provider looks the NPC up in the
 * default registry, `clone()`s it so the template NPC isn't moved, marks
 * the clone non-persistent (`should-save = false`) so it never bloats
 * `Citizens/saves.yml`, spawns it at [location], and destroys the clone
 * once its entity dies.
 */
class CitizensProvider(private val plugin: BetterTrialChambers) : TrialMobProvider, Listener {

    override val id: String = "citizens"
    override val displayName: String = "Citizens"

    /** Whether this plugin's classes are on the server; fixed for the run. */
    @Volatile private var classPresent: Boolean? = null

    /** Cloned NPCs spawned for waves (entity UUID -> Citizens NPC), destroyed on death. */
    private val spawnedClones = ConcurrentHashMap<UUID, Any>()

    init {
        plugin.scheduler.runTask(Runnable {
            if (plugin.isEnabled) {
                plugin.server.pluginManager.registerEvents(this, plugin)
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onCloneDeath(event: EntityDeathEvent) {
        val npc = spawnedClones.remove(event.entity.uniqueId) ?: return
        plugin.scheduler.runTaskLater(Runnable {
            runCatching { Reflect.callNoArg(npc, "destroy") }
        }, 1L)
    }

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Citizens")?.isEnabled != true) return false
        classPresent?.let { return it }
        return try {
            Class.forName("net.citizensnpcs.api.CitizensAPI")
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
            val apiCls = Class.forName("net.citizensnpcs.api.CitizensAPI")
            val registry = apiCls.getMethod("getNPCRegistry").invoke(null) ?: return null

            val template: Any = run<Any?> {
                val asInt = mobId.toIntOrNull()
                if (asInt != null) {
                    Reflect.method(registry.javaClass, "getById", java.lang.Integer.TYPE)
                        ?.invoke(registry, asInt)
                } else {
                    val it = Reflect.callNoArg(registry, "iterator") as? Iterator<*> ?: return@run null
                    var found: Any? = null
                    while (it.hasNext()) {
                        val npc = it.next() ?: continue
                        val name = Reflect.callNoArg(npc, "getName") as? String
                        if (name?.equals(mobId, ignoreCase = true) == true) { found = npc; break }
                    }
                    found
                }
            } ?: return null

            val clone = Reflect.callNoArg(template, "clone") ?: return null
            // Keep transient wave clones out of Citizens/saves.yml so waves never leak disk state.
            runCatching {
                val data = Reflect.callNoArg(clone, "data")
                if (data != null) {
                    Reflect.method(data.javaClass, "set", String::class.java, Any::class.java)
                        ?.invoke(data, "should-save", false)
                }
            }
            val spawn = Reflect.method(clone.javaClass, "spawn", Location::class.java) ?: return null
            spawn.invoke(clone, location)
            val entity = Reflect.callNoArg(clone, "getEntity") as? Entity
            if (entity != null) {
                spawnedClones[entity.uniqueId] = clone
                // Prune any dead/removed entries whose death event didn't fire (e.g. chamber reset removal).
                if (spawnedClones.size > 64) {
                    spawnedClones.entries.removeIf { (uid, npc) ->
                        val alive = Bukkit.getEntity(uid)?.isValid == true
                        if (!alive) runCatching { Reflect.callNoArg(npc, "destroy") }
                        !alive
                    }
                }
            } else {
                runCatching { Reflect.callNoArg(clone, "destroy") }
            }
            entity
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[CitizensProvider] Failed to spawn NPC '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}

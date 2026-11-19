package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.scheduler.SchedulerAdapter
import kotlinx.coroutines.CoroutineDispatcher
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatcher that runs on Bukkit's main thread (Paper)
 * or the global region thread (Folia).
 *
 * Required for operations that must execute on the main/region thread.
 */
fun Plugin.minecraftDispatcher(scheduler: SchedulerAdapter): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!scheduler.isFolia && Bukkit.isPrimaryThread()) {
                block.run()
            } else {
                scheduler.runTask(block)
            }
        }
    }
}

/**
 * Coroutine dispatcher that runs on the region thread owning a specific location.
 * On Paper, this is the main thread.
 * On Folia, this is the region thread for that location.
 *
 * Use this when modifying blocks or accessing location-specific world data.
 */
fun Plugin.locationDispatcher(scheduler: SchedulerAdapter, location: Location): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduler.runAtLocation(location, block)
        }
    }
}

/**
 * Coroutine dispatcher that runs on the region thread owning a specific entity.
 * On Paper, this is the main thread.
 * On Folia, this is the region thread for that entity.
 *
 * Use this when modifying entity state, inventory, or teleporting.
 */
fun Plugin.entityDispatcher(scheduler: SchedulerAdapter, entity: Entity): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduler.runAtEntity(entity, block)
        }
    }
}

/**
 * Legacy dispatcher, kept only so nothing outside this plugin breaks.
 *
 * **Do not use it.** It hands work to the server's own scheduler, and Folia
 * refuses that call outright, so anything routed through this simply throws
 * there. It had one caller left, pasting a schematic, which meant that whole
 * feature was broken on Folia rather than merely running in the wrong place.
 *
 * Use one of the three above instead, picked by what the work touches:
 * [locationDispatcher] for blocks, [entityDispatcher] for a player or mob, and
 * [minecraftDispatcher] taking a scheduler for anything global.
 */
@Deprecated(
    message = "Throws on Folia. Use locationDispatcher / entityDispatcher / minecraftDispatcher(scheduler).",
    level = DeprecationLevel.WARNING,
)
val Plugin.minecraftDispatcher: CoroutineDispatcher
    get() = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (server.isPrimaryThread) {
                block.run()
            } else {
                server.scheduler.runTask(this@minecraftDispatcher, block)
            }
        }
    }

package com.esmpfun.bettertrialchambers.adapter.v26_3

import com.esmpfun.bettertrialchambers.adapter.ServerAdapter
import io.papermc.paper.event.entity.EntityBreakByEntityEvent
import io.papermc.paper.event.entity.EntityBreakEvent
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/** 26.3: cushions, which are taken through Paper's own break event rather than a hanging one. */
open class Adapter26_3 : ServerAdapter {
    override val versions = "26.3"

    override fun registerListeners(plugin: Plugin, denyDecorationRemoval: (Entity, Entity?) -> Boolean) {
        plugin.server.pluginManager.registerEvents(CushionBreakListener(denyDecorationRemoval), plugin)
    }
}

/**
 * A cushion is not hung on a wall, so the hanging break handler never sees one. The by-entity
 * event shares this handler list, so it is read as a subclass here rather than listened to twice.
 */
class CushionBreakListener(private val deny: (Entity, Entity?) -> Boolean) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityBreak(event: EntityBreakEvent) {
        val remover = (event as? EntityBreakByEntityEvent)?.remover
        if (deny(event.entity, remover)) event.isCancelled = true
    }
}

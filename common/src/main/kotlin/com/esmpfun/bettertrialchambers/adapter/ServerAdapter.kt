package com.esmpfun.bettertrialchambers.adapter

import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/** What one group of Minecraft versions does differently. Picked by [ServerAdapters]. */
interface ServerAdapter {
    /** The versions this adapter is for, as a server owner reads them, e.g. "26.3". */
    val versions: String

    /**
     * Registers the listeners only these versions can have. [denyDecorationRemoval] answers
     * whether taking a decoration entity (by [Entity] remover, or null) must be stopped.
     */
    fun registerListeners(plugin: Plugin, denyDecorationRemoval: (Entity, Entity?) -> Boolean) {}
}

/** 1.21 to 26.2: everything lives in the shared code. */
class BaseAdapter : ServerAdapter {
    override val versions = "1.21 to 26.2"
}

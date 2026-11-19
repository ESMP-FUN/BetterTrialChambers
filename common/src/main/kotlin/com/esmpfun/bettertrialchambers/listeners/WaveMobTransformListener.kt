package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.EntityTransformEvent.TransformReason

/**
 * Keeps a wave mob the mob it was spawned as. A zombie drowning into a drowned, or (26.4+)
 * freezing into a frostbite in powder snow, comes back as a new entity the wave has never
 * seen, so the boss bar would wait for a mob that no longer exists. Slimes splitting is part
 * of the fight and is left alone.
 */
class WaveMobTransformListener(private val plugin: BetterTrialChambers) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTransform(event: EntityTransformEvent) {
        if (event.transformReason == TransformReason.SPLIT) return
        if (!plugin.spawnerWaveManager.isWaveMob(event.entity)) return
        event.isCancelled = true
    }
}

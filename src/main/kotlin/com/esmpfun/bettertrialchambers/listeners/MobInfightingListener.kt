package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bukkit.entity.Entity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent

/**
 * Stops trial-spawner wave mobs from fighting **each other**.
 *
 * Vanilla behaviour: a skeleton's arrow that clips another mob makes that mob
 * retaliate, and the wave dissolves into mob-vs-mob brawls (skeleton 1v1s,
 * 3v3s, …). In a Trial Chamber that's not just silly to watch — those deaths
 * still count toward wave/chamber completion, so a wave can clear itself with
 * the player doing nothing (which also corrupts TCP-MythicTrials tier progress,
 * since "clearing" is supposed to mean the player did the work).
 *
 * The fix is deliberately narrow: it only acts when **both** the attacker and
 * the victim are currently-tracked wave mobs (see [com.esmpfun.bettertrialchambers.managers.SpawnerWaveManager.isWaveMob]).
 * Player-vs-mob and mob-vs-player combat is never touched, and non-wave mobs
 * (wild animals, other plugins' entities) are left entirely alone.
 *
 * Two hooks, because suppressing the damage alone isn't enough — a mob can also
 * acquire another wave mob as its AI target through other paths:
 *  - [onWaveMobDamage] cancels the friendly-fire hit (arrow, melee, splash).
 *  - [onWaveMobTarget] stops a wave mob from locking onto another wave mob.
 *
 * Gated by `spawner-waves.prevent-infighting` (default true).
 */
class MobInfightingListener(private val plugin: BetterTrialChambers) : Listener {

    private fun enabled(): Boolean =
        plugin.isReady && plugin.config.getBoolean("spawner-waves.prevent-infighting", true)

    /** Resolve the living source of a hit — the shooter behind a projectile, or the direct attacker. */
    private fun sourceOf(damager: Entity): Entity? =
        if (damager is Projectile) damager.shooter as? Entity else damager

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onWaveMobDamage(event: EntityDamageByEntityEvent) {
        if (!enabled()) return
        val victim = event.entity
        val attacker = sourceOf(event.damager) ?: return
        if (attacker == victim) return
        // Only suppress friendly fire strictly between two tracked wave mobs.
        if (plugin.spawnerWaveManager.isWaveMob(attacker) &&
            plugin.spawnerWaveManager.isWaveMob(victim)
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onWaveMobTarget(event: EntityTargetLivingEntityEvent) {
        if (!enabled()) return
        val target = event.target ?: return
        if (plugin.spawnerWaveManager.isWaveMob(event.entity) &&
            plugin.spawnerWaveManager.isWaveMob(target)
        ) {
            // Cancel rather than null the target. Per EntityTargetEvent's contract,
            // cancelling makes the mob KEEP its original target, whereas setTarget(null)
            // resets it to target-less (which would just let it re-acquire the wave mob
            // next AI tick). Cancelling keeps a skeleton locked on the player it was
            // already aiming at.
            event.isCancelled = true
        }
    }
}

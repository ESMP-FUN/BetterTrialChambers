package io.github.darkstarworks.trialChamberPro.integrations

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.integrations.claims.Refl
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional AdvancedEnchantments integration (v1.5.18).
 *
 * AE custom enchants such as "Blast Mining" break blocks through their own effect path, which
 * ignores TCP's `BlockBreakEvent` cancel — so they could bypass chamber protection (and the per
 * block deny message flooded chat). When `protection.block-advanced-enchantments` is on and AE is
 * installed, TCP cancels AE's cancellable `EnchantActivateEvent` for a player standing in a
 * registered chamber (unless they hold `tcp.bypass.protection`), stopping the effect outright.
 * Enchant names listed in `protection.advanced-enchantments-allowlist` are still permitted
 * (e.g. combat enchants you want to keep working inside chambers).
 *
 * Verified against `net.advancedplugins.ae.api.EnchantActivateEvent` (AdvancedEnchantments 9.23.6):
 * `Cancellable`, `getFirstEntity(): Entity`, `getEnchant()` / `getEnchantment(): String`.
 *
 * Reflection-based — TCP carries no compile-time dependency on the paid AE jar, and the hook is
 * inert when AE is absent. Cancelling never reflects (the event implements Bukkit's [Cancellable]).
 */
object AdvancedEnchantmentsHook {

    private const val EVENT_CLASS = "net.advancedplugins.ae.api.EnchantActivateEvent"

    /** How far to ray-trace for the block the player is mining (survival reach ≈ 5–6 blocks). */
    private const val MINING_REACH = 6

    /** Cube half-extent added around the targeted block to cover an AoE mining enchant's radius. */
    private const val BLAST_MARGIN = 2

    private val lastMessage = ConcurrentHashMap<UUID, Long>()

    /** Whether AdvancedEnchantments is installed and enabled. */
    fun isAvailable(plugin: TrialChamberPro): Boolean =
        plugin.server.pluginManager.getPlugin("AdvancedEnchantments")?.isEnabled == true

    /** Dynamically register the enchant-activation guard. Call only after [isAvailable]. */
    fun register(plugin: TrialChamberPro) {
        val cls = Refl.classOrNull(EVENT_CLASS)?.let {
            if (Event::class.java.isAssignableFrom(it)) it.asSubclass(Event::class.java) else null
        } ?: run {
            plugin.logger.warning("AdvancedEnchantments is installed but '$EVENT_CLASS' was not found — integration inactive.")
            return
        }
        val executor = EventExecutor { _, event -> handle(plugin, event) }
        plugin.server.pluginManager.registerEvent(cls, object : Listener {}, EventPriority.HIGH, executor, plugin, true)
        plugin.logger.info(
            "  - AdvancedEnchantments integration: ready " +
                "(enchants blocked inside chambers when protection.block-advanced-enchantments is on)"
        )
    }

    private fun handle(plugin: TrialChamberPro, event: Event) {
        try {
            if (!plugin.config.getBoolean("protection.enabled", true)) return
            if (!plugin.config.getBoolean("protection.block-advanced-enchantments", false)) return

            val player = Refl.call(event, "getFirstEntity") as? Player ?: return
            if (player.hasPermission("tcp.bypass.protection")) return

            val enchant = (Refl.call(event, "getEnchant") as? String
                ?: Refl.call(event, "getEnchantment") as? String)?.lowercase()
            if (enchant != null) {
                val allow = plugin.config.getStringList("protection.advanced-enchantments-allowlist")
                    .map { it.lowercase() }
                if (enchant in allow) return
            }

            if (!affectsChamber(plugin, player)) return

            (event as? Cancellable)?.isCancelled = true
            notify(plugin, player)
        } catch (_: Throwable) {
            // Fail open: never break AE or spam the console over a reflection surprise.
        }
    }

    /**
     * True if this enchant activation should be blocked because it touches a (non-paused) chamber.
     *
     * AE's `EnchantActivateEvent` exposes no block or location — only the player — so we can't read
     * where a mining enchant like Blast Mining actually lands. We therefore check **two** things:
     *  1. the player standing inside a chamber, and
     *  2. the block the player is targeting, expanded by [BLAST_MARGIN] on every axis, intersecting a
     *     chamber — this catches mining the chamber *wall from outside* (the wall block is inside the
     *     chamber bounds) and AoE breaks that reach into a chamber from just outside it.
     */
    private fun affectsChamber(plugin: TrialChamberPro, player: Player): Boolean {
        plugin.chamberManager.getCachedChamberAt(player.location)?.let { return !it.isPaused }

        val target = runCatching { player.getTargetBlockExact(MINING_REACH) }.getOrNull() ?: return false
        val world = target.world?.name ?: return false
        val b = target.location
        val chamber = plugin.chamberManager.getIntersectingChamber(
            world,
            b.blockX - BLAST_MARGIN, b.blockY - BLAST_MARGIN, b.blockZ - BLAST_MARGIN,
            b.blockX + BLAST_MARGIN, b.blockY + BLAST_MARGIN, b.blockZ + BLAST_MARGIN,
        ) ?: return false
        return !chamber.isPaused
    }

    /** Throttled denial message (shares `protection.message-cooldown-ms`) to avoid per-swing spam. */
    private fun notify(plugin: TrialChamberPro, player: Player) {
        val cooldownMs = plugin.config.getLong("protection.message-cooldown-ms", 1500L)
        if (cooldownMs > 0L) {
            val now = System.currentTimeMillis()
            val last = lastMessage[player.uniqueId]
            if (last != null && now - last < cooldownMs) return
            lastMessage[player.uniqueId] = now
        }
        player.sendMessage(plugin.getMessageComponent("cannot-use-enchant-in-chamber"))
    }
}

package io.github.darkstarworks.trialChamberPro.integrations.claims

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor

/**
 * Wires TCP's chamber registry to whichever land-claim plugins are installed
 * (Residence / Lands / GriefPrevention), all via reflection (see [ClaimProvider]).
 *
 * Two jobs:
 * 1. **Shield** — dynamically register the cancellable claim-create/expand events of every
 *    available + enabled provider, and cancel any whose area overlaps a registered chamber
 *    (unless the player holds that provider's bypass permission).
 * 2. **Scan** — walk every chamber against every provider's existing claims and log a
 *    warning per overlap, so operators can find and resolve pre-existing conflicts. Runs on
 *    startup (config-gated) and on demand via `/tcp claims scan`.
 */
class ClaimIntegrationManager(private val plugin: TrialChamberPro) {

    private val providers: List<ClaimProvider> = listOf(
        ResidenceClaimProvider(),
        LandsClaimProvider(),
        GriefPreventionClaimProvider(),
    )

    /** A single shared listener instance is enough — dispatch happens in the executor. */
    private val listener = object : Listener {}

    /** Providers whose plugin is installed (regardless of the config toggle). */
    private fun installedProviders(): List<ClaimProvider> = providers.filter { it.isAvailable(plugin) }

    /** Providers that are installed AND enabled in config. */
    private fun activeProviders(): List<ClaimProvider> = installedProviders().filter {
        plugin.config.getBoolean(it.configKey, true)
    }

    /**
     * Register the claim-shield guards for every *installed* provider. The per-plugin config
     * toggle is re-checked live inside [handle], so enabling/disabling an integration takes
     * effect on `/tcp reload` without a restart (we just attach an early-returning handler).
     */
    fun registerGuards() {
        for (provider in installedProviders()) {
            val executor = EventExecutor { _, event -> handle(provider, event) }
            var registered = 0
            for (name in provider.eventClassNames) {
                val cls = Refl.classOrNull(name)?.let {
                    if (Event::class.java.isAssignableFrom(it)) it.asSubclass(Event::class.java) else null
                } ?: continue
                plugin.server.pluginManager.registerEvent(
                    cls, listener, EventPriority.HIGH, executor, plugin, true,
                )
                registered++
            }
            if (registered > 0) {
                val state = if (plugin.config.getBoolean(provider.configKey, true)) {
                    "enabled (chambers shielded from claims)"
                } else {
                    "detected, but disabled in config"
                }
                plugin.logger.info("  - ${provider.pluginName} integration: $state")
            }
        }
    }

    private fun handle(provider: ClaimProvider, event: Event) {
        if (!plugin.config.getBoolean(provider.configKey, true)) return
        try {
            val attempt = provider.parseAttempt(event) ?: return
            val actor = attempt.actor ?: return // console / non-player source — leave alone
            if (actor.hasPermission(provider.bypassPermission)) return

            val chamber = plugin.chamberManager.getIntersectingChamber(
                attempt.worldName,
                attempt.minX, attempt.minY, attempt.minZ,
                attempt.maxX, attempt.maxY, attempt.maxZ,
            ) ?: return

            (event as? Cancellable)?.isCancelled = true
            actor.sendMessage(plugin.getMessageComponent("cannot-claim-in-chamber", "chamber" to chamber.name))
        } catch (t: Throwable) {
            // Fail open: never let a reflection surprise break the claim plugin's flow or
            // spam the console — a missed denial is far less harmful than a crash loop.
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("${provider.pluginName} claim guard error: ${t.message}")
            }
        }
    }

    /**
     * Check every registered chamber against every active provider's existing claims and
     * log a warning per overlap. Returns the number of conflicts logged. Reflection-heavy
     * and best-effort — call off the main thread.
     */
    fun scanAndLog(): Int {
        val active = activeProviders()
        if (active.isEmpty()) return 0
        val chambers = plugin.chamberManager.getCachedChambers()
        if (chambers.isEmpty()) return 0
        var conflicts = 0
        for (provider in active) {
            val byChamber = try {
                provider.findConflicts(plugin, chambers)
            } catch (_: Throwable) {
                emptyMap()
            }
            for ((chamber, claims) in byChamber) {
                if (claims.isEmpty()) continue
                conflicts++
                val cx = (chamber.minX + chamber.maxX) / 2
                val cy = (chamber.minY + chamber.maxY) / 2
                val cz = (chamber.minZ + chamber.maxZ) / 2
                plugin.logger.warning(
                    "Claim conflict: chamber '${chamber.name}' (${chamber.world} $cx,$cy,$cz) " +
                        "overlaps ${provider.pluginName} claim(s): ${claims.joinToString(", ")}",
                )
            }
        }
        return conflicts
    }

    /** True if at least one provider is installed and enabled — used to gate the scan/command. */
    fun hasActiveProvider(): Boolean = activeProviders().isNotEmpty()
}

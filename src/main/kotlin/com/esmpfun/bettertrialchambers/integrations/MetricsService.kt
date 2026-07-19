package com.esmpfun.bettertrialchambers.integrations

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import dev.faststats.ErrorTracker
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric

/**
 * FastStats integration (v2.0.5; replaced bStats). Anonymous usage metrics that
 * drive feature prioritization: which database backend servers actually run,
 * whether auto-discovery is enabled in the wild, glow-mode adoption, fleet
 * chamber counts, and which premium modules are installed alongside BTC free.
 *
 * Respect knobs (either disables collection entirely):
 *  - BTC's own `metrics.enabled` in config.yml — checked here, and when false the
 *    context is never constructed, so nothing is sent and no config file is written.
 *  - FastStats' server-wide opt-out at `plugins/faststats/config.properties`
 *    (`enabled=false`, or the narrower `submitMetrics` / `submitErrors` flags).
 *    Equivalent to the old `plugins/bStats/config.yml`.
 *
 * Note that FastStats deliberately submits **nothing on its first run** — it
 * writes the opt-out file and waits for the next server start so owners get a
 * chance to opt out first. A fresh install showing no data for one boot is
 * expected, not a misconfiguration.
 *
 * Error tracking (v2.0.6) reports BTC's own uncaught exceptions so bugs surface
 * without waiting for someone to open a ticket. Scoped and filtered:
 *  - `contextAware(classLoader)` binds it to THIS plugin's class loader, so other
 *    plugins' exceptions are never captured — only ours.
 *  - Toggle separately with `metrics.error-tracking` in config.yml, or server-wide
 *    with `submitErrors=false` in `plugins/faststats/config.properties`.
 *  - Extra anonymisation on top of the SDK's built-ins (see [buildErrorTracker]).
 *
 * Metric suppliers are [java.util.concurrent.Callable]s evaluated by the SDK on
 * its own submission schedule; every one below reads cheap in-memory state only.
 */
object MetricsService {

    /**
     * FastStats project token for BetterTrialChambers. Not a secret — it ships
     * inside the distributed jar and identifies the project, exactly as the old
     * bStats service id did. Blank disables metrics init entirely.
     */
    private const val PROJECT_TOKEN: String = "cd65155a2ad3ff239f18424dc4faf43d"

    @Volatile
    private var context: BukkitContext? = null

    fun init(plugin: BetterTrialChambers): String {
        if (PROJECT_TOKEN.isBlank()) return "Disabled (no project token)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"
        // Guard against a double init (e.g. hot-reload) — ready() warns and ignores,
        // but two contexts would mean two submission schedulers.
        if (context != null) return "Enabled"

        val errorTracking = plugin.config.getBoolean("metrics.error-tracking", true)

        return try {
            val ctx = BukkitContext.Factory(plugin, PROJECT_TOKEN)
                .also { if (errorTracking) it.errorTrackerService(buildErrorTracker()) }
                .metrics { factory ->
                    factory
                        .addMetric(Metric.string("database_type") {
                            plugin.databaseManager.databaseType.toString().lowercase()
                        })
                        .addMetric(Metric.string("discovery_enabled") {
                            plugin.config.getBoolean("discovery.enabled", false).toString()
                        })
                        .addMetric(Metric.string("glow_mode") {
                            if (!plugin.config.getBoolean("spawner-waves.glow-active-spawners", false)) "disabled"
                            else plugin.config.getString("spawner-waves.glow-mode", "wave-active") ?: "wave-active"
                        })
                        .addMetric(Metric.string("chamber_count") {
                            when (val n = plugin.chamberManager.getCachedChamberNames().size) {
                                0 -> "0"
                                in 1..10 -> "1-10"
                                in 11..50 -> "11-50"
                                in 51..100 -> "51-100"
                                else -> if (n <= 250) "101-250" else "250+"
                            }
                        })
                        .addMetric(Metric.numberMap("premium_modules") {
                            val map = HashMap<String, Number>()
                            for (name in arrayOf("TCP-VaultCrates", "TCP-WildSpawners", "TCP-MythicTrials")) {
                                if (plugin.server.pluginManager.getPlugin(name) != null) map[name] = 1
                            }
                            if (map.isEmpty()) map["none"] = 1
                            map
                        })
                        .create()
                }
                .create()

            // Must run on the main thread and inside enable — on Paper this also
            // registers the server exception handlers. Phase 10 already executes
            // inside scheduler.runTask, so we're on the right thread here.
            ctx.ready()
            context = ctx
            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("FastStats init failed: ${e.message}")
            "Failed"
        }
    }

    /**
     * Builds the error tracker: scoped to this plugin, with cancellation noise
     * filtered out and BTC-specific redaction layered on the SDK's built-ins.
     *
     * **Scope.** `contextAware(loader)` auto-captures uncaught throwables whose
     * class loader matches. Passing our own loader explicitly (rather than the
     * no-arg overload) keeps that unambiguous: other plugins' exceptions are
     * never ours to report.
     *
     * **Cancellation.** Coroutine cancellation is normal control flow here —
     * `ResetManager` rethrows `CancellationException` by design, and shutdown
     * cancels `pluginScope`. Reporting those would bury real bugs. Note the SDK
     * matches ignored types by EXACT class (`getClass()` against a Set), not
     * `isAssignableFrom`, so registering `CancellationException` alone would miss
     * kotlinx's `JobCancellationException` subclass — hence the message pattern
     * alongside it.
     *
     * **Redaction.** The SDK already strips IPv4/IPv6 addresses, home-directory
     * paths (covering our SQLite `jdbc:sqlite:<abs path>` URL), the OS username,
     * and `user:pass@host` style JDBC credentials. Added here:
     *  - query-string credentials, for driver messages that echo connection
     *    properties (BTC passes the MySQL password via Hikari's setter, not the
     *    URL, but a driver can still surface it);
     *  - player UUIDs, so the "no player data is collected" promise stays literally
     *    true even when a stack trace happens to carry one.
     */
    private fun buildErrorTracker(): ErrorTracker =
        ErrorTracker.contextAware(MetricsService::class.java.classLoader)
            .ignoreError(java.util.concurrent.CancellationException::class.java)
            .ignoreError("(?i).*\\b(?:job|coroutine)\\b.*\\bcancell?ed\\b.*")
            .anonymize("(?i)([?&;](?:user|username|password|pass|pwd)=)[^&;\\s\"']*", "$1[hidden]")
            .anonymize(
                "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
                "[uuid hidden]"
            )

    /**
     * Releases the SDK's submission scheduler. Without this a `/reload` (or any
     * disable/enable cycle) would leak the previous context's threads — bStats
     * needed no such call, so this is new in v2.0.5.
     */
    fun shutdown() {
        val ctx = context ?: return
        context = null
        try {
            ctx.shutdown()
        } catch (e: Exception) {
            // Never let telemetry teardown break plugin shutdown.
            java.util.logging.Logger.getLogger("BetterTrialChambers")
                .warning("FastStats shutdown failed: ${e.message}")
        }
    }
}

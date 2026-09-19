package com.esmpfun.bettertrialchambers.integrations

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import dev.faststats.Attributes
import dev.faststats.ErrorTracker
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric
import java.util.concurrent.atomic.AtomicLong

/**
 * FastStats integration (v2.0.5; replaced bStats).
 *
 * **Usage metrics.** Anonymous aggregate config and activity, so development time
 * goes where the fleet actually uses it. Nothing identifies a server or a player.
 * Config gauges are read on the SDK's submission schedule; the three activity
 * counters ([recordReset], [recordVaultOpened], [recordChamberCleared]) are fed
 * by [com.esmpfun.bettertrialchambers.listeners.MetricsListener] and reset each
 * submission via `onFlush`.
 *
 * Off switches (each disables collection entirely):
 *  - BTC's own `metrics.enabled`, checked here. When false the context is never
 *    constructed, so nothing is sent and no config file is written.
 *  - FastStats' server-wide `plugins/faststats/config.properties` (`enabled=false`,
 *    or the narrower `submitMetrics` / `submitErrors`). Replaces the old
 *    `plugins/bStats/config.yml`.
 *
 * FastStats submits **nothing on a server's first run**: it writes the opt-out
 * file and waits for the next start, so an owner gets a chance to opt out. One
 * boot with no data on a fresh install is expected.
 *
 * **Error reporting.** On by default (`metrics.error-reporting`, set false to turn
 * off). Two paths:
 *  - `contextAware(classLoader)` auto-captures throwables that escape uncaught and
 *    whose stack frames belong to THIS plugin. Other plugins' exceptions are never
 *    captured.
 *  - [reportHandled] sends an exception BTC caught and recovered from (a reset that
 *    quietly failed, a discovery pass that threw), tagged with the operation and a
 *    little non-player context. These are the bugs that otherwise never become a
 *    ticket because the plugin kept running.
 * Every report carries fixed context (plugin/MC version, database type, Folia,
 * chamber-count band) and extra redaction on top of the SDK's built-ins. See
 * [buildErrorTracker].
 */
object MetricsService {

    /**
     * FastStats project token for BetterTrialChambers. Not a secret, it ships
     * inside the distributed jar and identifies the project, exactly as the old
     * bStats service id did. Blank disables metrics init entirely.
     */
    private const val PROJECT_TOKEN: String = "cd65155a2ad3ff239f18424dc4faf43d"

    @Volatile
    private var context: BukkitContext? = null

    @Volatile
    private var errorTracker: ErrorTracker? = null

    // Activity since the last submission. Fed by MetricsListener, drained by onFlush.
    private val resetsCompleted = AtomicLong(0)
    private val vaultsOpened = AtomicLong(0)
    private val chambersCleared = AtomicLong(0)

    fun recordReset() { resetsCompleted.incrementAndGet() }
    fun recordVaultOpened() { vaultsOpened.incrementAndGet() }
    fun recordChamberCleared() { chambersCleared.incrementAndGet() }

    fun init(plugin: BetterTrialChambers): String {
        if (PROJECT_TOKEN.isBlank()) return "Disabled (no project token)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"
        // Guard against a double init (e.g. hot-reload), ready() warns and ignores,
        // but two contexts would mean two submission schedulers.
        if (context != null) return "Enabled"

        // The unpublished pre-release key `metrics.error-tracking` is dead. It is
        // read only to tell an owner the line is inert, since mergeYamlDefaults may
        // have written it into their deployed config.yml.
        val legacyKey = "metrics.error-tracking"
        if (plugin.config.isSet(legacyKey)) {
            plugin.logger.info(
                "config.yml: '$legacyKey' is no longer used. Error reporting is controlled by " +
                    "'metrics.error-reporting' (on by default). The old line is inert and can be deleted."
            )
        }
        val errorReporting = plugin.config.getBoolean("metrics.error-reporting", true)
        val tracker = if (errorReporting) buildErrorTracker(plugin) else null

        return try {
            val ctx = BukkitContext.Factory(plugin, PROJECT_TOKEN)
                .also { f -> tracker?.let { f.errorTrackerService(it) } }
                .metrics { factory -> registerMetrics(plugin, factory) }
                .create()

            // Must run on the main thread and inside enable, on Paper this also
            // registers the server exception handlers. Phase 10 already executes
            // inside scheduler.runTask, so we're on the right thread here.
            ctx.ready()
            context = ctx
            errorTracker = tracker
            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("FastStats init failed: ${e.message}")
            "Failed"
        }
    }

    private fun registerMetrics(plugin: BetterTrialChambers, factory: dev.faststats.Metrics.Factory): dev.faststats.Metrics {
        return factory
            // Platform
            .addMetric(Metric.string("database_type") {
                plugin.databaseManager.databaseType.toString().lowercase()
            })
            .addMetric(Metric.string("mc_version") { plugin.server.minecraftVersion })
            .addMetric(Metric.string("server_software") { serverSoftware(plugin) })
            // Feature adoption
            .addMetric(Metric.string("discovery_enabled") {
                plugin.config.getBoolean("discovery.enabled", false).toString()
            })
            .addMetric(Metric.string("glow_mode") {
                if (!plugin.config.getBoolean("spawner-waves.glow-active-spawners", false)) "disabled"
                else plugin.config.getString("spawner-waves.glow-mode", "wave-active") ?: "wave-active"
            })
            .addMetric(Metric.string("vault_loot_mode") {
                (plugin.config.getString("vaults.loot-mode", "PER_PLAYER") ?: "PER_PLAYER").lowercase()
            })
            .addMetric(Metric.string("reset_interval") {
                resetIntervalBucket(plugin.config.getLong("global.default-reset-interval", 172800L))
            })
            .addMetric(Metric.bool("container_loot") {
                plugin.config.getBoolean("chests.per-player-loot", false)
            })
            .addMetric(Metric.string("custom_mob_provider") { customMobProviderSummary(plugin) })
            .addMetric(Metric.stringArray("soft_hooks") { softHooks(plugin) })
            // Scale and health
            .addMetric(Metric.string("chamber_count") {
                chamberCountBucket(plugin.chamberManager.getCachedChambers().size)
            })
            .addMetric(Metric.string("snapshotless_chambers") { snapshotlessBucket(plugin) })
            .addMetric(Metric.string("largest_chamber") { largestChamberBucket(plugin) })
            .addMetric(Metric.numberMap("premium_modules") {
                val map = HashMap<String, Number>()
                for (name in arrayOf("TCP-VaultCrates", "TCP-WildSpawners", "TCP-MythicTrials")) {
                    if (plugin.server.pluginManager.getPlugin(name) != null) map[name] = 1
                }
                if (map.isEmpty()) map["none"] = 1
                map
            })
            // Activity since the last submission
            .addMetric(Metric.number("resets_completed") { resetsCompleted.get() })
            .addMetric(Metric.number("vaults_opened") { vaultsOpened.get() })
            .addMetric(Metric.number("chambers_cleared") { chambersCleared.get() })
            .onFlush {
                resetsCompleted.set(0)
                vaultsOpened.set(0)
                chambersCleared.set(0)
            }
            .create()
    }

    /**
     * Reports an exception BTC caught and recovered from. No-op when error
     * reporting is off. `operation` is a short fixed label (`chamber-reset`,
     * `chamber-discovery`, `dungeon-generate`, ...). `context` values are put on
     * the report as-is, so keep them to counts, buckets and fixed enums, never
     * names or coordinates.
     */
    fun reportHandled(t: Throwable, operation: String, vararg context: Pair<String, Any?>) {
        val tracker = errorTracker ?: return
        if (t is java.util.concurrent.CancellationException) return
        if (!dueToReport(operation)) return
        runCatching {
            val attrs = Attributes.empty().put("operation", operation)
            for ((k, v) in context) {
                when (v) {
                    null -> {}
                    is Number -> attrs.put(k, v)
                    is Boolean -> attrs.put(k, v)
                    else -> attrs.put(k, v.toString())
                }
            }
            tracker.trackError(t).attributes(attrs).handled(true)
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
     * **Cancellation.** Coroutine cancellation is normal control flow here,
     * `ResetManager` rethrows `CancellationException` by design, and shutdown
     * cancels `pluginScope`. Reporting those would bury real bugs. Note the SDK
     * matches ignored types by EXACT class (`getClass()` against a Set), not
     * `isAssignableFrom`, so registering `CancellationException` alone would miss
     * kotlinx's `JobCancellationException` subclass, hence the message pattern
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
     *
     * **Context.** Plugin and Minecraft version, database type, Folia, and a
     * chamber-count band go on every report. All fixed for the server's lifetime
     * and none carry player data. They let a fix target the setup a crash came from.
     */
    private fun buildErrorTracker(plugin: BetterTrialChambers): ErrorTracker {
        val tracker = ErrorTracker.contextAware(MetricsService::class.java.classLoader)
            .ignoreError(java.util.concurrent.CancellationException::class.java)
            .ignoreError("(?i).*\\b(?:job|coroutine)\\b.*\\bcancell?ed\\b.*")
            .anonymize("(?i)([?&;](?:user|username|password|pass|pwd)=)[^&;\\s\"']*", "$1[hidden]")
            .anonymize(
                "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
                "[uuid hidden]"
            )
        // A file or database error quotes the path it failed on, and that names the hosting
        // account. Longest first, so the plugin folder wins over the server folder inside it.
        for ((from, to) in serverPaths(plugin)) {
            tracker.anonymize(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to))
        }

        runCatching {
            tracker.attributes
                .put("btc_version", plugin.pluginMeta.version)
                .put("mc_version", plugin.server.minecraftVersion)
                .put("database", plugin.databaseManager.databaseType.toString().lowercase())
                .put("folia", plugin.scheduler.isFolia)
                .put("chambers", chamberCountBucket(plugin.chamberManager.getCachedChambers().size))
        }
        return tracker
    }

    /** Absolute paths that should never leave the server, longest first. */
    private fun serverPaths(plugin: BetterTrialChambers): List<Pair<String, String>> {
        val data = plugin.dataFolder.absolutePath
        return listOfNotNull(
            data to "plugins/${plugin.dataFolder.name}",
            plugin.dataFolder.parentFile?.parentFile?.absolutePath?.let { it to "." },
            System.getProperty("user.home")?.takeIf { it.isNotBlank() }?.let { it to "~" },
            System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() }?.let { it to "<temp>" }
        ).distinctBy { it.first }.sortedByDescending { it.first.length }
    }

    /**
     * Some sites report per interaction, such as opening a vault, so one broken database would
     * otherwise send a report on every click. One per operation per ten minutes is enough to
     * know it is happening.
     */
    private fun dueToReport(operation: String): Boolean {
        val now = System.currentTimeMillis()
        var fire = false
        lastReported.compute(operation) { _, previous ->
            if (previous == null || now - previous >= REPORT_INTERVAL_MS) { fire = true; now } else previous
        }
        return fire
    }

    private const val REPORT_INTERVAL_MS = 600_000L

    private val lastReported = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // ---- metric helpers (cheap, thread-safe, no player data) --------------------

    /** Coarse fleet band for a chamber count. Shared by the metric and the error context. */
    private fun chamberCountBucket(n: Int): String = when (n) {
        0 -> "0"
        in 1..10 -> "1-10"
        in 11..50 -> "11-50"
        in 51..100 -> "51-100"
        in 101..250 -> "101-250"
        else -> "250+"
    }

    private fun serverSoftware(plugin: BetterTrialChambers): String {
        if (plugin.scheduler.isFolia) return "folia"
        if (classPresent("org.purpurmc.purpur.PurpurConfig")) return "purpur"
        if (classPresent("gg.pufferfish.pufferfish.PufferfishConfig")) return "pufferfish"
        if (classPresent("com.destroystokyo.paper.PaperConfig") ||
            classPresent("io.papermc.paper.configuration.Configuration")
        ) return "paper"
        return "other"
    }

    private fun classPresent(name: String): Boolean =
        runCatching { Class.forName(name); true }.getOrDefault(false)

    private fun resetIntervalBucket(seconds: Long): String = when {
        seconds <= 0 -> "disabled"
        seconds < 43_200 -> "under-12h"
        seconds < 172_800 -> "12h-48h"
        seconds < 604_800 -> "48h-1w"
        else -> "over-1w"
    }

    private fun customMobProviderSummary(plugin: BetterTrialChambers): String {
        val set = plugin.chamberManager.getCachedChambers()
            .mapNotNull { it.customMobProvider?.lowercase()?.takeUnless { p -> p == "vanilla" } }
            .toSet()
        return when {
            set.isEmpty() -> "none"
            set.size == 1 -> set.first()
            else -> "multiple"
        }
    }

    private fun softHooks(plugin: BetterTrialChambers): Array<String> {
        val pm = plugin.server.pluginManager
        return arrayOf(
            "WorldEdit", "FastAsyncWorldEdit", "WorldGuard", "PlaceholderAPI", "Vault", "LuckPerms",
            "Residence", "Lands", "GriefPrevention", "AdvancedEnchantments",
            "MythicMobs", "EliteMobs", "EcoMobs", "LevelledMobs", "InfernalMobs", "Citizens",
            "Nexo", "ItemsAdder", "Oraxen", "CraftEngine", "MythicCrucible",
        ).filter { pm.getPlugin(it) != null }.toTypedArray()
    }

    private fun snapshotlessBucket(plugin: BetterTrialChambers): String {
        val missing = plugin.chamberManager.getCachedChambers()
            .count { plugin.snapshotManager.getSnapshotFile(it.name) == null }
        return when (missing) {
            0 -> "0"
            in 1..5 -> "1-5"
            in 6..20 -> "6-20"
            else -> "20+"
        }
    }

    private fun largestChamberBucket(plugin: BetterTrialChambers): String {
        val max = plugin.chamberManager.getCachedChambers().maxOfOrNull { it.getVolume() } ?: 0
        return when {
            max == 0 -> "none"
            max < 100_000 -> "under-100k"
            max < 750_000 -> "100k-750k"
            max < 3_000_000 -> "750k-3m"
            else -> "over-3m"
        }
    }

    /**
     * Releases the SDK's submission scheduler. Without this a `/reload` (or any
     * disable/enable cycle) would leak the previous context's threads, bStats
     * needed no such call, so this is new in v2.0.5.
     */
    fun shutdown() {
        lastReported.clear()
        val ctx = context ?: return
        context = null
        errorTracker = null
        try {
            ctx.shutdown()
        } catch (e: Exception) {
            // Never let telemetry teardown break plugin shutdown.
            java.util.logging.Logger.getLogger("BetterTrialChambers")
                .warning("FastStats shutdown failed: ${e.message}")
        }
    }
}

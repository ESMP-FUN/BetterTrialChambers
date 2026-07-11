package com.esmpfun.bettertrialchambers.integrations

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.bstats.bukkit.Metrics
import org.bstats.charts.AdvancedPie
import org.bstats.charts.SimplePie

/**
 * bStats integration. Anonymous usage metrics that drive feature
 * prioritization: which database backend servers actually run, whether
 * auto-discovery is enabled in the wild, glow-mode adoption, fleet chamber
 * counts, and which premium modules are installed alongside TCP free.
 *
 * Respect knobs (either disables collection entirely):
 *  - TCP's own `metrics.enabled` in config.yml
 *  - bStats' global opt-out (`plugins/bStats/config.yml`)
 *
 * All chart callables are evaluated by bStats on its own submission
 * schedule (~30 min) on the main thread; every supplier below reads
 * cheap in-memory state only.
 */
object MetricsService {

    /**
     * bStats service id for BetterTrialChambers, registered at
     * https://bstats.org/plugin/bukkit/BetterTrialChambers/31905.
     * A value <= 0 disables metrics init entirely.
     */
    private const val BSTATS_SERVICE_ID: Int = 31905

    fun init(plugin: BetterTrialChambers): String {
        if (BSTATS_SERVICE_ID <= 0) return "Disabled (no service id)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"

        return try {
            val metrics = Metrics(plugin, BSTATS_SERVICE_ID)

            metrics.addCustomChart(SimplePie("database_type") {
                plugin.databaseManager.databaseType.toString().lowercase()
            })

            metrics.addCustomChart(SimplePie("discovery_enabled") {
                plugin.config.getBoolean("discovery.enabled", false).toString()
            })

            metrics.addCustomChart(SimplePie("glow_mode") {
                if (!plugin.config.getBoolean("spawner-waves.glow-active-spawners", false)) "disabled"
                else plugin.config.getString("spawner-waves.glow-mode", "wave-active") ?: "wave-active"
            })

            metrics.addCustomChart(SimplePie("chamber_count") {
                when (val n = plugin.chamberManager.getCachedChamberNames().size) {
                    0 -> "0"
                    in 1..10 -> "1-10"
                    in 11..50 -> "11-50"
                    in 51..100 -> "51-100"
                    else -> if (n <= 250) "101-250" else "250+"
                }
            })

            metrics.addCustomChart(AdvancedPie("premium_modules") {
                val map = HashMap<String, Int>()
                for (name in arrayOf("TCP-VaultCrates", "TCP-WildSpawners", "TCP-MythicTrials")) {
                    if (plugin.server.pluginManager.getPlugin(name) != null) map[name] = 1
                }
                if (map.isEmpty()) map["none"] = 1
                map
            })

            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("bStats init failed: ${e.message}")
            "Failed"
        }
    }
}

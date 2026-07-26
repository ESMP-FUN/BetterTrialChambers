package com.esmpfun.bettertrialchambers.integrations

import com.esmpfun.bettertrialchambers.BetterTrialChambers

/**
 * Owns the lifecycle of the PlaceholderAPI expansions — handing them over when the
 * plugin starts and taking them back when it shuts down.
 *
 * **Why this lives in its own file.** PlaceholderAPI is an optional plugin, so its
 * classes are only present when the server owner actually installed it. Keeping every
 * mention of those classes inside this file means the main plugin class never names a
 * PlaceholderAPI type in a field or method signature — which is what lets the test
 * suite build a stand-in for the plugin class without PlaceholderAPI on hand. Inlining
 * any of this back into the plugin class breaks the unit tests with a confusing
 * "lateinit property has not been initialized" error.
 *
 * **Why unregistering matters.** PlaceholderAPI keeps registered expansions in its own
 * list, and each expansion holds the plugin that created it. If a shutting-down copy of
 * the plugin never takes its expansions back, PlaceholderAPI keeps that whole copy in
 * memory for as long as the server runs. On a server that reloads plugins instead of
 * restarting, memory use then creeps up with every single reload.
 */
object PlaceholderIntegration {

    private val registered = mutableListOf<PlaceholderAPIExpansion>()

    /**
     * Registers the placeholders if PlaceholderAPI is installed.
     *
     * @return a short status word for the startup log.
     */
    fun register(plugin: BetterTrialChambers): String {
        if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") == null) return "Not Found"
        return try {
            add(PlaceholderAPIExpansion(plugin))
            // legacy pre-2.0 identifier so existing %tcp_*% placeholders keep resolving
            add(PlaceholderAPIExpansion(plugin, "tcp"))
            "Registered"
        } catch (e: Exception) {
            plugin.logger.warning("Failed to register PlaceholderAPI expansion: ${e.message}")
            "Failed"
        }
    }

    /**
     * Takes every placeholder back off PlaceholderAPI. Safe to call when PlaceholderAPI
     * was never installed, and safe to call twice.
     */
    fun unregisterAll(plugin: BetterTrialChambers) {
        for (expansion in registered) {
            try {
                expansion.unregister()
            } catch (e: Throwable) {
                plugin.logger.warning("Failed to unregister PlaceholderAPI expansion: ${e.message}")
            }
        }
        registered.clear()
    }

    private fun add(expansion: PlaceholderAPIExpansion) {
        if (expansion.register()) registered += expansion
    }
}

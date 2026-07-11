package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.filter.AbstractFilter

/**
 * Mutes the vanilla console spam `Trial Spawner at BlockPos{...} has no detected
 * players`, which a trial spawner logs every tick while it's stuck in a bad
 * state. TCP's reset fixes stop NEW occurrences, but spawners already broken on
 * a running server keep spamming until their chamber next resets — this filter
 * silences that one line in the meantime.
 *
 * Opt out via `global.suppress-trial-spawner-spam: false`.
 */
object TrialSpawnerLogFilter {

    private var installed: SpamFilter? = null

    fun install(plugin: BetterTrialChambers) {
        if (installed != null) return
        if (!plugin.config.getBoolean("global.suppress-trial-spawner-spam", true)) return
        try {
            val root = LogManager.getRootLogger() as? Logger ?: return
            val filter = SpamFilter()
            root.addFilter(filter)
            installed = filter
            plugin.logger.info("Muting vanilla 'Trial Spawner ... has no detected players' console spam (global.suppress-trial-spawner-spam).")
        } catch (e: Throwable) {
            plugin.logger.warning("Could not install trial-spawner log filter: ${e.message}")
        }
    }

    fun uninstall() {
        val filter = installed ?: return
        try {
            // Logger exposes addFilter, but removal is on its LoggerConfig.
            (LogManager.getRootLogger() as? Logger)?.get()?.removeFilter(filter)
        } catch (_: Throwable) {
            // best-effort
        } finally {
            installed = null
        }
    }

    private class SpamFilter : AbstractFilter() {
        override fun filter(event: LogEvent?): Filter.Result {
            val msg = event?.message?.formattedMessage ?: return Filter.Result.NEUTRAL
            return if (msg.startsWith("Trial Spawner at") && msg.contains("has no detected players")) {
                Filter.Result.DENY
            } else {
                Filter.Result.NEUTRAL
            }
        }
    }
}

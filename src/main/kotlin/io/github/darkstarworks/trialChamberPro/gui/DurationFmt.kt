package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro

/**
 * Shared duration formatter for chamber list views. Produces "2d 3h", "5m", or the
 * localized "due now" token when `durationMs <= 0`.
 *
 * Pulled out of `ChambersOverviewView.kt` in v1.5.0 when that legacy view (which
 * only aliased to `ChamberListView` via `openOverview`) was deleted.
 */
internal object DurationFmt {
    fun humanize(plugin: TrialChamberPro, durationMs: Long): String {
        if (durationMs <= 0) return plugin.rawMessage("gui.chamber-list.duration-due-now")  // nested sub-value
        var seconds = durationMs / 1000
        val months = seconds / (30L * 24 * 3600); seconds %= (30L * 24 * 3600)
        val weeks = seconds / (7L * 24 * 3600);   seconds %= (7L * 24 * 3600)
        val days = seconds / (24 * 3600);          seconds %= (24 * 3600)
        val hours = seconds / 3600;                seconds %= 3600
        val minutes = seconds / 60;                seconds %= 60
        val parts = mutableListOf<String>()
        if (months > 0) parts += "${months}mo"
        if (weeks > 0) parts += "${weeks}w"
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (parts.isEmpty() && seconds > 0) parts += "${seconds}s"
        return parts.take(2).joinToString(" ")
    }
}

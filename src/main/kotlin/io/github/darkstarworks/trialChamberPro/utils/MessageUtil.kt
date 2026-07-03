package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import java.util.concurrent.TimeUnit

/**
 * Utility class for formatting time values.
 *
 * The plugin-taking overloads read their unit suffixes from the `time-*` keys
 * in messages.yml (`time-days`, `time-hours`, `time-minutes`, `time-seconds`,
 * `time-now`, `time-ago`) so servers can localize durations. The plugin-less
 * versions keep the original English output for contexts without a plugin
 * instance.
 */
object MessageUtil {

    /**
     * Formats a time duration in milliseconds to a human-readable string.
     *
     * @param milliseconds Time in milliseconds
     * @return Formatted time string (e.g., "2d 3h 45m")
     */
    fun formatTime(milliseconds: Long): String {
        return formatTime(null, milliseconds)
    }

    /**
     * Localized variant of [formatTime] — unit suffixes come from the
     * messages.yml `time-*` keys.
     */
    fun formatTime(plugin: TrialChamberPro?, milliseconds: Long): String {
        // getRawMessage (not rawMessage): older user messages.yml copies may lack the
        // time-* keys, and this must degrade to English rather than "<missing: …>".
        fun unit(key: String, placeholder: String, value: Long, fallback: String): String =
            plugin?.getRawMessage(key, "{$placeholder}$fallback")
                ?.replace("{$placeholder}", value.toString())
                ?: "$value$fallback"

        if (milliseconds <= 0) return unit("time-seconds", "seconds", 0, "s")

        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add(unit("time-days", "days", days, "d"))
        if (hours > 0) parts.add(unit("time-hours", "hours", hours, "h"))
        if (minutes > 0) parts.add(unit("time-minutes", "minutes", minutes, "m"))
        if (seconds > 0 && days == 0L) parts.add(unit("time-seconds", "seconds", seconds, "s")) // Only show seconds if less than a day

        return if (parts.isEmpty()) unit("time-seconds", "seconds", 0, "s") else parts.joinToString(" ")
    }

    /**
     * Formats a time duration in seconds to a human-readable string.
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    fun formatTimeSeconds(seconds: Long): String {
        return formatTime(null, seconds * 1000)
    }

    /** Localized variant of [formatTimeSeconds]. */
    fun formatTimeSeconds(plugin: TrialChamberPro?, seconds: Long): String {
        return formatTime(plugin, seconds * 1000)
    }

    /**
     * Formats a timestamp to a relative time string (e.g., "2 hours ago").
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long): String {
        return formatRelativeTime(null, timestamp)
    }

    /** Localized variant of [formatRelativeTime] — uses `time-now` / `time-ago`. */
    fun formatRelativeTime(plugin: TrialChamberPro?, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 0) return "in the future"
        if (diff < 60000) return plugin?.getRawMessage("time-now", "just now") ?: "just now"

        val time = formatTime(plugin, diff)
        return plugin?.getRawMessage("time-ago", "{time} ago")?.replace("{time}", time) ?: "$time ago"
    }
}

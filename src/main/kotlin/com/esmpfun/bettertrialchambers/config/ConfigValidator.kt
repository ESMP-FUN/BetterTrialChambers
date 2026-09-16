package com.esmpfun.bettertrialchambers.config

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.database.TableNames

/**
 * Startup config sanity check (added in v1.3.0).
 *
 * Reads every numeric config value that has a sensible domain, clamps
 * out-of-range values back to a safe default, and logs a warning for each clamp.
 * Does not hard-fail, a misconfigured server still boots, just with corrected
 * in-memory values and a visible log trail so the admin can fix `config.yml`.
 *
 * Intentionally permissive: only the keys with known pathological values are
 * listed. Unknown keys are left alone.
 */
object ConfigValidator {

    /**
     * A clamped numeric entry.
     *
     * @param key YAML path
     * @param min inclusive lower bound; `null` = no lower bound
     * @param max inclusive upper bound; `null` = no upper bound
     * @param default value to write back when missing or unparseable
     * @param allowSentinel if non-null, this value is allowed even when it
     *        falls outside the min/max window (e.g. `-1` for "use vanilla
     *        cooldown" on spawner-related keys).
     */
    private data class LongRule(
        val key: String,
        val min: Long?,
        val max: Long?,
        val default: Long,
        val allowSentinel: Long? = null
    )

    private val rules = listOf(
        LongRule("vaults.normal-cooldown-hours", 0, null, 24),
        LongRule("vaults.ominous-cooldown-hours", 0, null, 48),
        LongRule("vaults.drop-loot-owner-grace-seconds", 0, null, 30),
        LongRule("vaults.feedback.hologram.duration-ticks", 1, 1200, 30),
        LongRule("generation.max-volume", 1, 5_000_000, 750_000),
        // These two live under `global:` in config.yml (not `reset:`/`performance:`).
        LongRule("global.default-reset-interval", 0, null, 172_800),
        LongRule("global.blocks-per-tick", 1, 50_000, 500),
        LongRule("reset.spawner-cooldown-minutes", 0, null, 30, allowSentinel = -1),
        LongRule("reset.wild-spawner-cooldown-minutes", 0, null, 30, allowSentinel = -1),
        LongRule("reset.spawner-key-drop-owner-grace-seconds", 0, null, 30),
        LongRule("performance.cache-duration-seconds", 1, null, 300),
        LongRule("performance.time-tracking-interval", 1, null, 300),
        LongRule("discovery.max-radius-xz", 0, null, 80),
        LongRule("discovery.max-radius-y", 0, null, 40),
        LongRule("discovery.max-center-y", -256, 320, 60),
        LongRule("discovery.cooldown-seconds", 0, null, 60),
        LongRule("discovery.pending-retry-seconds", 0, null, 30),
        LongRule("discovery.structure-max-volume", 1, null, 15_000_000, allowSentinel = -1),
        LongRule("protection.tunnel-breaking.shell-depth", 0, 64, 3)
    )

    /**
     * Validate and clamp. Returns the number of keys that were corrected.
     */
    /** Settings an older `/trial setup` wrote under the wrong section, and where they belong. */
    private val MISPLACED_BY_SETUP = mapOf(
        "reset.reset-require-confirmation" to "global.reset-require-confirmation",
        "reset.use-fawe" to "global.use-fawe",
    )

    fun validate(plugin: BetterTrialChambers): Int {
        val config = plugin.config
        var clamped = 0

        // The setup tour wrote two of its answers to the wrong section until
        // 2.1.1, so anyone who turned these on there had nothing happen. Carry
        // the answer over to the setting that is actually read, and clear the
        // one that never did anything.
        for ((wrong, right) in MISPLACED_BY_SETUP) {
            if (!config.contains(wrong)) continue
            val value = config.get(wrong)
            if (value is Boolean && !config.getBoolean(right, false)) {
                config.set(right, value)
                plugin.logger.info("[Config] Moved '$wrong' to '$right' (it was written in the wrong place by an older setup tour).")
            }
            config.set(wrong, null)
            clamped++
        }

        // v1.7.0: database.table-prefix must be letters/digits/underscore, max 16 chars.
        // DatabaseManager falls back to the default at runtime; warn here so the admin
        // sees why their custom prefix isn't taking effect.
        val rawPrefix = config.getString("database.table-prefix")
        if (rawPrefix != null && !TableNames.isValid(rawPrefix)) {
            plugin.logger.warning(
                "[Config] 'database.table-prefix' = '$rawPrefix' is invalid " +
                    "(letters/digits/underscore, max 16 chars); using '${TableNames.DEFAULT_PREFIX}'."
            )
            config.set("database.table-prefix", TableNames.DEFAULT_PREFIX)
            clamped++
        }

        for (rule in rules) {
            if (!config.contains(rule.key)) continue  // key absent -> plugin default wins

            val raw = config.getLong(rule.key, Long.MIN_VALUE)
            if (raw == Long.MIN_VALUE) {
                plugin.logger.warning(
                    "[Config] '${rule.key}' could not be parsed as a number; " +
                        "using default ${rule.default}."
                )
                config.set(rule.key, rule.default)
                clamped++
                continue
            }

            // Sentinel value passes through untouched.
            if (rule.allowSentinel != null && raw == rule.allowSentinel) continue

            val min = rule.min
            val max = rule.max
            val belowMin = min != null && raw < min
            val aboveMax = max != null && raw > max
            if (belowMin || aboveMax) {
                val clampedTo = if (belowMin) min!! else max!!
                plugin.logger.warning(
                    "[Config] '${rule.key}' = $raw is out of range " +
                        "[${rule.min ?: "-∞"}, ${rule.max ?: "+∞"}]" +
                        (rule.allowSentinel?.let { " (or sentinel $it)" } ?: "") +
                        "; clamped to $clampedTo."
                )
                config.set(rule.key, clampedTo)
                clamped++
            }
        }

        if (clamped > 0) {
            plugin.logger.warning(
                "[Config] $clamped value(s) were clamped at startup. " +
                    "Review config.yml to silence these warnings."
            )
        }
        return clamped
    }
}

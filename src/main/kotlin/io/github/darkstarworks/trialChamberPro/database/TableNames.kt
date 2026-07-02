package io.github.darkstarworks.trialChamberPro.database

/**
 * Computed table names for every table TCP owns, built from the configurable
 * `database.table-prefix` (v1.7.0, default `tcp_`).
 *
 * Pure and unit-testable: [of] sanitizes the raw config value so a malicious or
 * malformed prefix can never reach a SQL string. With the default prefix,
 * [playerStats] equals the pre-1.7.0 `tcp_player_stats` name, so already-namespaced
 * stats tables need no migration.
 */
data class TableNames(val prefix: String) {
    val chambers = "${prefix}chambers"
    val vaults = "${prefix}vaults"
    val spawners = "${prefix}spawners"
    val playerVaults = "${prefix}player_vaults"
    val playerContainerLoot = "${prefix}player_container_loot"
    val containerTemplate = "${prefix}container_template"
    val playerStats = "${prefix}player_stats"

    /** All tables in FK-safe order (parents before children). */
    val all: List<String> = listOf(
        chambers, vaults, spawners, playerVaults,
        playerContainerLoot, containerTemplate, playerStats,
    )

    companion object {
        const val DEFAULT_PREFIX = "tcp_"
        private val VALID = Regex("^[A-Za-z0-9_]{0,16}$")

        /** Legacy (pre-1.7.0) unprefixed base names, parent-first, keyed by base name. */
        val LEGACY_BASE_NAMES = listOf(
            "chambers", "vaults", "spawners", "player_vaults",
            "player_container_loot", "container_template",
        )

        /**
         * Builds a [TableNames] from the raw config value. Invalid values (characters
         * outside `[A-Za-z0-9_]` or longer than 16 chars) fall back to [DEFAULT_PREFIX];
         * the caller decides whether to log. Returns the sanitized instance plus a flag.
         */
        fun of(raw: String?): TableNames = TableNames(sanitize(raw))

        /** True when [raw] is a usable prefix as-is (no fallback needed). */
        fun isValid(raw: String?): Boolean = raw != null && VALID.matches(raw)

        fun sanitize(raw: String?): String =
            if (isValid(raw)) raw!! else DEFAULT_PREFIX
    }
}

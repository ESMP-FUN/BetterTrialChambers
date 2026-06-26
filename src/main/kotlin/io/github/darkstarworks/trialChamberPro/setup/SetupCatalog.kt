package io.github.darkstarworks.trialChamberPro.setup

/**
 * The curated list of settings the `/tcp setup` tour walks through, in order.
 *
 * Chosen for impact, not completeness — leading with the high-value settings that ship
 * **off** (so operators never find them), then the runtime toggles already surfaced in the
 * admin GUI, then a couple of "choice" settings. To add/remove a step, edit this list and
 * its `messages.yml` `setup.steps.<id>.*` entries. Order matters: dependent settings follow
 * the setting they depend on (auto-snapshot right after auto-discovery).
 */
object SetupCatalog {

    val steps: List<SetupStep> = listOf(
        // ── High-value, ship disabled ──────────────────────────────────────────
        SetupStep.Toggle("discovery", "discovery.enabled", default = false, cpuImpact = CpuImpact.LITTLE),
        SetupStep.Toggle("auto-snapshot", "discovery.auto-snapshot", default = false, cpuImpact = CpuImpact.LITTLE),
        SetupStep.Toggle("drop-loot", "vaults.drop-loot-at-vault", default = false),
        SetupStep.Toggle("reset-confirm", "reset.reset-require-confirmation", default = false),
        SetupStep.Toggle("fawe", "reset.use-fawe", default = false, requiresPlugin = "FastAsyncWorldEdit"),

        // ── Choice: how often chambers auto-reset ──────────────────────────────
        // 6 options (even count) so the 2-column button grid lays out cleanly with the
        // nav buttons grouped below. Path is global.default-reset-interval (what
        // ChamberManager actually reads) — NOT reset.* (a phantom key).
        SetupStep.Choice(
            "reset-interval", "global.default-reset-interval",
            options = listOf(
                ChoiceOption("off", "setup.steps.reset-interval.options.off", 0L),
                ChoiceOption("6h", "setup.steps.reset-interval.options.6h", 21_600L),
                ChoiceOption("12h", "setup.steps.reset-interval.options.12h", 43_200L),
                ChoiceOption("1d", "setup.steps.reset-interval.options.1d", 86_400L),
                ChoiceOption("2d", "setup.steps.reset-interval.options.2d", 172_800L),
                ChoiceOption("7d", "setup.steps.reset-interval.options.7d", 604_800L),
            ),
            formatCurrent = ::formatInterval,
        ),

        // ── Runtime toggles (mirrors GlobalSettingsView) ───────────────────────
        SetupStep.Toggle("per-player-loot", "vaults.per-player-loot", default = true),
        SetupStep.Toggle("clear-items", "reset.clear-ground-items", default = true),
        SetupStep.Toggle("remove-mobs", "reset.remove-spawner-mobs", default = true),
        SetupStep.Toggle("reset-spawners", "reset.reset-trial-spawners", default = true),
        SetupStep.Toggle("reset-cooldowns", "reset.reset-vault-cooldowns", default = true),
        SetupStep.Toggle("waves", "spawner-waves.enabled", default = true),
        SetupStep.Toggle("boss-bar", "spawner-waves.show-boss-bar", default = true),
        SetupStep.Toggle("spectator", "spectator-mode.enabled", default = true),
        SetupStep.Toggle("statistics", "statistics.enabled", default = true, cpuImpact = CpuImpact.TINY),
        SetupStep.Toggle("luck", "loot.apply-luck-effect", default = true),
    )

    fun stepAt(index: Int): SetupStep? = steps.getOrNull(index)
    val count: Int get() = steps.size

    /** The interval that ships in config.yml; tagged "(default)" in the Currently line. */
    private const val DEFAULT_INTERVAL = 172_800L

    /**
     * Reset-interval seconds → a human duration for the "Currently: …" line, e.g.
     * 172800 → "2 days (default)", 21600 → "6 hours", 104400 → "1 day, 5 hours".
     */
    private fun formatInterval(value: Any?): String {
        val s = (value as? Number)?.toLong() ?: return "not set"
        if (s <= 0L) return "Never (manual only)"
        val parts = mutableListOf<String>()
        var rem = s
        fun take(unit: Long, name: String) {
            val n = rem / unit
            if (n > 0) { parts += "$n $name${if (n == 1L) "" else "s"}"; rem %= unit }
        }
        take(604_800L, "week"); take(86_400L, "day"); take(3_600L, "hour"); take(60L, "minute")
        val out = parts.joinToString(", ").ifEmpty { "$s seconds" }
        return if (s == DEFAULT_INTERVAL) "$out (default)" else out
    }
}

package com.esmpfun.bettertrialchambers.setup

import net.kyori.adventure.text.format.NamedTextColor

/**
 * A coarse, easy-to-read CPU cost for a setting, measured against a *median default-size
 * chamber*. Far more useful to a non-technical operator than vague "uses a little CPU".
 */
enum class CpuImpact(val text: String, val color: NamedTextColor, val mini: String) {
    TINY("tiny", NamedTextColor.WHITE, "white"),
    LITTLE("little", NamedTextColor.GREEN, "green"),
    MEDIUM("medium", NamedTextColor.YELLOW, "yellow"),
    HIGH("high", NamedTextColor.RED, "red"),
}

/**
 * One step of the `/trial setup` tour — a single setting presented to the operator.
 *
 * Render-agnostic on purpose: holds only the config path + message keys + optional metadata,
 * so both the Dialog renderer and the clickable-chat renderer consume the same catalog. Text
 * lives in `messages.yml` under `setup.steps.<id>.*` (title = string, desc = list of lines).
 */
sealed class SetupStep {
    abstract val id: String
    abstract val configPath: String

    /** Optional CPU-cost badge shown under the description. */
    abstract val cpuImpact: CpuImpact?

    val titleKey: String get() = "setup.steps.$id.title"
    val descKey: String get() = "setup.steps.$id.desc"

    /** A boolean setting: Enable / Skip / Disable. */
    class Toggle(
        override val id: String,
        override val configPath: String,
        val default: Boolean,
        override val cpuImpact: CpuImpact? = null,
        /** When set and that plugin is absent, the Enable button is shown disabled. */
        val requiresPlugin: String? = null,
    ) : SetupStep()

    /** A pick-one setting (e.g. reset interval). Skip keeps the current value. */
    class Choice(
        override val id: String,
        override val configPath: String,
        val options: List<ChoiceOption>,
        override val cpuImpact: CpuImpact? = null,
        /** Renders the current config value for the "Currently: …" line (e.g. seconds → "2 days"). */
        val formatCurrent: ((Any?) -> String)? = null,
    ) : SetupStep()
}

/**
 * One option of a [SetupStep.Choice]. [value] is written via `config.set(path, value)`;
 * [labelKey] is a `messages.yml` key for the button label. [optionId] disambiguates the
 * chat-path `/trial setup set <key> <optionId>` action.
 */
data class ChoiceOption(
    val optionId: String,
    val labelKey: String,
    val value: Any,
)

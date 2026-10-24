package com.esmpfun.bettertrialchambers.models

import org.bukkit.configuration.ConfigurationSection

/**
 * The protection switches a chamber can set for itself. Anything a chamber
 * leaves unset follows the matching `protection.` key in config.yml.
 *
 * [guiKey] names the label and description under `gui.protection-menu`.
 */
enum class ProtectionSetting(val key: String, val default: Boolean, val guiKey: String) {
    BLOCK_BREAK("prevent-block-break", true, "block-break"),
    BLOCK_PLACE("prevent-block-place", true, "block-place"),
    CONTAINERS("prevent-container-access", false, "containers"),
    MOB_GRIEFING("prevent-mob-griefing", true, "mob-griefing"),
    LIQUIDS("prevent-liquid-flow", true, "liquids"),
    FIRE("prevent-fire", true, "fire"),
    PISTONS("prevent-piston-movement", true, "pistons"),
    DECORATIONS("protect-decorations", true, "decorations"),
    NATURAL_DECAY("prevent-natural-decay", false, "natural-decay"),
    PVP("allow-pvp", true, "pvp"),
    ENTRY("prevent-entry-without-permission", false, "entry"),
    TELEPORT("prevent-teleport-into-chamber", false, "teleport");

    val configPath: String get() = "protection.$key"
    val labelKey: String get() = "gui.protection-menu.$guiKey-label"
    val descKey: String get() = "gui.protection-menu.$guiKey-desc"

    fun globalValue(config: ConfigurationSection): Boolean = config.getBoolean(configPath, default)

    /** The chamber's own choice when it made one, otherwise the server-wide value. */
    fun valueFor(chamber: Chamber?, config: ConfigurationSection): Boolean =
        chamber?.protectionOverrides?.get(key) ?: globalValue(config)
}

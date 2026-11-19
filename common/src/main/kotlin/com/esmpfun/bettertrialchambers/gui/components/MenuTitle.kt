package com.esmpfun.bettertrialchambers.gui.components

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/** Keeps a chest menu's title inside the menu by shortening the chamber name in it. */
object MenuTitle {

    /** Room for the title in a chest menu, in the game's font pixels (176 wide, 8 margin each side). */
    private const val MAX_WIDTH = 160

    /** Fewer letters than this left of a name reads as noise, so the name is left out instead. */
    private const val MIN_KEPT = 5

    /**
     * Builds the title with [name] and, when it would run past the menu's edge, with the name cut
     * down and ending in "..". When not even [MIN_KEPT] letters fit, uses [withoutName] instead.
     */
    fun fitName(name: String, withoutName: () -> Component, build: (String) -> Component): Component {
        val full = build(name)
        if (width(full) <= MAX_WIDTH) return full
        for (keep in name.length - 1 downTo MIN_KEPT) {
            val candidate = build(name.take(keep).trimEnd() + "..")
            if (width(candidate) <= MAX_WIDTH) return candidate
        }
        return withoutName()
    }

    private fun width(title: Component): Int =
        PlainTextComponentSerializer.plainText().serialize(title).sumOf { charWidth(it) }

    /** Widths from the default Minecraft font, one pixel of spacing included. */
    internal fun charWidth(c: Char): Int = when (c) {
        'i', '!', '.', ',', ':', ';', '|', '\'' -> 2
        'l', '`' -> 3
        't', 'I', '[', ']', ' ' -> 4
        'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5
        '@', '~' -> 7
        else -> 6
    }
}

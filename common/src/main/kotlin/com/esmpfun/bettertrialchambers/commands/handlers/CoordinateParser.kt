package com.esmpfun.bettertrialchambers.commands.handlers

/**
 * Pure-logic coordinate parsing for `/trial generate` argument shapes. Extracted
 * from [GenerateCommand] in v1.3.0 so the parsers can be unit-tested
 * without mocking Bukkit's `World` registry. The world-aware composite parsing
 * (resolving world names, falling back to the sender's world, etc.) stays in
 * `GenerateCommand.parseCoordsArgs` because it needs the live Bukkit `World`
 * lookup.
 */
object CoordinateParser {

    /**
     * Parses a single hyphen-separated coordinate pair of the form
     * `x1,y1,z1-x2,y2,z2`. Whitespace around tokens is tolerated; negative
     * values are supported on every axis. Returns `null` for any malformed
     * input rather than throwing.
     */
    fun parseHyphenated(input: String): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>? {
        val regex = Regex(
            "^\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$"
        )
        val m = regex.matchEntire(input) ?: return null
        val x1 = m.groupValues[1].toIntOrNull() ?: return null
        val y1 = m.groupValues[2].toIntOrNull() ?: return null
        val z1 = m.groupValues[3].toIntOrNull() ?: return null
        val x2 = m.groupValues[4].toIntOrNull() ?: return null
        val y2 = m.groupValues[5].toIntOrNull() ?: return null
        val z2 = m.groupValues[6].toIntOrNull() ?: return null
        return Pair(Triple(x1, y1, z1), Triple(x2, y2, z2))
    }

    /**
     * Parses a comma-separated triple `x,y,z`. Returns `null` for malformed
     * input. Tolerates whitespace around each token; rejects floats.
     */
    fun parseTriple(input: String): Triple<Int, Int, Int>? {
        val parts = input.split(",")
        if (parts.size != 3) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        val z = parts[2].trim().toIntOrNull() ?: return null
        return Triple(x, y, z)
    }

    /** True iff [input] is a syntactically valid coordinate triple. */
    fun isTripleString(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return parseTriple(input) != null
    }
}

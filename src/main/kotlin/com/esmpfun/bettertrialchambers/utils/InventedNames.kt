package com.esmpfun.bettertrialchambers.utils

import kotlin.random.Random

/** Made-up chamber names such as "Copperhold" or "Galewatch", for when the name pool runs out. */
object InventedNames {

    private val FIRST = listOf(
        "Amber", "Ash", "Bleak", "Bramble", "Briar", "Bright", "Brim", "Cinder", "Cold", "Copper",
        "Crag", "Crow", "Dawn", "Deep", "Dread", "Dusk", "Ember", "Fallow", "Fen", "Flint", "Fog",
        "Frost", "Gale", "Gilt", "Gloam", "Gloom", "Grey", "Grim", "Hallow", "Haze", "Hollow", "Iron",
        "Ivy", "Mire", "Mist", "Moon", "Moss", "Mourn", "Night", "Oak", "Pale", "Raven", "Rime",
        "Rook", "Rust", "Sable", "Salt", "Shade", "Shale", "Silver", "Slate", "Star", "Stone", "Storm",
        "Sun", "Thorn", "Tide", "Tuff", "Umber", "Vex", "Wind", "Winter", "Wither", "Wolf", "Wraith",
        "Wren",
    )

    private val LAST = listOf(
        "barrow", "bourne", "crest", "crypt", "deep", "fall", "fell", "ford", "forge", "gate", "guard",
        "hall", "haven", "hold", "hollow", "keep", "mere", "moor", "reach", "rest", "spire", "stead",
        "vault", "ward", "watch", "wick", "wood",
    )

    // Same word twice or a doubled letter at the seam ("Mossstead") reads badly.
    private val ALL: List<String> = FIRST.flatMap { first ->
        LAST.filter { it != first.lowercase() && it.first() != first.last().lowercaseChar() }.map { first + it }
    }

    /** A name not in [taken] (lower-case names), or null once every one is used. */
    fun next(taken: Set<String>, random: Random = Random.Default): String? =
        ALL.filter { it.lowercase() !in taken }.randomOrNull(random)
}

package com.esmpfun.bettertrialchambers.utils

/**
 * What a chamber may be called.
 *
 * A chamber's name is also the name of its snapshot file, so a name carrying a
 * slash, a colon or a dot writes that file somewhere else or not at all, and
 * the chamber then cannot reset. The display name shown in announcements is a
 * separate field with no such limit, set with `/trial rename`.
 */
object ChamberNames {

    /** Letters, digits, underscore and hyphen; 1 to 32 characters. */
    private val ALLOWED = Regex("^[A-Za-z0-9_-]{1,32}$")

    fun isValid(name: String): Boolean = ALLOWED.matches(name)

    /** Makes a name out of text that was not typed by hand, such as a world's name. */
    fun sanitize(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .take(32)
            .ifEmpty { "chamber" }
}

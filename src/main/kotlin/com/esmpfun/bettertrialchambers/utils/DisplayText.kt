package com.esmpfun.bettertrialchambers.utils

/**
 * Cleans free text that a person typed before it goes into a message everyone
 * on the server sees.
 *
 * A chamber's display name is put into announcements and then styled, which
 * means anything in it is styled too. Colours and bold are welcome. A clickable
 * piece of text is not: it would put a button in front of every player that
 * runs a command as whoever clicks it, and text can be made to look like it
 * came from the server or from another player.
 */
object DisplayText {

    /** Tags that make text clickable, hoverable or insertable. */
    private val INTERACTIVE = Regex(
        "</?\\s*(?:click|hover|insert(?:ion)?|selector|nbt|score)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )

    /** Tags and characters that would start a new line. */
    private val LINE_BREAK = Regex("</?\\s*(?:newline|br)\\b[^>]*>|[\\r\\n]", RegexOption.IGNORE_CASE)

    /** Leaves colour and format alone; takes the rest out. */
    fun sanitize(raw: String): String =
        raw.replace(INTERACTIVE, "")
            .replace(LINE_BREAK, " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
}

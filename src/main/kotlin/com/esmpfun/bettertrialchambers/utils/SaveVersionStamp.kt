package com.esmpfun.bettertrialchambers.utils

import org.bukkit.Bukkit
import java.io.File
import java.util.logging.Logger

/**
 * Records which version of Minecraft wrote a file, and notices when that file
 * came from a newer version than the server is running now.
 *
 * ### Why this exists
 *
 * Several of the plugin's files store items exactly as the game stores them,
 * so that an item's enchantments, custom name and everything else survive:
 * `loot.yml` does it for loot the admin added through the editor, and chamber
 * snapshots do it for the contents of chests, pots, lecterns and jukeboxes.
 *
 * The game can always read an item saved by an older version, because it
 * upgrades it on the way in. It cannot read one saved by a newer version.
 * So going up a Minecraft version is always safe, and going back down is not.
 *
 * That only really comes up when someone tries a newer Minecraft version,
 * adds or changes some loot while they are on it, then decides to go back.
 * Their `loot.yml` now contains items the older version has never heard of.
 * Rather than let those quietly fail to load one at a time, the file is copied
 * to a backup and the owner is told plainly what happened and where the copy is.
 */
object SaveVersionStamp {

    /** Section written at the top of a stamped file. */
    const val SECTION = "saved-by"

    /**
     * Minecraft's own internal save-format number. It only ever goes up, which
     * is what makes it usable for "was this written by something newer than me".
     * Reads as 0 if the server will not say, in which case no check is made.
     *
     * `Bukkit.getUnsafe()` is deprecated as a whole, and there is no supported
     * alternative: `ServerBuildInfo` exposes only version *names*, which are
     * strings and cannot be ordered reliably. The number is exactly the value
     * the game itself stamps into saved items, so it is the right thing to
     * compare against, and reading it is harmless.
     */
    @Suppress("DEPRECATION")
    fun currentDataVersion(): Int = runCatching { Bukkit.getUnsafe().dataVersion }.getOrDefault(0)

    /** The readable version, for messages a person has to act on. */
    fun currentMinecraftVersion(): String =
        runCatching { Bukkit.getMinecraftVersion() }.getOrDefault("unknown")

    /**
     * Copies [file] to a `.bak` next to it, never overwriting a backup that is
     * already there, and returns the file it wrote. A second and third
     * downgrade produce `.bak2` and `.bak3` rather than destroying the first.
     */
    fun backUp(file: File): File? = runCatching {
        var target = File(file.parentFile, file.name + ".bak")
        var n = 2
        while (target.exists()) {
            target = File(file.parentFile, file.name + ".bak" + n)
            n++
        }
        file.copyTo(target)
        target
    }.getOrNull()

    /**
     * Checks a stamped file against the running server.
     *
     * When the file was written by a newer Minecraft than this one, takes a
     * backup and explains it in the console. Returns true when that happened,
     * so the caller can decide whether to keep going.
     *
     * @param stampedDataVersion the number read out of the file, or 0 when the
     *   file predates stamping (which is normal and never a problem, because
     *   anything older can always be read).
     */
    fun warnIfFromNewerVersion(
        file: File,
        stampedDataVersion: Int,
        stampedMinecraftVersion: String?,
        logger: Logger,
    ): Boolean {
        val current = currentDataVersion()
        if (current == 0 || stampedDataVersion <= current) return false

        val wroteIt = stampedMinecraftVersion ?: "a newer version"
        val backup = backUp(file)
        logger.warning("=".repeat(72))
        logger.warning("${file.name} was last saved on Minecraft $wroteIt, and this server is")
        logger.warning("running ${currentMinecraftVersion()}. Minecraft can open a file from an older")
        logger.warning("version but not from a newer one, so some items in it may not load.")
        if (backup != null) {
            logger.warning("")
            logger.warning("A copy of the file exactly as it was has been saved next to it:")
            logger.warning("  ${backup.name}")
            logger.warning("Nothing has been deleted. If you go back to Minecraft $wroteIt, rename")
            logger.warning("that copy back to ${file.name} and everything returns as it was.")
        }
        logger.warning("=".repeat(72))
        return true
    }
}

package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Bukkit

/**
 * WorldEdit availability check, deliberately kept in its OWN class with **zero**
 * WorldEdit imports.
 *
 * Why this is separate from [WorldEditUtil]: the JVM links (and bytecode-verifies)
 * a whole class the first time any of its methods is called. [WorldEditUtil] has
 * methods with `catch (IncompleteRegionException)` clauses, so verifying it forces
 * the loader to resolve `com.sk89q.worldedit.*` — which throws
 * `NoClassDefFoundError` when WorldEdit isn't installed. That means the very check
 * meant to guard WorldEdit usage couldn't run without WorldEdit present (it crashed
 * chamber resets and `/tcp generate wand` on servers without WE). Keeping the guard
 * here, free of any WE reference, lets it be called safely whether or not WorldEdit
 * is on the server.
 */
object WorldEditSupport {

    /** True if WorldEdit or FastAsyncWorldEdit is installed and enabled. */
    fun isAvailable(): Boolean {
        val pm = Bukkit.getPluginManager()
        return pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit")
    }
}

package com.esmpfun.bettertrialchambers.utils

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.entity.Entity
import org.bukkit.scoreboard.Team
import java.util.UUID

/**
 * Colours the outline of a glowing entity.
 *
 * The game only colours an outline by the team the entity is on, and a team can
 * only be one of the sixteen colours the game names. A configured colour is
 * matched to the closest of those.
 */
object GlowTeams {

    private const val TEAM_PREFIX = "btc_glow_"

    /** Puts [entity] on the team whose colour is closest to [color]. */
    fun apply(entity: Entity, color: Color) {
        runCatching {
            val named = nearest(color)
            val team = teamFor(named) ?: return
            team.addEntry(entity.uniqueId.toString())
        }
    }

    /** Takes a removed entity back off whichever team it was put on. */
    fun release(uuid: UUID) {
        runCatching {
            val entry = uuid.toString()
            val board = Bukkit.getScoreboardManager().mainScoreboard
            board.teams
                .filter { it.name.startsWith(TEAM_PREFIX) && it.hasEntry(entry) }
                .forEach { it.removeEntry(entry) }
        }
    }

    /** Removes the teams entirely. Called when the plugin stops. */
    fun clearAll() {
        runCatching {
            val board = Bukkit.getScoreboardManager().mainScoreboard
            board.teams.filter { it.name.startsWith(TEAM_PREFIX) }.forEach { it.unregister() }
        }
    }

    private fun teamFor(named: NamedTextColor): Team? {
        val board = Bukkit.getScoreboardManager().mainScoreboard
        val name = TEAM_PREFIX + NamedTextColor.NAMES.key(named)
        return (board.getTeam(name) ?: board.registerNewTeam(name)).also { it.color(named) }
    }

    internal fun nearest(color: Color): NamedTextColor =
        NamedTextColor.NAMES.values().minBy { named ->
            val dr = ((named.value() shr 16) and 0xFF) - color.red
            val dg = ((named.value() shr 8) and 0xFF) - color.green
            val db = (named.value() and 0xFF) - color.blue
            dr * dr + dg * dg + db * db
        }
}

package io.github.darkstarworks.trialChamberPro.utils

import com.google.gson.JsonParser
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.net.URI

/**
 * Checks for plugin updates from GitHub releases.
 * Folia compatible: Uses scheduler adapter for async operations.
 *
 * v1.5.2+: Also implements [Listener] — when an update is detected, admins
 * with `tcp.admin` see a one-line clickable Modrinth download link the next
 * time they log in (and any currently-online admins are pinged immediately).
 */
class UpdateChecker(
    private val plugin: TrialChamberPro,
    private val githubRepo: String,
    private val updateDescriptionUrl: String? = null,
    private val downloadUrl: String = "https://modrinth.com/plugin/trialchamberpro/versions"
) : Listener {
    private val currentVersion = plugin.pluginMeta.version

    /** Latest version observed by [checkForUpdates] when newer than [currentVersion], else null. */
    @Volatile
    private var latestKnown: String? = null

    fun checkForUpdates(notifyConsole: Boolean = true) {
        plugin.scheduler.runTaskAsync(Runnable {
            try {
                val latestVersion = fetchLatestVersion()
                if (isNewerVersion(latestVersion)) {
                    latestKnown = latestVersion
                    val updateInfo = updateDescriptionUrl?.let { fetchUpdateDescription(it) }
                        ?: "Check GitHub for details"
                    if (notifyConsole) {
                        val header = "<gold>[${plugin.name}]</gold> <green>Update available:</green> <yellow>$latestVersion</yellow> <gray>(current: $currentVersion)</gray>"
                        sendColoredConsoleMessage(header)
                        // Send each line of the update info separately for proper formatting
                        updateInfo.lines().forEach { line ->
                            if (line.isNotBlank()) sendColoredConsoleMessage(line)
                        }
                    }
                    // Ping any already-online admins so they don't have to wait until
                    // their next login to see the notice.
                    plugin.scheduler.runTask(Runnable {
                        Bukkit.getOnlinePlayers()
                            .filter { it.hasPermission("tcp.admin") }
                            .forEach { sendUpdateNotice(it, latestVersion) }
                    })
                } else {
                    latestKnown = null
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to check for updates: ${e.message}")
            }
        })
    }

    /** Notify admins on login when an update is pending. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val latest = latestKnown ?: return
        val player = event.player
        if (!player.hasPermission("tcp.admin")) return
        // Defer one tick so the join message stream has settled.
        plugin.scheduler.runAtEntity(player, Runnable {
            if (player.isOnline) sendUpdateNotice(player, latest)
        })
    }

    private fun sendUpdateNotice(player: Player, latest: String) {
        val message = MiniMessage.miniMessage().deserialize(
            "<gold>[TCP] <gray>You are using version <yellow>$currentVersion</yellow>, " +
                "latest version is <green>$latest</green>." +
                "<newline><click:open_url:'$downloadUrl'><hover:show_text:'<gray>Open Modrinth in your browser'>" +
                "<aqua>[Download Latest Version]</aqua></hover></click>"
        )
        player.sendMessage(message)
    }

    private fun sendColoredConsoleMessage(message: String) {
        val component = MiniMessage.miniMessage().deserialize(message)
        Bukkit.getConsoleSender().sendMessage(component)
    }

    private fun fetchUpdateDescription(url: String): String =
        URI(url).toURL().readText().trim()

    private fun fetchLatestVersion(): String {
        val url = "https://api.github.com/repos/$githubRepo/releases/latest"
        val json = URI(url).toURL().readText()
        return JsonParser.parseString(json).asJsonObject["tag_name"].asString.removePrefix("v")
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val remote = latest.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(current.size, remote.size)) {
            val c = current.getOrNull(i) ?: 0
            val r = remote.getOrNull(i) ?: 0
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

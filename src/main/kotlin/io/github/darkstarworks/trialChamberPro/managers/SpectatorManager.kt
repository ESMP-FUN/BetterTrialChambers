package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages spectator mode for players in Trial Chambers.
 *
 * Features:
 * - Players can spectate after death
 * - Keeps spectators within chamber bounds
 * - Automatic exit when chamber resets or empties
 * - Restores previous game mode on exit
 */
class SpectatorManager(private val plugin: TrialChamberPro) {

    /**
     * Data for a spectating player.
     */
    data class SpectatorData(
        val playerUUID: UUID,
        val chamberId: Int,
        val chamberName: String,
        val previousGameMode: GameMode,
        val previousLocation: Location,
        val startTime: Long = System.currentTimeMillis()
    )

    // Active spectators: UUID -> SpectatorData
    private val spectators = ConcurrentHashMap<UUID, SpectatorData>()

    // Players pending spectator confirmation
    private val pendingSpectate = ConcurrentHashMap<UUID, SpectatorData>()

    // v1.7.2 crash-recovery layer: the previous gamemode/location are ALSO written
    // to the player's PersistentDataContainer (persists in the player .dat across
    // restarts). The in-memory map stays the live source of truth; the PDC keys
    // exist only so a crash/restart mid-spectate doesn't strand the player in
    // SPECTATOR forever. Cleared on every normal exit path.
    private val prevGameModeKey = org.bukkit.NamespacedKey(plugin, "spectator_prev_gamemode")
    private val prevLocationKey = org.bukkit.NamespacedKey(plugin, "spectator_prev_location")

    private fun writeRecoveryData(player: Player, data: SpectatorData) {
        val pdc = player.persistentDataContainer
        pdc.set(prevGameModeKey, org.bukkit.persistence.PersistentDataType.STRING, data.previousGameMode.name)
        val loc = data.previousLocation
        pdc.set(
            prevLocationKey, org.bukkit.persistence.PersistentDataType.STRING,
            "${loc.world?.name};${loc.x};${loc.y};${loc.z};${loc.yaw};${loc.pitch}"
        )
    }

    private fun clearRecoveryData(player: Player) {
        val pdc = player.persistentDataContainer
        pdc.remove(prevGameModeKey)
        pdc.remove(prevLocationKey)
    }

    /**
     * Restores a player who was mid-spectate when the server stopped (crash or
     * restart): the PDC recovery keys survived but the in-memory state didn't.
     * Called from the join listener on the player's entity thread. Only acts when
     * the player is still in SPECTATOR (an admin may have fixed them manually);
     * always clears the keys.
     */
    fun restoreCrashedSpectator(player: Player) {
        val pdc = player.persistentDataContainer
        val gameModeName = pdc.get(prevGameModeKey, org.bukkit.persistence.PersistentDataType.STRING) ?: return
        val locationString = pdc.get(prevLocationKey, org.bukkit.persistence.PersistentDataType.STRING)
        clearRecoveryData(player)

        if (player.gameMode != GameMode.SPECTATOR) return
        val restored = runCatching { GameMode.valueOf(gameModeName) }.getOrDefault(GameMode.SURVIVAL)
        player.gameMode = restored

        val location = locationString?.split(';')?.takeIf { it.size == 6 }?.let { parts ->
            plugin.server.getWorld(parts[0])?.let { world ->
                runCatching {
                    Location(world, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(), parts[4].toFloat(), parts[5].toFloat())
                }.getOrNull()
            }
        }
        val exitMessage = plugin.getMessageComponent("spectate-exited")
        if (location != null) {
            player.teleportAsync(location).thenRun { player.sendMessage(exitMessage) }
        } else {
            player.sendMessage(exitMessage)
        }
        plugin.logger.info("[Spectator] Restored ${player.name} from an interrupted spectate session (server stopped mid-spectate).")
    }

    /**
     * Offers spectator mode to a player who died in a chamber.
     * Returns true if the offer was made.
     */
    fun offerSpectatorMode(player: Player, chamber: Chamber, deathLocation: Location): Boolean {
        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) {
            return false
        }

        if (!player.hasPermission("tcp.spectate")) {
            return false
        }

        // Don't offer if player is already spectating or has a pending offer
        if (spectators.containsKey(player.uniqueId) || pendingSpectate.containsKey(player.uniqueId)) {
            return false
        }

        // Create pending spectate data
        val data = SpectatorData(
            playerUUID = player.uniqueId,
            chamberId = chamber.id,
            chamberName = chamber.name,
            previousGameMode = player.gameMode,
            previousLocation = deathLocation.clone()
        )

        pendingSpectate[player.uniqueId] = data

        // Send offer message
        player.sendMessage(plugin.getMessageComponent("spectate-offer", "chamber" to chamber.name))
        player.sendMessage(plugin.getMessageComponent("spectate-hint"))

        // Expire the offer after configured time
        val timeoutSeconds = plugin.config.getInt("spectator-mode.offer-timeout", 30)
        plugin.scheduler.runTaskLater(Runnable {
            if (pendingSpectate.remove(player.uniqueId) != null) {
                if (player.isOnline) {
                    player.sendMessage(plugin.getMessageComponent("spectate-offer-expired"))
                }
            }
        }, (timeoutSeconds * 20).toLong())

        return true
    }

    /**
     * Accepts the spectator mode offer.
     */
    fun acceptSpectatorMode(player: Player): Boolean {
        val data = pendingSpectate.remove(player.uniqueId) ?: return false

        // Get the chamber
        val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId)
        if (chamber == null) {
            player.sendMessage(plugin.getMessageComponent("spectate-chamber-not-found"))
            return false
        }

        // Check if there are other players in the chamber
        val playersInChamber = chamber.getPlayersInside(plugin.server).filter { it.uniqueId != player.uniqueId }
        if (playersInChamber.isEmpty() && !plugin.config.getBoolean("spectator-mode.allow-solo-spectate", false)) {
            player.sendMessage(plugin.getMessageComponent("spectate-no-players"))
            return false
        }

        // Store spectator data (before async operation)
        spectators[player.uniqueId] = data

        // Folia-safe: All player operations must run on entity's region thread
        val center = chamber.getCenter()
        val startMessage = plugin.getMessageComponent("spectate-started", "chamber" to chamber.name)
        val exitHintMessage = plugin.getMessageComponent("spectate-exit-hint")

        plugin.scheduler.runAtEntity(player, Runnable {
            if (!player.isOnline) return@Runnable

            // v1.7.2: persist recovery data BEFORE switching gamemode, so a crash
            // at any later point can still restore the player.
            writeRecoveryData(player, data)

            // Put player in spectator mode (must be on entity's thread)
            player.gameMode = GameMode.SPECTATOR

            // Teleport to chamber center, then send messages once teleport completes
            player.teleportAsync(center).thenRun {
                player.sendMessage(startMessage)
                player.sendMessage(exitHintMessage)
            }
        })

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Spectator] ${player.name} is now spectating chamber ${chamber.name}")
        }

        return true
    }

    /**
     * Declines the spectator mode offer.
     */
    fun declineSpectatorMode(player: Player): Boolean {
        if (pendingSpectate.remove(player.uniqueId) != null) {
            player.sendMessage(plugin.getMessageComponent("spectate-declined"))
            return true
        }
        return false
    }

    /**
     * Exits spectator mode for a player.
     */
    fun exitSpectatorMode(player: Player, teleportToExit: Boolean = true): Boolean {
        val data = spectators.remove(player.uniqueId) ?: return false

        // Folia-safe: All player operations must run on entity's region thread
        val exitMessage = plugin.getMessageComponent("spectate-exited")
        val exitLocation = if (teleportToExit) {
            val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId)
            chamber?.getExitLocation() ?: data.previousLocation
        } else null

        plugin.scheduler.runAtEntity(player, Runnable {
            if (!player.isOnline) return@Runnable

            // Restore game mode (must be on entity's thread)
            player.gameMode = data.previousGameMode
            clearRecoveryData(player)

            // Teleport if requested, send message once complete (or immediately if no teleport)
            if (exitLocation != null) {
                player.teleportAsync(exitLocation).thenRun { player.sendMessage(exitMessage) }
            } else {
                player.sendMessage(exitMessage)
            }
        })

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Spectator] ${player.name} exited spectator mode for chamber ${data.chamberName}")
        }

        return true
    }

    /**
     * Checks if a player is spectating.
     */
    fun isSpectating(player: Player): Boolean = spectators.containsKey(player.uniqueId)

    /**
     * Checks if a player is spectating a specific chamber.
     */
    fun isSpectatingChamber(player: Player, chamberId: Int): Boolean {
        return spectators[player.uniqueId]?.chamberId == chamberId
    }

    /**
     * Gets the chamber a player is spectating, if any.
     */
    fun getSpectatingChamber(player: Player): Chamber? {
        val data = spectators[player.uniqueId] ?: return null
        return plugin.chamberManager.getCachedChamberById(data.chamberId)
    }

    /**
     * Checks if a location is within the spectator's allowed bounds.
     */
    fun isWithinSpectatorBounds(player: Player, location: Location): Boolean {
        val data = spectators[player.uniqueId] ?: return true // Not spectating, allow
        val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId) ?: return true

        // Allow some extra space around the chamber for spectators
        val buffer = plugin.config.getInt("spectator-mode.boundary-buffer", 10)
        return location.world?.name == chamber.world &&
               location.blockX >= chamber.minX - buffer &&
               location.blockX <= chamber.maxX + buffer &&
               location.blockY >= chamber.minY - buffer &&
               location.blockY <= chamber.maxY + buffer &&
               location.blockZ >= chamber.minZ - buffer &&
               location.blockZ <= chamber.maxZ + buffer
    }

    /**
     * Gets all spectators for a specific chamber.
     */
    fun getSpectatorsForChamber(chamberId: Int): List<Player> {
        return spectators.entries
            .filter { it.value.chamberId == chamberId }
            .mapNotNull { plugin.server.getPlayer(it.key) }
    }

    /**
     * Removes all spectators from a chamber (e.g., on reset).
     */
    fun exitAllSpectatorsFromChamber(chamberId: Int) {
        val toRemove = spectators.entries.filter { it.value.chamberId == chamberId }

        toRemove.forEach { (uuid, _) ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null) {
                exitSpectatorMode(player, teleportToExit = true)
            } else {
                spectators.remove(uuid)
            }
        }
    }

    /**
     * Cleans up when a player disconnects.
     *
     * v1.7.2: restores the previous gamemode/location synchronously — the quit
     * event runs on the player's thread and the player entity is still valid.
     * Previously the data was just dropped, so logging out while spectating
     * left the player in SPECTATOR (at the chamber) on their next join.
     */
    fun handlePlayerQuit(player: Player) {
        pendingSpectate.remove(player.uniqueId)
        val data = spectators.remove(player.uniqueId) ?: return
        runCatching {
            player.gameMode = data.previousGameMode
            player.teleport(data.previousLocation)
            clearRecoveryData(player)
        }.onFailure {
            plugin.logger.warning("[Spectator] Could not restore ${player.name} on quit: ${it.message} — join-time recovery will handle it.")
        }
    }

    /**
     * Checks if a player has a pending spectate offer.
     */
    fun hasPendingOffer(player: Player): Boolean = pendingSpectate.containsKey(player.uniqueId)

    /**
     * Gets all active spectators count.
     */
    fun getActiveSpectatorCount(): Int = spectators.size

    /**
     * Shuts down the spectator manager.
     */
    fun shutdown() {
        // Exit all spectators
        spectators.keys.toList().forEach { uuid ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null) {
                exitSpectatorMode(player, teleportToExit = false)
            }
        }
        spectators.clear()
        pendingSpectate.clear()
    }
}

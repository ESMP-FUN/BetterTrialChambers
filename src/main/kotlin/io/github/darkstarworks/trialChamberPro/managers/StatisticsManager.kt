package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.api.events.StatisticsUpdatedEvent
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player statistics tracking for Trial Chambers.
 * Tracks completions, vault opens, mobs killed, deaths, and time spent.
 */
class StatisticsManager(private val plugin: TrialChamberPro) {

    // In-memory cache for frequently accessed stats
    private val statsCache = ConcurrentHashMap<UUID, PlayerStats>()
    private val cacheExpiry = ConcurrentHashMap<UUID, Long>()

    /** TCP's prefixed stats table (`database.table-prefix` + `player_stats`, default `tcp_player_stats`). */
    private val statsTable get() = plugin.databaseManager.tables.playerStats

    // Per-player mutexes to prevent concurrent database loads for the same player
    private val loadingLocks = ConcurrentHashMap<UUID, Mutex>()

    /**
     * Player statistics data class
     */
    data class PlayerStats(
        val playerUuid: UUID,
        var chambersCompleted: Int = 0,
        var normalVaultsOpened: Int = 0,
        var ominousVaultsOpened: Int = 0,
        var mobsKilled: Int = 0,
        var deaths: Int = 0,
        var timeSpent: Long = 0, // in seconds
        var lastUpdated: Long = System.currentTimeMillis()
    )

    /**
     * Gets statistics for a player.
     * Uses cache with 5-minute expiry.
     * Thread-safe: uses per-player mutex to prevent concurrent database loads.
     */
    suspend fun getStats(playerUuid: UUID): PlayerStats = withContext(Dispatchers.IO) {
        // Quick check without lock (fast path for cached data)
        val expiry = cacheExpiry[playerUuid]
        val cachedStats = statsCache[playerUuid]
        if (expiry != null && cachedStats != null && System.currentTimeMillis() < expiry) {
            return@withContext cachedStats
        }

        // Get or create a mutex for this player to prevent concurrent DB loads
        val mutex = loadingLocks.computeIfAbsent(playerUuid) { Mutex() }

        mutex.withLock {
            // Double-check cache after acquiring lock (another coroutine may have loaded it)
            val expiryAfterLock = cacheExpiry[playerUuid]
            val cachedAfterLock = statsCache[playerUuid]
            if (expiryAfterLock != null && cachedAfterLock != null && System.currentTimeMillis() < expiryAfterLock) {
                return@withContext cachedAfterLock
            }

            // Load from database
            val stats = loadStatsFromDatabase(playerUuid)

            // Update cache
            statsCache[playerUuid] = stats
            cacheExpiry[playerUuid] = System.currentTimeMillis() + 300000 // 5 minutes

            stats
        }
    }

    /**
     * v1.7.2: atomic single-column increment upsert. The old read-modify-write
     * (getStats → ++ → saveStats full-row overwrite) silently lost counts when two
     * servers sharing one MySQL incremented concurrently — the database now does the
     * addition itself, same pattern as [batchAddTimeSpent]. [column] must come from
     * the hardcoded call sites below, never from user input.
     */
    private suspend fun atomicIncrement(playerUuid: UUID, column: String, amount: Long, reason: StatisticsUpdatedEvent.Reason) =
        withContext(Dispatchers.IO) {
            val isSQLite = plugin.databaseManager.databaseType ==
                io.github.darkstarworks.trialChamberPro.database.DatabaseManager.DatabaseType.SQLITE
            val sql = if (isSQLite) {
                """
                INSERT INTO $statsTable (player_uuid, $column, last_updated)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    $column = $column + excluded.$column,
                    last_updated = excluded.last_updated
                """.trimIndent()
            } else {
                """
                INSERT INTO $statsTable (player_uuid, $column, last_updated)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    $column = $column + VALUES($column),
                    last_updated = VALUES(last_updated)
                """.trimIndent()
            }
            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, playerUuid.toString())
                        stmt.setLong(2, amount)
                        stmt.setLong(3, System.currentTimeMillis())
                        stmt.executeUpdate()
                    }
                }
                invalidateCache(playerUuid)
                fireStatsUpdated(playerUuid, reason)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to increment $column for $playerUuid: ${e.message}")
            }
        }

    /**
     * Increments chambers completed for a player.
     */
    suspend fun incrementChambersCompleted(playerUuid: UUID) =
        atomicIncrement(playerUuid, "chambers_completed", 1, StatisticsUpdatedEvent.Reason.CHAMBER_COMPLETE)

    /**
     * Increments vaults opened counter (Normal or Ominous).
     */
    suspend fun incrementVaultsOpened(playerUuid: UUID, vaultType: VaultType) =
        atomicIncrement(
            playerUuid,
            when (vaultType) {
                VaultType.NORMAL -> "normal_vaults_opened"
                VaultType.OMINOUS -> "ominous_vaults_opened"
            },
            1,
            StatisticsUpdatedEvent.Reason.VAULT
        )

    /**
     * Increments mobs killed counter.
     */
    suspend fun incrementMobsKilled(playerUuid: UUID) =
        atomicIncrement(playerUuid, "mobs_killed", 1, StatisticsUpdatedEvent.Reason.MOB_KILL)

    /**
     * Increments deaths counter.
     */
    suspend fun incrementDeaths(playerUuid: UUID) =
        atomicIncrement(playerUuid, "deaths", 1, StatisticsUpdatedEvent.Reason.DEATH)

    /**
     * Adds time spent in chambers (in seconds).
     */
    suspend fun addTimeSpent(playerUuid: UUID, seconds: Long) =
        atomicIncrement(playerUuid, "time_spent", seconds, StatisticsUpdatedEvent.Reason.TIME)

    /**
     * Batch adds time spent for multiple players in a single transaction.
     * More efficient than calling addTimeSpent multiple times.
     */
    suspend fun batchAddTimeSpent(updates: Map<UUID, Long>) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext

        plugin.databaseManager.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Use database-specific upsert syntax
                val isSQLite = plugin.databaseManager.databaseType ==
                    io.github.darkstarworks.trialChamberPro.database.DatabaseManager.DatabaseType.SQLITE

                val sql = if (isSQLite) {
                    """
                    INSERT INTO $statsTable (player_uuid, time_spent, last_updated)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        time_spent = time_spent + excluded.time_spent,
                        last_updated = excluded.last_updated
                    """.trimIndent()
                } else {
                    """
                    INSERT INTO $statsTable (player_uuid, time_spent, last_updated)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        time_spent = time_spent + VALUES(time_spent),
                        last_updated = VALUES(last_updated)
                    """.trimIndent()
                }

                conn.prepareStatement(sql).use { stmt ->
                    updates.forEach { (uuid, seconds) ->
                        stmt.setString(1, uuid.toString())
                        stmt.setLong(2, seconds)
                        stmt.setLong(3, System.currentTimeMillis())
                        stmt.addBatch()
                    }

                    stmt.executeBatch()
                }
                conn.commit()

                // Invalidate cache for all updated players
                updates.keys.forEach {
                    invalidateCache(it)
                    fireStatsUpdated(it, StatisticsUpdatedEvent.Reason.TIME)
                }
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Gets top players by a specific stat.
     * @param stat The stat to rank by: "chambers", "vaults" (normal + ominous
     *   total), "normal_vaults", "ominous_vaults", "mobs", "time"
     * @param limit Number of top players to return
     */
    suspend fun getLeaderboard(stat: String, limit: Int = 10): List<Pair<UUID, Int>> = withContext(Dispatchers.IO) {
        // Whitelisted column/expression — never interpolate user input here.
        val column = when (stat.lowercase()) {
            "chambers" -> "chambers_completed"
            "vaults" -> "(normal_vaults_opened + ominous_vaults_opened)"
            "normal_vaults" -> "normal_vaults_opened"
            "ominous_vaults" -> "ominous_vaults_opened"
            "mobs" -> "mobs_killed"
            "time" -> "time_spent"
            else -> "chambers_completed"
        }

        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT player_uuid, $column AS value FROM $statsTable ORDER BY value DESC LIMIT ?"
                ).use { stmt ->
                    stmt.setInt(1, limit)

                    val results = mutableListOf<Pair<UUID, Int>>()
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val uuid = UUID.fromString(rs.getString("player_uuid"))
                            val value = rs.getInt("value")
                            results.add(uuid to value)
                        }
                    }

                    results
                }
            }
        } catch (e: Exception) {
            // Degrade gracefully: an empty board is far better than throwing into the GUI's
            // click handler ("Could not pass event"). The schema self-check logs the root cause.
            plugin.logger.warning("Leaderboard query failed (stat=$stat): ${e.message}")
            emptyList()
        }
    }

    /**
     * Resets all statistics for a player.
     */
    suspend fun resetStats(playerUuid: UUID) = withContext(Dispatchers.IO) {
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM $statsTable WHERE player_uuid = ?"
            ).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                stmt.executeUpdate()
            }
        }
        invalidateCache(playerUuid)
    }

    /**
     * Loads statistics from the database.
     * Creates a new entry if the player doesn't have stats yet.
     */
    private fun loadStatsFromDatabase(playerUuid: UUID): PlayerStats {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT * FROM $statsTable WHERE player_uuid = ?"
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())

                    stmt.executeQuery().use { rs ->
                        return if (rs.next()) {
                            PlayerStats(
                                playerUuid = playerUuid,
                                chambersCompleted = rs.getInt("chambers_completed"),
                                normalVaultsOpened = rs.getInt("normal_vaults_opened"),
                                ominousVaultsOpened = rs.getInt("ominous_vaults_opened"),
                                mobsKilled = rs.getInt("mobs_killed"),
                                deaths = rs.getInt("deaths"),
                                timeSpent = rs.getLong("time_spent"),
                                lastUpdated = rs.getLong("last_updated")
                            )
                        } else {
                            // Create new stats entry
                            val newStats = PlayerStats(playerUuid = playerUuid)
                            saveStats(newStats)
                            newStats
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Degrade gracefully on a schema/DB error: return empty stats rather than throwing.
            // The schema self-check logs the underlying cause (e.g. a drifted player_stats table).
            plugin.logger.warning("Failed to load stats for $playerUuid: ${e.message}")
            return PlayerStats(playerUuid = playerUuid)
        }
    }

    /**
     * Saves statistics to the database.
     */
    private fun saveStats(stats: PlayerStats) {
        plugin.databaseManager.connection.use { conn ->
            // Use database-specific upsert syntax
            val isSQLite = plugin.databaseManager.databaseType ==
                io.github.darkstarworks.trialChamberPro.database.DatabaseManager.DatabaseType.SQLITE

            val sql = if (isSQLite) {
                """
                INSERT OR REPLACE INTO $statsTable
                (player_uuid, chambers_completed, normal_vaults_opened, ominous_vaults_opened,
                 mobs_killed, deaths, time_spent, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """
            } else {
                """
                INSERT INTO $statsTable
                (player_uuid, chambers_completed, normal_vaults_opened, ominous_vaults_opened,
                 mobs_killed, deaths, time_spent, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    chambers_completed = VALUES(chambers_completed),
                    normal_vaults_opened = VALUES(normal_vaults_opened),
                    ominous_vaults_opened = VALUES(ominous_vaults_opened),
                    mobs_killed = VALUES(mobs_killed),
                    deaths = VALUES(deaths),
                    time_spent = VALUES(time_spent),
                    last_updated = VALUES(last_updated)
                """
            }

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, stats.playerUuid.toString())
                stmt.setInt(2, stats.chambersCompleted)
                stmt.setInt(3, stats.normalVaultsOpened)
                stmt.setInt(4, stats.ominousVaultsOpened)
                stmt.setInt(5, stats.mobsKilled)
                stmt.setInt(6, stats.deaths)
                stmt.setLong(7, stats.timeSpent)
                stmt.setLong(8, stats.lastUpdated)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Invalidates cache for a player.
     */
    private fun invalidateCache(playerUuid: UUID) {
        statsCache.remove(playerUuid)
        cacheExpiry.remove(playerUuid)
        loadingLocks.remove(playerUuid)
    }

    /**
     * Drops the cached stats for a player so the next read pulls fresh from the
     * shared database. Public cross-server cache-invalidation hook: the premium
     * Network Sync module calls this when a peer server reports it changed this
     * player's stats. Safe to call from any thread.
     */
    fun invalidatePlayer(playerUuid: UUID) = invalidateCache(playerUuid)

    /**
     * Fires [StatisticsUpdatedEvent] off the primary thread. No-op for
     * single-server installs (no listeners). The premium Network Sync module
     * listens here to broadcast cross-server invalidations.
     */
    private fun fireStatsUpdated(playerUuid: UUID, reason: StatisticsUpdatedEvent.Reason) {
        try {
            plugin.server.pluginManager.callEvent(StatisticsUpdatedEvent(playerUuid, reason))
        } catch (e: Exception) {
            plugin.logger.warning("Failed to fire StatisticsUpdatedEvent: ${e.message}")
        }
    }

    /**
     * Clears all cached statistics.
     */
    fun clearCache() {
        statsCache.clear()
        cacheExpiry.clear()
        loadingLocks.clear()
    }

    /**
     * Formats time in seconds to a human-readable string.
     */
    fun formatTime(seconds: Long): String {
        return io.github.darkstarworks.trialChamberPro.utils.MessageUtil.formatTimeSeconds(plugin, seconds)
    }
}

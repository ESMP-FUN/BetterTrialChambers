package io.github.darkstarworks.trialChamberPro.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.FileConfiguration
import java.io.File
import java.sql.Connection
import java.sql.SQLException

/**
 * Manages database connections using HikariCP connection pooling.
 * Supports both SQLite and MySQL databases.
 *
 * **v1.4.0 — open for extension.** This class is `open` so third-party
 * plugins (notably a planned premium "Network Sync" module adding Postgres
 * / MariaDB / Redis support) can subclass and register their implementation
 * via Bukkit's [org.bukkit.plugin.ServicesManager] at higher priority. The
 * instance is auto-registered at TCP startup; consumers resolve it with:
 *
 * ```kotlin
 * val dbm = Bukkit.getServicesManager().load(DatabaseManager::class.java)
 * ```
 *
 * Note: TCP itself currently still references the field
 * [TrialChamberPro.databaseManager] directly throughout its own codebase,
 * so subclass replacement only takes effect for callers that explicitly
 * resolve via the services manager. Full runtime substitution at every
 * TCP call site is planned for a future major version when the premium
 * Network Sync module is built.
 */
open class DatabaseManager(protected val plugin: TrialChamberPro) {

    private lateinit var dataSource: HikariDataSource
    private var _databaseType: DatabaseType = DatabaseType.SQLITE

    /** The type of database being used (SQLITE or MYSQL) */
    val databaseType: DatabaseType
        get() = _databaseType

    enum class DatabaseType {
        SQLITE, MYSQL
    }

    companion object {
        /**
         * TCP's player-statistics table. Namespaced with a `tcp_` prefix (v1.5.16) after a
         * shared-database collision with another plugin's generic `player_stats` table; older
         * TCP installs are migrated automatically (see `migratePlayerStatsTableName`).
         */
        const val STATS_TABLE = "tcp_player_stats"
    }

    /**
     * Initializes the database connection pool and creates tables.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val config = plugin.config
        _databaseType = try {
            DatabaseType.valueOf(config.getString("database.type", "SQLITE")!!.uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid database type, defaulting to SQLITE")
            DatabaseType.SQLITE
        }

        dataSource = when (_databaseType) {
            DatabaseType.SQLITE -> createSQLiteDataSource()
            DatabaseType.MYSQL -> createMySQLDataSource(config)
        }

        plugin.logger.info("Database connection pool initialized (${_databaseType.name})")

        // Create tables
        createTables()

        // Run schema migrations for new features
        runMigrations()

        // Verify the live schema matches what the code expects, then heal/report drift.
        // Catches tables created by a much older build (or externally) that CREATE TABLE
        // IF NOT EXISTS won't update — e.g. a player_stats table missing newer columns.
        verifyAndHealSchema()
    }

    /**
     * Creates a SQLite data source.
     */
    private fun createSQLiteDataSource(): HikariDataSource {
        val dbFile = File(plugin.dataFolder, "database.db")
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}?foreign_keys=on"
            driverClassName = "org.sqlite.JDBC"
            // WAL mode allows multiple concurrent readers (but still only 1 writer)
            // Setting pool size to 5 allows read operations to run concurrently
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30000 // 30 seconds - fail faster if pool is exhausted
            idleTimeout = 300000 // 5 minutes
            maxLifetime = 600000 // 10 minutes
            connectionTestQuery = "SELECT 1"
            poolName = "TrialChamberPro-SQLite"

            // Enable leak detection (helps identify connection leaks during development)
            leakDetectionThreshold = 10000 // 10 seconds

            // SQLite optimizations for better concurrent access
            addDataSourceProperty("journal_mode", "WAL") // Write-Ahead Logging for better concurrency
            addDataSourceProperty("synchronous", "NORMAL") // Balance between safety and speed
            addDataSourceProperty("busy_timeout", "5000") // Wait up to 5s for locks instead of failing immediately
        }
        return HikariDataSource(config)
    }

    /**
     * Creates a MySQL data source.
     */
    private fun createMySQLDataSource(config: FileConfiguration): HikariDataSource {
        val host = config.getString("database.host", "localhost")!!
        val port = config.getInt("database.port", 3306)
        val database = config.getString("database.database", "trialchamberpro")!!
        val username = config.getString("database.username", "root")!!
        val password = config.getString("database.password", "")!!
        val poolSize = config.getInt("database.pool-size", 10)

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = poolSize
            connectionTestQuery = "SELECT 1"
            poolName = "TrialChamberPro-MySQL"

            // Performance optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        }

        return HikariDataSource(hikariConfig)
    }

    /**
     * Creates all database tables if they don't exist.
     */
    private suspend fun createTables() = withContext(Dispatchers.IO) {
        connection.use { conn ->
            // Migrate TCP's own stats table to a namespaced name BEFORE creating tables,
            // so an unrelated plugin's `player_stats` table on a shared database can't be
            // mistaken for ours.
            migratePlayerStatsTableName(conn)
            conn.createStatement().use { stmt ->
                // Chambers table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS chambers (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        name VARCHAR(64) UNIQUE NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL,
                        min_y INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_y INT NOT NULL,
                        max_z INT NOT NULL,
                        exit_x DOUBLE,
                        exit_y DOUBLE,
                        exit_z DOUBLE,
                        exit_yaw FLOAT,
                        exit_pitch FLOAT,
                        snapshot_file VARCHAR(255),
                        reset_interval BIGINT NOT NULL,
                        last_reset BIGINT,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                // Vaults table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS vaults (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        type VARCHAR(16) DEFAULT 'NORMAL',
                        loot_table VARCHAR(64) NOT NULL,
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE,
                        UNIQUE (chamber_id, x, y, z, type)
                    )
                    """.trimIndent()
                )

                // Spawners table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS spawners (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        type VARCHAR(16) DEFAULT 'NORMAL',
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // Player vaults table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_vaults (
                        player_uuid VARCHAR(36) NOT NULL,
                        vault_id INT NOT NULL,
                        last_opened BIGINT NOT NULL,
                        times_opened INT DEFAULT 0,
                        PRIMARY KEY (player_uuid, vault_id),
                        FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // v1.5.7: per-player chamber container loot (chests/barrels).
                // One row per (container position, player) holding their private
                // copy of the container's contents. Cleared on chamber reset;
                // cascade-deleted with the chamber.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_container_loot (
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        contents TEXT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (chamber_id, x, y, z, player_uuid),
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // v1.5.9: shared per-container loot TEMPLATE. The canonical
                // contents each player's first-open copy is cloned from, one
                // row per container position. Materialized once (by rolling the
                // block's vanilla loot table) on first access, editable by ops
                // (sneak-open), and — unlike per-player copies — PERSISTS across
                // chamber resets so edits stick. Cascade-deleted with the chamber.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS container_template (
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        contents TEXT NOT NULL,
                        material VARCHAR(64) NOT NULL DEFAULT 'CHEST',
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (chamber_id, x, y, z),
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // Player statistics table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tcp_player_stats (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        chambers_completed INT DEFAULT 0,
                        normal_vaults_opened INT DEFAULT 0,
                        ominous_vaults_opened INT DEFAULT 0,
                        mobs_killed INT DEFAULT 0,
                        deaths INT DEFAULT 0,
                        time_spent BIGINT DEFAULT 0,
                        last_updated BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                // Create indexes for performance
                try {
                    // Use IF NOT EXISTS where supported; wrap in try/catch for MySQL which may not support it on older versions
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaults_chamber ON vaults(chamber_id)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaults_type ON vaults(type)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_vaults_player ON player_vaults(player_uuid)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_spawners_chamber ON spawners(chamber_id)")
                } catch (e: SQLException) {
                    // Fallback for databases that don't support IF NOT EXISTS
                    try { stmt.execute("CREATE INDEX idx_vaults_chamber ON vaults(chamber_id)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_vaults_type ON vaults(type)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_player_vaults_player ON player_vaults(player_uuid)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_spawners_chamber ON spawners(chamber_id)") } catch (_: SQLException) {}
                }
            }
        }
        plugin.logger.info("Database tables created/verified successfully")
    }

    /**
     * Runs schema migrations for new features.
     * Each migration is idempotent (safe to run multiple times).
     */
    private suspend fun runMigrations() = withContext(Dispatchers.IO) {
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // v1.2.7: Per-chamber loot tables
                listOf(
                    "ALTER TABLE chambers ADD COLUMN normal_loot_table VARCHAR(64)",
                    "ALTER TABLE chambers ADD COLUMN ominous_loot_table VARCHAR(64)"
                ).forEach { sql ->
                    try {
                        stmt.execute(sql)
                        plugin.logger.info("Migration executed: $sql")
                    } catch (_: SQLException) {
                        // Column already exists - this is expected on subsequent runs
                    }
                }

                // v1.2.13: Per-chamber spawner cooldown
                try {
                    stmt.execute("ALTER TABLE chambers ADD COLUMN spawner_cooldown_minutes INT")
                    plugin.logger.info("Migration executed: Added spawner_cooldown_minutes column")
                } catch (_: SQLException) {
                    // Column already exists - this is expected on subsequent runs
                }

                // v1.3.0: Custom mob provider + per-chamber mob id lists (JSON-encoded)
                listOf(
                    "ALTER TABLE chambers ADD COLUMN custom_mob_provider VARCHAR(32)",
                    "ALTER TABLE chambers ADD COLUMN custom_mob_ids_normal TEXT",
                    "ALTER TABLE chambers ADD COLUMN custom_mob_ids_ominous TEXT"
                ).forEach { sql ->
                    try {
                        stmt.execute(sql)
                        plugin.logger.info("Migration executed: $sql")
                    } catch (_: SQLException) {
                        // Column already exists - this is expected on subsequent runs
                    }
                }

                // v1.4.3: Chamber paused state — keeps the DB record while suspending all active behavior
                try {
                    stmt.execute("ALTER TABLE chambers ADD COLUMN is_paused BOOLEAN NOT NULL DEFAULT 0")
                    plugin.logger.info("Migration executed: Added is_paused column")
                } catch (_: SQLException) {
                    // Column already exists
                }

                // v1.4.4: Per-chamber reset-complete broadcast toggle
                try {
                    stmt.execute("ALTER TABLE chambers ADD COLUMN broadcast_reset_complete BOOLEAN NOT NULL DEFAULT 1")
                    plugin.logger.info("Migration executed: Added broadcast_reset_complete column")
                } catch (_: SQLException) {
                    // Column already exists
                }

                // v1.5.11: container-template icon material (the GUI shows each
                // template as its real container block instead of a generic chest)
                try {
                    stmt.execute("ALTER TABLE container_template ADD COLUMN material VARCHAR(64) NOT NULL DEFAULT 'CHEST'")
                    plugin.logger.info("Migration executed: Added container_template.material column")
                } catch (_: SQLException) {
                    // Column already exists (or table not yet present on a fresh install)
                }
            }
        }
    }

    /**
     * `player_stats` columns the self-check can safely add to an older table (name → the
     * column definition for `ALTER TABLE ADD COLUMN`). The primary key (`player_uuid`) is
     * intentionally absent — it can't be added to a populated table, so its absence is
     * reported loudly instead.
     */
    private val healablePlayerStatsColumns = linkedMapOf(
        "chambers_completed" to "INT DEFAULT 0",
        "normal_vaults_opened" to "INT DEFAULT 0",
        "ominous_vaults_opened" to "INT DEFAULT 0",
        "mobs_killed" to "INT DEFAULT 0",
        "deaths" to "INT DEFAULT 0",
        "time_spent" to "BIGINT DEFAULT 0",
        "last_updated" to "BIGINT NOT NULL DEFAULT 0",
    )

    /** Every table TCP owns, for the schema report. */
    private val knownTables = listOf(
        "chambers", "vaults", "spawners", "player_vaults",
        "player_container_loot", "container_template", STATS_TABLE,
    )

    /**
     * Compares the live schema to what the code expects and acts on drift: adds any missing
     * (safe) `player_stats` columns, and logs a loud, actionable warning if a critical column
     * such as the `player_uuid` primary key is missing (which means the table predates the
     * current schema and stats can't work until it's reconciled).
     */
    private suspend fun verifyAndHealSchema() = withContext(Dispatchers.IO) {
        connection.use { conn ->
            val statsCols = actualColumns(conn, STATS_TABLE)
            if (statsCols.isEmpty()) return@use // table absent or metadata unavailable

            if ("player_uuid" !in statsCols) {
                plugin.logger.severe("[schema] '$STATS_TABLE' is MISSING its 'player_uuid' column (found: ${statsCols.joinToString(", ")}).")
                plugin.logger.severe("[schema] Stats saving and leaderboards cannot work until this is reconciled.")
                plugin.logger.severe("[schema] Back up the table, then rename its id column to 'player_uuid', or drop '$STATS_TABLE' so TCP recreates it. See /tcp debug schema.")
                return@use
            }

            conn.createStatement().use { stmt ->
                for ((name, ddl) in healablePlayerStatsColumns) {
                    if (name in statsCols) continue
                    try {
                        stmt.execute("ALTER TABLE $STATS_TABLE ADD COLUMN $name $ddl")
                        plugin.logger.warning("[schema] Added missing '$STATS_TABLE' column '$name'.")
                    } catch (e: SQLException) {
                        plugin.logger.warning("[schema] Could not add '$STATS_TABLE' column '$name': ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Keeps TCP's stats table namespaced as [STATS_TABLE] (`tcp_player_stats`) without
     * disturbing any other plugin's `player_stats` table on a shared database.
     *
     * - **Recovery (for the 1.5.16 metadata bug):** if a `tcp_player_stats` exists but has no
     *   `player_uuid` column, it isn't ours — it was wrongly renamed from a foreign `player_stats`
     *   by a bug in `actualColumns`. If the `player_stats` name is free, rename it back so the
     *   other plugin gets its table (and data) returned; TCP then creates its own fresh table.
     * - **Adopt:** if a legacy `player_stats` is unmistakably TCP's (has `player_uuid`) and the
     *   namespaced table doesn't exist, rename it to [STATS_TABLE] (preserves existing TCP stats).
     * - A foreign `player_stats` is otherwise left completely untouched.
     */
    private fun migratePlayerStatsTableName(conn: Connection) {
        val tcpCols = actualColumns(conn, STATS_TABLE)
        if (tcpCols.isNotEmpty()) {
            if ("player_uuid" in tcpCols) return // ours already — nothing to do
            // Not ours: a foreign table got renamed here by the 1.5.16 bug. Put it back.
            if (actualColumns(conn, "player_stats").isEmpty()) {
                try {
                    conn.createStatement().use { it.execute("ALTER TABLE $STATS_TABLE RENAME TO player_stats") }
                    plugin.logger.warning("Restored a non-TCP table that a 1.5.16 bug renamed to '$STATS_TABLE' back to 'player_stats'. TCP will use its own fresh '$STATS_TABLE'.")
                } catch (e: SQLException) {
                    plugin.logger.severe("Could not restore '$STATS_TABLE' back to 'player_stats': ${e.message}. Manual reconciliation needed.")
                    return
                }
                // fall through: with player_stats restored, the adopt step below sees a foreign
                // table (no player_uuid) and leaves it, and createTables makes a fresh STATS_TABLE.
            } else {
                plugin.logger.severe("'$STATS_TABLE' exists but isn't TCP's, and a 'player_stats' table also exists — can't auto-reconcile. TCP stats are disabled until resolved; see /tcp debug schema.")
                return
            }
        }
        // Adopt a legacy TCP table; leave a foreign one alone.
        val legacy = actualColumns(conn, "player_stats")
        if (legacy.isEmpty() || "player_uuid" !in legacy) return
        try {
            conn.createStatement().use { it.execute("ALTER TABLE player_stats RENAME TO $STATS_TABLE") }
            plugin.logger.info("Renamed TCP's 'player_stats' table to '$STATS_TABLE' (avoids collisions with other plugins on shared databases).")
        } catch (e: SQLException) {
            plugin.logger.warning("Could not rename legacy 'player_stats' table to '$STATS_TABLE': ${e.message}")
        }
    }

    /**
     * Actual column names (lowercased) of [table] via JDBC metadata, or an empty set when the
     * table is absent or metadata is unavailable. Works for both SQLite and MySQL/MariaDB.
     */
    fun actualColumns(conn: Connection, table: String): Set<String> {
        val cols = linkedSetOf<String>()
        val catalog = try { conn.catalog } catch (_: Exception) { null }
        try {
            conn.metaData.getColumns(catalog, null, table, null).use { rs ->
                while (rs.next()) {
                    // CRITICAL: getColumns() treats '_' and '%' in the table-name pattern as
                    // SQL wildcards, so it can return rows for OTHER tables (e.g. the pattern
                    // "player_stats" also matches "playerXstats"). Filter to the exact table —
                    // and to the current catalog — so we never union foreign tables' columns.
                    val rowTable = rs.getString("TABLE_NAME") ?: continue
                    if (!rowTable.equals(table, ignoreCase = true)) continue
                    if (catalog != null) {
                        val rowCat = rs.getString("TABLE_CAT")
                        if (rowCat != null && !rowCat.equals(catalog, ignoreCase = true)) continue
                    }
                    rs.getString("COLUMN_NAME")?.let { cols += it.lowercase() }
                }
            }
        } catch (_: Exception) {
            // metadata not available — leave empty
        }
        return cols
    }

    /** Snapshot of every TCP table's live columns, for `/tcp debug schema`. */
    suspend fun describeSchema(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        connection.use { conn -> knownTables.associateWith { actualColumns(conn, it).toList() } }
    }

    /**
     * Gets a connection from the pool.
     */
    val connection: Connection
        get() = dataSource.connection

    /**
     * Executes a database operation with proper connection handling and error logging.
     * HIGH PRIORITY FIX: Prevents connection leaks from exceptions before .use block.
     *
     * @param block The database operation to execute with the connection
     * @return The result of the database operation
     * @throws SQLException if the database operation fails
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                connection.use { conn -> block(conn) }
            } catch (e: SQLException) {
                plugin.logger.severe("Database operation failed: ${e.message}")
                throw e
            }
        }

    /**
     * Closes the database connection pool.
     */
    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("Database connection pool closed")
        }
    }

    /**
     * Tests if the database connection is valid.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        rs.next()
                    }
                }
            }
            true
        } catch (e: SQLException) {
            plugin.logger.severe("Database connection test failed: ${e.message}")
            false
        }
    }
}

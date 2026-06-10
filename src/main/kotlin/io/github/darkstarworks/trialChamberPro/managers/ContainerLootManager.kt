package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID

/**
 * Per-player chamber container loot (v1.5.7, Lootr-style).
 *
 * Backs the opt-in `chests.per-player-loot` feature: every player gets a
 * private copy of a chamber container's contents, stored one row per
 * (container position, player) in `player_container_loot`. The real block's
 * inventory is never modified — it stays the pristine template every new
 * player's copy is cloned from.
 *
 * Lifecycle: rows are cleared per chamber on reset ([clearChamber], called
 * from ResetManager) and cascade-deleted with the chamber row.
 *
 * Contents encoding: base64 of a length-prefixed sequence of
 * `ItemStack.serializeAsBytes()` blobs (slot-faithful; -1 marks an empty
 * slot). Deliberately NOT Java serialization.
 */
class ContainerLootManager(private val plugin: TrialChamberPro) {

    data class ContainerPos(val x: Int, val y: Int, val z: Int)

    /**
     * Loads a player's private contents for a container, or null when they
     * have no copy yet (first open).
     */
    suspend fun loadContents(
        chamberId: Int,
        pos: ContainerPos,
        player: UUID
    ): Array<ItemStack?>? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT contents FROM player_container_loot WHERE chamber_id = ? AND x = ? AND y = ? AND z = ? AND player_uuid = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.setString(5, player.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) decodeContents(rs.getString("contents")) else null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Load failed (${pos.x},${pos.y},${pos.z}/$player): ${e.message}")
            null
        }
    }

    /** Persists a player's private contents for a container (upsert). */
    suspend fun saveContents(
        chamberId: Int,
        pos: ContainerPos,
        player: UUID,
        contents: Array<ItemStack?>
    ) = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            """
            INSERT INTO player_container_loot (chamber_id, x, y, z, player_uuid, contents, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE contents = VALUES(contents), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO player_container_loot (chamber_id, x, y, z, player_uuid, contents, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(chamber_id, x, y, z, player_uuid)
            DO UPDATE SET contents = excluded.contents, updated_at = excluded.updated_at
            """.trimIndent()
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.setString(5, player.toString())
                    stmt.setString(6, encoded)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Save failed (${pos.x},${pos.y},${pos.z}/$player): ${e.message}")
        }
    }

    /**
     * Drops every player's container copies for a chamber — fresh loot for
     * everyone after a reset. Cheap no-op when the feature is unused.
     */
    suspend fun clearChamber(chamberId: Int) = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM player_container_loot WHERE chamber_id = ?").use { stmt ->
                    stmt.setInt(1, chamberId)
                    val n = stmt.executeUpdate()
                    if (n > 0 && plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[ContainerLoot] Cleared $n per-player container copies for chamber $chamberId")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Clear failed for chamber $chamberId: ${e.message}")
        }
    }

    // ==== Encoding ====

    fun encodeContents(contents: Array<ItemStack?>): String {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(contents.size)
            for (item in contents) {
                if (item == null || item.type.isAir) {
                    out.writeInt(-1)
                } else {
                    val bytes = item.serializeAsBytes()
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun decodeContents(encoded: String): Array<ItemStack?>? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val size = input.readInt()
            require(size in 0..128) { "implausible container size $size" }
            Array(size) {
                val len = input.readInt()
                if (len < 0) null
                else {
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    ItemStack.deserializeBytes(buf)
                }
            }
        }
    } catch (e: Exception) {
        plugin.logger.warning("[ContainerLoot] Corrupt contents row ignored: ${e.message}")
        null
    }
}

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
     * Loads the shared template (the canonical contents every first-open copy
     * is cloned from) for a container, or null when none has been materialized
     * yet. v1.5.9.
     */
    suspend fun loadTemplate(
        chamberId: Int,
        pos: ContainerPos
    ): Array<ItemStack?>? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT contents FROM container_template WHERE chamber_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) decodeContents(rs.getString("contents")) else null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template load failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
            null
        }
    }

    /**
     * Persists the shared template for a container (upsert). [material] is the
     * container's block type, stored so the management GUI can show each
     * template as its real container icon (v1.5.11). v1.5.9.
     */
    suspend fun saveTemplate(
        chamberId: Int,
        pos: ContainerPos,
        contents: Array<ItemStack?>,
        material: org.bukkit.Material
    ) = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            """
            INSERT INTO container_template (chamber_id, x, y, z, contents, material, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE contents = VALUES(contents), material = VALUES(material), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO container_template (chamber_id, x, y, z, contents, material, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(chamber_id, x, y, z)
            DO UPDATE SET contents = excluded.contents, material = excluded.material, updated_at = excluded.updated_at
            """.trimIndent()
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.setString(5, encoded)
                    stmt.setString(6, material.name)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template save failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
        }
    }

    /**
     * Updates only the CONTENTS of an existing template (op edit), preserving
     * its stored [material] icon. Used by the close handler when an op finishes
     * editing a template. v1.5.11.
     */
    suspend fun updateTemplateContents(
        chamberId: Int,
        pos: ContainerPos,
        contents: Array<ItemStack?>
    ) = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE container_template SET contents = ?, updated_at = ? WHERE chamber_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setString(1, encoded)
                    stmt.setLong(2, System.currentTimeMillis())
                    stmt.setInt(3, chamberId)
                    stmt.setInt(4, pos.x)
                    stmt.setInt(5, pos.y)
                    stmt.setInt(6, pos.z)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template content update failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
        }
    }

    /** One stored template: its position, decoded contents, and container icon. */
    data class TemplateRow(
        val pos: ContainerPos,
        val contents: Array<ItemStack?>,
        val material: org.bukkit.Material
    )

    /** Lists every materialized template for a chamber (for the GUI/command). */
    suspend fun listTemplates(chamberId: Int): List<TemplateRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TemplateRow>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT x, y, z, contents, material FROM container_template WHERE chamber_id = ? ORDER BY x, y, z"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pos = ContainerPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                            val contents = decodeContents(rs.getString("contents")) ?: arrayOfNulls(0)
                            val material = runCatching { org.bukkit.Material.valueOf(rs.getString("material")) }
                                .getOrDefault(org.bukkit.Material.CHEST)
                            out.add(TemplateRow(pos, contents, material))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] listTemplates failed for chamber $chamberId: ${e.message}")
        }
        out
    }

    /** Whether a template already exists for a container position. */
    suspend fun hasTemplate(chamberId: Int, pos: ContainerPos): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT 1 FROM container_template WHERE chamber_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] hasTemplate failed: ${e.message}")
            false
        }
    }

    /** Counts a chamber's per-player container copies. */
    suspend fun countPlayerCopies(chamberId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT COUNT(*) FROM player_container_loot WHERE chamber_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] countPlayerCopies failed: ${e.message}")
            0
        }
    }

    /** Deletes every shared template for a chamber (they re-materialize on next access). Returns rows removed. */
    suspend fun clearTemplates(chamberId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM container_template WHERE chamber_id = ?").use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] clearTemplates failed for chamber $chamberId: ${e.message}")
            0
        }
    }

    /** Deletes a single container's template. */
    suspend fun deleteTemplate(chamberId: Int, pos: ContainerPos): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "DELETE FROM container_template WHERE chamber_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, pos.x)
                    stmt.setInt(3, pos.y)
                    stmt.setInt(4, pos.z)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] deleteTemplate failed: ${e.message}")
            false
        }
    }

    /**
     * Drops every player's container copies for a chamber — fresh loot for
     * everyone after a reset. Shared templates are intentionally KEPT (op edits
     * persist across resets). Cheap no-op when the feature is unused. Returns
     * the number of copies removed.
     */
    suspend fun clearChamber(chamberId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM player_container_loot WHERE chamber_id = ?").use { stmt ->
                    stmt.setInt(1, chamberId)
                    val n = stmt.executeUpdate()
                    if (n > 0 && plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[ContainerLoot] Cleared $n per-player container copies for chamber $chamberId")
                    }
                    n
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Clear failed for chamber $chamberId: ${e.message}")
            0
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

package io.github.darkstarworks.trialChamberPro.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TableNamesTest {

    @Test
    fun `default prefix produces tcp_ names, stats matches pre-1_7 name`() {
        val t = TableNames.of("tcp_")
        assertEquals("tcp_chambers", t.chambers)
        assertEquals("tcp_vaults", t.vaults)
        assertEquals("tcp_spawners", t.spawners)
        assertEquals("tcp_player_vaults", t.playerVaults)
        assertEquals("tcp_player_container_loot", t.playerContainerLoot)
        assertEquals("tcp_container_template", t.containerTemplate)
        // Must equal the pre-1.7.0 namespaced stats table so no stats migration is needed.
        assertEquals(DatabaseManager.LEGACY_NAMESPACED_STATS_TABLE, t.playerStats)
    }

    @Test
    fun `null or missing falls back to default`() {
        assertEquals("tcp_", TableNames.of(null).prefix)
    }

    @Test
    fun `custom valid prefix is used as-is`() {
        val t = TableNames.of("myserver_")
        assertEquals("myserver_chambers", t.chambers)
        assertEquals("myserver_player_stats", t.playerStats)
    }

    @Test
    fun `empty prefix is valid (opt-out)`() {
        val t = TableNames.of("")
        assertEquals("chambers", t.chambers)
        assertEquals("player_stats", t.playerStats)
    }

    @Test
    fun `injection and invalid characters fall back to default`() {
        for (bad in listOf("tcp_; DROP TABLE x;--", "pre fix", "a-b", "prefix\"", "läng")) {
            assertFalse(TableNames.isValid(bad), bad)
            assertEquals(TableNames.DEFAULT_PREFIX, TableNames.of(bad).prefix, bad)
        }
    }

    @Test
    fun `oversize prefix falls back to default`() {
        val long17 = "a".repeat(17)
        assertFalse(TableNames.isValid(long17))
        assertEquals(TableNames.DEFAULT_PREFIX, TableNames.of(long17).prefix)
        assertTrue(TableNames.isValid("a".repeat(16)))
    }

    @Test
    fun `all lists every table parent-first`() {
        val t = TableNames.of("tcp_")
        assertEquals(
            listOf(
                "tcp_chambers", "tcp_vaults", "tcp_spawners", "tcp_player_vaults",
                "tcp_player_container_loot", "tcp_container_template", "tcp_player_stats"
            ),
            t.all
        )
        // chambers (FK parent) must precede its children
        assertTrue(t.all.indexOf(t.chambers) < t.all.indexOf(t.vaults))
    }
}

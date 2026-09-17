package com.esmpfun.bettertrialchambers

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.potion.PotionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

/**
 * Guards the loot tables the plugin ships in `loot.yml`.
 *
 * These two tables are Minecraft's own Trial Chamber loot, generated from the
 * game's own files rather than typed out by hand. Hand transcription is exactly
 * how the previous pair drifted into being invented loot that only claimed to
 * be vanilla, so the shape and the totals are pinned here.
 *
 * The checks deliberately stay on the file itself and the enum values, because
 * those need no running server, which keeps this a plain unit test.
 */
class ShippedLootTablesTest {

    private fun shippedLoot(): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream("loot.yml")
        assertNotNull(stream, "loot.yml should be on the classpath as a plugin resource")
        return YamlConfiguration.loadConfiguration(InputStreamReader(stream!!, Charsets.UTF_8))
    }

    private fun pools(table: String): List<Map<*, *>> {
        val section = shippedLoot().getConfigurationSection("loot-tables.$table")
        assertNotNull(section, "loot.yml should define the '$table' table")
        @Suppress("UNCHECKED_CAST")
        return section!!.getList("pools") as List<Map<*, *>>
    }

    private fun items(table: String): List<Map<*, *>> {
        @Suppress("UNCHECKED_CAST")
        return pools(table).flatMap { it["weighted-items"] as List<Map<*, *>> }
    }

    @Test
    fun `both vault tables ship and use the three-pool vanilla shape`() {
        for (table in listOf("default", "ominous-default")) {
            val p = pools(table)
            assertEquals(3, p.size, "$table should have vanilla's three pools")
            assertEquals(listOf("main-reward", "extra-supplies", "unique"), p.map { it["name"] })
            // Vanilla gives one main reward, then one to three everyday items.
            assertEquals(1, p[0]["min-rolls"]); assertEquals(1, p[0]["max-rolls"])
            assertEquals(1, p[1]["min-rolls"]); assertEquals(3, p[1]["max-rolls"])
            assertEquals(1, p[2]["min-rolls"]); assertEquals(1, p[2]["max-rolls"])
        }
    }

    @Test
    fun `the standout pool runs as often as vanilla runs it`() {
        // A quarter of the time for a normal vault, three quarters for an
        // ominous one. Before pools had a chance this was faked with
        // min-rolls 0 / max-rolls 1, which lands near half.
        assertEquals(0.25, pools("default")[2]["chance"])
        assertEquals(0.75, pools("ominous-default")[2]["chance"])
    }

    @Test
    fun `the main pool splits eighty-twenty between rare and everyday items`() {
        for (table in listOf("default", "ominous-default")) {
            @Suppress("UNCHECKED_CAST")
            val weights = (pools(table)[0]["weighted-items"] as List<Map<*, *>>)
                .map { (it["weight"] as Number).toDouble() }
            // Weights in that pool are written as a share of 100, so they add up
            // to it, give or take the rounding to two decimal places.
            assertEquals(100.0, weights.sum(), 0.05, "$table main-reward weights should total 100")
        }
    }

    @Test
    fun `every shipped item names a real material, potion and enchantment`() {
        val enchantmentNames = Regex("^[A-Z0-9_]+$")
        var checked = 0
        for (table in listOf("default", "ominous-default")) {
            for (item in items(table)) {
                val type = item["type"] as String
                assertTrue(
                    Material.entries.any { it.name == type },
                    "'$type' in $table is not a Material"
                )
                (item["potion-type"] as String?)?.let { potion ->
                    assertTrue(
                        PotionType.entries.any { it.name == potion },
                        "'$potion' in $table is not a PotionType"
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val enchantEntries = (item["random-enchantment-pool"] as List<String>? ?: emptyList()) +
                    (item["enchantments"] as List<String>? ?: emptyList())
                for (entry in enchantEntries) {
                    val name = entry.substringBefore(':')
                    assertTrue(
                        enchantmentNames.matches(name),
                        "'$entry' in $table is not NAME:level or NAME:min:max"
                    )
                }
                checked++
            }
        }
        assertTrue(checked >= 50, "expected the full vanilla item set, only saw $checked")
    }

    @Test
    fun `enchanted books are books that store an enchantment, not enchanted books`() {
        // Vanilla hands out books carrying an enchantment for an anvil. Anything
        // here with enchantment data has to be an ENCHANTED_BOOK for the plugin
        // to put that enchantment in the slot an anvil reads.
        for (table in listOf("default", "ominous-default")) {
            for (item in items(table)) {
                val hasEnchantData = item.containsKey("random-enchantment-pool") ||
                    item.containsKey("enchantments")
                if (hasEnchantData) {
                    assertEquals(
                        "ENCHANTED_BOOK", item["type"],
                        "an item carrying enchantment data in $table should be an ENCHANTED_BOOK"
                    )
                }
            }
        }
    }
}

package com.esmpfun.bettertrialchambers.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the v2.0.2 independent-roll-mode model surface: the [LootRollMode]
 * config parser and the legacy-table → synthesized-pool passthrough in
 * [LootTable.getEffectivePools]. The generation math itself lives in LootManager
 * (needs a live Player), but these pin the pure decisions the engine depends on.
 */
class LootRollModeTest {

    @Test
    fun `fromConfig defaults to weighted for null, blank and unknown`() {
        assertEquals(LootRollMode.WEIGHTED, LootRollMode.fromConfig(null))
        assertEquals(LootRollMode.WEIGHTED, LootRollMode.fromConfig(""))
        assertEquals(LootRollMode.WEIGHTED, LootRollMode.fromConfig("nonsense"))
        assertEquals(LootRollMode.WEIGHTED, LootRollMode.fromConfig("weighted"))
    }

    @Test
    fun `fromConfig parses independent case-insensitively and trims`() {
        assertEquals(LootRollMode.INDEPENDENT, LootRollMode.fromConfig("independent"))
        assertEquals(LootRollMode.INDEPENDENT, LootRollMode.fromConfig("INDEPENDENT"))
        assertEquals(LootRollMode.INDEPENDENT, LootRollMode.fromConfig("Independent"))
        assertEquals(LootRollMode.INDEPENDENT, LootRollMode.fromConfig("  independent  "))
    }

    @Test
    fun `isKnown distinguishes valid modes from typos`() {
        assertTrue(LootRollMode.isKnown("weighted"))
        assertTrue(LootRollMode.isKnown("independent"))
        assertTrue(LootRollMode.isKnown("INDEPENDENT"))
        assertFalse(LootRollMode.isKnown(null))
        assertFalse(LootRollMode.isKnown("bogus"))
    }

    @Test
    fun `legacy table carries rollMode and maxItems into the synthesized pool`() {
        val table = LootTable(
            name = "t",
            minRolls = 1,
            maxRolls = 1,
            rollMode = LootRollMode.INDEPENDENT,
            maxItems = 3
        )
        val pools = table.getEffectivePools()
        assertEquals(1, pools.size)
        assertEquals("main", pools[0].name)
        assertEquals(LootRollMode.INDEPENDENT, pools[0].rollMode)
        assertEquals(3, pools[0].maxItems)
    }

    @Test
    fun `legacy table defaults to weighted mode with no cap`() {
        val pool = LootTable(name = "t").getEffectivePools().single()
        assertEquals(LootRollMode.WEIGHTED, pool.rollMode)
        assertEquals(0, pool.maxItems)
    }

    @Test
    fun `multi-pool table preserves each pool's own mode`() {
        val table = LootTable(
            name = "t",
            pools = listOf(
                LootPool("a", 1, 1, rollMode = LootRollMode.WEIGHTED),
                LootPool("b", 1, 1, rollMode = LootRollMode.INDEPENDENT, maxItems = 2)
            )
        )
        val pools = table.getEffectivePools()
        assertEquals(LootRollMode.WEIGHTED, pools[0].rollMode)
        assertEquals(LootRollMode.INDEPENDENT, pools[1].rollMode)
        assertEquals(2, pools[1].maxItems)
    }
}

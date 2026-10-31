package com.esmpfun.bettertrialchambers.utils

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventedNamesTest {

    @Test
    fun `every name comes once, then it runs out`() {
        val taken = mutableSetOf<String>()
        while (true) {
            val name = InventedNames.next(taken) ?: break
            assertTrue(taken.add(name.lowercase()), "$name came twice")
        }
        assertTrue(taken.size > 1000, "only ${taken.size} names")
        assertTrue(taken.none { Regex("(.)\\1\\1").containsMatchIn(it) }, "a name has a tripled letter")
        assertNull(InventedNames.next(taken))
    }
}

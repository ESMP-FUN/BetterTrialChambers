package com.esmpfun.bettertrialchambers.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DisplayTextTest {

    @Test
    fun `colour and format are kept`() {
        assertEquals("&6The Copper Vaults", DisplayText.sanitize("&6The Copper Vaults"))
        assertEquals("<gold><bold>Deep Halls</bold></gold>", DisplayText.sanitize("<gold><bold>Deep Halls</bold></gold>"))
    }

    @Test
    fun `a clickable name cannot be smuggled into an announcement`() {
        val cleaned = DisplayText.sanitize("<click:run_command:'/op me'>Free stuff</click>")
        assertEquals("Free stuff", cleaned)
        listOf("click", "hover", "insertion").forEach {
            assertFalse(cleaned.contains(it, ignoreCase = true), "$it should be gone")
        }
    }

    @Test
    fun `a name cannot fake extra lines`() {
        assertEquals("Chamber Server: you are banned", DisplayText.sanitize("Chamber<newline>Server: you are banned"))
        assertEquals("Chamber second line", DisplayText.sanitize("Chamber \n second line"))
    }
}

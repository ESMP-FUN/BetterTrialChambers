package com.esmpfun.bettertrialchambers.utils

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlowTeamsTest {

    @Test
    fun `shipped normal glow colour maps to yellow`() {
        assertEquals(NamedTextColor.YELLOW, GlowTeams.nearest(Color.fromRGB(0xFFFF55)))
    }

    @Test
    fun `shipped ominous glow colour maps to a purple`() {
        val named = GlowTeams.nearest(Color.fromRGB(0xA020F0))
        assertEquals(true, named == NamedTextColor.DARK_PURPLE || named == NamedTextColor.LIGHT_PURPLE)
    }

    @Test
    fun `an exact team colour maps to itself`() {
        for (named in NamedTextColor.NAMES.values()) {
            assertEquals(named, GlowTeams.nearest(Color.fromRGB(named.value())))
        }
    }
}

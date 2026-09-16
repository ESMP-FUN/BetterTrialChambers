package com.esmpfun.bettertrialchambers.integrations.claims

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

import com.esmpfun.bettertrialchambers.integrations.claims.fake.internalLand

class ReflTest {

    @Test
    fun `a method on a public interface can be called on a non-public implementation`() {
        assertEquals("Spawn Town", Refl.call(internalLand(), "getName"))
    }

    @Test
    fun `a missing method is null rather than an error`() {
        assertNull(Refl.call(internalLand(), "getOwnerName"))
    }

    @Test
    fun `a null target is null`() {
        assertNull(Refl.call(null, "getName"))
    }

    @Test
    fun `a class that is not on the server is null rather than an error`() {
        assertNull(Refl.classOrNull("me.angeschossen.lands.api.LandsIntegration"))
    }
}

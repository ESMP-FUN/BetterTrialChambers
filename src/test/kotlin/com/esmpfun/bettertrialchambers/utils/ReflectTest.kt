package com.esmpfun.bettertrialchambers.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

import com.esmpfun.bettertrialchambers.utils.fake.internalLand

class ReflectTest {

    @Test
    fun `a method on a public interface can be called on a non-public implementation`() {
        assertEquals("Spawn Town", Reflect.callNoArg(internalLand(), "getName"))
    }

    @Test
    fun `a missing method is null rather than an error`() {
        assertNull(Reflect.callNoArg(internalLand(), "getOwnerName"))
    }

    @Test
    fun `a null target is null`() {
        assertNull(Reflect.callNoArg(null, "getName"))
    }

    @Test
    fun `a class that is not on the server is null rather than an error`() {
        assertNull(Reflect.classOrNull("me.angeschossen.lands.api.LandsIntegration"))
    }
}

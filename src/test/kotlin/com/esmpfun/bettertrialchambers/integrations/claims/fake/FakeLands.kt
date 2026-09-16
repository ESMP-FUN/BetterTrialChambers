package com.esmpfun.bettertrialchambers.integrations.claims.fake

/** The shape a claim plugin's API takes: a public interface. */
interface FakeLand {
    fun getName(): String
}

/**
 * The shape their implementations take: package-private, in a package of their
 * own, so nothing outside it may invoke a method found on this class itself.
 */
private class InternalLand : FakeLand {
    override fun getName(): String = "Spawn Town"
}

/** Hands back the implementation typed as the interface, exactly as those APIs do. */
fun internalLand(): FakeLand = InternalLand()

package com.esmpfun.bettertrialchambers.utils

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * Reads Java-serialized plugin files, including ones saved before the rebrand, whose
 * classes still carry the old TrialChamberPro package name.
 */
class RenamedClassInputStream(input: InputStream) : ObjectInputStream(input) {

    override fun resolveClass(desc: ObjectStreamClass): Class<*> {
        val name = desc.name
        if (!name.startsWith(OLD_PACKAGE)) return super.resolveClass(desc)
        return Class.forName(NEW_PACKAGE + name.removePrefix(OLD_PACKAGE), false, javaClass.classLoader)
    }

    companion object {
        const val OLD_PACKAGE = "io.github.darkstarworks.trialChamberPro."
        const val NEW_PACKAGE = "com.esmpfun.bettertrialchambers."
    }
}

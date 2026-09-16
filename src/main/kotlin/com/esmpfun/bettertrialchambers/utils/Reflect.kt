package com.esmpfun.bettertrialchambers.utils

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Calling another plugin's API without building against it.
 *
 * Every one of these plugins hands back objects typed as a public interface
 * whose implementing class is not itself public. Asking the object's own class
 * for a method finds it, and Java then refuses to let anyone outside that class
 * call it, so the whole integration does nothing and says nothing. Looking for
 * the same method on a public interface or parent class first is what makes the
 * call go through.
 */
object Reflect {

    /** Calls a no-arg [name] on [target], or null if that is not possible. */
    fun callNoArg(target: Any?, name: String): Any? = try {
        target?.let { noArg(it.javaClass, name)?.invoke(it) }
    } catch (_: Throwable) {
        null
    }

    /** A no-arg [name] on [type] that can actually be invoked, or null. */
    fun noArg(type: Class<*>, name: String): Method? {
        val direct = try { type.getMethod(name) } catch (_: Throwable) { return null }
        if (Modifier.isPublic(direct.declaringClass.modifiers)) return direct

        var c: Class<*>? = type
        while (c != null) {
            for (iface in c.interfaces) {
                if (!Modifier.isPublic(iface.modifiers)) continue
                val m = try { iface.getMethod(name) } catch (_: Throwable) { null }
                if (m != null) return m
            }
            if (Modifier.isPublic(c.modifiers)) {
                val m = try { c.getMethod(name) } catch (_: Throwable) { null }
                if (m != null && Modifier.isPublic(m.declaringClass.modifiers)) return m
            }
            c = c.superclass
        }
        // Nothing public declares it; ask for access to the one we found.
        return try { direct.apply { isAccessible = true } } catch (_: Throwable) { null }
    }

    /** Resolves a class by name, or null when that plugin is not on this server. */
    fun classOrNull(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Throwable) {
        null
    }
}

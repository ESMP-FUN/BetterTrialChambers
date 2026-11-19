# =============================================================================
# BetterTrialChambers ProGuard configuration
# Shrink only. Names stay as they are, so error reports stay readable and no
# mapping file is needed.
#
# MythicTrials, WildSpawners and VaultCrates compile against this jar and call
# into the plugin class, its managers, models and scheduler, not only api/.
# So the whole plugin is kept as written, with its Kotlin metadata.
#
# The Kotlin standard library is kept whole too. Those plugins compile with
# this jar ahead of their own copy of it, so a trimmed one here hides parts of
# theirs (lazy, setOf, ...) and their build fails. Plugins that rely on this
# jar for Kotlin at runtime need all of it as well.
# =============================================================================
-dontobfuscate
# Kotlin copies the body of every inline function (runCatching, lazy, ...) out
# of this jar when those plugins are compiled, and it cannot read a body ProGuard
# has rewritten, nor one without its local variable table. So nothing is optimized.
-dontoptimize
-dontnote **

-keep class com.esmpfun.bettertrialchambers.** { *; }
-keep class kotlin.** { *; }

# Bukkit finds event handlers by reflection.
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Libraries that find their own parts by reflection, ServiceLoader, JNI or field
# updaters. Kept whole so the database, update checks and metrics behave exactly
# as they were written.
-keep class org.sqlite.** { *; }
-keep class com.mysql.** { *; }
-keep class com.esmpfun.btc.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.intellij.lang.annotations.** { *; }
-keep class org.jetbrains.annotations.** { *; }

-keepattributes Exceptions, InnerClasses, Signature, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable, RuntimeVisible*Annotations, MethodParameters, LocalVariableTable, LocalVariableTypeTable

# The server provides these, and the libraries above name optional parts we do not ship.
-dontwarn org.bukkit.**
-dontwarn io.papermc.**
-dontwarn com.destroystokyo.**
-dontwarn net.kyori.**
-dontwarn net.md_5.**
-dontwarn com.sk89q.**
-dontwarn com.fastasyncworldedit.**
-dontwarn me.clip.**
-dontwarn net.milkbowl.**
-dontwarn org.apache.logging.**
-dontwarn org.slf4j.**
-dontwarn com.mysql.**
-dontwarn org.sqlite.**
-dontwarn com.esmpfun.btc.**
-dontwarn com.google.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.jspecify.**
-dontwarn kotlin.**
-dontwarn javax.**
-dontwarn java.**
-dontwarn jdk.**
-dontwarn sun.**
-dontnote

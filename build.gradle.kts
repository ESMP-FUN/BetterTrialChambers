import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    // v1.3.3: enable Maven publication so Jitpack can serve TCP as a
    // compile-time dependency to premium add-on modules and third-party
    // integrations. Premium devs declare:
    //   compileOnly("com.github.darkstarworks:BetterTrialChambers:v1.3.3")
    // and import classes from the com.esmpfun.bettertrialchambers.api.* package.
    `maven-publish`
}

group = "com.esmpfun"
version = "2.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    // Paper API — 26.x track (the `-mc26` build). Carries the same Dialog API as 1.21.7+
    // (used by `/trial setup`); plugin.yml's api-version '26.1' keeps this jar to 26.x servers,
    // while the master build targets 1.21.7 + api-version '1.21'.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // Log4j core (bundled by the server at runtime) — for the console log filter
    // that mutes vanilla trial-spawner spam.
    compileOnly("org.apache.logging.log4j:log4j-core:2.19.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // GUI Framework — TCP uses its own in-house VcGui framework
    // (`gui/framework/`) as of v1.5.0; the prior InventoryFramework
    // dependency (com.github.stefvanschie.inventoryframework:IF) was
    // dropped in the same release.

    // JSON parsing for update checker
    implementation("com.google.code.gson:gson:2.10.1")

    // PluginPulse — multi-source update checking + verified install staging,
    // plus the opt-in hot-reload engine (gated behind update.allow-hot-reload).
    implementation("com.github.darkstarworks.PluginPulse:pluginpulse-core:v0.8.0")
    implementation("com.github.darkstarworks.PluginPulse:pluginpulse-hotreload:v0.8.0")

    // Anonymous usage metrics (relocated below — bStats requires it)
    implementation("org.bstats:bstats-bukkit:3.2.1")

    // Economy (optional)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // Note: FoliaLib can be added later if Folia support is needed
    // For now, using standard Bukkit scheduler

    // Optional integrations
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("me.clip:placeholderapi:2.11.5")
    // Land-claim plugins (Residence / Lands / GriefPrevention) are integrated purely
    // via reflection (see integrations/claims/) — no compile-time dependency or version
    // pin, so TCP binds to whatever version each server actually runs.

    // FastAsyncWorldEdit (FAWE) - preferred over vanilla WorldEdit for performance
    // Using BOM for version management and transitive=false to avoid dependency bloat
    implementation(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit") {
        isTransitive = false
    }

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper API on the test classpath so unit tests can mock Bukkit types directly.
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
    }
}

// Use JDK 25 to compile against the Paper 26.x API, but output Java 21 bytecode so
// the Shadow jar packager (which bundles an older ASM) can process the class files.
// api-version: '26.1' in plugin.yml is what prevents this jar from loading on 1.21.x servers.
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

// Tell Gradle's dependency resolution to accept JVM 25 libraries (Paper 26.x requires it),
// while still outputting JVM 21 bytecode above (so Shadow's bundled ASM can process it).
configurations.matching {
    it.name in setOf("compileClasspath", "runtimeClasspath")
}.configureEach {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("mc26")
        // Do not relocate Kotlin stdlib or kotlinx-coroutines to ensure Bukkit can find them
        // They will be shaded into the jar with their original package names
        // This avoids NoClassDefFoundError for kotlinx.coroutines.Dispatchers during plugin bootstrap
        // Important: Do NOT relocate org.sqlite, or the sqlite-jdbc native bindings (JNI) will fail to load
        relocate("com.zaxxer.hikari", "com.esmpfun.btc.hikari")
        // bStats mandates relocation so multiple plugins can shade different versions
        relocate("org.bstats", "com.esmpfun.btc.bstats")
        // PluginPulse relocation so other plugins can shade different versions
        relocate("io.github.darkstarworks.pluginpulse", "com.esmpfun.btc.pluginpulse")
        // InventoryFramework relocation removed in v1.5.0 — see dependency comment.

        // Exclude unnecessary SQLite native binaries to reduce jar size
        // Keep only Windows (x86/x64), Linux (x86/x64), and Linux-ARM for common MC server platforms
        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Mac/**")

        // Do not exclude HikariCP metrics; needed at runtime to avoid NoClassDefFoundError
        // (HikariConfig references MetricsTrackerFactory via reflection)
    }

    // Disable building the plain/thin jar; only produce the shaded (fat) jar
    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Ensure IDEs and users invoking 'assemble' also get the shaded (fat) jar
tasks {
    assemble {
        dependsOn(shadowJar)
    }
}

// v1.3.3: Maven publication for Jitpack consumption.
// Premium add-on modules and third-party integrations declare TCP as a
// compileOnly dependency and import from the api/* package. The shadow JAR
// is published as the main artifact — at compile time only the api classes
// are referenced; at runtime the consuming server has TCP installed
// separately so the fat JAR's bundled deps are inert.
publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "BetterTrialChambers"
            artifact(tasks.shadowJar.get())
        }
    }
}

// Auto-maintain the CHANGELOG "compare link" footer. At release time this inserts a line
// like `[1.7.2]: https://github.com/ESMP-FUN/BetterTrialChambers/compare/v1.7.1...v1.7.2`
// for the current `version`, placed newest-first above the previous release's link — the
// step that used to be done by hand on every release. Idempotent: a no-op once the current
// version's link is already present. Wired into `build` below so it runs on a version bump.
val changelogRepoUrl = "https://github.com/ESMP-FUN/BetterTrialChambers"
val updateChangelogLink by tasks.registering {
    group = "documentation"
    description = "Ensure CHANGELOG.md has a compare-link footer for the current version."
    doLast {
        val changelog = rootProject.file("CHANGELOG.md")
        if (!changelog.exists()) {
            logger.warn("updateChangelogLink: CHANGELOG.md not found — skipping.")
            return@doLast
        }
        val ver = version.toString()
        val text = changelog.readText()
        if (Regex("(?m)^\\[${Regex.escape(ver)}]:").containsMatchIn(text)) {
            logger.lifecycle("updateChangelogLink: CHANGELOG already links v$ver — nothing to do.")
            return@doLast
        }
        // The first existing footer link is the previous release, e.g. `[1.7.1]: .../compare/v1.7.0...v1.7.1`.
        val prevLink = Regex(
            "(?m)^\\[(\\d+\\.\\d+\\.\\d+)]:\\s*${Regex.escape(changelogRepoUrl)}/compare/v\\S+\\.\\.\\.v\\S+\\s*$"
        ).find(text)
        if (prevLink == null) {
            logger.warn("updateChangelogLink: no existing compare-link footer found — leaving CHANGELOG untouched.")
            return@doLast
        }
        val prevVer = prevLink.groupValues[1]
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        val newLine = "[$ver]: $changelogRepoUrl/compare/v$prevVer...v$ver"
        val at = prevLink.range.first
        changelog.writeText(text.substring(0, at) + newLine + nl + text.substring(at))
        logger.lifecycle("updateChangelogLink: added $newLine")
    }
}

tasks.named("build") {
    dependsOn(updateChangelogLink)
}

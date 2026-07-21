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
version = "2.0.8"

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
    maven("https://repo.faststats.dev/releases") {
        name = "faststatsReleases"
    }
}

dependencies {
    // Paper API — 1.21.7 for the Dialog API (added 1.21.6/.7) used by `/trial setup`.
    // api-version stays '1.21'; the Dialog code is runtime-gated behind a class-presence
    // check so older/non-Paper servers fall back to the clickable-chat tour.
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")

    // Log4j core (bundled by the server at runtime) — for the console log filter
    // that mutes vanilla trial-spawner spam.
    compileOnly("org.apache.logging.log4j:log4j-core:2.19.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // MySQL classic-protocol JDBC driver. config.yml has offered `type: MYSQL`
    // since 1.0 and DatabaseManager asks Hikari for com.mysql.cj.jdbc.Driver, but
    // the driver was never shipped — MySQL only worked because Paper happens to
    // bundle mysql-connector-j in its own libraries folder. That's the server's
    // choice to make, not something to depend on (and it isn't guaranteed across
    // forks or future Paper versions), so we now ship our own. Version matches
    // BetterAncientCities so the two plugins stay in step.
    // protobuf-java is only used by the X DevAPI (jdbc:mysqlx), which we never
    // touch — excluding it drops ~1.7 MB.
    implementation("com.mysql:mysql-connector-j:8.4.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
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

    // Anonymous usage metrics (relocated below). Replaced bStats in v2.0.5.
    // Pulls dev.faststats.metrics:core (+ :config at runtime) transitively.
    //
    // core requests gson 2.14.0, but the FAWE BOM below pins gson to 2.11.0 and wins,
    // so the shaded jar ships 2.11.0. Verified safe: every gson member the FastStats
    // classes reference (JsonObject/JsonArray/JsonPrimitive/JsonParser, including the
    // newer isEmpty() and parseString()) exists in 2.11.0. Re-check this if the SDK is
    // upgraded — a NoSuchMethodError here would surface as silent telemetry failure.
    implementation("dev.faststats.metrics:bukkit:0.28.0")

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
        minecraftVersion("1.21.7")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Do not relocate Kotlin stdlib or kotlinx-coroutines to ensure Bukkit can find them
        // They will be shaded into the jar with their original package names
        // This avoids NoClassDefFoundError for kotlinx.coroutines.Dispatchers during plugin bootstrap
        // Important: Do NOT relocate org.sqlite, or the sqlite-jdbc native bindings (JNI) will fail to load
        relocate("com.zaxxer.hikari", "com.esmpfun.btc.hikari")
        // FastStats recommends relocation so two plugins shading different SDK
        // versions can't collide on the same package (same rationale bStats had).
        relocate("dev.faststats", "com.esmpfun.btc.faststats")
        // PluginPulse relocation so other plugins can shade different versions
        relocate("io.github.darkstarworks.pluginpulse", "com.esmpfun.btc.pluginpulse")
        // InventoryFramework relocation removed in v1.5.0 — see dependency comment.

        // SQLite ships a native binary per OS/architecture and they dominate the jar
        // (~10.6 MB of 12 MB before this trim). Keep only the three a Minecraft server
        // realistically runs on:
        //   Linux/x86_64   - the overwhelming majority of hosts
        //   Linux/aarch64  - ARM hosts (Oracle Cloud free tier, AWS Graviton) and
        //                    64-bit Raspberry Pi OS, which has been the default since 2022
        //   Windows/x86_64 - people hosting from their own machine
        //   Mac/aarch64    - Apple Silicon, for local testing on a dev machine
        // Everything else goes: 32-bit x86/ARM can't address enough RAM for a server
        // worth running, Intel Macs are end-of-life as a dev platform, and
        // ppc64/s390x/riscv aren't Minecraft hosts.
        //
        // NOTE: this is a hard exclusion, not a fallback — sqlite-jdbc has no pure-Java
        // mode, so a host on a dropped platform fails at startup with "No native library
        // found". Add the arch back here if that ever comes up.
        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Mac/x86_64/**")
        exclude("org/sqlite/native/Linux/arm/**")
        exclude("org/sqlite/native/Linux/armv6/**")
        exclude("org/sqlite/native/Linux/armv7/**")
        exclude("org/sqlite/native/Linux/ppc64/**")
        exclude("org/sqlite/native/Linux/x86/**")
        exclude("org/sqlite/native/Windows/aarch64/**")
        exclude("org/sqlite/native/Windows/armv7/**")
        exclude("org/sqlite/native/Windows/x86/**")

        // MySQL X DevAPI (jdbc:mysqlx) — we only ever open classic jdbc:mysql://
        // connections, and these classes are what pull in protobuf. Dropping them
        // keeps the excluded protobuf dependency from being referenced at all.
        exclude("com/mysql/cj/x/**")
        exclude("com/mysql/cj/xdevapi/**")
        // mysql-connector-j is a SIGNED jar; shading it without stripping the
        // signature files throws "Invalid signature file digest" at load time.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        // Do not exclude HikariCP metrics; needed at runtime to avoid NoClassDefFoundError
        // (HikariConfig references MetricsTrackerFactory via reflection)

        // Paper's plugin remapper rejects a jar containing duplicate entries.
        // Concatenate any JDBC ServiceLoader registrations rather than deduping
        // them — a future second driver alongside sqlite-jdbc must not be dropped.
        mergeServiceFiles()
        // The faststats bukkit/config/core modules each ship an identical
        // META-INF/faststats.properties (version=0.28.0); keep the first.
        // Scoped to that path only — a task-wide strategy would override
        // mergeServiceFiles() above and silently drop a driver registration.
        //
        // Without this the remapper fails with
        //   IllegalStateException: Duplicate entries detected: META-INF/faststats.properties
        // and the plugin silently never appears in /plugins. Introduced with the
        // FastStats switch in 2.0.7; caught on Paper 1.21.11 while testing 2.0.8.
        // Same fix as BetterAncientCities 2.1.0 — keep the two in step.
        filesMatching("META-INF/faststats.properties") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
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

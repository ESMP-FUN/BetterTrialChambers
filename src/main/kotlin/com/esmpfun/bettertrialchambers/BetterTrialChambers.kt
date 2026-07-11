package com.esmpfun.bettertrialchambers

import com.esmpfun.bettertrialchambers.commands.TCPCommand
import com.esmpfun.bettertrialchambers.commands.TCPTabCompleter
import com.esmpfun.bettertrialchambers.database.DatabaseManager
import com.esmpfun.bettertrialchambers.gui.MenuService
import com.esmpfun.bettertrialchambers.listeners.*
import com.esmpfun.bettertrialchambers.managers.*
import com.esmpfun.bettertrialchambers.scheduler.SchedulerAdapter
import io.github.darkstarworks.pluginpulse.UpdateMode
import io.github.darkstarworks.pluginpulse.UpdateSubcommand
import io.github.darkstarworks.pluginpulse.Updater
import io.github.darkstarworks.pluginpulse.source.GitHubReleasesSource
import io.github.darkstarworks.pluginpulse.source.ModrinthSource
import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Main plugin class for BetterTrialChambers.
 * Manages Trial Chambers on multiplayer servers with automatic resets,
 * per-player vault loot, custom loot tables, and region protection.
 */
class BetterTrialChambers : JavaPlugin() {

    // Indicates when the plugin finished async initialization and is safe to use
    @Volatile
    var isReady: Boolean = false
        private set

    // Scheduler adapter (Paper/Folia compatible)
    lateinit var scheduler: SchedulerAdapter
        private set

    // Database manager
    lateinit var databaseManager: DatabaseManager
        private set

    // Snapshot manager
    lateinit var snapshotManager: SnapshotManager
        private set

    // Chamber manager
    lateinit var chamberManager: ChamberManager
        private set

    // Procedural dungeon generation
    lateinit var roomTemplateManager: com.esmpfun.bettertrialchambers.dungeon.RoomTemplateManager
        private set
    lateinit var dungeonGenerator: com.esmpfun.bettertrialchambers.dungeon.DungeonGenerator
        private set

    // Vault manager
    lateinit var vaultManager: VaultManager
    lateinit var containerLootManager: com.esmpfun.bettertrialchambers.managers.ContainerLootManager
        private set

    // Loot manager
    lateinit var lootManager: LootManager
        private set

    // Reset manager
    lateinit var resetManager: ResetManager
        private set

    // Statistics manager
    lateinit var statisticsManager: StatisticsManager
        private set

    // Menu (GUI) service
    lateinit var menuService: MenuService
        private set

    // Schematic manager
    lateinit var schematicManager: SchematicManager
        private set

    // Particle visualizer for schematic previews
    lateinit var particleVisualizer: com.esmpfun.bettertrialchambers.utils.ParticleVisualizer
        private set

    // Paste confirmation manager
    lateinit var pasteConfirmationManager: PasteConfirmationManager
        private set

    // Spawner wave manager
    lateinit var spawnerWaveManager: SpawnerWaveManager
        private set

    // Spectator manager
    lateinit var spectatorManager: SpectatorManager
        private set

    // Chamber auto-discovery manager
    lateinit var chamberDiscoveryManager: ChamberDiscoveryManager
        private set

    // v1.5.0: per-world spatial index of known trial-spawner positions.
    // Replaces the O(r³) block scan in SpawnerWaveListener.onPlayerMove
    // with an O(spawners-near-player) chunk-keyed query.
    lateinit var trialSpawnerIndex: com.esmpfun.bettertrialchambers.managers.TrialSpawnerIndex
        private set

    // Custom mob provider registry (v1.3.0) — always contains VanillaMobProvider;
    // additional providers (MythicMobs, ...) are registered after soft-deps are up.
    lateinit var trialMobProviderRegistry: com.esmpfun.bettertrialchambers.providers.TrialMobProviderRegistry
        private set

    // Spawner preset manager (v1.3.1) — backs `/trial give <preset>`.
    lateinit var spawnerPresetManager: SpawnerPresetManager
        private set

    // Module registry (v1.3.3) — lifecycle hub for premium add-on plugins
    // and third-party integrations implementing TCPModule.
    lateinit var moduleRegistry: com.esmpfun.bettertrialchambers.api.TCPModuleRegistry
        private set

    // Vault interaction listener (stored for proper shutdown)
    private lateinit var vaultInteractListener: VaultInteractListener

    /** Stored so the GUI / `/trial container` command can drive container-loot template ops. */
    lateinit var containerLootListener: com.esmpfun.bettertrialchambers.listeners.ContainerLootListener

    /** Land-claim plugin integrations (Residence/Lands/GriefPrevention); drives `/trial claims scan`. */
    lateinit var claimIntegrationManager: com.esmpfun.bettertrialchambers.integrations.claims.ClaimIntegrationManager

    // Listeners with coroutine scopes (stored for proper shutdown)
    private lateinit var playerMovementListener: PlayerMovementListener
    private lateinit var playerDeathListener: PlayerDeathListener
    private lateinit var pasteConfirmListener: PasteConfirmListener
    private lateinit var snapshotReminderService: com.esmpfun.bettertrialchambers.managers.SnapshotReminderService

    /** Engine behind the opt-in `/trial setup` settings tour. Created before the command executor. */
    lateinit var setupController: com.esmpfun.bettertrialchambers.setup.SetupController
        private set

    // Update checker
    lateinit var updater: Updater
        private set
    lateinit var updateSubcommand: UpdateSubcommand
        private set

    // Cached messages configuration (invalidated on reload)
    @Volatile
    private var cachedMessages: org.bukkit.configuration.file.YamlConfiguration? = null

    // Coroutine scope for async operations
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Launch an asynchronous task tied to the plugin lifecycle (cancelled on disable).
     */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit) = pluginScope.launch(Dispatchers.IO) { block() }

    // Snapshots directory
    val snapshotsDir: File by lazy {
        File(dataFolder, "snapshots").apply { mkdirs() }
    }

    override fun onEnable() {
        migrateLegacyDataFolder()

        // ASCII art banner
        logger.info("╔════════════════════════════════════╗")
        logger.info("║   BetterTrialChambers v${pluginMeta.version}           ║")
        logger.info("║   Advanced Trial Chamber Manager   ║")
        logger.info("╚════════════════════════════════════╝")

        // Initialize scheduler adapter (Paper/Folia compatible) - must be first!
        scheduler = SchedulerAdapter.create(this)
        if (scheduler.isFolia) {
            logger.info("Folia detected - using regionized scheduling")
        } else {
            logger.info("Paper/Spigot detected - using standard scheduling")
        }

        // v1.3.3: Module registry instantiated synchronously so that external
        // plugins can call `plugin.moduleRegistry.register(myModule)` from
        // their own onEnable, even while TCP's async startup is still in
        // flight. Modules registered early are queued and loaded by
        // `loadAllPending()` once TCP reaches isReady = true.
        moduleRegistry = com.esmpfun.bettertrialchambers.api.TCPModuleRegistry(this)
        server.pluginManager.registerEvents(moduleRegistry, this)

        // Save default config files (only written when absent)
        saveDefaultConfig()
        saveResource("messages.yml", false)
        saveResource("loot.yml", false)

        // Merge keys added in newer versions into the user's existing settings/text files.
        // saveResource/saveDefaultConfig only write when the file is ABSENT, so upgraders would
        // otherwise never get new config options or message keys (the latter showing as
        // "<missing: …>" in-game). Additive + comment-preserving + .bak backup. User-content
        // files (loot.yml, spawner_presets.yml, dungeon.yml) are deliberately NOT merged —
        // they're authored by the server owner and merging would fight their edits.
        mergeYamlDefaults("config.yml")
        reloadConfig() // pull the merged keys into getConfig()
        mergeYamlDefaults("messages.yml")

        // v1.3.0: sanity-check numeric config values; clamp with warnings rather than hard-fail
        com.esmpfun.bettertrialchambers.config.ConfigValidator.validate(this)

        // v1.8.0: PluginPulse updater (replaces the old UpdateChecker) — Modrinth
        // primary, GitHub Releases fallback, clickable admin notices, and (when
        // enabled in config) checksum-verified downloads staged into the server's
        // update folder for install on the next restart. Runs after config load
        // because update.* keys drive its mode. `-mc26` releases are matched
        // automatically when the server itself runs MC 26+.
        val updateMode = when (config.getString("update.mode", "notify")!!.lowercase()) {
            "check-only", "check", "silent" -> UpdateMode.CHECK_ONLY
            "download" -> UpdateMode.DOWNLOAD
            "auto-stage", "auto" -> UpdateMode.AUTO_STAGE
            else -> UpdateMode.NOTIFY
        }
        val onMc26 = runCatching {
            server.minecraftVersion.substringBefore('.').toInt() >= 26
        }.getOrDefault(false)
        updater = Updater.builder(this)
            .source(ModrinthSource("trialchamberpro"))
            .fallbackSource(GitHubReleasesSource("ESMP-FUN/BetterTrialChambers"))
            .mode(updateMode)
            .checkInterval(java.time.Duration.ofHours(
                config.getLong("update.check-interval-hours", 6L).coerceAtLeast(1)))
            .permission("btc.admin")
            .commandRoot("/trial")
            .prefix("<gold>[BTC]</gold>")
            .changelogUrl("https://raw.githubusercontent.com/ESMP-FUN/BetterTrialChambers/master/src/main/resources/update.txt")
            .userAgentContact("https://github.com/ESMP-FUN/BetterTrialChambers")
            .requireHash(config.getBoolean("update.require-hash", true))
            .apply { if (onMc26) track("mc26") }
            .apply {
                // Opt-in no-restart updates (/trial update apply). The engine
                // refuses on Folia and when other plugins depend on TCP —
                // restart-install remains the default and recommended path.
                if (config.getBoolean("update.allow-hot-reload", false)) {
                    reloadEngine(io.github.darkstarworks.pluginpulse.hotreload.HotReloadEngine.create())
                }
            }
            .build()
        updater.start()
        updateSubcommand = UpdateSubcommand(updater)

        // v1.4.1: warn when the user's messages.yml is missing keys this version expects.
        // Pure log output — never modifies the file, never blocks startup. See the user
        // bug report where `<missing: gui.loot-table-list.table-name-normal>` reached the
        // GUI because the deployed messages.yml was an older copy.
        com.esmpfun.bettertrialchambers.config.MessagesSchemaValidator.validate(this)

        // Setup tour: create the controller before the command executor (SetupCommand needs it),
        // and register the gentle op-join reminder. Both are config-driven and side-effect-free
        // at startup.
        setupController = com.esmpfun.bettertrialchambers.setup.SetupController(
            this, com.esmpfun.bettertrialchambers.setup.SetupState(dataFolder)
        )
        server.pluginManager.registerEvents(
            com.esmpfun.bettertrialchambers.setup.SetupReminderService(this, setupController.state), this
        )

        // Register command executor and tab completer immediately to avoid early "/trial [args]" usage messages
        val tcpCommand = TCPCommand(this)
        val tabCompleter = TCPTabCompleter(this)
        getCommand("trial")?.setExecutor(tcpCommand)
        getCommand("trial")?.tabCompleter = tabCompleter

        // Initialize database asynchronously
        pluginScope.launch {
            try {
                databaseManager = DatabaseManager(this@BetterTrialChambers)
                databaseManager.initialize()

                // v1.4.0: Register with Bukkit's ServicesManager so external
                // plugins (notably the planned premium "Network Sync" module)
                // can resolve and/or replace the database backend.
                server.servicesManager.register(
                    DatabaseManager::class.java,
                    databaseManager,
                    this@BetterTrialChambers,
                    org.bukkit.plugin.ServicePriority.Normal
                )

                // Test connection
                if (databaseManager.testConnection()) {
                    logger.info("Database connection test successful")
                } else {
                    logger.severe("Database connection test failed")
                }

                // Initialize managers
                snapshotManager = SnapshotManager(this@BetterTrialChambers)
                chamberManager = ChamberManager(this@BetterTrialChambers)
                roomTemplateManager = com.esmpfun.bettertrialchambers.dungeon.RoomTemplateManager(this@BetterTrialChambers)
                dungeonGenerator = com.esmpfun.bettertrialchambers.dungeon.DungeonGenerator(this@BetterTrialChambers, roomTemplateManager)
                vaultManager = VaultManager(this@BetterTrialChambers)
                containerLootManager = com.esmpfun.bettertrialchambers.managers.ContainerLootManager(this@BetterTrialChambers)
                lootManager = LootManager(this@BetterTrialChambers)
                resetManager = ResetManager(this@BetterTrialChambers)
                statisticsManager = StatisticsManager(this@BetterTrialChambers)
                menuService = MenuService(this@BetterTrialChambers)
                schematicManager = SchematicManager(this@BetterTrialChambers)
                schematicManager.initialize()
                particleVisualizer = com.esmpfun.bettertrialchambers.utils.ParticleVisualizer(this@BetterTrialChambers)
                pasteConfirmationManager = PasteConfirmationManager(this@BetterTrialChambers)
                spawnerWaveManager = SpawnerWaveManager(this@BetterTrialChambers)
                spectatorManager = SpectatorManager(this@BetterTrialChambers)
                chamberDiscoveryManager = ChamberDiscoveryManager(this@BetterTrialChambers)
                trialSpawnerIndex = com.esmpfun.bettertrialchambers.managers.TrialSpawnerIndex()

                // Expose the managers a network/extension module needs to resolve
                // (alongside DatabaseManager registered above). The planned premium
                // "Network Sync" module loads these via ServicesManager to read
                // leaderboards and drive cross-server cache invalidation
                // (StatisticsManager.invalidatePlayer / ChamberManager.reloadFromStore).
                server.servicesManager.register(
                    ChamberManager::class.java, chamberManager,
                    this@BetterTrialChambers, org.bukkit.plugin.ServicePriority.Normal
                )
                server.servicesManager.register(
                    StatisticsManager::class.java, statisticsManager,
                    this@BetterTrialChambers, org.bukkit.plugin.ServicePriority.Normal
                )
                server.servicesManager.register(
                    VaultManager::class.java, vaultManager,
                    this@BetterTrialChambers, org.bukkit.plugin.ServicePriority.Normal
                )

                // v1.3.0: Trial mob provider registry. Vanilla is registered by the registry's
                // init block; soft-depended providers are registered below once we know their
                // backing plugins are enabled (which is guaranteed by the time this runs because
                // plugin.yml `softdepend` controls load order).
                trialMobProviderRegistry = com.esmpfun.bettertrialchambers.providers.TrialMobProviderRegistry()
                if (server.pluginManager.getPlugin("MythicMobs") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.MythicMobsProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: MythicMobs")
                }
                if (server.pluginManager.getPlugin("EliteMobs") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.EliteMobsProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: EliteMobs")
                }
                if (server.pluginManager.getPlugin("EcoMobs") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.EcoMobsProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: EcoMobs")
                }
                if (server.pluginManager.getPlugin("LevelledMobs") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.LevelledMobsProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: LevelledMobs")
                }
                if (server.pluginManager.getPlugin("InfernalMobs") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.InfernalMobsProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: InfernalMobs")
                }
                if (server.pluginManager.getPlugin("Citizens") != null) {
                    trialMobProviderRegistry.register(
                        com.esmpfun.bettertrialchambers.providers.CitizensProvider(this@BetterTrialChambers)
                    )
                    logger.info("Registered TrialMobProvider: Citizens")
                }

                // Load loot tables
                lootManager.loadLootTables()

                // v1.3.1: Spawner presets (backs `/trial give <preset>`)
                spawnerPresetManager = SpawnerPresetManager(this@BetterTrialChambers)
                spawnerPresetManager.load()

                // Preload chambers cache for fast, thread-safe lookups in listeners
                chamberManager.preloadCache()

                // Start reset scheduler
                resetManager.startResetScheduler()

                // Register listeners and log readiness on main thread
                scheduler.runTask(Runnable {
                    // Register listeners
                    vaultInteractListener = VaultInteractListener(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        vaultInteractListener,
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        ProtectionListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    containerLootListener = com.esmpfun.bettertrialchambers.listeners.ContainerLootListener(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        containerLootListener,
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.ChamberCompletionListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    playerMovementListener = PlayerMovementListener(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        playerMovementListener,
                        this@BetterTrialChambers
                    )
                    playerDeathListener = PlayerDeathListener(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        playerDeathListener,
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        PostUndoHintListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    pasteConfirmListener = PasteConfirmListener(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        pasteConfirmListener,
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        SpawnerWaveListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.MobInfightingListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        SpectatorListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    server.pluginManager.registerEvents(
                        ChamberDiscoveryListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.5.0: maintain TrialSpawnerIndex in lock-step with the world
                    // (chunk loads scan tile entities; block break/place keep the
                    // index live; world unload drops cached entries).
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.TrialSpawnerIndexListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    com.esmpfun.bettertrialchambers.listeners.VaultDropOwnerListener.init(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.VaultDropOwnerListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.3.0: spawner-key drop owner lock (sibling of vault drop listener)
                    com.esmpfun.bettertrialchambers.listeners.SpawnerKeyDropOwnerListener.init(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.SpawnerKeyDropOwnerListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.3.0: one-shot chat input collector for CustomMobProviderView
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.MobIdInputListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.6.1: one-shot chat input collector for the chamber-detail rename button
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.ChamberRenameInputListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.3.0: drop GUI session cache entries on player quit
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.MenuSessionCleanupListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.5.1: snapshot reminder for auto-discovered chambers without a snapshot
                    snapshotReminderService = com.esmpfun.bettertrialchambers.managers.SnapshotReminderService(this@BetterTrialChambers)
                    server.pluginManager.registerEvents(snapshotReminderService, this@BetterTrialChambers)
                    snapshotReminderService.startScheduler()

                    // v1.5.0 GUI migration: central VcGui click/drag/close dispatcher.
                    // Replaces the standalone LootDepositListener — bulk-deposit
                    // close handling now lives in LootDepositView.handleClose,
                    // routed through this listener like every other VcGui view.
                    // Coexists with InventoryFramework's listener during the transition —
                    // VcGuiListener only routes events whose inventory holder is a BaseHolder,
                    // so IF-backed views are unaffected.
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.gui.framework.VcGuiListener(),
                        this@BetterTrialChambers
                    )
                    // v1.4.0: copy `tcp:preset_id` PDC tag from preset items
                    // onto placed TrialSpawner TileStates so the wild-spawner
                    // resolver seam can identify the source preset.
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.SpawnerPresetPlaceListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )
                    // v1.4.5: allow Silk-Touch recovery of TCP-preset spawners
                    // placed outside any registered chamber.
                    server.pluginManager.registerEvents(
                        com.esmpfun.bettertrialchambers.listeners.OrphanSpawnerMineListener(this@BetterTrialChambers),
                        this@BetterTrialChambers
                    )

                    // v1.5.15: shield registered chambers from land-claim plugins
                    // (Residence / Lands / GriefPrevention) via reflection — no compile-time
                    // dependency. Each integration is gated by its own config toggle + plugin
                    // presence. Also scans for pre-existing claim conflicts and logs them.
                    claimIntegrationManager =
                        com.esmpfun.bettertrialchambers.integrations.claims.ClaimIntegrationManager(this@BetterTrialChambers)
                    claimIntegrationManager.registerGuards()
                    if (config.getBoolean("protection.claim-conflict-scan-on-startup", true) &&
                        claimIntegrationManager.hasActiveProvider()
                    ) {
                        scheduler.runTask(Runnable {
                            val conflicts = claimIntegrationManager.scanAndLog()
                            if (conflicts > 0) {
                                logger.warning(
                                    "Claim conflict scan: $conflicts chamber(s) overlap existing claims " +
                                        "(see warnings above). Re-check anytime with /trial claims scan."
                                )
                            }
                        })
                    }

                    // v1.5.18: stop AdvancedEnchantments custom enchants (Blast Mining, etc.)
                    // from breaking blocks inside chambers — their effect path ignores the
                    // BlockBreakEvent cancel. Registered only when AE is installed; gated live
                    // by protection.block-advanced-enchantments.
                    if (com.esmpfun.bettertrialchambers.integrations.AdvancedEnchantmentsHook.isAvailable(this@BetterTrialChambers)) {
                        com.esmpfun.bettertrialchambers.integrations.AdvancedEnchantmentsHook.register(this@BetterTrialChambers)
                    }

                    // Mute the vanilla "Trial Spawner ... has no detected players"
                    // console spam from any chambers still broken from before the
                    // reset fixes (they clear on their next reset).
                    com.esmpfun.bettertrialchambers.utils.TrialSpawnerLogFilter.install(this@BetterTrialChambers)

                    logger.info("✓ Phase 1 Foundation: Initialized successfully")
                    logger.info("  - Database: Connected")
                    logger.info("  - Configuration: Loaded")
                    logger.info("  - Data Models: Ready")
                    logger.info("✓ Phase 2 Snapshot System: Ready")
                    logger.info("  - Snapshot Manager: Initialized")
                    logger.info("  - Block Restorer: Available")
                    logger.info("  - Compression: Enabled (Gzip)")
                    logger.info("✓ Phase 3 Chamber Registration: Ready")
                    logger.info("  - Chamber Manager: Initialized")
                    logger.info("  - Commands: Registered (/trial)")
                    logger.info("  - WorldEdit: ${if (server.pluginManager.getPlugin("WorldEdit") != null) "Available" else "Not found"}")
                    logger.info("✓ Phase 4 Per-Player Vault System: Ready")
                    logger.info("  - Vault Manager: Initialized")
                    logger.info("  - Vault Listener: Registered")
                    logger.info("  - Key Validation: ${if (config.getBoolean("trial-keys.validate-key-type")) "Enabled" else "Disabled"}")
                    logger.info("  - Cooldowns: Normal=${config.getLong("vaults.normal-cooldown-hours")}h, Ominous=${config.getLong("vaults.ominous-cooldown-hours")}h")
                    logger.info("✓ Phase 5 Loot Generation System: Ready")
                    logger.info("  - Loot Manager: Initialized")
                    logger.info("  - Loot Tables: ${lootManager.getLootTableNames().size} loaded")
                    logger.info("  - Weighted Selection: Enabled")
                    logger.info("  - Custom Items: Supported")
                    logger.info("✓ Phase 6 Automatic Reset System: Ready")
                    logger.info("  - Reset Manager: Initialized")
                    logger.info("  - Reset Scheduler: Running")
                    logger.info("  - Warnings: ${config.getIntegerList("global.reset-warning-times").size} configured")
                    logger.info("  - Player Teleport: ${if (config.getBoolean("global.teleport-players-on-reset")) "Enabled" else "Disabled"}")
                    logger.info("✓ Phase 7 Protection System: Ready")
                    logger.info("  - Protection Listener: Registered")
                    logger.info("  - Block Protection: ${if (config.getBoolean("protection.prevent-block-break")) "Enabled" else "Disabled"}")
                    logger.info("  - Container Protection: ${if (config.getBoolean("protection.prevent-container-access")) "Enabled" else "Disabled"}")
                    logger.info("  - Mob Griefing: ${if (config.getBoolean("protection.prevent-mob-griefing")) "Prevented" else "Allowed"}")
                    logger.info("  - WorldGuard: ${if (config.getBoolean("protection.worldguard-integration") && server.pluginManager.getPlugin("WorldGuard") != null) "Integrated" else "Disabled"}")
                    logger.info("✓ Phase 8 Statistics & Leaderboards: Ready")
                    logger.info("  - Statistics Manager: Initialized")
                    logger.info("  - Movement Listener: Registered")
                    logger.info("  - Death Listener: Registered")
                    logger.info("  - Stats Tracking: ${if (config.getBoolean("statistics.enabled")) "Enabled" else "Disabled"}")
                    logger.info("  - Time Tracking: ${if (config.getBoolean("statistics.track-time-spent")) "Enabled" else "Disabled"}")
                    logger.info("  - Leaderboards: Top ${config.getInt("statistics.top-players-count", 10)} players")
                    logger.info("✓ Phase 9 Schematic System: Ready")
                    logger.info("  - Schematic Manager: Initialized")
                    logger.info("  - Available Schematics: ${schematicManager.listSchematics().size}")
                    logger.info("  - WorldEdit/FAWE: ${if (schematicManager.isAvailable()) "Available" else "Not Found"}")

                    // Register PlaceholderAPI expansion if available
                    val placeholderAPIStatus = if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                        try {
                            com.esmpfun.bettertrialchambers.integrations.PlaceholderAPIExpansion(this@BetterTrialChambers).register()
                            // legacy pre-2.0 identifier so existing %tcp_*% placeholders keep resolving
                            com.esmpfun.bettertrialchambers.integrations.PlaceholderAPIExpansion(this@BetterTrialChambers, "tcp").register()
                            "Registered"
                        } catch (e: Exception) {
                            logger.warning("Failed to register PlaceholderAPI expansion: ${e.message}")
                            "Failed"
                        }
                    } else {
                        "Not Found"
                    }
                    val metricsStatus =
                        com.esmpfun.bettertrialchambers.integrations.MetricsService.init(this@BetterTrialChambers)
                    logger.info("✓ Phase 10 Integrations: Ready")
                    logger.info("  - PlaceholderAPI: $placeholderAPIStatus")
                    logger.info("  - bStats Metrics: $metricsStatus")
                    logger.info("✓ Phase 11 Spawner Wave System: Ready")
                    logger.info("  - Wave Manager: Initialized")
                    logger.info("  - Wave Listener: Registered")
                    logger.info("  - Boss Bar: ${if (config.getBoolean("spawner-waves.show-boss-bar", true)) "Enabled" else "Disabled"}")
                    logger.info("✓ Phase 12 Spectator Mode: Ready")
                    logger.info("  - Spectator Manager: Initialized")
                    logger.info("  - Spectator Listener: Registered")
                    logger.info("  - Death Spectate: ${if (config.getBoolean("spectator-mode.enabled", true)) "Enabled" else "Disabled"}")

                    // Mark plugin as fully ready after all sync registrations are done
                    this@BetterTrialChambers.isReady = true
                    logger.info("✓ BetterTrialChambers is fully initialized and ready!")

                    // v1.3.3: load any premium / third-party modules whose
                    // backing plugins registered with the module registry
                    // before TCP became ready. New registrations after this
                    // point load immediately.
                    moduleRegistry.loadAllPending()

                    // Sweep already-loaded chunks for chambers that existed before the
                    // ChunkLoadEvent listener was registered (spawn regions, pre-loaded worlds).
                    chamberDiscoveryManager.runStartupSweep()

                    // v1.5.0: seed the trial-spawner spatial index from chunks that
                    // were already resident before TrialSpawnerIndexListener registered.
                    // Symmetric to the discovery startup sweep above. Without this, the
                    // spawner-wave proximity query returns empty for spawn-area chambers
                    // until those chunks happen to fire ChunkLoadEvent again (which they
                    // won't, if they're permanently spawn-chunked).
                    var seeded = 0
                    for (world in server.worlds) {
                        seeded += trialSpawnerIndex.seedFromLoadedChunks(world)
                    }
                    logger.info("[Spawner Index] Seeded $seeded trial spawner(s) from already-loaded chunks")
                })
            } catch (e: Exception) {
                logger.severe("Failed to initialize plugin: ${e.message}")
                e.printStackTrace()
                scheduler.runTask(Runnable {
                    server.pluginManager.disablePlugin(this@BetterTrialChambers)
                })
            }
        }

        // Log debug mode status
        val debugEnabled = config.getBoolean("debug.verbose-logging", false)
        if (debugEnabled) {
            logger.warning("═══════════════════════════════════════")
            logger.warning("   DEBUG MODE ENABLED")
            logger.warning("   Verbose logging is active")
            logger.warning("   Expect detailed console output")
            logger.warning("═══════════════════════════════════════")
        }

        logger.info("BetterTrialChambers has been enabled!")
    }

    override fun onDisable() {
        logger.info("Shutting down BetterTrialChambers...")

        // Remove our console log filter so a reload doesn't stack duplicates.
        com.esmpfun.bettertrialchambers.utils.TrialSpawnerLogFilter.uninstall()

        // Stop update checks and unregister the join-notify listener.
        if (::updater.isInitialized) {
            updater.shutdown()
        }

        // v1.3.3: unload modules FIRST so they can still touch TCP
        // managers / database during their onUnload before everything
        // tears down. Reverse-registration-order is handled inside.
        if (::moduleRegistry.isInitialized) {
            moduleRegistry.shutdownAll()
        }

        // Cancel all coroutines
        pluginScope.cancel()

        // Cancel all scheduled tasks
        if (::scheduler.isInitialized) {
            scheduler.cancelAllTasks()
        }

        // Stop reset scheduler
        if (::resetManager.isInitialized) {
            resetManager.shutdown()
        }

        // Clean up pending pastes and visualizations
        if (::pasteConfirmationManager.isInitialized) {
            pasteConfirmationManager.clearAll()
        }
        if (::particleVisualizer.isInitialized) {
            particleVisualizer.stopAll()
        }
        if (::spawnerWaveManager.isInitialized) {
            spawnerWaveManager.shutdown()
        }
        if (::spectatorManager.isInitialized) {
            spectatorManager.shutdown()
        }
        if (::vaultInteractListener.isInitialized) {
            vaultInteractListener.shutdown()
        }
        if (::playerMovementListener.isInitialized) {
            playerMovementListener.shutdown()
        }
        if (::containerLootListener.isInitialized) {
            // v1.7.2: flush private container copies still open (see ContainerLootListener.shutdown)
            containerLootListener.shutdown()
        }
        if (::playerDeathListener.isInitialized) {
            playerDeathListener.shutdown()
        }
        if (::pasteConfirmListener.isInitialized) {
            pasteConfirmListener.shutdown()
        }
        if (::snapshotReminderService.isInitialized) {
            snapshotReminderService.shutdown()
        }

        // Close database connections
        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }

        logger.info("BetterTrialChambers has been disabled!")
    }

    /**
     * Reloads the plugin configuration.
     */
    fun reloadPluginConfig() {
        reloadConfig()
        com.esmpfun.bettertrialchambers.config.ConfigValidator.validate(this)
        cachedMessages = null // Force reload on next getMessage() call
        if (::lootManager.isInitialized) {
            lootManager.loadLootTables()
        }
        if (::spawnerPresetManager.isInitialized) {
            spawnerPresetManager.load()
        }
        logger.info("Configuration reloaded")
    }

    /**
     * Adds keys introduced in newer versions to an existing bundled YAML file ([resourceName],
     * e.g. `config.yml` / `messages.yml`). `saveDefaultConfig()` / `saveResource(..., false)`
     * only write a file when it's ABSENT, so a server that installed an earlier build never gets
     * new options or message keys — features run on code defaults that can't be configured, and
     * new messages render as `<missing: …>` in-game.
     *
     * Every key present in the jar's default but missing from the user's file is added, carrying
     * its comment across; existing values, comments, and order are left untouched. A `<file>.bak`
     * is written first as a safety net. No-op when nothing is missing or the file doesn't exist
     * yet (a fresh install already has the complete default).
     *
     * Intended for settings/text files with canonical defaults — NOT for user-content files like
     * `loot.yml` / `spawner_presets.yml` / `dungeon.yml`, where re-adding defaults would clobber
     * the owner's edits.
     */
    private fun mergeYamlDefaults(resourceName: String) {
        val resource = getResource(resourceName) ?: return
        val file = java.io.File(dataFolder, resourceName)
        if (!file.exists()) return // fresh install: saveResource already wrote the full default

        val defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            java.io.InputStreamReader(resource, Charsets.UTF_8)
        )
        val current = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        val missing = defaults.getKeys(true).filter { key ->
            !current.isSet(key) && !defaults.isConfigurationSection(key)
        }
        if (missing.isEmpty()) return

        runCatching { file.copyTo(java.io.File(dataFolder, "$resourceName.bak"), overwrite = true) }
            .onFailure { logger.warning("Could not back up $resourceName before merging new keys: ${it.message}") }

        for (key in missing) {
            current.set(key, defaults.get(key))
            // setComments is Paper 1.18+ — keep each key's doc comment in the file.
            runCatching {
                current.setComments(key, defaults.getComments(key))
                current.setInlineComments(key, defaults.getInlineComments(key))
            }
        }
        try {
            current.save(file)
        } catch (e: Exception) {
            logger.warning("Could not save merged $resourceName: ${e.message}")
            return
        }
        logger.info(
            "$resourceName: added ${missing.size} new key(s) introduced in this version " +
                "(your existing entries are kept; previous file saved as $resourceName.bak)."
        )
    }

    /**
     * Gets a message from messages.yml with optional placeholders.
     */
    private fun loadedMessages(): org.bukkit.configuration.file.YamlConfiguration {
        return cachedMessages ?: run {
            val loaded = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File(dataFolder, "messages.yml"))
            cachedMessages = loaded
            loaded
        }
    }

    /**
     * Gets a list-valued message from messages.yml (used for GUI lore and
     * multi-line help entries). Added in v1.3.0.
     *
     * Accepts either a YAML list (preferred) or a single string; returns empty
     * list if the key is absent. Runs `{placeholder}` substitution on every
     * line but does NOT add the chat prefix or convert color codes — callers
     * decide what to do with the raw strings.
     */
    fun getMessageList(key: String, vararg replacements: Pair<String, Any?>): List<String> {
        val messages = loadedMessages()
        val list = messages.getStringList(key)
        val source: List<String> = if (list.isEmpty()) {
            messages.getString(key)?.let { listOf(it) } ?: return emptyList()
        } else list

        return source.map { line ->
            var out = line
            replacements.forEach { (p, v) -> out = out.replace("{$p}", v?.toString() ?: "null") }
            out
        }
    }

    /**
     * Gets a GUI item name (Component) from messages.yml. Unlike [getMessage]
     * this never prepends the chat prefix and disables Minecraft's default
     * italic styling on item names.
     *
     * v1.4.0: parsed via [com.esmpfun.bettertrialchambers.utils.MessageParser],
     * so messages.yml entries can use full MiniMessage syntax (`<gradient>`,
     * `<hover>`, `<click>`, `<#hex>`) alongside legacy `&` codes. Existing
     * entries with only legacy codes continue to render unchanged.
     *
     * Added in v1.3.0.
     */
    fun getGuiText(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): net.kyori.adventure.text.Component {
        val messages = loadedMessages()
        var raw = messages.getString(key, "<missing: $key>")!!
        replacements.forEach { (p, v) -> raw = raw.replace("{$p}", v?.toString() ?: "null") }
        return com.esmpfun.bettertrialchambers.utils.MessageParser.parse(raw)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
    }

    /**
     * Gets a GUI item lore (list of Components) from messages.yml. The key
     * must point at a YAML list; each line is independently parsed and
     * has italics disabled. Empty lines render as [net.kyori.adventure.text.Component.empty].
     *
     * v1.4.0: full MiniMessage support per line via
     * [com.esmpfun.bettertrialchambers.utils.MessageParser].
     *
     * Added in v1.3.0.
     */
    fun getGuiLore(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): List<net.kyori.adventure.text.Component> {
        return getMessageList(key, *replacements).map { line ->
            if (line.isBlank()) net.kyori.adventure.text.Component.empty()
            else com.esmpfun.bettertrialchambers.utils.MessageParser.parse(line)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        }
    }

    /**
     * Whether TCP should stay inactive in this world (`global.excluded-worlds`
     * in config.yml). Gates auto-discovery, chamber creation, and wild-spawner
     * wave tracking; case-insensitive on world name.
     */
    fun isWorldExcluded(world: org.bukkit.World): Boolean {
        return config.getStringList("global.excluded-worlds")
            .any { it.equals(world.name, ignoreCase = true) }
    }

    /**
     * Gets a message from messages.yml as a legacy section-coded String.
     *
     * v1.4.0: messages are now parsed via
     * [com.esmpfun.bettertrialchambers.utils.MessageParser], so
     * MiniMessage syntax (`<gradient>`, `<#hex>`, `<click>`, `<hover>`)
     * works in messages.yml alongside legacy `&` codes. The result is then
     * serialized back to legacy section codes for callers that still use
     * `String`-based APIs (`Player.sendMessage(String)`, etc.). MiniMessage
     * features that have no legacy equivalent (gradients, click, hover,
     * fonts) **degrade gracefully** to plain text or a single representative
     * colour.
     *
     * For full MiniMessage fidelity (gradients, clickable text, hover
     * tooltips), use [getMessageComponent] instead — modern Bukkit/Paper
     * APIs all accept `Component`.
     */
    fun getMessage(key: String, vararg replacements: Pair<String, Any?>): String {
        return com.esmpfun.bettertrialchambers.utils.MessageParser
            .parseToLegacy(rawMessageWithPrefix(key, *replacements))
    }

    /**
     * Gets a message from messages.yml as a fully-styled Adventure
     * [net.kyori.adventure.text.Component]. Full MiniMessage fidelity is
     * preserved end-to-end — gradients, click events, hover events, and
     * custom fonts all render correctly when the result is delivered via
     * `Player.sendMessage(Component)` or other Component-accepting APIs.
     *
     * Identical placeholder substitution and chat-prefix logic as
     * [getMessage]. Added in v1.4.0.
     */
    fun getMessageComponent(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): net.kyori.adventure.text.Component {
        return com.esmpfun.bettertrialchambers.utils.MessageParser
            .parse(rawMessageWithPrefix(key, *replacements))
    }

    /**
     * Raw messages.yml string for [key] (no prefix, no parsing), or [default] if absent. Use for
     * values that get substituted into another message's `{placeholder}` rather than sent directly.
     */
    fun getRawMessage(key: String, default: String): String =
        loadedMessages().getString(key, default) ?: default

    /**
     * Raw messages.yml string with `{placeholder}` substitution applied, but **WITHOUT the chat
     * prefix and WITHOUT parsing** to a Component or legacy section codes.
     *
     * This is the correct tool for a value that is itself substituted into **another** message's
     * `{placeholder}` (e.g. the "exit" value injected into `info-exit: "Exit: {exit}"`, or a
     * toggle's label injected into `toggle-name-enabled`). The OUTER [getMessage] /
     * [getMessageComponent] / [getGuiText] then parses the combined string exactly once.
     *
     * **Do NOT use [getMessage] / [getMessageComponent] for nested sub-values** — those add the
     * chat prefix and pre-render to legacy `§` section codes, which the outer parse (MiniMessage +
     * `&` only) cannot re-read. The result is visible raw codes and an embedded "[TCP]" prefix in
     * the middle of the line. This was a recurring bug class; [rawMessage] exists to kill it.
     */
    fun rawMessage(key: String, vararg replacements: Pair<String, Any?>): String {
        var message = loadedMessages().getString(key, "<missing: $key>") ?: "<missing: $key>"
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value?.toString() ?: "null")
        }
        return message
    }

    /**
     * Translatable "Normal" / "Ominous" label for the `{type}` placeholder — from the
     * `vault-type-normal` / `vault-type-ominous` message keys (English fallback). Used for vault
     * and trial-key types so the word can be localized everywhere it's shown.
     */
    fun normalOminousLabel(ominous: Boolean): String =
        getRawMessage(if (ominous) "vault-type-ominous" else "vault-type-normal", if (ominous) "Ominous" else "Normal")

    /** Translatable display name for a vault type (see [normalOminousLabel]). */
    fun vaultTypeDisplay(type: com.esmpfun.bettertrialchambers.models.VaultType): String =
        normalOminousLabel(type == com.esmpfun.bettertrialchambers.models.VaultType.OMINOUS)

    /**
     * Internal helper: looks up the raw message string, performs
     * `{placeholder}` substitution, and prepends the chat prefix when
     * appropriate. The result is still a raw MM-or-legacy string — the
     * caller decides whether to render to Component or legacy section.
     */
    private fun rawMessageWithPrefix(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): String {
        val messages = loadedMessages()
        val prefix = messages.getString("prefix", "&8[&6TCP&8]&r ")
        var message = messages.getString(key, "&cMessage not found: $key")!!

        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value?.toString() ?: "null")
        }

        // Skip prefix for list items, headers, help entries, boss bars,
        // and any GUI key (`gui.*`) — same rules as before v1.4.0.
        val shouldAddPrefix = !key.contains("list-item") &&
                !key.contains("header") &&
                !key.contains("help-") &&
                !key.contains("boss-bar") &&
                !key.startsWith("gui.")

        return if (shouldAddPrefix) "$prefix$message" else message
    }

    /**
     * One-time migration from the pre-2.0 plugin name: if this plugin's data
     * folder does not exist yet but plugins/TrialChamberPro/ does, copy its
     * contents (config, database, snapshots, dungeon templates) so existing
     * servers upgrade in place. The old folder is left untouched as a backup.
     */
    private fun migrateLegacyDataFolder() {
        try {
            if (dataFolder.exists()) return
            val legacy = java.io.File(dataFolder.parentFile, "TrialChamberPro")
            if (!legacy.isDirectory) return
            logger.info("Migrating data from plugins/TrialChamberPro/ to plugins/${dataFolder.name}/ ...")
            legacy.walkTopDown().forEach { src ->
                val dest = java.io.File(dataFolder, src.relativeTo(legacy).path)
                if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = false)
            }
            logger.info("Migration complete. The old plugins/TrialChamberPro/ folder was kept as a backup and can be deleted once everything works.")
        } catch (e: Exception) {
            logger.severe("Legacy data-folder migration failed: ${e.message} — fix or migrate manually, then restart.")
        }
    }
}

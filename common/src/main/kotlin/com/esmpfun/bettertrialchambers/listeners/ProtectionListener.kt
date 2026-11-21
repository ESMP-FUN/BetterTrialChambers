package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.models.ProtectionSetting
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.entity.Projectile
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.block.SpongeAbsorbEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Handles protection for Trial Chambers.
 * Prevents unauthorized block modifications, container access, and mob griefing.
 */
class ProtectionListener(private val plugin: BetterTrialChambers) : Listener {

    /** Last time (ms) each player was shown a protection-denied message, for spam suppression. */
    private val lastDenyMessage = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()

    /**
     * Sends a chamber-protection denial message to [player], rate-limited per player so a
     * multi-block action (e.g. an AdvancedEnchantments "Blast Mining" enchant, a vein miner, or
     * rapid clicking) doesn't flood the chat with one line per affected block. Throttle window is
     * `protection.message-cooldown-ms` (default 1500; set 0 to disable throttling).
     */
    /** Logs a diagnostic line only when `debug.verbose-logging` is on. Call sites are rare events. */
    private fun dbg(message: String) {
        if (plugin.config.getBoolean("debug.verbose-logging", false)) plugin.logger.info("[Protection] $message")
    }

    private fun notifyBlocked(player: Player, messageKey: String) {
        val cooldownMs = plugin.config.getLong("protection.message-cooldown-ms", 1500L)
        if (cooldownMs > 0L) {
            val now = System.currentTimeMillis()
            val last = lastDenyMessage[player.uniqueId]
            if (last != null && now - last < cooldownMs) return
            lastDenyMessage[player.uniqueId] = now
        }
        player.sendMessage(plugin.getMessageComponent(messageKey))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (offEverywhere(ProtectionSetting.BLOCK_BREAK)) return

        val player = event.player
        val location = event.block.location

        // Check bypass permission
        if (player.hasPermission("btc.bypass.protection")) return

        val chamber = guardedChamberAt(location, ProtectionSetting.BLOCK_BREAK) ?: return
        // Respect WorldGuard: defer to a region that grants this player build rights.
        if (deferToWorldGuard(location, player)) return

        // v1.7.0 tunnel-breaking: opt-in mode letting players mine INTO a walled chamber
        // through a configured block set (default: tuff-brick shell), with drops always
        // suppressed so the renewable chamber can't be farmed. Mined blocks come back on
        // the next snapshot reset.
        when (tunnelBreakVerdict(chamber, event.block)) {
            TunnelVerdict.ALLOW -> {
                event.isDropItems = false
                event.expToDrop = 0
                return
            }
            TunnelVerdict.TOO_DEEP -> {
                event.isCancelled = true
                notifyBlocked(player, "tunnel-only-outer-wall")
                return
            }
            TunnelVerdict.NOT_TUNNELABLE -> { /* fall through to the generic denial */ }
        }

        event.isCancelled = true
        notifyBlocked(player, "cannot-break-blocks")
    }

    private enum class TunnelVerdict { ALLOW, TOO_DEEP, NOT_TUNNELABLE }

    /**
     * Memoized `protection.tunnel-breaking` settings. Invalidation is by config-object
     * identity: `plugin.reloadConfig()` swaps the [org.bukkit.configuration.file.FileConfiguration]
     * instance, so a reload naturally re-parses without needing a hook in the reload path.
     */
    private data class TunnelSettings(val enabled: Boolean, val blocks: java.util.EnumSet<Material>, val shellDepth: Int)
    @Volatile private var tunnelCache: Pair<org.bukkit.configuration.file.FileConfiguration, TunnelSettings>? = null

    private fun tunnelSettings(): TunnelSettings {
        val config = plugin.config
        tunnelCache?.let { (cfg, settings) -> if (cfg === config) return settings }
        val enabled = config.getBoolean("protection.tunnel-breaking.enabled", false)
        val blocks = java.util.EnumSet.noneOf(Material::class.java)
        if (enabled) {
            for (name in config.getStringList("protection.tunnel-breaking.blocks")) {
                val mat = Material.matchMaterial(name.trim())
                if (mat != null && mat.isBlock) blocks += mat
                else plugin.logger.warning("[Protection] tunnel-breaking.blocks: unknown material '$name'; skipped")
            }
        }
        val shellDepth = config.getInt("protection.tunnel-breaking.shell-depth", 3).coerceIn(0, 64)
        val settings = TunnelSettings(enabled, blocks, shellDepth)
        tunnelCache = config to settings
        return settings
    }

    private fun tunnelBreakVerdict(chamber: Chamber, block: org.bukkit.block.Block): TunnelVerdict {
        val s = tunnelSettings()
        if (!s.enabled || block.type !in s.blocks) return TunnelVerdict.NOT_TUNNELABLE
        if (s.shellDepth == 0) return TunnelVerdict.ALLOW // 0 = anywhere in the chamber
        return if (chamber.distanceToBoundary(block.x, block.y, block.z) < s.shellDepth) TunnelVerdict.ALLOW
        else TunnelVerdict.TOO_DEEP
    }

    /**
     * MONITOR-priority handler that auto-pauses a chamber when enough critical blocks
     * (vaults or trial spawners) inside it have been destroyed.
     *
     * Only active when `protection.auto-pause-on-destruction: true`.
     * `protection.auto-pause-threshold` (default 6) sets how many critical blocks must
     * be broken before the pause fires, so 1–2 stray breaks don't trigger it, but
     * systematic demolition (≥ threshold) does.
     *
     * The counter resets to zero whenever the chamber's pause state changes (via
     * [ChamberManager.resetDestructionCounter]), so a resumed chamber always starts fresh.
     *
     * Fires only on breaks that were NOT cancelled by any higher-priority handler.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreakMonitor(event: BlockBreakEvent) {
        if (!plugin.config.getBoolean("protection.auto-pause-on-destruction", false)) return
        val type = event.block.type
        if (type != Material.VAULT && type != Material.TRIAL_SPAWNER) return

        val chamber = plugin.chamberManager.getCachedChamberAt(event.block.location) ?: return
        if (chamber.isPaused) return

        val threshold = plugin.config.getInt("protection.auto-pause-threshold", 6).coerceAtLeast(1)
        val count = plugin.chamberManager.incrementDestructionCounter(chamber.id)
        if (count < threshold) return

        plugin.launchAsync {
            val success = plugin.chamberManager.setPaused(chamber.id, true)
            if (success) {
                plugin.scheduler.runTask(Runnable {
                    plugin.server.onlinePlayers
                        .filter { it.hasPermission("btc.discovery.notify") }
                        .forEach { p ->
                            p.sendMessage(plugin.getMessageComponent(
                                "chamber-auto-paused",
                                "chamber" to chamber.name,
                                "block" to type.name.lowercase().replace('_', ' '),
                                "count" to count
                            ))
                        }
                })
            }
        }
    }

    /**
     * Prevents player-vs-player damage inside registered chambers when
     * `protection.allow-pvp: false`. Covers melee and player-shot projectiles. Self-damage and
     * mob damage are untouched; paused chambers and players with `btc.bypass.protection` are
     * exempt. When `allow-pvp` is true (default) this no-ops and PvP follows world/server rules.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(event: EntityDamageByEntityEvent) {
        if (masterOff()) return
        if (ProtectionSetting.PVP.globalValue(plugin.config) &&
            !plugin.chamberManager.anyChamberSets(ProtectionSetting.PVP.key, false)) return

        val victim = event.entity as? Player ?: return
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            is org.bukkit.entity.AreaEffectCloud -> damager.source as? Player
            is org.bukkit.entity.TNTPrimed -> damager.source as? Player
            else -> null
        } ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val chamber = protectedChamberAt(victim.location) ?: return
        if (isOn(chamber, ProtectionSetting.PVP)) return // PvP allowed here
        if (attacker.hasPermission("btc.bypass.protection")) {
            dbg("PvP in '${chamber.name}' allowed: ${attacker.name} has btc.bypass.protection (note: OPs have this by default)")
            return
        }

        event.isCancelled = true
        dbg("BLOCKED PvP in '${chamber.name}': ${attacker.name} -> ${victim.name}")
        notifyBlocked(attacker, "pvp-disabled-in-chamber")
    }

    /**
     * Blocks **teleporting into** a registered chamber from outside it when
     * `protection.prevent-teleport-into-chamber: true`. Catches `/tpa`, `/tpahere`, `/home`,
     * `/warp`, `/tp`, ender pearls, chorus fruit, any teleport, since it hooks the teleport
     * itself rather than specific commands. Players with `btc.bypass.entry`, spectators, and
     * creative-mode players are exempt (this also covers TCP's own spectator-entry teleport,
     * which sets SPECTATOR before teleporting). Walking in through the entrance is unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTeleportIntoChamber(event: PlayerTeleportEvent) {
        if (blockedTeleportInto(event.player, event.from, event.to, event.cause)) event.isCancelled = true
    }

    /**
     * A portal counts as a teleport too.
     *
     * [org.bukkit.event.player.PlayerPortalEvent] extends [PlayerTeleportEvent]
     * but keeps its own handler list, so the handler above never sees it: a
     * portal whose far end sits inside a chamber walked straight past the
     * setting that is supposed to stop people arriving that way.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPortalIntoChamber(event: org.bukkit.event.player.PlayerPortalEvent) {
        if (blockedTeleportInto(event.player, event.from, event.to, event.cause)) event.isCancelled = true
    }

    /** True when `prevent-teleport-into-chamber` should stop this arrival. */
    private fun blockedTeleportInto(
        player: Player,
        from: org.bukkit.Location,
        to: org.bukkit.Location?,
        cause: PlayerTeleportEvent.TeleportCause,
    ): Boolean {
        if (offEverywhere(ProtectionSetting.TELEPORT)) return false

        val destination = to ?: return false
        val toChamber = guardedChamberAt(destination, ProtectionSetting.TELEPORT) ?: return false
        // Allow teleporting *within* the same chamber (e.g. /back while inside it).
        if (plugin.chamberManager.getCachedChamberAt(from)?.id == toChamber.id) return false

        if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
            dbg("teleport into '${toChamber.name}' allowed for ${player.name}: ${player.gameMode} mode is exempt")
            return false
        }
        if (player.hasPermission("btc.bypass.entry")) {
            dbg("teleport into '${toChamber.name}' allowed for ${player.name}: has btc.bypass.entry (note: OPs have this by default)")
            return false
        }

        dbg("BLOCKED teleport into '${toChamber.name}' for ${player.name} (cause $cause)")
        notifyBlocked(player, "cannot-teleport-into-chamber")
        return true
    }

    /**
     * Gates **walking into** a registered chamber: when `protection.prevent-entry-without-permission:
     * true`, a player without `btc.bypass.entry` is stopped at the boundary (the move is cancelled,
     * setting them back). Spectators and creative-mode players are exempt; moving within a chamber
     * you're already inside is allowed. Only runs on a block change, and only when the toggle is on.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onWalkIntoChamber(event: PlayerMoveEvent) {
        if (offEverywhere(ProtectionSetting.ENTRY)) return

        val to = event.to
        val from = event.from
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return // sub-block move

        val toChamber = guardedChamberAt(to, ProtectionSetting.ENTRY) ?: return
        if (plugin.chamberManager.getCachedChamberAt(from)?.id == toChamber.id) return // already inside it

        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
            dbg("entry into '${toChamber.name}' allowed for ${player.name}: ${player.gameMode} mode is exempt")
            return
        }
        if (player.hasPermission("btc.bypass.entry")) {
            dbg("entry into '${toChamber.name}' allowed for ${player.name}: has btc.bypass.entry (note: OPs have this by default)")
            return
        }

        event.isCancelled = true
        dbg("BLOCKED entry into '${toChamber.name}' for ${player.name}")
        notifyBlocked(player, "cannot-enter-chamber")
    }

    /** Stops players riding boats, minecarts, horses, or other vehicles across a gated chamber boundary. */
    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleMoveIntoChamber(event: org.bukkit.event.vehicle.VehicleMoveEvent) {
        if (offEverywhere(ProtectionSetting.ENTRY)) return
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val toChamber = guardedChamberAt(to, ProtectionSetting.ENTRY) ?: return
        if (plugin.chamberManager.getCachedChamberAt(from)?.id == toChamber.id) return

        val blockedPassengers = event.vehicle.passengers.filterIsInstance<Player>().filter { p ->
            p.gameMode != GameMode.SPECTATOR &&
                p.gameMode != GameMode.CREATIVE &&
                !p.hasPermission("btc.bypass.entry")
        }
        if (blockedPassengers.isEmpty()) return

        event.vehicle.teleportAsync(from)
        blockedPassengers.forEach { p ->
            dbg("BLOCKED vehicle entry into '${toChamber.name}' for ${p.name}")
            notifyBlocked(p, "cannot-enter-chamber")
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (offEverywhere(ProtectionSetting.BLOCK_PLACE)) return

        val player = event.player

        // Check bypass permission
        if (player.hasPermission("btc.bypass.protection")) return

        val locations = if (event is org.bukkit.event.block.BlockMultiPlaceEvent) {
            event.replacedBlockStates.map { it.location }
        } else {
            listOf(event.block.location)
        }
        for (location in locations) {
            guardedChamberAt(location, ProtectionSetting.BLOCK_PLACE) ?: continue
            if (deferToWorldGuard(location, player)) continue
            event.isCancelled = true
            notifyBlocked(player, "cannot-place-blocks")
            return
        }
    }

    /**
     * v1.5.7: blocks placing functioning VAULT blocks outside registered
     * chambers. A wild vault is a permanent vanilla loot dispenser TCP can't
     * manage (no per-player tracking, no resets, no loot tables), and
     * out-of-chamber vault mechanics are TCP-VaultCrates' domain. Admins
     * holding the bypass permission (default op) can still place them, so
     * crate setup and creative building are unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onWildVaultPlace(event: BlockPlaceEvent) {
        if (event.block.type != org.bukkit.Material.VAULT) return
        if (!plugin.config.getBoolean("protection.block-wild-vault-placement", true)) return
        if (event.player.hasPermission("btc.bypass.vaultplace")) return
        // Inside a registered chamber the normal protection rules apply instead.
        if (plugin.chamberManager.getCachedChamberAt(event.block.location) != null) return

        event.isCancelled = true
        event.player.sendMessage(plugin.getMessageComponent("wild-vault-place-blocked"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerAccess(event: PlayerInteractEvent) {
        if (offEverywhere(ProtectionSetting.CONTAINERS)) return
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        // Every block that holds items, not only the ones with a chest-style
        // screen. A shelf, a lectern, a jukebox, a chiseled bookshelf, a
        // decorated pot, and a campfire all hold items on right-click.
        val state = block.state
        if (state !is io.papermc.paper.block.TileStateInventoryHolder &&
            state !is org.bukkit.block.Campfire
        ) return

        val player = event.player
        val location = block.location

        // Check bypass permission
        if (player.hasPermission("btc.bypass.protection")) return

        // Allow vault access (handled by VaultInteractListener)
        if (block.type.name.contains("VAULT")) return

        guardedChamberAt(location, ProtectionSetting.CONTAINERS) ?: return
        if (deferToWorldGuard(location, player)) return
        // Cancel both main-hand and off-hand passes so off-hand clicks can't bypass
        // container protection, but only send the denial message on the main-hand pass.
        event.isCancelled = true
        if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND) {
            notifyBlocked(player, "cannot-access-container")
        }
    }

    /** Stops hoppers and hopper minecarts draining or feeding protected chamber containers. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHopperMoveContainer(event: org.bukkit.event.inventory.InventoryMoveItemEvent) {
        if (offEverywhere(ProtectionSetting.CONTAINERS)) return
        val srcLoc = event.source.location
        val dstLoc = event.destination.location
        val srcChamber = srcLoc?.let { guardedChamberAt(it, ProtectionSetting.CONTAINERS) }
        val dstChamber = dstLoc?.let { guardedChamberAt(it, ProtectionSetting.CONTAINERS) }
        if (srcChamber == null && dstChamber == null) return
        // Allow redstone hoppers that belong to the same chamber (both ends inside it).
        if (srcChamber != null && dstChamber != null && srcChamber.id == dstChamber.id) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (offEverywhere(ProtectionSetting.MOB_GRIEFING)) return
        // Wind charges (Breeze and player) only toggle buttons, trapdoors, copper bulbs,
        // and dispensers; they never destroy blocks, so let chamber mechanics work.
        if (event.explosionResult == org.bukkit.ExplosionResult.TRIGGER_BLOCK) return

        // v1.7.2: filter per BLOCK, not by the explosion center's chamber. The old
        // check resolved the chamber at the CENTER, a creeper/TNT detonating just
        // outside the wall wasn't "in" any chamber, so its blast destroyed chamber
        // blocks unprotected (same outside-in hole the AdvancedEnchantments check
        // had before 1.5.21). Cache-only, sync-safe.
        event.blockList().removeIf { block ->
            guardedChamberAt(block.location, ProtectionSetting.MOB_GRIEFING) != null
        }
    }

    /**
     * v1.7.2: block-sourced explosions (bed/respawn-anchor in the wrong dimension,
     * exploding TNT minecart rails, etc.) fire BlockExplodeEvent, not
     * EntityExplodeEvent, they previously bypassed chamber protection entirely.
     * Same per-block filter as [onEntityExplode].
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: org.bukkit.event.block.BlockExplodeEvent) {
        if (offEverywhere(ProtectionSetting.MOB_GRIEFING)) return

        event.blockList().removeIf { block ->
            guardedChamberAt(block.location, ProtectionSetting.MOB_GRIEFING) != null
        }
    }

    /**
     * Entities changing blocks:
     * - Players scraping/waxing copper, trampling farmland/eggs, or (26.4+) cracking Brittle Ice -> BLOCK_BREAK.
     * - Player-shot projectiles breaking decorated pots or chorus flowers -> BLOCK_BREAK.
     * - Mobs (endermen, silverfish, ravagers, mob-shot projectiles in 26.4+) -> MOB_GRIEFING.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val location = event.block.location
        val entity = event.entity

        if (entity is Player) {
            if (offEverywhere(ProtectionSetting.BLOCK_BREAK)) return
            if (entity.hasPermission("btc.bypass.protection")) return
            guardedChamberAt(location, ProtectionSetting.BLOCK_BREAK) ?: return
            if (deferToWorldGuard(location, entity)) return
            event.isCancelled = true
            notifyBlocked(entity, "cannot-break-blocks")
            return
        }

        val shooter = (entity as? Projectile)?.shooter as? Player
        if (shooter != null) {
            if (offEverywhere(ProtectionSetting.BLOCK_BREAK)) return
            if (shooter.hasPermission("btc.bypass.protection")) return
            guardedChamberAt(location, ProtectionSetting.BLOCK_BREAK) ?: return
            if (deferToWorldGuard(location, shooter)) return
            event.isCancelled = true
            notifyBlocked(shooter, "cannot-break-blocks")
            return
        }

        if (offEverywhere(ProtectionSetting.MOB_GRIEFING)) return
        guardedChamberAt(location, ProtectionSetting.MOB_GRIEFING) ?: return
        event.isCancelled = true
    }

    /**
     * Returns true when TCP should yield to WorldGuard at [location], i.e.
     * `protection.worldguard-integration` is on, WG is installed, and a WG region
     * there grants [player] build rights (membership, an explicit `build` allow,
     * or WG bypass). Callers `return` early (skip TCP protection) when this is
     * true, so region owners/staff can work inside chambers overlapping their
     * regions. No-op (false) when the setting is off or WG is absent.
     */

    // ======================================================================
    // Keeping a chamber the way it was built
    // ======================================================================
    //
    // Breaking and placing blocks were the only two ways in that were watched
    // for. There are a lot of others, and none of them raise a block break or a
    // block place, so none of them were ever seen:
    //
    //   - a bucket of water or lava does not count as placing a block
    //   - a piston can push blocks in, or pull them out, from outside the walls
    //   - fire spreads on its own once something is alight
    //   - item frames, paintings and armour stands are not blocks at all, so
    //     nothing stopped anyone taking them
    //
    // This matters most for the chambers that were never Trial Chambers. Plenty
    // of servers register a hand-built arena or a minigame area as a chamber
    // purely for the protection and the reset, and every one of these was open.

    /** The chamber at [location], or null when there is nothing to protect there. */
    private fun protectedChamberAt(location: org.bukkit.Location) =
        plugin.chamberManager.getCachedChamberAt(location)?.takeIf { !it.isPaused }

    /** The chamber at [location] when [setting] is on in it, otherwise null. */
    private fun guardedChamberAt(location: org.bukkit.Location, setting: ProtectionSetting): Chamber? =
        protectedChamberAt(location)?.takeIf { isOn(it, setting) }

    private fun isOn(chamber: Chamber, setting: ProtectionSetting): Boolean =
        setting.valueFor(chamber, plugin.config)

    private fun masterOff(): Boolean = !plugin.config.getBoolean("protection.enabled", true)

    /**
     * True when [setting] cannot apply anywhere: protection is off as a whole,
     * or the setting is off server-wide and no chamber turned it on for itself.
     * Checked first so the events that fire constantly stay cheap.
     */
    private fun offEverywhere(setting: ProtectionSetting): Boolean {
        if (masterOff()) return true
        if (setting.globalValue(plugin.config)) return false
        return !plugin.chamberManager.anyChamberSets(setting.key, true)
    }

    /**
     * Emptying a bucket does not raise a block-place event, so it needs its own.
     * It is still a player putting something into the chamber, so it answers to
     * `prevent-block-place`: with that off, water, lava and powder snow can be
     * placed like any block.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (offEverywhere(ProtectionSetting.BLOCK_PLACE)) return
        val player = event.player
        if (player.hasPermission("btc.bypass.protection")) return
        val location = event.block.location
        guardedChamberAt(location, ProtectionSetting.BLOCK_PLACE) ?: return
        if (deferToWorldGuard(location, player)) return
        event.isCancelled = true
        notifyBlocked(player, "cannot-place-blocks")
    }

    /** Scooping water, lava, or powder snow out of a chamber removes a block, so it answers to `prevent-block-break`. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBucketFill(event: org.bukkit.event.player.PlayerBucketFillEvent) {
        if (offEverywhere(ProtectionSetting.BLOCK_BREAK)) return
        val player = event.player
        if (player.hasPermission("btc.bypass.protection")) return
        val location = event.block.location
        guardedChamberAt(location, ProtectionSetting.BLOCK_BREAK) ?: return
        if (deferToWorldGuard(location, player)) return
        event.isCancelled = true
        notifyBlocked(player, "cannot-break-blocks")
    }

    /** Water or lava poured just outside the walls, running in. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onLiquidFlow(event: BlockFromToEvent) {
        if (offEverywhere(ProtectionSetting.LIQUIDS)) return
        // Only stop it crossing INTO a chamber. Liquid already inside one, in a
        // build that was made with it, goes on working.
        if (guardedChamberAt(event.toBlock.location, ProtectionSetting.LIQUIDS) == null) return
        if (protectedChamberAt(event.block.location) != null) return
        event.isCancelled = true
    }

    /**
     * A dispenser outside the walls firing liquids, powder snow, shulker boxes, fire, or
     * empty buckets into a chamber. Dispensers inside the same chamber (e.g. vanilla Trial
     * Chamber traps) are left alone.
     *
     * TODO(26.4): dispensers are data driven from snapshot 9 on and Paper has to rebuild
     * BlockDispenseEvent on them. On Paper 26.4, re-test that buckets fired from outside a
     * chamber are still stopped here.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDispense(event: BlockDispenseEvent) {
        val directional = event.block.blockData as? org.bukkit.block.data.Directional ?: return
        val targetLoc = event.block.getRelative(directional.facing).location
        val itemType = event.item.type

        val setting = when {
            itemType == Material.WATER_BUCKET ||
                itemType == Material.LAVA_BUCKET ||
                itemType == Material.POWDER_SNOW_BUCKET ||
                itemType == Material.BUCKET ||
                itemType.name.endsWith("_BUCKET") -> ProtectionSetting.LIQUIDS
            itemType == Material.FLINT_AND_STEEL || itemType == Material.FIRE_CHARGE -> ProtectionSetting.FIRE
            itemType.name.endsWith("SHULKER_BOX") ||
                itemType == Material.TNT ||
                itemType == Material.ARMOR_STAND ||
                itemType == Material.CARVED_PUMPKIN ||
                itemType == Material.WITHER_SKELETON_SKULL -> ProtectionSetting.BLOCK_PLACE
            else -> return
        }
        if (offEverywhere(setting)) return
        val targetChamber = guardedChamberAt(targetLoc, setting) ?: return
        // Allow dispensers that live inside the same chamber (vanilla chamber traps).
        if (protectedChamberAt(event.block.location)?.id == targetChamber.id) return
        event.isCancelled = true
    }

    /** Lighting a fire inside a chamber. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        if (offEverywhere(ProtectionSetting.FIRE)) return
        val location = event.block.location
        guardedChamberAt(location, ProtectionSetting.FIRE) ?: return
        val player = event.player
        if (player != null) {
            if (player.hasPermission("btc.bypass.protection")) return
            if (deferToWorldGuard(location, player)) return
            event.isCancelled = true
            notifyBlocked(player, "cannot-place-blocks")
            return
        }
        // Lightning, lava, or fire spreading in from somewhere else.
        event.isCancelled = true
    }

    /** Fire eating the chamber once something is already alight. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) {
        if (offEverywhere(ProtectionSetting.FIRE)) return
        guardedChamberAt(event.block.location, ProtectionSetting.FIRE) ?: return
        event.isCancelled = true
    }

    /**
     * Pistons, in both directions.
     *
     * A piston reaches twelve blocks, so someone outside the walls can push
     * blocks in or drag them out without ever touching the chamber themselves,
     * and no block break or place is raised for any of it. A piston that is
     * itself inside the same chamber is left alone, because that is somebody's
     * redstone working exactly as they built it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (offEverywhere(ProtectionSetting.PISTONS)) return
        val affected = buildList {
            // Piston head itself extending into a chamber even with 0 pushed blocks
            add(event.block.getRelative(event.direction).location)
            for (b in event.blocks) {
                add(b.location)
                add(b.getRelative(event.direction).location)
            }
        }
        if (pistonReachesIntoChamber(event.block.location, affected)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (offEverywhere(ProtectionSetting.PISTONS)) return
        val affected = buildList {
            for (b in event.blocks) {
                add(b.location)
                add(b.getRelative(event.direction).location)
            }
        }
        if (pistonReachesIntoChamber(event.block.location, affected)) {
            event.isCancelled = true
        }
    }

    /** True when a piston would move a protected block it does not share a chamber with. */
    private fun pistonReachesIntoChamber(
        piston: org.bukkit.Location,
        moved: List<org.bukkit.Location>,
    ): Boolean {
        val pistonChamber = protectedChamberAt(piston)
        return moved.any { movedBlock ->
            val chamber = guardedChamberAt(movedBlock, ProtectionSetting.PISTONS) ?: return@any false
            chamber.id != pistonChamber?.id
        }
    }

    // ---------- Decorations ----------
    //
    // Item frames, paintings and armour stands are entities rather than blocks,
    // so none of the block protection above has ever applied to them and anyone
    // could walk off with the lot. Destroying and looting are stopped; turning a
    // frame or swapping what is in it is left alone, since that survives a reset
    // and some minigames are built on it.

    /**
     * True for the entities that are part of a build rather than part of the
     * world.
     *
     * Asks the same question the snapshot asks, so what a reset puts back and
     * what protection defends can never drift apart.
     */
    private fun isDecoration(entity: org.bukkit.entity.Entity): Boolean =
        com.esmpfun.bettertrialchambers.utils.DecorationEntities.isDecoration(entity)

    /**
     * Stops a decoration being taken, and says so to the player who tried.
     *
     * [remover] is whoever did it, or null when nothing did: fire, an
     * explosion, the block it hung on being gone. Returns true when the
     * decoration is defended, false when this person is allowed to take it.
     */
    private fun denyDecorationRemoval(
        entity: org.bukkit.entity.Entity,
        remover: org.bukkit.entity.Entity?,
    ): Boolean {
        val location = entity.location
        guardedChamberAt(location, ProtectionSetting.DECORATIONS) ?: return false
        if (remover is Player) {
            if (remover.hasPermission("btc.bypass.protection")) return false
            if (deferToWorldGuard(location, remover)) return false
            notifyBlocked(remover, "cannot-break-blocks")
        }
        return true
    }

    /**
     * Anything hung on a wall: item frames, glow item frames, paintings.
     *
     * [HangingBreakByEntityEvent] arrives here too, because the two events
     * share one handler list. It is checked for rather than listened to
     * separately: as two handlers, whichever the server happened to call first
     * decided the outcome, and when that was the one with no idea who did it,
     * staff with `btc.bypass.protection` could not take a painting down and a
     * WorldGuard region granting build rights was ignored.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        if (offEverywhere(ProtectionSetting.DECORATIONS)) return
        val remover = (event as? HangingBreakByEntityEvent)?.remover
        if (denyDecorationRemoval(event.entity, remover)) event.isCancelled = true
    }

    /**
     * For a version adapter's own break event (26.3 cushions): true when taking [entity]
     * must be stopped. Same checks as [onHangingBreak].
     */
    fun denyDecorationRemovalFor(entity: org.bukkit.entity.Entity, remover: org.bukkit.entity.Entity?): Boolean {
        if (offEverywhere(ProtectionSetting.DECORATIONS)) return false
        if (!isDecoration(entity)) return false
        return denyDecorationRemoval(entity, remover)
    }

    /** Placing a decoration counts as placing a block, so `prevent-block-place` covers it too. */
    private fun denyDecorationPlace(entity: org.bukkit.entity.Entity, player: Player?): Boolean {
        if (player == null || offEverywhere(ProtectionSetting.BLOCK_PLACE)) return false
        if (player.hasPermission("btc.bypass.protection")) return false
        val location = entity.location
        guardedChamberAt(location, ProtectionSetting.BLOCK_PLACE) ?: return false
        if (deferToWorldGuard(location, player)) return false
        notifyBlocked(player, "cannot-place-blocks")
        return true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        if (denyDecorationPlace(event.entity, event.player)) event.isCancelled = true
    }

    /** Armour stands, and cushions on 26.3. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityPlace(event: EntityPlaceEvent) {
        if (!isDecoration(event.entity)) return
        if (denyDecorationPlace(event.entity, event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        if (offEverywhere(ProtectionSetting.DECORATIONS)) return
        val player = event.player
        if (player.hasPermission("btc.bypass.protection")) return
        val location = event.rightClicked.location
        guardedChamberAt(location, ProtectionSetting.DECORATIONS) ?: return
        if (deferToWorldGuard(location, player)) return
        event.isCancelled = true
        notifyBlocked(player, "cannot-break-blocks")
    }

    /**
     * Anything else that would destroy a decoration: fire, explosions, mobs,
     * and a player hitting one. [EntityDamageByEntityEvent] shares this handler
     * list as well, so who struck the blow is read from the event for the same
     * reason as above.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDecorationDamaged(event: EntityDamageEvent) {
        if (offEverywhere(ProtectionSetting.DECORATIONS)) return
        if (!isDecoration(event.entity)) return
        val damager = (event as? EntityDamageByEntityEvent)?.damager
        if (denyDecorationRemoval(event.entity, damager)) event.isCancelled = true
    }

    // ---------- Natural change ----------
    //
    // Off by default. Leaves dropping, grass creeping and ice melting is the
    // world behaving normally, and a reset puts it all back anyway. Turn it on
    // for a build that has to stay exactly as placed between resets.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        guardedChamberAt(event.block.location, ProtectionSetting.NATURAL_DECAY) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        guardedChamberAt(event.block.location, ProtectionSetting.NATURAL_DECAY) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        val location = event.block.location
        if (event is org.bukkit.event.block.EntityBlockFormEvent) {
            val entity = event.entity
            if (entity is Player) {
                // Frost Walker boots freezing chamber water
                if (!offEverywhere(ProtectionSetting.BLOCK_PLACE) &&
                    !entity.hasPermission("btc.bypass.protection") &&
                    guardedChamberAt(location, ProtectionSetting.BLOCK_PLACE) != null &&
                    !deferToWorldGuard(location, entity)
                ) {
                    event.isCancelled = true
                    return
                }
            } else {
                // Snow golems, 26.4 Cold Sulfur Cubes freezing water, etc.
                if (!offEverywhere(ProtectionSetting.MOB_GRIEFING) &&
                    guardedChamberAt(location, ProtectionSetting.MOB_GRIEFING) != null
                ) {
                    event.isCancelled = true
                    return
                }
            }
        }
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        guardedChamberAt(location, ProtectionSetting.NATURAL_DECAY) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        // Fire spreading is griefing rather than nature, so it answers to the
        // fire setting; everything else here answers to the decay setting.
        val setting = if (event.newState.type == Material.FIRE) ProtectionSetting.FIRE else ProtectionSetting.NATURAL_DECAY
        if (offEverywhere(setting)) return
        guardedChamberAt(event.block.location, setting) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        guardedChamberAt(event.block.location, ProtectionSetting.NATURAL_DECAY) ?: return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onStructureGrow(event: StructureGrowEvent) {
        // Always stop a tree/mushroom grown outside a chamber from pushing blocks into it.
        val originChamber = protectedChamberAt(event.location)
        val player = event.player
        if (originChamber == null && !offEverywhere(ProtectionSetting.BLOCK_PLACE)) {
            val crossesIn = event.blocks.any { state ->
                guardedChamberAt(state.location, ProtectionSetting.BLOCK_PLACE) != null &&
                    (player == null || (!player.hasPermission("btc.bypass.protection") && !deferToWorldGuard(state.location, player)))
            }
            if (crossesIn) {
                event.isCancelled = true
                return
            }
        }
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        if (event.blocks.none { guardedChamberAt(it.location, ProtectionSetting.NATURAL_DECAY) != null }) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFertilize(event: BlockFertilizeEvent) {
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        if (event.blocks.none { guardedChamberAt(it.location, ProtectionSetting.NATURAL_DECAY) != null }) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSpongeAbsorb(event: SpongeAbsorbEvent) {
        // Always stop a sponge placed outside a chamber from draining water inside it.
        val spongeChamber = protectedChamberAt(event.block.location)
        if (spongeChamber == null && !offEverywhere(ProtectionSetting.BLOCK_BREAK)) {
            if (event.blocks.any { guardedChamberAt(it.block.location, ProtectionSetting.BLOCK_BREAK) != null }) {
                event.isCancelled = true
                return
            }
        }
        if (offEverywhere(ProtectionSetting.NATURAL_DECAY)) return
        if (event.blocks.none { guardedChamberAt(it.block.location, ProtectionSetting.NATURAL_DECAY) != null }) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        lastDenyMessage.remove(event.player.uniqueId)
    }

    private fun deferToWorldGuard(location: org.bukkit.Location, player: Player): Boolean {
        if (!plugin.config.getBoolean("protection.worldguard-integration", true)) return false
        if (!com.esmpfun.bettertrialchambers.utils.WorldGuardHook.isAvailable(plugin)) return false
        return com.esmpfun.bettertrialchambers.utils.WorldGuardHook.grantsBuild(player, location)
    }
}

package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.entity.Projectile
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Handles protection for Trial Chambers.
 * Prevents unauthorized block modifications, container access, and mob griefing.
 */
class ProtectionListener(private val plugin: TrialChamberPro) : Listener {

    /** Last time (ms) each player was shown a protection-denied message — for spam suppression. */
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
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-block-break", true)) return

        val player = event.player
        val location = event.block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Check if in chamber (synchronous, cache-based); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
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
                else plugin.logger.warning("[Protection] tunnel-breaking.blocks: unknown material '$name' — skipped")
            }
        }
        val shellDepth = config.getInt("protection.tunnel-breaking.shell-depth", 3).coerceIn(0, 64)
        val settings = TunnelSettings(enabled, blocks, shellDepth)
        tunnelCache = config to settings
        return settings
    }

    private fun tunnelBreakVerdict(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber, block: org.bukkit.block.Block): TunnelVerdict {
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
     * be broken before the pause fires — so 1–2 stray breaks don't trigger it, but
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
                        .filter { it.hasPermission("tcp.discovery.notify") }
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
     * mob damage are untouched; paused chambers and players with `tcp.bypass.protection` are
     * exempt. When `allow-pvp` is true (default) this no-ops and PvP follows world/server rules.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (plugin.config.getBoolean("protection.allow-pvp", true)) return // PvP allowed → nothing to do

        val victim = event.entity as? Player ?: return
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val chamber = plugin.chamberManager.getCachedChamberAt(victim.location) ?: return
        if (chamber.isPaused) return
        if (attacker.hasPermission("tcp.bypass.protection")) {
            dbg("PvP in '${chamber.name}' allowed: ${attacker.name} has tcp.bypass.protection (note: OPs have this by default)")
            return
        }

        event.isCancelled = true
        dbg("BLOCKED PvP in '${chamber.name}': ${attacker.name} -> ${victim.name}")
        notifyBlocked(attacker, "pvp-disabled-in-chamber")
    }

    /**
     * Blocks **teleporting into** a registered chamber from outside it when
     * `protection.prevent-teleport-into-chamber: true`. Catches `/tpa`, `/tpahere`, `/home`,
     * `/warp`, `/tp`, ender pearls, chorus fruit — any teleport, since it hooks the teleport
     * itself rather than specific commands. Players with `tcp.bypass.entry`, spectators, and
     * creative-mode players are exempt (this also covers TCP's own spectator-entry teleport,
     * which sets SPECTATOR before teleporting). Walking in through the entrance is unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onTeleportIntoChamber(event: PlayerTeleportEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-teleport-into-chamber", false)) return

        val to = event.to ?: return
        val toChamber = plugin.chamberManager.getCachedChamberAt(to) ?: return // not teleporting into a chamber
        if (toChamber.isPaused) return
        // Allow teleporting *within* the same chamber (e.g. /back while inside it).
        if (plugin.chamberManager.getCachedChamberAt(event.from)?.id == toChamber.id) return

        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
            dbg("teleport into '${toChamber.name}' allowed for ${player.name}: ${player.gameMode} mode is exempt")
            return
        }
        if (player.hasPermission("tcp.bypass.entry")) {
            dbg("teleport into '${toChamber.name}' allowed for ${player.name}: has tcp.bypass.entry (note: OPs have this by default)")
            return
        }

        event.isCancelled = true
        dbg("BLOCKED teleport into '${toChamber.name}' for ${player.name} (cause ${event.cause})")
        notifyBlocked(player, "cannot-teleport-into-chamber")
    }

    /**
     * Gates **walking into** a registered chamber: when `protection.prevent-entry-without-permission:
     * true`, a player without `tcp.bypass.entry` is stopped at the boundary (the move is cancelled,
     * setting them back). Spectators and creative-mode players are exempt; moving within a chamber
     * you're already inside is allowed. Only runs on a block change, and only when the toggle is on.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onWalkIntoChamber(event: PlayerMoveEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-entry-without-permission", false)) return

        val to = event.to
        val from = event.from
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return // sub-block move

        val toChamber = plugin.chamberManager.getCachedChamberAt(to) ?: return // not stepping into a chamber
        if (toChamber.isPaused) return
        if (plugin.chamberManager.getCachedChamberAt(from)?.id == toChamber.id) return // already inside it

        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
            dbg("entry into '${toChamber.name}' allowed for ${player.name}: ${player.gameMode} mode is exempt")
            return
        }
        if (player.hasPermission("tcp.bypass.entry")) {
            dbg("entry into '${toChamber.name}' allowed for ${player.name}: has tcp.bypass.entry (note: OPs have this by default)")
            return
        }

        event.isCancelled = true
        dbg("BLOCKED entry into '${toChamber.name}' for ${player.name}")
        notifyBlocked(player, "cannot-enter-chamber")
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-block-place", true)) return

        val player = event.player
        val location = event.block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Check if in chamber (synchronous, cache-based); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        if (deferToWorldGuard(location, player)) return
        event.isCancelled = true
        notifyBlocked(player, "cannot-place-blocks")
    }

    /**
     * v1.5.7: blocks placing functioning VAULT blocks outside registered
     * chambers. A wild vault is a permanent vanilla loot dispenser TCP can't
     * manage (no per-player tracking, no resets, no loot tables) — and
     * out-of-chamber vault mechanics are TCP-VaultCrates' domain. Admins
     * holding the bypass permission (default op) can still place them, so
     * crate setup and creative building are unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onWildVaultPlace(event: BlockPlaceEvent) {
        if (event.block.type != org.bukkit.Material.VAULT) return
        if (!plugin.config.getBoolean("protection.block-wild-vault-placement", true)) return
        if (event.player.hasPermission("tcp.bypass.vaultplace")) return
        // Inside a registered chamber the normal protection rules apply instead.
        if (plugin.chamberManager.getCachedChamberAt(event.block.location) != null) return

        event.isCancelled = true
        event.player.sendMessage(plugin.getMessageComponent("wild-vault-place-blocked"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerAccess(event: PlayerInteractEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-container-access", false)) return
        // Right-click fires PlayerInteractEvent twice (main + off hand); only act on the
        // main-hand pass so the denial message isn't sent twice.
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return

        val block = event.clickedBlock ?: return
        if (block.state !is Container) return

        val player = event.player
        val location = block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Allow vault access (handled by VaultInteractListener)
        if (block.type.name.contains("VAULT")) return

        // Check if in chamber (cache-only, sync); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        if (deferToWorldGuard(location, player)) return
        event.isCancelled = true
        notifyBlocked(player, "cannot-access-container")
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        // v1.7.2: filter per BLOCK, not by the explosion center's chamber. The old
        // check resolved the chamber at the CENTER — a creeper/TNT detonating just
        // outside the wall wasn't "in" any chamber, so its blast destroyed chamber
        // blocks unprotected (same outside-in hole the AdvancedEnchantments check
        // had before 1.5.21). Cache-only, sync-safe.
        event.blockList().removeIf { block ->
            plugin.chamberManager.getCachedChamberAt(block.location)?.takeIf { !it.isPaused } != null
        }
    }

    /**
     * v1.7.2: block-sourced explosions (bed/respawn-anchor in the wrong dimension,
     * exploding TNT minecart rails, etc.) fire BlockExplodeEvent, not
     * EntityExplodeEvent — they previously bypassed chamber protection entirely.
     * Same per-block filter as [onEntityExplode].
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: org.bukkit.event.block.BlockExplodeEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        event.blockList().removeIf { block ->
            plugin.chamberManager.getCachedChamberAt(block.location)?.takeIf { !it.isPaused } != null
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        val location = event.block.location

        // Allow players
        if (event.entity is Player) return

        // Prevent endermen, silverfish, etc. from modifying blocks in non-paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        event.isCancelled = true
    }

    /**
     * Returns true when TCP should yield to WorldGuard at [location] — i.e.
     * `protection.worldguard-integration` is on, WG is installed, and a WG region
     * there grants [player] build rights (membership, an explicit `build` allow,
     * or WG bypass). Callers `return` early (skip TCP protection) when this is
     * true, so region owners/staff can work inside chambers overlapping their
     * regions. No-op (false) when the setting is off or WG is absent.
     */
    private fun deferToWorldGuard(location: org.bukkit.Location, player: Player): Boolean {
        if (!plugin.config.getBoolean("protection.worldguard-integration", true)) return false
        if (!io.github.darkstarworks.trialChamberPro.utils.WorldGuardHook.isAvailable(plugin)) return false
        return io.github.darkstarworks.trialChamberPro.utils.WorldGuardHook.grantsBuild(player, location)
    }
}

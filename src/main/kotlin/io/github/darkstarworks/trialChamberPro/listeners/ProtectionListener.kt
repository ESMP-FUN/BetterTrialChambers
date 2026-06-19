package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Handles protection for Trial Chambers.
 * Prevents unauthorized block modifications, container access, and mob griefing.
 */
class ProtectionListener(private val plugin: TrialChamberPro) : Listener {

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
        event.isCancelled = true
        player.sendMessage(plugin.getMessageComponent("cannot-break-blocks"))
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
        player.sendMessage(plugin.getMessageComponent("cannot-place-blocks"))
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
        player.sendMessage(plugin.getMessageComponent("cannot-access-container"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        val location = event.location

        // Use cache-only sync check first; if any cached non-paused chamber contains the
        // explosion center, filter out blocks that would be affected inside its bounds.
        val chamber = plugin.chamberManager.getCachedChamberAt(location)
        if (chamber != null && !chamber.isPaused) {
            event.blockList().removeIf { block ->
                chamber.contains(block.location)
            }
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

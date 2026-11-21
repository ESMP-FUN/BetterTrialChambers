package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.KeyType
import com.esmpfun.bettertrialchambers.models.VaultLootMode
import com.esmpfun.bettertrialchambers.models.VaultType
import com.esmpfun.bettertrialchambers.utils.AdvancementUtil
import com.esmpfun.bettertrialchambers.utils.MessageUtil
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Vault
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector

/**
 * Listens for vault interactions and handles per-player loot system.
 * Validates trial keys, checks cooldowns, and provides visual feedback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultInteractListener(private val plugin: BetterTrialChambers) : Listener {

    private val listenerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        plugin.logger.severe("Exception in vault listener: ${exception.message}")
        exception.printStackTrace()
    }

    // Track vaults currently being opened to prevent spam-clicking race condition
    private val openingVaults = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVaultInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        // A right-click fires PlayerInteractEvent twice (main hand + off hand);
        // only process the main-hand pass so feedback isn't sent twice.
        if (event.hand != EquipmentSlot.HAND) return

        val block = event.clickedBlock ?: return
        val player = event.player
        val item = player.inventory.itemInMainHand

        // Check if it's a vault and determine type from block state
        if (block.type != Material.VAULT) {
            return
        }

        // Determine vault type from block data
        // Note: Paper's Vault TileState doesn't have isOminous property (unlike TrialSpawner)
        // so we must use block data string parsing
        val blockDataString = block.blockData.asString
        val vaultType = if (blockDataString.contains("ominous=true", ignoreCase = true)) {
            VaultType.OMINOUS
        } else {
            VaultType.NORMAL
        }

        // Skip plugin logic for vaults outside registered chambers, allow vanilla behavior
        val chamberForVault = plugin.chamberManager.getCachedChamberAt(block.location)
        if (chamberForVault == null) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("Vault at ${block.location.toVector()} is outside registered chambers - allowing vanilla behavior")
            }
            return
        }

        // Reject vault interactions in paused chambers
        if (chamberForVault.isPaused) {
            event.isCancelled = true
            player.sendMessage(plugin.getMessageComponent("chamber-is-paused"))
            return
        }

        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Vault interaction: blockData='$blockDataString', detected type=$vaultType")
        }

        // Always require a trial key; trial-keys.validate-key-type controls whether
        // normal keys must match normal vaults and ominous keys must match ominous vaults.
        val keyType = when (item.type) {
            Material.TRIAL_KEY -> KeyType.NORMAL
            Material.OMINOUS_TRIAL_KEY -> KeyType.OMINOUS
            else -> null
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Key check: item=${item.type.name}, keyType=$keyType")
        }

        if (keyType == null) {
            event.isCancelled = true
            routeVaultFeedback(player, block.location, false, plugin.getMessageComponent("no-key")) {
                playErrorSound(player)
            }
            return
        }

        if (plugin.config.getBoolean("trial-keys.validate-key-type", true)) {
            val requiredKeyType = when (vaultType) {
                VaultType.NORMAL -> KeyType.NORMAL
                VaultType.OMINOUS -> KeyType.OMINOUS
            }

            if (keyType != requiredKeyType) {
                event.isCancelled = true
                val requiredTypeName = if (vaultType == VaultType.OMINOUS) "Ominous" else "Normal"
                routeVaultFeedback(
                    player, block.location, false,
                    plugin.getMessageComponent("wrong-key-type", "required_type" to requiredTypeName)
                ) {
                    playErrorSound(player)
                }
                return
            }
        }

        // Handle plugin-managed vaults. VANILLA mode hands the vault straight back
        // to Minecraft, we don't cancel, don't take the key, don't roll our loot.
        val lootMode = VaultLootMode.resolve(plugin)
        if (lootMode != VaultLootMode.VANILLA) {
            event.isCancelled = true // We'll handle the vault opening ourselves

            // CRITICAL: Check the lock SYNCHRONOUSLY before launching async work.
            // In SHARED mode, omit player UUID so two players clicking the same vault
            // concurrently cannot both claim it.
            val worldName = block.world.name
            val lockKey = if (lootMode == VaultLootMode.SHARED) {
                "SHARED:$worldName:${block.location.blockX}:${block.location.blockY}:${block.location.blockZ}:${vaultType.name}"
            } else {
                "${player.uniqueId}:$worldName:${block.location.blockX}:${block.location.blockY}:${block.location.blockZ}:${vaultType.name}"
            }
            val now = System.currentTimeMillis()

            // Periodically clean up old entries to prevent memory leaks (entries older than 30 seconds)
            if (openingVaults.size > 100) {
                openingVaults.entries.removeIf { now - it.value > 30000 }
            }

            val existingOperation = openingVaults[lockKey]

            if (existingOperation != null && now - existingOperation < 5000) {
                // Operation still in progress (less than 5 seconds ago), ignore this click
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Vault interaction ignored - already opening (spam protection)")
                }
                return
            }

            // Set the lock SYNCHRONOUSLY before launching async work
            openingVaults[lockKey] = now

            // Snapshot entity-thread state (held slot + luck bonus) before hopping async (Folia-safe).
            val keySlot = player.inventory.heldItemSlot
            val luckBonus = plugin.lootManager.computeLuckBonus(player)

            listenerScope.launch(exceptionHandler) {
                handleVaultOpen(player, block.location, vaultType, keySlot, lootMode, luckBonus)
            }
        }
    }

    /**
     * Handles the vault opening logic.
     * NOTE: Spam-click protection is done SYNCHRONOUSLY in the event handler before this is called.
     *
     * Uses Paper's native Vault TileState API for cooldown tracking (hasRewardedPlayer/addRewardedPlayer).
     * This is more reliable than database tracking because:
     * 1. It uses vanilla Minecraft's built-in tracking (rewarded_players NBT)
     * 2. It persists automatically with the block state
     * 3. It resets automatically when the vault block is restored during chamber reset
     */
    private suspend fun handleVaultOpen(
        player: org.bukkit.entity.Player,
        location: org.bukkit.Location,
        vaultType: VaultType,
        keySlot: Int,
        lootMode: VaultLootMode,
        luckBonus: Int,
    ) {
        // Get vault data for the specific type (normal or ominous) - needed for loot table resolution
        val vaultData = plugin.vaultManager.getVault(location, vaultType)
        if (vaultData == null) {
            player.sendMessage(plugin.getMessageComponent("vault-not-found"))
            return
        }

        // Check permission bypass - skip cooldown check entirely.
        if (player.hasPermission("btc.bypass.cooldown")) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Vault API] Player ${player.name} has btc.bypass.cooldown permission - " +
                    "SKIPPING cooldown check (and leaving the vault unmarked)")
            }
            openVault(player, vaultData, vaultType, location, keySlot, luckBonus, markUsed = false)
            return
        }

        // SHARED mode: the vault belongs to whoever opened it first, and stays shut
        // for everybody until the chamber resets.
        if (lootMode == VaultLootMode.SHARED) {
            val claim = plugin.vaultManager.getSharedClaim(vaultData.id)
            if (claim == null) {
                openVault(player, vaultData, vaultType, location, keySlot, luckBonus)
            } else {
                val claimantName = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    Bukkit.getOfflinePlayer(claim.first).name
                }
                handleLockedVault(
                    player, vaultData, vaultType, location, keySlot, luckBonus,
                    sharedClaimedAtMs = claim.second,
                    sharedClaimantName = claimantName
                ) {
                    // Unlocking a shared vault frees it for everyone, not just this player.
                    plugin.vaultManager.resetAllCooldowns(vaultData.id)
                }
            }
            return
        }

        // Use Paper's native Vault API for cooldown checking on 1.21.2+, falling back to DB
        // tracking on Paper 1.21.1 where Vault has no rewarded-players API.
        // Returns null when the native API is unavailable (Paper 1.21.1).
        val nativeRewarded: Boolean? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                var result: Boolean? = false
                try {
                    val block = location.block
                    val vaultState = block.state as? Vault
                    if (vaultState == null) {
                        plugin.logger.warning("Block at $location is not a Vault TileState!")
                        result = false
                    } else {
                        val hasRewarded = vaultState.hasRewardedPlayer(player.uniqueId)
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("[Vault API] hasRewardedPlayer(${player.name}): $hasRewarded")
                        }
                        result = hasRewarded
                    }
                } catch (_: NoSuchMethodError) {
                    result = null // Paper 1.21.1: fall back to DB tracking
                } catch (e: Throwable) {
                    plugin.logger.severe("Error checking vault reward status: ${e.message}")
                    e.printStackTrace()
                    result = false
                } finally {
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

        val hasAlreadyRewarded = nativeRewarded
            ?: (plugin.vaultManager.getLastOpened(player.uniqueId, vaultData.id) > 0L)

        if (hasAlreadyRewarded) {
            handleLockedVault(player, vaultData, vaultType, location, keySlot, luckBonus) {
                clearNativeRewardedFlag(location, player)
            }
        } else {
            openVault(player, vaultData, vaultType, location, keySlot, luckBonus)
        }
    }

    /**
     * Shared handling for a vault this player can't just open: works out whether a
     * timed cooldown has run out, whether they can pay keys to reopen it now, and
     * otherwise tells them why it's shut.
     *
     * [sharedClaimedAtMs] / [sharedClaimantName] are set only in SHARED mode, where
     * the vault is held by whoever opened it first rather than by this player. In
     * that case [unlock] frees the vault for everybody; in per-player mode it only
     * clears this player's hold.
     */
    private suspend fun handleLockedVault(
        player: org.bukkit.entity.Player,
        vaultData: com.esmpfun.bettertrialchambers.models.VaultData,
        vaultType: VaultType,
        location: Location,
        keySlot: Int,
        luckBonus: Int,
        sharedClaimedAtMs: Long? = null,
        sharedClaimantName: String? = null,
        unlock: suspend () -> Unit
    ) {
        val isShared = sharedClaimedAtMs != null

        val cooldownHours = when (vaultType) {
            VaultType.NORMAL -> plugin.config.getLong("vaults.normal-cooldown-hours", 0)
            VaultType.OMINOUS -> plugin.config.getLong("vaults.ominous-cooldown-hours", 0)
        }

        var cooldownRemainingMs = 0L
        if (cooldownHours > 0) {
            val (canOpen, remaining) = if (isShared) {
                val elapsed = System.currentTimeMillis() - sharedClaimedAtMs
                val window = cooldownHours * 60 * 60 * 1000
                (elapsed >= window) to (window - elapsed).coerceAtLeast(0L)
            } else {
                plugin.vaultManager.canOpenVault(player.uniqueId, vaultData)
            }
            if (canOpen) {
                openVault(player, vaultData, vaultType, location, keySlot, luckBonus, beforeMark = unlock)
                return
            }
            cooldownRemainingMs = remaining
        }

        val reopenCost = plugin.config.getInt("vaults.reopen-cost-keys", 0)
        if (reopenCost > 0) {
            val chamberForTable = plugin.chamberManager.getChamberById(vaultData.chamberId)
            val tableForReopen = plugin.chamberManager.getEffectiveLootTable(
                chamberForTable, vaultType, vaultData.lootTable
            )
            if (plugin.lootManager.getTable(tableForReopen) == null) {
                plugin.logger.warning("Loot table '$tableForReopen' not found! Reopen payment will NOT be taken.")
                player.sendMessage(plugin.getMessageComponent("vault-loot-table-missing"))
                return
            }
            if (hasEnoughKeys(player, vaultType, keySlot, reopenCost)) {
                openVault(
                    player, vaultData, vaultType, location, keySlot, luckBonus,
                    keyCost = reopenCost,
                    reopenNoticeCost = reopenCost,
                    beforeMark = unlock
                )
                return
            }
            val needMsg = plugin.getMessageComponent(
                "vault-reopen-need",
                "cost" to reopenCost, "type" to plugin.vaultTypeDisplay(vaultType)
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                routeVaultFeedback(player, location, false, needMsg) { playErrorSound(player) }
                showCooldownParticles(player, location, vaultType)
            })
            return
        }

        val feedback = if (cooldownHours > 0 && cooldownRemainingMs > 0 && cooldownRemainingMs != Long.MAX_VALUE) {
            plugin.getMessageComponent(
                if (isShared) "vault-cooldown-shared" else "vault-cooldown",
                "type" to plugin.vaultTypeDisplay(vaultType),
                "player" to (sharedClaimantName ?: "?"),
                "time" to plugin.statisticsManager.formatTime(cooldownRemainingMs / 1000))
        } else {
            val chamber = plugin.chamberManager.getCachedChamberAt(location)
            val afterLoot = plugin.config.getBoolean("global.reset-after-first-loot", false)
            val resetInMs = chamber
                ?.takeIf { it.resetInterval > 0 && !it.isPaused && (!afterLoot || it.firstLootedAt != null) }
                ?.let { plugin.resetManager.getTimeUntilReset(it) }
                ?.takeIf { it > 0 }
            val key = if (isShared) "vault-locked-shared" else "vault-locked"
            plugin.getMessageComponent(
                if (resetInMs != null) "$key-timed" else key,
                "type" to plugin.vaultTypeDisplay(vaultType),
                "player" to (sharedClaimantName ?: "?"),
                "chamber" to (chamber?.label() ?: ""),
                "time" to plugin.statisticsManager.formatTime((resetInMs ?: 0) / 1000))
        }
        plugin.scheduler.runAtEntity(player, Runnable {
            routeVaultFeedback(player, location, false, feedback) { playErrorSound(player) }
            showCooldownParticles(player, location, vaultType)
        })
    }

    /**
     * Checks whether the player holds at least [cost] matching trial keys (checking [preferredSlot]
     * first, then anywhere in their inventory) without mutating their inventory. Folia-safe.
     */
    private suspend fun hasEnoughKeys(
        player: org.bukkit.entity.Player,
        vaultType: VaultType,
        preferredSlot: Int,
        cost: Int
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        plugin.scheduler.runAtEntity(player, Runnable {
            val ok = try {
                countMatchingKeys(player, vaultType, preferredSlot) >= cost
            } catch (_: Exception) {
                false
            }
            if (continuation.isActive) continuation.resume(ok)
        }, Runnable {
            if (continuation.isActive) continuation.resume(false)
        })
    }

    private fun isAcceptedKey(material: Material, vaultType: VaultType): Boolean {
        val validateType = plugin.config.getBoolean("trial-keys.validate-key-type", true)
        if (!validateType) {
            return material == Material.TRIAL_KEY || material == Material.OMINOUS_TRIAL_KEY
        }
        return when (vaultType) {
            VaultType.NORMAL -> material == Material.TRIAL_KEY
            VaultType.OMINOUS -> material == Material.OMINOUS_TRIAL_KEY
        }
    }

    private fun countMatchingKeys(player: org.bukkit.entity.Player, vaultType: VaultType, preferredSlot: Int): Int {
        val inv = player.inventory
        var total = 0
        for (i in 0 until inv.size) {
            val stack = inv.getItem(i) ?: continue
            if (isAcceptedKey(stack.type, vaultType)) total += stack.amount
        }
        return total
    }

    /**
     * Atomically verifies [player] is online and holds [cost] accepted keys, and deducts them.
     * Returns the exact stacks removed (for refund if vault marking fails), or null if the player
     * is offline or no longer holds enough keys (key-drop / disconnect exploit guard).
     */
    private suspend fun tryChargeKeys(
        player: org.bukkit.entity.Player,
        preferredSlot: Int,
        vaultType: VaultType,
        cost: Int,
    ): List<ItemStack>? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        plugin.scheduler.runAtEntity(player, Runnable {
            var taken: List<ItemStack>? = null
            try {
                if (player.isOnline && countMatchingKeys(player, vaultType, preferredSlot) >= cost) {
                    val inv = player.inventory
                    var remaining = cost
                    val removed = mutableListOf<ItemStack>()
                    fun takeFromSlot(slot: Int) {
                        if (remaining <= 0) return
                        val stack = inv.getItem(slot) ?: return
                        if (!isAcceptedKey(stack.type, vaultType)) return
                        val take = minOf(remaining, stack.amount)
                        removed += stack.clone().also { it.amount = take }
                        if (stack.amount > take) stack.amount -= take else inv.setItem(slot, null)
                        remaining -= take
                    }
                    takeFromSlot(preferredSlot)
                    for (slot in 0 until inv.size) {
                        if (slot == preferredSlot) continue
                        takeFromSlot(slot)
                        if (remaining <= 0) break
                    }
                    if (remaining == 0) taken = removed
                }
            } catch (e: Exception) {
                plugin.logger.warning("[Vault] Key charge failed for ${player.name}: ${e.message}")
            }
            if (continuation.isActive) continuation.resume(taken)
        }, Runnable {
            if (continuation.isActive) continuation.resume(null)
        })
    }

    private fun refundKeys(player: org.bukkit.entity.Player, keys: List<ItemStack>, fallbackLoc: Location) {
        if (keys.isEmpty()) return
        plugin.scheduler.runAtEntity(player, Runnable {
            if (player.isOnline) {
                val leftover = player.inventory.addItem(*keys.toTypedArray())
                leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            } else {
                ejectLootAtVault(keys, player, fallbackLoc)
            }
        }, Runnable {
            ejectLootAtVault(keys, player, fallbackLoc)
        })
    }

    /**
     * Opens a vault for a player.
     * Rolls loot purely first (without running commands/economy), charges keys atomically,
     * marks the vault as opened, and only then delivers items + side effects.
     */
    private suspend fun openVault(
        player: org.bukkit.entity.Player,
        vaultData: com.esmpfun.bettertrialchambers.models.VaultData,
        vaultType: VaultType,
        location: Location,
        keySlot: Int,
        luckBonus: Int,
        markUsed: Boolean = true,
        keyCost: Int = 1,
        reopenNoticeCost: Int = 0,
        beforeMark: (suspend () -> Unit)? = null,
    ) {
        val chamber = plugin.chamberManager.getChamberById(vaultData.chamberId)
        val resolvedLootTable = plugin.chamberManager.getEffectiveLootTable(
            chamber, vaultType, vaultData.lootTable
        )

        val preEvent = com.esmpfun.bettertrialchambers.api.events.PreVaultOpenEvent(
            player = player,
            vault = vaultData,
            chamber = chamber,
            vaultType = vaultType,
            lootTableOverride = null
        )
        plugin.server.pluginManager.callEvent(preEvent)
        if (preEvent.isCancelled) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[PreVaultOpenEvent] Open cancelled by listener for vault ID ${vaultData.id}")
            }
            return
        }
        val effectiveLootTable = preEvent.lootTableOverride ?: resolvedLootTable

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            val chamberOverride = chamber?.getLootTable(vaultType)
            plugin.logger.info("Opening vault ID ${vaultData.id}: type=${vaultData.type}, " +
                "vaultDefault='${vaultData.lootTable}', chamberOverride='$chamberOverride', " +
                "resolved='$resolvedLootTable', effective='$effectiveLootTable' " +
                "(override-set=${preEvent.lootTableOverride != null})")
        }

        val tableExists = plugin.lootManager.getTable(effectiveLootTable) != null
        if (!tableExists) {
            plugin.logger.warning("Loot table '$effectiveLootTable' not found! Key will NOT be consumed.")
            player.sendMessage(plugin.getMessageComponent("vault-loot-table-missing"))
            return
        }

        val redeemCtx = com.esmpfun.bettertrialchambers.managers.LootManager.RedeemContext(
            plugin.vaultManager.getRedeemedLootIds(player.uniqueId, vaultData.chamberId)
        )
        val rolled = plugin.lootManager.rollLoot(effectiveLootTable, player, redeemCtx, luckBonus)

        if (rolled.isEmpty) {
            plugin.logger.warning("No loot generated from table '$effectiveLootTable'! Key will NOT be consumed.")
            player.sendMessage(plugin.getMessageComponent("vault-no-loot-generated"))
            return
        }

        // Charge the required key(s) BEFORE marking the vault or delivering loot/commands/economy,
        // so disconnecting or dropping the key mid-open cannot yield free rewards.
        val chargedKeys = tryChargeKeys(player, keySlot, vaultType, keyCost)
        if (chargedKeys == null) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("Vault open aborted for ${player.name}: required key(s) no longer present or player disconnected.")
            }
            return
        }

        // Perform timed/paid unlock right before re-marking fresh.
        beforeMark?.invoke()

        // Mark player as rewarded using Paper's native Vault API (on Paper 1.21.1,
        // NoSuchMethodError degrades cleanly to DB-backed tracking).
        val rewardMarked = if (!markUsed) true else kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                var ok = false
                try {
                    val block = location.block
                    val vaultState = block.state as? Vault
                    if (vaultState == null) {
                        plugin.logger.warning("Cannot mark reward - block at $location is not a Vault!")
                        ok = false
                    } else {
                        vaultState.addRewardedPlayer(player.uniqueId)
                        vaultState.update()
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("[Vault API] addRewardedPlayer(${player.name}) - marked and persisted")
                        }
                        ok = true
                    }
                } catch (_: NoSuchMethodError) {
                    // Paper 1.21.1: Vault has no addRewardedPlayer; DB record below tracks the open.
                    ok = true
                } catch (e: Throwable) {
                    plugin.logger.severe("Error marking vault reward: ${e.message}")
                    e.printStackTrace()
                    ok = false
                } finally {
                    if (continuation.isActive) continuation.resume(ok)
                }
            })
        }

        if (!rewardMarked) {
            plugin.logger.warning("Failed to mark player as rewarded! Refunding charged key(s).")
            refundKeys(player, chargedKeys, location)
            player.sendMessage(plugin.getMessageComponent("vault-error"))
            return
        }

        if (markUsed) {
            plugin.vaultManager.recordOpen(player.uniqueId, vaultData.id)
        }
        plugin.statisticsManager.incrementVaultsOpened(player.uniqueId, vaultData.type)

        val loot = rolled.items
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            plugin.scheduler.runAtEntity(player, Runnable {
                try {
                    if (redeemCtx.newlyRedeemed.isNotEmpty()) {
                        val earned = redeemCtx.newlyRedeemed.toList()
                        plugin.launchAsync {
                            plugin.vaultManager.recordLootRedemptions(player.uniqueId, vaultData.chamberId, earned)
                        }
                    }

                    // Key is already charged and vault is already marked; deliver side effects
                    // and items (ejecting at the vault if the player disconnected in the last tick).
                    plugin.lootManager.deliverSideEffects(rolled, player)

                    if (!player.isOnline) {
                        plugin.logger.info("Player ${player.name} disconnected during vault delivery; loot left at the vault")
                        if (loot.isNotEmpty()) ejectLootAtVault(loot, player, location)
                        return@Runnable
                    }

                    if (reopenNoticeCost > 0) {
                        player.sendMessage(plugin.getMessageComponent(
                            "vault-reopened",
                            "cost" to reopenNoticeCost, "type" to plugin.vaultTypeDisplay(vaultType)
                        ))
                    }

                    if (loot.isNotEmpty()) {
                        val dropAtVault = plugin.config.getBoolean("vaults.drop-loot-at-vault", false)
                        if (dropAtVault) {
                            ejectLootAtVault(loot, player, location)
                        } else {
                            val leftover = player.inventory.addItem(*loot.toTypedArray())
                            if (leftover.isNotEmpty() && player.isOnline) {
                                leftover.values.forEach { item ->
                                    player.world.dropItemNaturally(player.location, item)
                                }
                                player.sendMessage(plugin.getMessageComponent("inventory-full"))
                            }
                        }
                    }

                    routeVaultFeedback(
                        player, location, true,
                        plugin.getMessageComponent("vault-opened", "type" to plugin.vaultTypeDisplay(vaultType))
                    ) {
                        playSuccessSound(player, location, vaultType)
                    }

                    when (vaultType) {
                        VaultType.NORMAL -> AdvancementUtil.grantVaultUnlock(player)
                        VaultType.OMINOUS -> AdvancementUtil.grantOminousVaultUnlock(player)
                    }

                    showSuccessParticles(player, location, vaultType)

                    chamber?.let { plugin.resetManager.markLooted(it.id) }
                    plugin.server.pluginManager.callEvent(
                        com.esmpfun.bettertrialchambers.api.events.VaultOpenedEvent(
                            player = player,
                            vault = vaultData,
                            chamber = chamber,
                            lootTableName = effectiveLootTable,
                            items = loot.map { it.clone() }
                        )
                    )
                } catch (e: Exception) {
                    plugin.logger.severe("Error in vault open: ${e.message}")
                    e.printStackTrace()
                    com.esmpfun.bettertrialchambers.integrations.MetricsService.reportHandled(e, "vault-open")
                } finally {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }, Runnable {
                // Entity retired on Folia after keys were charged and vault was marked: eject items at vault.
                if (loot.isNotEmpty()) ejectLootAtVault(loot, player, location)
                if (continuation.isActive) continuation.resume(Unit)
            })
        }
    }

    /**
     * Shows cooldown particles around the vault.
     */
    private fun showCooldownParticles(player: org.bukkit.entity.Player, location: org.bukkit.Location, vaultType: VaultType) {
        if (!plugin.config.getBoolean("vaults.show-cooldown-particles", true)) return

        val particleType = when (vaultType) {
            VaultType.NORMAL -> {
                val name = plugin.config.getString("vaults.particles.normal-cooldown", "SMOKE_NORMAL")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SMOKE
                }
            }
            VaultType.OMINOUS -> {
                val name = plugin.config.getString("vaults.particles.ominous-cooldown", "SOUL")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SOUL
                }
            }
        }

        player.spawnParticle(
            particleType,
            location.clone().add(0.5, 1.0, 0.5),
            20,
            0.3, 0.5, 0.3,
            0.01
        )
    }

    /**
     * Shows success particles around the vault.
     */
    private fun showSuccessParticles(player: org.bukkit.entity.Player, location: org.bukkit.Location, vaultType: VaultType) {
        if (!plugin.config.getBoolean("vaults.show-cooldown-particles", true)) return

        val particleType = when (vaultType) {
            VaultType.NORMAL -> {
                val name = plugin.config.getString("vaults.particles.normal-available", "VILLAGER_HAPPY")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.HAPPY_VILLAGER
                }
            }
            VaultType.OMINOUS -> {
                val name = plugin.config.getString("vaults.particles.ominous-available", "SOUL_FIRE_FLAME")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SOUL_FIRE_FLAME
                }
            }
        }

        player.spawnParticle(
            particleType,
            location.clone().add(0.5, 1.0, 0.5),
            30,
            0.3, 0.5, 0.3,
            0.02
        )
    }

    /**
     * Clears the native Vault `rewarded_players` flag for [player] so the vault
     * can be opened fresh (used by the timed-cooldown and key-to-reopen paths).
     * Runs on the block's region thread (Folia-safe) and suspends until done.
     */
    private suspend fun clearNativeRewardedFlag(location: Location, player: org.bukkit.entity.Player) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                try {
                    (location.block.state as? Vault)?.let { vs ->
                        vs.removeRewardedPlayer(player.uniqueId)
                        vs.update()
                    }
                } catch (_: NoSuchMethodError) {
                    // Paper 1.21.1: Vault has no removeRewardedPlayer API.
                } catch (e: Throwable) {
                    plugin.logger.warning("[Vault] Failed to clear rewarded flag: ${e.message}")
                } finally {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        }
    }

    /**
     * Routes a single vault-interaction outcome to the configured feedback
     * channel (v1.5.8). In TEXT mode (default) it sends [message] and runs the
     * supplied [vanillaSound] block, byte-identical to the pre-v1.5.8 path. In
     * HOLOGRAM mode it shows a per-player ✓/✗ display above the vault with the
     * pillager sound instead, suppressing the chat line and the vanilla sound.
     * Particles/advancements at each call site are unaffected.
     */
    private inline fun routeVaultFeedback(
        player: org.bukkit.entity.Player,
        vaultLocation: org.bukkit.Location,
        success: Boolean,
        message: net.kyori.adventure.text.Component,
        vanillaSound: () -> Unit
    ) {
        if (com.esmpfun.bettertrialchambers.utils.VaultFeedback.isHologramMode(plugin)) {
            com.esmpfun.bettertrialchambers.utils.VaultFeedback
                .showHologram(plugin, player, vaultLocation, success)
        } else {
            player.sendMessage(message)
            vanillaSound()
        }
    }

    /**
     * Plays success sound to the player.
     */
    private fun playSuccessSound(player: org.bukkit.entity.Player, location: org.bukkit.Location, vaultType: VaultType) {
        if (!plugin.config.getBoolean("vaults.play-sound-on-open", true)) return

        val soundKey = if (vaultType == VaultType.OMINOUS) "vaults.sounds.ominous-open" else "vaults.sounds.normal-open"
        val soundName = plugin.config.getString(soundKey, "BLOCK_VAULT_OPEN_SHUTTER")!!
        val sound = com.esmpfun.bettertrialchambers.utils.SoundUtil.resolve(soundName) ?: return
        player.playSound(location, sound, 1.0f, 1.0f)
    }

    /**
     * Plays error sound to the player.
     */
    private fun playErrorSound(player: org.bukkit.entity.Player) {
        if (!plugin.config.getBoolean("vaults.play-sound-on-open", true)) return

        val soundName = plugin.config.getString("vaults.sounds.cooldown", "BLOCK_NOTE_BLOCK_BASS")!!
        val sound = com.esmpfun.bettertrialchambers.utils.SoundUtil.resolve(soundName) ?: return
        player.playSound(player.location, sound, 1.0f, 0.5f)
    }

    /**
     * v1.7.2: helper for post-delivery key consumption. Prefers the hotbar slot
     * held at click time; if the player scrolled away, finds any stack of the
     * matching key material; consumes NOTHING (with a log line) when no key is
     * found, loot was already delivered, so we fail in the player's favor
     * rather than eat an unrelated item.
     */
    private fun consumeOneKey(player: org.bukkit.entity.Player, preferredSlot: Int, vaultType: VaultType) {
        val keyMaterial = when (vaultType) {
            VaultType.NORMAL -> Material.TRIAL_KEY
            VaultType.OMINOUS -> Material.OMINOUS_TRIAL_KEY
        }
        val inv = player.inventory
        val slot = if (inv.getItem(preferredSlot)?.type == keyMaterial) preferredSlot
                   else inv.first(keyMaterial)
        if (slot < 0) {
            plugin.logger.warning("Vault opened by ${player.name} but no ${keyMaterial.name} found to consume (moved/dropped mid-open?), key not charged.")
            return
        }
        val stack = inv.getItem(slot) ?: return
        if (stack.amount > 1) {
            stack.amount -= 1
        } else {
            inv.setItem(slot, null)
        }
    }

    /**
     * Ejects loot as dropped items at the vault block (vanilla-style), tagging
     * each with the owner UUID and a drop timestamp when owner-only pickup is
     * enabled.
     *
     * v1.7.2: schedules the drops on the VAULT's region thread, the caller is
     * on the player's entity thread, and if the player moved/teleported during
     * the async open, dropping at the vault from that thread is a cross-region
     * write on Folia. Fire-and-forget is fine: nothing downstream depends on
     * the item entities.
     */
    private fun ejectLootAtVault(
        loot: List<ItemStack>,
        player: org.bukkit.entity.Player,
        vaultLocation: Location
    ) {
        val world = vaultLocation.world ?: return
        val spawn = vaultLocation.clone().add(0.5, 1.15, 0.5)
        val ownerOnly = plugin.config.getBoolean("vaults.drop-loot-owner-only", true)
        val droppedAt = System.currentTimeMillis()
        val ownerUuid = player.uniqueId

        plugin.scheduler.runAtLocation(vaultLocation, Runnable {
            loot.forEach { stack ->
                world.dropItem(spawn, stack) { entity ->
                    entity.velocity = Vector(
                        (Math.random() - 0.5) * 0.2,
                        0.3,
                        (Math.random() - 0.5) * 0.2
                    )
                    entity.pickupDelay = 10 // ~0.5s so players see the pop
                    if (ownerOnly) {
                        // Do NOT set vanilla entity.owner: vanilla permanently blocks other
                        // players (including btc.bypass.droplock) before EntityPickupItemEvent
                        // ever fires, defeating both the grace window and the bypass permission.
                        // VaultDropOwnerListener enforces ownership + grace + bypass via PDC.
                        val pdc = entity.persistentDataContainer
                        pdc.set(
                            VaultDropOwnerListener.OWNER_KEY,
                            PersistentDataType.STRING,
                            ownerUuid.toString()
                        )
                        pdc.set(
                            VaultDropOwnerListener.DROPPED_AT_KEY,
                            PersistentDataType.LONG,
                            droppedAt
                        )
                    }
                }
            }
        })
    }

    /**
     * Cleans up resources when the plugin is disabled.
     * Cancels the coroutine scope and clears the opening vaults map to prevent memory leaks.
     */
    fun shutdown() {
        listenerScope.cancel()
        openingVaults.clear()
    }
}

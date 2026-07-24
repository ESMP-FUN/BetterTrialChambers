package com.esmpfun.bettertrialchambers.utils

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Utility class for asynchronously restoring blocks from snapshots.
 * Uses batching and delays to prevent server lag during large restorations.
 *
 * Folia compatible: Uses location-based scheduling to ensure blocks are
 * modified on the correct region thread.
 *
 * WorldEdit integration: When a player is provided and WorldEdit is available,
 * block changes are recorded in WorldEdit's EditSession so they can be undone
 * with //undo. This doesn't replace WorldEdit's undo queue - it adds to it.
 */
class BlockRestorer(private val plugin: BetterTrialChambers) {

    /**
     * Incremental restore session: feed it batches of blocks as they stream off
     * disk and it places them on the correct region threads, so a restore never
     * needs the whole snapshot in memory at once. Call [submitBatch] repeatedly,
     * then [finish] — which suspends until every scheduled region-thread batch
     * has actually run (callers rely on this: ResetManager clears vault
     * rewarded_players and resets spawner state immediately after).
     *
     * WorldEdit //undo integration is set up lazily from the first batch when
     * an initiating player is present, matching the old whole-map behavior.
     */
    inner class StreamingSession(
        private val expectedTotal: Int,
        private val onProgress: ((Int, Int) -> Unit)? = null,
        private val initiatingPlayer: Player? = null
    ) {
        private val processed = java.util.concurrent.atomic.AtomicInteger(0)
        private val pendingBatches = java.util.concurrent.atomic.AtomicInteger(0)
        private val completionSignal = CompletableDeferred<Unit>()

        @Volatile
        private var allSubmitted = false
        private var weSession: WorldEditSessionData? = null
        private var weAttempted = false

        suspend fun submitBatch(batch: List<Pair<Location, BlockSnapshot>>) {
            if (batch.isEmpty()) return

            if (!weAttempted) {
                weAttempted = true
                weSession = if (initiatingPlayer != null && WorldEditSupport.isAvailable()) {
                    try {
                        createWorldEditSession(initiatingPlayer, batch.first().first)
                    } catch (e: Throwable) {
                        // Catch Throwable, not just Exception: a WorldEdit jar compiled for a
                        // newer Java than the server runtime throws UnsupportedClassVersionError
                        // (a LinkageError, not an Exception) the moment a WE class is touched.
                        // WorldEdit is only a soft dependency here (//undo integration), so a
                        // broken/incompatible install must degrade gracefully — never abort the
                        // reset. The undo hint is simply skipped.
                        plugin.logger.warning(
                            "WorldEdit //undo integration unavailable (${e.javaClass.simpleName}: ${e.message}); " +
                                "continuing reset without it. If this is an UnsupportedClassVersionError, your " +
                                "WorldEdit build targets a newer Java than this server's runtime."
                        )
                        null
                    }
                } else null

                if (weSession != null) {
                    plugin.logger.info("WorldEdit integration enabled - changes can be undone with //undo")
                }
            }

            // Group by chunk so each region task only touches blocks its thread owns
            // (Folia correctness); groupBy copies entries, so callers may reuse lists.
            batch.groupBy { chunkKey(it.first) }.values.forEach { group ->
                val representative = group.first().first
                pendingBatches.incrementAndGet()
                plugin.scheduler.runAtLocation(representative, Runnable {
                    try {
                        val world = representative.world
                        val chunkX = representative.blockX shr 4
                        val chunkZ = representative.blockZ shr 4
                        // Sync-load on the owning thread is the correct pattern here
                        if (world != null && !world.isChunkLoaded(chunkX, chunkZ)) {
                            world.getChunkAt(chunkX, chunkZ)
                        }
                        val session = weSession
                        group.forEach { (location, blockSnapshot) ->
                            try {
                                // Use WorldEdit if available, otherwise direct Bukkit API
                                if (session != null) {
                                    restoreBlockWithWorldEdit(location, blockSnapshot, session)
                                } else {
                                    restoreBlock(location, blockSnapshot)
                                }
                                processed.incrementAndGet()
                            } catch (e: Exception) {
                                plugin.logger.warning(
                                    "Failed to restore block at ${location.blockX},${location.blockY},${location.blockZ}: ${e.message}"
                                )
                            }
                        }
                        onProgress?.invoke(processed.get(), expectedTotal)
                    } finally {
                        batchFinished()
                    }
                })
            }

            // Small delay between batches to prevent lag (1 tick = 50ms)
            delay(50)
        }

        private fun batchFinished() {
            if (pendingBatches.decrementAndGet() == 0 && allSubmitted) {
                completionSignal.complete(Unit)
            }
        }

        /** @return the number of blocks actually restored. */
        suspend fun finish(): Int {
            allSubmitted = true
            if (pendingBatches.get() == 0) {
                completionSignal.complete(Unit)
            }
            completionSignal.await()

            val session = weSession
            if (session != null && initiatingPlayer != null) {
                try {
                    finalizeWorldEditSession(session, initiatingPlayer)
                    plugin.logger.info("WorldEdit session finalized - use //undo to revert changes")
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to finalize WorldEdit session: ${e.message}")
                }
            }
            return processed.get()
        }

        private fun chunkKey(location: Location): Long =
            ((location.blockX shr 4).toLong() shl 32) xor ((location.blockZ shr 4).toLong() and 0xFFFFFFFFL)
    }

    /**
     * Restores blocks from an in-memory snapshot map asynchronously.
     * Delegates to a [StreamingSession] fed in blocks-per-tick batches.
     *
     * @param snapshot Map of locations to block snapshots
     * @param onProgress Optional callback for progress updates (processed, total)
     * @param onComplete Optional callback when restoration is complete
     * @param initiatingPlayer Optional player who initiated the restoration (for WorldEdit undo support)
     */
    suspend fun restoreBlocks(
        snapshot: Map<Location, BlockSnapshot>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        initiatingPlayer: Player? = null
    ) {
        val blocksPerTick = plugin.config.getInt("global.blocks-per-tick", 500)
        val totalBlocks = snapshot.size

        plugin.logger.info("Starting block restoration: $totalBlocks blocks")

        val session = StreamingSession(totalBlocks, onProgress, initiatingPlayer)
        val batch = ArrayList<Pair<Location, BlockSnapshot>>(blocksPerTick)
        for (entry in snapshot) {
            batch.add(entry.key to entry.value)
            if (batch.size >= blocksPerTick) {
                session.submitBatch(batch)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            session.submitBatch(batch)
        }
        val restored = session.finish()

        plugin.logger.info("Block restoration complete: $restored/$totalBlocks blocks restored")

        // Call completion callback on main/global thread
        plugin.scheduler.runTask(Runnable {
            onComplete?.invoke()
        })
    }

    /**
     * Clears blocks a player ADDED into cells that were air at capture time.
     *
     * Snapshots skip air to save space (see [com.esmpfun.bettertrialchambers.managers.SnapshotManager]),
     * so [restoreBlocks] alone never reverts blocks placed into formerly-empty
     * cells — lava, cobble, anything. This pass walks the chamber volume and
     * sets every cell that is (a) not present in the snapshot and (b) currently
     * non-air back to AIR, so player additions don't survive a reset.
     *
     * Region-thread + chunk-batched for Folia, mirroring [restoreBlocks]. Block
     * *reads* must happen on the owning region thread, so each chunk's slice is
     * scanned inside its own region task rather than pre-filtered off-thread.
     *
     * Run this BEFORE [restoreBlocks]; the two touch disjoint cells (foreign
     * cells vs. captured cells).
     *
     * @param occupiedSorted every snapshot-covered position packed via [pack],
     *        sorted ascending (binary-searched; far cheaper than a boxed set
     *        for multi-million-block chambers).
     */
    suspend fun clearAddedBlocks(
        world: World,
        minX: Int, minY: Int, minZ: Int,
        maxX: Int, maxY: Int, maxZ: Int,
        occupiedSorted: LongArray,
    ) {

        val cleared = java.util.concurrent.atomic.AtomicInteger(0)
        val pending = java.util.concurrent.atomic.AtomicInteger(0)
        val signal = CompletableDeferred<Unit>()
        fun batchFinished() {
            if (pending.decrementAndGet() == 0) signal.complete(Unit)
        }

        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val x0 = maxOf(minX, cx shl 4)
                val x1 = minOf(maxX, (cx shl 4) + 15)
                val z0 = maxOf(minZ, cz shl 4)
                val z1 = minOf(maxZ, (cz shl 4) + 15)
                val representative = Location(world, x0.toDouble(), minY.toDouble(), z0.toDouble())

                pending.incrementAndGet()
                plugin.scheduler.runAtLocation(representative, Runnable {
                    try {
                        if (!world.isChunkLoaded(cx, cz)) world.getChunkAt(cx, cz)
                        for (x in x0..x1) {
                            for (z in z0..z1) {
                                for (y in minY..maxY) {
                                    if (java.util.Arrays.binarySearch(occupiedSorted, pack(x, y, z)) >= 0) continue
                                    val block = world.getBlockAt(x, y, z)
                                    if (block.type != Material.AIR) {
                                        block.setType(Material.AIR, false)
                                        cleared.incrementAndGet()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to clear added blocks in chunk $cx,$cz: ${e.message}")
                    } finally {
                        batchFinished()
                    }
                })

                // One chunk-column slice per tick to spread the load (matches restoreBlocks).
                delay(50)
            }
        }

        if (pending.get() == 0) signal.complete(Unit)
        signal.await()

        if (cleared.get() > 0) {
            plugin.logger.info("Cleared ${cleared.get()} player-added block(s) not in the snapshot")
        }
    }

    companion object {
        /** Pack block coords into a single long (vanilla BlockPos layout: 26/12/26 bits x/y/z). */
        fun pack(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
    }

    /**
     * Holder for WorldEdit session data during restoration.
     */
    private data class WorldEditSessionData(
        val editSession: Any, // com.sk89q.worldedit.EditSession
        val localSession: Any  // com.sk89q.worldedit.LocalSession
    )

    /**
     * Restores a single block from a snapshot.
     * MUST be called from the region thread owning this location (Folia)
     * or the main thread (Paper).
     *
     * @param location The block location
     * @param snapshot The block snapshot data
     */
    private fun restoreBlock(location: Location, snapshot: BlockSnapshot) {
        val block = location.block

        // Parse and set block data
        try {
            // CRITICAL FIX: Reset trial spawner state to waiting_for_players
            // If the snapshot was taken while spawners were in cooldown state,
            // they would be restored in cooldown and not drop keys for 30 minutes!
            val blockDataString = resetTrialSpawnerState(snapshot.blockData)
            val blockData = Bukkit.createBlockData(blockDataString)
            block.setBlockData(blockData, false) // Don't apply physics immediately
        } catch (_: Exception) {
            plugin.logger.warning("Invalid block data at ${location.blockX},${location.blockY},${location.blockZ}: ${snapshot.blockData}")
            return
        }

        // Restore tile entity data if present
        snapshot.tileEntity?.let { tileEntityData ->
            val state = block.state
            if (NBTUtil.restoreTileEntity(state, tileEntityData)) {
                // Successfully restored tile entity
            } else {
                plugin.logger.warning("Failed to restore tile entity at ${location.blockX},${location.blockY},${location.blockZ}")
            }
        }
    }

    /**
     * Restores blocks synchronously (use with caution - may cause lag).
     * On Folia, this should only be called from the correct region thread.
     *
     * @param snapshot Map of locations to block snapshots
     * @return Number of blocks successfully restored
     */
    fun restoreBlocksSync(snapshot: Map<Location, BlockSnapshot>): Int {
        var restored = 0

        snapshot.forEach { (location, blockSnapshot) ->
            try {
                restoreBlock(location, blockSnapshot)
                restored++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to restore block: ${e.message}")
            }
        }

        return restored
    }

    /**
     * Estimates restoration time based on block count.
     *
     * @param blockCount Number of blocks to restore
     * @return Estimated time in seconds
     */
    fun estimateRestorationTime(blockCount: Int): Long {
        val blocksPerTick = plugin.config.getInt("global.blocks-per-tick", 500)
        val batches = (blockCount + blocksPerTick - 1) / blocksPerTick
        // Each batch takes ~50ms, plus some overhead
        return ((batches * 50 + 500) / 1000).toLong() // Convert to seconds
    }

    /**
     * Resets trial spawner state in block data string to waiting_for_players.
     *
     * Trial spawners have 6 states: inactive, waiting_for_players, active,
     * waiting_for_reward_ejection, ejecting_reward, cooldown.
     *
     * If a snapshot was taken while spawners were in cooldown (or other non-fresh state),
     * restoring that snapshot would create spawners that won't drop keys!
     *
     * This function modifies the block data string to ensure spawners are restored
     * in the waiting_for_players state, ready to be activated.
     *
     * @param blockData The original block data string
     * @return The modified block data string with reset spawner state
     */
    private fun resetTrialSpawnerState(blockData: String): String {
        // Only process trial spawners
        if (!blockData.contains("trial_spawner")) {
            return blockData
        }

        // All possible trial spawner states that need to be reset
        val statesToReset = listOf(
            "trial_spawner_state=inactive",
            "trial_spawner_state=active",
            "trial_spawner_state=waiting_for_reward_ejection",
            "trial_spawner_state=ejecting_reward",
            "trial_spawner_state=cooldown"
        )

        var result = blockData
        for (state in statesToReset) {
            if (result.contains(state)) {
                result = result.replace(state, "trial_spawner_state=waiting_for_players")
                break // Only one state can be present
            }
        }

        return result
    }

    // ==================== WorldEdit Integration ====================

    /**
     * Creates a WorldEdit EditSession for recording block changes.
     * Uses reflection to work with WorldEdit's API without compile-time dependency.
     */
    private fun createWorldEditSession(player: Player, firstLocation: Location): WorldEditSessionData? {
        val world = firstLocation.world ?: return null

        try {
            // Get WorldEdit and FAWE classes via reflection
            val worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit")
            val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")

            // Get WorldEdit instance
            val getInstanceMethod = worldEditClass.getMethod("getInstance")
            val worldEditInstance = getInstanceMethod.invoke(null)

            // Get session manager
            val getSessionManagerMethod = worldEditClass.getMethod("getSessionManager")
            val sessionManager = getSessionManagerMethod.invoke(worldEditInstance)

            // Adapt player to WorldEdit actor
            val adaptPlayerMethod = bukkitAdapterClass.getMethod("adapt", Player::class.java)
            val actor = adaptPlayerMethod.invoke(null, player)

            // Get player's local session
            val sessionManagerClass = Class.forName("com.sk89q.worldedit.session.SessionManager")
            val getMethod = sessionManagerClass.getMethod("get", Class.forName("com.sk89q.worldedit.extension.platform.SessionOwner"))
            val localSession = getMethod.invoke(sessionManager, actor)

            // Adapt world
            val adaptWorldMethod = bukkitAdapterClass.getMethod("adapt", org.bukkit.World::class.java)
            val weWorld = adaptWorldMethod.invoke(null, world)

            // Create EditSession using builder pattern
            val newEditSessionBuilderMethod = worldEditClass.getMethod("newEditSessionBuilder")
            val builder = newEditSessionBuilderMethod.invoke(worldEditInstance)

            val builderClass = builder.javaClass
            val worldMethod = builderClass.getMethod("world", Class.forName("com.sk89q.worldedit.world.World"))
            worldMethod.invoke(builder, weWorld)

            val maxBlocksMethod = builderClass.getMethod("maxBlocks", Int::class.java)
            maxBlocksMethod.invoke(builder, -1) // No limit

            val buildMethod = builderClass.getMethod("build")
            val editSession = buildMethod.invoke(builder)

            return WorldEditSessionData(editSession, localSession)
        } catch (e: Exception) {
            plugin.logger.fine("WorldEdit integration not available: ${e.message}")
            return null
        }
    }

    /**
     * Restores a block using WorldEdit's EditSession for undo support.
     */
    private fun restoreBlockWithWorldEdit(location: Location, snapshot: BlockSnapshot, weSession: WorldEditSessionData) {
        try {
            val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")

            // Parse block data
            val blockDataString = resetTrialSpawnerState(snapshot.blockData)
            val bukkitBlockData = Bukkit.createBlockData(blockDataString)

            // Adapt to WorldEdit BlockState
            val adaptBlockDataMethod = bukkitAdapterClass.getMethod("adapt", org.bukkit.block.data.BlockData::class.java)
            val weBlockState = adaptBlockDataMethod.invoke(null, bukkitBlockData)

            // Create BlockVector3
            val blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3")
            val atMethod = blockVector3Class.getMethod("at", Int::class.java, Int::class.java, Int::class.java)
            val position = atMethod.invoke(null, location.blockX, location.blockY, location.blockZ)

            // Set block through EditSession
            val editSessionClass = weSession.editSession.javaClass
            val setBlockMethod = editSessionClass.getMethod("setBlock",
                blockVector3Class,
                Class.forName("com.sk89q.worldedit.world.block.BlockStateHolder"))
            setBlockMethod.invoke(weSession.editSession, position, weBlockState)

            // Handle tile entity data separately with Bukkit API (WorldEdit doesn't support all NBT)
            snapshot.tileEntity?.let { tileEntityData ->
                val block = location.block
                val state = block.state
                NBTUtil.restoreTileEntity(state, tileEntityData)
            }
        } catch (e: Exception) {
            // Fall back to direct Bukkit API
            restoreBlock(location, snapshot)
        }
    }

    /**
     * Finalizes the WorldEdit session by flushing changes and adding to undo history.
     */
    private fun finalizeWorldEditSession(weSession: WorldEditSessionData, player: Player) {
        try {
            // Flush the EditSession to apply all changes
            val editSessionClass = weSession.editSession.javaClass

            // Try to call flushSession() or close()
            try {
                val flushMethod = editSessionClass.getMethod("flushSession")
                flushMethod.invoke(weSession.editSession)
            } catch (_: NoSuchMethodException) {
                // Try close() for AutoCloseable
                try {
                    val closeMethod = editSessionClass.getMethod("close")
                    closeMethod.invoke(weSession.editSession)
                } catch (_: Exception) {
                    // Ignore if neither method exists
                }
            }

            // Remember this session in player's undo history
            val localSessionClass = weSession.localSession.javaClass
            val rememberMethod = localSessionClass.getMethod("remember", editSessionClass.interfaces.first { it.simpleName == "EditSession" } ?: editSessionClass)
            rememberMethod.invoke(weSession.localSession, weSession.editSession)

            plugin.logger.info("WorldEdit undo history updated for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to finalize WorldEdit session: ${e.message}")
        }
    }
}

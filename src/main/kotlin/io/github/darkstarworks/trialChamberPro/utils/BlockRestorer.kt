package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Chunk
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
class BlockRestorer(private val plugin: TrialChamberPro) {

    /**
     * Restores blocks from a snapshot asynchronously.
     * Groups blocks by chunk and processes them in batches to prevent lag.
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

        // Try to set up WorldEdit integration if player provided and WorldEdit available
        val weSession = if (initiatingPlayer != null && WorldEditSupport.isAvailable()) {
            try {
                createWorldEditSession(initiatingPlayer, snapshot)
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

        // Group blocks by chunk for efficient processing
        val blocksByChunk = snapshot.entries.groupBy { it.key.chunk }

        // Use atomic counter for thread-safe progress tracking across multiple region threads (Folia)
        val processedBlocks = java.util.concurrent.atomic.AtomicInteger(0)

        // Track pending region-thread batches so we can await actual completion.
        // runAtLocation is fire-and-forget; without this, restoreBlocks returned
        // before vault tile entities were actually rewritten, which meant the
        // post-restore vault rewarded_players clear ran against stale blocks.
        val pendingBatches = java.util.concurrent.atomic.AtomicInteger(0)
        val completionSignal = CompletableDeferred<Unit>()
        fun batchFinished() {
            if (pendingBatches.decrementAndGet() == 0) {
                completionSignal.complete(Unit)
            }
        }

        // Process each chunk
        blocksByChunk.forEach { (chunk, blockEntries) ->
            // Ensure chunk is loaded
            ensureChunkLoaded(chunk)

            // Process blocks in batches
            blockEntries.chunked(blocksPerTick).forEach { batch ->
                // Get a representative location for this batch (for Folia region scheduling)
                val representativeLocation = batch.firstOrNull()?.key

                if (representativeLocation != null) {
                    pendingBatches.incrementAndGet()
                    // Schedule on the region thread that owns this location
                    plugin.scheduler.runAtLocation(representativeLocation, Runnable {
                        try {
                            batch.forEach { (location, blockSnapshot) ->
                                try {
                                    // Use WorldEdit if available, otherwise direct Bukkit API
                                    if (weSession != null) {
                                        restoreBlockWithWorldEdit(location, blockSnapshot, weSession)
                                    } else {
                                        restoreBlock(location, blockSnapshot)
                                    }
                                    processedBlocks.incrementAndGet()
                                } catch (e: Exception) {
                                    plugin.logger.warning(
                                        "Failed to restore block at ${location.blockX},${location.blockY},${location.blockZ}: ${e.message}"
                                    )
                                }
                            }

                            // Call progress callback
                            onProgress?.invoke(processedBlocks.get(), totalBlocks)
                        } finally {
                            batchFinished()
                        }
                    })
                }

                // Small delay between batches to prevent lag (1 tick = 50ms)
                delay(50)
            }
        }

        // If nothing was scheduled (empty snapshot), complete immediately.
        if (pendingBatches.get() == 0) {
            completionSignal.complete(Unit)
        }

        // Wait for every scheduled batch to actually finish on its region thread
        // before returning. Callers rely on this (e.g. ResetManager clears vault
        // rewarded_players and resets spawner state immediately after).
        completionSignal.await()

        // Finalize WorldEdit session if used
        if (weSession != null) {
            try {
                finalizeWorldEditSession(weSession, initiatingPlayer!!)
                plugin.logger.info("WorldEdit session finalized - use //undo to revert changes")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to finalize WorldEdit session: ${e.message}")
            }
        }

        plugin.logger.info("Block restoration complete: ${processedBlocks.get()}/$totalBlocks blocks restored")

        // Call completion callback on main/global thread
        plugin.scheduler.runTask(Runnable {
            onComplete?.invoke()
        })
    }

    /**
     * Clears blocks a player ADDED into cells that were air at capture time.
     *
     * Snapshots skip air to save space (see [io.github.darkstarworks.trialChamberPro.managers.SnapshotManager]),
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
     */
    suspend fun clearAddedBlocks(
        world: World,
        minX: Int, minY: Int, minZ: Int,
        maxX: Int, maxY: Int, maxZ: Int,
        snapshot: Map<Location, BlockSnapshot>,
    ) {
        val occupied = HashSet<Long>(snapshot.size * 2)
        snapshot.keys.forEach { occupied.add(pack(it.blockX, it.blockY, it.blockZ)) }

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
                                    if (occupied.contains(pack(x, y, z))) continue
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

    /** Pack block coords into a single long (vanilla BlockPos layout: 26/12/26 bits x/y/z). */
    private fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

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
     * Ensures a chunk is loaded before restoring blocks.
     * On Folia, chunk loading is handled differently - we schedule to the chunk's region.
     *
     * @param chunk The chunk to load
     */
    private suspend fun ensureChunkLoaded(chunk: Chunk) {
        if (!chunk.isLoaded) {
            // Get a location in this chunk for region scheduling
            val chunkLocation = Location(chunk.world, chunk.x * 16.0, 64.0, chunk.z * 16.0)

            // Load chunk on the appropriate thread
            plugin.scheduler.runAtLocation(chunkLocation, Runnable {
                chunk.load()
            })
            // Wait for chunk to load
            delay(50)
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
    private fun createWorldEditSession(player: Player, snapshot: Map<Location, BlockSnapshot>): WorldEditSessionData? {
        if (snapshot.isEmpty()) return null

        val firstLocation = snapshot.keys.first()
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

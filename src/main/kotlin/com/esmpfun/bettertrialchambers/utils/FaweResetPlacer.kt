package com.esmpfun.bettertrialchambers.utils

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optional fast snapshot placement via FastAsyncWorldEdit. FAWE writes blocks
 * through one async EditSession instead of TCP's per-tick batched [BlockRestorer],
 * which smooths out the lag of large resets.
 *
 * **Paper-only.** FAWE has no current Folia support, and plain (non-async)
 * WorldEdit can't run off the main thread, so this only engages when FAWE
 * specifically is installed and the server isn't Folia — otherwise callers fall
 * back to [BlockRestorer].
 *
 * Block *data* goes through the EditSession; tile-entity NBT (spawner/vault/pot)
 * is applied afterwards via Bukkit on the owning region thread, exactly as
 * [BlockRestorer] does — including resetting trial-spawner state so reset
 * spawners drop keys again.
 */
class FaweResetPlacer(private val plugin: BetterTrialChambers) {

    suspend fun place(snapshot: Map<Location, BlockSnapshot>) {
        val session = Session()
        try {
            session.placeBatch(snapshot.map { it.key to it.value })
        } catch (e: Exception) {
            session.discard()
            throw e
        }
        session.finish()
    }

    /**
     * Streaming variant: feed batches as they come off disk so the whole
     * snapshot never has to sit in memory. The EditSession is created lazily
     * from the first batch's world; call [finish] to flush blocks and apply
     * tile-entity NBT, or [discard] to abandon the session after a failure.
     */
    inner class Session {
        private var editSession: com.sk89q.worldedit.EditSession? = null
        private val tiles = ArrayList<Pair<Location, Map<String, Any>>>()

        suspend fun placeBatch(batch: List<Pair<Location, BlockSnapshot>>) {
            if (batch.isEmpty()) return
            withContext(Dispatchers.IO) {
                val session = editSession ?: run {
                    val world = batch.first().first.world
                        ?: throw IllegalStateException("Snapshot location has no world")
                    WorldEdit.getInstance().newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()
                        .also { editSession = it }
                }
                batch.forEach { (loc, snap) ->
                    try {
                        val data = Bukkit.createBlockData(resetTrialSpawnerState(snap.blockData))
                        session.setBlock(
                            BlockVector3.at(loc.blockX, loc.blockY, loc.blockZ),
                            BukkitAdapter.adapt(data),
                        )
                    } catch (_: Exception) {
                        // Skip an unparseable block string rather than aborting the whole edit.
                    }
                    snap.tileEntity?.let { tiles.add(loc to it) }
                }
            }
        }

        suspend fun finish() {
            withContext(Dispatchers.IO) {
                editSession?.close() // close() flushes the queue
            }

            // Apply tile-entity NBT after the blocks exist (region-thread, Folia-safe in spirit
            // though this path is Paper-only). Await so the caller's later steps see them.
            if (tiles.isEmpty()) return
            val pending = AtomicInteger(tiles.size)
            val done = CompletableDeferred<Unit>()
            tiles.forEach { (loc, tile) ->
                plugin.scheduler.runAtLocation(loc, Runnable {
                    try {
                        NBTUtil.restoreTileEntity(loc.block.state, tile)
                    } catch (e: Exception) {
                        plugin.logger.warning("FAWE reset: failed tile entity at ${loc.blockX},${loc.blockY},${loc.blockZ}: ${e.message}")
                    } finally {
                        if (pending.decrementAndGet() == 0) done.complete(Unit)
                    }
                })
            }
            done.await()
        }

        /** Best-effort cleanup after a failure; never throws. */
        fun discard() {
            runCatching { editSession?.close() }
        }
    }

    /** Mirrors BlockRestorer: force any non-fresh trial-spawner state back to waiting_for_players. */
    private fun resetTrialSpawnerState(blockData: String): String {
        if (!blockData.contains("trial_spawner")) return blockData
        val states = listOf(
            "trial_spawner_state=inactive",
            "trial_spawner_state=active",
            "trial_spawner_state=waiting_for_reward_ejection",
            "trial_spawner_state=ejecting_reward",
            "trial_spawner_state=cooldown",
        )
        for (s in states) {
            if (blockData.contains(s)) return blockData.replace(s, "trial_spawner_state=waiting_for_players")
        }
        return blockData
    }

    companion object {
        /** True when the FAWE fast path can be used (FAWE installed and not on Folia). */
        fun isAvailable(plugin: BetterTrialChambers): Boolean =
            !plugin.scheduler.isFolia && Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
    }
}

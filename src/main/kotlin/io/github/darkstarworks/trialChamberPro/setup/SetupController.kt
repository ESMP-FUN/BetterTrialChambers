package io.github.darkstarworks.trialChamberPro.setup

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared, render-agnostic engine behind `/tcp setup` — both the Dialog and chat tours call
 * this. Owns per-player progress and applies setting changes.
 *
 * Applying a change reuses the exact path the admin GUI already ships
 * ([io.github.darkstarworks.trialChamberPro.gui.GlobalSettingsView]): `config.set` +
 * `saveConfig`. The curated settings are all read live from config on each use (verified:
 * e.g. `ChamberDiscoveryListener` gates on `discovery.enabled` per event), so changes take
 * effect immediately — no manager re-init required.
 */
class SetupController(
    private val plugin: TrialChamberPro,
    val state: SetupState,
) {
    // Per-player position in the tour (in memory; persisted only on Pause).
    private val progress = ConcurrentHashMap<UUID, Int>()

    val stepCount: Int get() = SetupCatalog.count
    fun stepAt(index: Int): SetupStep? = SetupCatalog.stepAt(index)
    fun currentIndex(uuid: UUID): Int = progress[uuid] ?: 0

    /** Set the in-memory position (used by the Dialog path before rendering a step). */
    fun jumpTo(uuid: UUID, index: Int) { progress[uuid] = index.coerceIn(0, SetupCatalog.count - 1) }

    /** Begin (or restart) the tour at step 0, and mark setup as touched (ends reminders). */
    fun start(uuid: UUID) {
        progress[uuid] = 0
        markTouched()
    }

    /** Resume from a persisted pause point; returns the index to render (0 if none). */
    fun resume(uuid: UUID): Int {
        val idx = state.pausedIndex(uuid)?.coerceIn(0, SetupCatalog.count - 1) ?: 0
        progress[uuid] = idx
        state.clearPaused(uuid)
        markTouched()
        return idx
    }

    fun hasPaused(uuid: UUID): Boolean = state.pausedIndex(uuid) != null

    /** Advance past the current step. Returns the new index, or null when the tour is done. */
    fun advance(uuid: UUID): Int? {
        val next = currentIndex(uuid) + 1
        if (next >= SetupCatalog.count) {
            progress.remove(uuid)
            markComplete()
            return null
        }
        progress[uuid] = next
        return next
    }

    /** Go back one step. Returns the new index (clamped at 0). */
    fun back(uuid: UUID): Int {
        val prev = (currentIndex(uuid) - 1).coerceAtLeast(0)
        progress[uuid] = prev
        return prev
    }

    fun pause(uuid: UUID) {
        state.setPaused(uuid, currentIndex(uuid))
        progress.remove(uuid)
    }

    fun stop(uuid: UUID) {
        progress.remove(uuid)
        state.clearPaused(uuid)
        // Bailed without finishing, and the initial weekly-reminder window is already spent →
        // arm one gentle "you started but didn't finish" nudge a week from now.
        if (!state.completed &&
            state.reminderCount >= SetupReminderService.MAX_REMINDERS &&
            state.followUpEpoch == 0L
        ) {
            state.followUpEpoch = System.currentTimeMillis() + SetupReminderService.WEEK_MS
        }
    }

    /** Marks the tour as finished (called when the last step is passed). */
    fun markComplete() { state.completed = true }

    // ── applying changes (the GlobalSettingsView path) ─────────────────────────

    fun applyToggle(path: String, enabled: Boolean) {
        plugin.config.set(path, enabled)
        plugin.saveConfig()
    }

    fun applyChoice(path: String, value: Any) {
        plugin.config.set(path, value)
        plugin.saveConfig()
    }

    // ── reading current values (for the "Currently: …" line) ──────────────────

    fun isEnabled(path: String, default: Boolean): Boolean =
        plugin.config.getBoolean(path, default)

    fun markTouched() { if (!state.touched) state.touched = true }
}

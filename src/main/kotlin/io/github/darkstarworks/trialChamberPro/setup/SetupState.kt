package io.github.darkstarworks.trialChamberPro.setup

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/**
 * Machine-owned persistence for the setup tour (`setup-state.yml`, not config.yml — no
 * comments to preserve). Tracks:
 *  - [touched]: once true (operator ran `/tcp setup` or applied any step), the reminder
 *    never fires again.
 *  - reminder cadence ([reminderCount] / [lastReminderEpoch]) — capped + weekly.
 *  - per-player paused position, so `/tcp setup continue` resumes across restarts.
 *
 * Mirrors [io.github.darkstarworks.trialChamberPro.utils.WEVarStore]'s load-once /
 * save-on-change approach; the file is tiny and writes are infrequent (setup actions only).
 */
class SetupState(private val dataFolder: File) {

    private val file = File(dataFolder, FILE_NAME)
    private val cfg = YamlConfiguration().apply { if (file.exists()) runCatching { load(file) } }

    var touched: Boolean
        get() = cfg.getBoolean("touched", false)
        set(v) { cfg.set("touched", v); save() }

    var reminderCount: Int
        get() = cfg.getInt("reminder.count", 0)
        set(v) { cfg.set("reminder.count", v); save() }

    var lastReminderEpoch: Long
        get() = cfg.getLong("reminder.last-epoch", 0L)
        set(v) { cfg.set("reminder.last-epoch", v); save() }

    /** True once the operator has walked the tour all the way to the end. */
    var completed: Boolean
        get() = cfg.getBoolean("completed", false)
        set(v) { cfg.set("completed", v); save() }

    /** When set (epoch ms), a single "you started but didn't finish" nudge is due after it. 0 = none. */
    var followUpEpoch: Long
        get() = cfg.getLong("follow-up-epoch", 0L)
        set(v) { cfg.set("follow-up-epoch", v); save() }

    var followUpShown: Boolean
        get() = cfg.getBoolean("follow-up-shown", false)
        set(v) { cfg.set("follow-up-shown", v); save() }

    fun pausedIndex(uuid: UUID): Int? =
        if (cfg.contains("paused.$uuid")) cfg.getInt("paused.$uuid") else null

    fun setPaused(uuid: UUID, index: Int) { cfg.set("paused.$uuid", index); save() }

    fun clearPaused(uuid: UUID) { cfg.set("paused.$uuid", null); save() }

    private fun save() {
        runCatching {
            if (!dataFolder.exists()) dataFolder.mkdirs()
            cfg.save(file)
        }
    }

    companion object {
        const val FILE_NAME = "setup-state.yml"
    }
}

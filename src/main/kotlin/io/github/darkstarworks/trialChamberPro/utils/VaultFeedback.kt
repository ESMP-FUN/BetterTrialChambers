package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.joml.AxisAngle4f
import org.joml.Vector3f

/**
 * Renders the opt-in floating ✓/✗ vault feedback (v1.5.8).
 *
 * When `vaults.feedback.mode: HOLOGRAM`, a short-lived [TextDisplay] is spawned
 * above the vault block showing a green ✓ (success) or red ✗ (failure), paired
 * with a configurable sound (defaults: pillager celebrate / pillager ambient).
 *
 * The display is made invisible-by-default and then revealed to **only the
 * acting player** ([Player.showEntity]), so each looter sees their own result
 * without spamming everyone nearby. All entity work runs on the vault's region
 * thread (Folia-correct) and the display is removed after the configured
 * duration.
 *
 * Note: `ENTITY_PILLAGER_CELEBRATE` / `ENTITY_PILLAGER_AMBIENT` are random
 * variant pools — the API plays the sound *event*, not a specific `.ogg`.
 */
object VaultFeedback {

    /** Whether the operator has opted into hologram feedback. */
    fun isHologramMode(plugin: TrialChamberPro): Boolean =
        plugin.config.getString("vaults.feedback.mode", "TEXT")!!
            .trim().equals("HOLOGRAM", ignoreCase = true)

    /**
     * Spawns a per-player ✓/✗ display above [vaultLocation] and plays the
     * matching sound. Safe to call from any thread — schedules onto the
     * location's region thread internally.
     */
    fun showHologram(
        plugin: TrialChamberPro,
        player: Player,
        vaultLocation: Location,
        success: Boolean
    ) {
        plugin.scheduler.runAtLocation(vaultLocation, Runnable {
            try {
                if (!player.isOnline) return@Runnable
                val world = vaultLocation.world ?: return@Runnable
                val cfg = plugin.config

                val yOffset = cfg.getDouble("vaults.feedback.hologram.y-offset", 1.4)
                val scale = cfg.getDouble("vaults.feedback.hologram.scale", 1.5).toFloat()
                val seeThrough = cfg.getBoolean("vaults.feedback.hologram.see-through", true)
                val durationTicks = cfg.getLong("vaults.feedback.hologram.duration-ticks", 30L)
                    .coerceAtLeast(1L)

                val rawText = if (success) {
                    cfg.getString("vaults.feedback.hologram.success-text", "&a✔")!!
                } else {
                    cfg.getString("vaults.feedback.hologram.fail-text", "&c✘")!!
                }
                val component = LegacyComponentSerializer.legacyAmpersand().deserialize(rawText)

                val center = vaultLocation.clone().add(0.5, yOffset, 0.5)
                val display = world.spawn(center, TextDisplay::class.java) { d ->
                    d.isVisibleByDefault = false
                    d.isPersistent = false
                    d.billboard = Display.Billboard.CENTER
                    d.isSeeThrough = seeThrough
                    d.text(component)
                    d.transformation = org.bukkit.util.Transformation(
                        Vector3f(),
                        AxisAngle4f(0f, 0f, 0f, 1f),
                        Vector3f(scale, scale, scale),
                        AxisAngle4f(0f, 0f, 0f, 1f)
                    )
                }

                // Reveal to the acting player only.
                player.showEntity(plugin, display)

                // Sound (configurable; defaults to pillager celebrate/ambient).
                val soundName = if (success) {
                    cfg.getString("vaults.feedback.sounds.success", "ENTITY_PILLAGER_CELEBRATE")!!
                } else {
                    cfg.getString("vaults.feedback.sounds.fail", "ENTITY_PILLAGER_AMBIENT")!!
                }
                SoundUtil.resolve(soundName)?.let { sound ->
                    player.playSound(center, sound, 1.0f, 1.0f)
                }

                // Despawn after the configured duration.
                plugin.scheduler.runAtLocationLater(vaultLocation, Runnable {
                    if (display.isValid) display.remove()
                }, durationTicks)
            } catch (e: Exception) {
                plugin.logger.warning("[VaultFeedback] Failed to show hologram: ${e.message}")
            }
        })
    }
}

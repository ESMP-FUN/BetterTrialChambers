package com.esmpfun.bettertrialchambers.listeners

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.managers.SpawnerPresetManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Allows players to recover TCP-preset trial spawners that were placed outside
 * any registered chamber ("orphaned" spawners).
 *
 * Vanilla trial spawners can never be mined for a drop, they always drop
 * nothing regardless of tool or enchantment. TCP's protection listener only
 * guards spawners *inside* chambers; outside a chamber it returns early,
 * leaving vanilla's silent-drop behaviour in place. This creates a usability
 * trap: a spawner given via `/trial give` and placed anywhere on the map is
 * permanently stuck there.
 *
 * Fix:
 *   - Silk Touch tool -> cancel vanilla nothing-drop, drop the full preset item
 *     (PDC tag intact for re-placement), play the vanilla break effect.
 *   - No Silk Touch -> cancel the break entirely, send a hint. Prevents
 *     accidental permanent loss.
 *
 * TCP-WildSpawners (when installed) handles wild-preset spawner recovery
 * without Silk Touch via its own configurable hardness system. There is no
 * conflict: WildSpawners presets are left to WildSpawners, which mines them itself.
 *
 * "Orphaned" means: block has `tcp:preset_id` on its TileState AND
 * [com.esmpfun.bettertrialchambers.managers.ChamberManager.getCachedChamberAt]
 * returns null for its location.
 *
 * Added in v1.4.5.
 */
class OrphanSpawnerMineListener(private val plugin: BetterTrialChambers) : Listener {

    private val presetIdKey: NamespacedKey =
        NamespacedKey("trialchamberpro", SpawnerPresetManager.PRESET_ID_KEY_NAME)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.TRIAL_SPAWNER) return

        // Creative players can break orphan spawners normally without Silk Touch.
        if (event.player.gameMode == org.bukkit.GameMode.CREATIVE) return

        // Only act on TCP-tagged spawners
        val tileState = block.state as? TileState ?: return
        val presetId = tileState.persistentDataContainer
            .get(presetIdKey, PersistentDataType.STRING) ?: return

        // If inside a registered chamber, leave it alone, ProtectionListener handles that.
        if (plugin.chamberManager.getCachedChamberAt(block.location) != null) return

        // A WildSpawners preset is mined by WildSpawners, which also fires a test BlockBreakEvent
        // for claim plugins. Acting on that test broke the block and dropped a plain spawner.
        if (plugin.spawnerPresetManager.get(presetId) == null &&
            plugin.server.pluginManager.isPluginEnabled("WildSpawners")
        ) return

        event.isCancelled = true

        val tool = event.player.inventory.itemInMainHand
        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            event.player.sendMessage(plugin.getMessageComponent("orphan-spawner-needs-silk-touch"))
            return
        }

        val preset = plugin.spawnerPresetManager.get(presetId)
        val drop = when {
            preset == null -> org.bukkit.inventory.ItemStack(Material.TRIAL_SPAWNER)
            else -> try {
                plugin.spawnerPresetManager.getItem(preset, 1)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning(
                    "Spawner preset '$presetId' could not be rebuilt into an item " +
                        "(${e.message}); handing back a plain trial spawner instead."
                )
                org.bukkit.inventory.ItemStack(Material.TRIAL_SPAWNER)
            }
        }

        val loc = block.location
        block.world.dropItemNaturally(loc, drop)
        block.world.playEffect(loc, org.bukkit.Effect.STEP_SOUND, Material.TRIAL_SPAWNER)
        block.type = Material.AIR
        // Because we cancelled the event, MONITOR listeners won't run — keep index & waves in sync.
        plugin.trialSpawnerIndex.remove(block.world, block.x, block.y, block.z)
        plugin.spawnerWaveManager.cancelWaveAt(loc)
        plugin.spawnerWaveManager.removeStandaloneGlowAt(loc)
    }
}

package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ChamberSettingsHolder : BaseHolder()

/**
 * Chamber settings view — configure chamber-specific reset interval, exit location,
 * loot table overrides, and spawner cooldown. v1.3.0; migrated to VcGui in v1.5.0.
 */
class ChamberSettingsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber,
) : VcGui(
    rows = 5,
    title = plugin.getGuiText("gui.chamber-settings.title", "chamber" to chamber.name),
    holder = ChamberSettingsHolder(),
) {
    companion object {
        private val RESET_INTERVALS = listOf(
            0L to "gui.chamber-settings.reset-disabled",
            3600L to "gui.chamber-settings.reset-1h",
            6 * 3600L to "gui.chamber-settings.reset-6h",
            12 * 3600L to "gui.chamber-settings.reset-12h",
            24 * 3600L to "gui.chamber-settings.reset-24h",
            48 * 3600L to "gui.chamber-settings.reset-48h",
            7 * 24 * 3600L to "gui.chamber-settings.reset-1w"
        )

        private val SPAWNER_COOLDOWNS = listOf<Pair<Int?, String>>(
            null to "gui.chamber-settings.spawner-cd-global",
            -2 to "gui.chamber-settings.spawner-cd-match",
            -1 to "gui.chamber-settings.spawner-cd-vanilla",
            0 to "gui.chamber-settings.spawner-cd-none",
            5 to "gui.chamber-settings.spawner-cd-5m",
            10 to "gui.chamber-settings.spawner-cd-10m",
            15 to "gui.chamber-settings.spawner-cd-15m",
            30 to "gui.chamber-settings.spawner-cd-30m",
            60 to "gui.chamber-settings.spawner-cd-1h"
        )
    }

    init { layout() }

    private fun layout() {
        clear()
        // Row 0: header
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.COMPARATOR,
                "gui.chamber-settings.header-name", "gui.chamber-settings.header-lore",
                "chamber" to chamber.name)
        ))

        // Row 1: reset interval / exit
        set(11, VcGuiItem.wrap(createResetIntervalItem()) { ctx ->
            if (ctx.event.isLeftClick) cycleResetInterval(ctx.player, 1)
            else if (ctx.event.isRightClick) cycleResetInterval(ctx.player, -1)
        })
        set(15, VcGuiItem.wrap(createExitLocationItem()) { ctx ->
            if (ctx.event.isLeftClick) setExitLocation(ctx.player)
            else if (ctx.event.isRightClick) teleportToExit(ctx.player)
        })

        // Row 2: normal/ominous loot overrides + custom mob entry
        set(20, VcGuiItem.wrap(createNormalLootOverrideItem()) { ctx ->
            when {
                ctx.event.isShiftClick && ctx.event.isRightClick -> clearLootOverride(ctx.player, VaultType.NORMAL)
                ctx.event.isLeftClick -> cycleLootTable(ctx.player, VaultType.NORMAL, 1)
                ctx.event.isRightClick -> cycleLootTable(ctx.player, VaultType.NORMAL, -1)
            }
        })
        set(22, VcGuiItem.wrap(createCustomMobEntryItem()) { ctx ->
            menu.openCustomMobProvider(ctx.player, chamber)
        })
        set(24, VcGuiItem.wrap(createOminousLootOverrideItem()) { ctx ->
            when {
                ctx.event.isShiftClick && ctx.event.isRightClick -> clearLootOverride(ctx.player, VaultType.OMINOUS)
                ctx.event.isLeftClick -> cycleLootTable(ctx.player, VaultType.OMINOUS, 1)
                ctx.event.isRightClick -> cycleLootTable(ctx.player, VaultType.OMINOUS, -1)
            }
        })

        // Row 3: spawner cooldown + broadcast toggle
        set(31, VcGuiItem.wrap(createSpawnerCooldownItem()) { ctx ->
            when {
                ctx.event.isShiftClick && ctx.event.isRightClick -> clearSpawnerCooldown(ctx.player)
                ctx.event.isLeftClick -> cycleSpawnerCooldown(ctx.player, 1)
                ctx.event.isRightClick -> cycleSpawnerCooldown(ctx.player, -1)
            }
        })
        set(33, VcGuiItem.wrap(createBroadcastResetItem()) { ctx ->
            if (ctx.event.isLeftClick) toggleBroadcastReset(ctx.player)
        })

        // Row 4: nav
        set(36, GuiComponents.backVcItem(plugin, "gui.common.dest-chamber") { ctx ->
            val refreshed = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshed != null) menu.openChamberDetail(ctx.player, refreshed)
            else menu.openChamberList(ctx.player)
        })
        set(44, GuiComponents.closeVcItem(plugin))
    }

    private fun createResetIntervalItem(): ItemStack {
        val labelKey = RESET_INTERVALS.find { it.first == chamber.resetInterval }?.second
        val value = labelKey?.let { plugin.rawMessage(it) } ?: formatDuration(chamber.resetInterval * 1000)
        return GuiComponents.infoItem(plugin, Material.CLOCK,
            "gui.chamber-settings.reset-interval-name", "gui.chamber-settings.reset-interval-lore",
            "value" to value)
    }

    private fun createExitLocationItem(): ItemStack {
        val exitLoc = chamber.getExitLocation()
        val value = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" }
            ?: plugin.rawMessage("gui.chamber-settings.exit-not-set")
        return GuiComponents.infoItem(plugin, Material.OAK_DOOR,
            "gui.chamber-settings.exit-name", "gui.chamber-settings.exit-lore",
            "value" to value)
    }

    private fun createNormalLootOverrideItem(): ItemStack {
        val value = chamber.normalLootTable ?: plugin.rawMessage("gui.chamber-settings.override-default")
        return GuiComponents.infoItem(plugin, Material.GREEN_WOOL,
            "gui.chamber-settings.normal-override-name", "gui.chamber-settings.normal-override-lore",
            "value" to value)
    }

    private fun createOminousLootOverrideItem(): ItemStack {
        val value = chamber.ominousLootTable ?: plugin.rawMessage("gui.chamber-settings.override-default")
        return GuiComponents.infoItem(plugin, Material.PURPLE_WOOL,
            "gui.chamber-settings.ominous-override-name", "gui.chamber-settings.ominous-override-lore",
            "value" to value)
    }

    private fun createCustomMobEntryItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.ZOMBIE_HEAD,
            "gui.chamber-settings.custom-mob-name", "gui.chamber-settings.custom-mob-lore",
            "provider" to (chamber.customMobProvider ?: "vanilla"),
            "normal" to chamber.customMobIdsNormal.size,
            "ominous" to chamber.customMobIdsOminous.size)

    private fun createSpawnerCooldownItem(): ItemStack {
        val labelKey = SPAWNER_COOLDOWNS.find { it.first == chamber.spawnerCooldownMinutes }?.second
        val value = labelKey?.let { plugin.rawMessage(it) }
            ?: chamber.spawnerCooldownMinutes?.let { "${it}m" }
            ?: plugin.rawMessage("gui.chamber-settings.spawner-cd-global")
        return GuiComponents.infoItem(plugin, Material.SPAWNER,
            "gui.chamber-settings.spawner-cd-name", "gui.chamber-settings.spawner-cd-lore",
            "value" to value)
    }

    private fun createBroadcastResetItem(): ItemStack {
        val globalEnabled = plugin.config.getBoolean("global.reset-complete-alert", true)
        return if (!globalEnabled) {
            GuiComponents.infoItem(plugin, Material.GRAY_DYE,
                "gui.chamber-settings.broadcast-reset-name", "gui.chamber-settings.broadcast-reset-overridden-lore")
        } else {
            GuiComponents.toggleItem(plugin, chamber.broadcastResetComplete,
                "gui.chamber-settings.broadcast-reset-name", "gui.chamber-settings.broadcast-reset-lore")
        }
    }

    // ==================== Action Handlers (verbatim) ====================

    private fun cycleResetInterval(player: Player, direction: Int) {
        val currentIndex = RESET_INTERVALS.indexOfFirst { it.first == chamber.resetInterval }
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else RESET_INTERVALS.lastIndex
        } else (currentIndex + direction).mod(RESET_INTERVALS.size)
        val (newInterval, newLabelKey) = RESET_INTERVALS[newIndex]
        val newName = plugin.rawMessage(newLabelKey)

        plugin.launchAsync {
            val success = plugin.chamberManager.updateResetInterval(chamber.id, newInterval)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-reset-interval-set", "value" to newName))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-reset-interval-failed"))
            })
        }
    }

    private fun setExitLocation(player: Player) {
        val location = player.location
        plugin.launchAsync {
            val success = plugin.chamberManager.updateExitLocation(
                chamber.id, location.x, location.y, location.z, location.yaw, location.pitch
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-exit-location-set"))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-exit-location-failed"))
            })
        }
    }

    private fun teleportToExit(player: Player) {
        val exitLoc = chamber.getExitLocation()
        if (exitLoc == null) {
            player.sendMessage(plugin.getMessageComponent("gui-no-exit-location"))
            return
        }
        player.teleportAsync(exitLoc).thenRun {
            player.sendMessage(plugin.getMessageComponent("gui-teleport-to-exit"))
        }
        player.closeInventory()
    }

    private fun cycleLootTable(player: Player, vaultType: VaultType, direction: Int) {
        val tables = plugin.lootManager.getLootTableNames().sorted()
        if (tables.isEmpty()) {
            player.sendMessage(plugin.getMessageComponent("gui-no-loot-tables"))
            return
        }
        val currentOverride = when (vaultType) {
            VaultType.NORMAL -> chamber.normalLootTable
            VaultType.OMINOUS -> chamber.ominousLootTable
        }
        val currentIndex = tables.indexOf(currentOverride)
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else tables.lastIndex
        } else (currentIndex + direction).mod(tables.size)
        val newTable = tables[newIndex]

        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, newTable)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-loot-table-set", "type" to plugin.vaultTypeDisplay(vaultType), "table" to newTable))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-loot-table-failed"))
            })
        }
    }

    private fun clearLootOverride(player: Player, vaultType: VaultType) {
        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, null)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-loot-table-cleared", "type" to plugin.vaultTypeDisplay(vaultType)))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-loot-clear-failed"))
            })
        }
    }

    private fun cycleSpawnerCooldown(player: Player, direction: Int) {
        val currentIndex = SPAWNER_COOLDOWNS.indexOfFirst { it.first == chamber.spawnerCooldownMinutes }
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else SPAWNER_COOLDOWNS.lastIndex
        } else (currentIndex + direction).mod(SPAWNER_COOLDOWNS.size)
        val (newCooldown, newLabelKey) = SPAWNER_COOLDOWNS[newIndex]
        val newName = plugin.rawMessage(newLabelKey)

        plugin.launchAsync {
            val success = plugin.chamberManager.updateSpawnerCooldown(chamber.id, newCooldown)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-spawner-cooldown-set", "value" to newName))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-spawner-cooldown-failed"))
            })
        }
    }

    private fun clearSpawnerCooldown(player: Player) {
        plugin.launchAsync {
            val success = plugin.chamberManager.updateSpawnerCooldown(chamber.id, null)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent("gui-spawner-cooldown-reset"))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-spawner-cooldown-reset-failed"))
            })
        }
    }

    private fun toggleBroadcastReset(player: Player) {
        if (!plugin.config.getBoolean("global.reset-complete-alert", true)) {
            player.sendMessage(plugin.getMessageComponent("gui-broadcast-reset-global-override"))
            return
        }
        val newValue = !chamber.broadcastResetComplete
        plugin.launchAsync {
            val success = plugin.chamberManager.setBroadcastResetComplete(chamber.id, newValue)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessageComponent(
                        if (newValue) "gui-broadcast-reset-enabled" else "gui-broadcast-reset-disabled"
                    ))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessageComponent("gui-broadcast-reset-failed"))
            })
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m")
        }.trim().ifEmpty { "${seconds}s" }
    }
}

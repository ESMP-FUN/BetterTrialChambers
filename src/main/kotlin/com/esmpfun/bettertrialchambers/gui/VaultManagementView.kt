package com.esmpfun.bettertrialchambers.gui

import com.esmpfun.bettertrialchambers.BetterTrialChambers
import com.esmpfun.bettertrialchambers.gui.components.GuiComponents
import com.esmpfun.bettertrialchambers.gui.framework.BaseHolder
import com.esmpfun.bettertrialchambers.gui.framework.VcGui
import com.esmpfun.bettertrialchambers.gui.framework.VcGuiItem
import com.esmpfun.bettertrialchambers.models.Chamber
import com.esmpfun.bettertrialchambers.models.VaultData
import com.esmpfun.bettertrialchambers.models.VaultType
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class VaultManagementHolder : BaseHolder()

/**
 * Vault management view — view and reset vault cooldowns for a chamber.
 * v1.3.0; migrated to VcGui in v1.5.0. `runBlocking` preserved.
 */
class VaultManagementView(
    private val plugin: BetterTrialChambers,
    private val menu: MenuService,
    private val chamber: Chamber,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.vault-management.title", "chamber" to chamber.name),
    holder = VaultManagementHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)

        // Row 0: reset-all / header / reset-player-hint
        set(2, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.TNT,
                "gui.vault-management.reset-all-name", "gui.vault-management.reset-all-lore")
        ) { ctx ->
            if (ctx.event.isShiftClick && ctx.event.isLeftClick) resetAllCooldowns(ctx.player)
        })
        set(4, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.VAULT,
                "gui.vault-management.header-name", "gui.vault-management.header-lore",
                "normal" to normalCount, "ominous" to ominousCount)
        ))
        set(6, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.PLAYER_HEAD,
                "gui.vault-management.reset-player-name", "gui.vault-management.reset-player-lore",
                "chamber" to chamber.name)
        ) { ctx ->
            ctx.player.sendMessage(plugin.getMessageComponent("vault-reset-usage-hint", "chamber" to chamber.name))
        })

        // Rows 1-4: vaults (up to 36, slots 9..44)
        val vaults = runBlocking { plugin.vaultManager.getVaultsForChamber(chamber.id) }
        vaults.take(36).forEachIndexed { index, vault ->
            val slot = 9 + index
            set(slot, VcGuiItem.wrap(createVaultItem(vault)) { ctx ->
                if (ctx.event.isShiftClick && ctx.event.isRightClick) resetVaultCooldowns(ctx.player, vault)
            })
        }
        if (vaults.isEmpty()) {
            set(22, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.vault-management.empty-name", "gui.vault-management.empty-lore",
                    "chamber" to chamber.name)
            ))
        }

        // Row 5: back / player-locks info / close
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-chamber") { ctx ->
            menu.openChamberDetail(ctx.player, chamber)
        })
        val playersWithLocks = getPlayersWithLockedVaults()
        if (playersWithLocks.isNotEmpty()) {
            set(49, VcGuiItem.wrap(createPlayersWithLocksItem(playersWithLocks)))
        }
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun createVaultItem(vault: VaultData): ItemStack {
        val isOminous = vault.type == VaultType.OMINOUS
        val material = if (isOminous) Material.CRYING_OBSIDIAN else Material.CHISELED_TUFF
        val nameKey = if (isOminous)
            "gui.vault-management.vault-name-ominous" else "gui.vault-management.vault-name-normal"
        val lockCount = runBlocking { plugin.vaultManager.getVaultLockCount(vault.id) }
        return GuiComponents.infoItem(plugin, material,
            nameKey, "gui.vault-management.vault-lore",
            "id" to vault.id,
            "type" to plugin.vaultTypeDisplay(vault.type),
            "x" to vault.x, "y" to vault.y, "z" to vault.z,
            "table" to vault.lootTable,
            "locks" to lockCount)
    }

    private fun createPlayersWithLocksItem(players: List<Pair<Player, Int>>): ItemStack {
        val lore = mutableListOf<Component>(
            plugin.getGuiText("gui.vault-management.locks-info-header"),
            Component.empty()
        )
        players.take(8).forEach { (p, count) ->
            lore.add(plugin.getGuiText("gui.vault-management.locks-info-line",
                "name" to p.name, "count" to count))
        }
        if (players.size > 8) {
            lore.add(plugin.getGuiText("gui.vault-management.locks-info-overflow",
                "extra" to (players.size - 8)))
        }
        return ItemStack(Material.KNOWLEDGE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.vault-management.locks-info-name"))
                lore(lore)
            }
        }
    }

    // ==================== Action Handlers (verbatim) ====================

    private fun resetAllCooldowns(player: Player) {
        player.sendMessage(plugin.getMessageComponent("vault-reset-all-start", "chamber" to chamber.name))
        plugin.launchAsync {
            val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)
            var resetCount = 0
            vaults.forEach { vault ->
                plugin.vaultManager.resetAllCooldowns(vault.id)
                resetCount++
            }
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("vault-reset-all-complete", "count" to resetCount))
                menu.openVaultManagement(player, chamber)
            })
        }
    }

    private fun resetVaultCooldowns(player: Player, vault: VaultData) {
        player.sendMessage(plugin.getMessageComponent("vault-reset-single-start", "id" to vault.id))
        plugin.launchAsync {
            plugin.vaultManager.resetAllCooldowns(vault.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("vault-reset-single-complete"))
                menu.openVaultManagement(player, chamber)
            })
        }
    }

    private fun getPlayersWithLockedVaults(): List<Pair<Player, Int>> {
        return Bukkit.getOnlinePlayers().mapNotNull { p ->
            val (normalLocked, ominousLocked) = runBlocking {
                plugin.vaultManager.getLockedVaultCounts(p.uniqueId, chamber.id)
            }
            val total = normalLocked + ominousLocked
            if (total > 0) p to total else null
        }.sortedByDescending { it.second }
    }
}

package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import io.github.darkstarworks.trialChamberPro.managers.ContainerLootManager
import io.github.darkstarworks.trialChamberPro.models.Chamber
import kotlinx.coroutines.runBlocking
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ContainerLootHolder : BaseHolder()

/**
 * Per-chamber container-loot management (v1.5.9). Surfaces the
 * `chests.per-player-loot` feature in `/tcp menu`: status + counts, a
 * paginated list of materialized templates (left-click = edit, right-click =
 * teleport), and bulk actions (materialize all, clear player copies, reset
 * templates). All strings under `gui.container-loot.*`. Mirrors
 * [VaultManagementView] (incl. the `runBlocking` fetch — see the deferred
 */
class ContainerLootView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber,
    private val page: Int,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.container-loot.view-title", "chamber" to chamber.name),
    holder = ContainerLootHolder(),
) {
    private companion object { const val PER_PAGE = 36 }

    init { layout() }

    private fun layout() {
        clear()
        val templates = runBlocking { plugin.containerLootManager.listTemplates(chamber.id) }
        val copies = runBlocking { plugin.containerLootManager.countPlayerCopies(chamber.id) }
        val enabled = plugin.config.getBoolean("chests.per-player-loot", false)
        val totalPages = maxOf(1, (templates.size + PER_PAGE - 1) / PER_PAGE)
        val pageClamped = page.coerceIn(0, totalPages - 1)

        // Row 0: status + bulk actions
        set(2, VcGuiItem.wrap(headerItem(enabled, templates.size, copies)))
        set(4, VcGuiItem.wrap(materializeItem()) { ctx -> doMaterialize(ctx.player) })
        set(6, VcGuiItem.wrap(clearCopiesItem(copies)) { ctx ->
            if (ctx.event.isShiftClick && ctx.event.isLeftClick) doClearCopies(ctx.player)
        })
        set(8, VcGuiItem.wrap(clearTemplatesItem(templates.size)) { ctx ->
            if (ctx.event.isShiftClick && ctx.event.isLeftClick) doClearTemplates(ctx.player)
        })

        // Rows 1-4: template entries (36 per page)
        val start = pageClamped * PER_PAGE
        templates.drop(start).take(PER_PAGE).forEachIndexed { i, t ->
            set(9 + i, VcGuiItem.wrap(entryItem(t, start + i)) { ctx ->
                handleEntry(ctx.player, t, ctx.event.isLeftClick, ctx.event.isRightClick)
            })
        }
        if (templates.isEmpty()) {
            set(22, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.container-loot.empty-name", "gui.container-loot.empty-lore",
                    "chamber" to chamber.name)
            ))
        }

        // Row 5: nav
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-chamber") { ctx ->
            menu.openChamberDetail(ctx.player, chamber)
        })
        set(48, GuiComponents.prevPageVcItem(plugin, pageClamped, totalPages) { ctx ->
            menu.openContainerLoot(ctx.player, chamber, pageClamped - 1)
        })
        set(50, GuiComponents.nextPageVcItem(plugin, pageClamped, totalPages) { ctx ->
            menu.openContainerLoot(ctx.player, chamber, pageClamped + 1)
        })
        set(53, GuiComponents.closeVcItem(plugin))
    }

    // ==================== Item creators ====================

    private fun headerItem(enabled: Boolean, templates: Int, copies: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.BARREL,
            "gui.container-loot.header-name", "gui.container-loot.header-lore",
            // Pass the RAW message (legacy `&` form) — not getMessage(), which
            // returns a section-coded string that breaks the lore's MiniMessage
            // re-parse and renders the `&` codes literally.
            "enabled" to (plugin.getMessageList(
                if (enabled) "gui.container-loot.status-on" else "gui.container-loot.status-off"
            ).firstOrNull() ?: ""),
            "templates" to templates, "copies" to copies)

    private fun materializeItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.CHEST,
            "gui.container-loot.materialize-name", "gui.container-loot.materialize-lore")

    private fun clearCopiesItem(copies: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.WATER_BUCKET,
            "gui.container-loot.clear-copies-name", "gui.container-loot.clear-copies-lore",
            "copies" to copies)

    private fun clearTemplatesItem(templates: Int): ItemStack =
        GuiComponents.infoItem(plugin, Material.TNT,
            "gui.container-loot.clear-templates-name", "gui.container-loot.clear-templates-lore",
            "templates" to templates)

    private fun entryItem(row: ContainerLootManager.TemplateRow, index: Int): ItemStack {
        val items = row.contents.count { it != null && !it.type.isAir }
        // Icon = the real container block (chest/barrel/dispenser/dropper) so ops
        // can spot the one they want at a glance instead of a wall of chests.
        val icon = if (row.material.isItem) row.material else Material.CHEST
        return GuiComponents.infoItem(plugin, icon,
            "gui.container-loot.entry-name", "gui.container-loot.entry-lore",
            "index" to (index + 1),
            "x" to row.pos.x, "y" to row.pos.y, "z" to row.pos.z,
            "items" to items)
    }

    // ==================== Actions ====================

    private fun handleEntry(player: Player, row: ContainerLootManager.TemplateRow, left: Boolean, right: Boolean) {
        when {
            right -> {
                val world = chamber.getWorld() ?: return
                val loc = Location(world, row.pos.x + 0.5, row.pos.y + 1.0, row.pos.z + 0.5)
                player.teleportAsync(loc)
                player.sendMessage(plugin.getMessageComponent("container-tp",
                    "x" to row.pos.x, "y" to row.pos.y, "z" to row.pos.z))
                player.closeInventory()
            }
            left -> plugin.launchAsync {
                // Opens the template editor inventory, replacing this GUI.
                plugin.containerLootListener.openTemplateEditor(player, chamber, row.pos)
            }
        }
    }

    private fun doMaterialize(player: Player) {
        player.sendMessage(plugin.getMessageComponent("container-materialize-start", "chamber" to chamber.name))
        plugin.launchAsync {
            val created = plugin.containerLootListener.materializeChamber(chamber)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("container-materialize-done", "count" to created))
                menu.openContainerLoot(player, chamber, page)
            })
        }
    }

    private fun doClearCopies(player: Player) {
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearChamber(chamber.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("container-cleared-copies", "count" to n))
                menu.openContainerLoot(player, chamber, page)
            })
        }
    }

    private fun doClearTemplates(player: Player) {
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearTemplates(chamber.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessageComponent("container-cleared-templates", "count" to n))
                menu.openContainerLoot(player, chamber, page)
            })
        }
    }
}

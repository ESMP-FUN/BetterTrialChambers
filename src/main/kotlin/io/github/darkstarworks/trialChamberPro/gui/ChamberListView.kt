package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import io.github.darkstarworks.trialChamberPro.models.Chamber
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Empty payload holder for the chamber-list GUI. */
class ChamberListHolder : BaseHolder()

/**
 * Paginated chamber list view — 36 chambers per page with prev/next and a create-help card.
 * All strings from `messages.yml` under `gui.chamber-list.*` (v1.3.0; migrated to VcGui in v1.5.0).
 *
 * NB: still uses `runBlocking { ... }` to fetch locked-vault counts during construction.
 * Per the deferred-refactor entry, that should become an async-build/sync-show pattern
 * eventually — out of scope for the framework migration itself.
 */
class ChamberListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val viewer: Player,
    private val page: Int = 0,
) : VcGui(
    rows = 6,
    title = buildChamberListTitle(plugin, page, totalPages(plugin)),
    holder = ChamberListHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        val allChambers = plugin.chamberManager.getCachedChambers().sortedBy { it.name }
        val totalPages = maxOf(1, (allChambers.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
        val currentPage = page.coerceIn(0, totalPages - 1)
        val startIdx = currentPage * ITEMS_PER_PAGE
        val endIdx = minOf(startIdx + ITEMS_PER_PAGE, allChambers.size)
        val onPage = if (allChambers.isNotEmpty()) allChambers.subList(startIdx, endIdx) else emptyList()

        // Rows 0-3: chamber cards
        onPage.forEachIndexed { i, chamber ->
            set(i, VcGuiItem.wrap(createChamberItem(chamber, viewer)) { ctx ->
                menu.openChamberDetail(ctx.player, chamber)
            })
        }
        if (allChambers.isEmpty()) {
            set(13, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.chamber-list.empty-name", "gui.chamber-list.empty-lore")
            ))
        }

        // Row 4: page indicator + prev/next
        set(40, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.PAPER,
                "gui.chamber-list.page-info-name", "gui.chamber-list.page-info-lore",
                "page" to (currentPage + 1), "total" to totalPages,
                "total-chambers" to allChambers.size, "on-page" to onPage.size)
        ))
        if (currentPage > 0) {
            set(39, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.SPECTRAL_ARROW,
                    "gui.chamber-list.prev-page-name", "gui.chamber-list.prev-page-lore",
                    "page" to currentPage)
            ) { ctx -> menu.openChamberList(ctx.player, currentPage - 1) })
        }
        if (currentPage < totalPages - 1) {
            set(41, VcGuiItem.wrap(
                GuiComponents.infoItem(plugin, Material.TIPPED_ARROW,
                    "gui.chamber-list.next-page-name", "gui.chamber-list.next-page-lore",
                    "page" to (currentPage + 2))
            ) { ctx -> menu.openChamberList(ctx.player, currentPage + 1) })
        }

        // Row 5: back / create-help / close
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(49, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
                "gui.chamber-list.create-name", "gui.chamber-list.create-lore")
        ))
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun createChamberItem(chamber: Chamber, player: Player): ItemStack {
        val timeUntilMs = plugin.resetManager.getTimeUntilReset(chamber)
        val lastResetMs = chamber.lastReset ?: chamber.createdAt
        val sinceLastMs = System.currentTimeMillis() - lastResetMs
        val playersInside = chamber.getPlayersInside().size
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)
        val (normalLocked, ominousLocked) = runBlocking {
            plugin.vaultManager.getLockedVaultCounts(player.uniqueId, chamber.id)
        }

        val base = GuiComponents.infoItem(
            plugin, Material.LODESTONE,
            "gui.chamber-list.chamber-name", "gui.chamber-list.chamber-lore",
            "chamber" to chamber.name,
            "world" to chamber.world,
            "volume" to chamber.getVolume(),
            "inside" to playersInside,
            "normal" to normalCount, "ominous" to ominousCount,
            "normalLocked" to normalLocked, "ominousLocked" to ominousLocked,
            "reset" to DurationFmt.humanize(plugin, timeUntilMs),
            "lastReset" to DurationFmt.humanize(plugin, sinceLastMs)
        )

        if (chamber.normalLootTable != null || chamber.ominousLootTable != null) {
            base.itemMeta = base.itemMeta?.apply {
                val existing = lore() ?: mutableListOf()
                val withTag = existing.toMutableList().apply {
                    add(Component.empty())
                    add(plugin.getGuiText("gui.chamber-list.chamber-custom-loot-tag"))
                }
                lore(withTag)
            }
        }
        return base
    }

    companion object {
        private const val ITEMS_PER_PAGE = 36

        private fun totalPages(plugin: TrialChamberPro): Int {
            val n = plugin.chamberManager.getCachedChambers().size
            return maxOf(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
        }
    }
}

private fun buildChamberListTitle(plugin: TrialChamberPro, page: Int, totalPages: Int): Component {
    val current = page.coerceIn(0, totalPages - 1)
    return plugin.getGuiText("gui.chamber-list.title", "page" to (current + 1), "total" to totalPages)
}

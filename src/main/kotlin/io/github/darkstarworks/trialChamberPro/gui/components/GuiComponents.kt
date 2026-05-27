package io.github.darkstarworks.trialChamberPro.gui.components

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

/**
 * Reusable building blocks for the admin GUI (added in v1.3.0).
 *
 * Every builder pulls its display strings from `messages.yml` via
 * [TrialChamberPro.getGuiText] / [TrialChamberPro.getGuiLore], so the entire
 * GUI layer becomes translatable. Icon conventions are standardised here so
 * callers don't have to remember which material is "the back arrow this
 * week".
 *
 * Material conventions (canonicalised here):
 *
 * Navigation:
 *   - Back           → ARROW
 *   - Close          → BARRIER
 *   - Prev page      → SPECTRAL_ARROW
 *   - Next page      → TIPPED_ARROW
 *
 * Action buttons (verbs the player performs):
 *   - Add            → LIME_DYE      (e.g. "Add from hand", "+ New pool")
 *   - Remove         → RED_DYE
 *   - Save           → GREEN_CONCRETE
 *   - Discard/Cancel → RED_CONCRETE
 *   - Adjust/Cycle   → YELLOW_CONCRETE
 *   - Reset (one-shot) → CYAN_CONCRETE
 *   - Empty placeholder → BARRIER (or LIGHT_GRAY_STAINED_GLASS_PANE for inline)
 *
 * State indicators (the item *is* the state, not an action):
 *   - Toggle ON      → LIME_WOOL     (used by [toggleItem])
 *   - Toggle OFF     → RED_WOOL      (used by [toggleItem])
 *
 * Settings categories:
 *   - General/numeric setting → COMPARATOR
 *   - Reload/refresh           → REPEATER
 *   - Advanced/admin           → COMMAND_BLOCK
 *
 * Domain icons (semantic, override the above when the meaning is obvious):
 *   - Time/duration setting    → CLOCK
 *   - Spawner/cooldown         → SPAWNER
 *   - Mob provider             → ZOMBIE_HEAD (avoid SPAWNER to prevent collision
 *                                with the spawner-cooldown icon on the same screen)
 *   - Chamber overview entry   → LODESTONE
 *   - Vault                    → VAULT
 *   - Teleport                 → ENDER_PEARL
 *   - Exit/door                → OAK_DOOR
 *   - Snapshot/inspect         → SPYGLASS
 *   - Loot pool / chest        → CHEST
 *   - Normal vault loot        → GREEN_WOOL  (color-keyed)
 *   - Ominous vault loot       → PURPLE_WOOL (color-keyed)
 *   - Loot override (set)      → ENCHANTED_BOOK
 *   - Loot override (default)  → BOOK
 *   - Information / hint card  → PAPER (text) or KNOWLEDGE_BOOK (info dump)
 */
object GuiComponents {

    /** Fills an entire row with a single-material filler (invisible border). */
    /**
     * Canonical "informational item" where both name and lore come from
     * messages.yml. For items that only have a name, pass `loreKey = null`.
     */
    fun infoItem(
        plugin: TrialChamberPro,
        material: Material,
        nameKey: String,
        loreKey: String? = null,
        vararg replacements: Pair<String, Any?>
    ): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText(nameKey, *replacements))
                if (loreKey != null) {
                    lore(plugin.getGuiLore(loreKey, *replacements))
                }
            }
        }
    }

    /** Player head ItemStack with offline-player resolution and custom name/lore. */
    fun playerHead(
        plugin: TrialChamberPro,
        owner: OfflinePlayer,
        nameKey: String,
        loreKey: String? = null,
        vararg replacements: Pair<String, Any?>
    ): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as? SkullMeta)?.apply {
                owningPlayer = owner
                displayName(plugin.getGuiText(nameKey, *replacements))
                if (loreKey != null) {
                    lore(plugin.getGuiLore(loreKey, *replacements))
                }
            }
        }
    }

    /**
     * Canonical toggle item for on/off settings. Material is LIME_WOOL when enabled,
     * RED_WOOL when disabled. Name and lore come from shared `gui.common.toggle-*` keys.
     *
     * @param labelKey messages.yml key for the setting label (e.g. "gui.protection-menu.block-break-label")
     * @param descKey  messages.yml key for a short description string
     */
    fun toggleItem(
        plugin: TrialChamberPro,
        enabled: Boolean,
        labelKey: String,
        descKey: String
    ): ItemStack {
        val material = if (enabled) Material.LIME_WOOL else Material.RED_WOOL
        val label = plugin.getMessage(labelKey)
        val description = plugin.getMessage(descKey)
        val nameKey = if (enabled) "gui.common.toggle-name-enabled" else "gui.common.toggle-name-disabled"
        val loreKey = if (enabled) "gui.common.toggle-lore-enabled" else "gui.common.toggle-lore-disabled"
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText(nameKey, "label" to label))
                lore(plugin.getGuiLore(loreKey, "description" to description))
            }
        }
    }

    /** Player head by UUID, resolved via [org.bukkit.Bukkit.getOfflinePlayer]. */
    fun playerHead(
        plugin: TrialChamberPro,
        uuid: UUID,
        nameKey: String,
        loreKey: String? = null,
        vararg replacements: Pair<String, Any?>
    ): ItemStack = playerHead(plugin, org.bukkit.Bukkit.getOfflinePlayer(uuid), nameKey, loreKey, *replacements)

    // ==================== VcGui nav helpers ====================
    //
    // Navigation buttons that bundle a click handler — these return
    // `VcGuiItem` directly because the click behavior is the helper's value.
    // The `Vc` prefix is a historical artifact of the v1.5.0 IF→VcGui
    // migration; the old IF-returning `backButton`/`closeButton`/etc. lived
    // here in parallel during the transition. The names can collapse to
    // unprefixed (`backItem` etc.) in a future renaming pass — held off for
    // now to keep the v1.5.0 diff focused.
    //
    // ItemStack-returning helpers above (infoItem, toggleItem, playerHead)
    // are framework-agnostic — views wrap with `VcGuiItem.wrap(stack, onClick = ...)`.

    /** Back-button as a `VcGuiItem`. */
    fun backVcItem(
        plugin: TrialChamberPro,
        destinationKey: String,
        onClick: (io.github.darkstarworks.trialChamberPro.gui.framework.ClickContext) -> Unit
    ): io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem {
        val destination = plugin.getMessage(destinationKey)
        val stack = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.common.back-button", "destination" to destination))
            }
        }
        return io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem.wrap(stack, onClick = onClick)
    }

    /** Close-button (closes the player's open inventory). */
    fun closeVcItem(
        plugin: TrialChamberPro
    ): io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem {
        val stack = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.common.close-button"))
            }
        }
        return io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem.wrap(stack) { ctx ->
            ctx.player.closeInventory()
        }
    }

    /** Previous-page button for paginated lists. Disabled-state lore + onClick gating when there's no prior page. */
    fun prevPageVcItem(
        plugin: TrialChamberPro,
        currentPage: Int,
        totalPages: Int,
        onClick: (io.github.darkstarworks.trialChamberPro.gui.framework.ClickContext) -> Unit
    ): io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem {
        val enabled = currentPage > 0
        val material = if (enabled) Material.SPECTRAL_ARROW else Material.GRAY_STAINED_GLASS_PANE
        val stack = ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.common.prev-page"))
                lore(plugin.getGuiLore(
                    "gui.common.page-indicator-lore",
                    "current" to (currentPage + 1),
                    "total" to totalPages
                ))
            }
        }
        return io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem.wrap(stack) { ctx ->
            if (enabled) onClick(ctx)
        }
    }

    /** Next-page button for paginated lists. Disabled-state lore + onClick gating when there's no next page. */
    fun nextPageVcItem(
        plugin: TrialChamberPro,
        currentPage: Int,
        totalPages: Int,
        onClick: (io.github.darkstarworks.trialChamberPro.gui.framework.ClickContext) -> Unit
    ): io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem {
        val enabled = currentPage < totalPages - 1
        val material = if (enabled) Material.TIPPED_ARROW else Material.GRAY_STAINED_GLASS_PANE
        val stack = ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.common.next-page"))
                lore(plugin.getGuiLore(
                    "gui.common.page-indicator-lore",
                    "current" to (currentPage + 1),
                    "total" to totalPages
                ))
            }
        }
        return io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem.wrap(stack) { ctx ->
            if (enabled) onClick(ctx)
        }
    }
}

/**
 * Tiny wrapper to serialize a gui-text message down to a legacy-section string
 * for places that require `String` (e.g. `ChestGui(rows, title)` constructor).
 */
object GuiText {
    fun plain(plugin: TrialChamberPro, key: String, vararg replacements: Pair<String, Any?>): String {
        val component = plugin.getGuiText(key, *replacements)
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection()
            .serialize(component)
    }
}

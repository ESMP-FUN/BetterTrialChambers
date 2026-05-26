package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.framework.BaseHolder
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGui
import io.github.darkstarworks.trialChamberPro.gui.framework.VcGuiItem
import org.bukkit.Material

class HelpMenuHolder : BaseHolder()

/**
 * Help menu view — mirrors `/tcp help`. v1.4.x rewrite, v1.5.0 to VcGui.
 */
class HelpMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
) : VcGui(
    rows = 6,
    title = plugin.getGuiText("gui.help-menu.title"),
    holder = HelpMenuHolder(),
) {
    init { layout() }

    private fun layout() {
        clear()
        set(4, info(Material.KNOWLEDGE_BOOK, "header"))
        set(10, info(Material.COMMAND_BLOCK, "commands"))
        set(13, info(Material.BOOK, "permissions"))
        set(16, VcGuiItem.wrap(
            GuiComponents.infoItem(plugin, Material.TRIAL_KEY,
                "gui.help-menu.about-name", "gui.help-menu.about-lore",
                "version" to plugin.pluginMeta.version)
        ))
        set(19, info(Material.LODESTONE, "chambers-cmd"))
        set(21, info(Material.CHEST, "loot-cmd"))
        set(23, info(Material.VAULT, "vault-cmd"))
        set(25, info(Material.WRITABLE_BOOK, "stats-cmd"))
        set(28, info(Material.SPYGLASS, "snapshot-cmd"))
        set(30, info(Material.GOLDEN_PICKAXE, "generate-cmd"))
        set(32, info(Material.ZOMBIE_HEAD, "mobs-cmd"))
        set(34, info(Material.SPAWNER, "give-cmd"))
        set(40, info(Material.COMPARATOR, "admin-cmd"))
        set(45, GuiComponents.backVcItem(plugin, "gui.common.dest-main-menu") { ctx ->
            menu.openMainMenu(ctx.player)
        })
        set(53, GuiComponents.closeVcItem(plugin))
    }

    private fun info(material: Material, id: String): VcGuiItem =
        VcGuiItem.wrap(GuiComponents.infoItem(plugin, material,
            "gui.help-menu.$id-name", "gui.help-menu.$id-lore"))
}

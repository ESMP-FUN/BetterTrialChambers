package io.github.darkstarworks.trialChamberPro.gui

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * High-level wrapper around Paper's Dialog API (added MC 1.21.6, Paper bindings since
 * 1.21.7). Ported from TCP-MythicTrials. Hides the raw `Dialog.create { ... }` ceremony
 * and funnels callbacks through `DialogAction.customClick`.
 *
 * **Runtime-gated:** this class references Paper-only Dialog classes, so it must only be
 * loaded/instantiated when the API is present (see [isAvailable]). On servers without it,
 * `/tcp setup` falls back to the clickable-chat tour and this class is never touched.
 *
 * Shapes:
 *  - [showNotice]       — single OK button.
 *  - [showConfirmation] — yes / no.
 *  - [showMultiAction]  — N arbitrary action buttons (the setup tour's
 *    Enable / Skip / Disable / Pause / Stop shape).
 */
class DialogPromptManager(@Suppress("unused") private val plugin: TrialChamberPro) {

    fun prompt(): Builder = Builder()

    /** One custom-action button: a label, optional tooltip, and a click handler. */
    data class Action(
        val label: Component,
        val tooltip: Component? = null,
        val handler: (io.papermc.paper.dialog.DialogResponseView) -> Unit,
    )

    inner class Builder {
        private var title: Component = Component.empty()
        private var canCloseWithEscape: Boolean = true
        private val body = mutableListOf<DialogBody>()
        private val inputs = mutableListOf<DialogInput>()

        fun title(t: Component): Builder = apply { title = t }

        fun canCloseWithEscape(allow: Boolean): Builder = apply { canCloseWithEscape = allow }

        fun bodyText(component: Component): Builder = apply {
            body += DialogBody.plainMessage(component)
        }

        fun bodyItem(stack: ItemStack): Builder = apply {
            body += DialogBody.item(stack).build()
        }

        fun textInput(
            id: String,
            label: Component,
            initial: String = "",
            maxLength: Int = 32,
            multiline: Boolean = false,
            maxLines: Int? = null,
            height: Int? = null,
        ): Builder = apply {
            val builder = DialogInput.text(id, label)
                .initial(initial)
                .maxLength(maxLength)
            if (multiline) {
                builder.multiline(TextDialogInput.MultilineOptions.create(maxLines, height))
            }
            inputs += builder.build()
        }

        fun numberInput(
            id: String,
            label: Component,
            min: Float,
            max: Float,
            step: Float = 1f,
            initial: Float = min,
            labelFormat: String = "%s",
        ): Builder = apply {
            inputs += DialogInput.numberRange(id, label, min, max)
                .step(step)
                .initial(initial)
                .labelFormat(labelFormat)
                .build()
        }

        fun booleanInput(id: String, label: Component, initial: Boolean = false): Builder = apply {
            inputs += DialogInput.bool(id, label).initial(initial).build()
        }

        fun singleOption(
            id: String,
            label: Component,
            options: List<SingleOptionDialogInput.OptionEntry>,
        ): Builder = apply {
            inputs += DialogInput.singleOption(id, label, options).build()
        }

        fun showNotice(
            player: Player,
            okLabel: Component,
            onOk: (io.papermc.paper.dialog.DialogResponseView) -> Unit,
        ) {
            val button = ActionButton.builder(okLabel)
                .action(DialogAction.customClick(
                    { view, _ -> onOk(view) },
                    ClickCallback.Options.builder().uses(1).build(),
                ))
                .build()
            val dialog = Dialog.create { factory ->
                factory.empty().base(buildBase()).type(DialogType.notice(button))
            }
            player.showDialog(dialog)
        }

        fun showConfirmation(
            player: Player,
            confirmLabel: Component,
            cancelLabel: Component,
            onConfirm: (io.papermc.paper.dialog.DialogResponseView) -> Unit,
            onCancel: ((io.papermc.paper.dialog.DialogResponseView) -> Unit)? = null,
        ) {
            val confirmButton = ActionButton.builder(confirmLabel)
                .action(DialogAction.customClick(
                    { view, _ -> onConfirm(view) },
                    ClickCallback.Options.builder().uses(1).build(),
                ))
                .build()
            val cancelButton = ActionButton.builder(cancelLabel)
                .action(if (onCancel != null) DialogAction.customClick(
                    { view, _ -> onCancel(view) },
                    ClickCallback.Options.builder().uses(1).build(),
                ) else null)
                .build()
            val dialog = Dialog.create { factory ->
                factory.empty().base(buildBase())
                    .type(DialogType.confirmation(confirmButton, cancelButton))
            }
            player.showDialog(dialog)
        }

        fun showMultiAction(player: Player, actions: List<Action>) {
            val buttons = actions.map { a ->
                ActionButton.builder(a.label)
                    .let { b -> if (a.tooltip != null) b.tooltip(a.tooltip) else b }
                    .action(DialogAction.customClick(
                        { view, _ -> a.handler(view) },
                        ClickCallback.Options.builder().uses(1).build(),
                    ))
                    .build()
            }
            val dialog = Dialog.create { factory ->
                factory.empty().base(buildBase()).type(DialogType.multiAction(buttons).build())
            }
            player.showDialog(dialog)
        }

        private fun buildBase(): DialogBase =
            DialogBase.builder(title)
                .canCloseWithEscape(canCloseWithEscape)
                .body(body.toList())
                .inputs(inputs.toList())
                .build()
    }

    companion object {
        /**
         * True when Paper's Dialog API is on the classpath. Checked once at enable; gates
         * whether the Dialog renderer (which references these classes) is ever loaded.
         */
        fun isAvailable(): Boolean =
            runCatching { Class.forName("io.papermc.paper.dialog.Dialog") }.isSuccess
    }
}

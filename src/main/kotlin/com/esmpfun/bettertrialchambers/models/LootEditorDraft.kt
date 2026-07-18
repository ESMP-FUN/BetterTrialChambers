package com.esmpfun.bettertrialchambers.models

/**
 * Mutable working copy of a loot table (or one pool of a multi-pool table) edited
 * inside the GUI. Lives across screens — the loot editor renders it, the amount
 * editor mutates a single item in it, and `MenuService` persists it across menu
 * navigation under a stable per-table-and-pool key so unsaved changes survive
 * back/forward clicks.
 *
 * Extracted from `LootEditorView.Draft` in v1.3.0 Phase 3 so amount/loot editors
 * and `MenuService` no longer reach into the view class for a shared data type.
 *
 * @property tableName  Target loot-table name. For chamber-specific edits this
 *                      is `chamber-<name>` or `ominous-<name>`; for global edits
 *                      it's the table name passed to the editor.
 * @property guaranteed Items that always drop in this pool/table.
 * @property weighted   Weighted-roll items. Their `weight` field is the relative
 *                      probability for a single roll.
 * @property minRolls   Minimum number of weighted rolls per vault open (WEIGHTED mode).
 * @property maxRolls   Maximum number of weighted rolls per vault open (WEIGHTED mode).
 * @property rollMode   How weighted items are drawn — see [LootRollMode]. In
 *                      INDEPENDENT mode each item's `weight` is read as its own
 *                      0-100% drop chance and min/max rolls don't apply.
 * @property maxItems   INDEPENDENT-mode cap on how many passing items to keep
 *                      per opening (0 = uncapped). Ignored in WEIGHTED mode.
 * @property dirty      Set whenever the draft is mutated through any editor;
 *                      used by the loot editor to decorate the Save button and
 *                      by the close handler to skip the auto-save when the user
 *                      explicitly discarded.
 */
data class LootEditorDraft(
    var tableName: String,
    val guaranteed: MutableList<LootItem>,
    val weighted: MutableList<LootItem>,
    var minRolls: Int,
    var maxRolls: Int,
    var rollMode: LootRollMode = LootRollMode.WEIGHTED,
    var maxItems: Int = 0,
    var dirty: Boolean = false
)

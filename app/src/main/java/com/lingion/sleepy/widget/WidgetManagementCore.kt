package com.lingion.sleepy.widget

/**
 * Pure-JVM core of the "manage widgets" screen — issue #24 Feature 1 (R1).
 *
 * One row per placed widget instance across all 10 variants. The Android
 * [WidgetManagementViewModel] supplies three lookup lambdas (system id
 * enumeration via AppWidgetManager, per-id binding via WidgetBindingStore,
 * per-table display name via the repository); every branching decision
 * lives here so it is unit-testable without Android.
 */
internal object WidgetManagementCore {

    /**
     * Build one row per placed widget id, in metadata order then id order.
     *
     * - [idsFor] enumerates the placed ids of one variant (system truth).
     * - [bindingFor] reads the per-widget binding; `null` = no entry.
     * - [tableNameFor] resolves the bound table's display name; returning
     *   `null` covers both "no explicit binding" (sentinel 0L translated by
     *   the caller) and "bound table deleted" (lazy invalidation) — the row
     *   renders as follow-default either way, which matches render-time
     *   behavior in [WidgetTableResolver].
     */
    fun buildPlacedItems(
        variants: List<WidgetVariantInfo>,
        idsFor: (WidgetVariantInfo) -> IntArray,
        bindingFor: (Int) -> Long?,
        tableNameFor: (Long) -> String?
    ): List<PlacedWidgetItem> = buildList {
        for (variant in variants) {
            for (id in idsFor(variant)) {
                val tableName = bindingFor(id)?.let(tableNameFor)
                add(PlacedWidgetItem(id, variant, tableName))
            }
        }
    }
}

package com.lingion.sleepy.ui.screen.widget

import androidx.compose.runtime.Composable
import com.lingion.sleepy.data.entity.TimeTableEntity

/**
 * Per-widget edit state, passed to every [WidgetEditSection].
 *
 * [onSelectTable] receives `null` to clear the binding (revert to the
 * app-wide default). The ViewModel is responsible for writing the change
 * to [com.lingion.sleepy.widget.WidgetBindingStore] and asking
 * [com.lingion.sleepy.widget.WidgetUpdater] to refresh.
 */
data class WidgetEditScope(
    val widgetId: Int,
    val currentBinding: Long?,
    val availableTables: List<TimeTableEntity>,
    val onSelectTable: (Long?) -> Unit,
    /** issue#26: 全部小组件共享一档 — widget 场景 课程名显示 原名/别名 */
    val useAlias: Boolean = false,
    val onUseAliasChange: (Boolean) -> Unit = {}
)

/**
 * A single "section" inside [com.lingion.sleepy.ui.screen.widget.WidgetEditScreen].
 *
 * Modeled as a sealed interface so adding a new section (e.g. theme, time
 * format) is one new file + one entry in the screen's `sections` list —
 * no edits to the screen, ViewModel, or storage layer.
 *
 * Sections must be stateless; the screen owns the ViewModel-derived
 * [WidgetEditScope] and threads it through.
 */
sealed interface WidgetEditSection {
    /** Title resource shown above the section body. */
    val titleRes: Int

    /** Render the section body. */
    @Composable
    fun Content(scope: WidgetEditScope)
}

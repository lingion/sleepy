package com.lingion.sleepy.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Read model for a single widget instance's edit screen.
 *
 * - [currentBinding]: `null` means "follow the app-wide default table"
 *   (i.e. [WidgetTableResolver.resolveCurrentTable]); a Long means the
 *   user has explicitly picked this schedule for this widget.
 * - [availableTables]: schedules with at least one course (binding to an
 *   empty table would render the "请先创建课表" state).
 */
data class WidgetEditUiState(
    val currentBinding: Long? = null,
    val availableTables: List<TimeTableEntity> = emptyList()
)

/**
 * Per-widget edit ViewModel. All non-trivial logic is delegated to
 * [WidgetEditCore] (pure-JVM, unit-tested in [WidgetEditCoreTest]).
 *
 * Context is resolved lazily via [SleepyApp.get] so the VM can be
 * instantiated from a Compose `remember { WidgetEditViewModel(id) }`
 * without an explicit Context parameter at the call site — same shape as
 * other VMs in this codebase.
 */
class WidgetEditViewModel(
    val widgetId: Int
) : ViewModel() {

    private val ctx: Context get() = SleepyApp.get()
    private val repo get() = SleepyApp.get().repository

    private val _state = MutableStateFlow(WidgetEditUiState())
    val state: StateFlow<WidgetEditUiState> = _state.asStateFlow()

    init {
        reload()
    }

    /** Re-read binding + non-empty tables from store and repo. */
    fun reload() {
        viewModelScope.launch {
            val all = repo.getAllTables()
            // repo.countCourses is suspend, but WidgetEditCore.filterAvailableTables
            // takes a plain (Long) -> Int so it stays pure-JVM testable. Pre-compute
            // the counts map here so the core lambda is non-suspending.
            val counts: Map<Long, Int> = all.associate { tableId ->
                tableId.id to runCatching { repo.countCourses(tableId.id) }.getOrDefault(0)
            }
            val available = WidgetEditCore.filterAvailableTables(all) { counts[it] ?: 0 }
            // Sentinel (0L) means "follow default" — translated to null so the
            // UI shows the "Default" row as selected (same as no binding).
            val raw = WidgetBindingStore.get(ctx, widgetId)
            _state.value = WidgetEditUiState(
                currentBinding = WidgetEditCore.displayBinding(raw),
                availableTables = available
            )
        }
    }

    /**
     * Persist a binding change. `null` clears the binding so the widget
     * falls back to [WidgetTableResolver.resolveCurrentTable]. After
     * writing, ask [WidgetUpdater] to refresh every widget receiver so the
     * change is reflected on the home screen immediately.
     */
    fun setBinding(tableId: Long?) {
        if (tableId == null) WidgetBindingStore.remove(ctx, widgetId)
        else WidgetBindingStore.put(ctx, widgetId, tableId)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }
}

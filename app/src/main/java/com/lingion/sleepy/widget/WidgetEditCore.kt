package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.TimeTableEntity

/**
 * Pure-JVM core of the widget edit screen. Mirrors [WidgetBindingCore]:
 * shape and ordering live here so they can be unit-tested without Android
 * or Robolectric. The Android [WidgetEditViewModel] facade composes this
 * core with [WidgetBindingStore] (SharedPreferences) and [WidgetUpdater]
 * (broadcast to receivers).
 *
 * Design notes:
 * - [filterAvailableTables] takes a `countCourses: (Long) -> Int` so the
 *   caller can wrap a `suspend fun` (Room DAO) without forcing coroutine
 *   plumbing into the core.
 * - [initialBinding] and [applyBindingChange] are thin re-exports of
 *   [WidgetBindingCore] so the ViewModel has one cohesive surface to test
 *   against, and so the edit-screen semantics live next to each other
 *   rather than scattered between two files.
 */
internal object WidgetEditCore {

    /**
     * Filter to tables that have at least one course. An empty table is
     * useless to bind to (the widget would render "请先创建课表"), so we
     * hide it from the picker. Input order is preserved — the picker shows
     * tables in the same order the rest of the app does.
     */
    fun filterAvailableTables(
        all: List<TimeTableEntity>,
        countCourses: (Long) -> Int
    ): List<TimeTableEntity> = all.filter { countCourses(it.id) > 0 }

    /** Read the current binding for [widgetId] from the raw prefs map. */
    fun initialBinding(raw: Map<String, Long>, widgetId: Int): Long? =
        WidgetBindingCore.read(raw, widgetId)

    /**
     * Apply a binding change in pure data. `null` means "remove" (revert
     * to the app-wide default table). The Android facade is responsible
     * for persisting the resulting map back to SharedPreferences.
     */
    fun applyBindingChange(
        raw: MutableMap<String, Long>,
        widgetId: Int,
        newTableId: Long?
    ) {
        if (newTableId == null) WidgetBindingCore.delete(raw, widgetId)
        else WidgetBindingCore.write(raw, widgetId, newTableId)
    }
}

package com.lingion.sleepy.ui.nav

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

/** Stable, typed keys for the app's single navigation stack. */
@Serializable
sealed interface SleepyRoute : NavKey {
    @Serializable data object Main : SleepyRoute
    @Serializable data class AddCourse(val courseId: Long = NO_ID, val editing: Boolean = false) : SleepyRoute
    @Serializable data object AllTables : SleepyRoute
    @Serializable data class EditTable(
        val tableId: Long = NO_ID,
        val pendingNew: Long = NO_ID,
        val prevDefault: Long = NO_ID,
    ) : SleepyRoute
    @Serializable data object Appearance : SleepyRoute
    @Serializable data object General : SleepyRoute
    @Serializable data object Holiday : SleepyRoute
    @Serializable data object Export : SleepyRoute
    @Serializable data object Reminder : SleepyRoute
    @Serializable data object About : SleepyRoute
    @Serializable data object License : SleepyRoute
    @Serializable data object WidgetManagement : SleepyRoute
    @Serializable data class WidgetEdit(val widgetId: Int) : SleepyRoute
    @Serializable data object PeriodTables : SleepyRoute
    @Serializable data class PeriodEdit(val periodId: Long, val isNew: Boolean = false) : SleepyRoute

    companion object { const val NO_ID = -1L }
}

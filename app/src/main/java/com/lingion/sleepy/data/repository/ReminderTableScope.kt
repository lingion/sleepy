package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.TimeTableEntity

/** Resolve switch values without changing stored defaults or timetable metadata. */
internal fun resolveReminderTableScope(
    tables: List<TimeTableEntity>,
    selectedTableId: Long?,
): List<TimeTableEntity> {
    val customized = tables.any { it.reminderOverride != null }
    val selected = tables.firstOrNull { it.id == selectedTableId }
        ?: tables.firstOrNull { it.isDefault }
        ?: tables.firstOrNull()
    return tables.map { table ->
        val enabled = if (customized) table.reminderOverride == true
            else table.id == selected?.id && table.reminderEnabled
        table.copy(reminderEnabled = enabled)
    }
}

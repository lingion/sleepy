package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.TimeTableEntity

/** Honor a newly committed default (including imports from another activity). */
internal fun resolveSelectedTableId(
    tables: List<TimeTableEntity>,
    selectedTableId: Long?,
    previousDefaultTableId: Long?,
    preserveSelection: Boolean,
): Long? {
    val defaultId = tables.find { it.isDefault }?.id
    if (defaultId != null && defaultId != previousDefaultTableId) return defaultId
    // While a manual selection is being saved, unrelated table emissions must not undo it.
    if (preserveSelection && tables.any { it.id == selectedTableId }) return selectedTableId
    return defaultId ?: tables.firstOrNull()?.id
}

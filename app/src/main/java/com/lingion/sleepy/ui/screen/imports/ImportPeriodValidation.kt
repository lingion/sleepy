package com.lingion.sleepy.ui.screen.imports

import android.content.Context
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils

internal fun importPeriodValidationError(
    context: Context,
    courses: List<CourseEntity>,
    rows: List<TimeTableUtils.TimeSlotRow>,
): String? {
    TimeTableUtils.firstMissingCourseNode(courses, rows)?.let {
        return context.getString(R.string.slot_time_required, it)
    }
    rows.firstOrNull { it.start.isBlank() || it.end.isBlank() }?.let {
        return context.getString(R.string.slot_time_required, it.node)
    }
    val validation = TimeTableUtils.validatePeriodRows(rows)
    return if (validation is TimeTableUtils.PeriodValidationResult.Valid) null else validation.errorMessage(context)
}

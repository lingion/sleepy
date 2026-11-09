package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalTime

internal data class DailyTableCourses(val table: TimeTableEntity, val courses: List<CourseEntity>)

internal data class DailyScheduleEntry(
    val table: TimeTableEntity,
    val course: CourseEntity,
    val startTime: LocalTime?
)

internal data class DailyScheduleSummary(
    val entries: List<DailyScheduleEntry>
)

/** Call after reminder, semester and holiday filtering; tables must have hydrated timeJson. */
internal fun buildDailyScheduleSummary(tables: List<DailyTableCourses>): DailyScheduleSummary {
    val entries = tables.flatMap { (table, courses) ->
        val nodeTimes = TimeTableUtils.parseNodes(table.timeJson).associateBy { it.node }
        courses.map { course ->
            val customTime = course.startTime.takeIf { course.ownTime && it.isNotBlank() }
            val start = if (customTime != null) {
                runCatching { LocalTime.parse(customTime) }.getOrNull()
            } else {
                nodeTimes[course.startNode]?.start
            }
            DailyScheduleEntry(table, course, start)
        }
    }.sortedWith(compareBy<DailyScheduleEntry> { it.startTime == null }.thenBy { it.startTime })
    return DailyScheduleSummary(entries)
}

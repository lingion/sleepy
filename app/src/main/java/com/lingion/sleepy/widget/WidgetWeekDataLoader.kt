package com.lingion.sleepy.widget

import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.WeekDisplayContext
import com.lingion.sleepy.util.WeekDisplayResolver
import java.time.LocalDateTime

/** 小部件统一读取课表并计算展示周，避免各 provider 各自判断周末。 */
internal data class WidgetWeekSource(
    val table: TimeTableEntity,
    val courses: List<CourseEntity>,
    val display: WeekDisplayContext
) {
    fun coursesFor(dayOfWeek: Int, week: Int = display.displayWeek): List<CourseEntity> =
        courses.asSequence()
            .filter { it.day == dayOfWeek && it.inWeek(week) }
            .sortedBy { it.startNode }
            .toList()

    fun dateFor(dayOfWeek: Int): java.time.LocalDate =
        DateUtils.dateOfWeek(table.startDate, display.displayWeek, dayOfWeek)
}

internal object WidgetWeekDataLoader {
    suspend fun resolve(appWidgetId: Int, now: LocalDateTime = LocalDateTime.now()): WidgetWeekSource? {
        val app = SleepyApp.get()
        val repo = app.repository
        val table = WidgetTableResolver.resolveBoundTable(appWidgetId)
            ?: WidgetTableResolver.resolveCurrentTable()
            ?: return null
        val courses = repo.getCourses(table.id)
        val display = WeekDisplayResolver.resolve(
            startDate = table.startDate,
            maxWeek = table.maxWeek,
            now = now,
            courses = courses,
            timeJson = table.timeJson,
            enabled = AppPrefs.isAutoNextWeek(app)
        )
        return WidgetWeekSource(table, courses, display)
    }
}

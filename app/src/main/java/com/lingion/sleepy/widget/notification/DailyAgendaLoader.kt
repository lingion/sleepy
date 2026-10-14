package com.lingion.sleepy.widget.notification

import android.content.Context
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import java.time.LocalDate

internal data class LoadedDailyAgenda(val agenda: DailyAgenda, val suppressed: Boolean)

/** Shared fresh read for notifications and their destination; no serialized course snapshot. */
internal suspend fun loadDailyAgenda(context: Context, targetDate: LocalDate): LoadedDailyAgenda {
    val app = context.applicationContext
    // 每表独立提醒: 聚合所有开启提醒的课表; 若全部被节假日策略豁免则整体静默
    val repo = SleepyApp.get().repository
    val tables = repo.getAllTables().map { t ->
        t.hydratedWith(t.periodTableId?.let { id -> runCatching { repo.getPeriodTable(id) }.getOrNull() })
    }
    val enabledTables = tables.filter { it.reminderEnabled }
    val activeTables = enabledTables.filter {
        com.lingion.sleepy.util.HolidayReminderPolicyAdapter
            .decide(app, targetDate, it.id)
            .allowReminder
    }
    val suppressed = enabledTables.isNotEmpty() && activeTables.isEmpty()

    val summaries = activeTables.map { table ->
        // 取课走反向查: 补班日=sourceDate 的课, 放假日=null(无课)
        val sourceDate = com.lingion.sleepy.widget.HolidayTransferHelper.courseDateForDate(app, table.id, targetDate)
        val week = DateUtils.currentWeek(table.startDate, sourceDate ?: targetDate)
        val inSemester = DateUtils.semesterStatus(table.startDate, table.maxWeek, sourceDate ?: targetDate) ==
            DateUtils.SemesterStatus.IN_RANGE
        val courses = if (!inSemester || sourceDate == null) {
            emptyList()
        } else {
            repo.getCoursesByDayOnce(table.id, sourceDate.dayOfWeek.value)
                .filter { it.inWeek(week) }
        }
        DailyTableCourses(table, courses)
    }
    return LoadedDailyAgenda(buildDailyAgenda(targetDate, buildDailyScheduleSummary(summaries)), suppressed)

}

package com.lingion.sleepy.widget

import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import java.time.LocalDate

/**
 * 最小档三天窗口数据构建 (2026-09-15 用户令) — 「本周课表（列表/周视图）· 小」专用。
 *
 * 窗口 = compactWindowDates 的三天真实日期; 每列按【该日期所在周】的周次过滤课程,
 * 因此窗口可以越出本周: 周一「今日居第二位」= 上周日(上周周次)/周一/周二(下周周次)。
 * 学期后日期课程清空(与整周口径一致); 学期前钳制到第 1 周(预习口径, currentWeek 自带)。
 */
internal object WidgetCompactWindow {

    suspend fun build(
        repo: ScheduleRepository,
        tableId: Long,
        timeJson: String,
        startDate: String,
        maxWeek: Int,
        today: LocalDate,
        todayFirst: Boolean,
        displayDate: LocalDate? = null,
    ): List<DayData> {
        val anchorDate = displayDate ?: today
        val dates = WidgetBitmapRenderers.compactWindowDates(today, todayFirst, anchorDate)
        val rows = repo.getCourses(tableId)
        return dates.map { effectiveDate ->
            val sourceDate = HolidayTransferHelper.courseDateForDate(
                com.lingion.sleepy.SleepyApp.get(), tableId, effectiveDate
            )
            val week = DateUtils.currentWeek(startDate, sourceDate ?: effectiveDate)
            val visible = if (sourceDate == effectiveDate && DateUtils.semesterStatus(startDate, maxWeek, effectiveDate) == DateUtils.SemesterStatus.BEFORE_START) {
                rows.filter { it.fixedDate == null && it.day == effectiveDate.dayOfWeek.value && it.inWeek(week) }
            } else com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.coursesForDate(
                effectiveDate, startDate, maxWeek, rows,
                com.lingion.sleepy.util.AppPrefs.getHolidayTransfers(com.lingion.sleepy.SleepyApp.get(), tableId)
            ).sortedBy { it.startNode }
            DayData(
                date = effectiveDate,
                dayOfWeek = effectiveDate.dayOfWeek.value,
                courses = visible,
                timeJson = timeJson,
                isGrey = HolidayManager.shouldGrey(com.lingion.sleepy.SleepyApp.get(), effectiveDate, tableId)
            )
        }
    }
}

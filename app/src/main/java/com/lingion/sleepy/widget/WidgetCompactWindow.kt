package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.util.DateUtils
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
        context: Context,
        repo: ScheduleRepository,
        tableId: Long,
        timeJson: String,
        startDate: String,
        maxWeek: Int,
        today: LocalDate,
        todayFirst: Boolean,
    ): List<DayData> = WidgetBitmapRenderers.compactWindowDates(today, todayFirst).map { date ->
        // issue#44: 调休映射后取课
        val dow = MakeupCourseDayHelper.effectiveDayOfWeek(context, tableId, date)
        val week = DateUtils.currentWeek(startDate, date)
        val afterEnd = DateUtils.semesterStatus(startDate, maxWeek, date) == DateUtils.SemesterStatus.AFTER_END
        val visible = if (afterEnd) emptyList()
            else repo.getCoursesByDayOnce(tableId, dow).filter { it.inWeek(week) }.sortedBy { it.startNode }
        DayData(date = date, dayOfWeek = dow, courses = visible, timeJson = timeJson)
    }
}

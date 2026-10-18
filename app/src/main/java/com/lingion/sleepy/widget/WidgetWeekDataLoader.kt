package com.lingion.sleepy.widget

import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseHueStore
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.WeekDisplayContext
import com.lingion.sleepy.util.WeekDisplayResolver
import java.time.LocalDateTime

/**
 * 小部件统一读取课表并计算展示日，避免各 provider 各自判定"今天之后最近一节有课的天"。
 *
 * v2 (用户 2026-09-20 改向):
 *   - [display.targetDate] 是 widget 应该呈现的"今天"（可能是被自动跳到的那一天）。
 *   - [dateFor(dayOfWeek)] 给的是展示周里 dayOfWeek 的真实日期（基于 targetWeek 计算）。
 */
internal data class WidgetWeekSource(
    val table: TimeTableEntity,
    val courses: List<CourseEntity>,
    val display: WeekDisplayContext,
    val transfers: List<com.lingion.sleepy.util.HolidayTransferEntry> = emptyList()
) {
    fun coursesOn(date: java.time.LocalDate, previewBeforeStart: Boolean = false): List<CourseEntity> {
        if (previewBeforeStart && display.semesterStatus == DateUtils.SemesterStatus.AFTER_END &&
            transfers.none { it.targetDate == date }) return emptyList()
        if (previewBeforeStart && transfers.none { it.sourceDate == date || it.targetDate == date } &&
            DateUtils.semesterStatus(table.startDate, table.maxWeek, date) == DateUtils.SemesterStatus.BEFORE_START) {
            return coursesFor(date.dayOfWeek.value, 1)
        }
        return com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.coursesForDate(
            date, table.startDate, table.maxWeek, courses, transfers
        ).sortedBy { it.startNode }
    }

    fun coursesFor(dayOfWeek: Int, week: Int = display.targetWeek): List<CourseEntity> =
        courses.asSequence()
            .filter { it.day == dayOfWeek && it.inWeek(week) }
            .sortedBy { it.startNode }
            .toList()

    fun dateFor(dayOfWeek: Int): java.time.LocalDate =
        if (display.semesterStatus == DateUtils.SemesterStatus.BEFORE_START && display.status == com.lingion.sleepy.util.WeekDisplayStatus.NORMAL)
            DateUtils.dateOfWeek(table.startDate, 1, dayOfWeek)
        else DateUtils.dateOfWeekDay(display.targetDate, dayOfWeek)

    /**
     * 返回 widget 应展示的"今天"语义日期：
     * - NORMAL 档：原 today
     * - NEAREST_BUSY_DAY 档：被自动跳到的最近有课那一天
     */
    fun resolvedToday(now: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate =
        if (display.status == com.lingion.sleepy.util.WeekDisplayStatus.NEAREST_BUSY_DAY)
            display.targetDate
        else now
}

internal object WidgetWeekDataLoader {
    suspend fun resolve(appWidgetId: Int, now: LocalDateTime = LocalDateTime.now()): WidgetWeekSource? {
        val app = SleepyApp.get()
        // spec §2.3 刷新源③: 五个 widget 装载器全部经由本函数 — 单点自刷持久 hue
        // 快照, 保证 Canvas 同步读 hueFor 与实际渲染同帧一致。配色是增值数据,
        // 刷失败仅回落 stableHue, 绝不阻断装载（CourseHueStore 红线）。
        runCatching { CourseHueStore.refresh(app) }
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
            enabled = AppPrefs.isNearestBusyDay(app),
            transfers = AppPrefs.getHolidayTransfers(app, table.id)
        )
        return WidgetWeekSource(table, courses, display, AppPrefs.getHolidayTransfers(app, table.id))
    }
}
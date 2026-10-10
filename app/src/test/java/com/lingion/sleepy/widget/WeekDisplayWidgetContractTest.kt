package com.lingion.sleepy.widget

import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.WeekDisplayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 主界面/小组件共用"最近有课日"契约的纯 JVM 覆盖。 */
class WeekDisplayWidgetContractTest {
    private fun course(day: Int, name: String, startWeek: Int = 1, endWeek: Int = 3) = CourseEntity(
        id = day.toLong(), groupId = "g$day", tableId = 1, courseName = name,
        day = day, startNode = 1, step = 1, startWeek = startWeek, endWeek = endWeek, color = ""
    )

    @Test
    fun `nearest busy day is opt in by default`() {
        assertFalse(AppPrefs.DEFAULT_NEAREST_BUSY_DAY)
    }

    @Test
    fun `resolver selects nearest future class day rather than whole next week`() {
        val context = com.lingion.sleepy.util.WeekDisplayResolver.resolve(
            startDate = "2026-09-14", maxWeek = 3,
            now = LocalDateTime.parse("2026-09-14T23:00"),
            courses = listOf(course(3, "周三课程")),
            timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON,
            enabled = true
        )
        assertEquals(LocalDate.of(2026, 9, 16), context.targetDate)
        assertEquals(1, context.targetWeek)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, context.status)
    }

    @Test
    fun `nearest busy day window returns two future class dates in calendar order`() {
        val dates = com.lingion.sleepy.util.WeekDisplayResolver.findNearestBusyDays(
            startDate = "2026-09-21",
            actualWeek = 1,
            maxWeek = 12,
            today = LocalDate.of(2026, 9, 25),
            courses = listOf(
                course(day = 1, name = "十一月课程", startWeek = 7, endWeek = 7),
                course(day = 3, name = "十二月课程", startWeek = 11, endWeek = 12)
            ),
            count = 2
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 2)),
            dates
        )
    }

    @Test
    fun `small grid mode uses nearest busy day instead of today's weekday`() {
        val target = LocalDate.of(2026, 9, 16)
        val data = WeekData(
            days = listOf(
                DayData(target, 3, listOf(course(3, "周三课程")), ""),
                DayData(target.plusDays(1), 4, emptyList(), "")
            ), hasTable = true,
            weekDisplayStatus = WeekDisplayStatus.NEAREST_BUSY_DAY
        )
        val result = WidgetBitmapRenderers.weekGridMinimumTodayData(data, LocalDate.of(2026, 9, 14))
        assertEquals(target, result.date)
        assertEquals(listOf("周三课程"), result.courses.map { it.courseName })
        assertFalse(result.isToday)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.weekDisplayStatus)
    }

    @Test
    fun `small grid mode picks explicit target date on full-week grid data`() {
        // 整周数据（周一起始）下, minBy=周一 ≠ 跳转目标日(周三);
        // 数据源显式给出 nearestBusyTargetDate 时必须优先于「最早日期」启发式。
        val monday = LocalDate.of(2026, 9, 14)
        val target = LocalDate.of(2026, 9, 16)
        val data = WeekData(
            days = (0..6).map { i ->
                val date = monday.plusDays(i.toLong())
                DayData(date, i + 1, if (date == target) listOf(course(3, "周三课程")) else emptyList(), "")
            },
            hasTable = true,
            weekDisplayStatus = WeekDisplayStatus.NEAREST_BUSY_DAY,
            nearestBusyTargetDate = target
        )
        val result = WidgetBitmapRenderers.weekGridMinimumTodayData(data, monday)
        assertEquals(target, result.date)
        assertEquals(listOf("周三课程"), result.courses.map { it.courseName })
        assertFalse(result.isToday)
    }

    @Test
    fun `week grid data loader reads resolver switch so empty week jumps to busy week`() {
        // 契约: WeekGrid.loadWeekData 必须经 WidgetWeekDataLoader(内含 WeekDisplayResolver)
        // 取数, 使「无课自动跳最近有课日」对周网格生效; 且 NEAREST_BUSY_DAY 档必须
        // 把 targetDate 写入 WeekData 供 SMALL 档定位单日。
        val src = java.io.File(
            "src/main/java/com/lingion/sleepy/widget/WeekGridWidgetProvider.kt"
        ).readText()
        assertTrue(src.contains("WidgetWeekDataLoader.resolve(appWidgetId)"))
        assertTrue(src.contains("weekDisplayStatus = display.status"))
        assertTrue(src.contains("nearestBusyTargetDate = display.targetDate.takeIf"))
    }

    @Test
    fun `today header gives nearest busy day an actionable status title`() {
        val data = WidgetData(
            date = LocalDate.of(2026, 9, 16), courses = emptyList(), timeJson = "",
            hasTable = true, isToday = false, weekDisplayStatus = WeekDisplayStatus.NEAREST_BUSY_DAY
        )
        val parts = WidgetBitmapRenderers.todayHeaderParts(
            data, "周三", true,
            resolve = { id -> when (id) {
                R.string.schedule_nearest_busy_day -> "最近有课的一天"
                R.string.today_nav_back_to_today -> "回到今天"
                else -> "其他"
            } }
        )
        assertTrue(parts.title.startsWith("最近有课的一天"))
        assertEquals("回到今天", parts.rightText)
        assertTrue(parts.rightIsAction)
    }
}

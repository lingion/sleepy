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

/** 主界面/小组件共用展示周契约的纯 JVM 覆盖。 */
class WeekDisplayWidgetContractTest {

    private fun course(day: Int, name: String) = CourseEntity(
        id = day.toLong(),
        groupId = "g$day",
        tableId = 1,
        courseName = name,
        day = day,
        startNode = 1,
        step = 1,
        startWeek = 2,
        endWeek = 3,
        color = ""
    )

    @Test
    fun `auto next week is opt in by default`() {
        assertFalse(AppPrefs.DEFAULT_AUTO_NEXT_WEEK)
    }

    @Test
    fun `manual return to actual week is labelled weekend`() {
        val context = com.lingion.sleepy.util.WeekDisplayResolver.resolve(
            startDate = "2026-09-14",
            maxWeek = 3,
            now = java.time.LocalDateTime.parse("2026-09-19T23:00"),
            courses = emptyList(),
            timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON,
            enabled = true
        )

        assertEquals(WeekDisplayStatus.NEXT_WEEK, context.status)
        assertEquals(
            WeekDisplayStatus.WEEKEND_CURRENT,
            com.lingion.sleepy.util.WeekDisplayResolver.statusForSelectedWeek(context, context.actualWeek)
        )
        assertEquals(
            WeekDisplayStatus.NEXT_WEEK,
            com.lingion.sleepy.util.WeekDisplayResolver.statusForSelectedWeek(context, context.displayWeek)
        )
    }

    @Test
    fun `small grid mode uses next monday data instead of today's weekday`() {
        val monday = LocalDate.of(2026, 9, 21)
        val data = WeekData(
            days = listOf(
                DayData(monday, 1, listOf(course(1, "下周高数")), ""),
                DayData(monday.plusDays(1), 2, emptyList(), "")
            ),
            hasTable = true,
            weekDisplayStatus = WeekDisplayStatus.NEXT_WEEK
        )

        val result = WidgetBitmapRenderers.weekGridMinimumTodayData(
            data,
            today = LocalDate.of(2026, 9, 20)
        )

        assertEquals(monday, result.date)
        assertEquals(listOf("下周高数"), result.courses.map { it.courseName })
        assertFalse(result.isToday)
        assertEquals(WeekDisplayStatus.NEXT_WEEK, result.weekDisplayStatus)
    }

    @Test
    fun `today header gives next week an actionable status title`() {
        val data = WidgetData(
            date = LocalDate.of(2026, 9, 21),
            courses = emptyList(),
            timeJson = "",
            hasTable = true,
            isToday = false,
            weekDisplayStatus = WeekDisplayStatus.NEXT_WEEK
        )
        val parts = WidgetBitmapRenderers.todayHeaderParts(
            data = data,
            dayName = "周一",
            showDate = true,
            resolve = { id ->
                when (id) {
                    R.string.schedule_next_week -> "下周课表"
                    R.string.today_nav_back_to_today -> "回到今天"
                    else -> "其他"
                }
            }
        )

        assertTrue(parts.title.startsWith("下周课表"))
        assertEquals("回到今天", parts.rightText)
        assertTrue(parts.rightIsAction)
    }
}

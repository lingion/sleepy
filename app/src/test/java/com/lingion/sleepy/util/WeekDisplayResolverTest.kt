package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** WeekDisplayResolver 的周末、课程结束和边界行为。 */
class WeekDisplayResolverTest {

    private val startDate = "2026-09-14" // Monday, week 1
    private val timeJson = TimeTableUtils.DEFAULT_TIME_JSON

    private fun course(
        id: Long,
        day: Int,
        startWeek: Int = 1,
        endWeek: Int = 3,
        startTime: String = "08:00",
        endTime: String = "09:00"
    ) = CourseEntity(
        id = id,
        groupId = "g$id",
        tableId = 1,
        courseName = "课程$id",
        day = day,
        startNode = 1,
        step = 1,
        startWeek = startWeek,
        endWeek = endWeek,
        type = 0,
        color = "",
        ownTime = true,
        isIrregularTime = true,
        startTime = startTime,
        endTime = endTime
    )

    private fun resolve(
        dateTime: String,
        courses: List<CourseEntity> = emptyList(),
        maxWeek: Int = 3,
        enabled: Boolean = true
    ) = WeekDisplayResolver.resolve(
        startDate = startDate,
        maxWeek = maxWeek,
        now = LocalDateTime.parse(dateTime),
        courses = courses,
        timeJson = timeJson,
        enabled = enabled
    )

    @Test
    fun `disabled keeps actual week even when current week has ended`() {
        val result = resolve("2026-09-19T23:00", enabled = false)

        assertEquals(1, result.actualWeek)
        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertFalse(result.enabled)
    }

    @Test
    fun `saturday course prevents switching to next week`() {
        val result = resolve(
            "2026-09-19T07:30",
            courses = listOf(course(1, day = 6, startTime = "08:00", endTime = "09:00"))
        )

        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertFalse(result.actualWeekEnded)
    }

    @Test
    fun `saturday without class does not switch if sunday still has class`() {
        val result = resolve(
            "2026-09-19T12:00",
            courses = listOf(course(2, day = 7, startTime = "08:00", endTime = "09:00"))
        )

        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertFalse(result.actualWeekEnded)
    }

    @Test
    fun `sunday before class ends remains on current week`() {
        val result = resolve(
            "2026-09-20T08:30",
            courses = listOf(course(3, day = 7, startTime = "08:00", endTime = "10:00"))
        )

        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
    }

    @Test
    fun `sunday after last class switches to next week`() {
        val result = resolve(
            "2026-09-20T10:01",
            courses = listOf(course(4, day = 7, startTime = "08:00", endTime = "10:00"))
        )

        assertEquals(2, result.displayWeek)
        assertEquals(WeekDisplayStatus.NEXT_WEEK, result.status)
        assertTrue(result.actualWeekEnded)
    }

    @Test
    fun `weekday after last class switches to next week`() {
        val result = resolve(
            "2026-09-18T10:01",
            courses = listOf(course(5, day = 5, startTime = "08:00", endTime = "10:00"))
        )

        assertEquals(2, result.displayWeek)
        assertEquals(WeekDisplayStatus.NEXT_WEEK, result.status)
    }

    @Test
    fun `invalid class time is treated as remaining`() {
        val result = resolve(
            "2026-09-19T23:00",
            courses = listOf(course(6, day = 6, startTime = "not-a-time", endTime = "also-invalid"))
        )

        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertFalse(result.actualWeekEnded)
    }

    @Test
    fun `last semester week never advances beyond max week`() {
        val result = resolve(
            "2026-09-20T23:00",
            courses = emptyList(),
            maxWeek = 1
        )

        assertEquals(1, result.actualWeek)
        assertEquals(1, result.displayWeek)
        assertEquals(WeekDisplayStatus.WEEKEND_CURRENT, result.status)
    }

    @Test
    fun `course outside current week is ignored`() {
        val result = resolve(
            "2026-09-19T23:00",
            courses = listOf(course(7, day = 6, startWeek = 2, endWeek = 3))
        )

        assertEquals(2, result.displayWeek)
        assertEquals(WeekDisplayStatus.NEXT_WEEK, result.status)
    }
}

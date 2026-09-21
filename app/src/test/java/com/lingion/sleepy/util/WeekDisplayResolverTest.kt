package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** WeekDisplayResolver 的"最近有课日"行为契约。 */
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
    fun `disabled keeps today even when nothing left today`() {
        val result = resolve("2026-09-19T23:00", enabled = false)

        assertEquals(1, result.actualWeek)
        assertEquals(LocalDate.of(2026, 9, 19), result.targetDate)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertFalse(result.enabled)
    }

    @Test
    fun `today with class remains today`() {
        val result = resolve(
            "2026-09-14T07:30",
            courses = listOf(course(1, day = 1, startTime = "08:00", endTime = "09:00"))
        )

        assertEquals(LocalDate.of(2026, 9, 14), result.targetDate)
        assertEquals(1, result.targetWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
        assertTrue(result.todayHasRemaining)
    }

    @Test
    fun `today after last class still today if later day has class this week`() {
        // 周一 09:00 上完，周二还有课 → 跳到周二
        val result = resolve(
            "2026-09-14T09:01",
            courses = listOf(
                course(1, day = 1, startTime = "08:00", endTime = "09:00"),
                course(2, day = 2, startTime = "10:00", endTime = "11:00")
            )
        )

        assertEquals(LocalDate.of(2026, 9, 15), result.targetDate)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.status)
    }

    @Test
    fun `saturday with later sunday class jumps to sunday`() {
        val result = resolve(
            "2026-09-19T23:00",
            courses = listOf(course(2, day = 7, startTime = "08:00", endTime = "09:00"))
        )

        assertEquals(LocalDate.of(2026, 9, 20), result.targetDate)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.status)
    }

    @Test
    fun `saturday weekend empty jumps to next week monday`() {
        // 周六周日均无课，最近有课日 = 下周一（第 2 周）
        val result = resolve(
            "2026-09-19T10:01",
            courses = listOf(course(4, day = 1, startWeek = 2, startTime = "08:00", endTime = "10:00"))
        )

        assertEquals(LocalDate.of(2026, 9, 21), result.targetDate)
        assertEquals(2, result.targetWeek)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.status)
    }

    @Test
    fun `three day gap in the middle of week jumps to first class day`() {
        // 周一上完，后面周三才有课 → 跳周三
        val result = resolve(
            "2026-09-14T10:00",
            courses = listOf(
                course(1, day = 1, startTime = "08:00", endTime = "09:30"),
                course(2, day = 3, startTime = "14:00", endTime = "15:30")
            )
        )

        assertEquals(LocalDate.of(2026, 9, 16), result.targetDate)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.status)
    }

    @Test
    fun `no class left until next semester stays today`() {
        val result = resolve(
            "2026-09-20T23:00",
            courses = emptyList(),
            maxWeek = 1
        )

        assertEquals(LocalDate.of(2026, 9, 20), result.targetDate)
        assertEquals(1, result.targetWeek)
        assertEquals(WeekDisplayStatus.NORMAL, result.status)
    }

    @Test
    fun `course outside current week is ignored for today has remaining`() {
        // 当前第 1 周，今天周二无第 1 周课（只有第 2 周的课），找最近日应在第 2 周
        val result = resolve(
            "2026-09-15T23:00",
            courses = listOf(course(7, day = 2, startWeek = 2, endWeek = 3))
        )

        assertEquals(LocalDate.of(2026, 9, 22), result.targetDate)
        assertEquals(2, result.targetWeek)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY, result.status)
    }

    @Test
    fun `invalid end time keeps today as remaining`() {
        val result = resolve(
            "2026-09-14T23:00",
            courses = listOf(course(6, day = 1, startTime = "not-a-time", endTime = "also-invalid"))
        )

        assertEquals(LocalDate.of(2026, 9, 14), result.targetDate)
        assertTrue(result.todayHasRemaining)
    }

    @Test
    fun `statusForSelectedWeek mirrors NEAREST_BUSY_DAY only on target week`() {
        // 周六无课，最近有课日在下周一（第 2 周）；手动选第 1 周(实际周)应回 NORMAL，
        // 只有停留在自动跳到的第 2 周才显示 NEAREST_BUSY_DAY
        val ctx = resolve(
            "2026-09-19T23:00",
            courses = listOf(course(2, day = 1, startWeek = 2, startTime = "14:00", endTime = "15:30"))
        )

        assertEquals(2, ctx.targetWeek)
        assertEquals(WeekDisplayStatus.NEAREST_BUSY_DAY,
            WeekDisplayResolver.statusForSelectedWeek(ctx, ctx.targetWeek))
        assertEquals(WeekDisplayStatus.NORMAL,
            WeekDisplayResolver.statusForSelectedWeek(ctx, ctx.actualWeek))
        assertEquals(WeekDisplayStatus.NORMAL,
            WeekDisplayResolver.statusForSelectedWeek(ctx, 3))
    }
}

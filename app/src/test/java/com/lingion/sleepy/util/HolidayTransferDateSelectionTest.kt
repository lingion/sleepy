package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Complements SourceDateTest with independently discriminating fixtures. */
class HolidayTransferDateSelectionTest {
    private val ops = HolidayRangeOps.HolidayTransferOps
    private fun d(value: String) = LocalDate.parse(value)
    private fun course(id: Long, type: Int = 0, first: Int = 1, last: Int = 20) =
        CourseEntity(id = id, groupId = "g" + id, tableId = 1, courseName = "c" + id, day = 3,
            startNode = 1, step = 1, startWeek = first, endWeek = last, type = type, color = "#123456")
    private fun selected(source: String, target: String, courses: List<CourseEntity>, max: Int = 20) =
        ops.coursesForDate(d(target), "2026-09-07", max, courses,
            listOf(HolidayTransferEntry(d(source), d(target), "test")))

    @Test
    fun parityIsFilteredIndependentlyOfCourseWeekRange() {
        // Both rows span the same range: parity alone distinguishes week 5 from week 6.
        val odd = course(1, type = 1)
        val even = course(2, type = 2)
        val rows = listOf(odd, even)
        val actual = selected("2026-10-07", "2026-10-14", rows)
        assertEquals(listOf(1L), actual.map { it.id })
        assertSame(odd, actual.single())
        assertEquals(listOf(2L), ops.coursesForDate(d("2026-10-14"), "2026-09-07", 20,
            rows, emptyList()).map { it.id })
    }

    @Test
    fun sourceBeforeSemesterCannotLeakClampedWeekOneCourse() {
        // A week-one Wednesday WOULD match if the semester check were removed.
        val firstWeek = course(3, first = 1, last = 1)
        assertTrue(selected("2026-09-02", "2026-09-13", listOf(firstWeek)).isEmpty())
        assertEquals(listOf(3L), selected("2026-09-09", "2026-09-13",
            listOf(firstWeek)).map { it.id })
    }

    @Test
    fun sourceAfterSemesterRejectedEvenWhenCourseWeekRangeWouldMatch() {
        // Course permits week 6, but timetable ends in week 5.
        val sixthWeek = course(4, first = 6, last = 6)
        assertTrue(selected("2026-10-14", "2026-10-11", listOf(sixthWeek), max = 5).isEmpty())
        assertEquals(listOf(4L), selected("2026-10-14", "2026-10-11",
            listOf(sixthWeek), max = 6).map { it.id })
    }

    @Test
    fun courseRangeIsFilteredOnSourceWeekWithoutDependingOnParity() {
        val fifthWeek = course(5, first = 5, last = 5)
        val sixthWeek = course(6, first = 6, last = 6)
        assertEquals(listOf(5L), selected("2026-10-07", "2026-10-14",
            listOf(fifthWeek, sixthWeek)).map { it.id })
    }
}

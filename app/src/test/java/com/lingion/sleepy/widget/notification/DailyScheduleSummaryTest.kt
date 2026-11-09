package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DailyScheduleSummaryTest {
    private fun table(id: Long, name: String, first: String = "08:00") = TimeTableEntity(
        id = id, name = name, startDate = "2026-09-01", createdAt = 0,
        timeJson = """[{"node":1,"start":"$first","end":"10:00"},{"node":2,"start":"14:00","end":"15:00"}]"""
    )

    private fun course(id: Long, tableId: Long, node: Int, custom: String? = null) = CourseEntity(
        id = id, tableId = tableId, groupId = "g$id", courseName = "课$id", room = "101",
        day = 1, startNode = node, step = 1, startWeek = 1, endWeek = 20, color = "#FF6750A4",
        ownTime = custom != null, startTime = custom.orEmpty()
    )

    @Test
    fun interleavedTablesAreSortedByActualTimeRatherThanTableOrNode() {
        val summary = buildDailyScheduleSummary(listOf(
            DailyTableCourses(table(1, "主课表"), listOf(course(1, 1, 1), course(3, 1, 2))),
            DailyTableCourses(table(2, "辅修", "09:00"), listOf(course(2, 2, 1)))
        ))
        assertEquals(listOf(1L, 2L, 3L), summary.entries.map { it.course.id })
        assertEquals(listOf("主课表", "辅修", "主课表"), summary.entries.map { it.table.name })
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(14, 0)),
            summary.entries.map { it.startTime })
    }

    @Test
    fun sourceIsPreservedWhenOnlyOneOfMultipleTablesHasCourses() {
        val summary = buildDailyScheduleSummary(listOf(
            DailyTableCourses(table(1, "主课表"), emptyList()),
            DailyTableCourses(table(2, "辅修"), listOf(course(2, 2, 1).copy(room = "")))
        ))
        assertEquals(table(2, "辅修"), summary.entries.single().table)
        assertEquals("", summary.entries.single().course.room)
    }

    @Test
    fun singleTableChoosesActualFirstCourse() {
        val summary = buildDailyScheduleSummary(listOf(
            DailyTableCourses(table(1, "主课表"), listOf(course(2, 1, 2), course(1, 1, 1)))
        ))
        assertEquals(1L, summary.entries.first().course.id)
        assertEquals(LocalTime.of(8, 0), summary.entries.first().startTime)
    }

    @Test
    fun customStartOverridesNodeAndMissingTimesAreKeptAtTheEnd() {
        val summary = buildDailyScheduleSummary(listOf(DailyTableCourses(table(1, "主课表"), listOf(
            course(1, 1, 99), course(2, 1, 1), course(3, 1, 2, "07:30"),
            course(4, 1, 1, "bad-time"), course(5, 1, 99, "23:59")
        ))))
        assertEquals(listOf(3L, 2L, 5L, 1L, 4L), summary.entries.map { it.course.id })
        assertEquals(LocalTime.of(7, 30), summary.entries.first().startTime)
        assertNull(summary.entries[3].startTime)
        assertNull(summary.entries[4].startTime)
    }

    @Test
    fun invalidTimeTableDoesNotHideCoursesOrPreventCustomTimesFromSorting() {
        val summary = buildDailyScheduleSummary(listOf(DailyTableCourses(
            table(1, "主课表").copy(timeJson = "invalid"),
            listOf(course(1, 1, 1), course(2, 1, 2, "09:15"))
        )))
        assertEquals(listOf(2L, 1L), summary.entries.map { it.course.id })
        assertEquals(2, summary.entries.size)
    }

    @Test
    fun noCoursesReturnsEmptySummary() {
        val summary = buildDailyScheduleSummary(listOf(
            DailyTableCourses(table(1, "主课表"), emptyList()),
            DailyTableCourses(table(2, "辅修"), emptyList())
        ))
        assertTrue(summary.entries.isEmpty())
    }
}

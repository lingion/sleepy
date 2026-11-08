package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.HolidayTransferEntry
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SchedulePresentationCacheTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val course = CourseEntity(
        id = 1, tableId = 1, groupId = "math", courseName = "Math", day = 1,
        startNode = 1, step = 2, startWeek = 1, endWeek = 20, color = "#FF6750A4",
    )
    private val initial = ScheduleState(
        tables = listOf(TimeTableEntity(id = 1, name = "Term", startDate = monday.toString())),
        selectedTableId = 1, courses = listOf(course),
    )

    @Test fun tabRoundTripAndWeekSelectionReuseUnchangedPresentation() {
        val cache = SchedulePresentationCache()
        val week = cache.weekCourses(initial, 1)
        val today = cache.today(initial, monday)
        cache.weekCourses(initial, 2)
        val selected = initial.copy(selectedWeek = 2, selectedCourseId = 1)
        assertSame(week, cache.weekCourses(selected, 1))
        assertSame(today, cache.today(selected, monday))
        assertEquals(listOf(course), today.courses)
        assertEquals(listOf(course), today.groups[course.groupId])
    }

    @Test fun editingCoursesInvalidatesBothPages() {
        val cache = SchedulePresentationCache()
        cache.weekCourses(initial, 1)
        cache.today(initial, monday)
        val edited = initial.copy(courses = listOf(course.copy(courseName = "Physics")))
        assertEquals("Physics", cache.weekCourses(edited, 1).single().courseName)
        assertEquals("Physics", cache.today(edited, monday).courses.single().courseName)
        assertTrue(cache.today(edited.copy(courses = emptyList()), monday).courses.isEmpty())
    }

    @Test fun transferAndFixedDateKeepTheirExistingSemantics() {
        val cache = SchedulePresentationCache()
        val saturday = monday.plusDays(5)
        val fixed = course.copy(id = 2, groupId = "fixed", fixedDate = saturday.toString(), day = 6)
        val moved = initial.copy(
            courses = listOf(course, fixed),
            transfers = listOf(HolidayTransferEntry(sourceDate = monday, targetDate = saturday, segmentId = "holiday")),
        )
        assertTrue(cache.today(moved, monday).courses.isEmpty())
        assertEquals(setOf(1L, 2L), cache.today(moved, saturday).courses.map { it.id }.toSet())
        assertEquals(setOf(6), cache.weekCourses(moved, 1).map { it.day }.toSet())
    }

    @Test fun dateAndTimetableChangesInvalidateResults() {
        val cache = SchedulePresentationCache()
        assertEquals(1, cache.today(initial, monday).courses.size)
        assertTrue(cache.today(initial, monday.plusDays(1)).courses.isEmpty())
        val irregular = initial.copy(courses = listOf(course.copy(ownTime = true, startTime = "08:10", endTime = "08:40")))
        val first = cache.today(irregular, monday)
        val changed = irregular.copy(tables = listOf(initial.tables.single().copy(
            timeJson = TimeTableUtils.DEFAULT_TIME_JSON.replace("\"node\":1,", "\"node\":0,"),
        )))
        assertNotSame(first, cache.today(changed, monday))
        assertEquals(0, cache.today(changed, monday).courses.single().startNode)
    }

    @Test fun weekCacheIsBounded() {
        val cache = SchedulePresentationCache()
        val first = cache.weekCourses(initial, 1)
        (2..20).forEach { cache.weekCourses(initial, it) }
        assertEquals(first, cache.weekCourses(initial, 1))
        assertNotSame(first, cache.weekCourses(initial, 1))
    }

    @Test fun pairedTabRoundTripBenchmark() {
        val state = initial.copy(courses = List(140) { index ->
            course.copy(id = index.toLong() + 1, day = index % 7 + 1,
                groupId = "group${index % 35}", startNode = index % 12 + 1)
        })
        val cache = SchedulePresentationCache()
        val expected = cache.weekCourses(state, 1).size + cache.today(state, monday).courses.size
        fun batch(reuse: Boolean) {
            repeat(30) {
                val presentation = if (reuse) cache else SchedulePresentationCache()
                assertEquals(expected, presentation.weekCourses(state, 1).size + presentation.today(state, monday).courses.size)
            }
        }
        repeat(3) { batch(false); batch(true) }
        val uncached = mutableListOf<Double>()
        val cached = mutableListOf<Double>()
        repeat(7) { sample ->
            val order = if (sample % 2 == 0) listOf(false, true) else listOf(true, false)
            order.forEach { reuse ->
                val duration = kotlin.system.measureNanoTime { batch(reuse) } / 1_000_000.0
                (if (reuse) cached else uncached).add(duration)
            }
        }
        println("PRESENTATION 30 round trips 140 courses uncached=$uncached cached=$cached medians=${uncached.sorted()[3]}/${cached.sorted()[3]} ms")
    }
}

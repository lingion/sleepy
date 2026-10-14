package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyAgendaTest {
    private val date = LocalDate.of(2026, 10, 14)
    private fun entry(id: Long, table: Long = id, start: String = "08:00", end: String = "09:00", teacher: String = "A", room: String = "101") =
        DailyTableCourses(TimeTableEntity(id = table, name = "Table $table", startDate = "2026-09-01"), listOf(
            CourseEntity(id = id, tableId = table, groupId = "g$id", courseName = "Math", day = 1,
                startNode = 1, step = 1, startWeek = 1, endWeek = 20, color = "#FF6750A4",
                teacher = teacher, room = room, ownTime = true, startTime = start, endTime = end)))
    private fun agenda(vararg tables: DailyTableCourses) = buildDailyAgenda(date, buildDailyScheduleSummary(tables.toList()))

    @Test fun duplicateAcrossTablesRetainsEverySourceAndCountsOnce() {
        val result = agenda(entry(1), entry(2), entry(3))
        assertEquals(1, result.items.size)
        assertEquals(3, result.tableCount)
        assertEquals(listOf(1L, 2L, 3L), result.items.single().sources.map { it.table.id })
        assertEquals(date, result.date)
    }
    @Test fun differencesAndUnknownFieldsRemainSeparate() {
        val result = agenda(entry(1), entry(2, end = "09:10"), entry(3, teacher = "B"),
            entry(4, room = "102"), entry(5, end = ""), entry(6, end = ""),
            entry(7, start = "bad"), entry(8, start = "bad"), entry(9, room = ""), entry(10, room = ""))
        assertEquals(10, result.items.size)
    }
    @Test fun sameTableOccurrencesAreNeverCollapsed() {
        assertEquals(2, agenda(entry(1, table = 1), entry(2, table = 1)).items.size)
    }
    @Test fun sameNameAtDifferentTimesRemainsTwoCoursesInTimeOrder() {
        val result = agenda(entry(1, start = "14:00", end = "15:00"), entry(2))
        assertEquals(listOf(2L, 1L), result.items.map { it.first.course.id })
    }
    @Test fun invalidIntervalsNeverMerge() {
        assertEquals(2, agenda(entry(1, end = "07:00"), entry(2, end = "07:00")).items.size)
    }
    @Test fun boundedPreviewReportsExactHiddenCountIncludingSingleTable() {
        val courses = (1L..9).map { entry(it, table = 1).courses.single() }
        val result = agenda(entry(1).copy(courses = courses))
        assertEquals(6, result.preview().first.size)
        assertEquals(3, result.preview().second)
        assertEquals(9, result.items.size)
        assertEquals(0, agenda().preview().second)
    }
    @Test fun periodAndCustomTimesCanDescribeSameOccurrence() {
        val period = entry(1).let { it.copy(table = it.table.copy(timeJson = """[{"node":1,"start":"08:00","end":"09:00"}]"""), courses = it.courses.map { c -> c.copy(ownTime = false) }) }
        assertEquals(1, agenda(period, entry(2)).items.size)
    }
}

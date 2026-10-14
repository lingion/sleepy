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
    private fun agenda(vararg tables: DailyTableCourses) = buildDailyAgenda(date, buildDailyScheduleSummary(tables.toList()), tables.map { it.table })

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

    @Test fun singleTableNotificationShowsOnlyActionableFields() {
        val result = agenda(entry(1))
        assertEquals("08:00 · Math · 101", result.items.single().notificationLine("Unknown", "Unknown", result.notificationSources("Unnamed")))
        assertEquals("08:00–09:00", result.items.single().timeText("Unknown"))
    }
    @Test fun enabledEmptyTableKeepsAttributionButDoesNotInflateContributingCount() {
        val result = agenda(entry(1), entry(2).copy(courses = emptyList()))
        assertEquals(1, result.tableCount)
        assertEquals("08:00 · Math · 101〔Table 1〕", result.items.single().notificationLine("Unknown", "Unknown", result.notificationSources("Unnamed")))
    }
    @Test fun mergedNotificationKeepsEverySource() {
        val result = agenda(entry(1), entry(2))
        assertEquals("08:00 · Math · 101〔Table 1／Table 2〕", result.items.single().notificationLine("Unknown", "Unknown", result.notificationSources("Unnamed")))
    }
    @Test fun duplicateAndBlankTableNamesRemainDistinguishable() {
        val a = entry(1).table.copy(name = "  ")
        val b = entry(2).table.copy(name = "")
        assertEquals(mapOf(1L to "Unnamed #1", 2L to "Unnamed #2"), notificationSourceLabels(listOf(a, b), "Unnamed"))
        assertEquals(mapOf(1L to "主修 #1", 2L to "主修 #2"), notificationSourceLabels(listOf(a.copy(name = "主修"), b.copy(name = "主修")), "Unnamed"))
    }
    @Test fun longSimilarSourceNamesAndCourseQualifiersAreNotTruncated() {
        val a = entry(1).let { it.copy(table = it.table.copy(name = "2026秋季学期主修"), courses = it.courses.map { c -> c.copy(courseName = "大学物理实验Ⅱ") }) }
        val b = entry(2).copy(table = entry(2).table.copy(name = "2026秋季学期辅修"), courses = emptyList())
        val result = agenda(a, b)
        assertEquals("08:00 · 大学物理实验Ⅱ · 101〔2026秋季学期主修〕", result.items.single().notificationLine("Unknown", "Unknown", result.notificationSources("Unnamed")))
    }
    @Test fun malformedTimeAndBlankRoomUseVisibleFallbacks() {
        val result = agenda(entry(1, start = "bad", room = ""))
        assertEquals("时间待定 · Math · 地点待定", result.items.single().notificationLine("时间待定", "地点待定", emptyMap()))
    }

    @Test fun generatedSuffixCannotCollideWithUserName() {
        val tables = listOf(entry(1).table.copy(name = "主修"), entry(2).table.copy(name = "主修"), entry(3).table.copy(name = "主修 #1"))
        val labels = notificationSourceLabels(tables, "Unnamed")
        assertEquals(3, labels.values.toSet().size)
    }
    @Test fun longSourceLabelsPreserveEndsAndDisambiguateOmittedMiddle() {
        val a = entry(1).table.copy(name = "2026秋季学期甲乙丙丁戊己庚辛壬癸主修")
        val b = entry(2).table.copy(name = "2026秋季学期不同中间内容庚辛壬癸主修")
        val labels = notificationSourceLabels(listOf(a, b), "Unnamed")
        assertEquals(2, labels.values.toSet().size)
        assertTrue(labels.getValue(1).startsWith("2026秋季学期"))
        assertTrue(labels.getValue(1).contains("主修"))
        assertTrue(labels.getValue(1).contains("…"))
        val emoji = "😀".repeat(20)
        val shortened = notificationSourceLabels(listOf(a.copy(name = emoji), b), "Unnamed").getValue(1)
        assertEquals(16, shortened.codePointCount(0, shortened.length))
    }
    @Test fun relativeNotificationsExpireAtNextLocalMidnightAcrossMonthAndDst() {
        val zone = java.time.ZoneId.of("America/New_York")
        fun lifetime(date: String) = dailyNotificationLifetime(java.time.LocalDate.parse(date).atStartOfDay(zone))
        assertEquals(23L * 60 * 60 * 1000, lifetime("2026-03-08"))
        assertEquals(25L * 60 * 60 * 1000, lifetime("2026-11-01"))
        assertEquals(24L * 60 * 60 * 1000, lifetime("2026-01-31"))
        assertEquals(1000L, dailyNotificationLifetime(java.time.ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, zone)))
    }
}

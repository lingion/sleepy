package com.lingion.sleepy.util

import com.lingion.sleepy.data.diff.RowKeyDiffer
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec.toEntity
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec.toRow
import com.lingion.sleepy.data.parser.ScheduleExporter
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.parser.SleepyNativeExporter
import com.lingion.sleepy.data.parser.SleepyNativeParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CourseOccurrencesTest {
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", maxWeek = 16)
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Math",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    private val source = LocalDate.parse("2026-10-05")
    private val destination = LocalDate.parse("2026-10-11")
    private val transfers = listOf(HolidayTransferEntry(source, destination, "holiday"))
    private fun on(date: LocalDate, rows: List<CourseEntity>) = HolidayRangeOps.HolidayTransferOps
        .coursesForDate(date, table.startDate, table.maxWeek, rows, transfers)

    @Test fun restoringAnyWeekMergesEveryWeekAndParitySchedulesWithoutChangingOccurrences() {
        for (type in 0..3) {
            val original = course.copy(type = type)
            val weeks = (1..16).filter(original::inWeek)
            for (week in weeks) {
                val date = DateUtils.dateOfWeek(table.startDate, week, original.day)
                val split = CourseOccurrences.without(original, date, table.startDate)
                    .mapIndexed { index, row -> row.copy(id = if (index == 0) original.id else 8) }
                val restored = CourseOccurrences.atDate(original, date, table, false).copy(id = 9)
                val merged = CourseOccurrences.mergeMatchingRows(split + restored).single()
                assertEquals("type=$type, restored week=$week", weeks, (1..16).filter(merged::inWeek))
                assertEquals(original.id, merged.id)
                assertEquals(listOf(merged), CourseOccurrences.mergeMatchingRows(listOf(merged)))
            }
        }
    }

    @Test fun mergingDoesNotFillMissingWeeksOrDiscardDuplicateOccurrences() {
        val before = course.copy(endWeek = 4)
        val after = course.copy(id = 8, startWeek = 6)
        assertEquals(listOf(before, after), CourseOccurrences.mergeMatchingRows(listOf(before, after)))
        val overlapping = course.copy(id = 8, startWeek = 4)
        assertEquals(listOf(before, overlapping), CourseOccurrences.mergeMatchingRows(listOf(before, overlapping)))
    }

    @Test fun anyDifferentContentOrFixedDateKeepsTimeBlocksSeparate() {
        val before = course.copy(endWeek = 4)
        val after = course.copy(id = 8, startWeek = 5)
        val differences = listOf(
            after.copy(teacher = "Other"), after.copy(room = "Other"), after.copy(note = "Other"),
            after.copy(color = "#FFFF0000"), after.copy(colorMode = 2), after.copy(alias = "Other"),
            after.copy(ownTime = true), after.copy(isIrregularTime = true), after.copy(isIrregularNode = true),
            after.copy(startTime = "08:10"), after.copy(endTime = "09:30"),
            after.copy(day = 2), after.copy(startNode = 3), after.copy(step = 1),
            after.copy(courseName = "Other"), after.copy(groupId = "Other"), after.copy(tableId = 2),
            after.copy(credit = 1f), after.copy(level = 1), after.copy(fixedDate = source.toString())
        )
        for (different in differences) {
            assertEquals(listOf(before, different), CourseOccurrences.mergeMatchingRows(listOf(before, different)))
        }
        val fixed = before.copy(fixedDate = source.toString())
        val secondFixed = after.copy(fixedDate = source.toString())
        assertEquals(listOf(fixed, secondFixed), CourseOccurrences.mergeMatchingRows(listOf(fixed, secondFixed)))
    }

    @Test fun removingAnyActualWeekPreservesEveryOtherOccurrenceAndParity() {
        for (type in 0..3) {
            val original = course.copy(type = type)
            val weeks = (1..16).filter(original::inWeek)
            for (week in weeks) {
                val result = CourseOccurrences.without(original, DateUtils.dateOfWeek(table.startDate, week, 1), table.startDate)
                assertEquals(weeks - week, (1..16).filter { w -> result.any { it.inWeek(w) } })
                assertTrue(result.size <= 2)
                assertTrue(result.all { it.type == type && it.step == 2 && it.groupId == original.groupId })
                assertEquals(1, result.count { it.id == original.id })
                assertTrue(result.drop(1).all { it.id == 0L })
            }
        }
    }

    @Test fun removingTheOnlyWeekLeavesNoEmptyRow() {
        assertTrue(CourseOccurrences.without(course.copy(startWeek = 5, endWeek = 5), source, table.startDate).isEmpty())
        val fixed = CourseOccurrences.atDate(course, destination, table, true)
        assertTrue(CourseOccurrences.without(fixed, destination, table.startDate).isEmpty())
    }

    @Test fun deletingTransferredCourseRemovesTheSourceOccurrenceOnly() {
        val selected = CourseOccurrences.resolve(course, destination, transfers)!!
        assertEquals(source, selected.sourceDate)
        val remaining = CourseOccurrences.without(course, selected.sourceDate, table.startDate)
        assertTrue(on(source, remaining).isEmpty())
        assertTrue(on(destination, remaining).isEmpty())
        assertEquals(1, on(source.plusWeeks(1), remaining).size)
    }

    // Compatibility for existing/imported fixed-date records; the direct-scheduling UI now creates ordinary rows.
    @Test fun legacyFixedDateAndTransferredCoursesCoexistAndFixedDateDoesNotMoveAgain() {
        val fixed = CourseOccurrences.atDate(course.copy(id = 8), destination, table, true)
        assertEquals(2, on(destination, listOf(course, fixed)).size)
        assertTrue(on(source, listOf(course, fixed)).isEmpty())
        val chained = transfers + HolidayTransferEntry(destination, destination.plusDays(1), "next")
        val result = HolidayRangeOps.HolidayTransferOps.coursesForDate(destination, table.startDate, 16, listOf(fixed), chained)
        assertEquals(listOf(fixed), result)
    }

    @Test fun sameGroupConflictsAreDetectedAtActualDestination() {
        val draft = CourseOccurrences.atDate(course, source, table, false)
        val conflicts = CourseOccurrences.conflictDetails(listOf(draft), listOf(course), table, transfers,
            arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), "%s %s %s %s")
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.single().contains(destination.toString()))
    }

    @Test fun touchingClockIntervalsDoNotConflictButOverlapDoes() {
        val early = CourseOccurrences.atDate(course.copy(ownTime = true, isIrregularTime = true, startTime = "08:00", endTime = "09:00"), destination, table, true)
        val later = early.copy(startTime = "09:00", endTime = "10:00")
        fun conflicts(row: CourseEntity) = CourseOccurrences.conflictDetails(listOf(row), listOf(early), table, transfers,
            arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), "%s %s %s %s")
        assertTrue(conflicts(later).isEmpty())
        assertEquals(1, conflicts(later.copy(startTime = "08:59")).size)
    }

    @Test fun fixedAndRecurringRowsDoNotCollapseInGroupDiff() {
        val recurring = CourseOccurrences.atDate(course, destination, table, false)
        val fixed = recurring.copy(fixedDate = destination.toString())
        assertEquals(2, RowKeyDiffer.diff(listOf(recurring, fixed), emptyList()).toInsert.size)
    }

    @Test fun nativeAndBackupRoundTripsRetainFixedDate() {
        val fixed = CourseOccurrences.atDate(course, destination, table, true)
        val text = SleepyNativeExporter.exportFile(table.name, table.startDate, table.maxWeek, table.nodesPerDay, table.timeJson, listOf(fixed))
        assertEquals(fixed.fixedDate, SleepyNativeParser.parse(text, 1, course.color).courses.single().fixedDate)
        val backup = MigrationDatabaseCodec.DatabaseSnapshot(courses = listOf(fixed.toRow()))
        assertEquals(fixed, MigrationDatabaseCodec.decode(MigrationDatabaseCodec.encode(backup)).courses.single().toEntity())
        val json = ScheduleExporter.exportWakeUpJson(table, listOf(fixed))
        assertEquals(fixed.fixedDate, ScheduleParser.parse(json, 1).getOrThrow().courses.single().fixedDate)
        val share = ScheduleExporter.exportWakeUpShareText(table, listOf(fixed))
        assertEquals(fixed.fixedDate, ScheduleParser.parse(share, 1).getOrThrow().courses.single().fixedDate)
    }

    @Test fun calendarExportUsesFinalDateWithoutDuplicateIds() {
        val one = course.copy(startWeek = 5, endWeek = 5)
        val fixed = CourseOccurrences.atDate(course.copy(id = 8), destination, table, true).copy(id = 8)
        val text = ScheduleExporter.exportIcs(table, listOf(one, fixed), transfers)
        assertEquals(2, text.lineSequence().count { it.startsWith("DTSTART:20261011T") })
        assertFalse(text.contains("DTSTART:20261005T"))
        assertFalse(text.contains("RRULE:"))
        assertEquals(2, text.lineSequence().filter { it.startsWith("UID:") }.distinct().count())
    }
}

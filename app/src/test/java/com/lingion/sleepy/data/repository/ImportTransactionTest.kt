package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.*
import com.lingion.sleepy.data.parser.ScheduleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportTransactionHelperTest {
    private val base = TimeTableEntity(id = 5, name = "Base", startDate = "2026-09-07",
        maxWeek = 16, isDefault = true, createdAt = 1)
    private val row = CourseEntity(id = 11, tableId = 5, groupId = "g", courseName = "Math",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FFFF0000")
    private val snapshot = ImportSnapshot(listOf(base), emptyList(), listOf(row))
    private val config = ImportConfiguration.forExisting(base).copy(dateInterpretationAcknowledged = true)
    private fun source(course: CourseEntity = row.copy(id = 0, tableId = 0)) =
        ScheduleParser.ParseResult("Imported", base.startDate, listOf(course),
            timeJson = base.timeJson, nodesPerDay = 12, maxWeek = 16)

    @Test fun duplicateOnlyIsActualNoopAndFalseDefaultDoesNotClearNavigation() {
        val plan = planImport(source(), snapshot, config.copy(isDefault = false))
        val writes = checkNotNull(validatedImportWriteSet(plan, snapshot))
        assertFalse(writes.hasChanges)
        assertEquals(base, writes.table)
        assertFalse(writes.setDefault)
    }

    @Test fun splitRetainsOriginalIdAndInsertsRemainingSegment() {
        val plan = planImport(source(row.copy(courseName = "Physics", startWeek = 7, endWeek = 8)),
            snapshot, config.copy(overlaps = OverlapPolicy.ReplaceExisting))
        val writes = checkNotNull(validatedImportWriteSet(plan, snapshot))
        assertEquals(listOf(11L), writes.updateCourses.map { it.id })
        assertEquals(6, writes.updateCourses.single().endWeek)
        assertEquals(2, writes.insertCourses.size)
        assertTrue(writes.insertCourses.all { it.id == 0L })
        assertTrue(writes.deleteCourseIds.isEmpty())
    }

    @Test fun forgedOutputAndClearedBlockingIssuesAreRejected() {
        val plan = planImport(source(row.copy(courseName = "Physics")), snapshot, config)
        assertNull(validatedImportWriteSet(plan.copy(finalCourses = emptyList()), snapshot))
        val invalid = planImport(source(), snapshot, config.copy(name = ""))
        assertNull(validatedImportWriteSet(invalid.copy(issues = emptyList()), snapshot))
    }

    @Test fun canonicalSnapshotIgnoresOrderButComparesEveryColumn() {
        val other = base.copy(id = 6, name = "Other", isDefault = false)
        val one = snapshot.copy(tables = listOf(base, other))
        val two = one.copy(tables = one.tables.reversed())
        assertEquals(canonicalImportSnapshot(one), canonicalImportSnapshot(two))
        assertNotEquals(canonicalImportSnapshot(one), canonicalImportSnapshot(one.copy(
            courses = listOf(row.copy(note = "Concurrent edit")))))
        val plan = planImport(source(row.copy(courseName = "Physics")), two, config)
        assertNotNull(validatedImportWriteSet(plan, canonicalImportSnapshot(one)))
    }

    @Test fun refreshFailureIsWarningAndLaterStagesStillRun() = runBlocking {
        var laterRan = false
        val warnings = importPostCommitWarnings(
            "cleanup" to { throw IllegalStateException("unavailable") },
            "refresh" to { laterRan = true }
        )
        assertEquals(listOf("cleanup: unavailable"), warnings)
        assertTrue(laterRan)
    }

    @Test fun cancellationIsNeverConvertedToWarning() = runBlocking {
        try {
            importPostCommitWarnings("refresh" to { throw CancellationException("cancelled") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected; callers must not receive Failed after a committed write.
        }
    }
}

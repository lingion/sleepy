package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OccurrenceSaveDialogsTest {
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", maxWeek = 16)
    private val course = CourseEntity(tableId = 1, groupId = "math", courseName = "Math", day = 7,
        startNode = 1, step = 2, startWeek = 5, endWeek = 5, color = "#FF6750A4")
    private val source = LocalDate.parse("2026-10-05")
    private val target = LocalDate.parse("2026-10-11")
    private val transfers = listOf(HolidayTransferEntry(source, target, "holiday"))

    @Test fun targetDefaultsToReverseLookup() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course), table, transfers) }
        assertFalse(job.isCompleted)
        assertEquals(2, dialogs.question!!.choices.size)
        assertEquals(R.string.occurrence_follow_transfer.toString(), dialogs.question!!.choices.first())
        dialogs.answer(0)
        val row = job.await()!!.single()
        assertNull(row.fixedDate)
        assertEquals(1, row.day)
    }

    @Test fun directChoiceNeedsSecondConfirmationAndIsActuallyCovered() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course), table, transfers) }
        dialogs.answer(1)
        yield()
        assertFalse(job.isCompleted)
        assertEquals(R.string.occurrence_overwritten_title.toString(), dialogs.question!!.title)
        assertTrue(dialogs.question!!.destructive)
        assertEquals(listOf(R.string.occurrence_direct_anyway.toString()), dialogs.question!!.choices)
        dialogs.answer(0)
        val saved = job.await()!!.single()
        assertEquals(target.dayOfWeek.value, saved.day)
        assertNull(saved.fixedDate)
        assertTrue(HolidayRangeOps.HolidayTransferOps.coursesForDate(
            target, table.startDate, table.maxWeek, listOf(saved), transfers
        ).isEmpty())
        assertEquals(listOf(saved), HolidayRangeOps.HolidayTransferOps.coursesForDate(
            target, table.startDate, table.maxWeek, listOf(saved), emptyList()
        ))
    }

    @Test fun cancellingSecondWarningAbortsTheWholeSave() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            dialogs.destinations(listOf(course.copy(day = 2), course), table, transfers)
        }
        dialogs.answer(1)
        yield()
        assertEquals(R.string.occurrence_overwritten_title.toString(), dialogs.question!!.title)
        dialogs.answer(null)
        assertNull(job.await())
    }

    @Test fun sourceWarningCanBeCancelledWithoutProducingASave() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course.copy(day = 1)), table, transfers) }
        assertNotNull(dialogs.question)
        dialogs.answer(null)
        assertNull(job.await())
    }

    @Test fun repeatedDraftOnlySplitsTheAffectedWeek() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course.copy(startWeek = 1, endWeek = 16)), table, transfers) }
        dialogs.answer(0)
        val rows = job.await()!!
        assertEquals(3, rows.size)
        assertEquals(15, (1..16).count { week -> rows.any { it.day == 7 && it.inWeek(week) } })
        assertEquals(1, rows.count { it.day == source.dayOfWeek.value && it.startWeek == 5 && it.endWeek == 5 })
    }

    @Test fun cancellingSaveCoroutineClosesPendingDialog() = runBlocking {
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course), table, transfers) }
        job.cancelAndJoin()
        assertNull(dialogs.question)
    }

    @Test fun defaultLookupUsesTheSourceWeekAndReplacesTheTargetsRegularCourses() = runBlocking {
        val crossWeekSource = source.minusWeeks(1)
        val mapping = listOf(HolidayTransferEntry(crossWeekSource, target, "holiday"))
        val dialogs = OccurrenceSaveDialogs { id, _ -> id.toString() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { dialogs.destinations(listOf(course), table, mapping) }
        dialogs.answer(0)
        val saved = job.await()!!.single().copy(id = 2)
        assertEquals(4, saved.startWeek)
        assertEquals(4, saved.endWeek)
        assertEquals(crossWeekSource.dayOfWeek.value, saved.day)
        assertNull(saved.fixedDate)
        val originalTargetCourse = course.copy(id = 1, courseName = "Original Sunday class")
        assertEquals(listOf(saved), HolidayRangeOps.HolidayTransferOps.coursesForDate(
            target, table.startDate, table.maxWeek, listOf(originalTargetCourse, saved), mapping
        ))
    }
}

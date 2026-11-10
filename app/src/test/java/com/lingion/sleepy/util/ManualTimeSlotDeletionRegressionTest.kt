package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.repository.remapRenumberedEdgeCourses
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualTimeSlotDeletionRegressionTest {
    @Test
    fun `deleting then appending with a before edge leaves a valid unique node range`() {
        val before = TimeSlotRow(0, "06:00", "06:30", EdgeClass.Before)
        val standard = listOf(
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "10:00", "10:45")
        )
        val original = TimeTableUtils.parseTimeSlotRows(
            TimeTableUtils.insertEdgeNode(
                TimeTableUtils.buildTimeJsonFromRows(standard), EdgeClass.Before, before.start, before.end
            )
        )
        assertEquals(listOf(1, 2, 0), original.map { it.node })

        val deleted = TimeTableUtils.removeAndRenumber(original, 2)
        assertEquals(listOf(before, standard[0]), deleted)

        val appended = TimeTableUtils.appendEmptyRow(deleted)
        val complete = TimeTableUtils.updateManualRowTime(appended, appended.last().node, "09:00", "09:45")

        assertEquals(listOf(0, 1, 2), complete.map { it.node })
        assertEquals(before, complete.first())
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(complete))
        assertEquals(complete, TimeTableUtils.parseTimeSlotRows(TimeTableUtils.buildTimeJsonFromRows(complete)))
    }

    @Test
    fun `deleting a standard keeps before identities and shifts after courses together`() {
        val original = listOf(
            TimeSlotRow(-1, "06:00", "06:30", EdgeClass.Before),
            TimeSlotRow(0, "07:00", "07:30", EdgeClass.Before),
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "10:00", "10:45"),
            TimeSlotRow(3, "12:00", "12:45"),
            TimeSlotRow(4, "20:00", "20:30", EdgeClass.After),
            TimeSlotRow(5, "21:00", "21:30", EdgeClass.After)
        )
        val deleted = TimeTableUtils.removeAndRenumber(original, 2)

        assertEquals(listOf(-1, 0, 1, 2, 3, 4), deleted.map { it.node })
        assertEquals(original.take(2), deleted.take(2))
        assertEquals(original.takeLast(2).mapIndexed { index, row -> row.copy(node = index + 3) }, deleted.takeLast(2))
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(deleted))

        val courses = listOf(course(1, -1), course(2, 0), course(3, 4), course(4, 5), course(5, 2, false))
        assertEquals(
            listOf(courses[0], courses[1], courses[2].copy(startNode = 3), courses[3].copy(startNode = 4), courses[4]),
            remapRenumberedEdgeCourses(
                courses,
                TimeTableUtils.buildTimeJsonFromRows(original),
                TimeTableUtils.buildTimeJsonFromRows(deleted)
            )
        )
    }

    @Test
    fun `deleting a standard retains unfinished tail and an established invalid card`() {
        val before = TimeSlotRow(0, "06:00", "06:30", EdgeClass.Before)
        val established = TimeSlotRow(1, "11:00", "08:45")
        val after = TimeSlotRow(5, "22:00", "22:30", EdgeClass.After)
        val firstPending = TimeSlotRow(3, "", "")
        val secondPending = TimeSlotRow(4, "", "")
        val original = listOf(
            before, established, TimeSlotRow(2, "10:00", "10:45"), after,
            firstPending, secondPending
        )

        val deleted = TimeTableUtils.removeAndRenumber(original, 2)

        assertEquals(
            listOf(before, established, after.copy(node = 4), firstPending.copy(node = 2), secondPending.copy(node = 3)),
            deleted
        )
        assertEquals(listOf(false, false, false, true, true), deleted.map { it.pendingManualInsertion })
    }

    private fun course(id: Long, node: Int, irregular: Boolean = true) = CourseEntity(
        id = id, tableId = 1, groupId = "group-$id", courseName = "Course $id", day = 1,
        startNode = node, step = 1, startWeek = 1, endWeek = 16,
        color = "#FF6750A4", isIrregularNode = irregular
    )
}

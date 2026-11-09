package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportCoursePeriodCoverageTest {
    private fun course(node: Int = 31, step: Int = 2) = CourseEntity(
        groupId = "high", tableId = 0, courseName = "实验", day = 1,
        startNode = node, step = step, startWeek = 1, endWeek = 20, color = "#123456",
    )

    private fun rows(vararg nodes: Int) = nodes.map { TimeTableUtils.TimeSlotRow(it, "08:00", "08:45") }

    @Test fun selectedShortScheduleCannotHideHigherCourseNodes() {
        val manual = rows(*(1..32).toList().toIntArray())
        val bound = TimeTableUtils.buildTimeJsonFromRows(rows(*(1..12).toList().toIntArray()))
        val selected = TimeTableUtils.effectiveRowsForConfirm(manual, 7, listOf(7L to bound))
        assertEquals(31L, TimeTableUtils.firstMissingCourseNode(listOf(course()), selected))
        assertEquals(32, manual.size)
        assertEquals(bound, TimeTableUtils.buildTimeJsonFromRows(selected))
    }

    @Test fun finalAndInteriorNodesMustExistEvenWhenStartAndMaximumExist() {
        assertEquals(32L, TimeTableUtils.firstMissingCourseNode(listOf(course()), rows(31)))
        assertEquals(4L, TimeTableUtils.firstMissingCourseNode(listOf(course(3, 3)), rows(3, 5, 32)))
    }

    @Test fun actualTimeCoursesDoNotRequireNominalNodes() {
        val actual = course().copy(ownTime = true, startTime = "18:00", endTime = "19:30")
        assertNull(TimeTableUtils.firstMissingCourseNode(listOf(actual), emptyList()))
        assertNull(TimeTableUtils.firstMissingCourseNode(listOf(actual.copy(ownTime = false, isIrregularTime = true)), rows(1)))
        assertEquals(31L, TimeTableUtils.firstMissingCourseNode(listOf(actual, course()), rows(1)))
    }

    @Test fun completeHighAndEdgeSlotsAreAcceptedWithoutRewritingAnything() {
        val courses = listOf(course(365, 2), course(0, 2), course(-1, 1))
        val selected = rows(366, 0, 365, 1, -1)
        val before = selected.toList()
        assertNull(TimeTableUtils.firstMissingCourseNode(courses, selected))
        assertEquals(before, selected)
        assertEquals(365, courses.first().startNode)
        assertEquals(2, courses.first().step)
    }

    @Test fun hugeCourseSpanReportsFirstGapWithoutOverflowOrExpandingTheRange() {
        assertEquals(32L, TimeTableUtils.firstMissingCourseNode(listOf(course(31, Int.MAX_VALUE)), rows(31)))
        assertEquals(Int.MAX_VALUE.toLong() + 1,
            TimeTableUtils.firstMissingCourseNode(listOf(course(Int.MAX_VALUE, 2)), rows(Int.MAX_VALUE)))
    }

    @Test fun unusedMissingNodesAndEmptyCourseListsDoNotBlockImport() {
        assertNull(TimeTableUtils.firstMissingCourseNode(listOf(course(31, 1)), rows(31)))
        assertNull(TimeTableUtils.firstMissingCourseNode(emptyList(), emptyList()))
    }

    @Test fun appendingCannotBorrowImportedPeriodsToExtendTheSelectedSharedSchedule() {
        val current = TimeTableUtils.buildTimeJsonFromRows(rows(*(1..12).toList().toIntArray()))
        val incoming = TimeTableUtils.buildTimeJsonFromRows(rows(31, 32))
        val selected = CourseImportPolicy.timeJsonForAppend(current, incoming, 32, bound = true)
        assertEquals(current, selected)
        assertEquals(31L, TimeTableUtils.firstMissingCourseNode(listOf(course()), TimeTableUtils.parseTimeSlotRows(selected)))
        val privateSchedule = CourseImportPolicy.timeJsonForAppend(current, incoming, 32, bound = false)
        assertNull(TimeTableUtils.firstMissingCourseNode(listOf(course()), TimeTableUtils.parseTimeSlotRows(privateSchedule)))
    }
}

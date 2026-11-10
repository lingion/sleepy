package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodTableEdgeRemappingTest {
    private fun course(id: Long, node: Int, irregular: Boolean = true) = CourseEntity(
        id = id, tableId = 1, groupId = "group-$id", courseName = "Course $id", day = 1,
        startNode = node, step = 1, startWeek = 1, endWeek = 16,
        color = "#FF6750A4", isIrregularNode = irregular,
    )
    private fun json(vararg rows: TimeSlotRow) = TimeTableUtils.buildTimeJsonFromRows(rows.toList())

    @Test fun renumberingAndEditingClocksKeepsDirectionOrderAndStandardCoursePolicy() {
        val old = json(TimeSlotRow(0, "06:00", "06:30", EdgeClass.Before),
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "20:00", "20:30", EdgeClass.After),
            TimeSlotRow(3, "21:00", "21:30", EdgeClass.After))
        val updated = json(TimeSlotRow(0, "06:15", "06:45", EdgeClass.Before),
            TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "09:00", "09:45"),
            TimeSlotRow(3, "19:30", "20:00", EdgeClass.After),
            TimeSlotRow(4, "20:30", "21:00", EdgeClass.After))
        val courses = listOf(course(1, 0), course(2, 2), course(3, 3), course(4, 1, irregular = false))
        assertEquals(listOf(courses[0], courses[1].copy(startNode = 3),
            courses[2].copy(startNode = 4), courses[3]), remapRenumberedEdgeCourses(courses, old, updated))
    }

    @Test fun deletedEdgeIsNotPairedWithSurvivingNeighbour() {
        val old = json(TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "20:00", "20:30", EdgeClass.After),
            TimeSlotRow(3, "21:00", "21:30", EdgeClass.After))
        val updated = json(TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "09:00", "09:45"),
            TimeSlotRow(3, "10:00", "10:45"), TimeSlotRow(4, "21:00", "21:30", EdgeClass.After))
        val removed = course(1, 2)
        val surviving = course(2, 3)
        assertEquals(listOf(removed, surviving.copy(startNode = 4)),
            remapRenumberedEdgeCourses(listOf(removed, surviving), old, updated))
    }
}

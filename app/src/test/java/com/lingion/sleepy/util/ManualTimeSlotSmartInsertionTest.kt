package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualTimeSlotSmartInsertionTest {
    private val initial = listOf(
        TimeSlotRow(1, "08:00", "08:45"),
        TimeSlotRow(2, "10:00", "10:45"),
        TimeSlotRow(3, "", "")
    )

    @Test
    fun `new card waits for both clocks then joins its chronological position`() {
        val partial = TimeTableUtils.updateManualRowTime(initial, 3, start = "09:00")
        assertEquals(listOf("08:00", "10:00", "09:00"), partial.map { it.start })

        val complete = TimeTableUtils.updateManualRowTime(partial, 3, end = "09:45")
        assertEquals(listOf("08:00", "09:00", "10:00"), complete.map { it.start })
        assertEquals(listOf(1, 2, 3), complete.map { it.node })
        assertEquals(complete, TimeTableUtils.parseTimeSlotRows(TimeTableUtils.buildTimeJsonFromRows(complete)))
    }

    @Test
    fun `end can be filled before start and another blank card stays at the tail`() {
        val withAnotherBlank = initial + TimeSlotRow(4, "", "")
        val partial = TimeTableUtils.updateManualRowTime(withAnotherBlank, 3, end = "07:45")
        assertEquals(withAnotherBlank.map { it.start }, partial.map { it.start })

        val complete = TimeTableUtils.updateManualRowTime(partial, 3, start = "07:00")
        assertEquals(listOf("07:00", "08:00", "10:00", ""), complete.map { it.start })
        assertEquals(listOf(1, 2, 3, 4), complete.map { it.node })
    }

    @Test
    fun `reversed invalid or overnight clocks keep the card in place for correction`() {
        for ((start, end) in listOf("09:45" to "09:00", "09:00" to "09:00", "26:00" to "26:45")) {
            val changed = TimeTableUtils.updateManualRowTime(initial, 3, start = start, end = end)
            assertEquals(listOf(1, 2, 3), changed.map { it.node })
            assertEquals(start, changed.last().start)
            assertEquals(end, changed.last().end)
        }
    }

    @Test
    fun `editing an established card retains its section instead of reordering all courses`() {
        val changed = TimeTableUtils.updateManualRowTime(initial, 2, start = "07:00")
        assertEquals(listOf("08:00", "07:00", ""), changed.map { it.start })
        assertEquals(listOf(1, 2, 3), changed.map { it.node })
    }

    @Test
    fun `edge identities and metadata survive standard card insertion`() {
        val before = TimeSlotRow(0, "06:00", "06:30", EdgeClass.Before)
        val after = TimeSlotRow(4, "22:00", "22:30", EdgeClass.After)
        val changed = TimeTableUtils.updateManualRowTime(listOf(before) + initial + after, 3, "09:00", "09:45")

        assertEquals(before, changed.first())
        assertEquals(after, changed.last())
        assertEquals(listOf(0, 1, 2, 3, 4), changed.map { it.node })
        assertEquals(listOf("06:00", "08:00", "09:00", "10:00", "22:00"), changed.map { it.start })
    }

    @Test
    fun `real edge creation append and completion keep standard nodes contiguous`() {
        val oldJson = TimeTableUtils.insertEdgeNode(
            TimeTableUtils.buildTimeJsonFromRows(initial.take(2)), EdgeClass.After, "22:00", "22:30"
        )
        val appended = TimeTableUtils.appendEmptyRow(TimeTableUtils.parseTimeSlotRows(oldJson))
        assertEquals(3, appended.last().node)
        assertEquals(4, appended.single { it.edgeClass == EdgeClass.After }.node)
        val partial = TimeTableUtils.updateManualRowTime(appended, appended.last().node, start = "09:00")
        val complete = TimeTableUtils.updateManualRowTime(partial, appended.last().node, end = "09:45")

        assertEquals(listOf(1, 2, 3, 4), complete.map { it.node })
        assertEquals(listOf("08:00", "09:00", "10:00", "22:00"), complete.map { it.start })
        assertEquals(EdgeClass.After, complete.last().edgeClass)
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(complete))
        val newJson = TimeTableUtils.buildTimeJsonFromRows(complete)
        assertEquals(complete, TimeTableUtils.parseTimeSlotRows(newJson))
        assertEquals(4 to 1, TimeTableUtils.remapCourseNodes(3, 1, oldJson, newJson))
        assertEquals(3, TimeTableUtils.inferSmartPeriodConfig(complete)!!.totalPeriods)
    }

    @Test
    fun `multiple after edges shift together while before node identities remain unchanged`() {
        var json = TimeTableUtils.buildTimeJsonFromRows(initial.take(2))
        json = TimeTableUtils.insertEdgeNode(json, EdgeClass.Before, "06:30", "07:00")
        json = TimeTableUtils.insertEdgeNode(json, EdgeClass.After, "22:00", "22:30")
        json = TimeTableUtils.insertEdgeNode(json, EdgeClass.After, "22:30", "23:00")
        val appended = TimeTableUtils.appendEmptyRow(TimeTableUtils.parseTimeSlotRows(json))
        val complete = TimeTableUtils.updateManualRowTime(appended, appended.last().node, "09:00", "09:45")

        assertEquals(listOf(0, 1, 2, 3, 4, 5), complete.map { it.node })
        assertEquals(listOf(EdgeClass.Before, null, null, null, EdgeClass.After, EdgeClass.After), complete.map { it.edgeClass })
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(complete))
    }
}

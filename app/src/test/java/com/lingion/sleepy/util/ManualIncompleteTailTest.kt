package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualIncompleteTailTest {
    private val rows = listOf(
        TimeSlotRow(1, "08:00", "08:45"),
        TimeSlotRow(2, "10:00", "10:45"),
        TimeSlotRow(3, "22:00", "22:30", EdgeClass.After),
    )

    @Test fun appendingAnotherBlankKeepsEveryUnfinishedCardAfterTheEdge() {
        val first = TimeTableUtils.appendEmptyRow(rows)
        val partial = TimeTableUtils.updateManualRowTime(first, 3, start = "09:00")
        val second = TimeTableUtils.appendEmptyRow(partial)

        assertEquals(listOf("08:00", "10:00", "22:00", "09:00", ""), second.map { it.start })
        assertEquals(listOf(1, 2, 5, 3, 4), second.map { it.node })
    }

    @Test fun completingEitherBlankInsertsOnlyThatCardAndLeavesTheOtherAtTheTail() {
        val draft = TimeTableUtils.appendEmptyRow(TimeTableUtils.appendEmptyRow(rows))
        for (completed in listOf(3, 4)) {
            val result = TimeTableUtils.updateManualRowTime(draft, completed, "09:00", "09:45")

            assertEquals(listOf("08:00", "09:00", "10:00", "22:00", ""), result.map { it.start })
            assertEquals(listOf(1, 2, 3, 5, 4), result.map { it.node })
            assertEquals(EdgeClass.After, result[result.lastIndex - 1].edgeClass)
        }
    }
}

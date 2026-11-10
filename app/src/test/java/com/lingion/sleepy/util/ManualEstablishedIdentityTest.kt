package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualEstablishedIdentityTest {
    @Test fun repairingAnEstablishedCardAfterTemporaryInvalidClocksKeepsItsNode() {
        val rows = listOf(
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "10:00", "10:45"),
            TimeSlotRow(3, "12:00", "12:45"),
        )
        val partial = TimeTableUtils.updateManualRowTime(rows, 1, start = "11:00")
        val repaired = TimeTableUtils.updateManualRowTime(partial, 1, end = "11:45")

        assertEquals(listOf("11:00", "10:00", "12:00"), repaired.map { it.start })
        assertEquals(listOf(1, 2, 3), repaired.map { it.node })
    }

    @Test fun addingABlankDoesNotMoveAnEstablishedCardWithTemporaryInvalidClocks() {
        val rows = listOf(TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "10:00", "10:45"))
        val partial = TimeTableUtils.updateManualRowTime(rows, 1, start = "11:00")
        val appended = TimeTableUtils.appendEmptyRow(partial)

        assertEquals(listOf("11:00", "10:00", ""), appended.map { it.start })
        assertEquals(listOf(1, 2, 3), appended.map { it.node })
    }
}

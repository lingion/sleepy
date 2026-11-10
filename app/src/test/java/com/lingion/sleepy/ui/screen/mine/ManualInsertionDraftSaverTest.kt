package com.lingion.sleepy.ui.screen.mine

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.SaverScope
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManualInsertionDraftSaverTest {
    private val established = listOf(
        TimeSlotRow(1, "08:00", "08:45"),
        TimeSlotRow(2, "10:00", "10:45"),
        TimeSlotRow(3, "22:00", "22:30", EdgeClass.After),
    )

    @Test fun restoredPendingCardStillInsertsAfterReversedClocksAreCorrected() {
        val appended = TimeTableUtils.appendEmptyRow(established)
        val reversed = TimeTableUtils.updateManualRowTime(appended, 3, "09:30", "09:00")

        val restored = roundTrip(reversed)
        assertEquals(reversed, restored)
        val completed = TimeTableUtils.updateManualRowTime(restored, 3, end = "09:45")

        assertEquals(listOf("08:00", "09:30", "10:00", "22:00"), completed.map { it.start })
        assertEquals(listOf(1, 2, 3, 4), completed.map { it.node })
        assertEquals(EdgeClass.After, completed.last().edgeClass)
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(completed))
    }

    @Test fun restoredEstablishedCardKeepsItsNodeAfterTemporaryInvalidClocksAreCorrected() {
        val invalid = TimeTableUtils.updateManualRowTime(established, 1, start = "11:00")

        val restored = roundTrip(invalid)
        assertEquals(invalid, restored)
        val corrected = TimeTableUtils.updateManualRowTime(restored, 1, end = "11:45")

        assertEquals(listOf("11:00", "10:00", "22:00"), corrected.map { it.start })
        assertEquals(listOf(1, 2, 3), corrected.map { it.node })
    }

    @Test fun completingPendingCardPreservesAnotherEstablishedCardsTemporaryInvalidOrder() {
        val invalid = TimeTableUtils.updateManualRowTime(established, 1, start = "11:00")
        val appended = TimeTableUtils.appendEmptyRow(invalid)

        val completed = TimeTableUtils.updateManualRowTime(roundTrip(appended), 3, "09:00", "09:45")

        assertEquals(listOf("09:00", "11:00", "10:00", "22:00"), completed.map { it.start })
        assertEquals(listOf(1, 2, 3, 4), completed.map { it.node })
    }

    @Test fun completingCardAtAnExistingStartKeepsTheEstablishedCardFirst() {
        val appended = TimeTableUtils.appendEmptyRow(established)

        val completed = TimeTableUtils.updateManualRowTime(roundTrip(appended), 3, "08:00", "08:30")

        assertEquals(listOf("08:45", "08:30", "10:45", "22:30"), completed.map { it.end })
    }

    @Test fun legacyFourColumnDraftsKeepClocksAndEdgesAndInferBlankInsertionState() {
        val legacy = listOf(
            "0", "06:00", "06:30", "Before",
            "1", "08:00", "08:45", "",
            "2", "10:00", "10:45", "",
            "4", "22:00", "22:30", "After",
            "3", "09:00", "", "",
        )
        val restored = TimeSlotRowsSaver.restore(legacy)!!.toList()

        assertEquals(listOf(0, 1, 2, 4, 3), restored.map { it.node })
        assertEquals(listOf(EdgeClass.Before, null, null, EdgeClass.After, null), restored.map { it.edgeClass })
        val completed = TimeTableUtils.updateManualRowTime(restored, 3, end = "09:45")
        assertEquals(listOf("06:00", "08:00", "09:00", "10:00", "22:00"), completed.map { it.start })
        assertEquals(listOf(0, 1, 2, 3, 4), completed.map { it.node })
    }

    @Test fun incompleteLegacyPayloadIsRejectedWithoutRestoringPartialRows() {
        assertNull(TimeSlotRowsSaver.restore(listOf("1", "08:00", "08:45")))
    }

    @Test fun persistedScheduleJsonExcludesDraftInsertionState() {
        val appended = TimeTableUtils.appendEmptyRow(established)
        val completed = TimeTableUtils.updateManualRowTime(appended, 3, "09:00", "09:45")
        val json = TimeTableUtils.buildTimeJsonFromRows(completed)

        assertFalse(json.contains("pendingManualInsertion"))
        assertEquals(completed, TimeTableUtils.parseTimeSlotRows(json))
        val invalid = TimeTableUtils.updateManualRowTime(TimeTableUtils.parseTimeSlotRows(json), 1, start = "11:00")
        val corrected = TimeTableUtils.updateManualRowTime(invalid, 1, end = "11:45")
        assertEquals(listOf("11:00", "09:00", "10:00", "22:00"), corrected.map { it.start })
    }

    private fun roundTrip(rows: List<TimeSlotRow>): List<TimeSlotRow> {
        val state = mutableStateListOf<TimeSlotRow>().apply { addAll(rows) }
        val saved = with(TimeSlotRowsSaver) { SaverScope { true }.save(state) }!!
        return TimeSlotRowsSaver.restore(saved)!!.toList()
    }
}

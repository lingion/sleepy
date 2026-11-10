package com.lingion.sleepy.ui.component

import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchingSmartPeriodPersistenceTest {
    @Test
    fun `invalid stored smart config cannot overwrite valid manual rows when saved`() {
        val rows = listOf(TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "09:00", "09:45"))
        val invalidStored = SmartPeriodConfig(totalPeriods = 40, periodMinutes = 45)

        val persisted = decodeSmartPeriodConfig(encodeMatchingSmartPeriodConfig(rows, invalidStored))!!

        assertNull(persisted.validationError())
        assertEquals(rows, persisted.derive())
    }

    @Test
    fun `manual edits that disagree with smart config are persisted as a matching config`() {
        val original = SmartPeriodConfig(startTime = "08:00", periodMinutes = 45, totalPeriods = 2)
        val rows = listOf(TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "10:00", "10:30"))

        val persisted = decodeSmartPeriodConfig(encodeMatchingSmartPeriodConfig(rows, original))!!

        assertEquals(rows, persisted.derive())
    }

    @Test
    fun `invalid rows do not persist stale automatic configuration`() {
        val rows = listOf(TimeSlotRow(1, "26:00", "26:45"))

        assertEquals("", encodeMatchingSmartPeriodConfig(rows, SmartPeriodConfig()))
    }

    @Test
    fun `automatic count only shifts colliding after group and preserves before identities and edge gaps`() {
        val before = TimeSlotRow(-1, "06:00", "06:30", EdgeClass.Before)
        val secondBefore = TimeSlotRow(0, "07:00", "07:30", EdgeClass.Before)
        val after = TimeSlotRow(3, "22:00", "22:30", EdgeClass.After)
        val secondAfter = TimeSlotRow(5, "23:00", "23:30", EdgeClass.After)
        val existing = listOf(before, secondBefore, TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "09:00", "09:45"), after, secondAfter)

        assertEquals(existing, mergeAutoRows(existing, existing.filter { it.edgeClass == null }))

        val grown = mergeAutoRows(existing, SmartPeriodConfig(totalPeriods = 3).derive())
        assertEquals(listOf(before, secondBefore), grown.take(2))
        assertEquals(listOf(after.copy(node = 4), secondAfter.copy(node = 6)), grown.filter { it.edgeClass == EdgeClass.After })
        assertEquals(listOf(-1, 0, 1, 2, 4, 6, 3), grown.map { it.node })
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(grown))

        val shrunk = mergeAutoRows(grown, SmartPeriodConfig(totalPeriods = 1).derive())
        assertEquals(listOf(before, secondBefore), shrunk.take(2))
        assertEquals(listOf(after.copy(node = 4), secondAfter.copy(node = 6)), shrunk.filter { it.edgeClass == EdgeClass.After })
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(shrunk))
    }
}

package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.PeriodTableEntity
import org.junit.Assert.*
import org.junit.Test

class ImportedPeriodTableDraftTest {
    private val confirmed = """[{"node":1,"start":"09:10","end":"10:00"},{"node":2,"start":"10:15","end":"11:05"}]"""

    @Test fun confirmedRowsOwnNewScheduleAndCount() {
        val draft = requireNotNull(importedPeriodTableDraft(null, "Imported", confirmed))
        assertEquals("Imported", draft.name)
        assertEquals(confirmed, draft.timeJson)
        assertEquals(2, draft.nodesPerDay)
        assertEquals("09:10", TimeTableUtils.parseTimeSlotRows(draft.timeJson).first().start)
    }

    @Test fun explicitSharedBindingDoesNotCreateOrOverwritePeriodTable() {
        assertNull(importedPeriodTableDraft(7L, "Imported", confirmed))
    }

    @Test fun separateImportsHaveIndependentUnsavedDrafts() {
        val a = requireNotNull(importedPeriodTableDraft(null, "A", confirmed))
        val b = requireNotNull(importedPeriodTableDraft(null, "B", confirmed.replace("09:10", "09:20")))
        assertEquals(0L, a.id)
        assertEquals(0L, b.id)
        assertEquals("09:10", TimeTableUtils.parseTimeSlotRows(a.timeJson).first().start)
        assertEquals("09:20", TimeTableUtils.parseTimeSlotRows(b.timeJson).first().start)
    }

    private val validCombo = """{"name":"s","publicSchedules":[{"name":"summer","startDate":"2026-05-01","endDate":"2026-09-30","timeSlots":[{"number":2,"startTime":"10:00","endTime":"10:50"}]}]}"""

    @Test fun validCombinationIsKeptOnNewSchedule() {
        val draft = requireNotNull(importedPeriodTableDraft(null, "Imported", confirmed, validCombo))
        assertEquals(validCombo, draft.comboScheduleJson)
        assertEquals("10:00", TimeTableUtils.parseTimeSlotRows(draft.effectiveTimeJson(java.time.LocalDate.of(2026, 6, 1))).last().start)
        assertEquals(confirmed, draft.effectiveTimeJson(java.time.LocalDate.of(2026, 1, 1)))
    }

    @Test fun unusableCombinationIsSkippedAndCoursesStillImportOnBaseSchedule() {
        val broken = validCombo.replace("2026-09-30", "2026-04-01") // endDate before startDate
        val draft = requireNotNull(importedPeriodTableDraft(null, "Imported", confirmed, broken))
        assertEquals("an unusable combination must not be stored", "", draft.comboScheduleJson)
        assertEquals("courses still get the confirmed base schedule", confirmed, draft.timeJson)
        assertEquals(confirmed, draft.effectiveTimeJson(java.time.LocalDate.of(2026, 6, 1)))
    }

    @Test fun corruptStoredCombinationFallsBackToBaseScheduleOnEveryDate() {
        // Legacy or damaged rows must not crash rendering; the base schedule is the safe answer.
        val stored = PeriodTableEntity(name = "stored", nodesPerDay = 2, timeJson = confirmed, comboScheduleJson = "{not json")
        assertEquals(confirmed, stored.effectiveTimeJson(java.time.LocalDate.of(2026, 6, 1)))
        assertEquals(confirmed, stored.effectiveTimeJson(java.time.LocalDate.of(2026, 1, 1)))
    }
}

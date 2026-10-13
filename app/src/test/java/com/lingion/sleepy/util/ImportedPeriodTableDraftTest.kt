package com.lingion.sleepy.util

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
}

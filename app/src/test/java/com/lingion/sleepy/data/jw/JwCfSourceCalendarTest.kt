package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwCfSourceCalendarTest {
    private fun source(weeks: String) = """{"sleepyCfNtss":true,"requestedWeeks":$weeks,"weeks":[{"week":1,"rows":[]}]}"""

    @Test fun `explicit requested weeks preserve empty late semester weeks through draft`() {
        val count = JwSourceCalendar.resolveWeekCount(source("[1,3,25,30]"), "cf_new")
        assertEquals(30, count)
        val snapshot = JwImportDraftSnapshot(JwSchoolInfo("T", "NTSS"), emptyList(), emptyList(), termWeekCount = count)
        assertEquals(snapshot, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot)))
    }

    @Test fun `invalid week list never truncates or defaults semester length`() {
        for (weeks in listOf("[]", "[0]", "[-1]", "[1.5]", "[1,367]", "[1,null]", "[1,\"bad\"]")) {
            assertEquals(weeks, 0, JwSourceCalendar.resolveWeekCount(source(weeks), "cf_new"))
        }
        assertEquals(0, JwSourceCalendar.resolveWeekCount("{}", "cf_new"))
        assertEquals(0, JwSourceCalendar.resolveWeekCount(source("[30]"), "unknown"))
    }
}

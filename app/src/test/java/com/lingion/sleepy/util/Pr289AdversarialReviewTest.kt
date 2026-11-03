package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.widget.TodayRowGeometry
import com.lingion.sleepy.widget.TodayWidgetReceiver
import com.lingion.sleepy.widget.WidgetData
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Data integrity and consumer consistency regressions for period validation. */
class Pr289AdversarialReviewTest {
    private fun json(vararg rows: TimeTableUtils.TimeSlotRow) = TimeTableUtils.buildTimeJsonFromRows(rows.toList())
    private fun row(node: Int, start: String, end: String) = TimeTableUtils.TimeSlotRow(node, start, end)

    @Test fun lateNightOverlapIsReportedWithoutInventingAnotherDay() {
        val input = json(row(1, "23:30", "23:55"), row(2, "23:40", "23:50"))
        val output = TimeTableUtils.sanitizeTimeJsonForDisplay(input)
        assertEquals("Display normalization cannot move real clocks", JSONArray(input).toString(), JSONArray(output).toString())
        assertTrue(TimeTableUtils.validateTimeJson(output) is TimeTableUtils.PeriodValidationResult.Overlap)
    }

    @Test fun formatNormalizationIsIdempotentAndPreservesMetadata() {
        val input = """[{"node":0,"edge":"before","start":"05:30 ","end":" 05:45","custom":{"a":1}},{"start":"08:00","end":"08:45"}]"""
        val once = TimeTableUtils.sanitizeTimeJsonForDisplay(input)
        assertEquals(once, TimeTableUtils.sanitizeTimeJsonForDisplay(once))
        val first = JSONArray(once).getJSONObject(0)
        assertEquals("05:30", first.getString("start"))
        assertEquals("05:45", first.getString("end"))
        assertEquals(0, first.getInt("node"))
        assertEquals("before", first.getString("edge"))
        assertEquals(1, first.getJSONObject("custom").getInt("a"))
        assertFalse("Legacy omitted identity must stay omitted", JSONArray(once).getJSONObject(1).has("node"))
    }

    @Test fun malformedJsonIsDiagnosedWithoutInventedDefaultTimetable() {
        val input = "not-json"
        assertFalse(TimeTableUtils.isPeriodTableValid(input))
        assertEquals(input, TimeTableUtils.sanitizeTimeJsonForDisplay(input))
    }

    @Test fun missingRequiredFieldsCannotBeValidatedUsingEditorDefaults() {
        for (input in listOf("[{}]", "[null]", "[1]", "[{\"start\":480,\"end\":\"08:45\"}]", "[{\"start\":\"08:00\"}]")) {
            assertFalse("Missing or mistyped required fields: $input", TimeTableUtils.isPeriodTableValid(input))
            assertEquals(input, TimeTableUtils.sanitizeTimeJsonForDisplay(input))
        }
    }

    @Test fun acceptedWhitespaceIsUsableByTheActualTimeParser() {
        val input = json(row(1, "08:00 ", "08:45 "))
        assertTrue(TimeTableUtils.isPeriodTableValid(input))
        val output = TimeTableUtils.sanitizeTimeJsonForDisplay(input)
        assertEquals("08:00", TimeTableUtils.parseTimeSlotRows(output).single().start)
        assertTrue(TimeTableUtils.timeSlotsFor(output).isNotEmpty())
        assertTrue("All consumers share the same strict trimmed clock parser", TimeTableUtils.timeSlotsFor(input).isNotEmpty())
    }

    @Test fun minuteOnlyClockFormatRejectsSecondsAndInvalidRanges() {
        for (start in listOf("08:00:01", "8:00", "24:00", "08:60", "")) {
            assertFalse(start, TimeTableUtils.isPeriodTableValid(json(row(1, start, "09:00"))))
        }
    }

    @Test fun duplicatedExplicitAndLegacyIdentitiesAreRejected() {
        assertFalse(TimeTableUtils.validatePeriodRows(listOf(row(1, "08:00", "08:45"), row(1, "09:00", "09:45"))) is TimeTableUtils.PeriodValidationResult.Valid)
        assertFalse(TimeTableUtils.isPeriodTableValid("""[{"node":2,"start":"08:00","end":"08:45"},{"start":"09:00","end":"09:45"}]"""))
        assertFalse(TimeTableUtils.isPeriodTableValid("""[{"node":"invalid","start":"08:00","end":"08:45"}]"""))
    }

    @Test fun legacyMissingNodesAndEarlyEdgeIdentitiesRemainSupported() {
        assertTrue(TimeTableUtils.isPeriodTableValid("""[{"start":"08:00","end":"08:45"},{"start":"09:00","end":"09:45"}]"""))
        val rows = listOf(row(-1, "00:00", "00:45"), row(0, "05:30", "05:45"), row(1, "08:00", "08:45"))
        assertTrue(TimeTableUtils.validatePeriodRows(rows) is TimeTableUtils.PeriodValidationResult.Valid)
        assertTrue(TimeTableUtils.isTimeSlotOrderValid(rows))
        assertNull(TimeTableUtils.findConflictForSlot(rows, 0))
    }

    @Test fun earlyEdgePeriodAndUnrelatedPeriodNeverMove() {
        val base = json(row(1, "08:00", "08:45"))
        val input = TimeTableUtils.insertEdgeNode(base, TimeTableUtils.EdgeClass.Before, "05:30", "05:45")
        val output = TimeTableUtils.sanitizeTimeJsonForDisplay(input)
        val rows = TimeTableUtils.parseTimeSlotRows(output)
        assertEquals("05:30", rows.first { it.node == 0 }.start)
        assertEquals("08:00", rows.first { it.node == 1 }.start)
        assertTrue(TimeTableUtils.isPeriodTableValid(output))
    }

    @Test fun selectedFixedWindowCoursesFitActualSpansAndKeepAllIdentities() {
        val timeJson = json(row(1, "08:00", "09:40"), row(2, "09:30", "10:15"))
        val scheduled = course(1, 2)
        val ownTime = course(2, 2).copy(ownTime = true, isIrregularTime = true, startTime = "09:30", endTime = "09:35")
        val data = WidgetData(date = LocalDate.of(2026, 11, 2), courses = listOf(scheduled, ownTime), timeJson = timeJson, hasTable = true)
        for (height in listOf(88f, 122f)) {
            val window = TodayWidgetReceiver.computeTodayWindow(data, height, null)
            val selected = window.visible.flatMap { it.row.courses }
            val spans = TodayRowGeometry.rowSpans(selected, headerSpace = false, timeJson = timeJson)
            assertEquals(setOf(1L, 2L), selected.map { it.id }.toSet())
            assertEquals(0, window.hiddenAheadCourses)
            assertTrue("Rendered span exceeds selected window: $spans", spans.last().bottomDp <= height - TodayRowGeometry.NAV_BAR_H_DP)
        }
    }

    @Test fun badStoredClockCannotDropAnyCourseFromGeometry() {
        val courses = listOf(course(1, 1), course(2, 2), course(3, 3))
        for (input in listOf("not-json", "[{}]", """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"invalid","end":"10:00"}]""")) {
            val display = TimeTableUtils.sanitizeTimeJsonForDisplay(input)
            val rows = TodayRowGeometry.rowSpans(courses, false, display)
            assertEquals(input, courses.map { it.id }.toSet(), rows.flatMap { it.row.courses }.map { it.id }.toSet())
        }
    }

    @Test fun effectiveTimeAndWindowNeverInventEditorDefaultsForBadStoredData() {
        for (input in listOf("broken", "[{}]", """[{"node":1,"start":"08:00"}]""")) {
            assertNull(input, TimeTableUtils.effectiveCourseTime(false, "", "", 1, 1, input))
            assertNull(input, com.lingion.sleepy.widget.FixedWindowCore.courseStartEndMin(course(1, 1), input))
        }
    }

    @Test fun effectiveTimeRejectsAmbiguousIdentityAndUsesTrimmedRealClock() {
        val duplicate = json(row(1, "08:00", "08:45"), row(1, "09:00", "09:45"))
        assertNull(TimeTableUtils.effectiveCourseTime(false, "", "", 1, 1, duplicate))
        val spaced = """[{"node":1,"start":" 05:30 ","end":"05:45 "}]"""
        assertEquals("05:30" to "05:45", TimeTableUtils.effectiveCourseTime(false, "", "", 1, 1, spaced))
    }

    @Test fun machineClockFormatIsIndependentOfUiLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            val input = """[{"node":1,"start":"05:30","end":"05:45"}]"""
            assertEquals("05:30" to "05:45", TimeTableUtils.effectiveCourseTime(false, "", "", 1, 1, input))
            assertEquals(330 to 345, com.lingion.sleepy.widget.FixedWindowCore.courseStartEndMin(course(1, 1), input))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun invalidSpanCannotWrapIntoAnotherNode() {
        val input = json(row(Int.MAX_VALUE, "08:00", "08:45"), row(Int.MIN_VALUE, "09:00", "09:45"))
        assertNull(TimeTableUtils.effectiveCourseTime(false, "", "", Int.MAX_VALUE, 2, input))
        assertNull(TimeTableUtils.effectiveCourseTime(false, "", "", 1, 0, TimeTableUtils.DEFAULT_TIME_JSON))
        assertNull(TimeTableUtils.effectiveCourseTime(false, "", "", 1, -1, TimeTableUtils.DEFAULT_TIME_JSON))
    }

    private fun course(id: Long, node: Int) = CourseEntity(id = id, groupId = "g$id", tableId = 1, courseName = "Course$id",
        day = 1, startNode = node, step = 1, startWeek = 1, endWeek = 20, color = "blue")
}

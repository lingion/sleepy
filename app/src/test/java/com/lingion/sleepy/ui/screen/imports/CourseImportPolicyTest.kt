package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.CourseImportPolicy
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Test

class CourseImportPolicyTest {
    private fun course(name: String, node: Int = 1) = CourseEntity(
        groupId = name, tableId = 42, courseName = name, day = 1,
        startNode = node, step = 1, startWeek = 1, endWeek = 20, color = "#FF6750A4",
    )

    private val timeJson = TimeTableUtils.buildTimeJsonFromRows(listOf(
        TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
        TimeTableUtils.TimeSlotRow(2, "08:55", "09:40"),
    ))

    @Test fun `backup is enabled and conflicting courses are skipped by default`() {
        assertEquals(ImportDecision(true, false), ImportDecision())
    }

    @Test fun `opposite week parity is not a conflict`() {
        assertFalse(CourseImportPolicy.conflicts(course("Odd").copy(type = 1), course("Even").copy(type = 2), timeJson))
        assertTrue(CourseImportPolicy.conflicts(course("Odd").copy(type = 1), course("Every"), timeJson))
        assertFalse(CourseImportPolicy.conflicts(
            course("Odd").copy(type = 1, startWeek = 2, endWeek = 2), course("Every"), timeJson
        ))
    }

    @Test fun `custom times override nodes and touching endpoints do not overlap`() {
        val own = course("Custom", 15).copy(ownTime = true, startTime = "08:30", endTime = "08:55")
        assertTrue(CourseImportPolicy.conflicts(own, course("First"), timeJson))
        assertFalse(CourseImportPolicy.conflicts(own, course("Second", 2), timeJson))
        val irregular = own.copy(ownTime = false, isIrregularTime = true)
        assertTrue(CourseImportPolicy.conflicts(irregular, course("First"), timeJson))
    }

    @Test fun `conflict switch keeps all or skips overlaps without editing original courses`() {
        val original = listOf(course("Existing"))
        val incoming = listOf(course("Overlap"), course("Free", 2))
        for (backup in listOf(false, true)) {
            for (append in listOf(false, true)) {
                val decision = ImportDecision(backup, append)
                val accepted = CourseImportPolicy.selectIncoming(incoming, original, decision.appendConflicts, timeJson)
                assertEquals(if (append) incoming else listOf(incoming[1]), accepted)
                assertEquals(listOf(course("Existing")), original)
            }
        }
    }

    @Test fun `conflicting source courses are retained when target is empty`() {
        val incoming = listOf(course("A"), course("B"))
        assertEquals(incoming, CourseImportPolicy.selectIncoming(incoming, emptyList(), false, timeJson))
    }

    @Test fun `all conflicts disabled produces no courses to save`() {
        assertTrue(CourseImportPolicy.selectIncoming(listOf(course("New")), listOf(course("Old")), false, timeJson).isEmpty())
    }

    @Test fun `preview and commit filter agree for mixed parity and custom times`() {
        val existing = listOf(course("Even").copy(type = 2))
        val incoming = listOf(
            course("Odd").copy(type = 1),
            course("Custom", 15).copy(ownTime = true, startTime = "08:20", endTime = "08:35"),
            course("Free", 2),
        )
        val preview = createImportPreview(
            com.lingion.sleepy.data.parser.ScheduleParser.ParseResult("Source", "2026-09-07", incoming),
            42, "Target", existing, effectiveTimeJson = timeJson,
        )
        val accepted = CourseImportPolicy.selectIncoming(incoming, existing, false, timeJson)
        assertEquals(listOf(incoming[1]), preview.conflicts.map { it.incoming })
        assertEquals(preview.cleanCount, accepted.size)
        assertEquals(listOf(incoming[0], incoming[2]), accepted)
    }

    @Test fun `adding courses preserves current times and edge slots while extending missing slots`() {
        val originalRows = listOf(
            TimeTableUtils.TimeSlotRow(0, "07:00", "07:40", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
        )
        val current = TimeTableUtils.buildTimeJsonFromRows(originalRows)
        val incoming = TimeTableUtils.buildTimeJsonFromRows(listOf(
            TimeTableUtils.TimeSlotRow(1, "10:00", "10:45"),
            TimeTableUtils.TimeSlotRow(2, "10:55", "11:40"),
        ))
        val merged = TimeTableUtils.parseTimeSlotRows(CourseImportPolicy.mergeTimeJson(current, incoming, 2))
        assertEquals(originalRows, merged.filter { it.node <= 1 })
        assertEquals("10:55", merged.single { it.node == 2 }.start)
        assertEquals(current, CourseImportPolicy.mergeTimeJson(current, "", 1))
    }
}

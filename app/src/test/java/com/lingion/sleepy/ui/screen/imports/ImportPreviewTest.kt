package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.parser.ScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPreviewTest {
    private fun course(name: String, node: Int = 1, week: Int = 1) = CourseEntity(
        groupId = "", tableId = 42, courseName = name,
        day = 1, startNode = node, step = 2,
        startWeek = week, endWeek = week + 3, color = "#FF6750A4"
    )

    private fun parsed(vararg courses: CourseEntity) = ScheduleParser.ParseResult(
        tableName = "Imported", startDate = "2026-09-07", courses = courses.toList(),
        nodesPerDay = 14, maxWeek = 22
    )

    @Test
    fun `existing table remains the append target and imported metadata is preserved`() {
        val incoming = parsed(course("New", node = 13))
        val existing = listOf(course("Existing"))
        val preview = createImportPreview(incoming, 42, "Current semester", existing)

        assertEquals(42L, preview.targetTableId)
        assertEquals("Current semester", preview.targetTableName)
        assertSame(incoming, preview.parseResult)
        assertEquals(existing, preview.existingCourses)
        assertEquals(1, preview.cleanCount)
        assertEquals(0, preview.conflictCount)
    }

    @Test
    fun `one incoming course overlapping several existing courses counts only once`() {
        val overlap = course("Overlap", node = 2)
        val free = course("Free", node = 5)
        val preview = createImportPreview(
            parsed(overlap, free), 42, "Current semester",
            listOf(course("Existing A"), course("Existing B", node = 3))
        )

        assertEquals(2, preview.incomingCount)
        assertEquals(1, preview.conflictCount)
        assertEquals(1, preview.cleanCount)
        assertEquals(overlap, preview.conflicts.single().incoming)
        // Keeping conflicting courses must still have access to the entire source.
        assertEquals(listOf(overlap, free), preview.parseResult.courses)
    }

    @Test
    fun `courses in different weeks days or nodes stay appendable`() {
        val preview = createImportPreview(
            parsed(
                course("Later weeks", week = 5),
                course("Next day").copy(day = 2),
                course("Later nodes", node = 3)
            ), 42, "Current semester", listOf(course("Existing"))
        )

        assertEquals(3, preview.cleanCount)
        assertTrue(preview.conflicts.isEmpty())
    }

    @Test
    fun `first import keeps the no-target state for the new-table-only dialog`() {
        val preview = createImportPreview(parsed(course("New")), 0, "", emptyList())

        assertEquals(0L, preview.targetTableId)
        assertEquals(1, preview.incomingCount)
        assertTrue(preview.existingCourses.isEmpty())
        assertTrue(preview.conflicts.isEmpty())
    }
}

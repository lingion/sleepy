package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.*
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.*
import org.junit.Test

class ImportPreviewTest {
    private val table = TimeTableEntity(id = 42, name = "Current semester", startDate = "2026-09-07", maxWeek = 22)
    private fun course(name: String, id: Long = 0, node: Int = 1, week: Int = 1) = CourseEntity(
        id = id, groupId = name, tableId = 42, courseName = name,
        day = 1, startNode = node, step = 2,
        startWeek = week, endWeek = week + 3, color = "#FF6750A4"
    )
    private fun parsed(vararg courses: CourseEntity) = ScheduleParser.ParseResult(
        tableName = "Imported", startDate = table.startDate, courses = courses.toList(),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON, nodesPerDay = 12, maxWeek = 22
    )
    private fun plan(source: ScheduleParser.ParseResult, old: List<CourseEntity>) = planImport(
        source, ImportSnapshot(listOf(table), emptyList(), old),
        ImportConfiguration.forExisting(table).copy(overlaps = OverlapPolicy.SkipIncoming)
    )

    @Test fun `existing target and source metadata remain intact while planning`() {
        val source = parsed(course("New", node = 9))
        val old = listOf(course("Existing", id = 1))
        val preview = plan(source, old)
        assertTrue(preview.issues.toString(), preview.canSubmit)
        assertEquals(42L, preview.finalTable.id)
        assertEquals("Current semester", preview.finalTable.name)
        assertSame(source, preview.source)
        assertEquals(old, preview.snapshot.courses)
        assertEquals(1, preview.incomingCount)
        assertTrue(preview.conflicts.isEmpty())
    }

    @Test fun `one incoming row reports every old conflict but is skipped only once`() {
        val overlap = course("Overlap", node = 2)
        val free = course("Free", node = 5)
        val preview = plan(parsed(overlap, free), listOf(
            course("Existing A", id = 1), course("Existing B", id = 2, node = 3)
        ))
        assertEquals(2, preview.conflicts.count { it.kind == ConflictKind.IncomingExisting })
        assertEquals(1, preview.skippedCount)
        assertEquals(listOf("Free"), preview.incomingCourses.map { it.courseName })
        assertEquals(listOf(overlap, free), preview.source.courses)
    }

    @Test fun `different weeks days and times remain appendable`() {
        val preview = plan(parsed(course("Later weeks", week = 5),
            course("Next day").copy(day = 2), course("Later nodes", node = 3)),
            listOf(course("Existing", id = 1)))
        assertEquals(3, preview.incomingCount)
        assertTrue(preview.conflicts.isEmpty())
        assertTrue(preview.skipped.isEmpty())
    }

    @Test fun `first import creates a new table without a synthetic base`() {
        val source = parsed(course("New"))
        val snapshot = ImportSnapshot(emptyList(), emptyList(), emptyList())
        val configuration = createImportDecisionConfiguration(source, snapshot, 0, "Imported", "Periods")
        val preview = planImport(source, snapshot, configuration)
        assertTrue(preview.issues.toString(), preview.canSubmit)
        assertEquals(ImportDestination.New, configuration.destination)
        assertEquals(ImportContent.ImportOnly, configuration.content)
        assertEquals(0L, preview.finalTable.id)
        assertEquals(1, preview.incomingCount)
        assertTrue(preview.snapshot.tables.isEmpty())
    }
}

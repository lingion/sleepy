package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class CourseUpdateRegressionTest {
    private val old = CourseEntity(id = 42, tableId = 1, groupId = "math", courseName = "Math",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16,
        color = "#FF00FF00", alias = "My math", note = "Bring book")
    private fun fresh(node: Int) = old.copy(id = 0, groupId = "", startNode = node,
        color = "#FF6750A4", alias = "", note = "")

    @Test fun exactRowsKeepTheirIdentityRegardlessOfIncomingOrder() {
        for (incoming in listOf(listOf(fresh(3), fresh(1)), listOf(fresh(1), fresh(3)))) {
            val diff = diffImportedCourses(listOf(old), incoming)
            assertEquals(old, diff.merged.single { it.startNode == 1 })
            assertEquals(0L, diff.added.single().id)
            assertEquals(3, diff.added.single().startNode)
            assertTrue(diff.changed.isEmpty())
            assertTrue(diff.removed.isEmpty())
        }
    }

    @Test fun fallbackMustBeUniqueOnBothSides() {
        val diff = diffImportedCourses(listOf(old), listOf(fresh(3), fresh(5)))
        assertEquals(2, diff.added.size)
        assertEquals(listOf(old), diff.removed)
        assertTrue(diff.changed.isEmpty())
        assertTrue(diff.merged.all { it.id == 0L })
    }

    @Test fun newMeetingOfAnExistingCourseKeepsItsGroupAndGroupAlias() {
        val diff = diffImportedCourses(listOf(old), listOf(fresh(1), fresh(1).copy(day = 3)))
        val added = diff.added.single()
        assertEquals("math", added.groupId)
        assertEquals("My math", added.alias)
        assertEquals(old.color, added.color)
        assertEquals("", added.note)
        assertEquals(old, diff.merged.single { it.day == 1 })
    }
}

package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseUpdateTest {
    private fun course(
        name: String = "Math",
        day: Int = 1,
        node: Int = 1,
        teacher: String = "Teacher",
        room: String = "Room",
        id: Long = 0,
        alias: String = "",
        note: String = "",
        color: String = "#FF000000",
    ) = CourseEntity(
        id = id,
        groupId = "group-$name-$day-$node",
        tableId = 1,
        courseName = name,
        teacher = teacher,
        room = room,
        alias = alias,
        note = note,
        day = day,
        startNode = node,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        color = color,
    )

    @Test
    fun matchesChangedCourseAndPreservesLocalFields() {
        val existing = course(id = 42, alias = "My Math", note = "Bring book", color = "#FF00FF00")
        val incoming = course(teacher = "New teacher", room = "New room")

        val diff = diffImportedCourses(listOf(existing), listOf(incoming))

        assertEquals(1, diff.changed.size)
        assertEquals(42L, diff.merged.single().id)
        assertEquals("New teacher", diff.merged.single().teacher)
        assertEquals("New room", diff.merged.single().room)
        assertEquals("My Math", diff.merged.single().alias)
        assertEquals("Bring book", diff.merged.single().note)
        assertEquals("#FF00FF00", diff.merged.single().color)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun ambiguousCandidatesRemainAddedAndRemoved() {
        val existing = listOf(course(id = 1, node = 1), course(id = 2, node = 3))
        val incoming = listOf(course(node = 5))

        val diff = diffImportedCourses(existing, incoming)

        assertEquals(1, diff.added.size)
        assertEquals(2, diff.removed.size)
        assertEquals(0, diff.changed.size)
    }
}

package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-26 用户定令: 加课四路径先进逐组勾选界面。
 * 锁勾选主键的纯函数语义 — 同一组课程在分组与过滤两侧必须得到同一稳定键,
 * 不依赖对象身份/解析顺序。
 */
class CourseSelectionContractTest {

    private fun course(
        groupId: String = "",
        name: String,
        teacher: String = "",
        room: String = "",
        day: Int = 1,
        startNode: Int = 1,
    ) = CourseEntity(
        id = 0L,
        groupId = groupId,
        tableId = 0L,
        courseName = name,
        teacher = teacher,
        room = room,
        day = day,
        startNode = startNode,
        step = 1,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4",
    )

    @Test
    fun `group key prefers groupId when authoritative`() {
        val a = course(groupId = "g1", name = "高等数学")
        val b = course(groupId = "g1", name = "高等数学", day = 3)
        assertEquals(importCourseGroupKey(a), importCourseGroupKey(b))
    }

    @Test
    fun `blank groupId falls back to trimmed name plus teacher as one key`() {
        val a = course(name = " 高等数学 ", teacher = " 张三 ")
        val b = course(name = "高等数学", teacher = "张三")
        assertEquals(importCourseGroupKey(a), importCourseGroupKey(b))
        val otherTeacher = course(name = "高等数学", teacher = "李四")
        assertTrue(importCourseGroupKey(a) != importCourseGroupKey(otherTeacher))
    }

    @Test
    fun `grouping aggregates every arrangement of one course group`() {
        val courses = listOf(
            course(groupId = "g1", name = "英语", room = "A101"),
            course(groupId = "g1", name = "英语", room = "A102", day = 2),
            course(groupId = "g2", name = "体育"),
        )
        val groups = groupCoursesForImportSelection(courses)
        assertEquals(2, groups.size)
        val english = groups.first { it.id == "g1" }
        assertEquals(2, english.rows.size)
        assertEquals(listOf("A101", "A102"), english.rooms)
    }

    @Test
    fun `filter by selected keys keeps whole groups only`() {
        val courses = listOf(
            course(groupId = "g1", name = "英语"),
            course(groupId = "g1", name = "英语", day = 3),
            course(groupId = "g2", name = "体育"),
        )
        val selected = setOf("g1")
        val filtered = courses.filter { importCourseGroupKey(it) in selected }
        assertEquals(listOf("英语", "英语"), filtered.map { it.courseName })
    }
}

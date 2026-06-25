package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Test
import org.junit.Assert.*

/**
 * 端到端验证：导出-导入闭环
 * 1. 构造真实 HEU 课表
 * 2. 用 ScheduleExporter 导出
 * 3. 用 ScheduleParser 解析导出的内容
 * 4. 验证 courses 数和字段一致
 */
class ExportImportRoundTripTest {

    private fun heuTable() = TimeTableEntity(
        id = 1,
        name = "2026 春学期",
        startDate = "2026-02-23",
        maxWeek = 18,
        nodesPerDay = 13,
        timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:50","end":"09:35"}]""",
        color = "#FF6750A4",
        isDefault = true
    )

    private fun heuCourses() = listOf(
        CourseEntity(
            id = 0, groupId = "", tableId = 1,
            courseName = "工科数学分析（二）",
            teacher = "王立刚",
            room = "21B2086中(西)",
            day = 2, startNode = 3, step = 3,
            startWeek = 2, endWeek = 8, type = 0,
            color = "#FF6750A4"
        ),
        CourseEntity(
            id = 0, groupId = "", tableId = 1,
            courseName = "军事理论",
            teacher = "刁莹",
            room = "21B0117中(东)",
            day = 2, startNode = 6, step = 2,
            startWeek = 2, endWeek = 18, type = 0,
            color = "#FF6750A4"
        )
    )

    @Test
    fun wakeUpShareText_roundTrip() {
        val exported = ScheduleExporter.exportWakeUpShareText(heuTable(), heuCourses())
        println("=== [share] exported FULL ===")
        println(exported)
        println("=== [share] exported length: ${exported.length} ===")
        println("=== [share] starts with {: ${exported.trim().startsWith("{")} ===")
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        println("=== [share] parse result: $result ===")
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        println("=== parsed: ${parsed.courses.size} courses ===")
        assertEquals("Course count mismatch", 2, parsed.courses.size)
        assertEquals("工科数学分析（二）", parsed.courses[0].courseName)
        assertEquals("王立刚", parsed.courses[0].teacher)
        assertEquals(2, parsed.courses[0].day)
        assertEquals(3, parsed.courses[0].startNode)
        assertEquals(3, parsed.courses[0].step)
        assertEquals(2, parsed.courses[0].startWeek)
        assertEquals(8, parsed.courses[0].endWeek)
    }

    @Test
    fun wakeUpJson_roundTrip() {
        val exported = ScheduleExporter.exportWakeUpJson(heuTable(), heuCourses())
        println("=== exported (first 500 chars) ===")
        println(exported.take(500))
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Course count mismatch", 2, parsed.courses.size)
    }

    @Test
    fun ics_roundTrip() {
        val exported = ScheduleExporter.exportIcs(heuTable(), heuCourses())
        println("=== exported (first 800 chars) ===")
        println(exported.take(800))
        println("=== exported length: ${exported.length} ===")
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        println("=== parse result: $result ===")
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        println("=== parsed: ${parsed.courses.size} courses ===")
        assertEquals("ICS should round-trip 2 courses, got ${parsed.courses.size}", 2, parsed.courses.size)
    }
}

package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Test
import org.junit.Assert.*

/**
 * issue#55: 分享文本格式导出课表再导入后非标准时间课程时间出现异常
 *
 * 根因: exportWakeUpShareText / exportWakeUpJson 只写 10 个字段(无 ownTime/startTime/endTime),
 * parseCourseJsonArrayRaw 也不读 — 20:50-22:00 退化为 11-12 节定位。
 * 修: 导出端 ownTime=true 时多写 startTime/endTime; 导入端按字段恢复 ownTime。
 */
class Issue55OwnTimeRoundTripTest {

    private fun table() = TimeTableEntity(
        id = 1, name = "Test55", startDate = "2026-09-07",
        maxWeek = 20, nodesPerDay = 12,
        timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":12,"start":"21:45","end":"22:30"}]""",
        color = "#FF6750A4", isDefault = true
    )

    private fun ownTimeCourse() = CourseEntity(
        id = 0, groupId = "", tableId = 1,
        courseName = "OwnTimeCourse", teacher = "Prof X", room = "B201",
        day = 2, startNode = 11, step = 2,
        startWeek = 1, endWeek = 16, type = 0,
        color = "#FF6750A4",
        ownTime = true, startTime = "20:50", endTime = "22:00"
    )

    @Test
    fun shareText_exportsOwnTimeCourse_withStartEndTime() {
        val exported = exportWakeUpShareTextEncoded()
        val decoded = java.net.URLDecoder.decode(exported, "UTF-8")
        assertTrue(
            "分享文本导出(解码后)必须包含 startTime/endTime",
            decoded.contains("\"startTime\": \"20:50\"") && decoded.contains("\"endTime\": \"22:00\"")
        )
    }

    private fun exportWakeUpShareTextEncoded(): String {
        val exported = ScheduleExporter.exportWakeUpShareText(table(), listOf(ownTimeCourse()))
        // 抠出 URL-encoded courseDetailJson 段
        val m = Regex("courseDetailJson\":\\s*\"([^\"]+)\"").find(exported)
            ?: throw AssertionError("导出里找不到 courseDetailJson; got: $exported")
        return m.groupValues[1]
    }

    @Test
    fun wakeUpJson_exportsOwnTimeCourse_withStartEndTime() {
        val exported = ScheduleExporter.exportWakeUpJson(table(), listOf(ownTimeCourse()))
        assertTrue(
            "WakeUp JSON 导出必须包含 startTime/endTime; got: $exported",
            exported.contains("\"startTime\"") && exported.contains("\"endTime\"") &&
                exported.contains("20:50") && exported.contains("22:00")
        )
    }

    @Test
    fun shareText_roundTrip_preservesOwnTime() {
        val exported = ScheduleExporter.exportWakeUpShareText(table(), listOf(ownTimeCourse()))
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        assertTrue("ownTime 必须恢复; got ownTime=${c.ownTime} startTime=${c.startTime} endTime=${c.endTime}",
            c.ownTime)
        assertEquals("20:50", c.startTime)
        assertEquals("22:00", c.endTime)
    }

    @Test
    fun wakeUpJson_roundTrip_preservesOwnTime() {
        val exported = ScheduleExporter.exportWakeUpJson(table(), listOf(ownTimeCourse()))
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        assertTrue("ownTime 必须恢复; got ownTime=${c.ownTime} startTime=${c.startTime} endTime=${c.endTime}",
            c.ownTime)
        assertEquals("20:50", c.startTime)
        assertEquals("22:00", c.endTime)
    }

    @Test
    fun parser_backCompat_noOwnTimeFields_staysFalse() {
        // 原生 WakeUp 分享不带 startTime/endTime — 不得崩, 维持 ownTime=false 兼容
        val raw = """
            【来自Sleepy】课程分享：
            {"name":"T","startDate":"2026-09-07","courseDetailJson":"%5B%7B%22name%22%3A%22X%22%2C%22day%22%3A1%2C%22startNode%22%3A1%2C%22step%22%3A1%2C%22startWeek%22%3A1%2C%22endWeek%22%3A16%2C%22type%22%3A0%2C%22color%22%3A%22%23FF6750A4%22%2C%22teacher%22%3A%22%22%2C%22position%22%3A%22%22%7D%5D"}
        """.trimIndent()
        val result = ScheduleParser.parse(raw, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val c = result.getOrThrow().courses[0]
        assertFalse("缺字段时 ownTime 必须保持 false, 不得瞎填", c.ownTime)
        assertEquals("", c.startTime)
        assertEquals("", c.endTime)
    }
}

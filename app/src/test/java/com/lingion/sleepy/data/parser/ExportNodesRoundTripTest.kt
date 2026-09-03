package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16k 导出→导入闭环矩阵 — "导入导出都应该是无损的"(用户 2026-09-03):
 * 13 节课表(课程真到 13 节)经全部三种导出格式 shareText / JSON / ICS round-trip 后,
 * nodesPerDay 必须仍是 13, timeJson 不得缩水。任何一个格式丢 = 该格式有损。
 */
class ExportNodesRoundTripTest {

    /** 13 节课表: 作息声明含 13(稀疏: 只存 1/12/13 三个锚点), 课程实际到达 13 */
    private fun table13() = TimeTableEntity(
        id = 1,
        name = "13节表",
        startDate = "2026-02-23",
        maxWeek = 18,
        nodesPerDay = 13,
        timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":12,"start":"21:00","end":"21:45"},{"node":13,"start":"21:50","end":"22:35"}]""",
        color = "#FF6750A4",
        isDefault = true
    )

    private fun courses13() = listOf(
        CourseEntity(
            id = 0, groupId = "", tableId = 1,
            courseName = "补实验", teacher = "李平", room = "11#2003",
            day = 4, startNode = 11, step = 3,
            startWeek = 6, endWeek = 6, type = 0,
            color = "#FF6750A4"
        )
    )

    private fun assertNodes13(parsed: ScheduleParser.ParseResult, fmt: String) {
        assertEquals("[$fmt] 课程节次 startNode=11 step=3 全保真", 11, parsed.courses[0].startNode)
        assertEquals("[$fmt] step=3 全保真", 3, parsed.courses[0].step)
        assertEquals("[$fmt] 13 节能力 round-trip 后必须仍是 13", 13, parsed.nodesPerDay)
    }

    @Test
    fun shareText_roundTrip_preserves_13_nodes() {
        val exported = ScheduleExporter.exportWakeUpShareText(table13(), courses13())
        val parsed = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()
        assertNodes13(parsed, "shareText")
    }

    @Test
    fun wakeUpJson_roundTrip_preserves_13_nodes_and_time() {
        val exported = ScheduleExporter.exportWakeUpJson(table13(), courses13())
        val parsed = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()
        assertNodes13(parsed, "json")
        // JSON 带完整 tableInfo.time → timeJson 必须往返保真不缩水
        assertTrue("[json] timeJson 应保留作息", parsed.timeJson.isNotBlank())
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(parsed.timeJson)
        assertEquals("[json] timeJson 第13节不得丢", 13, nodes.maxOf { it.node })
    }

    @Test
    fun ics_roundTrip_preserves_13_nodes() {
        val exported = ScheduleExporter.exportIcs(table13(), courses13())
        val parsed = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()
        assertTrue("[ics] 应解析出课程", parsed.courses.isNotEmpty())
        assertNodes13(parsed, "ics")
    }
}

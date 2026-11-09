package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.PeriodTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.56 T11: 作息表单独导出契约。
 * - exportPeriodTableShareText: sleepy-v1 纯 P 区块文本(marker 包裹, 无 C 行)
 * - exportPeriodTableJson: {name, nodesPerDay, time[]} JSON
 * - 往返: 导出的两种格式都能被 ScheduleParser.parse 吃回 → T9 纯作息导入路径
 *   (courses.isEmpty + periodTable != null) 建一张作息表, 内容无损
 */
class PeriodTableOnlyExportTest {

    private val timeJson = """
        [{"node":1,"start":"08:20","end":"09:05"},
         {"node":2,"start":"09:15","end":"10:00"},
         {"node":3,"start":"10:20","end":"11:05"}]
    """.trimIndent()

    private fun entity() = PeriodTableEntity(
        id = 7L, name = "春季作息", nodesPerDay = 3, timeJson = timeJson
    )

    // ---- sleepy-v1 纯 P 文本 ----

    @Test
    fun shareText_contains_magic_and_period_rows() {
        val text = SleepyNativeExporter.exportPeriodTableShareText(entity())
        assertTrue(text.contains("<<<SLEEPY-BEGIN>>>"))
        assertTrue(text.contains("#sleepy-v1"))
        assertTrue(text.contains("P春季作息|7|3"))
        assertTrue(text.contains("Pn1|08:20|09:05"))
        assertTrue(text.contains("Pn3|10:20|11:05"))
        // 纯作息 = 零课程行, 零 T 行
        assertTrue(text.lines().none { it.startsWith("C") })
        assertTrue(text.lines().none { it.startsWith("T") })
    }

    @Test
    fun shareText_parses_back_to_pure_period_result() {
        val text = SleepyNativeExporter.exportPeriodTableShareText(entity())
        val r = ScheduleParser.parse(text, 1L).getOrThrow()
        assertEquals(0, r.courses.size)
        assertNotNull(r.periodTable)
        assertEquals("春季作息", r.periodTable!!.name)
        assertEquals(3, r.periodTable.nodesPerDay)
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.periodTable.timeJson)
        assertEquals(3, nodes.size)
        assertEquals("08:20", nodes[0].start.toString())
        assertEquals("11:05", nodes[2].end.toString())
    }

    @Test
    fun shareText_nd_preset_folds_to_pd() {
        val e = entity().copy(timeJson = com.lingion.sleepy.util.TimeTableUtils.DEFAULT_TIME_JSON)
        val text = SleepyNativeExporter.exportPeriodTableShareText(e)
        assertTrue(text.contains("Pd"))
    }

    // ---- JSON 格式 ----

    @Test
    fun json_contains_name_nodes_time() {
        val json = SleepyNativeExporter.exportPeriodTableJson(entity())
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("春季作息"))
        assertTrue(json.contains("\"nodesPerDay\""))
        assertTrue(json.contains("\"node\""))
        // timeList 用 WakeUp 原生字段名 — 解析端 harvest 按此收割
        assertTrue(json.contains("\"startTime\""))
        assertTrue(json.contains("\"endTime\""))
    }

    @Test
    fun json_parses_back_to_pure_period_result() {
        val json = SleepyNativeExporter.exportPeriodTableJson(entity())
        val r = ScheduleParser.parse(json, 1L).getOrThrow()
        assertEquals(0, r.courses.size)
        assertNotNull(r.periodTable)
        assertEquals("春季作息", r.periodTable!!.name)
        assertEquals(3, r.periodTable.nodesPerDay)
        assertEquals(3, com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.periodTable.timeJson).size)
    }

    // ---- 兜底: 旧 P 区块(带 C 行)不受影响 ----

    @Test
    fun mixed_export_still_carries_courses() {
        val doc = "#sleepy-v1\nT表|2026-03-02|20|3\nP春季作息|7|3\nPn1|08:20|09:05\nPn2|09:15|10:00\nPn3|10:20|11:05\nC高数|2|1-2|1-16"
        val r = ScheduleParser.parse(doc, 1L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertNotNull(r.periodTable)
    }

    @Test
    fun pure_period_without_time_rows_still_yields_null_period_table() {
        // P 头存在但零 Pn/Pd → 无法构成时间表 → periodTable=null(诚实) — T9 分支不触发
        val doc = "#sleepy-v1\nP春季作息|7|3\n"
        val r = ScheduleParser.parse(doc, 1L).getOrThrow()
        assertNull(r.periodTable)
    }
}

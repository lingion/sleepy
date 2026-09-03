package com.lingion.sleepy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16k 无损解析 — 用户 2026-09-03「导入导出都应该是无损的」:
 * 课程实际到达 13 节, 解析结果就必须承认 13 节 —
 * 即使粘贴文本没有任何作息时间块。没时间 ≠ 没节次。
 */
class ParseNodesLosslessTest {

    @Test
    fun simple_text_without_time_block_still_reports_max_course_node() {
        val text = """
            高数 张三 A101 1 1-2 1-16
            体育 李四 操场 3 12-13 1-16
            大物 王五 B202 2 13 1-16
        """.trimIndent()

        val result = ScheduleParser.parse(text, defaultTableId = 999L)

        val parsed = result.getOrThrow()
        assertEquals("课程到达 13 节, nodesPerDay 必须是 13", 13, parsed.nodesPerDay)
        assertTrue("没时间块就不该伪造时间(timeJson 留空由合并层铺底)", parsed.timeJson.isBlank())
    }

    @Test
    fun time_block_nodes_do_not_shrink_below_course_reach() {
        // TIME 块只声明到 10 节, 但课程到达 13 节 → 仍取 13
        val text = """
            <<<SLEEPY-TIME-BEGIN>>>
            第1节 08:00-08:45
            第10节 19:50-20:35
            <<<SLEEPY-TIME-END>>>
            高数 张三 A101 1 12-13 1-16
        """.trimIndent()

        val parsed = ScheduleParser.parse(text, defaultTableId = 999L).getOrThrow()
        assertEquals("max(作息声明, 课程到达) = 13", 13, parsed.nodesPerDay)
    }
}

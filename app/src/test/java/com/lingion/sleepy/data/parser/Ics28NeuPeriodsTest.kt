package com.lingion.sleepy.data.parser

import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #28 P1: NEU ics 导入"导入前自动识别每节课时间乱成一团"。
 * 报告者真实文件 (test-ics.zip → neu-schedule-2026-2027-1.ics, 与报告者文件逐字节一致)。
 *
 * NEU 导出语义: 五个教学块 [08:00-09:40, 10:00-11:40, 14:00-15:40, 16:00-17:40, 18:30-22:00]
 * 对应节次 [1-2, 3-4, 5-6, 7-8, 9-12]; 连排事件 (08:00-11:40 / 14:00-17:40) 跨两个教学块。
 *
 * 旧实现两个缺陷(在真实文件上同时发生):
 *  1) extractNeuTime 把同一开始时刻的"最长事件步数"套给该时刻的所有事件
 *     → 08:00-09:40(单节块 100min) 被拉成 1-4 节; 14:00-15:40 同理被拉成 5-8 节;
 *  2) harvestNodeTimes 长块路径把长事件自己的 start 写进 endNode.start,
 *     同节次被不同事件先后写 → 节次时间倒挂 (如 第4节 08:00-11:40 排在 第3节 10:00 之后)。
 */
class Ics28NeuPeriodsTest {

    private val ics: String by lazy {
        javaClass.getResourceAsStream("/neu-schedule-2026-2027-1.ics")
            ?.bufferedReader()?.use { it.readText() } ?: error("NEU fixture missing")
    }

    private fun parse() = ScheduleParser.parse(ics, defaultTableId = 1L).getOrThrow()

    @Test
    fun neuIcs_eventsSpanAtomicBlocks_notInflated() {
        val pr = parse()
        // 真实文件全部 7 种时间形态 → 期望映射。旧实现把 (1,2) 和 (5,2) 整体吞掉
        // (08:00/14:00 两个时刻的所有事件都被最长事件拉成 4 步), 所以 (1,2)/(5,2) 必缺。
        val expected = setOf(
            1 to 2, // 08:00-09:40 上午一
            1 to 4, // 08:00-11:40 上午连排(跨 1-2 + 3-4)
            3 to 2, // 10:00-11:40 上午二
            5 to 2, // 14:00-15:40 下午一
            5 to 4, // 14:00-17:40 下午连排(跨 5-6 + 7-8)
            7 to 2, // 16:00-17:40 下午二
            9 to 4  // 18:30-22:00 晚间(9-12)
        )
        val actual = pr.courses.map { it.startNode to it.step }.toSet()
        assertTrue(
            "span set mismatch: missing=${expected - actual} unexpected=${actual - expected}",
            actual == expected
        )
    }

    @Test
    fun neuIcs_timetableIsCompleteMonotonicAndAnchored() {
        val pr = parse()
        val rows = TimeTableUtils.parseTimeSlotRows(pr.timeJson)

        assertEquals("nodesPerDay", 12, pr.nodesPerDay)
        assertEquals("timetable rows", (1..12).toList(), rows.map { it.node })

        // 严格递增: 第 N+1 节开始不得早于第 N 节开始(旧实现第4节 08:00 排第3节 10:00 之后)
        for (i in 1 until rows.size) {
            assertTrue(
                "node ${rows[i].node} start ${rows[i].start} not after node ${rows[i - 1].node} start ${rows[i - 1].start}",
                rows[i - 1].start < rows[i].start
            )
        }
        // 每节 start < end
        rows.forEach { row ->
            assertTrue("node ${row.node} start>=end (${row.start}>=${row.end})", row.start < row.end)
        }

        // 教学块锚点: 块首节 start = 块起点钟点, 块末节 end = 块终点钟点
        val anchors = mapOf(
            1 to "08:00", 2 to "09:40",
            3 to "10:00", 4 to "11:40",
            5 to "14:00", 6 to "15:40",
            7 to "16:00", 8 to "17:40",
            9 to "18:30", 12 to "22:00"
        )
        anchors.forEach { (node, time) ->
            val row = rows.first { it.node == node }
            val actual = if (node in setOf(1, 3, 5, 7, 9)) row.start else row.end
            assertTrue("node $node expected $time got start=${row.start} end=${row.end}", actual == time)
        }
    }
}

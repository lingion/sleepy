package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16k 无损合并语义 — "哪个大用哪个"(用户 2026-09-03):
 * 原课表 10 节 + 导入源可达 13 节 → 合并结果必须是 13 节, 任何一方不得把另一方压小。
 * 节次时间: 导入源有真实时间用导入源; 导入源没有的回退原表; 两边都没有的默认时间不得重叠。
 */
class TimeTableUtilsMergeTest {

    private fun json(vararg rows: Triple<Int, String, String>) =
        TimeTableUtils.buildTimeJsonFromRows(rows.map { TimeTableUtils.TimeSlotRow(it.first, it.second, it.third) })

    private fun rowsOf(json: String) = TimeTableUtils.parseTimeSlotRows(json)

    @Test
    fun incoming_declares_13_real_times_over_current_10_result_is_13() {
        // 原表 10 节真实时间, 导入源带 13 节真实作息 → 结果 13 节, 时间以导入源为准
        val current = json(
            Triple(1, "08:00", "08:45"), Triple(2, "08:50", "09:35"),
            Triple(3, "10:00", "10:45"), Triple(4, "10:50", "11:35"),
            Triple(5, "14:00", "14:45"), Triple(6, "14:50", "15:35"),
            Triple(7, "16:00", "16:45"), Triple(8, "16:50", "17:35"),
            Triple(9, "19:00", "19:45"), Triple(10, "19:50", "20:35")
        )
        val incoming = json(
            Triple(1, "08:30", "09:10"), Triple(2, "09:20", "10:00"),
            Triple(3, "10:10", "10:50"), Triple(4, "11:00", "11:40"),
            Triple(5, "14:30", "15:10"), Triple(6, "15:20", "16:00"),
            Triple(7, "16:10", "16:50"), Triple(8, "17:00", "17:40"),
            Triple(9, "19:30", "20:10"), Triple(10, "20:20", "21:00"),
            Triple(11, "21:10", "21:50"), Triple(12, "22:00", "22:40"),
            Triple(13, "22:50", "23:30")
        )

        val merged = TimeTableUtils.mergeMostComplete(current, incoming)

        val rows = rowsOf(merged)
        assertEquals("节次数必须 = max(双方) = 13, 导入源不得被原表压小", 13, rows.size)
        assertEquals("有真实时间时以导入源为准", "08:30", rows[0].start)
        assertEquals("第13节取导入源真实时间", "22:50", rows[12].start)
        assertEquals("23:30", rows[12].end)
    }

    @Test
    fun incoming_blank_current_10_courses_reach_13_defaults_fill_tail_in_order() {
        // 用户实际场景: 粘贴文本无 TIME 块(incoming 空), 原表 10 节, 导入课程到 13 节
        // → 结果 13 节: 1..10 用原表真实时间, 11..13 的默认时间不得重叠
        val current = json(
            Triple(1, "08:00", "08:45"), Triple(2, "08:50", "09:35"),
            Triple(3, "10:00", "10:45"), Triple(4, "10:50", "11:35"),
            Triple(5, "14:00", "14:45"), Triple(6, "14:50", "15:35"),
            Triple(7, "16:00", "16:45"), Triple(8, "16:50", "17:35"),
            Triple(9, "19:00", "19:45"), Triple(10, "19:50", "20:35")
        )

        val merged = TimeTableUtils.mergeMostComplete(current, "", requiredNodeCount = 13)

        val rows = rowsOf(merged)
        assertEquals("13 节可达就必须是 13 节", 13, rows.size)
        assertEquals("1..10 保留原表真实时间", "08:00", rows[0].start)
        assertEquals("19:50", rows[9].start)
        assertEquals("第11节使用逐节默认起始", "20:50", rows[10].start)
        assertTrue("扩展作息必须顺序有效", TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun incoming_shorter_than_current_never_shrinks() {
        // 导入源只声明 5 节, 原表 10 节 → 结果 10 节: 1..5 导入源, 6..10 原表
        val current = json(
            Triple(1, "08:00", "08:45"), Triple(2, "08:50", "09:35"),
            Triple(3, "10:00", "10:45"), Triple(4, "10:50", "11:35"),
            Triple(5, "14:00", "14:45"), Triple(6, "14:50", "15:35"),
            Triple(7, "16:00", "16:45"), Triple(8, "16:50", "17:35"),
            Triple(9, "19:00", "19:45"), Triple(10, "19:50", "20:35")
        )
        val incoming = json(
            Triple(1, "08:30", "09:10"), Triple(2, "09:20", "10:00"),
            Triple(3, "10:10", "10:50"), Triple(4, "11:00", "11:40"),
            Triple(5, "14:30", "15:10")
        )

        val merged = TimeTableUtils.mergeMostComplete(current, incoming)

        val rows = rowsOf(merged)
        assertEquals("导入源短不得把原表压小", 10, rows.size)
        assertEquals("1..5 导入源优先", "08:30", rows[0].start)
        assertEquals("6..10 原表补位", "14:50", rows[5].start)
        assertEquals("20:35", rows[9].end)
    }

    @Test
    fun both_blank_returns_current_unchanged() {
        val current = TimeTableUtils.DEFAULT_TIME_JSON
        val merged = TimeTableUtils.mergeMostComplete("", "")
        assertEquals(current, merged)
    }

    @Test
    fun required_node_count_extends_beyond_both_sources() {
        // 双方都只有 10 节, 但导入课程实际到达 13 节(startNode+step-1=13)
        // → 必须拓到 13 节, 课程才有格子渲染(无损硬要求)
        val current = json(Triple(1, "08:00", "08:45"))
        val incoming = json(Triple(1, "08:30", "09:10"), Triple(2, "09:20", "10:00"))

        val merged = TimeTableUtils.mergeMostComplete(current, incoming, requiredNodeCount = 13)

        val rows = rowsOf(merged)
        assertEquals(13, rows.size)
        assertEquals("导入源优先", "08:30", rows[0].start)
        assertEquals("超出双方声明的节次用逐节默认", "20:50", rows[10].start)
    }

    private val reportedTimeJson = """
        [{"node":1,"start":"08:30","end":"09:15"},
         {"node":2,"start":"09:20","end":"10:05"},
         {"node":3,"start":"10:25","end":"11:10"},
         {"node":4,"start":"11:15","end":"12:00"},
         {"node":5,"start":"13:50","end":"14:35"},
         {"node":6,"start":"14:40","end":"15:25"},
         {"node":7,"start":"15:30","end":"16:15"},
         {"node":8,"start":"16:30","end":"17:15"},
         {"node":9,"start":"17:20","end":"18:05"},
         {"node":10,"start":"18:30","end":"19:15"},
         {"node":11,"start":"19:20","end":"20:05"},
         {"node":12,"start":"20:10","end":"20:55"}]
    """.trimIndent()

    @Test
    fun generatedThirteenthPeriod_doesNotOverlapReportedTwelfthPeriod() {
        val merged = TimeTableUtils.mergeMostComplete("", reportedTimeJson, requiredNodeCount = 13)
        val rows = rowsOf(merged)
        assertEquals(13, rows.size)
        assertEquals(rowsOf(reportedTimeJson), rows.take(12))
        assertTrue("Generated period 13 must follow period 12: $rows", TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun multipleGeneratedTailPeriods_remainInOrder() {
        val merged = TimeTableUtils.mergeMostComplete("", reportedTimeJson, requiredNodeCount = 14)
        assertEquals(14, rowsOf(merged).size)
        assertTrue("Generated tail periods must not overlap one another", TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun absentSourceTimes_useIndividualDefaultPeriodsBeforeExtendingTail() {
        val merged = TimeTableUtils.mergeMostComplete("", "", requiredNodeCount = 13)
        assertEquals(13, rowsOf(merged).size)
        assertTrue("Generated defaults must form a valid timetable", TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun generatedTailWithoutRoomInDay_staysBlankForUserToConfigure() {
        val source = json(Triple(1, "23:00", "23:50"))
        val rows = rowsOf(TimeTableUtils.mergeMostComplete("", source, requiredNodeCount = 3))
        assertEquals(3, rows.size)
        assertEquals(rowsOf(source)[0], rows[0])
        assertEquals("", rows[1].start)
        assertEquals("", rows[1].end)
        assertEquals("", rows[2].start)
        assertEquals("", rows[2].end)
    }

    @Test
    fun explicitOverlappingTimes_arePreservedForValidationRatherThanRewritten() {
        val source = json(Triple(1, "08:00", "09:00"), Triple(2, "08:30", "09:15"))
        val merged = TimeTableUtils.mergeMostComplete("", source)
        assertEquals(rowsOf(source), rowsOf(merged))
        assertFalse(TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun missingInteriorRows_useSeparatePeriodsWithoutChangingSourceAnchors() {
        val source = json(Triple(1, "08:00", "08:45"), Triple(4, "10:55", "11:40"))
        val merged = TimeTableUtils.mergeMostComplete("", source)
        assertEquals(4, rowsOf(merged).size)
        assertEquals(rowsOf(source), rowsOf(merged).filter { it.node == 1 || it.node == 4 })
        assertTrue("Missing interior periods must not share overlapping block defaults", TimeTableUtils.isPeriodTableValid(merged))
    }

    @Test
    fun interiorGapTooShortForDefault_isLeftBlankWithoutMovingSourceTimes() {
        val source = json(Triple(1, "08:30", "09:15"), Triple(3, "09:30", "10:15"))
        val rows = rowsOf(TimeTableUtils.mergeMostComplete("", source))
        assertEquals(rowsOf(source), rows.filter { it.node != 2 })
        assertEquals("", rows[1].start)
        assertEquals("", rows[1].end)
    }

    @Test
    fun partialSourceClock_doesNotReceiveAnEndEarlierThanItsStart() {
        val source = json(Triple(1, "08:30", "09:15"), Triple(2, "14:00", ""))
        val rows = rowsOf(TimeTableUtils.mergeMostComplete("", source))
        assertEquals("14:00", rows[1].start)
        assertTrue("Missing end must remain blank or follow the supplied start",
            rows[1].end.isBlank() || rows[1].end > rows[1].start)
    }

    @Test
    fun partialSourceClock_doesNotReceiveAStartAfterItsEnd() {
        val source = json(Triple(1, "08:30", "09:15"), Triple(2, "", "07:30"))
        val rows = rowsOf(TimeTableUtils.mergeMostComplete("", source))
        assertEquals("07:30", rows[1].end)
        assertEquals("", rows[1].start)
    }
}

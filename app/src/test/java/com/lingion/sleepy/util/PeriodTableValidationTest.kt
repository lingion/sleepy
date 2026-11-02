package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次作息防呆校验契约测试 (issue #29)
 * 1. 相邻节次区间不重叠 (下一节开始不得早于上一节结束)
 * 2. 钟点为有效的分钟级 HH:mm，凌晨课程不被擅自改为下午
 * 3. 节次时间有序，异常明确提示，不自动推迟课程
 * 4. 拒绝保存 + 明确提示冲突
 */
class PeriodTableValidationTest {

    @Test
    fun defaultTimeJsonIsValid() {
        val rows = TimeTableUtils.parseTimeSlotRows(TimeTableUtils.DEFAULT_TIME_JSON)
        val result = TimeTableUtils.validatePeriodRows(rows)
        assertTrue("默认作息表必须合法", result is TimeTableUtils.PeriodValidationResult.Valid)
        assertTrue("isPeriodTableValid 必须返回 true", TimeTableUtils.isPeriodTableValid(TimeTableUtils.DEFAULT_TIME_JSON))
    }

    @Test
    fun overlapBetweenAdjacentNodesIsRejected() {
        // 第 1 节 08:00–09:40，第 2 节 09:30–10:15 (重叠 10 分钟)
        val rows = listOf(
            TimeSlotRow(1, "08:00", "09:40"),
            TimeSlotRow(2, "09:30", "10:15"),
            TimeSlotRow(3, "10:30", "11:15")
        )
        val result = TimeTableUtils.validatePeriodRows(rows)
        assertTrue("相邻节次重叠必须被拒绝", result is TimeTableUtils.PeriodValidationResult.Overlap)
        val overlap = result as TimeTableUtils.PeriodValidationResult.Overlap
        assertEquals(1, overlap.prevNode)
        assertEquals("09:40", overlap.prevEnd)
        assertEquals(2, overlap.nextNode)
        assertEquals("09:30", overlap.nextStart)

        val msg = overlap.errorMessage(null)
        assertTrue("提示必须包含冲突节次与时间", msg.contains("第 1 节") && msg.contains("第 2 节") && msg.contains("09:40") && msg.contains("09:30"))
    }

    @Test
    fun orderedEarlyMorningPeriodsAreNotGuessedToBeAfternoon() {
        val rows = listOf(TimeSlotRow(1, "01:00", "01:45"), TimeSlotRow(2, "08:00", "08:45"))
        assertTrue(TimeTableUtils.validatePeriodRows(rows) is TimeTableUtils.PeriodValidationResult.Valid)
        assertTrue(TimeTableUtils.isTimeSlotOrderValid(rows))
        assertEquals(null, TimeTableUtils.findConflictForSlot(rows, 1))
    }

    @Test
    fun middleNodeClockRegressionIsRejectedAsOverlap() {
        // 第 4 节上午 11:40 结束，第 5 节误改成了 01:00–01:45
        val rows = listOf(
            TimeSlotRow(4, "10:55", "11:40"),
            TimeSlotRow(5, "01:00", "01:45")
        )
        val result = TimeTableUtils.validatePeriodRows(rows)
        // Reject the actual chronological overlap, not the early-morning clock by itself.
        assertFalse("中间节次小时回绕必须被拒绝", result is TimeTableUtils.PeriodValidationResult.Valid)
    }

    @Test
    fun endBeforeStartIsRejected() {
        // 单节开始晚于结束 (如 09:40–08:50)
        val rows = listOf(
            TimeSlotRow(1, "09:40", "08:50")
        )
        val result = TimeTableUtils.validatePeriodRows(rows)
        assertTrue("结束早于开始必须被拒绝", result is TimeTableUtils.PeriodValidationResult.EndBeforeStart)
        val err = result as TimeTableUtils.PeriodValidationResult.EndBeforeStart
        assertEquals(1, err.node)
        assertTrue(err.errorMessage(null).contains("需晚于开始时间"))
    }

    @Test
    fun emptyRowsIsRejected() {
        val result = TimeTableUtils.validatePeriodRows(emptyList())
        assertTrue("空作息表必须被拒绝", result is TimeTableUtils.PeriodValidationResult.Empty)
    }

    @Test
    fun invalidFormatIsRejected() {
        val rows = listOf(TimeSlotRow(1, "invalid", "08:45"))
        val result = TimeTableUtils.validatePeriodRows(rows)
        assertTrue("格式非法必须被拒绝", result is TimeTableUtils.PeriodValidationResult.InvalidFormat)
    }

    @Test
    fun findConflictForSlotIdentifiesRealtimeError() {
        val rows = listOf(
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "08:40", "09:25"), // 与第 1 节冲突
            TimeSlotRow(3, "10:00", "10:45")
        )
        val conflict1 = TimeTableUtils.findConflictForSlot(rows, 1)
        assertTrue("第 1 节检测出与第 2 节冲突", conflict1 is TimeTableUtils.PeriodValidationResult.Overlap)

        val conflict2 = TimeTableUtils.findConflictForSlot(rows, 2)
        assertTrue("第 2 节检测出与第 1 节冲突", conflict2 is TimeTableUtils.PeriodValidationResult.Overlap)

        val conflict3 = TimeTableUtils.findConflictForSlot(rows, 3)
        assertEquals("第 3 节无冲突", null, conflict3)
    }

    @Test
    fun isTimeSlotOrderValidPreventsOverlappingDrag() {
        val overlapping = listOf(
            TimeSlotRow(1, "08:00", "09:00"),
            TimeSlotRow(2, "08:30", "09:30")
        )
        assertFalse("重叠行不允许通过 isTimeSlotOrderValid", TimeTableUtils.isTimeSlotOrderValid(overlapping))

        val wrapAround = listOf(
            TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "01:00", "01:45")
        )
        assertFalse("小时回绕不允许通过 isTimeSlotOrderValid", TimeTableUtils.isTimeSlotOrderValid(wrapAround))
    }

    @Test
    fun displayFormattingPreservesRealClocksAndReportsOverlap() {
        val early = """[{"node":1,"start":"01:00","end":"01:45"}]"""
        assertEquals(early, TimeTableUtils.sanitizeTimeJsonForDisplay(early))
        val overlapping = """[{"node":1,"start":"08:00","end":"09:40"},{"node":2,"start":"09:30","end":"10:15"}]"""
        assertEquals(overlapping, TimeTableUtils.sanitizeTimeJsonForDisplay(overlapping))
        assertTrue(TimeTableUtils.validateTimeJson(overlapping) is TimeTableUtils.PeriodValidationResult.Overlap)
    }
}

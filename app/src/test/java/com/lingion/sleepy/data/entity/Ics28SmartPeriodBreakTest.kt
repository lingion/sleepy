package com.lingion.sleepy.data.entity

import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * issue #28 P2 数据链路守卫: 自动模式"添加课间"依赖的推导链
 * SmartPeriodConfig.copy(breaks/transitionAssignments).derive() → buildTimeJsonFromRows
 * 必须真实生效 — 导入确认框导入路径曾因 smartConfig 回调没接线而整条链路哑掉(见
 * ImportConfirmDialog / JwImportActivity 的 TimeSlotEditor 调用), 本测试锁死
 * 该链路本身的语义, 接线修复后按钮改动经此链路落到行。
 */
class Ics28SmartPeriodBreakTest {

    private val withBreak = SmartPeriodConfig(
        breaks = listOf(BreakOption(minutes = 20)),
        transitionAssignments = List(11) { i -> if (i == 1) 0 else null } // 第2节后加大课间
    )

    @Test
    fun baseDerive_isContinuousFortyFiveMinPeriods() {
        val rows = SmartPeriodConfig().derive()
        assertEquals(12, rows.size)
        assertEquals("08:00" to "08:45", rows[0].start to rows[0].end)
        assertEquals("08:45" to "09:30", rows[1].start to rows[1].end)
        assertEquals("09:30" to "10:15", rows[2].start to rows[2].end)
        assertEquals("16:15" to "17:00", rows.last().start to rows.last().end)
    }

    @Test
    fun addingBreak_shiftsOnlyRowsAfterTheBreak() {
        val base = SmartPeriodConfig().derive()
        val rows = withBreak.derive()

        // 课间之前的节次不动
        assertEquals(base[0].start to base[0].end, rows[0].start to rows[0].end)
        assertEquals(base[1].start to base[1].end, rows[1].start to rows[1].end)
        // 第2节后加 20min → 第3节开始 09:30 → 09:50
        assertEquals("09:50" to "10:35", rows[2].start to rows[2].end)
        // 之后所有节次整体后移 20min (末节结束 17:00 → 17:20)
        assertEquals("17:20", rows.last().end)
    }

    @Test
    fun derivedRows_roundTripThroughTimeJson_lossless() {
        val expected = withBreak.derive()
        val json = TimeTableUtils.buildTimeJsonFromRows(expected)
        val rows = TimeTableUtils.parseTimeSlotRows(json)
        assertEquals(expected.map { Triple(it.node, it.start, it.end) }, rows.map { Triple(it.node, it.start, it.end) })
    }
}

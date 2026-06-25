package com.lingion.sleepy.data.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPeriodConfigTest {

    @Test
    fun `derive - default 0 minute breaks (consecutive)`() {
        // 100 节 × 1 分钟, 默认全 0 分钟
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 1,
            totalPeriods = 5,
            breaks = emptyList(),
            transitionAssignments = emptyList()
        )
        val rows = cfg.derive()
        assertEquals(5, rows.size)
        assertEquals("08:00", rows[0].start)
        assertEquals("08:01", rows[0].end)
        assertEquals("08:01", rows[1].start) // 连续
        assertEquals("08:05", rows[4].end)   // 5节 × 1分钟 = 08:05
    }

    @Test
    fun `derive - one small break per transition`() {
        // 5 节 × 1 分钟, 每个 transition 用 1分钟小课间
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 1,
            totalPeriods = 5,
            breaks = listOf(BreakOption(1, isLong = false)),
            transitionAssignments = listOf(0, 0, 0, 0)
        )
        val rows = cfg.derive()
        assertEquals(5, rows.size)
        assertEquals("08:00", rows[0].start)
        assertEquals("08:01", rows[0].end)
        assertEquals("08:02", rows[1].start) // +1min break
        assertEquals("08:03", rows[1].end)
        assertEquals("08:04", rows[2].start) // +1min break
        assertEquals("08:09", rows[4].end)   // 5节 × 1分钟 + 4 × 1分钟 = 09
    }

    @Test
    fun `derive - HEU real example 5 breaks`() {
        // HEU: 5 个 break, 索引 0=10分钟小课间, 1=15分钟大课间, 2=65分钟大课间(午休)
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 45,
            totalPeriods = 5,
            breaks = listOf(
                BreakOption(10, isLong = false),  // 0
                BreakOption(15, isLong = true),   // 1
                BreakOption(65, isLong = true),   // 2 (午休)
            ),
            // transition 0: 1→2, small (10min)
            // transition 1: 2→3, big (15min)
            // transition 2: 3→4, small (10min)
            // transition 3: 4→5, big (15min)
            // transition 4 omitted (5→6 lunch, but we only have 5 periods)
            transitionAssignments = listOf(0, 1, 0, 1, null)
        )
        val rows = cfg.derive()
        assertEquals(5, rows.size)
        // 第1节 08:00 - 08:45
        assertEquals("08:00", rows[0].start)
        assertEquals("08:45", rows[0].end)
        // 第2节 08:55 (08:45 + 10min) - 09:40
        assertEquals("08:55", rows[1].start)
        assertEquals("09:40", rows[1].end)
        // 第3节 09:55 (09:40 + 15min) - 10:40
        assertEquals("09:55", rows[2].start)
        assertEquals("10:40", rows[2].end)
        // 第4节 10:50 (10:40 + 10min) - 11:35
        assertEquals("10:50", rows[3].start)
        assertEquals("11:35", rows[3].end)
        // 第5节 11:50 (11:35 + 15min) - 12:35
        assertEquals("11:50", rows[4].start)
        assertEquals("12:35", rows[4].end)
    }

    @Test
    fun `effectiveAssignments - null positions default to 0 minute`() {
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 45,
            totalPeriods = 5,
            breaks = listOf(BreakOption(10)),
            transitionAssignments = listOf(null, 0, null, null)
        )
        val assigns = cfg.effectiveAssignments()
        assertEquals(4, assigns.size)
        assertEquals(null, assigns[0])
        assertEquals(0, assigns[1])
        assertEquals(null, assigns[2])
        assertEquals(null, assigns[3])
    }

    @Test
    fun `effectiveAssignments - invalid index fallback to null`() {
        // 索引越界 -> 视为 null (0 分钟)
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 45,
            totalPeriods = 5,
            breaks = listOf(BreakOption(10)),
            transitionAssignments = listOf(5, 0, -1, 99) // 越界
        )
        val assigns = cfg.effectiveAssignments()
        // 越界 -> null
        assertEquals(null, assigns[0])
        assertEquals(0, assigns[1])
        assertEquals(null, assigns[2])
        assertEquals(null, assigns[3])
    }

    @Test
    fun `effectiveTransitionMinutes - mixed breaks`() {
        val cfg = SmartPeriodConfig(
            startTime = "08:00",
            periodMinutes = 45,
            totalPeriods = 5,
            breaks = listOf(
                BreakOption(10, false), // 0
                BreakOption(15, true)   // 1
            ),
            transitionAssignments = listOf(0, 1, null, 0)
        )
        val mins = cfg.effectiveTransitionMinutes()
        assertEquals(listOf(10, 15, 0, 10), mins)
    }

    @Test
    fun `total periods minimum 1`() {
        val cfg = SmartPeriodConfig(totalPeriods = 1)
        val rows = cfg.derive()
        assertEquals(1, rows.size)
    }
}
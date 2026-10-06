package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLiveCardStateTest {
    @Test
    fun `progress follows OPPO window from zero to one hundred`() {
        assertEquals(0, progressPercent(1_000L, 11_000L, 1_000L))
        assertEquals(50, progressPercent(1_000L, 11_000L, 6_000L))
        assertEquals(100, progressPercent(1_000L, 11_000L, 11_000L))
    }

    @Test
    fun `progress clamps before and after window`() {
        assertEquals(0, progressPercent(1_000L, 11_000L, 0L))
        assertEquals(100, progressPercent(1_000L, 11_000L, 20_000L))
    }

    @Test
    fun `state exposes active lifecycle and shared detail text`() {
        val state = CourseLiveCardState(
            courseName = "高等数学",
            room = "A301",
            teacher = "张老师",
            startTime = "08:00",
            notifyEpoch = 1_000L,
            classEpoch = 11_000L,
            nowEpoch = 6_000L,
            updateSequence = 3
        )

        assertTrue(state.isActive)
        assertEquals(50, state.progress)
        assertEquals("高等数学", state.primaryText)
        assertEquals("08:00  ·  A301  ·  张老师", state.detailText)
    }

    @Test
    fun `state ends at class epoch`() {
        val state = CourseLiveCardState(
            courseName = "英语",
            room = "B202",
            teacher = "",
            startTime = "10:00",
            notifyEpoch = 1_000L,
            classEpoch = 11_000L,
            nowEpoch = 11_000L
        )

        assertFalse(state.isActive)
        assertEquals(100, state.progress)
        assertEquals("10:00  ·  B202", state.detailText)
    }

    @Test
    fun `minutesLeft rounds up and floors at one`() {
        val base = CourseLiveCardState(
            courseName = "数学",
            room = "A1",
            teacher = "",
            startTime = "08:00",
            notifyEpoch = 0L,
            classEpoch = 600_000L,
            nowEpoch = 0L
        )
        // 10 分钟整 → 10
        assertEquals(10, base.copy(nowEpoch = 0L).minutesLeft)
        // 剩 90 秒 → 向上取整为 2
        assertEquals(2, base.copy(nowEpoch = 600_000L - 90_000L).minutesLeft)
        // 已到点 → 至少 1
        assertEquals(1, base.copy(nowEpoch = 600_000L).minutesLeft)
    }

    @Test
    fun `timeRangeText shows start-end only when end differs`() {
        val base = CourseLiveCardState(
            courseName = "数学",
            room = "",
            teacher = "",
            startTime = "14:00",
            notifyEpoch = 0L,
            classEpoch = 1L,
            nowEpoch = 0L
        )
        assertEquals("14:00", base.timeRangeText)
        assertEquals("14:00 - 15:50", base.copy(endTime = "15:50").timeRangeText)
        assertEquals("14:00", base.copy(endTime = "14:00").timeRangeText)
    }

    @Test
    fun `colorOsDetailText joins time room teacher with middot`() {
        val state = CourseLiveCardState(
            courseName = "数据结构",
            room = "实训楼307",
            teacher = "叶晟",
            startTime = "14:00",
            notifyEpoch = 0L,
            classEpoch = 1L,
            nowEpoch = 0L,
            endTime = "15:50"
        )
        assertEquals("14:00 - 15:50 · 实训楼307 · 叶晟", state.colorOsDetailText)
        assertEquals("14:00 - 15:50 · 实训楼307", state.copy(teacher = "").colorOsDetailText)
        assertEquals("14:00 · 实训楼307", state.copy(teacher = "", endTime = "").colorOsDetailText)
    }
}

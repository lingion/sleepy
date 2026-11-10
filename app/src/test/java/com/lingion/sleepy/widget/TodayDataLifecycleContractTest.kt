package com.lingion.sleepy.widget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodayDataLifecycleContractTest {
    @Test
    fun `nextMidnightDelayMillis computes wall-clock distance to next local midnight`() {
        // 23:59:30 → 30s
        assertEquals(
            30_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 9, 10, 23, 59, 30))
        )
        // 午夜整点 → 距"下一个"午夜整 24h (恒 >0, 不会 0ms 连环触发)
        assertEquals(
            86_400_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 9, 10, 0, 0, 0))
        )
        // 13:45 → 10h15m
        assertEquals(
            36_900_000L,
            WidgetUpdater.nextMidnightDelayMillis(LocalDateTime.of(2026, 3, 8, 13, 45, 0))
        )
    }

    @Test
    fun `nextMidnightDelayMillis is always positive`() {
        // 全天逐时采样, 恒 (0, 24h]
        for (hour in 0..23) {
            val delay = WidgetUpdater.nextMidnightDelayMillis(
                LocalDateTime.of(2026, 9, 10, hour, 0, 0)
            )
            assertTrue("hour=$hour delay=$delay 必须 >0", delay > 0)
            assertTrue("hour=$hour delay=$delay 必须 ≤24h", delay <= 86_400_000L)
        }
    }
}

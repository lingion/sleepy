package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Task 4 虚拟时钟核心语义：
 *  - setAbsolute 后日期/时间 = 目标时刻；
 *  - offsetBy 平移 millis 且 offsetMs 可查；
 *  - reset 回真实时间；
 *  - 时区固定 Asia/Shanghai（与全 app 业务日期口径一致）。
 */
class AppClockTest {

    @After
    fun tearDown() = AppClock.reset()

    @Test
    fun setAbsolute_shiftsDateAndTimeToTarget() {
        // 2027-01-01 08:30 Asia/Shanghai
        val target = LocalDate.of(2027, 1, 1).atTime(8, 30)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        AppClock.setAbsolute(target)
        assertEquals(LocalDate.of(2027, 1, 1), LocalDate.now(AppClock))
        // setAbsolute 用真实 System.currentTimeMillis() 反算 offset, 断言时跨出 1ms tick
        // 会读到 08:30:00.001; 截到秒吸收 tick, 真实偏移错误仍由下一行 ±2ms 兜住
        assertEquals(LocalTime.of(8, 30), LocalTime.now(AppClock).truncatedTo(ChronoUnit.SECONDS))
        assertEquals(target.toDouble(), AppClock.millis().toDouble(), 2.0)
    }

    @Test
    fun absoluteByDayOffset_movesExactlyNDays() {
        val realToday = LocalDate.now(AppClock)
        AppClock.setAbsolute(AppClock.millis() + 10L * 24 * 3600_000)
        assertEquals(realToday.plusDays(10), LocalDate.now(AppClock))
    }

    @Test
    fun offsetMoves_millisDiffEqualsDelta() {
        val before = AppClock.millis()
        AppClock.offsetBy(3_600_000L)
        val after = AppClock.millis()
        assertEquals(3_600_000.0, (after - before).toDouble(), 5.0)
        assertEquals(3_600_000L, AppClock.offsetMs)
    }

    @Test
    fun offsetBy_isCumulative() {
        AppClock.offsetBy(1_000L)
        AppClock.offsetBy(2_000L)
        assertEquals(3_000L, AppClock.offsetMs)
    }

    @Test
    fun reset_returnsToRealTime() {
        AppClock.offsetBy(86_400_000L)
        AppClock.reset()
        assertEquals(0L, AppClock.offsetMs)
        assertEquals(System.currentTimeMillis().toDouble(), AppClock.millis().toDouble(), 2.0)
    }

    @Test
    fun zoneIsShanghai_likeRestOfApp() {
        assertEquals(ZoneId.of("Asia/Shanghai"), AppClock.getZone())
    }

    @Test
    fun instantNow_equivalentToMillis() {
        val i = Instant.now(AppClock)
        assertEquals(AppClock.millis().toDouble(), i.toEpochMilli().toDouble(), 2.0)
    }
}

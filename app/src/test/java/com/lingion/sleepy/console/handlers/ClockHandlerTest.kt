package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.util.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

/**
 * /v1/clock 命令面纯逻辑：offset/absolute/reset 三模式 × dayOffset 糖。
 * applyCommand 注入 now/realNow, 断言 AppClock 状态迁移与回包结构。
 */
class ClockHandlerTest {

    @After
    fun tearDown() = AppClock.reset()

    private val real = 1_765_000_000_000L // 任意固定"真实 now"

    @Test
    fun offsetWithDeltaMs_shiftsAndReports() {
        AppClock.reset()
        val (data, err) = ClockHandler.applyCommand(
            """{"mode":"offset","deltaMs":3600000}""",
            nowMillis = AppClock.millis(), realNowMillis = real
        )
        assertNull(err)
        assertNotNull(data)
        assertEquals(3_600_000L, AppClock.offsetMs)
        val o = JSONObject(data!!)
        assertEquals(3_600_000L, o.getLong("offsetMs"))
        assertEquals(
            o.getLong("realNowEpochMs") + o.getLong("offsetMs"),
            o.getLong("virtualNowEpochMs")
        )
    }

    @Test
    fun offsetWithDayOffset_movesWholeDays() {
        AppClock.reset()
        val (data, err) = ClockHandler.applyCommand(
            """{"mode":"offset","dayOffset":-3}""",
            nowMillis = AppClock.millis(), realNowMillis = real
        )
        assertNull(err)
        assertEquals(-3L * 86_400_000L, AppClock.offsetMs)
        assertEquals(-3L * 86_400_000L, JSONObject(data!!).getLong("offsetMs"))
    }

    @Test
    fun absoluteWithEpochMs_pinsOffsetToTarget() {
        AppClock.reset()
        val target = real + 5L * 86_400_000L
        val (data, err) = ClockHandler.applyCommand(
            """{"mode":"absolute","epochMs":$target}""",
            nowMillis = AppClock.millis(), realNowMillis = real
        )
        assertNull(err)
        // offset = target - System.currentTimeMillis(); 断言回包自洽
        val o = JSONObject(data!!)
        assertEquals(o.getLong("virtualNowEpochMs"), o.getLong("realNowEpochMs") + o.getLong("offsetMs"))
    }

    @Test
    fun absoluteWithDayOffset_pinsRealNowPlusDays() {
        AppClock.reset()
        val (data, err) = ClockHandler.applyCommand(
            """{"mode":"absolute","dayOffset":14}""",
            nowMillis = AppClock.millis(), realNowMillis = real
        )
        assertNull(err)
        val o = JSONObject(data!!)
        assertEquals(o.getLong("virtualNowEpochMs"), o.getLong("realNowEpochMs") + o.getLong("offsetMs"))
        // 注入的 realNowMillis 是固定历史时刻, 生产偏移用 System.currentTimeMillis()
        // — 断言虚拟化目标 = realNow + 14 天 与回包 virtualNow 一致(容差=执行间隔)。
        assertEquals((real + 14L * 86_400_000L).toDouble(), o.getLong("virtualNowEpochMs").toDouble(), 5_000.0)
    }

    @Test
    fun reset_zeroesOffset() {
        AppClock.offsetBy(999_999L)
        val (data, err) = ClockHandler.applyCommand(
            """{"mode":"reset"}""",
            nowMillis = AppClock.millis(), realNowMillis = real
        )
        assertNull(err)
        assertEquals(0L, AppClock.offsetMs)
        assertEquals(0L, JSONObject(data!!).getLong("offsetMs"))
    }

    @Test
    fun badInputs_400NotThrow() {
        listOf(
            "",
            "not json",
            """{"mode":"warp"}""",
            """{"mode":"offset"}""",
            """{"mode":"absolute"}""",
        ).forEach { body ->
            val (data, err) = ClockHandler.applyCommand(body, AppClock.millis(), real)
            assertNull("违例 body 必须 data=null: $body", data)
            assertNotNull("违例 body 必须带 400 说明: $body", err)
        }
    }
}

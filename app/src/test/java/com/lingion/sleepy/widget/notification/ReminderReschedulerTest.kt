package com.lingion.sleepy.widget.notification

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ReminderRescheduler 防抖契约 — 真实时钟短窗口 (debounceMs=40), 不引 coroutines-test。
 * 核心不变量: 窗口内 N 次 request 只触发 1 次 action; 窗口后新 request 再触发一次。
 */
class ReminderReschedulerTest {

    @After fun tearDown() = ReminderRescheduler.resetForTest()

    @Test
    fun `窗口内连发 只触发一次重排`() = runBlocking {
        var count = 0
        // 窗口 600ms / 连发间隔 20ms: 全量 3210 套并发下测试线程可能被
        // GC/调度停顿几十~几百 ms, 旧参数(40ms/5ms)停一次就横跨窗口 →
        // pending 提前触发 + 后续 request 重开第二个 → count=2 偶发红。
        // 停顿须超 600ms 才能复现, 与防抖语义(静默够久即触发)不冲突。
        ReminderRescheduler.init(debounceMs = 600) { count++ }
        repeat(8) { ReminderRescheduler.request(); delay(20) }
        delay(1500)
        assertEquals(1, count)
    }

    @Test
    fun `窗口结束后再次请求 触发第二次`() = runBlocking {
        var count = 0
        ReminderRescheduler.init(debounceMs = 40) { count++ }
        ReminderRescheduler.request()
        delay(200)
        ReminderRescheduler.request()
        delay(200)
        assertEquals(2, count)
    }

    @Test
    fun `未 init 时 request 静默丢弃不崩`() {
        repeat(3) { ReminderRescheduler.request() }
    }

    @Test
    fun `重复 init 覆盖动作 只有一个执行者`() = runBlocking {
        var first = 0
        var second = 0
        ReminderRescheduler.init(debounceMs = 40) { first++ }
        ReminderRescheduler.init(debounceMs = 40) { second++ } // 覆盖
        ReminderRescheduler.request()
        delay(200)
        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun `action 抛异常不杀收集协程 后续请求仍生效`() = runBlocking {
        var count = 0
        ReminderRescheduler.init(debounceMs = 40) {
            count++
            if (count == 1) throw IllegalStateException("boom")
        }
        ReminderRescheduler.request()
        delay(200)
        ReminderRescheduler.request()
        delay(200)
        assertEquals(2, count)
    }

    @Test
    fun `init 替换时取消 in-flight pending — 旧 action 不再执行`() = runBlocking {
        var first = 0
        var second = 0
        // 600ms 防抖窗口 — 给 init 替换留出足够时间窗口(全量并发下
        // 20ms/80ms 旧参数停顿一次即破, 与首测同源 flake)
        ReminderRescheduler.init(debounceMs = 600) { first++ }
        ReminderRescheduler.request() // first 进入 600ms 防抖延迟
        delay(100) // 在延迟中段
        // 替换 action + 取消 in-flight pending — 旧 first 不再触发
        ReminderRescheduler.init(debounceMs = 40) { second++ }
        delay(1500) // 等旧 first delay 跑完
        assertEquals("first 不应执行 (被 init 取消)", 0, first)
        // init 替换不发起 request, 验证 init 没破坏后续 request 链路:
        ReminderRescheduler.request()
        delay(200)
        assertEquals("second 在 init 后 request 应执行", 1, second)
    }
}

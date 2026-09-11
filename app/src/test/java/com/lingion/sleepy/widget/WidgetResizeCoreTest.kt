package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 纯 JVM 测试: [WidgetResizeCore] — 今日系小组件 resize/导航推送世代号核心。
 *
 * 背景 (resize 稳定性): resize 拖拽期间系统连发 OPTIONS_CHANGED, 每次触发一次
 * "后台渲染 → awm.updateAppWidget"。旧尺寸的任务若后完成, 会把新尺寸的推送覆盖回旧内容
 * 且不再有后续更新来纠正 (永久 stale)。世代号 = 每个 widgetId 独立单调计数,
 * commit 前校验: 变了 = 渲染期间落了更新的触发 → 本结果作废。
 */
class WidgetResizeCoreTest {

    // ---- bump / current 基本语义 ----

    @Test
    fun `current returns zero for unknown widget id`() {
        assertEquals(0L, WidgetResizeCore.current(990_001))
    }

    @Test
    fun `bump increments monotonically per widget`() {
        val id = 990_002
        val g1 = WidgetResizeCore.bump(id)
        val g2 = WidgetResizeCore.bump(id)
        assertTrue("bump 必须单调递增", g2 > g1)
        assertEquals(g2, WidgetResizeCore.current(id))
    }

    @Test
    fun `different widget ids have independent generations`() {
        val a = 990_003
        val b = 990_004
        val ga = WidgetResizeCore.bump(a)
        WidgetResizeCore.bump(b)
        WidgetResizeCore.bump(b)
        assertEquals("别的 id 连续 bump 不得影响本 id 世代", ga, WidgetResizeCore.current(a))
    }

    // ---- isStale 判定 ----

    @Test
    fun `isStale false right after bump with same snapshot`() {
        val id = 990_005
        val gen = WidgetResizeCore.bump(id)
        assertFalse("刚 bump 的世代未被超越 → 不 stale", WidgetResizeCore.isStale(id, gen))
    }

    @Test
    fun `isStale true after a newer bump supersedes the snapshot`() {
        val id = 990_006
        val gen = WidgetResizeCore.bump(id)
        WidgetResizeCore.bump(id) // 模拟渲染期间落了新的 resize 触发
        assertTrue("被更新的触发超越 → 旧渲染结果必须作废", WidgetResizeCore.isStale(id, gen))
    }

    @Test
    fun `isStale for unknown id and gen zero is not stale`() {
        // gen=0 是"无世代把关"哨兵之外的合法历史值; 未知 id current=0 → gen=0 不 stale
        assertFalse(WidgetResizeCore.isStale(990_007, 0L))
    }

    // ---- remove: 实例删除后 id 复用不误判 ----

    @Test
    fun `remove clears generation so reused widget id starts fresh`() {
        val id = 990_008
        WidgetResizeCore.bump(id)
        WidgetResizeCore.bump(id)
        WidgetResizeCore.remove(id)
        assertEquals("remove 后世代归零 (widget id 会被系统复用)", 0L, WidgetResizeCore.current(id))
    }

    // ---- 并发冒烟: 多线程 bump 不丢计数 ----

    @Test
    fun `concurrent bumps never lose increments`() {
        val id = 990_009
        val threads = 8
        val perThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.execute {
                ready.countDown()
                ready.await()
                repeat(perThread) { WidgetResizeCore.bump(id) }
                done.countDown()
            }
        }
        done.await()
        pool.shutdown()
        assertEquals(threads * perThread.toLong(), WidgetResizeCore.current(id))
        WidgetResizeCore.remove(id)
    }
}

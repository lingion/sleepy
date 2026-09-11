package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PinWidgetActivity 的 `widget_type` 字符串 → receiver Class 路由必须覆盖
 * 全部 10 个变体（跨厂商 pin 路径：所有大陆主流启动器同一份 widget 集合都可被 pin）。
 *
 * 解析函数 [PinWidgetRouting.resolveClass] 返回 Kotlin Class 而非 ComponentName
 * — 本仓库 JVM 单测对 android.jar 走 returnDefaultValues（无 Robolectric），
 * ComponentName 的 getter 会返回 null，Kotlin 类型完全可测。
 *
 * 契约与 [WidgetUpdaterWiringTest]（refresh receiver 集合）同型：
 * 新增变体必须同时接入 ALL_WIDGET_VARIANTS → refresh 广播与 Pin 路由自动获得，
 * 忘接的回归直接红。
 */
class PinWidgetRoutingTest {

    private fun shortNames(): Set<String> =
        ALL_WIDGET_VARIANTS.map { it.receiverClass.simpleName.lowercase() }.toSet()

    /** simpleName 全小写 key 与历史短 key 两类入口全部可解析且指向正确类。 */
    @Test
    fun `every widget variant is routable by simple name and short key`() {
        assertEquals(10, shortNames().size) // ALL_WIDGET_VARIANTS 数量闸
        for (variant in ALL_WIDGET_VARIANTS) {
            val cls = variant.receiverClass
            // 入口 1: simpleName 全小写
            assertEquals(cls, PinWidgetRouting.resolveClass(cls.simpleName.lowercase()))
            // 入口 2: 历史短 key（去 Receiver/Provider 后缀 + Small + Widget 中缀，历史脚本用）
            val shortKey = cls.simpleName
                .removeSuffix("Receiver").removeSuffix("Provider")
                .removeSuffix("Small").replace("Widget", "").lowercase()
            assertEquals("short key=$shortKey", cls, PinWidgetRouting.resolveClass(shortKey))
        }
    }

    @Test
    fun `routed classes match ALL_WIDGET_VARIANTS 1 to 1`() {
        val routed = shortNames()
            .map { PinWidgetRouting.resolveClass(it) }
            .toSet()
        val expected = ALL_WIDGET_VARIANTS.map { it.receiverClass }.toSet()
        assertEquals(expected, routed)
    }

    /** 未知 / 空 / null type 一律回落 WeekGrid，永不抛、永不 null — 与旧行为一致。 */
    @Test
    fun `unknown empty or null type falls back to weekgrid`() {
        assertEquals(WeekGridWidgetProvider::class.java,
            PinWidgetRouting.resolveClass("garbage-unknown-type"))
        assertEquals(WeekGridWidgetProvider::class.java, PinWidgetRouting.resolveClass(""))
        assertEquals(WeekGridWidgetProvider::class.java, PinWidgetRouting.resolveClass(null))
        // trim + 大小写不敏感
        assertEquals(TodayWidgetReceiver::class.java, PinWidgetRouting.resolveClass("  TODAY "))
    }

    /** 历史 4 个 adb 手写 key（weekgrid/today/twoday/weeklist）必须继续可解析。 */
    @Test
    fun `legacy handwritten keys keep resolving`() {
        assertEquals(WeekGridWidgetProvider::class.java, PinWidgetRouting.resolveClass("weekgrid"))
        assertEquals(TodayWidgetReceiver::class.java, PinWidgetRouting.resolveClass("today"))
        assertEquals(TwoDayWidgetReceiver::class.java, PinWidgetRouting.resolveClass("twoday"))
        assertEquals(WeekListWidgetReceiver::class.java, PinWidgetRouting.resolveClass("weeklist"))
    }

    /** 路由表非空且覆盖 ≥10 个 key（防御 ALL_WIDGET_VARIANTS 被清空的回归）。 */
    @Test
    fun `routing table holds both key families`() {
        assertTrue("routing table unexpectedly small: ${PinWidgetRouting.routableTypes().size}",
            PinWidgetRouting.routableTypes().size >= 10)
    }
}
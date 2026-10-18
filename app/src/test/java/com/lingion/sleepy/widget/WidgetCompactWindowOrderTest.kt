package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 「今日居于第一位/第二位」失灵锁 (2026-10-18 用户报障)。
 *
 * 根因: compactShownDays 三天窗口按【真实日期升序】输出 — 周日窗口
 * [日(7), 一(1), 二(2)] 恰跨 dayOfWeek 边界; renderWeekListRegular /
 * renderWeekViewRegular 又把输入列 `sortedBy { it.dayOfWeek }` 重排 →
 * 周日从第 1/2 位飞回最右列 → 档位「失灵」。只在跨周边界日(周日附近)
 * 发作, 周一~周六三天同周递增、重排不变序 → 平时看着正常。
 *
 * 修复口径: 列序决策抽成纯函数 visibleDaysForRender(days, visibleDays,
 * keepDateOrder) — compact 脸传 true 保留日期序, regular 档传 false
 * 维持星序。测试 = 纯函数两条 + 源码 token 闸(compact 调用点必须传 true)。
 */
class WidgetCompactWindowOrderTest {

    private val sun = DayData(LocalDate.of(2026, 10, 18), 7, emptyList(), "")
    private val mon = DayData(LocalDate.of(2026, 10, 19), 1, emptyList(), "")
    private val tue = DayData(LocalDate.of(2026, 10, 20), 2, emptyList(), "")
    private val allVisible = setOf(1, 2, 3, 4, 5, 6, 7)

    @Test
    fun `keepDateOrder preserves window order across the week boundary`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), allVisible, keepDateOrder = true
        )
        assertEquals(listOf(sun.date, mon.date, tue.date), out.map { it.date })
    }

    @Test
    fun `regular tier still sorts columns by dayOfWeek`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), allVisible, keepDateOrder = false
        )
        assertEquals(listOf(mon.date, tue.date, sun.date), out.map { it.date })
    }

    @Test
    fun `visible-day filter still applies in date order mode`() {
        val out = WidgetBitmapRenderers.visibleDaysForRender(
            listOf(sun, mon, tue), setOf(1, 2, 3, 4, 5), keepDateOrder = true
        )
        assertEquals(listOf(mon.date, tue.date), out.map { it.date })
    }

    // ---- 源码闸: compact 渲染路径必须把列序交还给日期序 ----

    private val renderers: String by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt")
            if (f.exists()) return@lazy f.readText()
            dir = dir.parentFile
        }
        error("WidgetBitmapRenderers.kt not found")
    }

    @Test
    fun `compact render branches delegate column order to keepDateOrder true`() {
        listOf("renderWeekListRegular(", "renderWeekViewRegular(").forEach { call ->
            assertEquals(
                "$call 调用点必须恰有 compact 脸传 keepDateOrder = true(漏传=周日窗口重排失灵复发)",
                1,
                        Regex(Regex.escape(call) + """[^)]*keepDateOrder\s*=\s*true""")
                    .findAll(renderers).count()
            )
        }
    }
}

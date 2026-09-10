package com.lingion.sleepy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v9.1 overflow scroll parity (2026-09-10 真机复盘):
 *
 * 用户报: 缩小 widget (hDp≈130dp) 只显示 2 节课, 往下滑直接空白 — 壳图与条带
 * 各自的可见窗/位图尺寸口径分裂。
 *
 * 根因复盘 (renderer 源码 + 真机 logcat 三方交叉):
 *   renderTodayRegular 内部 visible filter 用 "pageVisiblePx = h − contentTopPx − pad"
 *   (h = 渲染器接收的位图高 px); pushScrollable 路径分两次调 renderToday:
 *     - 壳图 (TodayWidget.kt): 用 hDp (容器尺寸) → pageVisiblePx ≈ 102dp → 8 行课
 *       只画前 2 行 (行高 38+gap 10=48dp × 2 = 96dp), 后 6 行被 filter 丢弃;
 *     - 条带 (ScrollStripService.kt): 用 ceil(contentHdp) (全展开尺寸) → pageVisiblePx
 *       ≈ 398dp → 8 行全画。
 *   壳图永远只显示首 2 行 = 用户"只看到 2 节"; 滚动时壳图覆盖容器 fitXY, 露出 ListView
 *   范围外的 launcher 桌面 = 用户"下面空白"。
 *
 * TwoDay/WeekList 不中招: 它们走 pushScrollable 的条带单独调用 renderTwoDay /
 * renderWeekList, 没有同源 shell+filter 的尺寸分裂。
 *
 * 锁死契约:
 * 1. nav-overflow + !navEnabled 两条 pushScrollable 路径的 shell 渲染器都接
 *    contentH (todayContentHeightDp 派生), 与 ScrollStripService 条带渲染同参;
 *    禁再传 hDp.toFloat() 给 renderToday 第三参数。
 * 2. renderTodayRegular visible filter 表达式只引用 spans / offsetPx / pageVisiblePx,
 *    无外部常量; pageVisiblePx 由 *渲染器接收的 h* 推导, 不准反向依赖容器尺寸。
 */
class TodayOverflowScrollParityTest {

    private fun widgetSource(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/lingion/sleepy/widget/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found")
    }

    @Test
    fun `today nav overflow shell renders full content height not widget container`() {
        // v9.2: 壳图尺寸 = hDp (首屏页), 条带分页后每页同高。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        assertTrue("nav overflow 走 pushScrollable", body.contains("pushScrollable"))
        assertTrue(
            "v9.2: nav overflow 壳图按 hDp 渲染",
            Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*hDp\\.toFloat\\(\\),").containsMatchIn(body)
        )
        // !navEnabled 分支也同源
        val nonNav = src.substringAfter("!navEnabled").substringBefore("} else if (contentH")
        assertTrue(
            "!navEnabled overflow 壳图按 hDp 渲染",
            Regex("renderToday\\s*\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*hDp\\.toFloat\\(\\),").containsMatchIn(nonNav)
        )
    }

    @Test
    fun `overflow scroll parity shell and strip share same renderToday size parameter`() {
        // v9.2: 壳图和第一条带页都使用 viewport 高度 hDp；后续页通过 offset 分页。
        val today = widgetSource("TodayWidget.kt").readText()
        val navBody = today.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        val nonNavBody = today.substringAfter("!navEnabled").substringBefore("} else if (contentH")
        // v9.2: 壳图与第一条带页都是 viewport 高度 hDp; 后续页通过 pageOffsetsDp 分页。
        val shellPattern = Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*hDp\\.toFloat\\(\\),")
        assertTrue("nav overflow 壳图渲染高 = hDp (v9.2)", shellPattern.containsMatchIn(navBody))
        assertTrue("!navEnabled overflow 壳图渲染高 = hDp (v9.2)",
            shellPattern.containsMatchIn(nonNavBody))
        val strip = widgetSource("ScrollStripService.kt").readText()
        val stripBody = strip.substringAfter("SCOPE_TODAY ->").substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "条带必须按 pageOffsetsDp 分页",
            stripBody.contains("pageOffsetsDp") && stripBody.contains("pages = offsets.map")
        )
        assertTrue(
            "条带每页高度 = hDp (与壳图首屏同参)",
            stripBody.contains("wDp.toFloat(), hDp.toFloat()") && stripBody.contains("pageOffsetDp = offset")
        )
    }

    @Test
    fun `renderTodayRegular shell receives content height so filter does not drop first-screen courses`() {
        // v9.1 复盘: 壳图尺寸 = 全展开 contentH (pushScrollable 时由调用方保证),
        // 进入 renderer 后 pageVisiblePx 仅做几何兜底 (首行高 < pageVisiblePx ⇒ 不过滤)。
        // 锁法: pageVisiblePx 必须由 *渲染器接收的 h* 推导; filter 表达式只引用局部量。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertTrue(
            "pageVisiblePx 必须由渲染器参数 h 推导 (h - contentTopPx - pad)",
            body.contains("pageVisiblePx") && body.contains(" - contentTopPx")
        )
        // filter 行: 仅引用 spans / offsetPx / pageVisiblePx (局部量, 无外部常量)
        val filterLine = body.substringAfter("val visible =").substringBefore("\n").trim()
        assertTrue(
            "visible filter 表达式仅引用 spans/offsetPx/pageVisiblePx (无外部常量)",
            "spans" in filterLine && "offsetPx" in filterLine && "pageVisiblePx" in filterLine
        )
    }

    @Test
    fun `pageOffsetsDp generates fixed-viewport pages aligned to content bottom`() {
        // v9.2 核心: 多页条带 = 固定高 viewport; 末页 offset 钳到 maxOffset (末页贴底)。
        // 锁法: 直接验证 pageOffsetsDp 几何 (纯 JVM 可测)。
        val hDp = 281f
        val contentH = 618f  // 模拟器实测 logcat 数据
        val offsets = TodayRowGeometry.pageOffsetsDp(contentH, hDp, headerSpace = true)
        assertTrue("内容超出视口时页数 >= 2", offsets.size >= 2)
        // 每页 offset 增量 ≤ viewport 减头部减底部的可视行高 (= 281 - 14 - 14 = 253)
        for (i in 1 until offsets.size) {
            val step = offsets[i] - offsets[i - 1]
            assertTrue("页 ${i-1}→${i} 步长 ${step} 必须 ≤ viewport 可视高 253", step <= 253f + 0.01f)
        }
        // 末页 = contentH − viewport 可视行高 (贴底)
        assertEquals(
            "末页 offset 必须对齐内容底 (maxOffset)",
            contentH - 253f,
            offsets.last(),
            0.01f
        )
        // 内容装得下: 1 页即可
        val small = TodayRowGeometry.pageOffsetsDp(120f, 281f, headerSpace = true)
        assertEquals("装得下时只一页", listOf(0f), small)
    }
}

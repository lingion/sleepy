package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v8 overflow 几何契约 (2026-09-10, 真机 "巨型圆角卡片" 报障后的修复锁定):
 *
 * 三个被本测试锁死的缺陷 (研究 workflow 三方交叉证实):
 * 1. 镜像失配 — renderTodayRegular 的行几何硬编码 (14+24)dp 起点与 h−52dp 可见窗,
 *    而 todayContentHeightDp(headerSpace=true) 只计 14dp 起点 → 条带长图顶部 24dp
 *    死带 + 最后一行课程被可见过滤丢弃 (Today 是唯一 headerSpace=true 调用方,
 *    TwoDay/WeekList 从未中招)。修法: 行几何抽成单一真值纯函数, 渲染/内容高度
 *    两边同调一份。
 * 2. 巨型卡片根因 — v7 条带 ListView 是第一个不在根 FrameLayout 直下
 *    match_parent 的条带宿主 (卡在 weight=1 FrameLayout 里), viewport 高度
 *    落回 launcher 量测自由 (v2 翻车同源), ColorOS 量错 → 单 child 整图行
 *    (管线里唯一 >38dp 的圆角矩形) 就是那张巨卡。修法: 行 = 每课程行一张卡
 *    (多 child, 各行 setViewLayoutHeight 钉死 38dp/分栏堆叠高), viewport 量错
 *    只影响可视区不再放大整卡 — 退回全网 collection widget 标准形态。
 * 3. overscroll stretch — 拉到边缘 ListView 纵向拉伸整行 (Android 12+ stretch),
 *    条带整图行被拉成跟手巨卡。修法: 全部条带布局 overScrollMode="never"。
 */
class TodayOverflowGeometryTest {

    private fun widgetSource(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/lingion/sleepy/widget/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found")
    }

    private fun layoutFile(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res/layout/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found")
    }

    // ---- 修复 1: 行几何单一真值 (渲染/内容高度必须同调) ----

    @Test
    fun `today content height derives from shared row geometry`() {
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        // 渲染与内容高度必须调用同一行几何真值函数 (禁再手写 maxStack*rowH 镜像)
        val body = src.substringAfter("fun todayContentHeightDp(")
            .substringBefore("fun renderNavTriangle")
        assertTrue(
            "todayContentHeightDp 必须经 TodayRowGeometry 计算行几何 (镜像失配根除)",
            body.contains("TodayRowGeometry")
        )
        // 渲染端也必须用同一真值 (v4 硬编码 14+24 起点 = 丢失末行根因)
        val render = src.substringAfter("fun renderTodayRegular(")
            .substringBefore("private fun renderTodayCompact")
        assertTrue(
            "renderTodayRegular 必须经 TodayRowGeometry 取行 span (禁硬编码 contentTopPx)",
            render.contains("TodayRowGeometry")
        )
        // contentHeight 的返回必须来自几何函数 (不是再手写一遍累加)
        assertTrue(
            "todayContentHeightDp 返回值必须来自 TodayRowGeometry.contentHeightDp",
            body.contains("contentHeightDp")
        )
    }

    @Test
    fun `today row geometry is pure and mirror free`() {
        val src = widgetSource("TodayRowGeometry.kt").readText()
        // 纯几何: 零 Android import (纯 JVM 可测)
        assertFalse(
            "TodayRowGeometry 必须纯 Kotlin (禁 Android import)",
            Regex("import android\\.").containsMatchIn(src)
        )
        // headerSpace 参数化: 起点 14 vs 38 语义锁死
        assertTrue(
            "行几何必须按 headerSpace 参数化起点 (true→14dp, false→38dp)",
            src.contains("headerSpace")
        )
        assertTrue(
            "行高常量 38dp 锁死 (渲染/内容两侧同一口径)",
            src.contains("38f")
        )
    }

    @Test
    fun `row span geometry drops no course rows`() {
        // 纯 JVM 算术: 与真机丢行场景逐数字对应 —
        // 8 门无冲突课 headerSpace=true: 全部 8 行的 bottom 必须落在 [0, contentH) 内
        val courses = (1..8).map { i ->
            com.lingion.sleepy.data.entity.CourseEntity(
                id = i.toLong(), groupId = "g$i", tableId = 1L,
                courseName = "课$i", day = i % 7 + 1, startNode = i * 2, step = 1,
                startWeek = 1, endWeek = 20, color = "blue"
            )
        }
        val geo = TodayRowGeometry.rowSpans(courses, headerSpace = true)
        val contentH = TodayRowGeometry.contentHeightDp(courses, headerSpace = true)
        assertEquals("8 门课 = 8 行 (无冲突不并栏)", 8, geo.size)
        // 每一行必须完整落在内容高度内 (老代码 lastTop=374 > pageVisible=360 → 末行被丢)
        for (span in geo) {
            assertTrue("行 ${span.rowIndex} 顶不得为负", span.topDp >= 0f)
            assertTrue(
                "行 ${span.rowIndex} 底 ${span.bottomDp} 超出内容高 $contentH (会被可见过滤丢弃)",
                span.bottomDp <= contentH + 0.01f
            )
        }
        assertEquals("内容高 = 末行底 + 底 pad", geo.last().bottomDp + 14f, contentH, 0.01f)
    }

    @Test
    fun `conflict lane row spans taller than single row`() {
        // 链式区域 {1-2, 2-4, 4-5}: 栏 0 堆叠 1-2 与 4-5 (零重叠), 栏 1 = 2-4 →
        // maxStack=2 → 行高 2*38+3=79dp (真堆叠场景; 完全重叠两课是并排两栏各1门, 行高仍38)
        val courses = listOf(
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 1L, groupId = "a", tableId = 1L, courseName = "甲",
                day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 20, color = "blue"
            ),
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 2L, groupId = "b", tableId = 1L, courseName = "乙",
                day = 1, startNode = 2, step = 3, startWeek = 1, endWeek = 20, color = "green"
            ),
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 3L, groupId = "c", tableId = 1L, courseName = "丙",
                day = 1, startNode = 4, step = 2, startWeek = 1, endWeek = 20, color = "red"
            )
        )
        val geo = TodayRowGeometry.rowSpans(courses, headerSpace = false)
        assertEquals("链式区域并一渲染行", 1, geo.size)
        assertEquals("冲突行高 = 2*38+3 (maxStack 镜像语义)", 79f, geo[0].bottomDp - geo[0].topDp, 0.01f)
        val noHeaderH = TodayRowGeometry.contentHeightDp(courses, headerSpace = false)
        assertEquals("带头内容高 = 38 起点 + 行 79 + 底 pad 14 (末行后无 gap)", 38f + 79f + 14f, noHeaderH, 0.01f)
        val headerH = TodayRowGeometry.contentHeightDp(courses, headerSpace = true)
        assertEquals("去头内容高 = 14 起点 + 行 79 + 底 pad 14 (末行后无 gap)", 14f + 79f + 14f, headerH, 0.01f)
    }

    // ---- 修复 2: 条带行 = 每课程行一张卡 (多 child, 巨型整图类别性消失) ----

    @Test
    fun `strip factory keeps whole-image row with pinned height`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        val body = svc.substringAfter("override fun onDataSetChanged")
        // v3 契约保留: 单 child 整图是 OPPO 真机唯一被证明可滚的架构 (v1 多行切片
        // extent 冻结翻车在先) — 修巨卡不靠推翻 v3, 靠收回 viewport 量测自由
        assertTrue(
            "v3 单 child 整图契约必须保留 (strips = listOf(full))",
            Regex("strips\\s*=\\s*listOf\\(full\\)").containsMatchIn(body)
        )
        // 行高照 v3 契约仍须显式钉死 (launcher 量测自由零容忍)
        assertTrue(
            "行高仍须 setViewLayoutHeight 显式钉死 (v3 契约保留)",
            svc.contains("setViewLayoutHeight")
        )
    }

    @Test
    fun `scroll strip service consumes shared row geometry`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        val body = svc.substringAfter("SCOPE_TODAY ->")
            .substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "SCOPE_TODAY 内容高必须经 TodayRowGeometry (与渲染同一真值, 禁 todayContentHeightDp 旁路)",
            body.contains("TodayRowGeometry")
        )
    }

    @Test
    fun `overflow scroll layer viewport height is server pinned not launcher measured`() {
        // 巨型卡片根因修复: v7 的条带 ListView 卡在 weight=1 FrameLayout 里,
        // viewport 高度落回 ColorOS 量测自由 (v2 翻车同源)。v8 = 服务端 setViewLayoutHeight
        // 显式钉死滚动层高 (hDp − bar 36dp), launcher 失去量测权。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        assertTrue(
            "v8 overflow 必须显式钉滚动层 viewport 高 (setViewLayoutHeight on 滚动层容器)",
            body.contains("setViewLayoutHeight")
        )
        val xml = layoutFile("widget_today_overflow.xml").readText()
        assertTrue(
            "滚动层容器必须有可寻址 id (服务端钉高目标)",
            xml.contains("widget_overflow_scroll")
        )
    }

    // ---- 修复 3: overscroll stretch 禁用 (Android 12+ 纵向拉伸 = 跟手巨卡) ----

    @Test
    fun `all strip list views disable overscroll stretch`() {
        for (lay in listOf(
            "widget_scroll_today.xml",
            "widget_scroll_twoday.xml",
            "widget_scroll_weeklist.xml",
            "widget_today_overflow.xml"
        )) {
            val xml = layoutFile(lay).readText()
            assertTrue(
                "$lay 条带 ListView 必须 overScrollMode=never (Android 12+ stretch 把整图行拉成跟手巨卡)",
                Regex("overScrollMode=\"never\"").containsMatchIn(xml)
            )
        }
    }
}

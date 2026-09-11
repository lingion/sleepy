package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v9 overflow 几何契约 (2026-09-10, 用户放弃 overflow 左右切换后的定稿锁定):
 *
 * v9 = overflow 回归 TwoDay 形态 (用户定稿: 「样子就是跟最近两天一样, 就是这个
 * 头部和下面一起滚动」): 壳图+条带 ListView 双层, bitmap 头部画进长图随内容滚,
 * 无 bar 行无导航键。本测试锁死:
 * 1. 镜像失配根除 (v8 修复保留) — 渲染/内容高度/条带全部经 TodayRowGeometry
 *    单一真值, 禁再手写 maxStack*rowH 镜像 (v8 之前 24dp 死带 + 丢末行根因)。
 * 2. overflow 结构 = TwoDay 逐字节同构 — widget_scroll_today (根 FrameLayout +
 *    match_parent ListView, 无 weight 分层), 壳图带头 (emptyHeader 缺省 false),
 *    条带带头 (stripHeaderless 缺省 false)。v7/v8 的 bar 行 + 去头条带 + viewport
 *    钉高全部退场 — 真机巨卡翻车形态整体回撤, 回到同台 OPPO 一直正常的结构。
 * 3. overscroll stretch 禁用 (v8 修复保留) — 条带布局 overScrollMode="never"。
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

    // ---- 修复 2 (v9.1 形态回归): 条带 = 单 child 整张不透明长图 ----

    @Test
    fun `strip factory renders single full-content bitmap not row items`() {
        // v11 契约: Today 条带 = 一张全展开不透明长图 (与 TwoDay/WeekList 同构),
        // 整图一次画完。v10 逐行子项在 OPPO 上复发 v1 extent 冻结 + 透明位图叠加
        // 错位 + 第一行覆盖壳图标题 → 三症状同根; 回归单 child 整长图。
        val svc = widgetSource("ScrollStripService.kt").readText()
        val body = svc.substringAfter("fun onDataSetChangedInner")
        assertTrue(
            "Today 条带必须调 renderToday 出整张位图 (v9.1 形态回归)",
            body.contains("WidgetBitmapRenderers.renderToday(") && !body.contains("renderTodayRow")
        )
        assertTrue(
            "条带位图必须按 todayContentHeightDp 全展开高度画",
            body.contains("todayContentHeightDp")
        )
        assertTrue(
            "行高仍须 setViewLayoutHeight 显式钉死 (v3 契约保留, 整图位图也要给 launcher 行高)",
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

    // ---- binder 线程防御 (v9.3 保留) ----

    @Test
    fun `getViewAt and getCount snapshot strips and never throw on binder thread`() {
        // onDataSetChanged 与 getViewAt 是独立 binder 池线程 — strips 是可变 var,
        // wholesale 重赋值期间 getViewAt(position) 可拿旧 size 炸 ArrayIndexOutOfBounds
        // (杀进程级)。锁法: 先局部 snapshot, 再越界判空回退, 禁裸 strips[position]。
        val svc = widgetSource("ScrollStripService.kt").readText()
        val viewAt = svc.substringAfter("override fun getViewAt").substringBefore("override fun getLoadingView")
        assertTrue(
            "getViewAt 必须先取局部 snapshot (禁裸读可变 var strips)",
            viewAt.contains("val snapshot = strips") || viewAt.contains("val snapshot = this.strips")
        )
        assertTrue(
            "getViewAt 必须越界判空回退 (binder 线程禁 throw)",
            Regex("position >= snapshot\\.size").containsMatchIn(viewAt)
        )
        val count = svc.substringAfter("override fun getCount").substringBefore("override fun getViewAt")
        assertTrue(
            "getCount 必须同 pattern snapshot (与 getViewAt 同源, 禁裸 strips.size)",
            count.contains("val snapshot = strips") || count.contains("val snapshot = this.strips")
        )
    }

    @Test
    fun `onDataSetChanged wraps binder entry in try catch with safe fallback`() {
        // loadDataSync / renderToday 任何 throw (OOM / createBitmap 0px …) 在 binder
        // 线程裸抛 = 杀进程。锁法: try/catch 包体, 失败路径落空白单页安全回退。
        val svc = widgetSource("ScrollStripService.kt").readText()
        val body = svc.substringAfter("override fun onDataSetChanged")
        assertTrue("onDataSetChanged 必须 try/catch 包体 (binder 线程防御边界)", body.contains("try"))
        assertTrue(
            "失败路径必须有安全回退页 (strips = emptyList) 而非留旧值裸奔",
            Regex("catch \\([^)]*Throwable").containsMatchIn(body)
        )
    }

    @Test
    fun `generation gate is two-stage around single full-content render`() {
        // v11 回到单 child 整图渲染 (不再逐行), 世代闸从「行循环内」简化为
        // 渲染前后两道闸 (resize 拖拽期间任一阶段世代变更即丢弃)。锁法:
        // 服务端必须同时有"读尺寸前"和"commit 前"两道 isStale 闸, 行循环不再存在。
        val svc = widgetSource("ScrollStripService.kt").readText()
        val body = svc.substringAfter("fun onDataSetChangedInner")
            .substringBefore("override fun getCount")
        // 行循环应已退场
        assertFalse(
            "v11 不再有 for span in spans 逐行子项渲染 (回归整图单 child)",
            body.contains("for (span in spans)")
        )
        assertTrue(
            "v11 整图渲染保留两道 isStale 闸 (读尺寸前 + commit 前)",
            Regex("WidgetResizeCore\\.isStale[^)]*\\)").findAll(body).count() >= 2
        )
    }

    @Test
    fun `nav overflow branch is byte-identical to non-nav overflow pattern`() {
        // v11 定稿: Today overflow = TwoDay overflow 同构 (壳图按 contentH 全展开
        // 渲染 + 条带带头, 头部画进长图随内容滚)。v9.x 分页/v10 逐行子项都已退场。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        assertTrue(
            "v11 overflow 必须走 pushScrollable (竖排滑动)",
            body.contains("pushScrollable")
        )
        assertTrue(
            "v11 overflow 必须用 widget_scroll_today (与 !navEnabled overflow 同一布局)",
            body.contains("widget_scroll_today")
        )
        assertTrue(
            "v11 overflow 禁 bar 行布局 (widget_today_overflow 已删)",
            !body.contains("widget_today_overflow")
        )
        assertTrue(
            "v11 overflow 条带必须带头 (stripHeaderless=false = TwoDay 行为, 头部随内容滚)",
            !body.contains("stripHeaderless")
        )
        assertTrue(
            "v11 overflow 壳图按 contentH 全展开渲染 (v9.1 契约, 与条带同参)",
            Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*contentH,").containsMatchIn(body)
        )
        val xml = File(layoutDir(), "widget_today_overflow.xml")
        assertFalse(
            "widget_today_overflow.xml 布局必须已删 (bar 行形态整体退场)",
            xml.exists()
        )
    }

    private fun layoutDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res/layout")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("layout dir not found")
    }

    // ---- v11: 整图带头契约 (单 child 长图带壳图同参头部) + API26-30 行高退化说明 ----

    @Test
    fun `strip long bitmap keeps header param parity with shell`() {
        // v11: 条带 = 整张长图 (不再有续页概念)。条带与壳图同参: emptyHeader 透传
        // 给渲染器, headerSpace = emptyHeader 同值 (条带头部随内容滚, 滚动位 0 与壳图
        // 逐像素一致 — v9.1 契约)。
        val svc = widgetSource("ScrollStripService.kt").readText()
        val todayBody = svc.substringAfter("SCOPE_TODAY ->").substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "条带长图渲染必须透传 emptyHeader (与壳图同参)",
            todayBody.contains("emptyHeader = emptyHeader")
        )
        assertTrue(
            "条带长图渲染必须 headerSpace = emptyHeader (v6 条带坐标系契约)",
            todayBody.contains("headerSpace = emptyHeader")
        )
        // 渲染端锁法在 TodayOverflowScrollParityTest (header block guard 回归 emptyHeader 单条件)。
    }

    @Test
    fun `strip row layout documents pre-API31 degradation`() {
        // API26-30: setViewLayoutHeight 是 API31+ — 行高兜底固定 dp 无法跟随每页位图高。
        // 已评估的替代 (RemoteViews 无 per-row API, match_parent 在 OPPO 上重现量错高
        // 翻车) 均不可行 → 保留固定 dp 兜底 + 注释显式声明退化, 禁静默。
        val xml = layoutFile("widget_scroll_row.xml").readText()
        assertTrue(
            "API<31 行高退化必须在布局注释里显式声明 (禁静默退化)",
            xml.contains("API") && xml.contains("31")
        )
        assertTrue(
            "行高必须有固定 dp 兜底 (ScrollStripWholeImageTest 同契约保留)",
            Regex("layout_height=\"\\d+dp\"").containsMatchIn(xml)
        )
    }

    // ---- 修复 3: overscroll stretch 禁用 (Android 12+ 纵向拉伸 = 跟手巨卡) ----

    @Test
    fun `all strip list views disable overscroll stretch`() {
        for (lay in listOf(
            "widget_scroll_today.xml",
            "widget_scroll_twoday.xml",
            "widget_scroll_weeklist.xml"
        )) {
            val xml = layoutFile(lay).readText()
            assertTrue(
                "$lay 条带 ListView 必须 overScrollMode=never (Android 12+ stretch 把整图行拉成跟手巨卡)",
                Regex("overScrollMode=\"never\"").containsMatchIn(xml)
            )
        }
    }
}

package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v4 手动翻页架构核心 (2026-09-10 第四次实现, 前三轮 launcher 滚动全灭后的定案):
 *
 * 真机实证 (OPPO PKX110): 标准滚动三模式全灭 —
 *   v1 多 child 切片: extent 冻结 ceil(V/H)*H−V → 尾部不可达;
 *   v2 单 child wrap_content: 量错高 270/626px → 压扁+错乱;
 *   v3 单 child 钉高: 滑动即整页错乱 (巨型 prev 三角 + 全透明背景)。
 * 静态单位图路径三轮全程正常 (用户实证: 拖到不冲突尺寸「一切正常」)。
 * → ColorOS 启动器唯一稳定通道 = setImageViewBitmap 静态位图。
 *
 * v4: 放弃 launcher 滚动。内容装不下时切页 (pager), 顶栏 ‹›按钮翻页,
 * 页尾跨天。页几何 = TodayPagerCore (纯 JVM), 渲染 = renderToday 带
 * pageOffsetDp (从内容纵轴任意偏移起画一页)。
 */
class TodayPagerCoreTest {

    // ---- 页几何: 页高 / 页数 / 页起点 ----

    @Test
    fun `page height subtracts header and both paddings`() {
        // 视口 179dp: 顶栏 36 + 顶 pad 14 → 内容页高 179-36-14-14(底 pad) = 115dp
        assertEquals(115f, TodayPagerCore.pageHeightDp(179f), 0.01f)
        // 视口 148dp: 148-36-28 = 84dp
        assertEquals(84f, TodayPagerCore.pageHeightDp(148f), 0.01f)
    }

    @Test
    fun `page count is ceil of content over page height`() {
        // 内容 196dp, 页高 115dp → 2 页
        assertEquals(2, TodayPagerCore.pageCount(196f, 179f))
        // 内容 98dp, 页高 84dp → 2 页
        assertEquals(2, TodayPagerCore.pageCount(98f, 148f))
        // 内容恰等于页高 → 1 页
        assertEquals(1, TodayPagerCore.pageCount(115f, 179f))
        // 内容小于页高 → 1 页 (静态闸门兜底, 理论上不进 pager)
        assertEquals(1, TodayPagerCore.pageCount(50f, 179f))
    }

    @Test
    fun `page offset snaps to row boundary semantics`() {
        // 页 0 从 0 起; 页 1 从 pageH 起 (下一页接着上一页底, 无内容重叠/丢失)
        assertEquals(0f, TodayPagerCore.pageOffsetDp(page = 0, contentH = 196f, hDp = 179f), 0.01f)
        assertEquals(115f, TodayPagerCore.pageOffsetDp(page = 1, contentH = 196f, hDp = 179f), 0.01f)
        // 末页允许负余量: offset 不超过 content (clamp), 渲染器画不足一页的部分
        val lastOffset = TodayPagerCore.pageOffsetDp(page = 1, contentH = 130f, hDp = 179f)
        assertEquals(115f, lastOffset, 0.01f)
    }

    @Test
    fun `clamps page index into valid range`() {
        // 越界页码 clamp 到 [0, pages-1] — 广播乱序/竞态下不越界
        assertEquals(0, TodayPagerCore.clampPage(-1, totalPages = 2))
        assertEquals(1, TodayPagerCore.clampPage(1, totalPages = 2))
        assertEquals(1, TodayPagerCore.clampPage(5, totalPages = 2))
        assertEquals(0, TodayPagerCore.clampPage(0, totalPages = 1))
    }

    // ---- 翻页 vs 翻天语义: 页内先翻页, 页尾跨天 ----

    @Test
    fun `next action turns page first then crosses day`() {
        // 2 页 widget: 页 0 按 next → 留在同一天, 页码 +1
        val r1 = TodayPagerCore.resolveNext(page = 0, totalPages = 2)
        assertEquals(TodayPagerCore.Outcome.Page(page = 1, totalPages = 2), r1)
        // 已在末页按 next → 跨到下一天, 页码归 0
        val r2 = TodayPagerCore.resolveNext(page = 1, totalPages = 2)
        assertEquals(TodayPagerCore.Outcome.DayShift(deltaDays = 1), r2)
        // 单页 widget (静态尺寸): next 直接翻天 (既有日期导航行为零变化)
        assertEquals(TodayPagerCore.Outcome.DayShift(deltaDays = 1), TodayPagerCore.resolveNext(page = 0, totalPages = 1))
    }

    @Test
    fun `prev action turns page back then crosses day`() {
        // 页 1 按 prev → 回页 0
        assertEquals(TodayPagerCore.Outcome.Page(page = 0, totalPages = 2), TodayPagerCore.resolvePrev(page = 1, totalPages = 2))
        // 页 0 按 prev → 跨到前一天并落到其末页
        val r = TodayPagerCore.resolvePrev(page = 0, totalPages = 3)
        assertEquals(TodayPagerCore.Outcome.DayShiftToLastPage(deltaDays = -1), r)
        // 单页: prev 直接翻天 (既有行为零变化)
        assertEquals(TodayPagerCore.Outcome.DayShiftToLastPage(deltaDays = -1), TodayPagerCore.resolvePrev(page = 0, totalPages = 1))
    }

    // ---- 源码守卫: 布局与推送管线契约 ----

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

    @Test
    fun `nav overflow path uses static layout never listview`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow")
        assertTrue(
            "overflow 必须走 widget_today_nav_static (静态位图通道, OPPO 唯一稳定)",
            body.contains("widget_today_nav_static")
        )
        assertFalse(
            "overflow 禁走 ListView 滚动布局 (三轮真机翻车证据)",
            body.contains("widget_scroll_today_nav")
        )
    }

    @Test
    fun `renderer supports page offset drawing`() {
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertTrue(
            "renderToday 必须支持 pageOffsetDp 参数 (从内容纵轴偏移起画)",
            src.contains("pageOffsetDp")
        )
    }

    @Test
    fun `pager actions wired as broadcast constants`() {
        val src = widgetSource("TodayWidget.kt").readText()
        assertTrue("须有 ACTION_PREV_PAGE", src.contains("ACTION_PREV_PAGE"))
        assertTrue("须有 ACTION_NEXT_PAGE", src.contains("ACTION_NEXT_PAGE"))
        assertTrue("页码须持久化 TodayPagerStore", src.contains("TodayPagerStore"))
    }

    @Test
    fun `nav scroll layouts and service removed from today path`() {
        // widget_scroll_today_nav 布局文件删除 (今日导航专用滚动布局完成历史使命)
        val lay = File(".").resolve("app/src/main/res/layout/widget_scroll_today_nav.xml")
        assertTrue(
            "widget_scroll_today_nav.xml 必须删除 (v4 无滚动, 残留=误用风险)",
            !lay.exists()
        )
    }
}

private fun assertFalse(message: String, condition: Boolean) {
    assertTrue(message, !condition)
}

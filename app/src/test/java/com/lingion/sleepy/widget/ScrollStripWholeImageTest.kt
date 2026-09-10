package com.lingion.sleepy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 条带架构 v3 定稿 (2026-09-10 第三次实现, 真机两轮翻车后的最终架构):
 *
 * 真机实证 (OPPO PKX110, uiautomator 标签+坐标, 无截图):
 * 1. 多 child 48dp 切片: launcher ListView 滚动 extent 冻结于首屏 fill 数,
 *    extent = ceil(V/H)*H − V (789: 4×125.5−386=116 实测吻合) → 尾部永不可达;
 * 2. 单 child wrap_content+adjustViewBounds: launcher 量出错高 (位图等比应 626px,
 *    实测 270px) → fitXY 压扁 + 渲染错乱 (巨型三角糊满整页, 不可滚)。
 * v3: 单 child 整张长图 + **setViewLayoutHeight 显式钉行高** (API31+)。
 * 冻结公式在单 child (H ≥ V) 时退化为 extent = H − V = 完整可滚距离 — 数学上
 * 唯一确定性逃逸; 显式行高剥夺 launcher 的自由量测 (wrap_content 翻车根因)。
 * 壳图层从今日导航布局删除 (双图层重影根因); 非导航滚动布局零改动。
 *
 * 守卫为源码级 (仓库无 Robolectric, 沿用 TodayDateNavWiringTest 风格)。
 */
class ScrollStripWholeImageTest {

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

    // ---- 行布局: 固定 dp 兜底高, 禁 wrap_content / adjustViewBounds (真机量错高根因) ----

    @Test
    fun `row layout uses fixed dp fallback never wrap_content`() {
        val xml = layoutFile("widget_scroll_row.xml").readText()
        assertTrue(
            "行高必须有固定 dp 兜底 (API31 以下或 setViewLayoutHeight 未生效时的确定值)",
            Regex("layout_height=\"\\d+dp\"").containsMatchIn(xml)
        )
        assertFalse(
            "禁 wrap_content 行高 (OPPO launcher 量出错高 270/626px 的直接根因)",
            xml.contains("layout_height=\"wrap_content\"")
        )
        assertFalse(
            "禁 adjustViewBounds (RemoteViews 行内等比自适应 = 量错高根因, 高度改由 setViewLayoutHeight 显式钉死)",
            xml.contains("adjustViewBounds")
        )
    }

    // ---- 工厂: API31+ 整图 + setViewLayoutHeight 钉行高; count 恒等 strips.size ----

    @Test
    fun `factory pins whole-image row height explicitly on API31 plus`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertTrue(
            "必须调用 setViewLayoutHeight 显式钉行高 (剥夺 launcher 量测自由)",
            svc.contains("setViewLayoutHeight")
        )
        assertTrue(
            "setViewLayoutHeight 必须 API31+ 守卫 (minSdk 26)",
            Regex("SDK_INT >= Build\\.VERSION_CODES\\.(S|TIRAMISU)").containsMatchIn(svc)
        )
        assertTrue(
            "行高单位必须 DIP (launcher 进程按 launcher 密度换算, 与位图渲染密度同源)",
            svc.contains("TypedValue.COMPLEX_UNIT_DIP")
        )
        assertTrue(
            "getCount 必须恒等 strips.size (空 adapter 安全: stale/异常路径 strips 为空 → count=0 不触 getViewAt; 写死 1 = 越界崩溃)",
            Regex("getCount\\(\\)[^=]*=\\s*strips\\.size").containsMatchIn(svc)
        )
        assertTrue(
            "世代闸必须保留 (isStale 双道校验, resize 竞态防御不回退)",
            svc.contains("isStale")
        )
        assertTrue(
            "必须带 adb 取证标签 (whole ... id=)",
            svc.contains("\"whole ")
        )
    }

    // ---- 壳图层: 今日导航滚动布局 v4 已整体删除 (滚动方案废弃) ----

    @Test
    fun `nav scroll layout removed in v4 pager architecture`() {
        val lay = File(".").resolve("app/src/main/res/layout/widget_scroll_today_nav.xml")
        assertTrue(
            "widget_scroll_today_nav.xml 必须已删 (v4 手动翻页, 无 ListView 滚动)",
            !lay.exists()
        )
    }

    @Test
    fun `non-nav scroll layouts keep shell contract untouched`() {
        // WeekGrid 最小档等非导航滚动路径不在本次重写范围 — 布局零改动契约
        val xml = layoutFile("widget_scroll_today.xml").readText()
        assertTrue("非导航布局壳图零改动", xml.contains("widget_shell"))
    }

    // ---- pushScrollable: 壳位图可空 (导航路径不设壳) ----

    @Test
    fun `pushScrollable shell bitmap is nullable`() {
        val helper = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        assertTrue(
            "pushScrollable 的 shellBitmap 参数必须可空",
            Regex("shellBitmap:\\s*Bitmap\\?").containsMatchIn(helper)
        )
        assertTrue(
            "null 壳必须跳过 setImageViewBitmap (RemoteViews 对不存在 id 的 action 会炸整次 apply)",
            helper.contains("if (shellBitmap != null)")
        )
    }

    // ---- 调用方: v4 overflow 走静态整图 + 手动翻页, 无滚动服务 ----

    @Test
    fun `today nav overflow uses static pager not scroll service`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow")
        assertTrue(
            "v4 overflow 必须渲染进 widget_today_nav_static (无 ListView)",
            body.contains("widget_today_nav_static")
        )
        assertTrue(
            "v4 overflow 必须经 TodayPagerCore 切页",
            body.contains("TodayPagerCore")
        )
        assertTrue(
            "v4 overflow 禁再走 pushScrollable (ColorOS ListView 滚动全线翻车)",
            !body.contains("pushScrollable")
        )
        assertTrue(
            "v4 overflow 禁引用已删的 widget_scroll_today_nav",
            !body.contains("widget_scroll_today_nav")
        )
        // 取证标签: 所有元素 drawn or not 全打标 — spacer 也要有
        val nav = src.substringAfter("fun configureTodayNav(")
            .substringBefore("val zones = listOf(")
        assertTrue(
            "configureTodayNav 必须给 spacer 打标签",
            nav.contains("widget_spacer_l") && nav.contains("widget_spacer_r")
        )
    }

    @Test
    fun `nav layouts label every element including spacers and root`() {
        listOf("widget_today_nav_static.xml").forEach { name ->
            val xml = layoutFile(name).readText()
            assertTrue("$name spacer 左须有 id", xml.contains("widget_spacer_l"))
            assertTrue("$name spacer 右须有 id", xml.contains("widget_spacer_r"))
        }
    }
}

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

    // ---- 壳图层: 今日导航滚动布局 v4 已整体删除 (真实视图覆盖层 = ColorOS 腐坏源) ----

    @Test
    fun `nav scroll layout removed in v4 pager architecture`() {
        val lay = File(".").resolve("app/src/main/res/layout/widget_scroll_today_nav.xml")
        assertTrue(
            "widget_scroll_today_nav.xml 必须已删 (v5 复用 widget_scroll_today, 无覆盖层布局)",
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

    // ---- 调用方: v7 overflow = TwoDay 同构 (壳图+条带) + 上方 bar 行 ----
    // 2026-09-10 用户定稿: 「最近两日的那个小组件它怎么做你就怎么做, 然后在上面
    // 加一个切换的 bar」。v6 无壳 (shellBitmap=null) 翻车: 条带异步加载期间整卡透明。
    // v7 回归真机已证安全的 TwoDay 双层 (widget_scroll_twoday 同构) + bar 独立行。

    @Test
    fun `today nav overflow uses twoday-identical shell plus strip with bar row`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow")
        assertTrue(
            "v7 overflow 必须走 pushScrollable (竖排滑动)",
            body.contains("pushScrollable")
        )
        assertTrue(
            "v7 overflow 必须用 widget_today_overflow (bar 行 + TwoDay 同构滚动层)",
            body.contains("widget_today_overflow")
        )
        assertTrue(
            "v7 overflow 必须有壳图 (TwoDay 同构: 条带加载期间壳图兜底, 不透明闪空)",
            body.contains("renderToday(")
        )
        assertTrue(
            "v7 overflow 条带去头不留空档 (bar 是布局独立行)",
            body.contains("stripHeaderless = true")
        )
        assertTrue(
            "v7 overflow 禁翻页残留 (TodayPagerCore 已删)",
            !body.contains("TodayPagerCore")
        )
        assertTrue(
            "v7 overflow 顶栏走 configureTodayOverflow (三键, 非静态档四件套)",
            body.contains("configureTodayOverflow")
        )
        // 取证标签: 所有元素 drawn or not 全打标 — spacer 也要有 (静态分支仍在用)
        val nav = src.substringAfter("fun configureTodayNav(")
            .substringBefore("val zones = listOf(")
        assertTrue(
            "configureTodayNav 必须给 spacer 打标签",
            nav.contains("widget_spacer_l") && nav.contains("widget_spacer_r")
        )
    }

    @Test
    fun `overflow layout is bar row above twoday-identical scroll layer`() {
        // v7 结构铁律: bar 行在上 (36dp, 三键), 滚动层 1:1 抄 widget_scroll_twoday
        // (壳图 ImageView + 条带 ListView) — 用户定稿: 最近两天怎么做就怎么做。
        val xml = layoutFile("widget_today_overflow.xml").readText()
        assertTrue(
            "根容器须 LinearLayout vertical (bar 行 + 滚动层竖排)",
            Regex("LinearLayout[^>]*android:orientation=\"vertical\"").containsMatchIn(xml)
        )
        // 滚动层 = TwoDay 同构: 壳图 ImageView (widget_shell, fitXY) + 条带 ListView (weight=1)
        assertTrue(
            "滚动层必须有壳图 widget_shell (TwoDay 同构, 条带加载期间兜底)",
            xml.contains("widget_shell")
        )
        val shellBlock = xml.substring(xml.indexOf("widget_shell"), xml.indexOf("widget_strip_list"))
        assertTrue(
            "壳图 scaleType 必须与 widget_scroll_twoday 一致 (fitXY)",
            shellBlock.contains("fitXY")
        )
        // bar 行: 三键顺序 prev < title < next, 按钮恒 40x28dp
        val iPrev = xml.indexOf("widget_today_nav_prev")
        val iTitle = xml.indexOf("widget_today_nav_title")
        val iNext = xml.indexOf("widget_today_nav_next")
        assertTrue("三键顺序 prev<title<next", iPrev >= 0 && iPrev < iTitle && iTitle < iNext)
        assertTrue("按钮口径 40dp/28dp 保留", xml.contains("40dp") && xml.contains("28dp"))
        assertTrue("bar 高 36dp (NAV_HEADER_H_DP 口径)", xml.contains("36dp"))
        // 中键日期: weight=1 + ellipsize → 拉缩只截断标题, 三键结构上永不被挤出
        val titleBlock = xml.substring(xml.indexOf("widget_today_nav_title"), xml.indexOf("widget_today_nav_next"))
        assertTrue("标题须 weight=1", titleBlock.contains("layout_weight=\"1\""))
        assertTrue("标题须 ellipsize (窄卡截断而非挤出 ›)", titleBlock.contains("ellipsize"))
        // 滚动层 FrameLayout 占满剩余高 (weight=1, TwoDay 的 ListView 就是 match_parent)
        // + 禁裸 View (@RemoteView 白名单)
        assertFalse("禁裸 <View>", Regex("<View\\b").containsMatchIn(xml))
        val scrollBlock = xml.substring(xml.indexOf("widget_today_nav_next"))
        assertTrue("滚动层须 weight=1 吃满剩余高", scrollBlock.contains("layout_weight=\"1\""))
        assertTrue(
            "条带 ListView 须 match_parent (widget_scroll_twoday 逐行同构)",
            scrollBlock.contains("match_parent")
        )
    }

    @Test
    fun `overflow header configures three-button zones with labels`() {
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("fun configureTodayOverflow(")
            .substringBefore("/** 顶栏标题")
        // 三键 PendingIntent: prev/next 翻天, 日期键回到今天
        assertTrue("prev→ACTION_PREV_DAY", body.contains("ACTION_PREV_DAY"))
        assertTrue("next→ACTION_NEXT_DAY", body.contains("ACTION_NEXT_DAY"))
        assertTrue("日期键→ACTION_RESET_DAY (三键定稿: 无独立回到今天键)",
            body.contains("ACTION_RESET_DAY"))
        // 全元素取证标签
        assertTrue("bar 行打标", body.contains("overflow bar v7"))
        assertTrue("壳图打标", body.contains("overflow shell v7"))
        assertTrue("列表打标", body.contains("overflow list v7"))
        assertTrue("日期键打标 (date=…tap=back-to-today)", body.contains("tap=back-to-today"))
        assertTrue("prev 打标", body.contains("prev 40x28dp"))
        assertTrue("next 打标", body.contains("next 40x28dp"))
        // 底色: bar 行同 scheme setBackgroundColor (滚动层底色由壳图自带, 不再手动补)
        assertTrue("bar 行底色 setBackgroundColor", body.contains("setBackgroundColor"))
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

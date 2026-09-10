package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * issue #24 顶栏导航定稿 (真实视图顶栏 + 三角按钮 + 恒显日期) 守卫。
 * 涵盖 StackView 机制全清除、ScrollStripService emptyHeader 透传、
 * 渲染器低对比三角按钮、nav 布局 36dp 顶栏尺寸契约。
 */
class TodayDateNavHeaderWiringTest {

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

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    @Test
    fun `navTitle always shows the date in both states`() {
        val today = WidgetData(date = LocalDate.of(2026, 9, 9), courses = emptyList(),
            timeJson = "", hasTable = false, isToday = true)
        assertEquals("9/9 · 周三", TodayWidgetReceiver.navTitle(today, "周三"))
        val nav = today.copy(date = LocalDate.of(2026, 9, 12), isToday = false)
        assertEquals("9/12 · 周六", TodayWidgetReceiver.navTitle(nav, "周六"))
    }

    @Test
    fun `stack machinery fully removed`() {
        // StackView 方案作废: 竖滑只留给内容滚动 (ListView), 不切日期
        val dir = findUpward("app/src/main/java/com/lingion/sleepy/widget")
        assertTrue("TodayStack*.kt 必须已删",
            dir.listFiles()!!.none { it.name.startsWith("TodayStack") })
        val lay = findUpward("app/src/main/res/layout")
        assertTrue("stack 布局必须已删",
            lay.listFiles()!!.none { it.name.startsWith("widget_today_stack") })
        val mf = findUpward("app/src/main/AndroidManifest.xml").readText()
        assertFalse("manifest 不得残留 TodayStackService", mf.contains("TodayStackService"))
        val src = widgetSource("TodayWidget.kt").readText()
        assertFalse("TodayWidget 不得残留 stack 管线",
            src.contains("TodayStackCore") || src.contains("TodayStackService") ||
                src.contains("setRemoteAdapter"))
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertFalse("ScrollStripService 不得残留 TodayStack 引用", svc.contains("TodayStack"))
        val rdr = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertFalse("渲染器不得残留 TodayStack 引用", rdr.contains("TodayStack"))
    }

    @Test
    fun `ScrollStripService plumbs emptyHeader flag through`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertTrue("服务端须读 EXTRA_EMPTY_HEADER",
            svc.contains("EXTRA_EMPTY_HEADER") && svc.contains("getBooleanExtra"))
        assertTrue("SCOPE_TODAY 条带须透传 emptyHeader",
            svc.contains("emptyHeader = emptyHeader"))
        val helper = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        assertTrue("pushScrollable 须带 stripHeaderless 参数并 putExtra",
            helper.contains("stripHeaderless") && helper.contains("EXTRA_EMPTY_HEADER"))
        // WeekGrid 最小档 overflow 条带仍带头 - 缺省 false = 既有调用方零改动契约
        assertTrue("缺省必须 false",
            helper.contains("stripHeaderless: Boolean = false"))
    }

    @Test
    fun `nav fit measurement honors font scale`() {
        // sp 文本跟随系统字体缩放: Paint textSize 必须乘 fontScale,
        // 否则大字体设备 (fontScale>1 常见) 实测文本比测量宽 → fitsNavTodayFourChar
        // 漏判 → nav_next 又被挤成第二行巨钮。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("private fun fitsNavTodayFourChar(\n            context: Context")
            .ifEmpty { src.substringAfter("context: Context, titleText: String, wDp: Int") }
        assertTrue("nav fit Android 入口必须读 configuration.fontScale",
            body.contains("fontScale"))
        assertTrue("Paint textSize 须 sp*density*fontScale 构造",
            body.contains("fontScale"))
    }

    @Test
    fun `nav header tier degrades inside configureTodayNav`() {
        // 真机根因 (OPPO 148dp 窄档, 2026-09-09): 满标题+两字「今天」固定宽 ≈187dp
        // > 148dp → LinearLayout 横向溢出, nav_next 被推出可视区。旧判定只到两字档,
        // 装不下无路可退 → 必须用 navHeaderTier 逐档降级 (去星期 → 隐藏 nav_today)。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("fun configureTodayNav(")
            .substringBefore("/** 顶栏标题")
        assertTrue("configureTodayNav 必须走 navHeaderTier 降级判定",
            body.contains("navHeaderTier("))
        assertTrue("SHORT_TITLE 档必须标题去星期 (dateOnlyTitle)",
            body.contains("dateOnlyTitle"))
        assertTrue("HIDE_TODAY 档必须隐藏 nav_today (prev/next 保留)",
            body.contains("HIDE_TODAY"))
        assertTrue("隐藏判定必须保留 isToday 短路 (今日不显回到今天)",
            body.contains("data.isToday"))
    }

    @Test
    fun `renderer nav triangle is low-contrast and glyph-free`() {
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderNavTriangle")
            .substringBefore("data class TodayNavHeaderColors")
        assertTrue("低对比: surfaceVariant 圆角矩形底", body.contains("surfaceVariant"))
        assertTrue("低对比: onSurfaceVariant 三角图标", body.contains("onSurfaceVariant"))
        assertFalse("按钮禁圆形 (用户要三角形)", body.contains("drawCircle"))
        assertFalse("按钮禁文字 glyph (用户要三角形图标)", body.contains("drawText"))
        assertTrue("renderToday 须有 emptyHeader 参数",
            src.contains("emptyHeader: Boolean = false"))
    }

    @Test
    fun `nav header layouts declare 36dp strip with spacers and sizes`() {
        listOf("widget_today_nav_static.xml").forEach { name ->
            val xml = layoutFile(name).readText()
            assertTrue("$name 缺顶栏容器 widget_today_header", xml.contains("widget_today_header"))
            assertTrue("$name 顶栏高须 36dp (NAV_HEADER_H_DP 口径)", xml.contains("36dp"))
            assertTrue("$name 按钮须 40dp/28dp (NAV_BUTTON_W_DP/NAV_BUTTON_H_DP 口径)",
                xml.contains("40dp") && xml.contains("28dp"))
            assertEquals("$name 须两个等重 spacer (Space 无 @RemoteView 禁用)",
                2, Regex("layout_weight=\"1\"").findAll(xml).count())
            // 顶栏子视图顺序: title < prev < today < next (用户定稿)
            val iTitle = xml.indexOf("widget_today_nav_title")
            val iPrev = xml.indexOf("widget_today_nav_prev")
            val iToday = xml.indexOf("widget_today_nav_today")
            val iNext = xml.indexOf("widget_today_nav_next")
            assertTrue("$name 顶栏顺序须 title<prev<today<next",
                iTitle < iPrev && iPrev < iToday && iToday < iNext)
        }
    }

    @Test
    fun `configure activity declares empty taskAffinity and delayed auto-finish`() {
        // 2026-09-10 真机+模拟器实证: 拖放添加时 launcher 经 ProxyActivityStarter 启动 configure,
        // 缺省 affinity 下 ActivityRecord 被丢弃 → CanceledException → add 回滚 → "小组件无法添加"。
        // 守卫: manifest 须 taskAffinity="" 且 first-add auto-finish 须延迟 (≥300ms), 禁回退一帧 post。
        val mf = findUpward("app/src/main/AndroidManifest.xml").readText()
        val block = mf.substringAfter("WidgetConfigureActivity")
            .substringBefore("/>")
        assertTrue("configure 须 android:taskAffinity=\"\"", block.contains("android:taskAffinity=\"\""))
        val cfg = widgetSource("WidgetConfigureActivity.kt").readText()
        assertTrue("first-add finish 须 postDelayed ≥300ms (一帧 post 在 launcher result 回调前送达)",
            Regex("postDelayed\\(\\s*\\{[^}]*finishWithResult", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(cfg))
        assertFalse("禁回退到一帧 decorView.post 直 finish (竞态根因)",
            cfg.contains("decorView.post {"))
    }
}

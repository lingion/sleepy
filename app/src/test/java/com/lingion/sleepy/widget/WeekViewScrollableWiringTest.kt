package com.lingion.sleepy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * issue#31 荣耀 4×5 定案守卫 (源码级, 仓库无 Robolectric, 沿用
 * ScrollStripWholeImageTest / TodayDateNavWiringTest 风格)。
 *
 * 报障: 本周课表（周视图）4×5 显示不完全 (每列课程静默裁到 5 门,
 * renderWeekViewRegular courses.take(5), 超出画到 bitmap 外), 且无法滚动
 * (WeekView push 从未接 pushScrollable — WeekList v1.0.36 已有, WeekView 漏)。
 *
 * 契约:
 *  1. WeekViewWidget.push 必须与 WeekListWidget.push 同构 — contentH ≤ hDp 走
 *     静态 renderAndPush, 超出走 pushScrollable (widget_scroll_weeklist 布局 +
 *     SCOPE_WEEKVIEW);
 *  2. ScrollStripService 必须处理 SCOPE_WEEKVIEW (loadDataSync + 全展开渲染);
 *  3. 周视图全展开内容高度单一事实来源 weekViewContentHeightDp — 推送闸与
 *     条带工厂共用同一函数 (口径分裂 = 壳图/条带错位);
 *  4. 条带渲染禁止 take(5) 截断 — 全展开渲染路径 maxCourses 不设限。
 */
class WeekViewScrollableWiringTest {

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
    fun `weekview push gates on content height and falls back to scrollable`() {
        val src = widgetSource("WeekViewWidget.kt").readText()
        assertTrue(
            "WeekView.push 必须先算内容高度 (contentH ≤ hDp 闸, 与 WeekList 同构)",
            src.contains("weekViewContentHeightDp")
        )
        assertTrue(
            "WeekView.push 必须保留静态 renderAndPush 分支",
            src.contains("renderAndPush")
        )
        assertTrue(
            "WeekView.push 内容超出必须走 pushScrollable (v1.0.36 WeekList 同款, #31 4×5 显示不完全根因)",
            src.contains("pushScrollable")
        )
        assertTrue(
            "WeekView 可滚动分支必须用 SCOPE_WEEKVIEW 条带",
            src.contains("SCOPE_WEEKVIEW")
        )
        assertTrue(
            "WeekView.push 必须以内容高度为闸 (contentH ≤ hDp → 静态; 超出 → 可滚动)",
            Regex("contentH\\s*<=\\s*hDp").containsMatchIn(src.substringAfter("private fun push"))
        )
    }

    @Test
    fun `strip factory handles weekview scope with full-content render`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertTrue(
            "StripFactory 必须声明 SCOPE_WEEKVIEW",
            svc.contains("SCOPE_WEEKVIEW")
        )
        val weekviewCase = svc.substringAfter("SCOPE_WEEKVIEW ->", "")
        assertTrue(
            "SCOPE_WEEKVIEW 分支必须存在 (loadDataSync + 渲染)",
            weekviewCase.isNotEmpty() && weekviewCase.substringBefore("else ->").contains("WeekViewWidgetReceiver.loadDataSync")
        )
        assertTrue(
            "SCOPE_WEEKVIEW 渲染必须走全展开内容高度 (weekViewContentHeightDp)",
            weekviewCase.contains("weekViewContentHeightDp")
        )
    }

    @Test
    fun `weekview content height is the single source of truth`() {
        val renderers = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertTrue(
            "weekViewContentHeightDp 必须存在于渲染器 (推送闸与条带工厂共用)",
            renderers.contains("fun weekViewContentHeightDp")
        )
        // 全展开渲染: 周视图渲染器必须支持不设课程数上限 (条带长图不得 take(5))
        assertTrue(
            "renderWeekView 必须暴露全展开入口 (maxCoursesPerDay 参数, 条带长图禁截断)",
            renderers.contains("maxCoursesPerDay")
        )
    }
}

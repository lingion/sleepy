package com.lingion.sleepy.widget

import com.lingion.sleepy.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * v10 overflow scroll 架构 (2026-09-11 用户定稿, 分页模型整体退场):
 *
 * v9.2/v9.3 的固定视口分页在「轻微溢出」场景产出跨缝重复内容: 3 节课 + 26dp 溢出 →
 * 页 0 显示 2.5 节, 末页为贴底把前 2 节再显示一遍 — 用户看到的是「半节 + 拼图」,
 * 不是课程列表。用户定稿: 「任意一个视角, 用户看到的都是完整的课」。
 *
 * v10 契约:
 * 1. 条带 = 每课程行一个子项 (rowSpans 驱动, getCount = 行数) — 滑动由 launcher
 *    原生 ListView 处理, 每节课只出现一次、永远完整, 无页无缝。
 * 2. pageOffsetsDp / MAX_PAGES / 可见性过滤 / pageOffset 守卫全部删除。
 * 3. 行位图由 renderTodayRow 产出 (单行、无头、行高 = 行真实 dp)。
 * 4. 壳图仍按 viewport hDp 渲染 (首屏兜底)。
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
        // v10: 壳图尺寸 = hDp (首屏视口), 条带逐行子项 (每行 = 一门/一组冲突课)。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        assertTrue("nav overflow 走 pushScrollable", body.contains("pushScrollable"))
        assertTrue(
            "v10: nav overflow 壳图按 hDp 渲染",
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
        // v10: 壳图按 viewport hDp 渲染 (首屏), 条带改逐行子项 — 滑动语义交给
        // launcher 原生 ListView, 无分页参数。
        val today = widgetSource("TodayWidget.kt").readText()
        val navBody = today.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        val nonNavBody = today.substringAfter("!navEnabled").substringBefore("} else if (contentH")
        val shellPattern = Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*hDp\\.toFloat\\(\\),")
        assertTrue("nav overflow 壳图渲染高 = hDp (v10)", shellPattern.containsMatchIn(navBody))
        assertTrue("!navEnabled overflow 壳图渲染高 = hDp (v10)",
            shellPattern.containsMatchIn(nonNavBody))
    }

    @Test
    fun `renderTodayRegular row window uses full bitmap height not chrome-subtracted viewport`() {
        // v10: renderTodayRegular 回归单屏渲染器 (offset 恒 0) — 分页窗口概念删除,
        // 可见性过滤由条带逐行子项天然完成, 渲染器不再过滤行。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertFalse(
            "禁 chrome 扣减窗 (v9.2 混搭口径根因, v10 整体退场)",
            body.contains("pageVisiblePx")
        )
        assertFalse(
            "v10 渲染器禁可见性过滤 (条带逐行子项, 每行完整渲染)",
            body.contains("val visible = spans.filter")
        )
    }

    @Test
    fun `header block skips continuation pages via pageOffset guard`() {
        // v10: 头部块守卫回归 emptyHeader 单条件 — 无分页, 无续页概念;
        // 条带行位图由 renderTodayRow 产出 (从不画头)。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertFalse(
            "v10 禁 pageOffset 守卫 (分页删除, 头部守卫回归 emptyHeader 单条件)",
            body.contains("pageOffsetDp <= 0f") || body.contains("pageOffsetDp <= 0.0f")
        )
    }

    @Test
    fun `paging geometry is deleted - strip items are per-course rows`() {
        // v10 (2026-09-11 用户定稿): 「页」概念整体删除 — 固定视口分页在轻微溢出场景
        // 产出跨缝重复内容 (3 节课 + 26dp 溢出: 页 0 显示 2.5 节, 页 1 贴底把 1/2 节
        // 再显示一遍 = 用户看到的「半节 + 拼图」)。正常列表语义 = 每行一个子项,
        // 滑动由 launcher ListView 原生处理, 每节课只出现一次、永远完整。
        // 锁法: pageOffsetsDp / MAX_PAGES 必须不存在 (纯 JVM 反射)。
        val geoClass = TodayRowGeometry::class.java
        val methodNames = geoClass.declaredMethods.map { it.name }
        assertFalse(
            "pageOffsetsDp 必须删除 (分页模型整体退场)",
            "pageOffsetsDp" in methodNames
        )
        assertFalse(
            "MAX_PAGES 必须删除 (页数封顶随分页模型一起退场)",
            geoClass.declaredFields.any { it.name == "MAX_PAGES" }
        )
    }

    @Test
    fun `status content forces single page`() {
        // v9.3 修复 4 保留 (v10 语义更新): 状态分支 (无课表/学期外/无课) 不进条带 —
        // 状态内容高度 ≤ 任何 sane viewport, 静态/overflow 闸门自然走单页静态路径。
        // 行为锁死 (纯 JVM 可测):
        // 1. isTodayStatusContent 三态全真 (无课表 / 学期外 / 无课), 有课表有课时假
        val base = com.lingion.sleepy.data.entity.CourseEntity(
            id = 1L, groupId = "a", tableId = 1L, courseName = "甲",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 20, color = "blue"
        )
        val withCourse = WidgetData(
            date = LocalDate.now(), courses = listOf(base), timeJson = "",
            hasTable = true, semesterStatus = DateUtils.SemesterStatus.IN_RANGE
        )
        assertFalse("有课表+学期内+有课 = 可翻页内容", WidgetBitmapRenderers.isTodayStatusContent(withCourse))
        assertTrue(
            "无课表必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(withCourse.copy(hasTable = false))
        )
        assertTrue(
            "学期外必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(
                withCourse.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
            )
        )
        assertTrue(
            "无课必须判状态内容",
            WidgetBitmapRenderers.isTodayStatusContent(withCourse.copy(courses = emptyList()))
        )
        // 2. 状态内容的内容高度口径 = 顶 pad 单行 (≤ 任何 sane viewport → 永走静态路径)
        val statusH = WidgetBitmapRenderers.todayContentHeightDp(withCourse.copy(courses = emptyList()))
        assertTrue(
            "状态内容高度必须 ≤ 顶 pad + 一行 + 底 pad (永不溢出)",
            statusH <= TodayRowGeometry.contentTopDp(false) + TodayRowGeometry.ROW_H_DP + TodayRowGeometry.PAD_BOTTOM_DP
        )
    }

    @Test
    fun `strip rows are per-course-row items not viewport pages`() {
        // v10 核心契约: SCOPE_TODAY 条带 = 每行一张行位图 (getCount = 行数),
        // 禁再按 viewport 切页。锁法: ScrollStripService 的 SCOPE_TODAY 分支必须
        // 逐行渲染 (rowSpans → 每行 renderTodayRow), 不得出现 pageOffsetsDp 调用。
        val strip = widgetSource("ScrollStripService.kt").readText()
        val todayBody = strip.substringAfter("SCOPE_TODAY ->").substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "条带必须逐行渲染 (TodayRowGeometry.rowSpans 驱动)",
            todayBody.contains("TodayRowGeometry.rowSpans") && todayBody.contains("renderTodayRow")
        )
        assertFalse(
            "条带禁再调 pageOffsetsDp (分页模型删除)",
            todayBody.contains("pageOffsetsDp")
        )
        // 渲染端: 必须存在逐行渲染入口 renderTodayRow (单行位图, rowH = 行真实 dp 高)
        val renderers = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertTrue(
            "必须提供逐行渲染入口 renderTodayRow",
            renderers.contains("fun renderTodayRow(")
        )
    }
}

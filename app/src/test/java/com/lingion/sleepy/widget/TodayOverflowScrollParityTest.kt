package com.lingion.sleepy.widget

import com.lingion.sleepy.util.DateUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * v11 overflow scroll parity (2026-09-11 OPPO 真机三症状后撤回 v10):
 *
 * v9.x 固定视口分页: 用户定稿否决 (跨缝「半节 + 拼图」)。
 * v10 逐行子项: 用户实机证伪 — 三症状同根(无法拖动/双层错位叠影/TopBar 消失),
 *   全部源自多 child 在 OPPO launcher 上的 v1 extent 冻结 + 透明位图叠加 + 第一
 *   行覆盖壳图标题区。
 *
 * v11 形态 = v9.1 回归: 单 child 整张不透明长图 (条带 = 一张全展开位图), 滚动走
 * launcher 原生 ListView (与 TwoDay/WeekList 同构, 同台 OPPO 一直正常)。
 *
 * 锁死契约:
 * 1. pushScrollable 两条路径的 shell 渲染器都接 contentH (todayContentHeightDp 派生),
 *    与 ScrollStripService 条带渲染同参; 禁再传 hDp.toFloat() 给 renderToday 第三参数。
 * 2. renderTodayRegular 渲染器: 不再产出逐行位图 (renderTodayRow 已删); pageOffsetsDp /
 *    MAX_PAGES / pageVisiblePx / 可见性过滤 全部删除 — 单 child 整图一次画完。
 * 3. ScrollStripService.SCOPE_TODAY: 单 child = 一张全展开位图 (strips = listOf(full)),
 *    逐行子项的 rowSpans→renderTodayRow 链已退场。
 * 4. todayContentHeightDp: 状态内容 (无课表/学期外/无课) 走单行估算 (与渲染器 status 分支对得上)。
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
        // v11: 壳图尺寸 = contentH (全展开), 与条带同参 (v9.1 同形态回归)。
        // 锁法: 路径里 renderToday 第三参数必须出现 contentH 字面量, 不能再是 hDp.toFloat()。
        val src = widgetSource("TodayWidget.kt").readText()
        val body = src.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        assertTrue("nav overflow 走 pushScrollable", body.contains("pushScrollable"))
        assertTrue(
            "v11: nav overflow 壳图必须引用 contentH 作渲染高",
            body.contains("renderToday(") && body.contains("contentH")
        )
        // !navEnabled 分支也同源
        val nonNav = src.substringAfter("!navEnabled").substringBefore("} else if (contentH")
        assertTrue(
            "!navEnabled overflow 也须按 contentH 渲染 (TwoDay 形态同步)",
            nonNav.contains("renderToday(") && nonNav.contains("contentH")
        )
    }

    @Test
        fun `overflow scroll parity shell and strip share same renderToday size parameter`() {
        // v11: pushScrollable 路径下壳图与 ScrollStripService 条带传同样的位图高。
        // 锁法: 两条路径的 renderToday 第三参数都引用 contentH, 禁再传 hDp.toFloat()。
        val today = widgetSource("TodayWidget.kt").readText()
        val navBody = today.substringAfter("Today 系 overflow").substringBefore("fun loadDataSync")
        val nonNavBody = today.substringAfter("!navEnabled").substringBefore("} else if (contentH")
        val shellPattern = Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*contentH,")
        assertTrue("nav overflow 壳图渲染高 = contentH (v11)", shellPattern.containsMatchIn(navBody))
        assertTrue("!navEnabled overflow 壳图渲染高 = contentH (TwoDay 同步)",
            shellPattern.containsMatchIn(nonNavBody))
        // 禁: shell 不能再传 hDp.toFloat() 给 renderToday
        val bugPattern = Regex("renderToday\\(\\s*context,\\s*data,\\s*wDp\\.toFloat\\(\\),\\s*hDp\\.toFloat\\(\\)")
        assertFalse(
            "renderToday 第三参数禁传 hDp.toFloat() (v9 bug: 容器尺寸进 renderer 后丢行)",
            bugPattern.containsMatchIn(navBody) || bugPattern.containsMatchIn(nonNavBody)
        )
        // 条带 (v9.1 契约保留): 走 todayContentHeightDp 全展开派生
        val strip = widgetSource("ScrollStripService.kt").readText()
        val stripBody = strip.substringAfter("SCOPE_TODAY ->").substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "条带长图 = todayContentHeightDp 全展开派生 (ScrollStripService.kt)",
            stripBody.contains("todayContentHeightDp")
        )
    }

    @Test
        fun `renderTodayRegular is a single-shot full-content renderer no paging window`() {
        // v11: 渲染器回归 v9.1 — 一次画完整展开长图, 无分页/无可见性过滤/无 chrome 扣减窗。
        // 锁法: pageOffsetsDp / pageVisiblePx / val visible = spans.filter 全部禁止出现;
        //       渲染器也不再有逐行位图入口 renderTodayRow (整图渲染不再分包)。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertFalse(
            "禁 pageVisiblePx chrome 扣减窗 (v9.2 混搭口径根因, v11 整体退场)",
            body.contains("pageVisiblePx")
        )
        assertFalse(
            "v11 渲染器禁可见性过滤 (单 child 整图, 每节完整画一次)",
            body.contains("val visible = spans.filter")
        )
        // 整图渲染端: 不再存在逐行位图入口 renderTodayRow
        assertFalse(
            "renderTodayRow 入口已删 (整图渲染, 不再逐行分包)",
            src.contains("fun renderTodayRow(")
        )
    }

    @Test
        fun `paging geometry is deleted no pageOffsets no MAX_PAGES`() {
        // v11 (2026-09-11 OPPO 真机翻车后回归 v9.1 形态): 「页」概念整体删除 —
        // 分页 (v9.x) 在轻微溢出场景产出跨缝重复; 逐行子项 (v10) 在 OPPO 上复发
        // v1 extent 冻结 → 三症状同根。v11 形态 = 单 child 整张不透明长图, 滚动
        // 由 launcher 原生 ListView 在单 child 上完成, 永远不再有页数。
        // 锁法: pageOffsetsDp / MAX_PAGES 必须不存在 (纯 JVM 反射)。
        val geoClass = TodayRowGeometry::class.java
        val methodNames = geoClass.declaredMethods.map { it.name }
        assertFalse(
            "pageOffsetsDp 必须不存在 (分页模型整体退场)",
            "pageOffsetsDp" in methodNames
        )
        assertFalse(
            "MAX_PAGES 必须不存在 (页数封顶随分页模型一起退场)",
            geoClass.declaredFields.any { it.name == "MAX_PAGES" }
        )
    }

    @Test
        fun `status content forces single page with non-zero padding`() {
        // v11: 状态分支 (无课表/学期外/无课) 高度 = 顶 pad + 状态行 + 各自底 pad —
        // 不进 overflow, 直接静态 (statusH < hDp)。锁法:
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
        // 2. 状态内容的内容高度口径 = 顶 pad + 一行状态 + 各自底 pad (与渲染器 status 分支对得上)
        val statusH = WidgetBitmapRenderers.todayContentHeightDp(withCourse.copy(courses = emptyList()))
        assertTrue(
            "状态内容高度必须 = contentTopDp + 22f + 14f (与 renderer status 分支镜像)",
            statusH <= TodayRowGeometry.contentTopDp(false) + 22f + 14f + 0.01f
        )
    }

    @Test
        fun `strip is a single full-content bitmap not a row-by-row list`() {
        // v11 核心契约: SCOPE_TODAY 条带 = 一张全展开不透明位图 (strips = listOf(full)),
        // 禁再按 viewport 切页 / 按行分包。锁法: ScrollStripService 的 SCOPE_TODAY
        // 分支必须 strips = listOf(full!!), 不得出现 rowSpans→renderTodayRow 链。
        val strip = widgetSource("ScrollStripService.kt").readText()
        val todayBody = strip.substringAfter("SCOPE_TODAY ->").substringBefore("SCOPE_TWODAY ->")
        assertTrue(
            "条带走 renderToday 整图 (与 TwoDay/WeekList 同构, v9.1 形态回归)",
            todayBody.contains("renderToday(") && !todayBody.contains("renderTodayRow")
        )
        assertFalse(
            "条带禁调 renderTodayRow (v10 逐行子项退场)",
            todayBody.contains("renderTodayRow")
        )
        assertFalse(
            "条带禁再调 pageOffsetsDp (分页模型删除)",
            todayBody.contains("pageOffsetsDp")
        )
        // 渲染端: renderTodayRow 必须不存在 (整图渲染, 不再逐行分包)
        val renderers = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertFalse(
            "renderTodayRow 入口必须不存在 (整图渲染, 不再逐行分包)",
            renderers.contains("fun renderTodayRow(")
        )
        // 单 child 整长图 = strips = listOf(full)
        val stripFullAssign = Regex("strips\\s*=\\s*listOf\\(full[^)]*\\)").containsMatchIn(strip)
        assertTrue(
            "strips 必须 = listOf(full!!) 或 listOf(full) (单 child 整长图, 与 TwoDay/WeekList 同构)",
            stripFullAssign
        )
    }
}
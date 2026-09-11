package com.lingion.sleepy.widget

import com.lingion.sleepy.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * v9.3 overflow scroll parity (2026-09-10 v9.2 真机复盘接续):
 *
 * v9.1 复盘 (壳图/条带尺寸分裂) 之后, v9.2 把条带改成多页固定 viewport, 但窗与
 * 步长口径混搭: pageOffsetsDp 步长 = viewport − 52dp chrome, 而每页位图是完整
 * viewport 高、行按 raw crop 坐标 (y = topPx − offsetPx) 画 → 页间重叠 52dp
 * (跨缝行两页各画一遍) + 每页底部 52dp 空带; 头部块只看 emptyHeader → 第 2 页起
 * 每页重复画 ‹›/标题/右侧槽位。
 *
 * v9.3 锁死契约:
 * 1. 页 = 虚拟长条的完整 viewport 窗: 渲染端行窗 topPx < offsetPx + h (完整位图高),
 *    pageOffsetsDp 步长 = viewportHeightDp、末页钳到 contentH − viewport。
 *    页 0 (offset=0) 与静态壳图同参同函数 = 逐像素一致。
 * 2. 头部块 emptyHeader && pageOffsetDp<=0 双条件守卫 — 续页无头 (raw crop 语义)。
 * 3. pageOffsetsDp 退化 viewport 单页兜底 + 页数封顶 (MAX_PAGES), 封顶后末页仍贴底。
 * 4. 状态内容 (无课表/学期外/无课) 强制单页 — 状态分支画固定 y, 分页只产出 N 张同图。
 * 5. renderTodayRegular 行窗表达式只引用 spans / offsetPx / h (局部量)。
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
            stripBody.contains("pageOffsetsDp") && stripBody.contains("pageOffsetDp = offset")
        )
        assertTrue(
            "条带每页高度 = hDp (与壳图首屏同参)",
            stripBody.contains("wDp.toFloat(), hDp.toFloat()") && stripBody.contains("pageOffsetDp = offset")
        )
    }

    @Test
    fun `renderTodayRegular row window uses full bitmap height not chrome-subtracted viewport`() {
        // v9.3 核心修复 1: 行窗必须是完整位图高 — 每页 = 虚拟长条的完整 viewport 窗
        // (raw crop), 禁再把窗扣掉 52dp chrome (v9.2 页间重叠 52dp + 页底空带根因)。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertFalse(
            "禁 chrome 扣减窗 (v9.2 混搭口径: 页重叠 + 空带根因)",
            body.contains("pageVisiblePx")
        )
        // filter 行: 仅引用 spans / offsetPx / h (完整位图高, 局部量, 无外部常量)
        val filterLine = body.substringAfter("val visible =").substringBefore("\n").trim()
        assertTrue(
            "visible filter 表达式仅引用 spans/offsetPx/h (无外部常量)",
            "spans" in filterLine && "offsetPx" in filterLine && Regex("\\bh\\b") in filterLine
        )
    }

    @Test
    fun `header block skips continuation pages via pageOffset guard`() {
        // v9.3 核心修复 2: 头部块 (‹› + 标题 + 右侧槽位) 只在页 0 画 — 续页是 raw
        // crop, 再画头 = 每页重复标题 (v9.2 头部只看 emptyHeader 的根因)。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderTodayRegular(")
            .substringAfter("): Bitmap")
            .substringBefore("private fun renderTodayCompact")
        assertTrue(
            "头部块守卫必须双条件 emptyHeader && pageOffsetDp<=0 (续页无头)",
            body.contains("pageOffsetDp <= 0f") || body.contains("pageOffsetDp <= 0.0f")
        )
    }

    @Test
    fun `pageOffsetsDp generates fixed-viewport pages aligned to content bottom`() {
        // v9.3 核心: 页 = 完整 viewport 窗; 步长 = viewportHeightDp (v9.2 是可视行高);
        // 末页 offset 钳到 contentH − viewport (末页贴底); 页 0 恒 offset=0 (壳图同参)。
        // 锁法: 直接验证 pageOffsetsDp 几何 (纯 JVM 可测)。
        val hDp = 281f
        val contentH = 618f  // 模拟器实测 logcat 数据
        val offsets = TodayRowGeometry.pageOffsetsDp(contentH, hDp, headerSpace = true)
        assertEquals("首页恒 offset=0", 0f, offsets.first(), 0.01f)
        assertTrue("内容超出视口时页数 >= 2", offsets.size >= 2)
        // 每页 offset 增量 = viewport 高 281 (完整窗推进, 零重叠)
        for (i in 1 until offsets.size - 1) {
            val step = offsets[i] - offsets[i - 1]
            assertEquals("页 $i 步长必须 = viewport 高 281 (raw crop 窗, 零重叠)", 281f, step, 0.01f)
        }
        // 末页 = contentH − viewport (贴底)
        assertEquals(
            "末页 offset 必须对齐内容底 (maxOffset = contentH − viewport)",
            contentH - hDp,
            offsets.last(),
            0.01f
        )
        // 内容装得下: 1 页即可
        val small = TodayRowGeometry.pageOffsetsDp(120f, 281f, headerSpace = true)
        assertEquals("装得下时只一页", listOf(0f), small)
    }

    @Test
    fun `pageOffsetsDp caps page count and never loops in 1dp steps`() {
        // v9.3 核心修复 3: 页数封顶 — 退化 viewport 不再 coerceAtLeast(1f) 后按 1dp
        // 步进产出 O(contentH) 张位图; 单页兜底 + MAX_PAGES 上限, 封顶后末页仍贴底。
        // 1. 装不下一行课的退化视口 → 单页 (禁 1dp 步进死循环)
        val degenerate = TodayRowGeometry.pageOffsetsDp(600f, 30f, headerSpace = false)
        assertEquals("退化视口 (30dp 装不下一行) 必须单页兜底", listOf(0f), degenerate)
        // 2. 正常视口但内容极长 → 页数 ≤ MAX_PAGES, 且末页仍贴底 (maxOffset)
        val contentH = 20000f
        val offsets = TodayRowGeometry.pageOffsetsDp(contentH, 281f, headerSpace = true)
        val maxPages = TodayRowGeometry.MAX_PAGES
        assertTrue("MAX_PAGES 常量必须 >= 2 (确实能分多页)", maxPages >= 2)
        assertTrue(
            "页数 ${offsets.size} 必须封顶 MAX_PAGES=$maxPages",
            offsets.size <= maxPages
        )
        assertEquals(
            "封顶后末页仍必须贴底",
            contentH - 281f,
            offsets.last(),
            0.01f
        )
    }

    @Test
    fun `status content forces single page`() {
        // v9.3 核心修复 4: 状态分支 (无课表/学期外/无课) 内容是固定 y 的状态行,
        // 分页只产出 N 张同图 — 行为锁死 (纯 JVM 可测):
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
        // 2. 状态内容的分页序列恒单页 (pageOffsetsDp 用单页高度算, 恒 [0])
        val statusH = WidgetBitmapRenderers.todayContentHeightDp(withCourse.copy(courses = emptyList()))
        assertEquals(
            "状态内容只产单页 offset 序列",
            listOf(0f),
            TodayRowGeometry.pageOffsetsDp(statusH, 100f, headerSpace = true)
        )
        // 3. 渲染入口闸: pageOffsetDp>0 对状态内容强制归零 (条带端防御性透传也归零)
        val renderers = widgetSource("WidgetBitmapRenderers.kt").readText()
        val renderToday = renderers.substringAfter("fun renderToday(")
            .substringBefore("fun todayCompactTexts")
        assertTrue(
            "renderToday 入口必须对状态内容强制 pageOffset=0 (单页兜底闸)",
            renderToday.contains("isTodayStatusContent") && renderToday.contains("effectiveOffset")
        )
    }
}

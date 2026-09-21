package com.lingion.sleepy.widget

import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WidgetVariant 紧凑档文本选取逻辑 — 纯 JVM 单测。
 *
 * 仓库无 Robolectric(先例: AppPrefsIsolationTest "degraded from Robolectric"),
 * Bitmap 像素管线无法在纯 JVM 断言(Bitmap.createBitmap 返回 null 桩)。
 * 因此这里断言渲染与测试共用的单一事实来源 todayCompactTexts 的
 * 资源解析无关核心重载((Int)->String resolver 版):
 *   - 课表内 + 学期内: 保留全部课程名，不能因 SMALL 变体截断数据
 *   - 无课表 / 学期前 / 学期后 / 无课: 各自状态文案分支选中正确资源
 *
 * Bitmap 尺寸断言(SMALL 档输出宽高)需 Robolectric — 本仓库未引入该依赖且
 * 任务约束禁新增, 交由 assembleDebug 编译 + 源码审查保障(详见 task-1-report.md)。
 */
class WidgetVariantRenderTest {

    private val data = WidgetData(
        date = LocalDate.of(2026, 9, 1),
        courses = listOf(
            testCourse(name = "高等数学", startNode = 1),
            testCourse(name = "大学英语", startNode = 3),
            testCourse(name = "数据结构", startNode = 5)
        ),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
        hasTable = true
    )

    /** resId → 资源名字面量, 证明分支选中的是"哪个资源"而非具体文案 */
    private val resNames = mapOf(
        R.string.widget_create_schedule to "widget_create_schedule",
        R.string.semester_not_started to "semester_not_started",
        R.string.semester_ended to "semester_ended",
        R.string.today_no_course to "today_no_course",
        R.string.no_course to "no_course"
    )

    private fun resolve(resId: Int): String = resNames.getValue(resId)

    @Test
    fun `compact texts keep all courses`() {
        val texts = WidgetBitmapRenderers.todayCompactTexts(::resolve, data)
        assertEquals(listOf("高等数学", "大学英语", "数据结构"), texts)
    }

    @Test
    fun `compact texts cover no-table out-of-semester and empty states`() {
        val noTable = data.copy(hasTable = false)
        assertEquals(
            listOf("widget_create_schedule"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, noTable)
        )

        val beforeStart = data.copy(semesterStatus = DateUtils.SemesterStatus.BEFORE_START)
        assertEquals(
            listOf("semester_not_started"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, beforeStart)
        )

        val afterEnd = data.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
        assertEquals(
            listOf("semester_ended"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, afterEnd)
        )

        val empty = data.copy(courses = emptyList())
        assertEquals(
            listOf("today_no_course"),
            WidgetBitmapRenderers.todayCompactTexts(::resolve, empty)
        )
    }

    @Test
    fun `variant enum exposes regular and small`() {
        assertEquals(
            listOf(WidgetVariant.REGULAR, WidgetVariant.SMALL),
            WidgetVariant.values().toList()
        )
    }

    @Test
    fun `small receiver declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, TodaySmallWidgetReceiver().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.TodayWidgetReceiver().variantHint)
    }

    /** TwoDayData fixture — 字段以 WidgetContent.kt 真实定义为准(days: List<DayData>) */
    private val twoDayData = TwoDayData(
        days = listOf(
            DayData(
                date = LocalDate.of(2026, 9, 1),
                dayOfWeek = 2,
                courses = listOf(
                    testCourse(name = "高等数学", startNode = 1),
                    testCourse(name = "大学英语", startNode = 3)
                ),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 2),
                dayOfWeek = 3,
                courses = listOf(testCourse(name = "数据结构", startNode = 5)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true
    )

    @Test
    fun `twoDay compact texts keep all courses across both days`() {
        val texts = WidgetBitmapRenderers.twoDayCompactTexts(::resolve, twoDayData)
        assertEquals(listOf("高等数学", "大学英语", "数据结构"), texts)
    }

    @Test
    fun `twoDay compact texts cover no-table out-of-semester and empty states`() {
        val noTable = twoDayData.copy(hasTable = false)
        assertEquals(
            listOf("widget_create_schedule"),
            WidgetBitmapRenderers.twoDayCompactTexts(::resolve, noTable)
        )

        val beforeStart = twoDayData.copy(semesterStatus = DateUtils.SemesterStatus.BEFORE_START)
        assertEquals(
            listOf("semester_not_started"),
            WidgetBitmapRenderers.twoDayCompactTexts(::resolve, beforeStart)
        )

        val afterEnd = twoDayData.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
        assertEquals(
            listOf("semester_ended"),
            WidgetBitmapRenderers.twoDayCompactTexts(::resolve, afterEnd)
        )

        // 无课: 今日栏为空 → no_course 一行(与 regular TwoDay 渲染空列同资源)
        val empty = twoDayData.copy(days = twoDayData.days.map { it.copy(courses = emptyList()) })
        assertEquals(
            listOf("no_course"),
            WidgetBitmapRenderers.twoDayCompactTexts(::resolve, empty)
        )
    }

    @Test
    fun `twoDay small receiver declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, TwoDaySmallWidgetReceiver().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.TwoDayWidgetReceiver().variantHint)
    }

    /** WeekData fixture — 字段以 WidgetContent.kt 真实定义为准(days: List<DayData>) */
    private val weekData = WeekData(
        days = listOf(
            DayData(
                date = LocalDate.of(2026, 9, 2),
                dayOfWeek = 3,
                courses = listOf(
                    testCourse(name = "高等数学", startNode = 1),
                    testCourse(name = "数据结构", startNode = 3)
                ),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 3),
                dayOfWeek = 4,
                courses = listOf(testCourse(name = "大学英语", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 4),
                dayOfWeek = 5,
                courses = emptyList(),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true
    )

    @Test
    fun `weekList compact face is 3-day column face not text lines`() {
        // 2026-09-14 用户定稿: 列表·小 2×2 = 与周视图·小同一张脸 (今天邻域 ≤3 列
        // 彩色胶囊)。旧「周X 课名」两行纯文本脸整体退场 — 禁回流。
        var dir: java.io.File? = java.io.File(".").absoluteFile
        lateinit var r: String
        while (dir != null) {
            val f = java.io.File(dir, "app/src/main/java/com/lingion/sleepy/widget/WidgetBitmapRenderers.kt")
            if (f.exists()) { r = f.readText(); break }
            dir = dir.parentFile
        }
        val fn = r.substringAfter("private fun renderWeekListCompact").substringBefore("fun renderWeekList(")
        // 2026-09-15: 列选取升级为 compactShownDays (compactWindow 优先, 回退 weekViewCompactColumns)
        assertTrue("compact 档必须复用 compact 列选取单一口径", fn.contains("compactShownDays("))
        assertTrue("compact 档必须走 Regular 渲染器", fn.contains("renderWeekListRegular("))
        assertFalse("纯文本脸 weekListCompactTexts 禁回流", r.contains("weekListCompactTexts"))
        // 列选取本身 (今天邻域 ≤3 列) 由 weekViewCompactColumns 既有单测锁定
    }

    @Test
    fun `weekList small receiver declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, WeekListSmallWidgetReceiver().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.WeekListWidgetReceiver().variantHint)
    }

    /** 周视图 compact 列选取 fixture — 全 7 天都有课 */
    private fun weekDataAllDays(): WeekData = WeekData(
        days = (1..7).map { dow ->
            DayData(
                date = LocalDate.of(2026, 8, 31).plusDays(dow.toLong() - 1),
                dayOfWeek = dow,
                courses = listOf(testCourse(name = "day$dow 课", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        },
        hasTable = true
    )

    /** 周视图 compact 列选取 fixture — 只有周四有课 */
    private fun weekDataOnlyThursday(): WeekData = WeekData(
        days = (1..7).map { dow ->
            DayData(
                date = LocalDate.of(2026, 8, 31).plusDays(dow.toLong() - 1),
                dayOfWeek = dow,
                courses = if (dow == 4) listOf(testCourse(name = "周四课", startNode = 1)) else emptyList(),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        },
        hasTable = true
    )

    @Test
    fun `weekView compact columns center on today`() {
        // 周三(3)为中心 → 与今天距离最近 3 列 = 周二三四
        assertEquals(
            listOf(2, 3, 4),
            WidgetBitmapRenderers.weekViewCompactColumns(weekDataAllDays(), todayDow = 3, maxColumns = 3)
        )
    }

    @Test
    fun `weekView compact columns skip empty days`() {
        // 只有周四(4)有课 → 池里只剩周四; 今天(周三)无课不在有课池中
        assertEquals(
            listOf(3, 4),
            WidgetBitmapRenderers.weekViewCompactColumns(weekDataOnlyThursday(), todayDow = 3, maxColumns = 3)
        )
    }

    @Test
    fun `weekView compact columns include today even if empty`() {
        // 今天(周三)无课也必须出现在结果中(锚点语义) — 且不挤掉唯一有课的周四
        val cols = WidgetBitmapRenderers.weekViewCompactColumns(weekDataOnlyThursday(), todayDow = 3, maxColumns = 3)
        assertTrue("today(3) must appear in $cols", 3 in cols)
        assertTrue("thursday(4) must appear in $cols", 4 in cols)
    }

    @Test
    fun `weekView small receiver declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, WeekViewSmallWidgetReceiver().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.WeekViewWidgetReceiver().variantHint)
    }

    // ── 最小档三天窗口 (2026-09-15 用户令): 今日居第一位/第二位, 上下周打通 ──

    @Test
    fun `compact window today first is today plus two`() {
        val wed = LocalDate.of(2026, 9, 2)
        assertEquals(
            listOf(wed, wed.plusDays(1), wed.plusDays(2)),
            WidgetBitmapRenderers.compactWindowDates(wed, todayFirst = true)
        )
    }

    @Test
    fun `compact window today first sunday rolls into next week`() {
        // 周日(2026-09-06)居第一位 → 日、一、二 — 下周一二顺上来
        val sun = LocalDate.of(2026, 9, 6)
        assertEquals(
            listOf(sun, sun.plusDays(1), sun.plusDays(2)),
            WidgetBitmapRenderers.compactWindowDates(sun, todayFirst = true)
        )
    }

    @Test
    fun `compact window today second monday includes last sunday`() {
        // 周一(2026-08-31)居第二位 → 上周日、周一、周二 — 窗口越出本周
        val mon = LocalDate.of(2026, 8, 31)
        assertEquals(
            listOf(mon.minusDays(1), mon, mon.plusDays(1)),
            WidgetBitmapRenderers.compactWindowDates(mon, todayFirst = false)
        )
    }

    @Test
    fun `compact shown days prefers window in date order across weeks`() {
        // 跨周窗口 [周日(7),周一(1),周二(2)] 必须按日期升序, 不得按 dayOfWeek 重排
        val mon = LocalDate.of(2026, 8, 31)
        val win = listOf(
            DayData(mon.minusDays(1), 7, listOf(testCourse(name = "上周日课", startNode = 1)), TimeTableUtils.DEFAULT_TIME_JSON),
            DayData(mon, 1, listOf(testCourse(name = "周一课", startNode = 1)), TimeTableUtils.DEFAULT_TIME_JSON),
            DayData(mon.plusDays(1), 2, listOf(testCourse(name = "下周二课", startNode = 1)), TimeTableUtils.DEFAULT_TIME_JSON),
        )
        val data = weekDataAllDays().copy(compactWindow = win)
        val shown = WidgetBitmapRenderers.compactShownDays(data, visibleDays = (1..7).toSet(), todayDow = 1)
        assertEquals(listOf(mon.minusDays(1), mon, mon.plusDays(1)), shown.map { it.date })
    }

    @Test
    fun `compact shown days window respects visibleDays and defends empty filter`() {
        val mon = LocalDate.of(2026, 8, 31)
        val win = listOf(
            DayData(mon.minusDays(1), 7, emptyList(), TimeTableUtils.DEFAULT_TIME_JSON),
            DayData(mon, 1, emptyList(), TimeTableUtils.DEFAULT_TIME_JSON),
            DayData(mon.plusDays(1), 2, emptyList(), TimeTableUtils.DEFAULT_TIME_JSON),
        )
        val data = weekDataAllDays().copy(compactWindow = win)
        // 隐藏周日 → 只剩周一二
        assertEquals(
            listOf(mon, mon.plusDays(1)),
            WidgetBitmapRenderers.compactShownDays(data, visibleDays = setOf(1, 2), todayDow = 1).map { it.date }
        )
        // 过滤后为空 → 回退全窗口 (防御)
        assertEquals(3, WidgetBitmapRenderers.compactShownDays(data, visibleDays = setOf(5), todayDow = 1).size)
    }

    @Test
    fun `compact shown days falls back to legacy columns without window`() {
        // compactWindow 空 (旧数据源/预览) → 回退今天邻域 ≤3 列旧口径
        val shown = WidgetBitmapRenderers.compactShownDays(weekDataAllDays(), visibleDays = (1..7).toSet(), todayDow = 3)
        assertEquals(listOf(2, 3, 4), shown.map { it.dayOfWeek })
    }

    @Test
    fun `weekGrid small provider declares SMALL variant`() {
        assertEquals(WidgetVariant.SMALL, WeekGridSmallWidgetProvider().variantHint)
        assertEquals(WidgetVariant.REGULAR, com.lingion.sleepy.widget.WeekGridWidgetProvider().variantHint)
    }

    // ── weekGrid 最小档: 今日数据映射(渲染走 renderToday(SMALL), 与今日课程·小同一张脸) ──

    /** 全周 DayData fixture(带课) — 2026-08-31 起的周一…周日 */
    private fun fullWeekWithCourses(): WeekData {
        val timeJson = TimeTableUtils.DEFAULT_TIME_JSON
        return WeekData(
            days = (1..7).map { dow ->
                DayData(
                    date = LocalDate.of(2026, 8, 31).plusDays(dow.toLong() - 1),
                    dayOfWeek = dow,
                    courses = listOf(testCourse(name = "周$dow 课", startNode = 1)),
                    timeJson = timeJson
                )
            },
            hasTable = true
        )
    }

    @Test
    fun `weekGrid minimum maps today day to WidgetData`() {
        // 周三(3)锚点: 取周三的 DayData → WidgetData(date=周三, courses=周三课程)
        val wd = fullWeekWithCourses()
        val today = LocalDate.of(2026, 9, 2)  // 周三
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(wd, today)
        assertEquals(today, mapped.date)
        assertEquals(listOf("周3 课"), mapped.courses.map { it.courseName })
        assertEquals(wd.days[2].timeJson, mapped.timeJson)
        assertTrue(mapped.hasTable)
        assertEquals(wd.themeKey, mapped.themeKey)
        assertEquals(wd.isDark, mapped.isDark)
        assertEquals(wd.semesterStatus, mapped.semesterStatus)
    }

    @Test
    fun `weekGrid minimum maps missing today to empty courses not no-table`() {
        // 今天无课: courses 为空但 hasTable 仍为 true(映射 days.first 失败时的回退分支)
        val emptyDays = fullWeekWithCourses().copy(
            days = fullWeekWithCourses().days.map { it.copy(courses = emptyList()) }
        )
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(emptyDays, LocalDate.of(2026, 9, 2))
        assertTrue(mapped.hasTable)
        assertTrue(mapped.courses.isEmpty())
    }

    @Test
    fun `weekGrid minimum no-table passes through`() {
        val noTable = fullWeekWithCourses().copy(hasTable = false)
        val mapped = WidgetBitmapRenderers.weekGridMinimumTodayData(noTable, LocalDate.of(2026, 9, 2))
        assertFalse(mapped.hasTable)
    }

    // ── drawCourse meta 行: 恒单行, 时间/地点各自半宽截断 ──
    // (用户原话: 长的截断, 不允许元素过长挡住其他的, 不能挤占其他的 →
    //  旧"放不下拆两行"会让 meta 块总高超过胶囊行高, 竖向盖住相邻行 = 挤占, 已废)

    @Test
    fun `courseMetaLines single line fits time and location`() {
        // 宽度足够 → "3-4节 · 教3-101" 一行
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-4节 · 教3-101"), lines)
    }

    @Test
    fun `courseMetaLines overflow keeps single line with both halves ellipsized`() {
        // measure=长度×10, " · "=30。combined 13 字=130 > 100 → 溢出:
        // 半宽 = 50 - 15 = 35 → 时间截到 "3-…", 地点截到 "教3…", 一行
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { t -> t.length * 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-… · 教3…"), lines)
    }

    @Test
    fun `courseMetaLines extreme overflow still returns exactly one line`() {
        // 任何宽度下绝不返回两行(两行 = 总高超行高挤占相邻行)
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { t -> t.length * 10f },
            maxWidth = 10f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(1, lines.size)
    }

    @Test
    fun `courseMetaLines no room returns time only`() {
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = ""
        )
        assertEquals(listOf("3-4节"), lines)
    }

    @Test
    fun `courseMetaLines no time returns room only`() {
        // 无时间只有地点 → 不带前导分隔符
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "",
            room = "教3-101"
        )
        assertEquals(listOf("教3-101"), lines)
    }

    @Test
    fun `courseMetaLines both blank returns empty`() {
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "",
            room = "  "
        )
        assertTrue(lines.isEmpty())
    }
}

/** 测试用最小 CourseEntity — 字段以实体真实定义为准(参照 CourseColorUtilTest 同款 fixture) */
private fun testCourse(name: String, startNode: Int): CourseEntity = CourseEntity(
    id = 0L,
    groupId = "grp-widget-test",
    tableId = 1L,
    courseName = name,
    day = 2,
    startNode = startNode,
    step = 2,
    startWeek = 1,
    endWeek = 16,
    color = "#FF6750A4"
)

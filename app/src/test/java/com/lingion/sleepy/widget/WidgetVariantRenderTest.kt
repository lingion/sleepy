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
 *   - 课表内 + 学期内: 只保留首门课程名(compact 档仅显示一行)
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
    fun `compact texts keep only first course`() {
        val texts = WidgetBitmapRenderers.todayCompactTexts(::resolve, data)
        assertEquals(1, texts.size)
        assertEquals("高等数学", texts[0])
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
                courses = listOf(testCourse(name = "高等数学", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 2),
                dayOfWeek = 3,
                courses = emptyList(),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true
    )

    @Test
    fun `twoDay compact texts keep only today first course`() {
        val texts = WidgetBitmapRenderers.twoDayCompactTexts(::resolve, twoDayData)
        assertEquals(1, texts.size)
        assertEquals("高等数学", texts[0])
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

    /** 星期名 fake — 模拟 DateUtils.localizedDay 的 R.array.day_names 输出(周一…周日) */
    private fun dayLabel(dow: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[dow - 1]

    @Test
    fun `weekList compact texts show today and tomorrow first courses`() {
        // 2026-09-02 = 周三(锚点), 明天 09-03 = 周四; 周五无课跳过
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            ::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), weekData
        )
        assertEquals(2, texts.size)
        // 每天只取首课(courses 已按 startNode 排序): 周三取 高等数学 非第 2 门 数据结构
        assertEquals("周三 高等数学", texts[0])
        assertEquals("周四 大学英语", texts[1])
    }

    @Test
    fun `weekList compact texts cover no-table out-of-semester and empty states`() {
        val noTable = weekData.copy(hasTable = false)
        assertEquals(
            listOf("widget_create_schedule"),
            WidgetBitmapRenderers.weekListCompactTexts(::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), noTable)
        )

        val beforeStart = weekData.copy(semesterStatus = DateUtils.SemesterStatus.BEFORE_START)
        assertEquals(
            listOf("semester_not_started"),
            WidgetBitmapRenderers.weekListCompactTexts(::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), beforeStart)
        )

        val afterEnd = weekData.copy(semesterStatus = DateUtils.SemesterStatus.AFTER_END)
        assertEquals(
            listOf("semester_ended"),
            WidgetBitmapRenderers.weekListCompactTexts(::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), afterEnd)
        )

        // 今明两天全无课 → no_course 一行
        val empty = weekData.copy(days = weekData.days.map { it.copy(courses = emptyList()) })
        assertEquals(
            listOf("no_course"),
            WidgetBitmapRenderers.weekListCompactTexts(::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), empty)
        )
    }

    @Test
    fun `weekList compact sunday anchor keeps today first`() {
        // 周日(2026-09-06)锚点: 今天=周日(7), 明天=周一(ISO 1, 周循环回绕)
        // 回归: 旧实现按 ISO 星期排序 → 周一(明天)排到周日(今天)前面
        val sundayWeek = weekData.copy(
            days = listOf(
                DayData(
                    date = LocalDate.of(2026, 9, 6),
                    dayOfWeek = 7,
                    courses = listOf(testCourse(name = "周日体育", startNode = 1)),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON
                ),
                DayData(
                    date = LocalDate.of(2026, 9, 7),
                    dayOfWeek = 1,
                    courses = listOf(testCourse(name = "周一高数", startNode = 1)),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON
                )
            )
        )
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            ::resolve, ::dayLabel, LocalDate.of(2026, 9, 6), sundayWeek
        )
        assertEquals(2, texts.size)
        assertEquals("周日 周日体育", texts[0])   // 今天恒为第 1 行
        assertEquals("周一 周一高数", texts[1])   // 明天第 2 行
    }

    @Test
    fun `weekList compact only today has courses yields single row`() {
        // 只有今天(周三)有课, 明天(周四)无课 → 只回 1 行
        val todayOnly = weekData.copy(
            days = weekData.days.map {
                if (it.dayOfWeek == 3) it else it.copy(courses = emptyList())
            }
        )
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            ::resolve, ::dayLabel, LocalDate.of(2026, 9, 2), todayOnly
        )
        assertEquals(1, texts.size)
        assertEquals("周三 高等数学", texts[0])
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

    // ── drawCourse meta 行拆分: 时间一行/地点一行(宽度不够时) ──

    @Test
    fun `courseMetaLines single line fits time and location`() {
        // 宽度足够 → 保持旧行为: "3-4节 · 教3-101" 一行
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { _ -> 10f },
            maxWidth = 100f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-4节 · 教3-101"), lines)
    }

    @Test
    fun `courseMetaLines splits into time line and room line when overflow`() {
        // 拼行放不下 → 拆两行: 时间/地点
        val lines = WidgetBitmapRenderers.courseMetaLines(
            measure = { t -> t.length * 10f },
            maxWidth = 50f,
            timeStr = "3-4节",
            room = "教3-101"
        )
        assertEquals(listOf("3-4节", "教3-101"), lines)
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

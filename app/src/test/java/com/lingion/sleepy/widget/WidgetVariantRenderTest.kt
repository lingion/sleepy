package com.lingion.sleepy.widget

import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
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
        R.string.today_no_course to "today_no_course"
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

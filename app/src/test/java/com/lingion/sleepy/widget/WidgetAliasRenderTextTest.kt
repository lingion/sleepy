package com.lingion.sleepy.widget

import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * issue#26 widget 场景别名 — 紧凑档文本(resolver 注入版)按 useAlias 取名。
 * 纯 JVM 单测, 与 WidgetVariantRenderTest 同一套单一事实来源, 只是加了 useAlias 参数。
 */
class WidgetAliasRenderTextTest {

    private val resolve = { resId: Int ->
        mapOf(
            R.string.widget_create_schedule to "widget_create_schedule",
            R.string.semester_not_started to "semester_not_started",
            R.string.semester_ended to "semester_ended",
            R.string.today_no_course to "today_no_course",
            R.string.no_course to "no_course"
        )[resId]!!
    }

    private val dayLabel = { dow: Int ->
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[dow - 1]
    }

    private fun testCourse(name: String, alias: String = "", startNode: Int = 1): CourseEntity = CourseEntity(
        id = 0L,
        groupId = "grp-alias-test",
        tableId = 1L,
        courseName = name,
        alias = alias,
        day = 2,
        startNode = startNode,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4"
    )

    // ── today 紧凑档 ──

    private val todayData = WidgetData(
        date = LocalDate.of(2026, 9, 1),
        courses = listOf(
            testCourse(name = "高等数学", alias = "高数", startNode = 1),
            testCourse(name = "大学英语", alias = "", startNode = 3)
        ),
        timeJson = TimeTableUtils.DEFAULT_TIME_JSON,
        hasTable = true
    )

    @Test
    fun today_compact_alias_off_keeps_original_names() {
        val texts = WidgetBitmapRenderers.todayCompactTexts(resolve, useAlias = false, data = todayData)
        assertEquals(listOf("高等数学", "大学英语"), texts)
    }

    @Test
    fun today_compact_alias_on_shows_alias_blank_falls_back() {
        val texts = WidgetBitmapRenderers.todayCompactTexts(resolve, useAlias = true, data = todayData)
        assertEquals(listOf("高数", "大学英语"), texts)
    }

    @Test
    fun today_compact_status_lines_untouched_by_alias() {
        val noTable = todayData.copy(hasTable = false)
        assertEquals(
            listOf("widget_create_schedule"),
            WidgetBitmapRenderers.todayCompactTexts(resolve, useAlias = true, data = noTable)
        )
        val empty = todayData.copy(courses = emptyList())
        assertEquals(
            listOf("today_no_course"),
            WidgetBitmapRenderers.todayCompactTexts(resolve, useAlias = true, data = empty)
        )
    }

    // ── twoDay 紧凑档 ──

    private val twoDayData = TwoDayData(
        days = listOf(
            DayData(
                date = LocalDate.of(2026, 9, 1),
                dayOfWeek = 2,
                courses = listOf(testCourse(name = "高等数学", alias = "高数", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 2),
                dayOfWeek = 3,
                courses = listOf(testCourse(name = "数据结构", alias = "DS", startNode = 5)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true
    )

    @Test
    fun twoDay_compact_alias_on_renames_across_days() {
        val texts = WidgetBitmapRenderers.twoDayCompactTexts(resolve, useAlias = true, data = twoDayData)
        assertEquals(listOf("高数", "DS"), texts)
    }

    @Test
    fun twoDay_compact_alias_off_keeps_original() {
        val texts = WidgetBitmapRenderers.twoDayCompactTexts(resolve, useAlias = false, data = twoDayData)
        assertEquals(listOf("高等数学", "数据结构"), texts)
    }

    // ── weekList 紧凑档 ──

    private val weekData = WeekData(
        days = listOf(
            DayData(
                date = LocalDate.of(2026, 9, 2),
                dayOfWeek = 3,
                courses = listOf(testCourse(name = "高等数学", alias = "高数", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                date = LocalDate.of(2026, 9, 3),
                dayOfWeek = 4,
                courses = listOf(testCourse(name = "大学英语", alias = "", startNode = 1)),
                timeJson = TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true
    )

    @Test
    fun weekList_compact_alias_on_and_blank_fallback() {
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            resolve, dayLabel, useAlias = true, today = LocalDate.of(2026, 9, 2), data = weekData
        )
        assertEquals(listOf("周三 高数", "周四 大学英语"), texts)
    }

    @Test
    fun weekList_compact_alias_off_keeps_original() {
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            resolve, dayLabel, useAlias = false, today = LocalDate.of(2026, 9, 2), data = weekData
        )
        assertEquals(listOf("周三 高等数学", "周四 大学英语"), texts)
    }

    @Test
    fun weekList_compact_sunday_anchor_with_alias() {
        val sundayWeek = weekData.copy(
            days = listOf(
                DayData(
                    date = LocalDate.of(2026, 9, 6),
                    dayOfWeek = 7,
                    courses = listOf(testCourse(name = "周日体育", alias = "体育", startNode = 1)),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON
                ),
                DayData(
                    date = LocalDate.of(2026, 9, 7),
                    dayOfWeek = 1,
                    courses = listOf(testCourse(name = "周一高数", alias = "高数", startNode = 1)),
                    timeJson = TimeTableUtils.DEFAULT_TIME_JSON
                )
            )
        )
        val texts = WidgetBitmapRenderers.weekListCompactTexts(
            resolve, dayLabel, useAlias = true, today = LocalDate.of(2026, 9, 6), data = sundayWeek
        )
        assertEquals(2, texts.size)
        assertEquals("周日 体育", texts[0])
        assertEquals("周一 高数", texts[1])
    }
}

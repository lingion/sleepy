package com.lingion.sleepy.data.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CourseEntity.inWeek] 的核心语义 — 类型列 type=3 = 按周次列实际指定的周.
 *
 * 改动背景: 此前 type 缺失/不明时 parseSimpleText 硬填 0=每周都上, 把"周次=6 单次实验"
 * 误标成"每周都上". 现在 type=3 等价于"只在 startWeek..endWeek 区间内上, 区间外不上".
 *
 * 旧行为保护: type=0/1/2 行为不变; 非法 type (4+, 负数) 仍按每周兜底防丢数据.
 */
class CourseEntityTest {

    private fun c(startWeek: Int, endWeek: Int, type: Int) = CourseEntity(
        id = 0,
        groupId = "",
        tableId = 0,
        courseName = "test",
        day = 1,
        startNode = 1,
        step = 1,
        startWeek = startWeek,
        endWeek = endWeek,
        type = type,
        color = "#FF6750A4"
    )

    // --- type=3: 按周次列实际指定的周 ---

    @Test
    fun type3_singleWeek_onlyThatWeek() {
        // 第 6 周上一次的实验课, type=3
        val course = c(6, 6, 3)
        assertTrue("第6周应该上", course.inWeek(6))
        assertFalse("第5周应该不上", course.inWeek(5))
        assertFalse("第7周应该不上", course.inWeek(7))
    }

    @Test
    fun type3_rangeInsideInWeek() {
        // 第 4-8 周上, type=3
        val course = c(4, 8, 3)
        for (w in 1..16) {
            val expected = w in 4..8
            assertEqualsSafe("第${w}周", expected, course.inWeek(w))
        }
    }

    // --- 旧 type=0/1/2 行为不变 ---

    @Test
    fun type0_weekly_anyWeekInRange() {
        val course = c(1, 16, 0)
        for (w in 1..16) assertTrue(course.inWeek(w))
        assertFalse(course.inWeek(0))
        assertFalse(course.inWeek(17))
    }

    @Test
    fun type1_oddWeeksOnly() {
        val course = c(1, 16, 1)
        for (w in 1..16) {
            assertEqualsSafe("第${w}周", w % 2 == 1, course.inWeek(w))
        }
    }

    @Test
    fun type2_evenWeeksOnly() {
        val course = c(1, 16, 2)
        for (w in 1..16) {
            assertEqualsSafe("第${w}周", w % 2 == 0, course.inWeek(w))
        }
    }

    @Test
    fun type4_fallsBackToEveryWeekInsideRange_legacySafety() {
        // 防御性: 历史数据可能存了非法 type (例如旧 schema 误写), 不应把课全丢
        val course = c(6, 6, 4)
        assertTrue(course.inWeek(6))
    }

    // 边界: week 在 startWeek 之前/之后
    @Test
    fun weekBeforeStart_returnsFalse() {
        val course = c(6, 10, 0)
        assertFalse(course.inWeek(5))
        assertFalse(course.inWeek(0))
    }

    @Test
    fun weekAfterEnd_returnsFalse() {
        val course = c(1, 8, 0)
        assertFalse(course.inWeek(9))
        assertFalse(course.inWeek(16))
    }

    private fun assertEqualsSafe(msg: String, expected: Boolean, actual: Boolean) {
        if (expected != actual) throw AssertionError("$msg: expected=$expected actual=$actual")
    }
}
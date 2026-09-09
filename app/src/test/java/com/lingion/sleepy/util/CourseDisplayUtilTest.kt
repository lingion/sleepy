package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#26 展示名解析 — 纯 JVM 单测。
 * 场景开关语义: 关 = 原名(默认, 升级行为不变); 开 = 别名(空回退原名)。
 */
class CourseDisplayUtilTest {

    private fun course(name: String, alias: String = ""): CourseEntity = CourseEntity(
        groupId = "g1",
        tableId = 1L,
        courseName = name,
        alias = alias,
        day = 1,
        startNode = 3,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4"
    )

    @Test
    fun switch_off_returns_original_name() {
        val c = course(name = "高等数学", alias = "高数")
        assertEquals("高等数学", CourseDisplayUtil.displayName(c, useAlias = false))
    }

    @Test
    fun switch_on_with_alias_returns_alias() {
        val c = course(name = "高等数学", alias = "高数")
        assertEquals("高数", CourseDisplayUtil.displayName(c, useAlias = true))
    }

    @Test
    fun switch_on_with_blank_alias_falls_back_to_original() {
        val c = course(name = "大学英语", alias = "   ")
        assertEquals("大学英语", CourseDisplayUtil.displayName(c, useAlias = true))
    }

    @Test
    fun switch_on_with_empty_alias_falls_back_to_original() {
        val c = course(name = "大学英语", alias = "")
        assertEquals("大学英语", CourseDisplayUtil.displayName(c, useAlias = true))
    }

    @Test
    fun lambda_overload_delegates_to_boolean_version() {
        val c = course(name = "数据结构", alias = "DS")
        assertEquals("DS", CourseDisplayUtil.displayName(c) { it.alias.isNotBlank() })
        assertEquals("数据结构", CourseDisplayUtil.displayName(c) { false })
    }

    @Test
    fun alias_is_display_only_field() {
        // 别名不影响 inWeek/startNode 等业务字段
        val c = course(name = "数据结构", alias = "DS")
        assertTrue(c.inWeek(3))
        assertEquals(3, c.startNode)
    }
}

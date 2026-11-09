package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwAhmuNodeBoundsTest {
    private fun row(name: String, first: Int, length: Int) = JSONObject()
        .put("kcmc", name).put("xm", "张老师").put("jsmc", "实验楼")
        .put("xqj", 3).put("sjd", first).put("skcd", length).put("qsz", 2).put("jsz", 3)

    private fun parse(vararg rows: JSONObject) = JwAhmuParser(JSONArray(rows.toList()).toString()).generateCourseList()

    @Test fun `explicit node after thirty is preserved`() {
        assertEquals(listOf(JwCourse("晚课", "实验楼", "张老师", 3, 31, 32, 2, 3)),
            parse(row("晚课", 31, 2)))
    }

    @Test fun `block crossing thirty keeps every declared node`() {
        assertEquals(listOf(JwCourse("实验", "实验楼", "张老师", 3, 30, 32, 2, 3)),
            parse(row("实验", 30, 3)))
    }

    @Test fun `length after thirty is a length and is not clamped`() {
        assertEquals(listOf(JwCourse("集中实践", "实验楼", "张老师", 3, 2, 32, 2, 3)),
            parse(row("集中实践", 2, 31)))
    }

    @Test fun `nonpositive fractional and overflowing nodes cannot create courses`() {
        assertEquals(listOf(JwCourse("正常", "实验楼", "张老师", 3, 5, 6, 2, 3)), parse(
            row("负节次", -1, 2), row("零长度", 1, 0), row("负长度", 1, -1),
            row("小数", 1, 2).put("sjd", 1.5), row("小数长度", 1, 2).put("skcd", 2.5),
            row("溢出起点", Int.MAX_VALUE, 2), row("溢出长度", 1, Int.MAX_VALUE),
            row("正常", 5, 2)))
    }

    @Test fun `explicit resource boundary is accepted but excessive expansion is rejected`() {
        assertEquals(listOf(JwCourse("边界", "实验楼", "张老师", 3, 366, 366, 2, 3)), parse(
            row("边界", 366, 1), row("超界起点", 367, 1), row("超界终点", 366, 2),
            row("超界长度", 1, 367)))
    }
}

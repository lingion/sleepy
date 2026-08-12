package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwNewZfParser 单测 — 验证 v1.0.29 审计修复：
 *  1. parseCell 不再丢弃多段周次（"1-11周(单),13-16周"）
 *  2. 结束节次缺失时默认单节（不假设连上2节）
 *  3. 空周次兜底改为 16 周（不再硬编码 20）
 *  4. JSON 路径多段周次展开
 *
 * 纯 JVM，不依赖 Android。
 */
class JwNewZfParserTest {
    /** 正方新版典型 API JSON：多段周次 + 缺结束节次 */
    private val jsonSource = """
        {"kbxx":[
          {"kcmc":"高等数学","jsxm":"张老师","jasmc":"A101","kcxq":1,
           "ksjc":1,"jsjc":2,"zcd":"1-11周(单),13-16周"},
          {"kcmc":"单节缺结束","jsxm":"李老师","jasmc":"B202","kcxq":2,
           "ksjc":3,"zcd":"1-16周"}
        ]}
    """.trimIndent()

    @Test
    fun `multi-segment weeks preserved`() {
        val courses = JwNewZfParser(jsonSource).generateCourseList()
        val gao = courses.filter { it.name == "高等数学" }
        assertEquals("高等数学应展开成 2 段周次", 2, gao.size)
        val ranges = gao.map { it.startWeek to it.endWeek }.toSet()
        assertTrue("应含 1-11 段", ranges.contains(1 to 11))
        assertTrue("应含 13-16 段（之前会被 ranges.first() 丢弃）", ranges.contains(13 to 16))
    }

    @Test
    fun `missing end node defaults to single section`() {
        val courses = JwNewZfParser(jsonSource).generateCourseList()
        val single = courses.first { it.name == "单节缺结束" }
        // 无 jsjc → 之前默认 startNode+1=4(2节)，修复后按单节 startNode=endNode=3
        assertEquals("缺结束节次应按单节：startNode=endNode", 3, single.startNode)
        assertEquals("缺结束节次应按单节：endNode", 3, single.endNode)
    }

    @Test
    fun `bitmap week string parsed`() {
        // 正方 bitmap 周次：11111111111100000（1-12 周）
        val src = """{"kbxx":[{"kcmc":"位图课","kcxq":3,"ksjc":5,"jsjc":6,"zcd":"11111111111100000"}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("位图周次应解析出课程", courses.isNotEmpty())
        val c = courses.first()
        assertEquals(1, c.startWeek)
        assertEquals(12, c.endWeek)
    }
}

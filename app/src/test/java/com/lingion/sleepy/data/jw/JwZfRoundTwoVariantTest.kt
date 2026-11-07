package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwZfRoundTwoVariantTest {
    @Test fun `generic zf replays the table tb branch without weekday header`() {
        val html = """
            <table id='table_tb'><tr><td id='td_4-5' rowspan='3'><table><tr><td>
            <p>离散数学</p><p>课程属性</p><p>理科楼305</p><p>陈老师</p><p>1-5周(单),8周</p>
            </td></tr></table></td></tr></table>
        """.trimIndent()
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 4, 5, 7, 1, 5, 1),
            JwCourse("离散数学", "理科楼305", "陈老师", 4, 5, 7, 8, 8)),
            JwOldZfParser(html).generateCourseList())
    }

    @Test fun `generic zf replays positional p fields`() {
        val html = """
            <table id='table1'><tr><td id='3-1' rowspan='2'><div><span class='title'>离散数学</span>
            <p>(1-2,5-6节)1-5周(单)</p><p>理科楼305</p><p>陈老师</p>
            </div></td></tr></table>
        """.trimIndent()
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 3, 1, 2, 1, 5, 1),
            JwCourse("离散数学", "理科楼305", "陈老师", 3, 5, 6, 1, 5, 1)),
            JwOldZfParser(html).generateCourseList())
    }

    @Test fun `generic zf replays class table six with teacher distinct from room`() {
        val html = """
            <table id='Table6'><tr><td>第1节</td><td rowspan='2'>
            <a href='#'>离散数学<br>1-5单(1-2节)<br>陈老师<br>理科楼305</a>
            </td></tr></table>
        """.trimIndent()
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 1, 5, 1)),
            JwOldZfParser(html).generateCourseList())
    }

    @Test fun `class table six slash layout uses source span and isolated week`() {
        val html = """
            <table id='Table6'><tr><td><span class='festival'>3</span></td><td rowspan='2'>
            <a href='#'>离散数学<br>2节/单周(1-5)<br>陈老师<br>理科楼305</a>
            </td></tr></table>
        """.trimIndent()
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 3, 4, 1, 5, 1)),
            JwOldZfParser(html).generateCourseList())
    }

    @Test fun `generic zf falls back to the second old layout`() {
        val html = "<table id='Table1'><tr><td>第1节</td><td>" +
            "离散数学 A {第1-4周}(1-2节) 陈老师 理科楼305</td></tr></table>"
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 1, 4)),
            JwOldZfParser(html).generateCourseList())
    }

    @Test fun `declared class table route is wired`() {
        val html = "<table id='Table6'><tr><td>第1节</td><td rowspan='2'>" +
            "<a href='#'>离散数学<br>1-5单(1-2节)<br>陈老师<br>理科楼305</a></td></tr></table>"
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 1, 5, 1)),
            JwParserRegistry.selectBest(html, "zf_cls").first)
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** A td class is present in modern Kingosoft too; it cannot identify the legacy parser. */
class JwKingoTdRoutingTest {
    private fun assertAllImportPaths(html: String, expected: Set<JwCourse>) {
        val paths = mapOf(
            "direct kingo_new" to JwKingoParser(html).generateCourseList(),
            "declared kingo_new" to JwParserRegistry.selectBest(html, JwProtocol.TYPE_KINGO_NEW).first,
            "automatic registry" to JwParserRegistry.selectBest(html).first,
        )
        for ((path, courses) in paths) {
            assertEquals("$path must not duplicate an occurrence", expected.size, courses.size)
            assertEquals("$path must preserve each course and its actual weekday", expected, courses.toSet())
        }
    }

    @Test fun `td class and occupied rowspan columns preserve Monday and Tuesday`() {
        val html = """<table id="mytable">
            <tr class="H"><th>时段</th><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td rowspan="2">上午</td><td rowspan="2">1-4节</td>
                <td class="td" rowspan="2"><div>周一实验<br>教师甲<br>1-8周[1-4节]<br>实验室甲</div></td>
                <td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td></tr>
            <tr><td class="td"><div>周二实验<br>教师乙<br>2-10双[3-4节]<br>实验室乙</div></td>
                <td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td></tr>
            </table>"""
        assertAllImportPaths(html, setOf(
            JwCourse("周一实验", "实验室甲", "教师甲", 1, 1, 4, 1, 8),
            JwCourse("周二实验", "实验室乙", "教师乙", 2, 3, 4, 2, 10, 2),
        ))
    }

    @Test fun `td class cannot override Sunday first weekday headers`() {
        val html = """<table id="mytable">
            <tr class="H"><th colspan="2">时段</th><th>星期日</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th></tr>
            <tr><td>下午</td><td>第5节</td><td class="td"><div>周日实验<br>教师甲<br>3-9周[5节]<br>实验室甲</div></td>
                <td class="td"><div>周一实验<br>教师乙<br>2-8周[5节]<br>实验室乙</div></td>
                <td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td></tr></table>"""
        assertAllImportPaths(html, setOf(
            JwCourse("周日实验", "实验室甲", "教师甲", 7, 5, 5, 3, 9),
            JwCourse("周一实验", "实验室乙", "教师乙", 1, 5, 5, 2, 8),
        ))
    }

    @Test fun `one legacy compatible block does not suppress a modern block without room`() {
        val html = """<table id="mytable">
            <tr class="H"><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td class="td"><div>有教室课程<br>教师甲<br>1-8周[1-2节]<br>A101</div></td>
                <td class="td"><div>待定教室课程<br>教师乙<br>1-8周[3-4节]</div></td>
                <td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td><td class="td"></td></tr></table>"""
        assertAllImportPaths(html, setOf(
            JwCourse("有教室课程", "A101", "教师甲", 1, 1, 2, 1, 8),
            JwCourse("待定教室课程", "", "教师乙", 2, 3, 4, 1, 8),
        ))
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwKingoGridOccurrenceTest {
    @Test fun `occupied Monday cell and two rowspan gutters preserve Tuesday on next row`() {
        val html = """<table id="mytable">
            <tr class="H"><th>时段</th><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td rowspan="2">上午</td><td rowspan="2">1-4节</td>
                <td rowspan="2"><div>周一实验<br>教师甲<br>1-8周[1-4节]<br>实验室甲</div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td></tr>
            <tr><td><div>周二实验<br>教师乙<br>2-10周(双)[3-4节]<br>实验室乙</div></td>
                <td></td><td></td><td></td><td></td><td></td></tr>
            </table>"""
        assertEquals(setOf(
            JwCourse("周一实验", "实验室甲", "教师甲", 1, 1, 4, 1, 8),
            JwCourse("周二实验", "实验室乙", "教师乙", 2, 3, 4, 2, 10, 2)
        ), JwKingoParser(html).generateCourseList().toSet())
    }

    @Test fun `weekday headers determine Sunday first layout`() {
        val html = """<table id="mytable">
            <tr class="H"><th colspan="2">时段</th><th>星期日</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th></tr>
            <tr><td>下午</td><td>第5节</td><td><div>周日实验<br>教师<br>3-9周[5节]<br>实验室</div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td></tr></table>"""
        assertEquals(JwCourse("周日实验", "实验室", "教师", 7, 5, 5, 3, 9),
            JwKingoParser(html).generateCourseList().single())
    }

    @Test fun `kbDiv takes final location token after optional metadata`() {
        val html = """<table id="kbDiv"><tbody><tr><td>数学(1-8周 中间字段 A101)</td>
            <td></td><td></td><td></td><td></td><td></td><td></td></tr></tbody></table>"""
        assertEquals("A101", JwKingoParser(html).generateCourseList().single().room)
    }
}

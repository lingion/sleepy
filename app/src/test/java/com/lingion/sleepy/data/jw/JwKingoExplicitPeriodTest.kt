package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwKingoExplicitPeriodTest {
    @Test fun `mytable retains explicitly numbered periods above thirty`() {
        val html = """<table id="mytable"><tr><td></td><td><div>
            实验课<br>张老师<br>2-4周[31-32节]<br>A101</div></td></tr></table>"""
        assertEquals(listOf(JwCourse("实验课", "A101", "张老师", 2, 31, 32, 2, 4)),
            JwKingoParser(html).generateCourseList())
    }

    @Test fun `format two retains three digit explicit period and credit`() {
        val html = """<table><thead><tr><td>课程</td><td>教师</td><td>地点</td><td>周次</td><td>节次</td><td>学分</td></tr></thead>
            <tbody><tr><td>[C01]短训课</td><td>[T01]李老师</td><td>B202</td><td>3-5</td><td>周日[101节]</td><td>1.5</td></tr></tbody></table>"""
        assertEquals(listOf(JwCourse("短训课", "B202", "李老师", 7, 101, 101, 3, 5, credit = 1.5f)),
            JwKingoParser(html).generateCourseList())
    }

    @Test fun `positive selection report retains explicit high periods`() {
        val cells = MutableList(11) { "<td></td>" }
        cells[1] = "<td>[C01]实验课</td>"
        cells[2] = "<td>2.5</td>"
        cells[4] = "<td>王老师[T01]</td>"
        cells[10] = "<td>[1-3周]星期五[31-32节]/C303</td>"
        val html = "<div id='pageRpt'><table><tr>${cells.joinToString("")}</tr></table></div>"
        assertEquals(listOf(JwCourse("实验课", "C303", "王老师", 5, 31, 32, 1, 3, credit = 2.5f)),
            JwKingoZxParser(html).generateCourseList())
    }

    @Test fun `legacy grid retains explicit high periods`() {
        val html = """<div id="xkinfo"><table><tr><td class="td"><div>
            夜校课<br>赵老师<br>2-4[31-32]<br>D404</div></td><td></td></tr></table></div>"""
        assertEquals(listOf(JwCourse("夜校课", "D404", "赵老师", 1, 31, 32, 2, 4)),
            JwKingoZxParser(html).generateCourseList())
    }

    @Test fun `invalid explicit period numbers do not become courses`() {
        for (nodes in listOf("0", "-1", "32-31", "2147483648", "1-2147483648")) {
            val html = """<table id="mytable"><tr><td><div>
                无效课<br>教师<br>1-2周[$nodes 节]<br>A101</div></td></tr></table>"""
            assertTrue(nodes, WakeUpCompat.parseKingoSuperHtml(html).isEmpty())
        }
    }
}

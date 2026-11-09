package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic protocol examples derived from the compact and ordinary mytable branches. */
class JwKingoCompactOccurrenceTest {
    @Test fun `compact nbsp separators retain separate course teacher weeks and room`() {
        val html = """<table id="mytable"><tr><td class="td">
            <div class="div1"><span class="xkinfo">&nbsp;</span></div>
            <div>离散数学&nbsp;张老师 1-8周[3-4节]&nbsp;&nbsp;教学楼201</div>
            </td></tr></table>"""
        val course = JwKingoParser(html).generateCourseList().single()
        assertEquals(JwCourse("离散数学", "教学楼201", "张老师", 1, 3, 4, 1, 8), course)
    }

    @Test fun `compact marker does not change week parsing in another weekday cell`() {
        val html = """<table id="mytable"><tr>
            <td class="td"><div class="div1"><span class="xkinfo">&nbsp;</span></div>
              <div>紧凑课<br>张老师 1-8周[1-2节]<br>A101</div></td>
            <td class="td"><div>普通课<br>李老师<br>1-7周 (单)[3-4节]<br>B202</div></td>
            </tr></table>"""
        val courses = JwKingoParser(html).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(JwCourse("普通课", "B202", "李老师", 2, 3, 4, 1, 7, 1),
            courses.single { it.name == "普通课" })
    }
}

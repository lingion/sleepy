package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwKingoTeachingArrangementTest {
    @Test fun `five weekday kbDiv retains cells instead of requiring a weekend`() {
        val html = """<table id="kbDiv"><tbody><tr>
            <td></td><td>高等数学(1-8 A101)</td><td></td><td></td><td></td>
            </tr></tbody></table>"""
        assertEquals(listOf(JwCourse("高等数学", "A101", "", 2, 1, 1, 1, 8)),
            JwKingoParser(html).generateCourseList())
    }

    @Test fun `kbDiv separates final timing parentheses from parentheses in course name`() {
        val html = """<table id="kbDiv"><tbody><tr>
            <td>高等数学(下)(1-8 A101)</td><td></td><td></td><td></td><td></td><td></td><td></td>
            </tr></tbody></table>"""
        assertEquals(listOf(JwCourse("高等数学(下)", "A101", "", 1, 1, 1, 1, 8)),
            JwKingoParser(html).generateCourseList())
    }
}

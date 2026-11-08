package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwHtmlExplicitEndpointTest {
    @Test fun oldQzRejectsExplicitOverflowInsteadOfCreatingSingleValues() {
        for (time in listOf("2-2147483648周[1-2节]", "2-4周[1-2147483648节]")) {
            assertEquals(time, emptyList<JwCourse>(), oldQz(time))
        }
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 2, 2, 7, 7)), oldQz("7周[2节]"))
    }

    @Test fun newZfGridRejectsOverflowingExplicitEndAndKeepsSingleNode() {
        assertEquals(emptyList<JwCourse>(), grid("1-2147483648"))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 2, 2, 1, 4)), grid("2"))
    }

    @Test fun newZfListRejectsOverflowingExplicitEndAndKeepsSingleNode() {
        assertEquals(emptyList<JwCourse>(), list("1-2147483648"))
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 2, 2, 1, 4)), list("2"))
    }

    private fun oldQz(time: String) = JwOldQzSuperParser(
        "<table id='kbtable'><tr><td>节次</td><td><div>数学<br>王老师<br>$time<br>A</div></td></tr></table>",
        JwOldQzSuperParser.Form.BASE).generateCourseList()

    private fun grid(nodes: String) = JwNewZfParser(
        "<table id='kbgrid_table_0'><tr><td id='1-1'><div class='timetable_con'>" +
            "<div class='title'>数学</div><p>1-4周($nodes" + "节)</p><p>A</p><p>王老师</p>" +
            "</div></td></tr></table>").generateCourseList()

    private fun list(nodes: String) = JwNewZfParser(
        "<table id='kblist_table'><tbody><tr><td>表头</td></tr></tbody>" +
            "<tbody><tr><td>$nodes</td><td><div class='title'>数学</div>" +
            "周数：1-4周 上课地点：A 教师：王老师</td></tr></tbody></table>").generateCourseList()
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwNewZfHtmlNodeBoundsTest {
    @Test fun gridRejectsZeroStart() {
        assertEquals(emptyList<JwCourse>(), grid("0-1"))
    }

    @Test fun gridRejectsReversedRange() {
        assertEquals(emptyList<JwCourse>(), grid("2-1"))
    }

    @Test fun listRejectsZeroStart() {
        assertEquals(emptyList<JwCourse>(), list("0-1"))
    }

    @Test fun listRejectsReversedRange() {
        assertEquals(emptyList<JwCourse>(), list("2-1"))
    }

    @Test fun htmlViewsRejectPastResourceBoundary() {
        for (nodes in listOf("366-367", "367")) {
            assertEquals(nodes, emptyList<JwCourse>(), grid(nodes))
            assertEquals(nodes, emptyList<JwCourse>(), list(nodes))
        }
    }

    @Test fun gridKeepsThreeDigitExplicitStart() {
        for ((first, last) in listOf(100 to 101, 366 to 366)) {
            assertEquals(listOf(course(first, last)), grid("$first-$last"))
        }
    }

    @Test fun extraMinusCannotCreateANegativeEnd() {
        assertEquals(emptyList<JwCourse>(), grid("1--2"))
        assertEquals(emptyList<JwCourse>(), list("1--2"))
    }

    @Test fun missingEndMeansOneExplicitNode() {
        for (node in listOf(2, 366)) {
            assertEquals(listOf(course(node, node)), grid("$node"))
            assertEquals(listOf(course(node, node)), list("$node"))
        }
    }

    @Test fun invalidExplicitEndCannotFallBackToTheStart() {
        for (nodes in listOf("1-2147483648", "1-")) {
            assertEquals(nodes, emptyList<JwCourse>(), grid(nodes))
            assertEquals(nodes, emptyList<JwCourse>(), list(nodes))
        }
    }

    private fun course(first: Int, last: Int) = JwCourse("数学", "A", "王老师", 1, first, last, 1, 4)

    private fun grid(nodes: String) = JwNewZfParser(
        "<table id='kbgrid_table_0'><tr><td id='1-1'><div class='timetable_con'>" +
            "<div class='title'>数学</div><p>1-4周($nodes" + "节)</p><p>A</p><p>王老师</p>" +
            "</div></td></tr></table>").generateCourseList()

    private fun list(nodes: String) = JwNewZfParser(
        "<table id='kblist_table'><tbody><tr><td>表头</td></tr></tbody>" +
            "<tbody><tr><td>$nodes</td><td><div class='title'>数学</div>" +
            "周数：1-4周 上课地点：A 教师：王老师</td></tr></tbody></table>").generateCourseList()
}

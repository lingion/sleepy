package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwScauRemainingNodeBoundsTest {
    private fun course(first: Int, last: Int) = JwCourse("数学", "A101", "李老师", 1, first, last, 1, 3)
    private fun element(label: String, timing: String): List<JwCourse> {
        val block = "<div style='position:relative'><div></div><div></div><div></div>" +
            "<div>数学</div><div>李老师</div><div>班级</div><div>$timing(1-3周)</div><div>A101</div></div>"
        return JwScauParser("<div class='el-table__body-wrapper'><table class='el-table__body'><tbody>" +
            "<tr><td>$label</td><td>$block</td>" + "<td></td>".repeat(6) + "</tr></tbody></table></div>")
            .generateCourseList()
    }
    private fun paired(group: String) = JwScauParser(
        "<table border=1 bordercolor='#000000'><tr><td valign=top>$group" + "节)</td></tr>" +
            "<tr><td valign=top>数学：李老师<br>1-3周<br>A101</td></tr></table>").generateCourseList()

    @Test fun elementRowsAndCourseFieldsKeepExplicitHighNodes() {
        for ((first, last) in listOf(31 to 32, 100 to 101, 365 to 366)) {
            assertEquals(listOf(course(first, last)), element("$first-$last" + "节", ""))
            assertEquals(listOf(course(first, last)), element("1-2节", "$first-$last" + "节"))
        }
    }

    @Test fun pairedOldTableUsesItsExplicitGroupBeyondThirty() {
        assertEquals(listOf(course(61, 62)), paired("31"))
        assertEquals(listOf(course(365, 366)), paired("183"))
    }

    @Test fun invalidExplicitNodesCannotBecomeRowDefaultsOrOverflow() {
        for (nodes in listOf("0-1", "2-1", "366-367", "1-2147483648")) {
            assertEquals(nodes, emptyList<JwCourse>(), element("1-2节", nodes + "节"))
            assertEquals(nodes, emptyList<JwCourse>(), element(nodes + "节", ""))
        }
        for (group in listOf("0", "184", "2147483648")) {
            assertEquals(group, emptyList<JwCourse>(), paired(group))
        }
    }
}

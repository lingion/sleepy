package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwKingoZxReportMetadataTest {
    private fun report(id: String, timing: String, credit: String = "2.5", name: String = "实验") : String {
        val cells = MutableList(if (id == "reportArea") 13 else 11) { "<td></td>" }
        cells[1] = "<td>[EX01]$name</td>"
        cells[2] = "<td>$credit</td>"
        cells[4] = "<td>张老师[T01]</td>"
        cells[cells.lastIndex] = "<td><font>$timing</font></td>"
        return "<div id='$id'><table><tr style='height:20px'>${cells.joinToString("")}</tr></table><span>选定</span></div>"
    }

    private fun assertRoutes(html: String, expected: List<JwCourse>) {
        assertEquals(expected, JwKingoZxParser(html).generateCourseList())
        assertEquals(expected, JwParserRegistry.parserFor("kingo_zx", html).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(html, "kingo_zx").first)
    }

    @Test fun `pageRpt keeps plain teacher and credit rather than only anchor names`() {
        assertRoutes(report("pageRpt", "[1-8周]星期三[3-4节]/A101"),
            listOf(JwCourse("实验", "A101", "张老师", 3, 3, 4, 1, 8, credit=2.5f)))
    }

    @Test fun `reportArea uses its thirteenth column and en space room delimiter`() {
        assertRoutes(report("reportArea", "[1-7周单]周日[5-6节] A101<br>[10-12周双]周二[7节] B202"), listOf(
            JwCourse("实验", "A101", "张老师", 7, 5, 6, 1, 7, 1, credit=2.5f),
            JwCourse("实验", "B202", "张老师", 2, 7, 7, 10, 12, 2, credit=2.5f),
        ))
    }

    @Test fun `positive selection report accepts day section parity weeks token order`() {
        assertRoutes(report("pageRpt", "六 1-2节 双 [2-8周]/C303"),
            listOf(JwCourse("实验", "C303", "张老师", 6, 1, 2, 2, 8, 2, credit=2.5f)))
    }

    @Test fun `online name suffix preserves online location when source omits room`() {
        assertRoutes(report("pageRpt", "[1-8周]星期一[1-2节]", name="在线课m"),
            listOf(JwCourse("在线课m", "在线", "张老师", 1, 1, 2, 1, 8, credit=2.5f)))
    }

    @Test fun `report credits are always finite and nonnegative`() {
        for (credit in listOf("invalid", "NaN", "Infinity", "-1")) {
            assertRoutes(report("pageRpt", "[1-8周]星期三[3-4节]/A101", credit),
                listOf(JwCourse("实验", "A101", "张老师", 3, 3, 4, 1, 8)))
        }
    }
}

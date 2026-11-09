package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class JwZjuPostExplicitPeriodTest {
    private fun cell(name: String, height: String? = null) =
        "<td${height?.let { " rowspan='$it'" }.orEmpty()}><a><strong>$name</strong><br><br>" +
            "类别<br>时间||(前二 周)<br>王<br>A101</a></td>"

    private fun parse(rows: String) = JwPostgradSchoolParser(JSONArray().put(
        "<div><form id='kcbForm'></form><table><tr><th>头</th></tr>$rows</table></div>"
    ).toString(), "zju_post").generateCourseList()

    @Test fun `large rowspan preserves physical nodes and next row weekday occupancy`() {
        assertEquals(listOf(JwCourse("研讨", "A101", "王", 1, 1, 35, 1, 2),
            JwCourse("专题", "A101", "王", 2, 2, 2, 1, 2)),
            parse("<tr>${cell("研讨", "35")}</tr><tr>${cell("专题")}</tr>"))
    }

    @Test fun `late physical row plus rowspan preserves explicit high endpoints`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 1, 35, 37, 1, 2)),
            parse("<tr></tr>".repeat(34) + "<tr>${cell("专题", "3")}</tr>"))
    }

    @Test fun `rowspan may end exactly at resource boundary`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 1, 1, 366, 1, 2)),
            parse("<tr>${cell("专题", "366")}</tr>"))
        assertEquals(listOf(JwCourse("专题", "A101", "王", 1, 365, 366, 1, 2)),
            parse("<tr></tr>".repeat(364) + "<tr>${cell("专题", "2")}</tr>"))
    }

    @Test fun `invalid explicit rowspan never becomes missing rowspan default`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 1, 1, 1, 1, 2)),
            parse("<tr>${cell("专题")}</tr>"))
        for (height in listOf("0", "-1", "367", "2147483647", "2147483648", "待定", "")) {
            assertEquals(height, emptyList<JwCourse>(), parse("<tr>${cell("专题", height)}</tr>"))
        }
    }

    @Test fun `span endpoint beyond resource bound rejects overflowing arrangement`() {
        assertEquals(emptyList<JwCourse>(), parse("<tr></tr>".repeat(365) + "<tr>${cell("专题", "2")}</tr>"))
    }
}

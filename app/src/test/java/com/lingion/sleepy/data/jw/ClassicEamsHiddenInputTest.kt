package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class ClassicEamsHiddenInputTest {
    @Test fun `hidden values are attributes and may precede the field name`() {
        for (html in listOf(
            """<input type="hidden" name="ids" value="2024000001">""",
            """<input value='2024000001' name='ids' type='hidden'>""",
            """<input
              name = "ids"
              value = "2024000001"
              type = "hidden">""",
        )) assertEquals(html, "2024000001", ClassicEamsWebVpn.extractIds(html))
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId("""<input name="semester.id" value="279">"""))
        assertEquals("9", ClassicEamsWebVpn.extractProjectId("""<input value="9" name="project.id">"""))
    }

    @Test fun `candidate parameters preserve the selected hidden semester and project`() {
        val html = """<form name="courseTableForm" action="courseTableForStd.action">
          <input name="ids" value="2024000001"><input value="280" name="semester.id">
          <input name="project.id" value="9"></form>"""
        assertEquals(listOf(
            "ignoreHead=1&setting.kind=std&startWeek=&project.id=9&semester.id=280&ids=2024000001",
            "ignoreHead=1&setting.kind=std&startWeek=&semester.id=280&ids=2024000001",
        ), ClassicEamsWebVpn.buildCandidates(html))
    }

    @Test fun `calendar accepts the quoted object keys used by course table pages`() {
        for (calendar in listOf(
            """semesterCalendar({"onChange":"","value":"279"})""",
            """semesterCalendar({'onChange':'','value':'279'})""",
            """semesterCalendar({ value : "279" })""",
        )) assertEquals(calendar, "279", ClassicEamsWebVpn.extractSemesterId(calendar))
    }

    @Test fun `missing value cannot borrow an unrelated field or following text`() {
        for (html in listOf(
            """<input name="ids"><input name="other" value="99">""",
            """<input name="ids">value="99"""",
            """<input name="ids" value=""><input name="other" value="99">""",
            """<div name="ids" value="99"></div>""",
            """<input data-name="ids" value="99">""",
        )) assertNull(html, ClassicEamsWebVpn.extractIds(html))
    }
}

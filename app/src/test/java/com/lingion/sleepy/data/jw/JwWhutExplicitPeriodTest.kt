package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwWhutExplicitPeriodTest {
    private fun parseMap(first: Any, last: Any) = JwWhutParser(JSONObject(
        """{"datas":{"cxxszhxqkb":{"rows":[{"KCM":"专题","SKJS":"王","JASMC":"A101","SKXQ":2,"KSJC":8,"JSJC":9,"SKZC":"101"}]}}}"""
    ).put("sleepyWhutSectionMap", JSONObject().put("8", first).put("9", last)).toString()).generateCourseList()

    @Test fun `dynamic physical periods above thirty override legacy DM mapping`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 2, 35, 37, 1, 3, 1)), parseMap(35, 37))
    }

    @Test fun `dynamic map accepts the resource boundary`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 2, 366, 366, 1, 3, 1)), parseMap(366, 366))
    }

    @Test fun `invalid dynamic values retain existing fallback mapping`() {
        for (invalid in listOf(0, -1, 367, 2147483648L, "待定")) {
            assertEquals(invalid.toString(), listOf(JwCourse("专题", "A101", "王", 2, 6, 7, 1, 3, 1)),
                parseMap(invalid, invalid))
        }
    }

    @Test fun `legacy HTML physical periods are not mapped as DMs`() {
        val html = "<div id='xqkb'><table class='table-class-even'><tr><td style='x'>" +
            "<a>专题<span>@A101</span><span>1-3单周(35-37节)</span></a></td></tr></table></div>"
        assertEquals(listOf(JwCourse("专题", "A101", "", 1, 35, 37, 1, 3, 1)),
            JwWhutParser(html).generateCourseList())
    }
}

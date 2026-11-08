package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwStumExplicitPeriodTest {
    private fun parse(vararg nodes: String): List<JwCourse> {
        val lesson = JSONArray().put(JSONObject().put("f2", "专题").put("f3", "王").put("f4", "A101"))
        val rows = JSONArray()
        for (node in nodes) rows.put(JSONObject().put("name", node).put("start_time", "08:00")
            .put("end_time", "08:45").put("wednesday", lesson.toString()))
        return JwStumParser(JSONArray().put(JSONObject().put("data",
            JSONObject().put("week", 3).put("data", rows))).toString()).generateCourseList()
    }

    @Test fun `explicit high lesson retains actual time and all course fields`() {
        assertEquals(listOf(JwCourse("专题", "A101", "王", 3, 35, 35, 3, 3,
            startTime = "08:00", endTime = "08:45")), parse("35"))
    }

    @Test fun `resource boundary and fixed letter lessons remain distinct`() {
        assertEquals(listOf(366, 11, 12, 13).map { node ->
            JwCourse("专题", "A101", "王", 3, node, node, 3, 3, startTime = "08:00", endTime = "08:45")
        }, parse("366", "A", "B", "C"))
    }

    @Test fun `invalid explicit lessons do not fall back to first period`() {
        assertEquals(emptyList<JwCourse>(), parse("0", "-1", "367", "2147483648", "待定", ""))
    }
}

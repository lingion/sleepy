package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwShsmuDraftOriginTest {
    private fun snapshot() = JwImportDraftSnapshot(
        school=JwSchoolInfo("S","上海交通大学医学院",type="shsmu"),
        courses=listOf(JwCourse("解剖学",day=2,startNode=1,endNode=2,startWeek=6,endWeek=6)),
        periods=emptyList(),termStartDate="2026-09-14",
    )
    @Test fun originalCalendarSurvivesEditedDateAndDraftRoundTrip() {
        val json=JSONObject(JwImportDraftCodec.toJson(snapshot())).put("datedCourseOrigin","2026-09-07")
        val restored=JwImportDraftCodec.fromJson(json.toString())!!
        val saved=JSONObject(JwImportDraftCodec.toJson(restored))
        assertEquals("2026-09-07",saved.optString("datedCourseOrigin"))
        assertEquals("2026-09-14",restored.termStartDate)
        assertEquals(6,restored.courses.single().startWeek)
    }
    @Test fun invalidDatedOriginIsNotSilentlyLoadedAsOrdinaryCourseWeeks() {
        for(value in listOf("2026-02-30","2026-09-08","bad")) {
            val json=JSONObject(JwImportDraftCodec.toJson(snapshot())).put("datedCourseOrigin",value)
            assertNull(JwImportDraftCodec.fromJson(json.toString()))
        }
    }
    @Test fun oldDraftWithoutDatedOriginRemainsReadable() {
        val json=JSONObject(JwImportDraftCodec.toJson(snapshot())).apply { remove("datedCourseOrigin") }
        assertNotNull(JwImportDraftCodec.fromJson(json.toString()))
    }
}

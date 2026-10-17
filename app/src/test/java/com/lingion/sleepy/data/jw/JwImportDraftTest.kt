package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwImportDraftTest {

    private val school = JwSchoolInfo(
        sortKey = "H",
        name = "哈尔滨工程大学",
        url = "https://jw.example.edu.cn",
        type = "qz",
        status = JwSchoolInfo.STATUS_SUPPORTED,
        aliases = listOf("哈工程"),
        sortKeyFull = "haerbingongchengdaxue",
        enableFetch = true,
        catalogId = "school:hrbeu",
    )

    private val snapshot = JwImportDraftSnapshot(
        school = school,
        courses = listOf(
            JwCourse(
                name = "高等数学",
                room = "教一101",
                teacher = "张老师",
                day = 1,
                startNode = 1,
                endNode = 2,
                startWeek = 1,
                endWeek = 16,
                type = 0,
            ),
            JwCourse(
                name = "实验课",
                room = "实验楼",
                teacher = "李老师",
                day = 3,
                startNode = 5,
                endNode = 5,
                startWeek = 2,
                endWeek = 8,
                type = 2,
            ),
        ),
        periods = listOf(
            JwImportDraftPeriod(1, "08:00", "08:45"),
            JwImportDraftPeriod(2, "08:55", "09:40"),
        ),
        termStartDate = "2026-09-07",
        tableName = "教务导入 - 哈工程",
        smartConfigJson = """{"enabled":true,"morningStart":"08:00"}""",
    )

    @Test
    fun `snapshot round trips all import confirmation fields`() {
        val decoded = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))

        assertEquals(snapshot, decoded)
        assertEquals(snapshot.smartConfigJson, decoded?.smartConfigJson)
    }

    @Test fun `login draft restoration preserves supported access but reconfirms unverified entries`() {
        val login = snapshot.copy(phase = JwImportDraftPhase.WEBVIEW_LOGIN)
        fun restored(status: String, url: String = school.url, type: String? = school.type) =
            JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(login.copy(
                school = school.copy(status = status, url = url, type = type),
            )))!!
        org.junit.Assert.assertFalse(restored(JwSchoolInfo.STATUS_SUPPORTED).requiresSchoolConfirmation)
        org.junit.Assert.assertFalse(restored(JwSchoolInfo.STATUS_GRAD_SUPPORTED).requiresSchoolConfirmation)
        assertTrue(restored(JwSchoolInfo.STATUS_UNVERIFIED).requiresSchoolConfirmation)
        assertTrue(restored(JwSchoolInfo.STATUS_GRAD_UNVERIFIED).requiresSchoolConfirmation)
        assertTrue(restored(JwSchoolInfo.STATUS_PENDING).requiresSchoolConfirmation)
        assertTrue(restored("unknown_status").requiresSchoolConfirmation)
        assertTrue(restored(JwSchoolInfo.STATUS_SUPPORTED, url = "").requiresSchoolConfirmation)
        assertTrue(restored(JwSchoolInfo.STATUS_UNVERIFIED, type = "unknown_protocol").requiresSchoolConfirmation)
        // A parsed preview does not reopen a login page or discard already recovered courses.
        org.junit.Assert.assertFalse(snapshot.copy(school = school.copy(
            status = JwSchoolInfo.STATUS_UNVERIFIED)).requiresSchoolConfirmation)
    }

    @Test
    fun `optional school values remain distinguishable when absent`() {
        val minimal = snapshot.copy(
            school = JwSchoolInfo(sortKey = "X", name = "测试学校"),
            termStartDate = "",
            tableName = "",
            periods = emptyList(),
        )

        val decoded = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(minimal))

        assertEquals(minimal, decoded)
        assertEquals(null, decoded?.school?.type)
        assertEquals(emptyList<String>(), decoded?.school?.aliases)
    }

    @Test fun `old drafts without catalog identity remain readable`() {
        val json = org.json.JSONObject(JwImportDraftCodec.toJson(snapshot))
        json.getJSONObject("school").remove("catalogId")
        val restored = JwImportDraftCodec.fromJson(json.toString())!!
        assertEquals("H_哈尔滨工程大学", restored.school.stableKey)
    }

    @Test fun `drafts preserve unverified admission and reject unknown status upgrade`() {
        val candidate = snapshot.copy(school = school.copy(status = JwSchoolInfo.STATUS_UNVERIFIED))
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(candidate))!!
        assertTrue(restored.school.isUnverified)
        val json = org.json.JSONObject(JwImportDraftCodec.toJson(candidate))
        json.getJSONObject("school").put("status", "future_status")
        assertEquals(JwSchoolInfo.STATUS_PENDING, JwImportDraftCodec.fromJson(json.toString())!!.school.status)
        json.getJSONObject("school").put("type", org.json.JSONObject.NULL)
        assertNull(JwImportDraftCodec.fromJson(json.toString())!!.school.type)
    }

    @Test
    fun `codec rejects unsupported version and missing required fields`() {
        assertNull(JwImportDraftCodec.fromJson("""{"schemaVersion":99}"""))
        assertNull(JwImportDraftCodec.fromJson("""{"schemaVersion":1,"school":{"name":""},"courses":[]}"""))
        assertNull(JwImportDraftCodec.fromJson("not-json"))
    }

    @Test
    fun `codec rejects malformed course and period entries`() {
        val malformedCourse = """
            {
              "schemaVersion":1,
              "school":{"sortKey":"X","name":"测试学校"},
              "courses":[{"name":"","day":1,"startNode":1,"endNode":1,"startWeek":1,"endWeek":1}],
              "periods":[]
            }
        """.trimIndent()
        val malformedPeriod = """
            {
              "schemaVersion":1,
              "school":{"sortKey":"X","name":"测试学校"},
              "courses":[{"name":"课","day":1,"startNode":1,"endNode":1,"startWeek":1,"endWeek":1}],
              "periods":[{"node":0,"start":"08:00","end":"08:45"}]
            }
        """.trimIndent()

        assertNull(JwImportDraftCodec.fromJson(malformedCourse))
        assertNull(JwImportDraftCodec.fromJson(malformedPeriod))
    }

    @Test
    fun `webview draft round trips without courses`() {
        val webViewDraft = JwImportDraftSnapshot(
            school = school,
            courses = emptyList(),
            periods = emptyList(),
            phase = JwImportDraftPhase.WEBVIEW_LOGIN,
        )

        val decoded = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(webViewDraft))

        assertEquals(webViewDraft, decoded)
        assertEquals(JwImportDraftPhase.WEBVIEW_LOGIN, decoded?.phase)
    }

    @Test
    fun `json uses explicit schema and preserves course ordering`() {
        val json = JwImportDraftCodec.toJson(snapshot)

        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.indexOf("高等数学") < json.indexOf("实验课"))
        assertTrue(json.contains("\"type\":2"))
    }
}

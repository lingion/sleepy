package com.lingion.sleepy.console

import com.lingion.sleepy.console.handlers.JwMockHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * jw-mock 端点面（JVM）：红线 2 的 403 闸 + put/get/delete 语义 +
 * url_pattern 正则校验。respond_with=file 的路径守卫在 JwMockRuleUnitTest;
 * 真 WebView 回放链在 TriggerEndpoint/JwMock androidTest 与 Task 8 验收。
 */
class JwMockHandlerUnitTest {

    @Before
    fun debugBuild() {
        MockRuleStore.clear()
        DebugGate.activate(debugBuild = true, releaseUnlocked = false)
    }

    @After
    fun leaveDebugBuild() {
        MockRuleStore.clear()
        DebugGate.deactivate()
    }

    @Test
    fun releaseUnlocked_allThreeOps_403() {
        DebugGate.activate(debugBuild = false, releaseUnlocked = true)
        val put = org.json.JSONObject(JwMockHandler.handlePut(null,
            """{"url_pattern":"xkbcx","respond_with":"html","body":"<html/>"}"""))
        val get = org.json.JSONObject(JwMockHandler.handleGet(null))
        val del = org.json.JSONObject(JwMockHandler.handleDelete("""{"all":true}"""))
        listOf(put to "POST", get to "GET", del to "DELETE").forEach { (o, m) ->
            assertTrue("$m 在 release 解锁态必须失败", !o.getBoolean("ok"))
            assertEquals("$m 必须 403", 403, o.getInt("code"))
        }
    }

    @Test
    fun debugBuild_putListDelete_roundTrip() {
        val put = org.json.JSONObject(JwMockHandler.handlePut(null,
            """{"url_pattern":"https://jw\\.x\\.edu/.*","status":200,"content_type":"text/html","respond_with":"html","body":"<html>ok</html>"}"""))
        assertTrue(put.getBoolean("ok"))
        assertEquals(15, put.getJSONObject("data").getInt("bytes"))

        val list = org.json.JSONObject(JwMockHandler.handleGet(null))
        val rules = list.getJSONObject("data").getJSONArray("rules")
        assertEquals(1, rules.length())
        assertEquals("""https://jw\.x\.edu/.*""", rules.getJSONObject(0).getString("url_pattern"))

        val del = org.json.JSONObject(JwMockHandler.handleDelete("""{"url_pattern":"https://jw\\.x\\.edu/.*"}"""))
        assertTrue(del.getBoolean("ok"))
        assertEquals(0, MockRuleStore.list().size)
    }

    @Test
    fun b64AndBadInput_semantics() {
        val b64 = org.json.JSONObject(JwMockHandler.handlePut(null,
            """{"url_pattern":"a","respond_with":"b64","body":"aGk="}"""))
        assertTrue(b64.getBoolean("ok"))
        assertEquals("hi", String(MockRuleStore.list().first().body))

        val badRegex = org.json.JSONObject(JwMockHandler.handlePut(null,
            """{"url_pattern":"([unclosed","respond_with":"html","body":"x"}"""))
        assertEquals(400, badRegex.getInt("code"))

        val badKind = org.json.JSONObject(JwMockHandler.handlePut(null,
            """{"url_pattern":"b","respond_with":"zip","body":"x"}"""))
        assertEquals(400, badKind.getInt("code"))

        val delMissing = org.json.JSONObject(JwMockHandler.handleDelete("""{"url_pattern":"nope"}"""))
        assertEquals(404, delMissing.getInt("code"))
    }
}

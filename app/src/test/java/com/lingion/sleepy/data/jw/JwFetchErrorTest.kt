package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwFetchErrorTest {
    @Test fun `round trip SessionExpired through json`() {
        val err = FetchError(FetchErrorKind.SessionExpired, "未登录或会话已过期", "wisedu", "https://webvpn/hrbeu/jwapp/...")
        val json = JwFetchError.toJson(err)
        val parsed = JwFetchError.fromJson(json)!!
        assertEquals(FetchErrorKind.SessionExpired, parsed.kind)
        assertEquals("未登录或会话已过期", parsed.message)
        assertEquals("wisedu", parsed.schoolType)
        assertTrue("URL 必须保留", parsed.url!!.contains("webvpn"))
    }

    @Test fun `malformed json returns null`() {
        assertNull(JwFetchError.fromJson("not-json"))
        assertNull(JwFetchError.fromJson("""{"kind":"UNKNOWN"}"""))
        assertNull(JwFetchError.fromJson("{}"))
    }

    @Test fun `kind enum covers exactly four categories`() {
        assertEquals(4, listOf(FetchErrorKind.SessionExpired, FetchErrorKind.ParseFailed,
            FetchErrorKind.Network, FetchErrorKind.Empty).size)
    }
}

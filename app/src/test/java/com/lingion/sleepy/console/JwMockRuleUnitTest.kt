package com.lingion.sleepy.console

import com.lingion.sleepy.console.MockRuleStore.MockRule
import com.lingion.sleepy.console.MockRuleStore.matchRuleIn
import com.lingion.sleepy.console.MockRuleStore.resolveBodyFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 6 jw-mock 纯逻辑面（brief Step 1）：正则命中 → 构造响应字段对;
 * 未命中 → null; respond_with=file 拒绝 filesDir 外路径; 空规则恒 null
 * （Review Focus 5: 未注入 mock 时教务真实通路零影响 = 链头拦截点必须
 * 在空表时短路返回 null, 不改变任何既有 WebView 行为）。
 */
class JwMockRuleUnitTest {

    private fun rule(
        pattern: String,
        status: Int = 200,
        contentType: String = "text/html",
        body: String = "<html>mock</html>",
        headers: Map<String, String> = emptyMap(),
    ) = MockRule(
        urlPattern = pattern,
        status = status,
        contentType = contentType,
        headers = headers,
        body = body.toByteArray(),
    )

    @Test
    fun emptyRuleSet_neverMatches() {
        assertNull(matchRuleIn(emptyList(), "https://jw.hrbeu.edu.cn/kbcx/xkbcx"))
        assertNull(matchRuleIn(emptyList(), "https://anything"))
    }

    @Test
    fun regexHit_returnsThatRule() {
        val r = rule("""https://jw\.example\.edu/.*xkbcx.*""")
        val hit = matchRuleIn(listOf(r), "https://jw.example.edu/kbcx/xkbcx/query?fx=1")
        assertNotNull(hit)
        assertEquals("""https://jw\.example\.edu/.*xkbcx.*""", hit!!.urlPattern)
        assertEquals(200, hit.status)
        assertEquals("<html>mock</html>", String(hit.body))
    }

    @Test
    fun miss_returnsNull() {
        val r = rule("xkbcx")
        assertNull(matchRuleIn(listOf(r), "https://jw.example.edu/logout"))
    }

    @Test
    fun firstHitWins_registrationOrderIsPriority() {
        val a = rule("query", body = "A")
        val b = rule("xkbcx/query", body = "B")
        val hit = matchRuleIn(listOf(a, b), "https://h/xkbcx/query")
        assertEquals("A", String(hit!!.body))
    }

    @Test
    fun invalidPattern_isSkippedNotCrash() {
        val bad = rule("([unclosed", body = "BAD")
        val good = rule("xkbcx", body = "GOOD")
        val hit = matchRuleIn(listOf(bad, good), "https://h/xkbcx")
        assertEquals("GOOD", String(hit!!.body))
    }

    @Test
    fun fileBody_rejectsTraversalOutsideFilesDir() {
        val filesDir = File("build/test-mock-filesdir").apply { mkdirs() }
        val ok = resolveBodyFile(filesDir, "mock_kbcx.html")
        assertNotNull(ok)
        assertTrue(ok!!.canonicalPath.startsWith(filesDir.canonicalPath))
        listOf(
            "/etc/passwd",
            "../../../etc/passwd",
            "sub/../../outside.html",
            "",
            " ",
        ).forEach { bad ->
            assertNull("必须拒绝 filesDir 外路径: $bad", resolveBodyFile(filesDir, bad))
        }
    }

    @Test
    fun headerPassthrough_keepsCaseAndMultipleEntries() {
        val r = rule(
            "xkbcx",
            status = 302,
            headers = mapOf("Location" to "https://h/home", "Set-Cookie" to "a=1; Path=/"),
        )
        val hit = matchRuleIn(listOf(r), "https://h/xkbcx")!!
        assertEquals(302, hit.status)
        assertEquals("https://h/home", hit.headers["Location"])
        assertEquals("a=1; Path=/", hit.headers["Set-Cookie"])
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #25 WebView contract test: locks the portal-host guard in EAMS5_FETCH_JS
 * and the cross-language studentId regex invariant.
 * Pattern: JwNeuWebViewContractTest - read source text, lock both-side contract.
 */
class HfutPortalEams5WebViewContractTest {

    private fun loadSourceFile(path: String): String {
        for (p in listOf("src/main/java/$path", "app/src/main/java/$path")) {
            val f = java.io.File(p)
            if (f.isFile) return f.readText(Charsets.UTF_8)
        }
        error("source file $path should exist")
    }

    /** Extract the EAMS5_FETCH_JS string literal (not the AHU variant). */
    private fun extractEams5FetchJs(src: String): String {
        val marker = "private const val EAMS5_FETCH_JS = \"\"\""
        val start = src.indexOf(marker)
        assertTrue("EAMS5_FETCH_JS must exist", start >= 0)
        val bodyStart = start + marker.length
        val bodyEnd = src.indexOf("\"\"\"", bodyStart)
        assertTrue("EAMS5_FETCH_JS must close", bodyEnd > bodyStart)
        return src.substring(bodyStart, bodyEnd)
    }

    private fun webViewSource(): String =
        loadSourceFile("com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")

    /** Extract the JVM EAMS5_STUDENT_ID_REGEX raw string content from Eams5PathPrefix.kt. */
    private fun loadJvmStudentIdRegex(): String {
        val src = loadSourceFile("com/lingion/sleepy/data/jw/Eams5PathPrefix.kt")
        val marker = "EAMS5_STUDENT_ID_REGEX: Regex = Regex(\"\"\""
        val i = src.indexOf(marker)
        assertTrue("EAMS5_STUDENT_ID_REGEX must exist", i >= 0)
        val bodyStart = i + marker.length
        val bodyEnd = src.indexOf("\"\"\"", bodyStart)
        assertTrue("regex raw string must close", bodyEnd > bodyStart)
        return src.substring(bodyStart, bodyEnd)
    }

    // -------- Test 1: portal host rejection (Fix C target) --------

    @Test
    fun `EAMS5_FETCH_JS rejects portal host one dot hfut`() {
        val js = extractEams5FetchJs(webViewSource())
        assertTrue(
            "EAMS5_FETCH_JS must explicitly reject portal host one.hfut.edu.cn",
            js.contains("one.hfut.edu.cn")
        )
    }

    @Test
    fun `EAMS5_FETCH_JS portal rejection message mentions jxglstu`() {
        val js = extractEams5FetchJs(webViewSource())
        assertTrue(
            "portal rejection message must guide user to the jxglstu course-table entry",
            js.contains("jxglstu")
        )
    }

    // -------- Test 2: existing host guards must not regress --------

    @Test
    fun `EAMS5_FETCH_JS keeps existing host guards`() {
        val js = extractEams5FetchJs(webViewSource())
        for (token in listOf("hfut.edu.cn", "jxglstu", "ahu.edu.cn", "cumtb.edu.cn")) {
            assertTrue("EAMS5_FETCH_JS must keep host guard $token", js.contains(token))
        }
    }

    @Test
    fun `EAMS5_FETCH_JS keeps specific login failure messages`() {
        val js = extractEams5FetchJs(webViewSource())
        assertTrue(
            "session-expired message must be preserved",
            js.contains("login") || js.contains("Login")
        )
    }

    // -------- Test 3: cross-language regex invariant --------

    @Test
    fun `JVM and WebView studentId regex literals are character identical`() {
        val jvm = loadJvmStudentIdRegex()
        val js = extractEams5FetchJs(webViewSource())
        val jsMarker = "html.match(/"
        val jsStart = js.indexOf(jsMarker)
        assertTrue("JS regex literal must exist", jsStart >= 0)
        val bodyStart = jsStart + jsMarker.length
        val jsEnd = js.indexOf("/", bodyStart)
        assertTrue("JS regex must close", jsEnd > bodyStart)
        val jsLiteral = js.substring(bodyStart, jsEnd)
        assertEquals(
            "JVM and JS studentId regex must be character-identical (cross-language invariant)",
            jvm, jsLiteral
        )
    }
}

package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SepXrwStripInterceptor 纯函数单测 — UCAS #18 SEP SSO 域 XRW 剥离代理。
 * sep.ucas.ac.cn filter 对带 X-Requested-With 的请求返 401 JSON, WebView 每请求
 * 强制带 <包名> 作该头 → App 内 SEP 登录链断。代理仅拦 SEP 域 GET。
 */
class SepXrwStripInterceptorTest {

    // ---------- shouldProxy: 拦截门 ----------

    @Test
    fun shouldProxy_sepGet_true() {
        assertTrue(SepXrwStripInterceptor.shouldProxy("sep.ucas.ac.cn", "GET"))
    }

    @Test
    fun shouldProxy_caseInsensitiveHostAndMethod_true() {
        assertTrue(SepXrwStripInterceptor.shouldProxy("SEP.UCAS.AC.CN", "get"))
    }

    @Test
    fun shouldProxy_postPassthrough_false() {
        // POST /slogin 登录端点实测与 XRW 无关 (空凭据带/不带该头同为 403) → 放行原生
        assertFalse(SepXrwStripInterceptor.shouldProxy("sep.ucas.ac.cn", "POST"))
    }

    @Test
    fun shouldProxy_otherHosts_false() {
        // xkgo.ucas.ac.cn:3000 实测不检查 XRW (带该头仍 200 HTML) → 不代理
        assertFalse(SepXrwStripInterceptor.shouldProxy("xkgo.ucas.ac.cn", "GET"))
        assertFalse(SepXrwStripInterceptor.shouldProxy("jwxt.example.edu", "GET"))
    }

    @Test
    fun shouldProxy_nullHost_false() {
        assertFalse(SepXrwStripInterceptor.shouldProxy(null, "GET"))
        assertFalse(SepXrwStripInterceptor.shouldProxy("sep.ucas.ac.cn", null))
    }

    // ---------- contentTypeParts ----------

    @Test
    fun contentType_html_withCharset() {
        val (mime, enc) = SepXrwStripInterceptor.contentTypeParts("text/html;charset=UTF-8")
        assertEquals("text/html", mime)
        assertEquals("utf-8", enc)
    }

    @Test
    fun contentType_json_withCharset() {
        val (mime, enc) = SepXrwStripInterceptor.contentTypeParts("application/json;charset=UTF-8")
        assertEquals("application/json", mime)
        assertEquals("utf-8", enc)
    }

    @Test
    fun contentType_nullOrBlank_fallsBackToHtml() {
        assertEquals("text/html", SepXrwStripInterceptor.contentTypeParts(null).first)
        assertEquals("text/html", SepXrwStripInterceptor.contentTypeParts("").first)
    }

    @Test
    fun contentType_noCharset_encodingNull() {
        val (mime, enc) = SepXrwStripInterceptor.contentTypeParts("text/html")
        assertEquals("text/html", mime)
        assertEquals(null, enc)
    }

    @Test
    fun contentType_quotedCharset_strippedAndLowercased() {
        val (mime, enc) = SepXrwStripInterceptor.contentTypeParts("text/html;charset=\"utf-8\"")
        assertEquals("text/html", mime)
        assertEquals("utf-8", enc)
    }

    @Test
    fun contentType_multipleParams_charsetExtracted() {
        val (mime, enc) = SepXrwStripInterceptor.contentTypeParts("text/html; foo=bar; charset=UTF-8")
        assertEquals("text/html", mime)
        assertEquals("utf-8", enc)
    }

    // ---------- resolveRedirect ----------

    @Test
    fun resolveRedirect_absoluteLocation() {
        val got = SepXrwStripInterceptor.resolveRedirect(
            "https://sep.ucas.ac.cn/appStore",
            "https://sep.ucas.ac.cn/?loginFrom=/appStore"
        )
        assertEquals("https://sep.ucas.ac.cn/?loginFrom=/appStore", got)
    }

    @Test
    fun resolveRedirect_protocolRelativeLocation() {
        val got = SepXrwStripInterceptor.resolveRedirect(
            "https://sep.ucas.ac.cn/appStore",
            "//sep.ucas.ac.cn/?loginFrom=/appStore"
        )
        assertEquals("https://sep.ucas.ac.cn/?loginFrom=/appStore", got)
    }

    @Test
    fun resolveRedirect_absolutePathLocation() {
        val got = SepXrwStripInterceptor.resolveRedirect(
            "http://sep.ucas.ac.cn/appStore",
            "/include/jump?to=x"
        )
        assertEquals("http://sep.ucas.ac.cn/include/jump?to=x", got)
    }

    @Test
    fun resolveRedirect_relativePathLocation() {
        val got = SepXrwStripInterceptor.resolveRedirect(
            "http://sep.ucas.ac.cn/a/b",
            "c/d"
        )
        assertEquals("http://sep.ucas.ac.cn/a/c/d", got)
    }

    @Test
    fun resolveRedirect_blankLocation_staysOnCurrentUrl() {
        val got = SepXrwStripInterceptor.resolveRedirect("https://sep.ucas.ac.cn/appStore", "  ")
        assertEquals("https://sep.ucas.ac.cn/appStore", got)
    }
}

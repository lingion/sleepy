package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SEP XRW 剥离代理接线契约 (UCAS #18) — 源码扫描锁不变量。
 *
 * 该代理是 SSO 可用性的唯一保障: WebView 对每个请求强制加 X-Requested-With:
 * <包名>, SEP filter 见头即 401 JSON。若未来重构删掉接线或剥离逻辑, 该组用例
 * 必须先红。
 *
 * 跨文件契约: JwWebViewLoginScreen 的 WebViewClient 必须挂 shouldInterceptRequest
 * 并委托 SepXrwStripInterceptor.intercept; 代理本体必须: 仅 SEP 域 GET、跳过
 * x-requested-with/accept-encoding 请求头、手动逐跳重定向 + Set-Cookie 同步、
 * POST 放行。
 */
class SepXrwStripInterceptorContractTest {

    private val screen: String = sequenceOf(
        java.io.File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwWebViewLoginScreen.kt source")

    private val interceptor: String = sequenceOf(
        java.io.File("src/main/java/com/lingion/sleepy/ui/screen/imports/SepXrwStripInterceptor.kt"),
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/SepXrwStripInterceptor.kt"),
        java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/SepXrwStripInterceptor.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load SepXrwStripInterceptor.kt source")

    // ---------- 接线: WebViewClient 挂载 + 委托 ----------

    @Test
    fun webviewClient_mustHook_shouldInterceptRequest_delegatingToInterceptor() {
        assertTrue(
            "WebViewClient must override shouldInterceptRequest and delegate to the SEP proxy",
            Regex("""override\s+fun\s+shouldInterceptRequest""").containsMatchIn(screen)
        )
        assertTrue(
            "shouldInterceptRequest must delegate to SepXrwStripInterceptor.intercept",
            screen.contains("SepXrwStripInterceptor.intercept(")
        )
    }

    @Test
    fun sslBypass_mustStayInPlace() {
        // XRW 修复不得回退既有 SSL 白名单防护 (中间人防护, 曾出过事故)
        assertTrue(
            "SslBypassRegistry guard must survive",
            screen.contains("SslBypassRegistry.isAllowed")
        )
    }

    // ---------- 代理不变量 ----------

    @Test
    fun interceptor_gateOnlySepHost() {
        assertTrue(
            "Proxy must be scoped to sep.ucas.ac.cn only",
            interceptor.contains("sep.ucas.ac.cn") &&
                Regex("""host\?\.equals\(SEP_HOST""").containsMatchIn(interceptor)
        )
    }

    @Test
    fun interceptor_stripsXrwAndAcceptEncoding() {
        // x-requested-with = 根因头; accept-encoding 手动复制会禁用透明 gzip 解压
        assertTrue(
            "SKIP set must contain the root-cause header",
            Regex("""x-requested-with""").containsMatchIn(interceptor)
        )
        assertTrue(
            "SKIP set must contain accept-encoding (transparent gzip)",
            Regex("""accept-encoding""").containsMatchIn(interceptor)
        )
    }

    @Test
    fun interceptor_manualRedirectsWithCookieSync() {
        assertTrue(
            "Redirects must be followed manually (instanceFollowRedirects = false)",
            interceptor.contains("instanceFollowRedirects = false")
        )
        assertTrue(
            "Every hop must sync Set-Cookie into the WebView cookie jar",
            interceptor.contains("setCookie(")
        )
        assertTrue(
            "Hops must be capped",
            interceptor.contains("MAX_HOPS")
        )
    }

    @Test
    fun interceptor_leaveHostRedirectForTicketJumps() {
        // 重定向出域 (→ xkgo?ticket=) 回 302 交 WebView 原生跟随
        assertTrue(
            "Leaving-host redirect must return a synthetic 302",
            interceptor.contains("leaveHostRedirect")
        )
    }

    @Test
    fun interceptor_postPassthroughLockedByUnitTests() {
        // POST 放行断言在 SepXrwStripInterceptorTest (行为锁); 这里锁入口签名:
        // intercept 只在 shouldProxy(host, method) 门后调用 execute
        assertTrue(
            "intercept must gate on shouldProxy before execute",
            Regex("""if\s*\(!shouldProxy\(request\.url\.host, request\.method\)\)\s*return null""")
                .containsMatchIn(interceptor)
        )
    }
}
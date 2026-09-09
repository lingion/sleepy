package com.lingion.sleepy.ui.screen.imports

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * UCAS (#18) SEP SSO 域 (sep.ucas.ac.cn) 的 X-Requested-With 剥离拦截器。
 *
 * #18 根因 (2026-09-09 服务端实测实锤):
 *   - sep.ucas.ac.cn 的鉴权 filter 对**任何带 X-Requested-With 头**的请求返回
 *     401 JSON `{"code":401,"msg":"未登录或会话已过期","toUrl":"/"}`, 而无此头时
 *     返回 302 跳登录页。Android WebView 对**每个**请求 (含顶层导航) 都强制加
 *     `X-Requested-With: <包名>` (chromium 硬编码, 公开 API 无法移除), 结果
 *     App 内点「请重新登录」直接渲染这串 JSON 正文, 桌面浏览器因无此头得到
 *     302 → 正常登录页。
 *
 * 修复: 拦截 sep.ucas.ac.cn 的 GET (POST /slogin 登录端点经实测与 XRW 无关 —
 * 空凭据 POST 带/不带该头同为 403, 放行原生), 用 HttpURLConnection 手动重发并
 * **不带**该头; 302 逐跳跟随, 每跳 Set-Cookie 同步回 WebView CookieManager;
 * 重定向出域 (→ xkgo?ticket=) 时回 302 交 WebView 原生跟随 (xkgo 不检查该头)。
 *
 * 实现 [JwRequestInterceptor] 接口 — userAgent / schoolHost 通过 [JwInterceptorContext]
 * 在主线程工厂期一次性捕获注入, 本类在 background thread 调用时不再触碰 WebView
 * 实例 (该约束是该接口的核心, 见 `JwWebViewCallbackThreadAffinenessTest`)。
 *
 * 纯函数部分 (shouldProxy / contentTypeParts / resolveRedirect) JVM 单测锁契约;
 * 网络执行体由 [SepXrwStripInterceptorContractTest] 源码扫描锁不变量 (class 名沿用
 * 历史名以保留契约不变量)。
 */
class SepXrwRequestInterceptor : JwRequestInterceptor {

    override fun handles(request: WebResourceRequest): Boolean =
        shouldProxy(request.url.host, request.method)

    override fun handle(
        request: WebResourceRequest,
        ctx: JwInterceptorContext
    ): WebResourceResponse? = try {
        execute(request.url.toString(), request.requestHeaders, ctx.userAgent, ctx.cookieManager)
    } catch (e: Exception) {
        null
    }

    /**
     * 网络执行体: SEP 域内逐跳跟随重定向 (每跳同步 Set-Cookie), 出域回 302。
     * headers = WebView 原始请求头 (SKIP_REQUEST_HEADERS 里的键跳过)。
     * cookieManager = 主线程工厂期从 [JwInterceptorContext.cookieManager] 注入,
     * 避免 callback 内调用 [android.webkit.CookieManager.getInstance] 引发的额外开销。
     */
    private fun execute(
        url0: String,
        headers: Map<String, String>,
        userAgent: String?,
        cookieManager: android.webkit.CookieManager,
    ): WebResourceResponse {
        var url = url0
        repeat(MAX_HOPS) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                headers.forEach { (k, v) ->
                    if (k.lowercase() !in SKIP_REQUEST_HEADERS) {
                        runCatching { setRequestProperty(k, v) }
                    }
                }
                if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    userAgent?.takeIf { it.isNotBlank() }?.let { setRequestProperty("User-Agent", it) }
                }
                cookieManager.getCookie(url)?.takeIf { it.isNotEmpty() }?.let {
                    setRequestProperty("Cookie", it)
                }
            }
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                runCatching { conn.disconnect() }
                throw e
            }
            syncSetCookies(cookieManager, url, conn)
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                if (location != null) {
                    val target = resolveRedirect(url, location)
                    conn.disconnect()
                    if (URL(target).host.equals(SEP_HOST, ignoreCase = true)) {
                        url = target
                        return@repeat
                    }
                    // 出域 (→ xkgo?ticket=…): 302 交 WebView 做原生跟随 (xkgo 不查 XRW)
                    return leaveHostRedirect(target)
                }
                conn.disconnect()
                return serve(conn, code)
            }
            return serve(conn, code)
        }
        throw IOException("SEP redirect hops exceeded $MAX_HOPS")
    }

    /** 每跳 Set-Cookie 同步进 WebView cookie jar (intercepted 响应的 Set-Cookie WebView 不自理) */
    private fun syncSetCookies(cm: android.webkit.CookieManager, url: String, conn: HttpURLConnection) {
        for ((name, values) in conn.headerFields) {
            if (name != null && name.equals("Set-Cookie", ignoreCase = true)) {
                for (v in values) {
                    runCatching { cm.setCookie(url, v) }
                }
            }
        }
    }

    /** 200/4xx/5xx 落地响应: 直接流回 WebView (4xx 用 errorStream) */
    private fun serve(conn: HttpURLConnection, code: Int): WebResourceResponse {
        val (mime, encoding) = contentTypeParts(conn.contentType)
        val stream: InputStream = if (code >= 400) {
            conn.errorStream ?: ByteArrayInputStream(ByteArray(0))
        } else {
            conn.inputStream
        }
        return WebResourceResponse(mime, encoding, code, reasonPhrase(code), null, stream)
    }

    /** 出域 302: data 给空流 (构造器不接受 null) */
    private fun leaveHostRedirect(target: String): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", 302, "Found",
            mapOf("Location" to target),
            ByteArrayInputStream(ByteArray(0))
        )

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        301 -> "Moved Permanently"
        302 -> "Found"
        303 -> "See Other"
        307 -> "Temporary Redirect"
        308 -> "Permalink Redirect"
        else -> "Status"
    }

    companion object {
        /** SEP SSO host — 实测仅此域的 filter 按 XRW 分流 (xkgo.ucas.ac.cn:3000 不检查) */
        const val SEP_HOST = "sep.ucas.ac.cn"

        /** 重定向逐跳上限: appStore→login 实测两跳 + 裕量 */
        const val MAX_HOPS = 5

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000

        /** 请求头里跳过的键 (忽略大小写): 根因头、会禁用透明 gzip 的头、由代理自管的头 */
        internal val SKIP_REQUEST_HEADERS = setOf(
            "x-requested-with",
            "accept-encoding",
            "cookie",
            "host",
            "content-length",
            "connection",
        )

        /** 拦截门: 仅 sep.ucas.ac.cn 的 GET (POST /slogin 实测与 XRW 无关, 放行) */
        fun shouldProxy(host: String?, method: String?): Boolean =
            host?.equals(SEP_HOST, ignoreCase = true) == true && method.equals("GET", ignoreCase = true)

        /**
         * Content-Type → (mimeType, encoding)。charset 大小写/空格形态多样
         * ("charset=UTF-8" / "charset=utf8" / 无 charset)。
         */
        internal fun contentTypeParts(ct: String?): Pair<String, String?> {
            if (ct.isNullOrBlank()) return "text/html" to null
            val mime = ct.substringBefore(';').trim().ifBlank { "text/html" }
            val charset = ct.substringAfter(';', "")
                .substringAfter("charset=", "")
                .substringBefore(';').trim().trim('"').trim()
            return mime to charset.takeIf { it.isNotEmpty() }?.lowercase()
        }

        /**
         * Location 解析: 绝对 URL / 协议相对 (//host/…) / 绝对路径 (/…) / 相对路径,
         * 解析基 = 当前跳 URL。
         */
        internal fun resolveRedirect(currentUrl: String, location: String): String {
            val loc = location.trim()
            if (loc.isEmpty()) return currentUrl
            return try {
                URL(URL(currentUrl), loc).toString()
            } catch (e: Exception) {
                currentUrl
            }
        }
    }
}
package com.lingion.sleepy.ui.screen.imports

import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.lingion.sleepy.data.jw.JwSchoolInfo

/**
 * WebViewClient 装配器。
 *
 * ## 根因 (HEBZYHJ 闪退, 2026-09-09)
 * 旧实现 inline 在 [JwWebViewLoginScreen] factory 闭包内 new 出来的 client,
 * callback 体内触碰 `view.settings.userAgentString` → `WebView.checkThread()` →
 * RuntimeException → 进程终止。
 *
 * ## 修复 (架构边界)
 * 1. 在工厂期 (主线程) 一次性读出 `userAgent` 和 `schoolHost` → 缓存进 [JwInterceptorContext]。
 * 2. callback 体内只读 ctx 注入的字符串 / 单例, 不再触碰任何 `view.*` 方法。
 * 3. interceptor 按 school.url 装配, 默认 client 不感知协议特例, 新协议加新 interceptor class +
 *    在 [assembleInterceptors] 加一行即可。
 * 4. [JwWebViewCallbackThreadAffinenessTest] 锁死架构边界, 任何重构把 view.settings /
 *    view.url 重新塞回 callback 都被拦截。
 *
 * ## 生命周期
 * 装配完成后, [JwWebViewClientImpl] 不再持有 webView 引用 (除 view 参数在 callback
 * 调用时的临时传递, 不存字段)。userAgent / schoolHost / cookieManager 都是 immutable /
 * singleton, GC 安全。
 */
object JwWebViewClientBuilder {

    /**
     * 装配一个 WebViewClient, 配套给已创建并 settings 已配置的 WebView。
     *
     * @param webView 已创建好的 WebView (主线程传入)。仅用于一次性读 userAgent,
     *                装配完成后不持有。
     * @param school 学校信息 — 决定 interceptor 装配 + 提取 schoolHost。
     * @param desktopMode 桌面 UA 模式 (issue #18 PCUA)。true 时 SEP 域页面在
     *                    onPageFinished 后注入 viewport 覆盖 JS (钉布局宽 1024px,
     *                    触发 Bootstrap 桌面分支 — UA 字符串本身对布局零影响)。
     * @param onPageFinished onPageFinished 回调。
     */
    fun build(
        webView: WebView,
        school: JwSchoolInfo,
        desktopMode: Boolean = false,
        onPageFinished: (String?) -> Unit = {},
    ): WebViewClient {
        // 主线程读: settings.userAgentString 是 main-thread-only API, 只能在工厂期一次捕获。
        val userAgent = webView.settings.userAgentString
        val schoolHost = school.url.toUri().host.orEmpty()
        val ctx = JwInterceptorContext(
            userAgent = userAgent,
            cookieManager = CookieManager.getInstance(),
            schoolHost = schoolHost,
        )
        val interceptors = assembleInterceptors(school)
        val desktopViewport = desktopMode && school.url.contains(UCAS_DOMAIN, ignoreCase = true)
        return JwWebViewClientImpl(interceptors, ctx, schoolHost, desktopViewport, onPageFinished)
    }

    /**
     * 按 school 协议族装配拦截器链。当前只挂 SEP XRW 剥离 (UCAS #18) —
     * 新协议 (例如其他 SPA 教务的 XRW 类问题) 在这里加一行即可, 不污染 [JwWebViewClientImpl]
     * 通用层。
     */
    private fun assembleInterceptors(school: JwSchoolInfo): List<JwRequestInterceptor> {
        val list = mutableListOf<JwRequestInterceptor>()
        // UCAS #18: SEP SSO 域 (sep.ucas.ac.cn) 剥离 X-Requested-With, 否则 filter 返 401 JSON
        if (school.url.contains(UCAS_DOMAIN, ignoreCase = true)) {
            list += SepXrwRequestInterceptor()
        }
        // 后续: HEBZYHJ qz_app / CQU myportal / WHUT / QZ_APP 等 SPA 类教务
        // 如发现新的 XRW 类问题, 在这里加一行 if (school.url.contains(DOMAIN)) { list += NewInterceptor() }
        return list.toList()
    }

    private const val UCAS_DOMAIN = "ucas.ac.cn"
}

/**
 * 装配出的 WebViewClient 实现。callback 体内**禁止**触碰 view.settings / view.url
 * 等 main-thread-only API — 见类级不变量 + [JwWebViewCallbackThreadAffinenessTest]。
 */
private class JwWebViewClientImpl(
    private val interceptors: List<JwRequestInterceptor>,
    private val ctx: JwInterceptorContext,
    private val schoolHost: String,
    private val desktopViewport: Boolean,
    private val onPageFinished: (String?) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        // 遍历 interceptor 链: 第一个 handles() 匹配的拦截, 其余跳过。
        // 不触碰 view.* — view 参数保留只为接口签名, 实际不调用任何 view 方法。
        for (interceptor in interceptors) {
            if (interceptor.handles(request)) {
                return interceptor.handle(request, ctx)
            }
        }
        return null
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        // 用 error.url (SslError 实例字段, String) 而非 view.url (main-thread-only)。
        // schoolHost 已捕获 — 工厂期一次性解析, callback 内不再碰 view.url。
        val host = error.url?.toUri()?.host.orEmpty()
        val allowed = SslBypassRegistry.isAllowed(host, schoolHost)
        if (allowed) handler.proceed() else handler.cancel()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageFinished(url)
        // issue #18 PCUA 修复: 桌面模式 + SEP 域 → 页面加载完后注入 viewport 覆盖,
        // 把 layout viewport 钉到 1024px 触发 @media (min-width: 980px) 桌面分支
        // (侧栏展开)。UA 字符串对 Bootstrap 布局零影响, viewport 是唯一杠杆。
        // onPageFinished 主线程回调, evaluateJavascript 主线程约束满足。
        // 双注入 (立即 + 300ms 重注入): chromium 对 meta 变更的重排时机不定
        // (SO 19953717 模式 — 重复注入), 第二次兜底 SPA 迟挂的 meta 重写。
        if (desktopViewport && url != null &&
            url.toUri().host.orEmpty().equals("sep.ucas.ac.cn", ignoreCase = true)
        ) {
            view?.evaluateJavascript(DESKTOP_VIEWPORT_JS, null)
            view?.postDelayed({
                view.evaluateJavascript(DESKTOP_VIEWPORT_JS, null)
            }, 300)
        }
    }
}
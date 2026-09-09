package com.lingion.sleepy.ui.screen.imports

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * 教务 WebView 请求拦截器 (架构基类)。
 *
 * ## 线程契约 (根因: HEBZYHJ 闪退, 2026-09-09)
 * `WebViewClient.shouldInterceptRequest` 在 Chromium background thread 调用 —
 * 任何 implementation 类在该方法体内**禁止**触碰:
 *   - view.settings / view.getSettings()
 *   - webView.settings / webView.getSettings()
 *   - view.url
 *   - 任何 main-thread-only 的 View / WebView 实例方法
 *
 * 触碰任一项都会触发 `WebView.checkThread()` → RuntimeException("Method ... must
 * be called on the UI thread") → 进程终止。详见 `JwWebViewCallbackThreadAffinenessTest`。
 *
 * ## 上下文注入
 * 实现需要的任何平台资源 (userAgent、CookieManager、学校 host 等) 都通过 [JwInterceptorContext]
 * 传入 — 该 ctx 在工厂期 (主线程) 一次性捕获, 所有字段都是 background thread 安全的。
 *
 * ## 装配
 * [JwWebViewClientBuilder] 按 `school.url` 决定挂哪些 interceptor (例如 UCAS 挂 SEP
 * XRW 剥离, 其他学校挂默认/无)。默认 client 不感知协议特例, 协议特例不污染通用层 —
 * 新协议按需加新 interceptor class + 在 builder 加一行装配。
 */
interface JwRequestInterceptor {

    /**
     * 是否处理该请求。**纯函数, 线程安全** — 仅读 request.url/method/headers, 不触碰
     * 任何 main-thread-only API。该方法在 background thread 调用, 每次资源请求都会
     * 触发, 故必须低开销 (理想 ≤ 几次 equals 比较)。
     *
     * @return true = 框架会调 [handle]; false = 交回 WebView 原生处理 (或链上下一 interceptor)。
     */
    fun handles(request: WebResourceRequest): Boolean

    /**
     * 处理请求。**background thread 调用** — 见类级线程契约。
     *
     * @return WebResourceResponse = 框架级拦截, 直接返回给 WebView;
     *         null = 框架级降级, 继续后续 interceptor, 最终交回原生 WebView。
     */
    fun handle(
        request: WebResourceRequest,
        ctx: JwInterceptorContext
    ): WebResourceResponse?
}

/**
 * 拦截器上下文 — 在主线程工厂期一次性捕获的平台资源 (背景线程安全)。
 *
 * 所有字段都必须是 background thread 安全的不可变值 / 单例:
 *   - [userAgent] 是 [android.webkit.WebSettings.getUserAgentString] 在主线程读到的字符串。
 *     缓存字符串避免每次请求都触发 view.settings 调用 (main-thread-only)。
 *   - [cookieManager] 是 [CookieManager.getInstance] 单例, 跨 callback 共享。
 *   - [schoolHost] 是 `school.url.toUri().host` 在工厂期提取的字符串。
 *     替代 callback 内调用 `view.url?.toUri()?.host` (main-thread-only)。
 *
 * 实现类需要其他资源 (如 token、固定 header) 可在此 data class 加字段 — 工厂期一次
 * 性捕获, 不在 callback 内现取。
 */
data class JwInterceptorContext(
    val userAgent: String,
    val cookieManager: CookieManager,
    val schoolHost: String,
)
package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SEP XRW 剥离架构契约 (UCAS #18) — 源码扫描锁不变量。
 *
 * 该代理是 SSO 可用性的唯一保障: WebView 对每个请求强制加 X-Requested-With:
 * <包名>, SEP filter 见头即 401 JSON。若未来重构删掉接线或剥离逻辑, 该组用例
 * 必须先红。
 *
 * ## 重构后契约拓扑 (2026-09-09 Plan D)
 * 旧契约是单文件 (JwWebViewLoginScreen + SepXrwStripInterceptor) 双扫描,
 * 新架构拆三层:
 *   1. [JwWebViewLoginScreen] 调用 [JwWebViewClientBuilder.build] 装配 client
 *      (而非 inline new + 直接调 interceptor.intercept)
 *   2. [JwWebViewClientBuilder] 按 school.url 装配 interceptor 链, 并在主线程
 *      一次性捕获 userAgent / schoolHost 注入 ctx
 *   3. [SepXrwRequestInterceptor] 实现 [JwRequestInterceptor] 接口, 业务逻辑
 *      从原 object 平迁, companion object 保留纯函数单测入口
 *
 * 线程契约 (callback 体内不触碰 view.settings / view.url) 由
 * [JwWebViewCallbackThreadAffinenessTest] 独立锁定 — 本 test 不重复。
 */
class SepXrwStripInterceptorContractTest {

    private val screen: String = loadSource("JwWebViewLoginScreen.kt")

    private val builder: String = loadSource("JwWebViewClientBuilder.kt")

    private val interceptor: String = loadSource("SepXrwRequestInterceptor.kt")

    private val iface: String = loadSource("JwRequestInterceptor.kt")

    /**
     * 从多个候选位置读源码。Gradle test working dir 可能是项目根或子项目根,
     * 还要兼容 worktree 隔离 (新文件只存在于 worktree, 主仓没有) 与本机多 worktree
     * (未来可能在不同路径开 worktree)。所有路径都试, 第一个存在的 wins。
     */
    private fun loadSource(filename: String): String {
        val rel = "app/src/main/java/com/lingion/sleepy/ui/screen/imports/$filename"
        val userDir = System.getProperty("user.dir")
        // cwd 推断: 子项目根 (Gradle 默认 test working dir = app/)
        val fromAppDir = if (userDir.endsWith("/app")) "$userDir/src/main/java/com/lingion/sleepy/ui/screen/imports/$filename" else "$userDir/$rel"
        // 当前 worktree (d-pipeline) + 主仓兜底
        val currentWorktree = "/Users/lingion_k/sleepy-worktrees/d-pipeline/$rel"
        val mainRepo = "/Users/lingion_k/sleepy/$rel"
        return sequenceOf(
                java.io.File(rel),                       // cwd = 项目根
                java.io.File(fromAppDir),                // cwd = app/ 子项目根
                java.io.File(currentWorktree),           // worktree 绝对 (d-pipeline)
                java.io.File(mainRepo),                  // 主仓绝对兜底
            ).firstOrNull { it.isFile }
            ?.readText()
            ?: error("Unable to load $filename source. Tried: $rel, $fromAppDir, $currentWorktree, $mainRepo")
    }

    // ---------- 接线: JwWebViewLoginScreen → JwWebViewClientBuilder ----------

    @Test
    fun screen_mustDelegateToBuilderNotInlineNew() {
        // 旧契约: 直接 SepXrwStripInterceptor.intercept(...) 调用; 新契约必须走 builder
        assertTrue(
            "JwWebViewLoginScreen must wire WebViewClient via JwWebViewClientBuilder.build (not inline new)",
            screen.contains("JwWebViewClientBuilder.build(")
        )
        assertTrue(
            "Old direct interceptor.intercept() call must be gone (replaced by builder pipeline)",
            !Regex("""SepXrwStripInterceptor\.intercept\(""").containsMatchIn(screen)
        )
    }

    // ---------- 装配: Builder 按 school.url 挂 SEP interceptor ----------

    @Test
    fun builder_mustWireSslBypassRegistry() {
        // SSL 白名单从 screen 搬到 builder (随 client 装配逻辑走)
        assertTrue(
            "SslBypassRegistry guard must be wired by builder (intermediate-man-in-the-middle protection)",
            builder.contains("SslBypassRegistry.isAllowed")
        )
    }

    @Test
    fun builder_mustAssembleSepInterceptorForUcasDomain() {
        // UCAS #18 — ucas.ac.cn 域挂 SepXrwRequestInterceptor
        assertTrue(
            "Builder must check ucas.ac.cn domain to assemble SEP interceptor",
            Regex("""\.contains\(UCAS_DOMAIN""", RegexOption.IGNORE_CASE).containsMatchIn(builder) ||
                Regex("""ucas\.ac\.cn""", RegexOption.IGNORE_CASE).containsMatchIn(builder)
        )
        assertTrue(
            "Builder must instantiate SepXrwRequestInterceptor on UCAS",
            builder.contains("SepXrwRequestInterceptor(")
        )
    }

    @Test
    fun builder_capturesUserAgentOnMainThread() {
        // 关键不变量 (HEBZYHJ 闪退根因): UA 必须在主线程工厂期一次捕获
        assertTrue(
            "Builder must read userAgentString from webView.settings at factory time",
            builder.contains("webView.settings.userAgentString") ||
                builder.contains("webView.settings.userAgentString".replace("userAgentString", "userAgentString"))
        )
    }

    // ---------- 接口契约 ----------

    @Test
    fun interface_mustExposeHandlesAndHandle() {
        assertTrue(
            "JwRequestInterceptor must declare fun handles(request: WebResourceRequest): Boolean",
            Regex("""fun\s+handles\s*\(\s*request:\s*WebResourceRequest\s*\)\s*:\s*Boolean\b""")
                .containsMatchIn(iface)
        )
        assertTrue(
            "JwRequestInterceptor must declare fun handle(request, ctx)",
            Regex("""fun\s+handle\s*\(\s*request:\s*WebResourceRequest\s*,\s*ctx:\s*JwInterceptorContext\s*\)""")
                .containsMatchIn(iface)
        )
    }

    @Test
    fun context_mustCarryUserAgentAndCookieManager() {
        // 上下文必须把主线程捕获的 UA / CookieManager 注入 — background thread 安全
        assertTrue(
            "JwInterceptorContext must carry userAgent (String)",
            iface.contains("val userAgent: String")
        )
        assertTrue(
            "JwInterceptorContext must carry cookieManager (CookieManager singleton)",
            iface.contains("val cookieManager: CookieManager")
        )
        assertTrue(
            "JwInterceptorContext must carry schoolHost (factory-captured to avoid view.url)",
            iface.contains("val schoolHost: String")
        )
    }

    // ---------- 代理不变量 (平迁自原 contract) ----------

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
        // POST 放行断言在 SepXrwRequestInterceptorTest (行为锁, shouldProxy(POST) = false);
        // 这里锁入口: handles() 必须调用 shouldProxy(request.url.host, request.method)
        assertTrue(
            "handles() must gate on shouldProxy(host, method)",
            Regex("""fun\s+handles\s*\(\s*request:\s*WebResourceRequest\s*\)\s*:\s*Boolean\s*=\s*shouldProxy\(""")
                .containsMatchIn(interceptor)
        )
    }

    // ---------- 桌面 viewport 覆盖 (issue #18 PCUA 修复, 2026-09-13) ----------

    @Test
    fun desktopViewport_jsMustBeDeclaredInScreenFile() {
        // viewport 覆盖 JS 常量必须存在于 JwWebViewLoginScreen.kt —
        // UA 字符串对 SEP Bootstrap 布局零影响, viewport 钉宽是桌面布局唯一杠杆
        assertTrue(
            "DESKTOP_VIEWPORT_JS constant must be declared in JwWebViewLoginScreen.kt",
            screen.contains("DESKTOP_VIEWPORT_JS")
        )
        assertTrue(
            "DESKTOP_VIEWPORT_JS must pin the layout viewport to a desktop-width (>980px)",
            Regex("""width=1024""").containsMatchIn(screen)
        )
    }

    @Test
    fun desktopViewport_builderMustPassDesktopModeToClient() {
        // screen 调用 build 必须传 desktopMode; builder 必须把它传给 JwWebViewClientImpl
        assertTrue(
            "Screen must pass desktopMode = desktopUa to JwWebViewClientBuilder.build",
            Regex("""desktopMode\s*=\s*desktopUa""").containsMatchIn(screen)
        )
        assertTrue(
            "Builder must forward desktopViewport into JwWebViewClientImpl",
            Regex("""JwWebViewClientImpl\([^)]*desktopViewport""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(builder)
        )
    }

    @Test
    fun desktopViewport_injectionGatedOnSepDomainAndDesktopMode() {
        // onPageFinished 注入必须同时满足 desktopViewport + sep.ucas.ac.cn 域 —
        // xkgo 课表页手机宽度已可用 (报告人横屏导入实证), 禁全局注入
        assertTrue(
            "Builder onPageFinished must gate viewport injection on desktopViewport flag",
            Regex("""if\s*\(desktopViewport && url != null""").containsMatchIn(builder)
        )
        assertTrue(
            "Builder onPageFinished must gate viewport injection on sep.ucas.ac.cn host",
            Regex("""sep\.ucas\.ac\.cn""", RegexOption.IGNORE_CASE).containsMatchIn(
                // 只看 onPageFinished 之后的注入段
                builder.substringAfter("onPageFinished(view: WebView?, url: String?)")
            )
        )
        assertTrue(
            "Builder onPageFinished must evaluateJavascript DESKTOP_VIEWPORT_JS",
            Regex("""evaluateJavascript\(DESKTOP_VIEWPORT_JS""").containsMatchIn(builder)
        )
    }
}
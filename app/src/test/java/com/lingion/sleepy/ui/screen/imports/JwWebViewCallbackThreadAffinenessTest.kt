package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 静态 lint 测试: JwWebViewLoginScreen 的 WebView 回调体内禁止触碰 main-thread-only API。
 *
 * 根因 (2026-09-09 HEBZYHJ 闪退复现, 崩溃栈 17:07:51.487):
 *   shouldInterceptRequest 跑在 Chromium background thread, 旧实现里
 *   `view.settings.userAgentString` 的 getter 在 background thread 触发
 *   `WebView.checkThread()` → RuntimeException("Method ... must be called on
 *   the UI thread") → 进程终止。
 *
 * 不变量:
 *   任何 override fun shouldInterceptRequest / onReceivedSslError 的回调体内
 *   不能出现:
 *     - view.settings
 *     - view.getSettings()
 *     - webView.settings / webView.getSettings()
 *     - view.url / webView.url
 *     - WebView 相关 main-thread-only API
 *
 *   userAgent 必须从 view.settings 提前在主线程一次性读出 → 缓存到 ctx 注入;
 *   host 信息同理 — 用 school.url.toUri().host 替代 view.url.toUri().host。
 *
 * 该测试是 JwWebViewLoginScreen 的架构边界 guard。任何后续重构把 view.settings
 * 重新放进 shouldInterceptRequest, 该测试必须先红。
 */
class JwWebViewCallbackThreadAffinenessTest {

    private val screen: String = sequenceOf(
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwWebViewLoginScreen.kt source")

    init {
        // 源码必须加载成功才能跑后续测试 — 文件不存在直接 fail, 给 CI 一个清晰的错误
        assertNotNull(screen)
        assertTrue("JwWebViewLoginScreen.kt must load non-empty", screen.isNotEmpty())
    }

    /**
     * 提取 `override fun NAME(...)` 后的全部内容 — 终止于下一个 `override fun` 或外层 `}`。
     * 同时覆盖两种函数体语法: expression body (`= expr`) 与 block body (`{ ... }`)。
     * 简易 brace match 在 expression body 上会因 `[^)]*` 跨过第一个 `)` 而错位,
     * 故此处按"到下一个 override fun / 外层 }"切尾。
     */
    private fun extractCallbackBody(name: String): String {
        val header = Regex("""override\s+fun\s+${Regex.escape(name)}\s*\([^)]*\)""")
        val m = header.find(screen)
        assertTrue("override fun $name must exist on the WebViewClient", m != null)
        val start = m!!.range.last + 1
        // 终止候选 1: 下一个 override fun
        val nextOverride = Regex("""(?<=\s)override\s+fun\s+\w+\s*\(""").find(screen, start)
        // 终止候选 2: 外层 `}` (匿名 object 表达式结尾)
        // 在 start 之后找一个 depth-0 的 }, 用简易粗略匹配: 找最近一个 `}` 但跳过前导的 `{...}` block
        // 这里偷懒: 直接取 nextOverride 与 第一个未配对 `}` 中较前者
        val nextClose = run {
            var depth = 0
            var i = start
            while (i < screen.length) {
                when (screen[i]) {
                    '{' -> depth++
                    '}' -> if (depth == 0) return@run i
                         else depth--
                }
                i++
            }
            -1
        }
        val endOverride = nextOverride?.range?.first ?: screen.length
        val endClose = if (nextClose > 0) nextClose + 1 else screen.length
        val end = minOf(endOverride, endClose)
        return screen.substring(start, end)
    }

    @Test
    fun shouldInterceptRequest_doesNotCallViewSettings() {
        // 主线程约束: view.settings / view.getSettings() 在 background thread 触发 checkThread() 崩
        val body = extractCallbackBody("shouldInterceptRequest")
        assertFalse(
            "shouldInterceptRequest callback must NOT call view.settings (background thread crashes)\n" +
                "→ userAgent must be captured on main thread at factory time, passed via ctx\n\n" +
                "Offending body:\n$body",
            Regex("""\bview\.settings\b""").containsMatchIn(body) ||
            Regex("""\bview\.getSettings\b""").containsMatchIn(body)
        )
    }

    @Test
    fun shouldInterceptRequest_doesNotCallWebViewSettings() {
        // 兜底: 捕获 webView.settings / webView.getSettings() 等别名引用
        val body = extractCallbackBody("shouldInterceptRequest")
        assertFalse(
            "shouldInterceptRequest callback must NOT touch WebView settings APIs\n\n" +
                "Offending body:\n$body",
            Regex("""\bwebView\.(settings|getSettings)\b""").containsMatchIn(body)
        )
    }

    @Test
    fun shouldInterceptRequest_doesNotCallViewUrl() {
        // view.url 同为 main-thread-only (WebView.url 在 background thread 触发 checkThread)
        val body = extractCallbackBody("shouldInterceptRequest")
        assertFalse(
            "shouldInterceptRequest callback must NOT read view.url (main-thread-only)\n" +
                "→ use request.url directly or host captured at factory time\n\n" +
                "Offending body:\n$body",
            Regex("""\bview\.url\b""").containsMatchIn(body)
        )
    }

    @Test
    fun onReceivedSslError_doesNotCallViewUrl() {
        // onReceivedSslError: Android docs say UI thread, but historical implementations have
        // moved it to background in some Chromium builds. Same defensive ban applies.
        val body = extractCallbackBody("onReceivedSslError")
        assertFalse(
            "onReceivedSslError callback must NOT read view.url — thread affinity is unreliable\n" +
                "→ use school.url.toUri().host at factory time, pass as captured param\n\n" +
                "Offending body:\n$body",
            Regex("""\bview\.url\b""").containsMatchIn(body)
        )
    }
}
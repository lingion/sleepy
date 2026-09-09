package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 静态 lint 测试: 包内任何 `override fun shouldInterceptRequest / onReceivedSslError`
 * 的回调体内禁止触碰 main-thread-only API。
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
 *     - view.settings / view.getSettings()
 *     - webView.settings / webView.getSettings()
 *     - view.url / webView.url
 *     - WebView 相关 main-thread-only API
 *
 *   userAgent 必须从 view.settings 提前在主线程一次性读出 → 缓存到 ctx 注入;
 *   host 信息同理 — 用 school.url.toUri().host 替代 view.url.toUri().host。
 *
 * ## 扫描策略 (Plan D 重构后)
 * 重构后回调搬进 [JwWebViewClientBuilder] 的 [JwWebViewClientImpl] (不再是 screen 内 inline),
 * 但 lint 不变量不变 — 整个 `ui.screen.imports` 包内任何 .kt 文件若定义这两个 override,
 * body 必须不能触碰 view.settings/view.url。扫描覆盖包内所有 .kt 源文件 (worktree / 主仓
 * 多候选路径),未来加新 interceptor 也会自动被扫描。
 */
class JwWebViewCallbackThreadAffinenessTest {

    /**
     * 加载 ui.screen.imports 包内全部 .kt 源文件。从多个候选根路径 (cwd / 当前 worktree /
     * 主仓兜底) 扫;Gradle test working dir 在子项目根,故子项目路径也兼容。
     */
    private fun loadPackageSources(): List<Pair<String, String>> {
        val pkgDirRel = "app/src/main/java/com/lingion/sleepy/ui/screen/imports"
        val userDir = System.getProperty("user.dir") ?: ""
        val fromAppDir = if (userDir.endsWith("/app")) "$userDir/src/main/java/com/lingion/sleepy/ui/screen/imports"
                         else "$userDir/$pkgDirRel"
        val currentWorktree = "/Users/lingion_k/sleepy-worktrees/d-pipeline/$pkgDirRel"
        val mainRepo = "/Users/lingion_k/sleepy/$pkgDirRel"
        val roots = sequenceOf(
            java.io.File(pkgDirRel),
            java.io.File(fromAppDir),
            java.io.File(currentWorktree),
            java.io.File(mainRepo),
        )
        val firstExisting = roots.firstOrNull { it.isDirectory } ?: error(
            "No source root found for $pkgDirRel. Tried: $pkgDirRel, $fromAppDir, $currentWorktree, $mainRepo"
        )
        return firstExisting.listFiles { f -> f.isFile && f.name.endsWith(".kt") }
            ?.map { it.name to it.readText() }
            ?.sortedBy { it.first }
            ?: emptyList()
    }

    private val packageSources: List<Pair<String, String>> by lazy { loadPackageSources() }

    init {
        // 至少一个源文件存在才能跑后续测试
        assertTrue(
            "ui.screen.imports package must contain at least one .kt source file",
            packageSources.isNotEmpty()
        )
    }

    /**
     * 在单文件源码中提取 `override fun NAME(...)` 后的 body (expression body 或 block body),
     * 终止于下一个 `override fun` 或外层 `}`。
     *
     * 简易 brace match 在 expression body 上会因 `[^)]*` 跨过第一个 `)` 而错位,
     * 故按"到下一个 override fun / 外层 }"切尾 — 见 [bug-reproduction skill] §"replace
     * arbitrary timeouts with condition polling" 类似的"终止候选双保险"模式。
     */
    private fun extractCallbackBody(source: String, name: String): String? {
        val header = Regex("""override\s+fun\s+${Regex.escape(name)}\s*\([^)]*\)""")
        val m = header.find(source) ?: return null
        val start = m.range.last + 1
        val nextOverride = Regex("""(?<=\s)override\s+fun\s+\w+\s*\(""").find(source, start)
        val nextClose = run {
            var depth = 0
            var i = start
            while (i < source.length) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> if (depth == 0) return@run i else depth--
                }
                i++
            }
            -1
        }
        val endOverride = nextOverride?.range?.first ?: source.length
        val endClose = if (nextClose > 0) nextClose + 1 else source.length
        val end = minOf(endOverride, endClose)
        val raw = source.substring(start, end)
        return stripComments(raw)
    }

    /**
     * 剥离单行 + 块注释 — 否则反例文档 `// 而非 view.url (main-thread-only)` 里的
     * "view.url" 字面字符串会被误判为违规调用 (false positive, 2026-09-09 翻车)。
     */
    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")  // 块注释 → 留空白保留列号对齐
            .replace(Regex("""(?m)^\s*//[^\n]*"""), "")  // 行注释整行删
            .replace(Regex("""//[^\n]*"""), "")          // 行尾注释

    /** 对每个包源文件, 若声明了 `override fun NAME`, body 必须不含 view.settings / view.url */
    private fun assertNoOffendingCalls(callbackName: String, pattern: Regex, rule: String) {
        var checked = 0
        for ((filename, source) in packageSources) {
            val body = extractCallbackBody(source, callbackName) ?: continue
            checked++
            assertFalse(
                "$filename: $callbackName callback must NOT call $rule\n" +
                    "→ Plan D invariant: any WebView callback that touches main-thread-only API\n" +
                    "  crashes the app when Chromium dispatches it on the IO thread (HEBZYHJ 2026-09-09).\n" +
                    "  Capture state on main thread at factory time, pass via ctx instead.\n\n" +
                    "Offending body:\n$body",
                pattern.containsMatchIn(body)
            )
        }
        assertTrue(
            "At least one file in ui.screen.imports must declare $callbackName (sanity guard)",
            checked >= 1
        )
    }

    @Test
    fun shouldInterceptRequest_doesNotCallViewSettings() {
        assertNoOffendingCalls(
            "shouldInterceptRequest",
            Regex("""\bview\.settings\b|\bview\.getSettings\b|\bwebView\.(settings|getSettings)\b"""),
            "view.settings / view.getSettings / webView.settings"
        )
    }

    @Test
    fun shouldInterceptRequest_doesNotCallViewUrl() {
        assertNoOffendingCalls(
            "shouldInterceptRequest",
            Regex("""\bview\.url\b|\bwebView\.url\b"""),
            "view.url (main-thread-only; use ctx.schoolHost or request.url instead)"
        )
    }

    @Test
    fun onReceivedSslError_doesNotCallViewUrl() {
        // Android docs say onReceivedSslError is UI thread, but historical Chromium builds
        // moved it to background. Same defensive ban applies.
        assertNoOffendingCalls(
            "onReceivedSslError",
            Regex("""\bview\.url\b|\bwebView\.url\b"""),
            "view.url (use error.url — SslError instance field — or ctx.schoolHost instead)"
        )
    }

    @Test
    fun onReceivedSslError_doesNotCallViewSettings() {
        // 兜底: 同样的 main-thread-only 约束适用于所有 callback
        assertNoOffendingCalls(
            "onReceivedSslError",
            Regex("""\bview\.settings\b|\bview\.getSettings\b|\bwebView\.(settings|getSettings)\b"""),
            "view.settings"
        )
    }
}
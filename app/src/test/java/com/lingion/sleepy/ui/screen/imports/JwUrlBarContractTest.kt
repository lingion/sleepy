package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the browser-like URL bar wiring.
 *
 * The URL shown in the bar must come from WebView navigation callbacks, and URL
 * submission must be restricted to HTTP(S) before calling loadUrl.
 */
class JwUrlBarContractTest {
    private fun loadSource(name: String): String {
        val rel = "app/src/main/java/com/lingion/sleepy/ui/screen/imports/$name"
        val userDir = (System.getProperty("user.dir") ?: "").replace('\\', '/')
        val fromAppDir = if (userDir.endsWith("/app")) {
            "$userDir/src/main/java/com/lingion/sleepy/ui/screen/imports/$name"
        } else null
        return sequenceOf(
            System.getProperty("sleepy.test.root")?.let { java.io.File(it, rel) },
            java.io.File(rel),
            fromAppDir?.let { java.io.File(it) },
            java.io.File("/private/tmp/jw-url-address-bar/$rel"),
        ).filterNotNull().firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load $name")
    }

    private val builder by lazy { loadSource("JwWebViewClientBuilder.kt") }
    private val screen by lazy { loadSource("JwWebViewLoginScreen.kt") }

    @Test
    fun builder_forwards_navigation_url_without_reading_webViewUrl() {
        assertTrue(builder.contains("onUrlChanged: (String?) -> Unit = {}"))
        assertTrue(builder.contains("doUpdateVisitedHistory"))
        assertTrue(builder.contains("onUrlChanged(url)"))
    }

    @Test
    fun screen_keeps_url_draft_separate_from_live_navigation_url() {
        assertTrue(screen.contains("var currentUrl by remember"))
        assertTrue(screen.contains("val urlDraft = rememberTextFieldState(school.url)"))
        assertTrue(screen.contains("var editingUrl by remember"))
        assertTrue(screen.contains("if (!editingUrl &&"))
    }

    @Test
    fun screen_submits_only_http_or_https_urls() {
        assertTrue(screen.contains("(uri.scheme == \"http\" || uri.scheme == \"https\")"))
        assertTrue(screen.contains("loadUrl(normalized)"))
    }

    @Test
    fun screen_seeds_edit_draft_from_live_url_and_exits_edit_before_back() {
        assertTrue(screen.contains("replace(0, length, currentUrl)"))
        val navigation = screen.substringAfter("navigationIcon = {").substringBefore("actions = {")
        assertTrue(navigation.contains("if (editingUrl) editingUrl = false else { cancelCapture(); onBack() }"))
        val backHandler = screen.substringAfter("BackHandler {").substringBefore("Scaffold(")
        val editExit = backHandler.indexOf("return@BackHandler")
        val captureCancel = backHandler.indexOf("cancelCapture()")
        assertTrue(backHandler.contains("if (editingUrl) {"))
        assertTrue(backHandler.substringBefore("return@BackHandler").contains("editingUrl = false"))
        assertTrue(editExit >= 0 && captureCancel > editExit)
    }

    @Test
    fun screen_hides_secondary_actions_while_editing_to_preserve_url_field_width() {
        assertTrue(screen.contains("if (!editingUrl) {"))
        assertTrue(screen.contains("desktopUa = !desktopUa"))
        assertTrue(screen.contains("webViewRef?.reload()"))
    }

    @Test
    fun screen_uses_state_text_field_with_scrollable_single_line() {
        // state 版才走 TextFieldCoreModifierNode 的横向滚动路径;
        // 编辑时只替换文本, 不播种 selection, 首次点击位置由输入框决定。
        assertTrue(screen.contains("rememberTextFieldState(school.url)"))
        assertTrue(screen.contains("TextFieldLineLimits.SingleLine"))
        assertTrue(screen.contains("scrollState = rememberScrollState()"))
        assertTrue(screen.contains("urlDraft.text.trim()"))
        assertTrue(!screen.contains("TextRange(currentUrl.length)"))
    }
}

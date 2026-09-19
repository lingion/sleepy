package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 诊断会话 ring buffer 契约 — 记录 WebView 加载期间网络请求 + console 输出。
 *
 * 边加载边记: 请求在页面加载时就发,失败时才想抓已经晚了——必须边加载边记。
 * ring buffer 上限各 500 条,超则丢最旧。
 */
class JwDiagnosticSessionContractTest {

    private val source: String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt"),
        File("src/main/java/com/lingion/sleepy/ui/screen/imports/JwDiagnosticSession.kt"),
    ).firstOrNull { it.isFile }?.readText() ?: error("Unable to load JwDiagnosticSession.kt source")

    @Test
    fun diagnostic_session_records_network_requests() {
        assertTrue(
            "必须有记录网络请求的方法 — shouldInterceptRequest 回调消费",
            Regex("""fun\s+(onInterceptRequest|recordRequest|logRequest)\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun diagnostic_session_records_console_messages() {
        assertTrue(
            "必须有记录 console 输出的方法",
            Regex("""fun\s+(onConsoleMessage|recordConsole|logConsole)\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun diagnostic_session_has_ring_buffer_limit_500() {
        assertTrue(
            "网络请求 ring buffer 上限必须 500 条 — 超则丢最旧",
            source.contains("500")
        )
    }

    @Test
    fun diagnostic_session_exports_netlog_text() {
        assertTrue(
            "必须有导出 netlog.txt 的方法 — 排查包消费",
            Regex("""fun\s+(exportNetlog|toNetlog|generateNetlog)\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun diagnostic_session_exports_console_text() {
        assertTrue(
            "必须有导出 console.txt 的方法",
            Regex("""fun\s+(exportConsole|toConsole|generateConsole)\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun diagnostic_session_thread_safe() {
        // WebView interceptor / console callback 在后台线程调用,必须有线程安全措施
        assertTrue(
            "必须使用线程安全机制 (synchronized / ConcurrentLinkedQueue / CopyOnWriteArrayList)",
            Regex("""synchronized|ConcurrentLinkedQueue|ConcurrentLinkedDeque|CopyOnWriteArrayList|Collections\.synchronized""").containsMatchIn(source)
        )
    }
}

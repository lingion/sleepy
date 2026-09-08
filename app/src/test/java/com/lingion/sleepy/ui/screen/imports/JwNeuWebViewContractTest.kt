package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #27: NEU WebView capture contract.
 *
 * NEU uses the Wisedu mobile JSON protocol, but its endpoint is
 * /jwapp/sys/home/student/getMyScheduleDetail.do (not the dqxnxq/xskcb.do
 * sequence the generic Wisedu path expects). The capture dispatcher must
 * pick a dedicated JS branch for TYPE_NEU, and the JS must:
 *   - target the NEU endpoint,
 *   - send session cookies via credentials:'include',
 *   - return raw JSON through __sleepyBridge.onWiseduResult,
 *   - keep the {ok, data, periods} payload shape so the existing Kotlin
 *     handler routes it into JwImportViewModel.parseHtml(..., "neu").
 */
class JwNeuWebViewContractTest {

    private val source: String = sequenceOf(
        java.io.File("app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        java.io.File(System.getProperty("user.dir"), "sleepy/app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt")
    ).firstOrNull { it.isFile }
        ?.readText()
        ?: error("Unable to load JwWebViewLoginScreen.kt source")

    @Test
    fun neuWebView_dispatch_selects_a_dedicated_fetch_branch() {
        assertTrue(
            "Capturing NEU must not fall through to the generic Wisedu dqxnxq/xskcb flow",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_NEU""").containsMatchIn(source)
        )
        assertTrue(
            "There must be a dedicated NEU fetch JS constant",
            source.contains("NEU_FETCH_JS")
        )
    }

    @Test
    fun neuFetchJs_targets_schedule_detail_endpoint_with_session_credentials() {
        val start = source.indexOf("private const val NEU_FETCH_JS")
        val end = source.indexOf("private const val WISEDU_FETCH_JS")
        assertTrue("NEU_FETCH_JS constant missing", start >= 0 && end > start)
        val js = source.substring(start, end)

        assertTrue(
            "NEU fetch must hit /jwapp/sys/home/student/getMyScheduleDetail.do",
            js.contains("/jwapp/sys/home/student/getMyScheduleDetail.do")
        )
        assertTrue(
            "NEU fetch must include session cookies via credentials:'include'",
            js.contains("credentials:'include'") || js.contains("credentials: \"include\"")
        )
        assertTrue(
            "NEU fetch must hand the response back through __sleepyBridge.onWiseduResult",
            js.contains("__sleepyBridge.onWiseduResult")
        )
        assertTrue(
            "NEU fetch payload must keep the {ok,data,periods} envelope expected by the Kotlin handler",
            js.contains("ok:true") && js.contains("data:") && js.contains("periods:")
        )
    }
}
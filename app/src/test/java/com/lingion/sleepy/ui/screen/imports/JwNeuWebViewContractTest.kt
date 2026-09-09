package com.lingion.sleepy.ui.screen.imports

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #27: NEU WebView capture contract.
 *
 * NEU's timetable lives only in the mobile JSON API under /jwapp/sys/homeapp/
 * (PR #29): the page HTML carries no course data, so the capture dispatcher
 * must pick a dedicated JS branch for TYPE_NEU instead of falling through to
 * the generic Wisedu dqxnxq/xskcb flow. The JS must:
 *   - refuse to run off jwxt.neu.edu.cn,
 *   - resolve the current term via currentUser.do and the campus via
 *     getMyScheduledCampus.do?termCode=,
 *   - POST getMyScheduleDetail.do with termCode/campusCode/type=term and
 *     assert the datas.arrangedList payload,
 *   - send session cookies via credentials:'include',
 *   - return the raw JSON through __sleepyBridge.onWiseduResult with the
 *     {ok, data} envelope so the Kotlin handler routes it into
 *     JwImportViewModel.parseHtml(..., "neu").
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
        val start = source.indexOf("const val NEU_FETCH_JS")
        val end = source.indexOf("const val CQU_FETCH_JS")
        assertTrue("NEU_FETCH_JS constant missing", start >= 0 && end > start)
        val js = source.substring(start, end)

        assertTrue(
            "NEU fetch must refuse to run off jwxt.neu.edu.cn",
            js.contains("jwxt.neu.edu.cn")
        )
        assertTrue(
            "NEU fetch must resolve the current term via currentUser.do",
            js.contains("/jwapp/sys/homeapp/api/home/currentUser.do")
        )
        assertTrue(
            "NEU fetch must resolve the campus via getMyScheduledCampus.do?termCode=",
            js.contains("getMyScheduledCampus.do?termCode=")
        )
        assertTrue(
            "NEU fetch must POST /jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do",
            js.contains("/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do")
        )
        assertTrue(
            "NEU fetch must POST the schedule detail form (termCode/campusCode/type=term)",
            js.contains("method:'POST'")
                && js.contains("termCode=") && js.contains("campusCode=") && js.contains("type=term")
        )
        assertTrue(
            "NEU fetch must include session cookies via credentials:'include'",
            js.contains("credentials:'include'") || js.contains("credentials: \"include\"")
        )
        assertTrue(
            "NEU fetch must assert the datas.arrangedList payload",
            js.contains("arrangedList")
        )
        assertTrue(
            "NEU fetch must hand the response back through __sleepyBridge.onWiseduResult",
            js.contains("__sleepyBridge.onWiseduResult")
        )
        assertTrue(
            "NEU fetch payload must keep the {ok,data} envelope expected by the Kotlin handler",
            js.contains("ok:true") && js.contains("data:")
        )
    }
}

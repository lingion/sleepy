package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSourceCalendar
import com.lingion.sleepy.testutil.readProjectSource
import com.lingion.sleepy.util.DateUtils
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static WebView wiring contracts plus execution of the real shared calendar/date consumers. */
class JwFduWebViewContractTest {
    private val webView = readProjectSource(
        "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt",
    )
    private val callback: String
        get() {
            val start = webView.indexOf("val handleWiseduResult")
            val end = webView.indexOf("fun evaluateFetchWithTimeout", start)
            check(start >= 0 && end > start) { "Shared JSON callback boundaries are missing" }
            return webView.substring(start, end)
        }

    @Test fun `FDU dispatch follows USTC and returns before other API EAMS5 and frame capture`() {
        val captureStart = webView.indexOf("CaptureBar(")
        val ustc = webView.indexOf("JwUstcCapture.scriptFor(school.type)", captureStart)
        val branch = Regex(
            """if\s*\(\s*school\.type\s*==\s*JwProtocol\.TYPE_FDU\s*\)\s*\{\s*evaluateFetchWithTimeout\(wv,\s*JwFduCapture\.FETCH_JS\)\s*return@CaptureBar\s*}""",
        ).find(webView, captureStart)
        assertTrue("FDU must have its own bounded fetch branch and terminal CaptureBar return", branch != null)
        val fdu = branch!!.range.first
        val otherApi = webView.indexOf("JwSchoolApiCapture.scriptFor(school.type)", captureStart)
        val eams5 = webView.indexOf("if (school.type == JwProtocol.TYPE_EAMS5)", captureStart)
        val frames = webView.indexOf("captureWithRetry(wv, 0, school.type)", captureStart)
        assertTrue("Dedicated route order is USTC, FDU, other API, EAMS5, generic frames",
            captureStart >= 0 && ustc > captureStart && fdu > ustc && otherApi > fdu &&
                eams5 > otherApi && frames > eams5)
        assertFalse("The declared FDU route must not depend on guessed URL or catalog promotion",
            branch.value.contains("enableFetch") || branch.value.contains("contains("))
    }

    @Test fun `CI executes the production FDU JavaScript contract exactly once`() {
        val workflow = readProjectSource(".github/workflows/ci.yml")
        assertEquals(1, Regex("""(?m)^\s*node tools/test-fdu-fetch\.js\s*$""")
            .findAll(workflow).count())
    }

    @Test fun `shared initial JSON callback forwards startDate without consuming sourceStartDate`() {
        assertTrue(callback.contains("val termStartDate = obj.optString(\"startDate\", \"\")"))
        assertTrue(callback.contains("var termStart = termStartDate"))
        assertTrue(callback.contains("val effectiveStartDate = termStart.ifBlank { termStartDate }"))
        assertTrue(callback.contains("onHtmlCaptured(data, capturedSchool, periods, effectiveStartDate)"))
        assertFalse("The raw Sunday is provenance only", callback.contains("sourceStartDate"))
        assertFalse("The initial callback must not normalize the raw Sunday", callback.contains("mondayOf("))
    }

    @Test fun `Activity forwards the callback date into the real shared calendar resolver`() {
        val activity = readProjectSource(
            "app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwImportActivity.kt",
        )
        val start = activity.indexOf("onHtmlCaptured = { html, sch, periods, termStartDate ->")
        assertTrue(start >= 0)
        val consumer = Regex(
            """configStartDate\s*=\s*com\.lingion\.sleepy\.data\.jw\.JwSourceCalendar\s*\.resolveStartDate\(htmlForParse,\s*effectiveType,\s*termStartDate\)""",
        ).find(activity, start)
        assertTrue("The Activity must pass the callback date, not the raw source Sunday", consumer != null)
    }

    private fun payload(startDate: String?, sourceSunday: String, data: String = """{"studentTableVms":[{"activities":[]}]}"""): String =
        JSONObject().put("ok", true).put("data", data).put("sourceStartDate", sourceSunday).apply {
            if (startDate != null) put("startDate", startDate)
        }.toString()

    // The Android lambda is locked above by source contracts. Only its actual JSON field key is
    // read here; date interpretation below executes JwSourceCalendar and DateUtils themselves.
    private fun consume(payload: String): String {
        val key = Regex("""val termStartDate\s*=\s*obj\.optString\("([^"]+)",\s*""\)""")
            .find(callback)?.groupValues?.get(1) ?: error("Shared callback date read is missing")
        val root = JSONObject(payload)
        return JwSourceCalendar.resolveStartDate(root.optString("data", ""), JwProtocol.TYPE_FDU,
            root.optString(key, ""))
    }

    @Test fun `advanced Monday survives shared calendar consumption without falling back a week`() {
        val date = consume(payload("2026-03-02", "2026-03-01"))
        assertEquals("2026-03-02", date)
        assertEquals("2026-02-23", DateUtils.mondayOf(LocalDate.parse("2026-03-01")).toString())
        assertEquals(LocalDate.parse("2026-03-02"), DateUtils.dateOfWeek(date, 1, 1))
        assertEquals(LocalDate.parse("2026-03-08"), DateUtils.dateOfWeek(date, 1, 7))
    }

    @Test fun `following Monday stays in the new year through actual date normalization`() {
        val date = consume(payload("2024-01-01", "2023-12-31"))
        assertEquals("2024-01-01", date)
        assertEquals("2024-01-01", DateUtils.normalizeStartDate(date))
        assertEquals(LocalDate.parse("2024-01-07"), DateUtils.dateOfWeek(date, 1, 7))
    }

    @Test fun `source Sunday cannot supply a missing captured Monday`() {
        assertEquals("", consume(payload(null, "2026-03-01")))
        assertEquals("", consume(payload("", "2026-03-01")))
    }

    @Test fun `unproven dates inside original course JSON cannot override the captured Monday`() {
        val courses = """{"studentTableVms":[{"activities":[]}],"startDate":"2026-02-23","sourceStartDate":"2026-03-01"}"""
        assertEquals("2026-03-02", consume(payload("2026-03-02", "2026-03-01", courses)))
    }

    @Test fun `PG JSON cannot invent an opening date when the collector has no date evidence`() {
        val courses = """{"results":[],"startDate":"2030-01-01","sourceStartDate":"2026-03-01"}"""
        assertEquals("", consume(payload("", "", courses)))
    }
}

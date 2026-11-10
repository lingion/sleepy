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

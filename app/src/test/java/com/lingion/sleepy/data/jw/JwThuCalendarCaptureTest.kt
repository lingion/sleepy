package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.JwThuSemesterCapture
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwThuCalendarCaptureTest {
    private fun html(semester:String="2026-2027-1")="""<input name='p_xnxq' value='$semester'><script>function setInitValue() {
        strHTML += "<a class='mainHref' href='/x;ABCDEFG1'>";
        strHTML += "<b>清华课程</b>";
        strHTML1 += "；王老师";
        strHTML1 += "；全周";
        strHTML1 += "；主楼";
        getElementById('a1_1').innerHTML += strHTML;
        } Event.observe(window,'load',setInitValue);</script>"""

    @Test fun explicitSemesterSelectsTheOnlySourceCalendarUrl() = runBlocking {
        val requested=mutableListOf<String>()
        val envelope=JwThuSemesterCapture.captureWith(html()) { url -> requested+=url; """{"weekCount":18,"reschedule":[[1,1,2,7],[3,1]]}""" }
        assertEquals(listOf("https://schedule-data.netlify.app/2026-2027-1.json"),requested)
        val actual=JwThuparser(envelope).generateCourseList()
        assertEquals(listOf(JwCourse("清华课程","主楼","王老师",1,1,2,2,2),JwCourse("清华课程","主楼","王老师",1,1,2,4,18),JwCourse("清华课程","主楼","王老师",7,1,2,2,2)),actual)
    }

    @Test fun capturedCalendarSurvivesRegistryStaticAndDraftWithItsWeekCount() {
        val payload = JwThuCapturePayload.wrap(html(), """{"weekCount":18,"reschedule":[[1,1,2,7],[3,1]]}""")
        val expected = listOf(
            JwCourse("清华课程", "主楼", "王老师", 1, 1, 2, 2, 2),
            JwCourse("清华课程", "主楼", "王老师", 1, 1, 2, 4, 18),
            JwCourse("清华课程", "主楼", "王老师", 7, 1, 2, 2, 2))
        assertEquals(expected, assertUndergradAJsonImportRoutes(JwProtocol.TYPE_THU, payload))
        assertEquals(18, JwSourceCalendar.resolveWeekCount(payload, JwProtocol.TYPE_THU))
        val wrong = JSONObject(payload).put("semester", "2026-2027-2").toString()
        assertTrue(JwParserRegistry.selectBest(wrong, JwProtocol.TYPE_THU).first.isEmpty())
        assertEquals(0, JwSourceCalendar.resolveWeekCount(wrong, JwProtocol.TYPE_THU))
    }

    @Test fun selectionCheckRequiresOneUnchangedExplicitSemester() {
        assertTrue(JwThuCapturePayload.sameSemester(html(), html()))
        for (changed in listOf(html("2026-2027-2"), "<html>login</html>", html() + html("2026-2027-2")))
            assertTrue(!JwThuCapturePayload.sameSemester(html(), changed))
        assertTrue(!JwThuCapturePayload.sameSemester("", ""))
    }

    @Test fun unsafeMissingAndAmbiguousSemesterNeverRequestsNetwork() = runBlocking {
        for(raw in listOf(html("../../x"),html("") ,html()+"<input name='p_xnxq' value='2026-2027-2'>")) {
            var requests=0
            val failure=runCatching { JwThuSemesterCapture.captureWith(raw) { requests++; "{}" } }
            assertTrue(failure.isFailure);assertEquals(0,requests)
        }
    }

    @Test fun calendarRequestOrInvalidDataDoesNotSilentlyFallBackTo16Weeks() = runBlocking {
        assertTrue(runCatching { JwThuSemesterCapture.captureWith(html()) { error("offline") } }.isFailure)
        for(raw in listOf("<html>error</html>","{}","""{"ok":false}""","""{"weekCount":2.5}""","""{"reschedule":[[1,1,2]]}"""))
            assertTrue(runCatching { JwThuSemesterCapture.captureWith(html()) { raw } }.isFailure)
    }

    @Test fun changedSemesterAndMalformedEnvelopeAreRejected() {
        val valid=JwThuCapturePayload.wrap(html(),"""{"weekCount":18}""")
        assertEquals(18,JwThuparser(valid).generateCourseList().single().endWeek)
        for(raw in listOf(JSONObject(valid).put("semester","2026-2027-2").toString(),JSONObject(valid).put("semesterData",JSONObject().put("weekCount",0)).toString(),JSONObject(valid).put("sleepyThuCapture","true").toString()))
            assertTrue(JwThuparser(raw).generateCourseList().isEmpty())
    }
}

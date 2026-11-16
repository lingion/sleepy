package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwNewUrpParser
import org.junit.Assert.*
import org.junit.Test

class JwAutomaticSchemaFrameTest {
    private val html = """<script>const arbitraryPayload={"dateList":[{"selectCourseList":[{"courseName":"结构课程","attendClassTeacher":"甲","timeAndPlaceList":[{"classDay":3,"classSessions":4,"continuingSession":2,"classWeek":"101","classroomName":"A101"}]}]}]};</script>"""
    private fun snapshots(decoy: String = "<html><body>门户</body></html>") = FrameSnapshotList("https://example.edu",8,listOf(
        FrameSnapshot("(top)",null,0,emptyList(),decoy,null),
        FrameSnapshot("schedule","/schedule",2,listOf("(top)","main"),html,null),
    ))

    @Test fun `automatic capture uses registered schema without decorative anchors`() {
        assertTrue(FrameTraversalTree.findAnchors(html).isEmpty())
        val result=FrameTraversalTree.selectBestFrame(snapshots())
        assertEquals(FrameCaptureStatus.OK,result.status)
        assertEquals(listOf("(top)","main","schedule"),result.selectedFramePath)
        assertEquals(html,result.html)
        assertEquals(JwNewUrpParser(html).generateCourseList().size,result.courseCount)
        assertEquals(2,result.allFrames.size)
    }

    @Test fun `an empty decorated frame cannot hide another frames actual schema`() {
        val result=FrameTraversalTree.selectBestFrame(snapshots("<table id='Table1'><tr><td>暂无课程</td></tr></table>"))
        assertEquals(FrameCaptureStatus.OK,result.status)
        assertEquals(html,result.html)
        assertTrue(result.courseCount>0)
    }

    @Test fun `declared schema capture records its successfully parsed count`() {
        val result=FrameTraversalTree.selectBestFrame(snapshots(),declaredType="urp_new")
        assertEquals(FrameCaptureStatus.OK,result.status)
        assertEquals(html,result.html)
        assertEquals(JwNewUrpParser(html).generateCourseList().size,result.courseCount)
    }

    @Test fun `all reachable frames still guard automatic schema capture login state`() {
        val result=FrameTraversalTree.selectBestFrame(snapshots("<form action='/authserver/login'><input type='password'></form>"))
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED,result.status)
    }

    @Test fun `no schema and no known container remains a wrong page`() {
        val frames=snapshots().copy(frames=listOf(FrameSnapshot("(top)",null,0,emptyList(),"<p>dateList courseName classWeek</p>",null)))
        assertEquals(FrameCaptureStatus.WRONG_PAGE,FrameTraversalTree.selectBestFrame(frames).status)
    }
    @Test fun `parsed courses remain ready despite stale loading decorations at any retry`() {
        val result=FrameCaptureResult(listOf("(top)"), "<div id='kblist_table'>loading</div>"+html,
            emptyList(),courseCount=1,status=FrameCaptureStatus.OK)
        for (retry in 0..3) assertEquals(RenderReadinessChecker.Readiness.READY, RenderReadinessChecker.check(result,retry))
    }

    @Test fun `expired sessions bypass delayed page retries`() {
        val result=FrameCaptureResult(listOf("(top)"),"<div id='kbgrid_table_0'>loading</div>",
            emptyList(),status=FrameCaptureStatus.SESSION_EXPIRED)
        assertEquals(RenderReadinessChecker.Readiness.READY,RenderReadinessChecker.check(result,0))
    }

    @Test fun `actual password controls accept attribute quoting and whitespace`() {
        for (input in listOf("<input type='password'>", "<input type = \"password\">", "<input type=password>")) {
            val result=FrameTraversalTree.selectBestFrame(snapshots("<form action='/authserver/login'>"+input+"</form>"))
            assertEquals(input, FrameCaptureStatus.SESSION_EXPIRED,result.status)
        }
    }

}

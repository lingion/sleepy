package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwImportViewModel
import org.junit.Assert.*
import org.junit.Test

class JwDeclaredHtmlCaptureTest {
    private val samples = mapOf(
        "cnu" to "<table><tbody><tr><td>节次</td><td><div class='curriculum-item'><div><span>课程甲</span></div><div>张</div><div>1-2周[1-2节]</div><div>A</div></div></td>${"<td></td>".repeat(6)}</tr></tbody></table>",
        "gliet" to "<table role='presentation'><tr><td>第1、2节</td><td><div>课号:101张<br>课程甲<br>(1-2)A</div></td></tr></table>",
        "shcc" to """<script>var ypData=[{"kcS":"报关实务","js":"张(1-2)","jsmc":"A","xqj":3,"djj":5,"skcd":2}];</script>""",
        "hbmzu" to "<table class='list_table tac'><tr><td><p><span>课程甲</span><span>1-2周</span><span>A</span><span>张</span></p></td></tr></table>",
        "nua" to "<table><tr><th>课表</th></tr><tr><td>课程甲</td><td>张</td><td></td><td>1-2周 周三5,6</td><td>A</td></tr></table>",
        "gdei" to "<table><tbody><tr><td>第1-2节</td><td><div><div>课程甲</div><div><span>第1-2周</span></div><div></div><div>A</div><div>张</div></div></td></tr></tbody></table>",
        "aic" to "<table id='table'><tr><th>课表</th></tr><tr><td>1</td><td><div class='courseInfo'><b>课程甲</b><span class='weekDetail'>1-2周</span><span class='place'>A</span><span class='teacher'>张</span></div></td></tr></table><script>getScheduleNew.do</script>",
        "hust" to "<div class='main-page-block'><div><p>课程甲</p><p></p><p>专业</p><div class='search-details-body'><div><div class='text-box'>时间:1-2周 星期三 1-2节</div><div class='text-box'>教师:张</div><div class='text-box'>教室:A</div></div></div></div></div>",
    )

    private fun frames(html: String, login: String? = null) = FrameSnapshotList("https://example.edu.cn", 8, listOfNotNull(
        FrameSnapshot("(top)", null, 0, emptyList(), "<table id='kbtable'><tr><td>菜单</td></tr></table>", null),
        FrameSnapshot("schedule", "/schedule", 2, listOf("(top)", "main"), html, null),
        login?.let { FrameSnapshot("login", "/login", 1, listOf("(top)"), it, null) },
    ))

    @Test fun `declared school timetable reaches parser without generic container ids`() {
        for ((type, html) in samples) {
            val result = FrameTraversalTree.selectBestFrame(frames(html), declaredType = type)
            assertEquals(type, FrameCaptureStatus.OK, result.status)
            assertEquals(type, listOf("(top)", "main", "schedule"), result.selectedFramePath)
            assertEquals(type, html, result.html)
            assertFalse(type, JwImportViewModel.parseHtmlStatic(result.html, type).first.isEmpty())
        }
    }

    @Test fun `declared capture does not promote unrelated table or bypass login`() {
        val unrelated = frames("<table><tr><td>通知公告</td></tr></table>")
        for (type in samples.keys) {
            assertFalse(FrameTraversalTree.selectBestFrame(unrelated, declaredType = type).matchedAnchors.contains("declared-$type"))
            val expired = frames(samples.getValue(type), "<script>window.parent.location.href='logout.aspx'</script>")
            assertEquals(FrameCaptureStatus.SESSION_EXPIRED, FrameTraversalTree.selectBestFrame(expired, declaredType = type).status)
        }
    }
}

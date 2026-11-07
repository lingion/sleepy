package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus
import com.lingion.sleepy.ui.screen.imports.FrameSnapshot
import com.lingion.sleepy.ui.screen.imports.FrameSnapshotList
import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source layouts exercise the actual declared import route, frame selection and persistence boundary. */
class JwSixDeclaredProtocolRoutingTest {
    private fun verify(type: String, html: String, expected: JwCourse) {
        assertEquals(type, listOf(expected), JwParserRegistry.parserFor(type, html).generateCourseList())
        assertEquals(type, listOf(expected), JwParserRegistry.selectBest(html, type).first)
        val snapshots = FrameSnapshotList("https://example.edu.cn", 8, listOf(
            FrameSnapshot("(top)", null, 0, emptyList(), "<table id='kbtable'><tr><td>菜单</td></tr></table>", null),
            FrameSnapshot("courses", "/courses", 2, listOf("(top)", "main"), html, null),
        ))
        val frame = FrameTraversalTree.selectBestFrame(snapshots, declaredType = type)
        assertEquals(type, FrameCaptureStatus.OK, frame.status)
        assertEquals(type, listOf("(top)", "main", "courses"), frame.selectedFramePath)
        val courses = JwImportViewModel.parseHtmlStatic(frame.html, type).first
        assertEquals(listOf(expected), courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("test", "synthetic", type = type), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(draft, restored)
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4").single()
        assertEquals(listOf(expected.name, expected.teacher, expected.room), listOf(entity.courseName, entity.teacher, entity.room))
        assertEquals(listOf(expected.day, expected.startNode, expected.endNode - expected.startNode + 1,
            expected.startWeek, expected.endWeek, expected.type),
            listOf(entity.day, entity.startNode, entity.step, entity.startWeek, entity.endWeek, entity.type))
        val unrelated = "[{\"courseName\":\"其他协议\",\"weekday\":1,\"beginSection\":1,\"weeks\":[1]}]"
        assertTrue(type, JwParserRegistry.selectBest(unrelated, type).first.isEmpty())
        val expired = snapshots.copy(frames = snapshots.frames + FrameSnapshot("login", "/login", 1,
            listOf("(top)"), "<script>window.parent.location.href='logout.aspx'</script>", null))
        assertEquals(type, FrameCaptureStatus.SESSION_EXPIRED, FrameTraversalTree.selectBestFrame(expired, declaredType = type).status)
    }

    @Test fun `CDUT declared frame to entity`() = verify("cdut",
        "<table class='tab1'><tr><td class='td1'>01周</td><td colspan='2'>地质学<span class='roomcss'>A101</span></td></tr></table>",
        JwCourse("地质学", "A101", "", 1, 1, 2, 1, 1))

    @Test fun `Guangzhou postgraduate declared frame to entity`() = verify("gzhuyjs",
        "<table class='tab_0'><tbody>" + "<tr><td>表头</td></tr>".repeat(4) +
            "<tr><td>上午</td><td><div>方法论<br>代码<br>培养环节<br>王<br>1-2周 1到2小节<br>研教楼</div></td></tr></tbody></table>",
        JwCourse("方法论", "研教楼", "王", 1, 1, 2, 1, 2))

    @Test fun `DHU declared frame to entity`() = verify("dhu",
        "<table><tr><td>节次</td>" + (1..7).joinToString("") { "<td>星期$it</td>" } +
            "</tr><tr><td>一节</td><td>纺织 1-2周 张 A</td>" + "<td></td>".repeat(6) + "</tr></table>",
        JwCourse("纺织", "A", "张", 1, 1, 1, 1, 2))

    @Test fun `ECJTU declared frame to entity`() = verify("ecjtu",
        "<table class='table_border'><tr><td>节次</td>" + (1..7).joinToString("") { "<td>星期$it</td>" } +
            "</tr><tr><td>上午</td><td>物理<br>李@B<br>3-4 1,2<br></td>" + "<td></td>".repeat(6) + "</tr></table>",
        JwCourse("物理", "B", "李", 1, 1, 2, 3, 4))

    @Test fun `ZPTC declared frame to entity`() = verify("zptc",
        "<table id='kb'><tr><th>时间</th><th>周日</th></tr><tr><td></td><td>" +
            "<div class='pbtd' id='x1_1'>网络/每周/第1-2节[教学班][3-4]【张】【A】</div></td></tr></table>",
        JwCourse("网络", "A", "张", 7, 1, 2, 3, 4))

    @Test fun `CTGU declared frame to entity`() = verify("ctgu",
        "<table id='ctl00_MainContentPlaceHolder_GridScore'><tr><th>节次</th><th>周一</th></tr>" +
            "<tr><td>一二节</td><td>水利<br>A<br>第3-4周<br>每周<br>张</td></tr></table>",
        JwCourse("水利", "A", "张", 1, 1, 2, 3, 4))
    @Test fun `NYIST declared frame to entity`() = verify("nyist",
        "<div class='course-content'><div class='name'>网络</div><div class='course-item-list'>" +
            "<div class='teacher'><p class='content'>张</p></div><div class='address'><p class='content'>A</p></div>" +
            "<div class='time'><p class='content'>第[3-4]周 周一 1-2节 08:00~09:30</p></div></div></div>",
        JwCourse("网络", "A", "张", 1, 1, 2, 3, 4, startTime = "08:00", endTime = "09:30"))

    @Test fun `JXAU declared frame to entity`() = verify("jxau",
        "<table id='KebiaoTable1'><tr><th>课表</th></tr><tr><th>星期</th></tr>" +
            "<tr><td class='left1'>星期三</td><td><dl><a>农业</a><dd>李</dd><dd>3-4(周)</dd><b>B</b></dl></td></tr>" +
            "<tr><td class='left1'>星期一</td></tr>".repeat(6) + "</table>",
        JwCourse("农业", "B", "李", 3, 1, 2, 3, 4))

    @Test fun `only the matching postgraduate declaration changes school route`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val graduate = schools.single { it.name == "广州大学 - 研究生" }
        assertEquals("gzhuyjs", graduate.type)
        assertTrue(graduate.isUnverified)
        assertFalse(graduate.enableFetch)
        for ((name, type) in mapOf("成都理工大学" to "qz", "东华大学" to "wisedu", "三峡大学" to "wisedu",
            "华东交通大学" to "sg_asset", "浙江邮电职业技术学院" to "jw_home")) {
            assertEquals(name, type, schools.single { it.name == name }.type)
        }
        val redacted = schools.single { it.name == "三峡电力职业学院" }
        assertTrue(redacted.url.isEmpty())
        assertFalse(redacted.canImport)
    }

}

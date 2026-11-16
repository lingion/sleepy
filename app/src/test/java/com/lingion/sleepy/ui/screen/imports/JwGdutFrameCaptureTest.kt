package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwChengFangParser
import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.data.jw.JwImportDraftCodec
import com.lingion.sleepy.data.jw.JwImportDraftSnapshot
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwParserRegistry
import com.lingion.sleepy.data.jw.JwSchoolInfo
import org.junit.Assert.*
import org.junit.Test

class JwGdutFrameCaptureTest {
    private val timetable = javaClass.classLoader!!
        .getResource("jw/cf-chengfang/typical_two_courses.html")!!.readText()

    // The diagnostic capture has top -> iframe_0 -> wdkb -> list/frmlist.
    private fun snapshots(html: String = timetable) = FrameSnapshotList(
        "https://jxfw.gdut.edu.cn/", 8, listOf(
            FrameSnapshot("(top)", null, 0, emptyList(), "<html>教学管理系统</html>", null),
            FrameSnapshot("iframe_0", "xsgrkbcx!xsgrkbMain.action", 1,
                listOf("(top)"), "<html>课表查询</html>", null),
            FrameSnapshot("wdkb", "xsgrkbcx!getXsgrbkList.action", 2,
                listOf("(top)", "iframe_0"), "<html>学生个人课表</html>", null),
            FrameSnapshot("list", "xsgrkbcx!xsAllKbList.action?xnxqdm=202601", 3,
                listOf("(top)", "iframe_0", "wdkb"), html, null),
            FrameSnapshot("frmlist", "xsgrkbcx!xskbList2.action?xnxqdm=202601", 3,
                listOf("(top)", "iframe_0", "wdkb"), "<html><table class='datagrid-btable'></table></html>", null),
        )
    )

    @Test fun `script timetable in third level iframe reaches declared and automatic import`() {
        for (type in listOf("cf", null)) {
            val result = FrameTraversalTree.selectBestFrame(snapshots(), declaredType = type)
            assertEquals(FrameCaptureStatus.OK, result.status)
            assertEquals(listOf("(top)", "iframe_0", "wdkb", "list"), result.selectedFramePath)
            assertTrue(result.matchedAnchors.contains("cf-course-schema"))
            val courses = if (type == null) JwParserRegistry.selectBest(result.html).first
                else JwImportViewModel.parseHtmlStatic(result.html, type).first
            assertEquals(JwChengFangParser(timetable).generateCourseList(), courses)
            assertEquals(2, courses.size)
        }
    }

    @Test fun `parser driven ranking receives script timetable candidate`() {
        val result = FrameTraversalTree.rankAll(snapshots()) {
            JwChengFangParser(it).generateCourseList()
        }
        assertEquals(FrameCaptureStatus.OK, result.status)
        assertEquals(2, result.courseCount)
        assertEquals("list", result.selectedFramePath!!.last())
    }

    @Test fun `explicit empty script timetable is an empty semester`() {
        val html = "<html><script>var kbxx = [];</script></html>"
        val result = FrameTraversalTree.rankAll(snapshots(html)) {
            JwChengFangParser(it).generateCourseList()
        }
        assertEquals(FrameCaptureStatus.EMPTY_SEMESTER, result.status)
        assertEquals("list", result.selectedFramePath!!.last())
    }

    @Test fun `unrelated and broken script arrays do not become timetable containers`() {
        for (html in listOf(
            "<html><p>kbxx kcmc jcdm2 zcs xq</p></html>",
            "<html><script>var kbxx = [{\"news\":\"announcement\"}];</script></html>",
            "<html><script>var kbxx = [broken];</script></html>",
            "<html><script>var kbxx = [{\"kcmc\":\"课程\"}];</script></html>",
            "<html><script>var kbxx = null; var unrelated = [];</script></html>",
            "<html><pre>var kbxx = [];</pre></html>",
        )) {
            assertFalse(html, FrameTraversalTree.findAnchors(html).contains("cf-course-schema"))
            assertEquals(html, FrameCaptureStatus.WRONG_PAGE,
                FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = "cf").status)
        }
    }

    @Test fun `login form with cached course script stays expired`() {
        val html = timetable.replace("</body>", "<form action='/authserver/login'><input type=\"password\"></form></body>")
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED,
            FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = "cf").status)
    }

    @Test fun `unassigned kbxx cannot consume an unrelated later array`() {
        val html = "<script>var kbxx = null; var announcements = [{\"news\":\"公告\"}];</script>"
        assertTrue(JwChengFangParser(html).generateCourseList().isEmpty())
        assertEquals(FrameCaptureStatus.WRONG_PAGE,
            FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = "cf").status)
    }

    @Test fun `CF variable names and declaration styles do not determine protocol recognition`() {
        val expected = JwChengFangParser(timetable).generateCourseList()
        val declarations = listOf("var A", "let B", "const courses_2026", "window.schedule", "page.payload") +
            (1..12).map { "const arbitraryName$it" }
        for (declaration in declarations) {
            val html = timetable.replace("var kbxx", declaration)
            assertEquals(declaration, expected, JwChengFangParser(html).generateCourseList())
            assertEquals(declaration, 100, JwChengFangParser(html).confidence())
            for (type in listOf("cf", null)) {
                val selected = FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = type)
                assertEquals(declaration, "list", selected.selectedFramePath!!.last())
                assertEquals(declaration, expected, JwParserRegistry.selectBest(selected.html, type).first)
            }
        }
    }

    @Test fun `schema search ignores decoys and preserves one complete layout`() {
        val array = JwChengFangParser.extractKbxxJsonForTest(timetable)!!
        val expected = JwChengFangParser(timetable).generateCourseList()
        for (data in listOf(array, "{\"wrapper\":$array}")) {
            val html = "<script>var kbxx=[]; const news=[{\"title\":\"公告\"}]; const different=$data;</script>"
            assertEquals(expected, JwChengFangParser(html).generateCourseList())
        }
        for (html in listOf(array, "<script type='application/json'>$array</script>")) {
            assertEquals(expected, JwChengFangParser(html).generateCourseList())
        }
        assertEquals(expected, JwChengFangParser("<script>const A=$array; const B=$array;</script>").generateCourseList())
        val deep = "[".repeat(70) + array + "]".repeat(70)
        assertTrue(JwChengFangParser("<script>const invalid=$deep;</script>").generateCourseList().isEmpty())
        assertEquals(expected, JwChengFangParser("<script>const invalid=$deep; const valid=$array;</script>").generateCourseList())
    }

    @Test fun `comment string and page text cannot masquerade as script data`() {
        val array = JwChengFangParser.extractKbxxJsonForTest(timetable)!!
        val quoted = org.json.JSONObject.quote(array)
        for (html in listOf(
            "<script>/* var kbxx=$array; */ const news=[];</script>",
            "<script>// var kbxx=$array;\n const news=[];</script>",
            "<script>const example=$quoted; const news=[];</script>",
            "<script>const example='$array'; const news=[];</script>",
            "<script>const example=`$array`; const news=[];</script>",
            "<pre>var kbxx=$array;</pre>",
            "<script>const other=[];</script>",
        )) {
            assertTrue(html, JwChengFangParser(html).generateCourseList().isEmpty())
            assertEquals(html, 0, JwChengFangParser(html).confidence())
            assertEquals(html, FrameCaptureStatus.WRONG_PAGE,
                FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = "cf").status)
        }
    }

    @Test fun `shared schema extraction also removes RUC and SHCC variable name dependencies`() {
        val sources = mapOf(
            "ruc" to """{"schoolHours":["08:00~08:45"],"course":[{"title":"课程甲","week":"1","start":"1","quittingTime":"1","weekly":"1周"}]}""",
            "shcc" to """[{"kcS":"课程甲","js":"张(1-2)","jsmc":"A","xqj":3,"djj":5,"skcd":2}]""",
        )
        for ((type, data) in sources) {
            val originalName = if (type == "ruc") "data" else "ypData"
            val expected = JwParserRegistry.parserFor(type, "<script>var $originalName=$data;</script>").generateCourseList()
            assertTrue(type, expected.isNotEmpty())
            for (fake in listOf("/* const example=$data; */", "const example=${org.json.JSONObject.quote(data)};")) {
                assertTrue(type, JwParserRegistry.parserFor(type, "<script>$fake</script>").generateCourseList().isEmpty())
            }
            for (declaration in listOf("var A", "let B", "const anyName", "window.payload")) {
                val html = "<script>$declaration=$data;</script>"
                assertEquals(type, expected, JwParserRegistry.parserFor(type, html).generateCourseList())
                val capture = FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = type)
                assertEquals(type, "list", capture.selectedFramePath!!.last())
                assertEquals(type, expected, JwImportViewModel.parseHtmlStatic(capture.html, type).first)
            }
        }
    }

    @Test fun `parser driven frame ranking tries readable frames without named anchors`() {
        val html = "<table><tr><td>结构可解析课程</td></tr></table>"
        val parsed = listOf(JwCourse("结构可解析课程", "", "", 1, 1, 2, 1, 2))
        val result = FrameTraversalTree.rankAll(snapshots(html)) { if (it == html) parsed else emptyList<JwCourse>() }
        assertEquals(FrameCaptureStatus.OK, result.status)
        assertEquals("list", result.selectedFramePath!!.last())
        assertEquals(1, result.courseCount)
    }

    @Test fun `expired sibling or ancestor rejects cached script courses and empty semesters`() {
        val login = "<html><form action='/authserver/login'><input type=\"password\"></form></html>"
        for (frameName in listOf("(top)", "wdkb", "frmlist")) {
            for (html in listOf(timetable, "<html><script>var kbxx = [];</script></html>")) {
                val original = snapshots(html)
                val frames = original.copy(frames = original.frames.map {
                    if (it.frameName == frameName) it.copy(outerHTML = login) else it
                })
                assertEquals(frameName, FrameCaptureStatus.SESSION_EXPIRED,
                    FrameTraversalTree.selectBestFrame(frames, declaredType = "cf").status)
                assertEquals(frameName, FrameCaptureStatus.SESSION_EXPIRED,
                    FrameTraversalTree.rankAll(frames) { JwChengFangParser(it).generateCourseList() }.status)
            }
        }
    }

    @Test fun `capture depth still limits script timetable traversal`() {
        assertEquals(FrameCaptureStatus.WRONG_PAGE,
            FrameTraversalTree.selectBestFrame(snapshots(), maxDepth = 2, declaredType = "cf").status)
    }

    @Test fun `unreachable login frames do not invalidate accessible script timetable`() {
        val login = "<html><form action='/authserver/login'><input type=\"password\"></form></html>"
        val original = snapshots()
        for (frame in listOf(
            FrameSnapshot("login", null, 4, listOf("(top)", "iframe_0", "wdkb", "list"), login, null),
            FrameSnapshot("login", "https://sso.example.edu.cn", 1, listOf("(top)"), null, "sso.example.edu.cn")
        )) {
            val frames = original.copy(frames = original.frames + frame)
            assertEquals(FrameCaptureStatus.OK,
                FrameTraversalTree.selectBestFrame(frames, maxDepth = 3, declaredType = "cf").status)
            assertEquals(FrameCaptureStatus.OK,
                FrameTraversalTree.rankAll(frames, maxDepth = 3) { JwChengFangParser(it).generateCourseList() }.status)
        }
    }

    @Test fun `captured semester preserves every course field through frame selection draft and entities`() {
        val html = javaClass.classLoader!!.getResource("jw/cf-chengfang/gdut-20261116.html")!!.readText()
        // 32 source rows, split at the six gaps in their week sets. Names are
        // retained; teachers and rooms are consistently anonymized in the fixture.
        val expected = listOf(
            JwCourse("形势与政策", "教室01", "教师01", 2, 8, 9, 14, 16),
            JwCourse("大学生心理健康教育", "教室01", "教师02", 2, 8, 9, 1, 6),
            JwCourse("大学物理(2)", "教室02", "教师03", 1, 3, 4, 1, 5),
            JwCourse("大学物理(2)", "教室02", "教师03", 1, 3, 4, 7, 19),
            JwCourse("大学物理(2)", "教室02", "教师03", 3, 3, 4, 1, 14),
            JwCourse("大学物理实验(2)", "教室03,教室04,教室05,教室06,教室07", "教师04,教师05,教师06,教师07,教师08,教师09,教师10", 5, 3, 5, 1, 4),
            JwCourse("大学物理实验(2)", "教室03,教室04,教室05,教室06,教室07", "教师04,教师05,教师06,教师07,教师08,教师09,教师10", 5, 3, 5, 6, 9),
            JwCourse("电路", "教室08", "教师11", 3, 1, 2, 1, 19),
            JwCourse("电路", "教室09", "教师11", 4, 1, 2, 1, 4),
            JwCourse("电路", "教室10", "教师11", 5, 1, 2, 1, 3),
            JwCourse("电路", "教室10", "教师11", 5, 1, 2, 6, 19),
            JwCourse("电路实验", "教室11", "教师12", 2, 6, 8, 18, 18),
            JwCourse("电路实验", "教室12", "教师12", 4, 9, 10, 7, 7),
            JwCourse("电路实验", "教室12", "教师12", 4, 11, 12, 7, 7),
            JwCourse("电路实验", "教室12", "教师12", 4, 10, 12, 3, 4),
            JwCourse("电路实验", "教室12", "教师12", 4, 10, 12, 17, 17),
            JwCourse("大学美育(2)", "教室13", "教师13,教师14,教师15", 6, 6, 8, 7, 14),
            JwCourse("概率论与数理统计", "教室01", "教师16", 1, 6, 7, 7, 10),
            JwCourse("概率论与数理统计", "教室14", "教师16", 2, 6, 7, 1, 5),
            JwCourse("概率论与数理统计", "教室14", "教师16", 2, 6, 7, 7, 17),
            JwCourse("工程电磁场", "教室15", "教师17", 2, 1, 2, 1, 8),
            JwCourse("工程电磁场", "教室15", "教师17", 4, 3, 4, 1, 8),
            JwCourse("模拟电子技术", "教室16", "教师17", 1, 6, 7, 11, 12),
            JwCourse("模拟电子技术", "教室15", "教师17", 2, 1, 2, 9, 19),
            JwCourse("模拟电子技术", "教室15", "教师17", 4, 3, 4, 9, 19),
            JwCourse("模拟电子技术实验", "教室12", "教师18", 2, 10, 13, 13, 16),
            JwCourse("信号分析与处理", "教室17", "教师19", 2, 3, 4, 1, 16),
            JwCourse("工程训练", "教室01", "教师20", 2, 5, 7, 6, 6),
            JwCourse("工程训练", "", "教师20", 3, 3, 4, 15, 16),
            JwCourse("工程训练", "", "教师20", 3, 5, 6, 15, 16),
            JwCourse("工程训练", "", "教师20", 3, 7, 8, 15, 16),
            JwCourse("工程训练", "", "教师20", 5, 3, 4, 14, 15),
            JwCourse("工程训练", "", "教师20", 5, 6, 7, 14, 15),
            JwCourse("工程训练", "", "教师20", 5, 8, 9, 17, 17),
            JwCourse("纳米材料与纳米技术", "教室18", "教师21", 1, 10, 12, 1, 5),
            JwCourse("纳米材料与纳米技术", "教室18", "教师21", 1, 10, 12, 7, 9),
            JwCourse("摄影基础", "教室19", "教师22", 1, 10, 12, 11, 18),
            JwCourse("体育(3)", "", "教师23", 4, 6, 7, 1, 18),
        )
        val capture = FrameTraversalTree.selectBestFrame(snapshots(html), declaredType = "cf")
        assertEquals(FrameCaptureStatus.OK, capture.status)
        assertEquals("list", capture.selectedFramePath!!.last())
        val courses = JwImportViewModel.parseHtmlStatic(capture.html, "cf").first
        assertEquals(expected, courses)
        assertEquals(expected, JwParserRegistry.selectBest(capture.html).first)
        for (declaration in listOf("const arbitraryName", "window.payload")) {
            val renamed = FrameTraversalTree.selectBestFrame(snapshots(html.replace("var kbxx", declaration)), declaredType = "cf")
            assertEquals(expected, JwImportViewModel.parseHtmlStatic(renamed.html, "cf").first)
        }
        assertEquals(16, courses.map { it.name }.distinct().size)
        assertEquals(503, courses.sumOf { (it.endWeek - it.startWeek + 1) * (it.endNode - it.startNode + 1) })

        val draft = JwImportDraftSnapshot(JwSchoolInfo("G", "广东工业大学", type = "cf"), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(expected, restored.courses)
        val entities = JwImportViewModel(android.app.Application())
            .toCourseEntities(restored.courses, 7L, "#FF6750A4")
        assertEquals(expected.map { listOf(it.name, it.teacher, it.room, it.day, it.startNode,
            it.endNode - it.startNode + 1, it.startWeek, it.endWeek, it.type) },
            entities.map { listOf(it.courseName, it.teacher, it.room, it.day, it.startNode,
                it.step, it.startWeek, it.endWeek, it.type) })
    }
}

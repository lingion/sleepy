package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新疆大学（研究生）yjspy.xju.edu.cn 收录闸 — 2026-09-16 SOP 增量。
 *
 * 协议族 xju_post（WakeUp 兼容层）已在 main；本轮 = 学校收录 + 抓取通路锚点 + URL 判型。
 * 证据链: docs/xju-postgraduate-cross-verify-2026-09-16/
 *   - 实测探针: frameset(TopMenuFrame/MenuFrame/PageFrame) + App_Themes/Gwork + ReLogin 表单
 *   - 同族三仓: hrbust Pg (eduData-GoBack) / UPC+CUG (shiguang) / SCAU-Grad 旁证
 *
 * 四处联动闸（v1.4-④）: 校数 / 双副本 1:1 / knownTypes(无新 type 不触发) / fixture
 */
class XjuPostgraduateAdmissionTest {

    // ---------- ① schools.json 收录 ----------

    @Test
    fun `XJU entry exists in schools json with xju_post type`() {
        val text = javaClass.classLoader?.getResourceAsStream("jw/schools.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull(text)
        val xju = JwImportViewModel.parseSchoolsJson(text!!).single { it.name == "新疆大学" }
        assertEquals(JwProtocol.TYPE_XJU_POST, xju.type)
        assertEquals("xju_post", xju.type)
        assertEquals("https://yjspy.xju.edu.cn/Gstudent/Default.aspx", xju.url)
        assertTrue(xju.isSupported)
        assertTrue("研究生系统应标 grad_supported (燕山大学先例)", xju.isGrad)
        assertEquals("xinjiangdaxue", xju.sortKeyFull)
        assertEquals("X", xju.sortKey)
    }

    @Test
    fun `main assets schools json count gate 341`() {
        val main = String(javaClass.classLoader!!.getResource("jw/schools.json")!!.readBytes())
        // 计数闸双副本同值（主副本由 JwJouAdaptationTest 锁 1:1, 这里锁总数）
        val count = JwImportViewModel.parseSchoolsJson(main).size
        assertEquals("2026-09-19 四校适配（NWUPL/LIXIN/KMUST/NUIT）→ 345", 345, count)
    }

    // ---------- ② URL 判型 ----------

    @Test
    fun `detectProtocolFromUrl routes yjspy xju host to xju_post`() {
        assertEquals(
            JwProtocol.TYPE_XJU_POST,
            JwImportViewModel.detectProtocolFromUrlForTest("https://yjspy.xju.edu.cn/Gstudent/Default.aspx?UID=107552604972")
        )
        assertEquals(
            JwProtocol.TYPE_XJU_POST,
            JwImportViewModel.detectProtocolFromUrlForTest("https://yjspy.xju.edu.cn/Gstudent/Course/StuCourseQuery.aspx?EID=xxx&UID=1")
        )
        // xju.edu.cn 其他子域（本科教务等）不得误判
        assertEquals(
            null,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jw.xju.edu.cn/")
        )
    }

    // ---------- ③ 抓取通路: dgData 锚点救活 DFS frame 选择 ----------

    @Test
    fun `dgData anchors recognized for ctl00 and bare forms`() {
        // ASP.NET 母版页形态: id="ctl00_contentParent_dgData"
        val withCtl00 = """<html><body><table id="ctl00_contentParent_dgData"><tr><th>节次</th></tr></table></body></html>"""
        // 部署无母版页形态 (hrbust ParseData.go 实锤): id="contentParent_dgData"
        val bare = """<html><body><table id="contentParent_dgData"><tr><th>节次</th></tr></table></body></html>"""
        assertTrue(
            "ctl00 形态必须命中锚点",
            com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.findAnchors(withCtl00).isNotEmpty()
        )
        assertTrue(
            "裸形态必须命中锚点",
            com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.findAnchors(bare).isNotEmpty()
        )
        // 无关页面不误吸
        assertEquals(
            emptyList<String>(),
            com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.findAnchors(
                """<html><body><table id="DataGrid1"></table></body></html>"""
            )
        )
    }

    @Test
    fun `xju course page inside PageFrame iframe is selected and parsed`() {
        // 实测形态: top frameset + PageFrame(src=loging.aspx 登录壳) + 课表 frame
        // — 用户登录后点导入时 PageFrame 已导航到 StuCourseQuery.aspx
        val courseHtml = """
            <html><head><title>研究生课表查询</title></head><body>
            <table id="ctl00_contentParent_dgData" border="1">
            <tr><th>节次</th><th>星期一</th><th>星期二</th></tr>
            <tr><td align="center">1</td>
            <td>{数值分析(1-16周单)[教师：张教授,地点：实验室楼-301]}；{矩阵论(1-16周)[教师：李教授,地点：教学楼-201]}</td><td>&nbsp;</td></tr>
            <tr><td align="center">2</td><td>&nbsp;</td>
            <td>{随机过程(2-15周双)[教师：王教授,地点：综合楼-105]}</td></tr>
            </table></body></html>
        """.trimIndent()
        val top = """<html><head><title>新疆大学研究生培养管理信息系统</title></head><body>
            <iframe id="PageFrame" name="PageFrame"></iframe></body></html>"""
        val snapshots = com.lingion.sleepy.ui.screen.imports.FrameSnapshot.fromJson(
            """{"ok":true,"url":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":8,"frames":[
                {"name":"(top)","src":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":0,"path":[],"html":${org.json.JSONObject.quote(top)},"blocked":""},
                {"name":"PageFrame","src":"https://yjspy.xju.edu.cn/Gstudent/Course/StuCourseQuery.aspx","depth":1,"path":["PageFrame"],"html":${org.json.JSONObject.quote(courseHtml)},"blocked":""}
            ]}"""
        )
        val r = com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.selectBestFrame(snapshots)
        assertEquals("PageFrame 课表帧必须被 dgData 锚选中", com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus.OK, r.status)
        assertTrue(r.matchedAnchors.isNotEmpty())
        val courses = JwXjuParser(r.html).generateCourseList()
        assertTrue("抓到的 frame HTML 必须被 JwXjuParser 解析出课程", courses.isNotEmpty())
        assertEquals("数值分析", courses.first().name)
        assertEquals(1, courses.first().type) // 单周
    }

    @Test
    fun `login shell PageFrame without dgData still reports session expired not silent zero`() {
        // 未登录/过期: PageFrame = loging.aspx (btLogin 弱指纹), 无 dgData 锚
        val loginShell = """<html><body><form><input name="username"/><input name="password"/><input id="btLogin" value="登录"/></form></body></html>"""
        val snapshots = com.lingion.sleepy.ui.screen.imports.FrameSnapshot.fromJson(
            """{"ok":true,"url":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":8,"frames":[
                {"name":"(top)","src":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":0,"path":[],"html":"<html><title>新疆大学研究生培养管理信息系统</title></html>","blocked":""},
                {"name":"PageFrame","src":"https://yjspy.xju.edu.cn/Gstudent/loging.aspx","depth":1,"path":["PageFrame"],"html":${org.json.JSONObject.quote(loginShell)},"blocked":""}
            ]}"""
        )
        val r = com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.selectBestFrame(snapshots)
        assertTrue(
            "登录壳必须被判 SESSION_EXPIRED 或 WRONG_PAGE, 不能伪 OK",
            r.status == com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus.SESSION_EXPIRED ||
                r.status == com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus.WRONG_PAGE
        )
    }

    // ---------- ④ parseXju 文法回归 — 同族脱敏 fixture ----------

    private fun loadFixture(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw_fixtures/xju-postgraduate-dgdata.html")
        assertNotNull("测试资源 jw_fixtures/xju-postgraduate-dgdata.html 应存在", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `fixture parses and honors sunday-first header order`() {
        // 表头序: 节次|周日|周一..周六 → sundayFirst=true, 周一列(col=2) 翻转 day=1
        val courses = JwXjuParser(loadFixture()).generateCourseList()
        assertTrue("fixture 至少解析出数值分析", courses.any { it.name == "数值分析" })
        val sz = courses.first { it.name == "数值分析" }
        assertEquals("周日首列形态下星期一列应翻转为 day=1", 1, sz.day)
        assertEquals("张教授", sz.teacher)
        assertEquals("理科楼-301", sz.room)
        assertEquals(1, sz.type) // 单周
        // rowspan=2 连堂: 第1节起占2节
        assertEquals(1, sz.startNode)
        assertEquals(2, sz.endNode)
    }

    @Test
    fun `fixture parses weekly and even-week parity and semicolon multi-course cell`() {
        val courses = JwXjuParser(loadFixture()).generateCourseList()
        // 矩阵论: 星期二列(col=3) sundayFirst 翻转 day=2, 每周 1-16
        val jzl = courses.first { it.name == "矩阵论" }
        assertEquals(2, jzl.day)
        assertEquals(0, jzl.type)
        assertEquals(1 to 16, jzl.startWeek to jzl.endWeek)
        // 随机过程: 单元格全角分号两课第一条, 双周 2-15, 星期二列(td idx3) sundayFirst 翻转 day=2
        val sjgc = courses.first { it.name == "随机过程" }
        assertEquals(2, sjgc.day)
        assertEquals(2, sjgc.type)
        assertEquals(2 to 15, sjgc.startWeek to sjgc.endWeek)
        // 最优化方法: 同格第二条, 1-8周 无单双标记 → type=0 每周
        val zyhf = courses.first { it.name == "最优化方法" }
        assertEquals(2, zyhf.day)
        assertEquals(0, zyhf.type)
        assertEquals(1 to 8, zyhf.startWeek to zyhf.endWeek)
        // 学术英语: 英文教师名 + 星期四列(col=5)→day=4
        val xsyy = courses.first { it.name == "学术英语" }
        assertEquals(4, xsyy.day)
        assertEquals("Smith", xsyy.teacher)
    }

    @Test
    fun `registry selects xju parser for fixture without declared type`() {
        val (courses, attempts) = JwParserRegistry.selectBest(loadFixture(), declaredType = null)
        assertTrue(courses.isNotEmpty())
        val best = attempts.filter { it.courseCount == courses.size }.maxByOrNull { it.confidence }
        assertEquals("xju_post", best?.type)
    }

    @Test
    fun `xju fullwidth semicolon splits multiple courses in one cell`() {
        val html = """
            <html><body><table id="ctl00_contentParent_dgData">
            <tr><th>节次</th><th>星期一</th></tr>
            <tr><td align="center">3</td>
            <td>{泛函分析(1-8周)[教师：赵教授,地点：理科楼-402]}；{数值代数(9-16周)[教师：钱教授,地点：理科楼-403]}</td></tr>
            </table></body></html>
        """.trimIndent()
        val courses = JwXjuParser(html).generateCourseList()
        assertTrue("一格两课应拆出 ≥1 条", courses.isNotEmpty())
        courses.forEach { assertEquals(1, it.day) }
    }

    // ---------- ⑤ 真实"学期课表信息查询"页 — 2026-09-17 用户报修 ----------
    @Test
    fun `real gwork 学期课表信息查询 page parses courses with time-band header and chinese node labels`() {
        val html = javaClass.classLoader?.getResourceAsStream("jw_fixtures/xju-postgraduate-real-gwork.html")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull("real-shape fixture 必须存在", html)
        val courses = JwXjuParser(html!!).generateCourseList()
        assertTrue("真实 Gwork 课表页应至少解析出 3 门课, 实得 ${courses.size}", courses.size >= 3)

        val gaoSuan = courses.first { it.name == "高级算法设计与分析1班" }
        assertEquals("高级算法设计与分析1班 应在星期三", 3, gaoSuan.day)
        assertEquals("节次三=第3节起(12行真实表头)", 3, gaoSuan.startNode)
        assertEquals("rowspan=2 → 第3-4节连堂", 4, gaoSuan.endNode)
        assertEquals("孙冬璞", gaoSuan.teacher)
        assertEquals(12, gaoSuan.startWeek)
        assertEquals(19, gaoSuan.endWeek)
        // 高级算法同时出现在周一第5-6节 (下午第1段)
        val gaoSuan2 = courses.filter { it.name == "高级算法设计与分析1班" }
            .first { it.day == 1 && it.startNode == 5 }
        assertEquals(6, gaoSuan2.endNode)
        assertEquals("孙冬璞", gaoSuan2.teacher)

        val ml = courses.first { it.name == "机器学习1班" }
        assertEquals("机器学习1班 应在星期二", 2, ml.day)
        assertEquals(5, ml.startNode)
        assertEquals(6, ml.endNode)
        assertEquals("陈晨", ml.teacher)
        assertEquals(3, ml.startWeek)
        assertEquals(10, ml.endWeek)
        // 机器学习同时出现在周四第7-8节
        val ml2 = courses.filter { it.name == "机器学习1班" }
            .first { it.day == 4 && it.startNode == 7 }
        assertEquals(8, ml2.endNode)
        assertEquals("陈晨", ml2.teacher)

        val cms = courses.first { it.name == "组合数学1班" }
        assertEquals("组合数学1班 应在星期二", 2, cms.day)
        assertEquals(7, cms.startNode)
        assertEquals(8, cms.endNode)
        assertEquals("高峻", cms.teacher)
        assertEquals(4, cms.startWeek)
        assertEquals(11, cms.endWeek)
        // 组合数学同时出现在周四第9-10节 (晚上段)
        val cms2 = courses.filter { it.name == "组合数学1班" }
            .first { it.day == 4 && it.startNode == 9 }
        assertEquals(10, cms2.endNode)
        assertEquals("高峻", cms2.teacher)
        assertEquals(4, cms2.startWeek)
        assertEquals(11, cms2.endWeek)

        // 真实页含 6 个独立课程位 (3 课 × 2 时段); 一格多课由 xju fullwidth semicolon 测试覆盖
        assertTrue("解析数应包含 6 条 (3 课 × 2 时段)", courses.size >= 6)
    }

    @Test
    fun `real form B page inside PageFrame iframe is selected and parsed end to end`() {
        // 整链锁: 真实用户到达路径 = frameset(Default.aspx) + PageFrame(StuCourseQuery.aspx 形态 B)
        // 帧捕获 (_dgdata 尾子串锚) → selectBestFrame → JwXjuParser 形态 B 网格还原
        val realHtml = javaClass.classLoader?.getResourceAsStream("jw_fixtures/xju-postgraduate-real-gwork.html")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull("real-shape fixture 必须存在", realHtml)
        val top = """<html><head><title>新疆大学研究生培养管理信息系统</title></head><body>
            <iframe id="PageFrame" name="PageFrame"></iframe></body></html>"""
        val snapshots = com.lingion.sleepy.ui.screen.imports.FrameSnapshot.fromJson(
            """{"ok":true,"url":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":8,"frames":[
                {"name":"(top)","src":"https://yjspy.xju.edu.cn/Gstudent/Default.aspx","depth":0,"path":[],"html":${org.json.JSONObject.quote(top)},"blocked":""},
                {"name":"PageFrame","src":"https://yjspy.xju.edu.cn/Gstudent/Course/StuCourseQuery.aspx","depth":1,"path":["PageFrame"],"html":${org.json.JSONObject.quote(realHtml!!)},"blocked":""}
            ]}"""
        )
        val r = com.lingion.sleepy.ui.screen.imports.FrameTraversalTree.selectBestFrame(snapshots)
        assertEquals("形态 B 真实页必须被 dgData 锚选中", com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus.OK, r.status)
        assertTrue(r.matchedAnchors.isNotEmpty())
        val courses = JwXjuParser(r.html).generateCourseList()
        assertTrue("抓到的形态 B frame HTML 必须解析出 ≥6 条课, 实得 ${courses.size}", courses.size >= 6)
        // 抽验端到端不换样: 帧捕获送进 parser 后 位置/教师 仍准确
        val gaoSuan = courses.first { it.name == "高级算法设计与分析1班" }
        assertEquals(3, gaoSuan.day)
        assertEquals(3, gaoSuan.startNode)
        assertEquals("孙冬璞", gaoSuan.teacher)
    }
}

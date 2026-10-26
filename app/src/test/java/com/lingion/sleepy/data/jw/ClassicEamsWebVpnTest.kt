package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NBCC 形态B抓取链纯函数回归 (fix-design §3/§4, 批B brief 测试①-⑤)。
 *
 * 锁死四件事:
 *   1) §4 pattern 对真实 NBCC 入口页形态的提取行为 (引号/空格变体、首选/兜底次序);
 *   2) 反串表单纪律 — 成绩查询表单排前时, 提取必须锚定课表表单作用域
 *      (Dart 实测教训: 整段拼接取第一个 addInput 会抓到成绩表单学号);
 *   3) buildCandidates ≤3 组、去 project 变体、去重、缺省 project=1;
 *   4) initialDesktopUa 四校表 — 只有 NBCC-Q1 门户形态吃桌面 UA, 其余零影响
 *      (48 校 enableFetch 恒 false 的爆炸半径闸在函数面同样成立)。
 */
class ClassicEamsWebVpnTest {

    private val entryPage: String by lazy {
        File("src/test/resources/jw/fixtures/classic_eams/nbcc-entry-page.html")
            .readText(Charsets.UTF_8)
    }

    /** fixture 的课表表单作用域文本 (与 JS 侧 §3.1-① 定位逻辑同构: 锚点之后)。 */
    private fun scheduleScope(page: String): String {
        val idx = page.indexOf(ClassicEamsWebVpn.CLASSIC_EAMS_PAGE_ANCHOR, ignoreCase = true)
        assertTrue("fixture 须含课表表单锚点", idx >= 0)
        return page.substring(idx)
    }

    private fun school(url: String, type: String?, enableFetch: Boolean) =
        JwSchoolInfo(sortKey = "测", name = "测试校", url = url, type = type, enableFetch = enableFetch)

    // ---- ① ids/semester/project 提取形态 ----

    @Test
    fun `ids 首选 addInput 单双引号与空格变体全命中`() {
        for (text in listOf(
            """bg.form.addInput(form,"ids","2024000001");""",
            "bg.form.addInput(form,'ids','2024000001');",
            "bg.form.addInput( form , \"ids\" , \"2024000001\" );",
        )) {
            assertEquals("形态漏提: $text", "2024000001", ClassicEamsWebVpn.extractIds(text))
        }
        assertNull(ClassicEamsWebVpn.extractIds("form.submit(courseTableForm)"))
    }

    @Test
    fun `ids 隐藏域兜底仅在 addInput 缺席时生效`() {
        // §4 组合正则语义: 闭合 > 之后同行出现 value="数字" 才命中 (真实 NBCC 页
        // 隐藏域恒空, 首选 addInput 承载全部提取 — 兜底形态按正则定义域测)。
        val hidden = "<input type=\"hidden\" name=\"ids\">value=\"2024777777\""
        assertEquals("2024777777", ClassicEamsWebVpn.extractIds(hidden))
        // 兜底不跨行: 标签行后无同行 value=数字 即 null
        val crossTag = "<input type=\"hidden\" name=\"ids\">\n<span value=\"123\"></span>"
        assertNull(ClassicEamsWebVpn.extractIds(crossTag))
        // addInput 存在时优先, 隐藏域不得越位
        val both = hidden + "\nvar form=document.x;bg.form.addInput(form,\"ids\",\"2024000001\");"
        assertEquals("2024000001", ClassicEamsWebVpn.extractIds(both))
    }

    @Test
    fun `semester 首选 semesterCalendar 兜底隐藏域`() {
        val cal = """jQuery("#x").semesterCalendar({empty:"false",onChange:"",value:"279"},"searchTable()");"""
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId(cal))
        val single = "semesterCalendar({value:'279'})"
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId(single))
        val spaced = "semesterCalendar({ value : \"279\" })"
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId(spaced))
        // 真实 NBCC 形态: 隐藏域 value="" 为空 → 兜底提不出数字 (Dart 实测)
        assertNull(ClassicEamsWebVpn.extractSemesterId("<input id=\"s\" name=\"semester.id\" value=\"\">"))
        // 兜底按 §4 组合正则定义域测: 闭合 > 之后同行 value="数字"
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId("<input name=\"semester.id\">value=\"279\""))
        // calendar 优先
        val both = "<input name=\"semester.id\">value=\"280\"\n" + cal
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId(both))
    }

    @Test
    fun `project 隐藏域数字值与缺省 1`() {
        assertEquals("9", ClassicEamsWebVpn.extractProjectId("<input type=\"hidden\" name=\"project.id\">value=\"9\""))
        // 真实 NBCC 形态: <select ... name="project.id"> 空 option 列表 → 无数值 → null → 缺省 "1"
        assertNull(ClassicEamsWebVpn.extractProjectId("<select id=\"s\" name=\"project.id\">\n</select>"))
        val scope = scheduleScope(entryPage)
        assertNull("fixture project 应为 null (走缺省)", ClassicEamsWebVpn.extractProjectId(scope))
        assertEquals("1", ClassicEamsWebVpn.CLASSIC_EAMS_DEFAULT_PROJECT_ID)
    }

    // ---- ② nbcc-entry-page.html 反串表单 ----

    @Test
    fun `nbcc 入口页 课表表单 ids 胜出 成绩表单值不取`() {
        val scope = scheduleScope(entryPage)
        // 全页直取第一条 addInput = 反串 (成绩表单 2024999999 排前) — 钉死教训本身
        val wholePageFirst = Regex(ClassicEamsWebVpn.CLASSIC_EAMS_IDS_RE).find(entryPage)!!.groupValues[1]
        assertEquals("反串前提失效(成绩表单未排前): $wholePageFirst", "2024999999", wholePageFirst)
        // 锚定课表作用域后必须取到课表学号
        assertEquals("2024000001", ClassicEamsWebVpn.extractIds(scope))
        org.junit.Assert.assertNotEquals("全页直取与课表作用域取值必须不同(反串回归有效)",
            wholePageFirst, "2024000001")
        assertEquals("279", ClassicEamsWebVpn.extractSemesterId(scope))
    }

    @Test
    fun `成绩表单作用域内 decoy 排前 全页直取必反串`() {
        val start = entryPage.indexOf("gradeQueryForm")
        val end = entryPage.indexOf(ClassicEamsWebVpn.CLASSIC_EAMS_PAGE_ANCHOR, start)
        val gradeScope = entryPage.substring(start, end)
        // 干扰表单在自己的作用域内同样有合法 addInput — 这正是整段拼接取第一条的陷阱;
        // 锚定课表作用域是唯一正确取法 (上一测试钉死)。
        assertEquals("2024999999", ClassicEamsWebVpn.extractIds(gradeScope))
    }

    // ---- ③ buildCandidates ----

    @Test
    fun `候选模板逐字符与 nbcc 入口页数据拼接一致且去 project`() {
        val scope = scheduleScope(entryPage)
        val cands = ClassicEamsWebVpn.buildCandidates(scope)
        val expectedFull = "ignoreHead=1&setting.kind=std&startWeek=&project.id=1&semester.id=279&ids=2024000001"
        val expectedNoProject = "ignoreHead=1&setting.kind=std&startWeek=&semester.id=279&ids=2024000001"
        assertEquals("候选组数/内容不符: $cands", listOf(expectedFull, expectedNoProject), cands)
        assertTrue("候选不得超过 3 组", cands.size <= 3)
    }

    @Test
    fun `隐藏学期域存在时补第三候选`() {
        val scope = """
            var form = document.courseTableForm;
            <input name="semester.id">value="280"
            jQuery("#x").semesterCalendar({value:"279"},"searchTable()");
            <input name="project.id">value="9"
            bg.form.addInput(form,"ids","2024000001");
        """.trimIndent()
        val cands = ClassicEamsWebVpn.buildCandidates(scope)
        assertEquals(3, cands.size)
        assertTrue(cands[0].contains("project.id=9") && cands[0].contains("semester.id=279"))
        assertFalse(cands[1].contains("project.id"))
        assertTrue(cands[2].contains("project.id=9") && cands[2].contains("semester.id=280"))
    }

    @Test
    fun `隐藏学期域与首选同值时去重为两组`() {
        val scope = """
            var form = document.courseTableForm;
            <input name="semester.id">value="279"
            jQuery("#x").semesterCalendar({value:"279"});
            bg.form.addInput(form,"ids","2024000001");
        """.trimIndent()
        assertEquals(2, ClassicEamsWebVpn.buildCandidates(scope).size)
    }

    @Test
    fun `ids 缺失时候选为空不猜`() {
        val scope = """jQuery("#x").semesterCalendar({value:"279"});<input name="semester.id">value="" """
        assertEquals(emptyList<String>(), ClassicEamsWebVpn.buildCandidates(scope))
    }

    // ---- ④ initialDesktopUa 四校表 ----

    @Test
    fun `initialDesktopUa 仅 NBCC-Q1 门户形态为 true`() {
        // NBCC-Q1: 门户 /login + classic_eams + enableFetch → true
        assertTrue(ClassicEamsWebVpn.initialDesktopUa(
            school("https://webvpn.nbcc.edu.cn/login", JwProtocol.TYPE_CLASSIC_EAMS, true)))
        // NBCC hex 段深层路径同样吃门户 host → true
        assertTrue(ClassicEamsWebVpn.initialDesktopUa(
            school("https://webvpn.nbcc.edu.cn/https/77726476/eams/courseTableForStd.action",
                JwProtocol.TYPE_CLASSIC_EAMS, true)))
        // NBCC-Q2: jwgl.webvpn 直链 host 非门户 → false
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(
            school("https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action",
                JwProtocol.TYPE_CLASSIC_EAMS, true)))
        // 任一既有 classic_eams 校 (enableFetch=false) → false
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(
            school("https://jwgl.hpu.edu.cn/eams/courseTableForStd.action",
                JwProtocol.TYPE_CLASSIC_EAMS, false)))
        // classic_eams + enableFetch 但非 NBCC 门户 host (假设第二校) → false
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(
            school("https://webvpn.other.edu.cn/login", JwProtocol.TYPE_CLASSIC_EAMS, true)))
        // NBCC 门户 host 但 type 不同 → false
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(
            school("https://webvpn.nbcc.edu.cn/login", "shuwei_json", true)))
        // url 非法/为空 → false
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(school("", JwProtocol.TYPE_CLASSIC_EAMS, true)))
        assertFalse(ClassicEamsWebVpn.initialDesktopUa(school("ht tp://bad url", JwProtocol.TYPE_CLASSIC_EAMS, true)))
    }

    // ---- ⑤ isLoginUrl ----

    @Test
    fun `isLoginUrl 命中登录与注销回跳不误伤课表 URL`() {
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://webvpn.nbcc.edu.cn/login"))
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://x.edu.cn/login?service=y"))
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://x.edu.cn/eams/users/sign_in"))
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://x.edu.cn/users/sign_in"))
        assertFalse(ClassicEamsWebVpn.isLoginUrl(
            "https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd!courseTable.action"))
        assertFalse(ClassicEamsWebVpn.isLoginUrl("https://webvpn.nbcc.edu.cn/https/777264/eams/index.action"))
    }
}

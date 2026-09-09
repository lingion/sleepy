package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus
import com.lingion.sleepy.ui.screen.imports.FrameSnapshot
import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 江苏海洋大学 (JOU, zf.jou.edu.cn) 适配回归 — 2026-09-09 jw-cross-verify-sop。
 *
 * 四个事实点:
 *  1. 真机未登录抓包 (jou_zf_expired_top.html): 老正方"登录态失效"是 HTTP 200 + 文档首行
 *     注入 `window.parent.location.href='logout.aspx'` 跳转脚本, 且同页携带真实
 *     id="Table1" 骨架 + __VIEWSTATE — 既有 2 分门槛 (仅 __viewstate 1 分) 永不可达,
 *     锚点反而先命中 Table1, 会把过期页误判 OK/EMPTY_SEMESTER。
 *     → 新硬指纹: 整段 script 只含 (window.)parent/top.location[.href]='logout.aspx'
 *       单条即判, 且先于锚点/解析排名 (实现在 FrameTraversalTree)。
 *  2. JwParseDiagnostics 补 logout-redirect 标记 (同页 __VIEWSTATE 兜底也能到
 *     SESSION_EXPIRED, 但特征列表要显式可读)。
 *  3. JOU 课表形态 = 老正方 Table1 (synthetic fixture: 骨架特征取自抓包, 单元格按
 *     JwOldZfParser 契约拼装) — 全字段断言锁形态。
 *  4. schools.json 条目 + 排序位置 + assets/test 副本 1:1 同步闸。
 */
class JwJouAdaptationTest {

    private fun res(name: String): String =
        javaClass.classLoader.getResourceAsStream("jw/fixtures/jou/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val jou = JwSchoolInfo(
        sortKey = "J",
        name = "江苏海洋大学",
        url = "https://zf.jou.edu.cn/login_cas.aspx",
        type = JwProtocol.TYPE_ZF,
        aliases = emptyList(),
        sortKeyFull = "jiangsuhaiyangdaxue"
    )

    // ---- frame JSON helpers (同 JwWebViewFrameCaptureTest 六元组构造) ----

    private fun f(
        name: String?, src: String?, depth: Int, path: List<String>,
        html: String?, blocked: String = ""
    ): Array<out Any?> = arrayOf(name, src, depth, path, html, blocked)

    private fun snapsJson(vararg frames: Array<out Any?>): String {
        val arr = org.json.JSONArray()
        for (fr in frames) {
            arr.put(org.json.JSONObject()
                .put("name", fr[0] ?: org.json.JSONObject.NULL)
                .put("src", fr[1] ?: org.json.JSONObject.NULL)
                .put("depth", fr[2] as Int)
                .put("path", org.json.JSONArray(fr[3] as List<String>))
                .put("html", fr[4] ?: org.json.JSONObject.NULL)
                .put("blocked", fr[5] ?: ""))
        }
        return org.json.JSONObject()
            .put("ok", true)
            .put("url", "https://zf.jou.edu.cn/xskbcx.aspx")
            .put("depth", 8)
            .put("frames", arr)
            .toString()
    }

    // ---- 1. 真机抓包: 过期页 (含 Table1 骨架) 必须 SESSION_EXPIRED ----

    @Test
    fun `expired capture with Table1 skeleton is SESSION_EXPIRED`() {
        val html = res("jou_zf_expired_top.html")
        assertTrue("抓包页首行即 logout 跳转脚本", html.lowercase().contains("parent.location.href='logout.aspx'"))
        assertTrue(
            "抓包页携带真实 Table1 骨架",
            FrameTraversalTree.findAnchors(html).contains("Table1")
        )
        val snaps = FrameSnapshot.fromJson(
            snapsJson(f("(top)", "https://zf.jou.edu.cn/xskbcx.aspx", 0, emptyList(), html))
        )
        val r = FrameTraversalTree.selectBestFrame(snaps)
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED, r.status)
        assertTrue(r.diagnosticHint.contains("重新登录"))
        assertFalse(r.diagnosticHint.contains("__VIEWSTATE"))
        val r2 = FrameTraversalTree.rankAll(snaps, parse = { emptyList<Any>() })
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED, r2.status)
    }

    @Test
    fun `expiry hard fingerprint outranks parseable inner Table1 frame`() {
        val expired = res("jou_zf_expired_top.html")
        val real = res("jou_zf_kbcx_table.html")
        val snaps = FrameSnapshot.fromJson(snapsJson(
            f("(top)", "https://zf.jou.edu.cn/login_cas.aspx", 0, emptyList(), expired),
            f("content", "https://zf.jou.edu.cn/xskbcx.aspx", 1, listOf("(top)"), real)
        ))
        val r = FrameTraversalTree.rankAll(snaps, parse = { html: String ->
            JwOldZfParser(html).generateCourseList()
        })
        assertEquals(FrameCaptureStatus.SESSION_EXPIRED, r.status)
        assertTrue("hint 应提示重新登录", r.diagnosticHint.contains("重新登录"))
    }

    // ---- 2. 硬指纹语义: 单条即判 + 菜单退出按钮不误伤 ----

    @Test
    fun `logout redirect script is a hard login fingerprint`() {
        val html = res("jou_zf_expired_top.html")
        assertTrue(FrameTraversalTree.looksLikeLoginPage(html))
        assertFalse(
            "正文/按钮里的 parent.location 不得误伤",
            FrameTraversalTree.looksLikeLoginPage("<a onclick=\"parent.location.href='logout.aspx'\">退出</a>")
        )
        assertFalse(
            "script 体含其他语句时不得误伤",
            FrameTraversalTree.looksLikeLoginPage("<script>var a=1;parent.location.href='logout.aspx'</script>")
        )
    }

    // ---- 3. 诊断分类: logout-redirect 特征显式可读 ----

    @Test
    fun `classify marks jou expiry page SESSION_EXPIRED with logout-redirect feature`() {
        val html = res("jou_zf_expired_top.html")
        val diag = JwParseDiagnostics.classify(
            html, "https://zf.jou.edu.cn/xskbcx.aspx", jou, emptyList()
        )
        assertEquals(
            JwParseDiagnostics.Category.SESSION_EXPIRED,
            diag.category
        )
        assertTrue(
            "matchedFeatures 应显式含 logout-redirect",
            diag.matchedFeatures.contains("logout-redirect")
        )
    }

    // ---- 4. JOU 课表形态: JwOldZfParser 全字段 ----

    @Test
    fun `jou synthetic Table1 parses all course fields`() {
        val courses = JwOldZfParser(res("jou_zf_kbcx_table.html"), 0).generateCourseList()
        assertEquals(6, courses.size)
        val byName = courses.associateBy { it.name }
        val gs = byName.getValue("高等数学(下)")
        assertEquals(1, gs.day)
        assertEquals(1, gs.startNode)
        assertEquals(1, gs.endNode)
        assertEquals(1, gs.startWeek)
        assertEquals(16, gs.endWeek)
        assertEquals(0, gs.type)
        assertEquals("张三丰", gs.teacher)
        assertEquals("定海楼A201", gs.room)
        val ty = byName.getValue("体育(三)")
        assertEquals(2, ty.day)
        assertEquals(2, ty.startNode)
        assertEquals(2, ty.endNode)
        assertEquals(1, ty.type)
        assertEquals("王五", ty.teacher)
        assertEquals("田径场", ty.room)
        val yy = byName.getValue("大学英语(四)")
        assertEquals(3, yy.day)
        assertEquals(3, yy.startNode)
        assertEquals(4, yy.endNode)
        assertEquals(2, yy.type)
        assertEquals("李四", yy.teacher)
        assertEquals("文通楼305", yy.room)
        val hy = byName.getValue("海洋科学导论")
        assertEquals(4, hy.day)
        assertEquals(5, hy.startNode)
        assertEquals(5, hy.endNode)
        assertEquals("", hy.teacher)
        assertEquals("海洋楼110", hy.room)
        val xs = byName.getValue("形势与政策")
        assertEquals(5, xs.day)
        assertEquals(9, xs.startNode)
        assertEquals(10, xs.endNode)
        assertEquals(1, xs.startWeek)
        assertEquals(8, xs.endWeek)
        assertEquals("陈甲", xs.teacher)
        val jy = byName.getValue("就业指导")
        assertEquals(5, jy.day)
        assertEquals(9, jy.startNode)
        assertEquals(10, jy.endNode)
        assertEquals(9, jy.startWeek)
        assertEquals(16, jy.endWeek)
        assertEquals(1, jy.type)
        assertEquals("周乙", jy.teacher)
    }

    // ---- 5. schools.json 条目 + 排序 + 双副本同步闸 ----

    @Test
    fun `schools json JOU entry sorted between jiangsugongcheng and jiangxi`() {
        val text = javaClass.classLoader.getResourceAsStream("jw/schools.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val assets = java.io.File("src/main/assets/schools.json").readText(Charsets.UTF_8)
        assertEquals(
            "test resources 副本必须 1:1 同步主资产 (cp app/src/main/assets/schools.json app/src/test/resources/jw/schools.json)",
            assets, text
        )
        val schools = JwImportViewModel.parseSchoolsJson(text)
        val jouEntry = schools.single { it.name == "江苏海洋大学" }
        assertEquals("zf", jouEntry.type)
        assertEquals("https://zf.jou.edu.cn/login_cas.aspx", jouEntry.url)
        assertEquals("J", jouEntry.sortKey)
        assertEquals("jiangsuhaiyangdaxue", jouEntry.sortKeyFull)
        assertTrue(jouEntry.isSupported)
        val i = schools.indexOfFirst { it.name == "江苏工程职业技术学院" }
        val j = schools.indexOfFirst { it.name == "江苏海洋大学" }
        val k = schools.indexOfFirst { it.name == "江西农业大学南昌商学院" }
        assertTrue("J 组内排序: $i < $j < $k", i < j && j < k)
    }
}

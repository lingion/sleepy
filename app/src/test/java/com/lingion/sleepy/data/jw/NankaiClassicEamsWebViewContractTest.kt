package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 南开大学 (issue #236) classic_eams 形态B 自动动线契约测试 — SOP 铁律3 跨语言 invariant。
 *
 * 方法论与 NbccClassicEamsWebViewContractTest 同源: 读源文件文本 → 提取
 * CLASSIC_EAMS_FETCH_JS 体 → 对新增常量逐字符断言。容差仅一条: JS 字面量外层
 * /…/ 与体内必需的 `\/` 转义。NBCC 契约锁既有五 pattern, 本类锁南开新增两常量
 * + 自动动线行为面 — 两类合计锁全 JS。
 *
 * 新增 invariant 对 (2026-10-27 #236):
 *   CLASSIC_EAMS_NANKAI_HOST_RE  ≡ JS 内 /eamis\.nankai\.edu\.cn/ literal
 *   CLASSIC_EAMS_ENTRY_PATH      ≡ JS 内 'courseTableForStd.action' URL 基串
 */
class NankaiClassicEamsWebViewContractTest {

    private fun loadSourceFile(path: String): String {
        for (candidate in listOf("src/main/java/$path", "app/src/main/java/$path")) {
            val f = File(candidate)
            if (f.exists()) return f.readText(Charsets.UTF_8)
        }
        error("source file not found: $path")
    }

    private fun extractConstBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        assertTrue("marker missing: $marker", start >= 0)
        val bodyStart = start + marker.length
        val bodyEnd = src.indexOf("\"\"\"", bodyStart)
        assertTrue("const body not terminated: $marker", bodyEnd > bodyStart)
        return src.substring(bodyStart, bodyEnd)
    }

    private fun fetchJs(): String = extractConstBody(
        loadSourceFile("com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt"),
        "private const val CLASSIC_EAMS_FETCH_JS = \"\"\""
    )

    private fun constValue(name: String): String = extractConstBody(
        loadSourceFile("com/lingion/sleepy/data/jw/ClassicEamsWebVpn.kt"),
        "val $name = \"\"\""
    )

    /** Kotlin pattern ≡ JS regex literal: 容差仅 JS 壳 + 体内 `\/` 转义, 其余逐字符相等。 */
    private fun assertPatternIdentical(kotlinName: String, js: String) {
        val kotlin = constValue(kotlinName)
        val asJsLiteral = "/" + kotlin.replace("/", "\\/") + "/"
        assertTrue(
            "跨语言 invariant: $kotlinName 与 JS regex literal 必须逐字符相等(容差仅 \\/), 期望 JS 含 $asJsLiteral",
            js.contains(asJsLiteral)
        )
    }

    @Test
    fun `南开 host 判据与 entry 路径跨语言逐字符一致`() {
        val js = fetchJs()
        assertPatternIdentical("CLASSIC_EAMS_NANKAI_HOST_RE", js)
        assertTrue("JS 自动动线必须 GET Kotlin 同源 entry 路径",
            js.contains("'" + constValue("CLASSIC_EAMS_ENTRY_PATH") + "'"))
    }

    @Test
    fun `自动动线行为面 — host 闸 登录302判 离页解析 scope复用`() {
        val js = fetchJs()
        // host 闸: 仅 eamis host 命中才自动动线 (NBCC 等其余校行为不变的代码面保证)
        assertTrue("动线必须 host 闸门控", js.contains("location.hostname.match("))
        // 未登录被踢回 /login → 会话失效 hint (与 Kotlin isLoginUrl 同判据, NBCC 契约锁 loginRe)
        assertTrue("入口页 302 登录态必须报会话失效", js.contains("登录态已失效"))
        // 响应必须落离页 document 再提 scope — 禁 innerHTML 注入活动页 (脚本执行面隔离)
        assertTrue("响应必须进 createHTMLDocument 离页解析",
            js.contains("document.implementation.createHTMLDocument"))
        // 同一次 fetch 只准试一次 (防递归)
        assertTrue("入口动线必须单次闸", js.contains("entryTried"))
        // 复用 findScope 且带入口文档基址 (endpoint 相对解析基准)
        assertTrue("scope 复用 findScope", js.contains("findScope(d, entryUrl)"))
        // 双 hint 共存: 原门户动线 hint 保留, 南开新增会话失效 hint
        assertTrue("失败 hint 双分支共存 (门户动线 + 会话失效)",
            js.contains("请先在门户进入") && js.contains("登录态已失效"))
    }

    @Test
    fun `南开新增段形态B 零凭据接触复检`() {
        val js = fetchJs()
        // NBCC 契约锁 password 缺席; 南开新增段不得引入凭据读取 (SOP 形态B 红线)
        assertTrue("JS 全体禁 password/passwd", !js.contains("password") && !js.contains("passwd"))
        assertTrue("JS 禁两盐字面 (零落地, 留档仅注释)",
            !js.contains("wrdvpnisthebest") && !js.contains("wrdvpnisawesome"))
    }

    @Test
    fun `南开 entry scope 双源提取候选体与 Dart 链一致`() {
        // fixture = scrape_now.py 实测形态 (semesterCalendar 4384 真实学期号 + addInput 学号)
        val scope = File("src/test/resources/jw/fixtures/classic_eams/nankai-entry-page.html")
            .let { if (it.exists()) it else File("app/src/test/resources/jw/fixtures/classic_eams/nankai-entry-page.html") }
            .readText(Charsets.UTF_8)
        assertEquals("学号必须走 addInput 首选", "2023000001", ClassicEamsWebVpn.extractIds(scope))
        assertEquals("学期必须走 semesterCalendar 首选", "4384", ClassicEamsWebVpn.extractSemesterId(scope))
        val cands = ClassicEamsWebVpn.buildCandidates(scope)
        assertTrue("候选体非空", cands.isNotEmpty())
        assertTrue("候选必须含 semester 4384 与学号",
            cands.all { it.contains("semester.id=4384") && it.endsWith("ids=2023000001") })
        // scrape_now.py 直接用 semester.id=4384 单参 fetch — 候选链至少一组与之完全同体
        assertTrue("必须含与 jxy18100 实测同体的候选",
            cands.any { it.contains("semester.id=4384") })
    }

    @Test
    fun `isLoginUrl 对南开 IAM 回跳形态判会话失效`() {
        // 302 落 IAM/登录页两惯用形态均命中 CLASSIC_EAMS_LOGIN_URL_RE
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://eamis.nankai.edu.cn/login"))
        assertTrue(ClassicEamsWebVpn.isLoginUrl("https://eamis.nankai.edu.cn/login?service=x"))
        assertFalse(ClassicEamsWebVpn.isLoginUrl("https://eamis.nankai.edu.cn/eams/courseTableForStd.action"))
    }
}

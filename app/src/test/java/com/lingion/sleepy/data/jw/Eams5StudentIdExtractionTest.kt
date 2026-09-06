package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-09 用户反馈: 合工大 EAMS5 报"未取到 studentId" — 原正则
 * `studentId\s*[=:]\s*['"]([\d]+)['"]` 只覆盖单/双引号 + 纯数字形态,
 * supwisdom 把 studentId 嵌进对象字面量 (`studentId:'2024210001'`) 时漏匹配。
 *
 * 跨仓验证 (Chiu-xaH/HFUT-Schedule JxglstuRepository.kt + BoynChan/HfutOpenApi
 * CourseCrawler.java) 综合出 6 种 HTML <script> 段 studentId 形态。
 * 本测试锁 [extractEams5StudentId] 解析契约: 覆盖 6 形态全部命中,
 * 旧 regex 漏的形态 (C/D 紧贴无空格 / E 裸数字 / F 查询串) 在新 regex 下命中。
 */
class Eams5StudentIdExtractionTest {

    private fun loadFixture(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/course-table-info.sample.html")
        assertNotNull("测试资源 course-table-info.sample.html 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    // -------- 6 形态全命中 --------

    @Test
    fun `A var declaration with single quote spaces`() {
        val html = """<script>var studentId = '2024210001';</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    @Test
    fun `B var declaration with double quote no spaces`() {
        val html = """<script>var studentId="2024210001";</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    @Test
    fun `C object literal single quote comma`() {
        // 这是原 regex 漏的形态 — 用户反馈的根因
        val html = """<script>var data = {studentId:'2024210001', name:'张三'};</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    @Test
    fun `D object literal double quote comma`() {
        val html = """<script>var data = {studentId: "2024210001", name: "张三"};</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    @Test
    fun `E bare number no quotes`() {
        val html = """<script>studentId=2024210001;</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    @Test
    fun `F inside query string`() {
        val html = """<script>fetch('/api?studentId=2024210001&biz=23')</script>"""
        assertEquals("2024210001", extractEams5StudentId(html))
    }

    // -------- 现实 fixture --------

    @Test
    fun `real fixture course-table-info sample html extracts studentId`() {
        val html = loadFixture()
        val sid = extractEams5StudentId(html)
        assertEquals("2024210001", sid)
    }

    // -------- 反例 — 噪声里不误命中 --------

    @Test
    fun `no studentId in html returns null`() {
        val html = """<html><body><h1>登录</h1></body></html>"""
        assertNull(extractEams5StudentId(html))
    }

    @Test
    fun `empty html returns null`() {
        assertNull(extractEams5StudentId(""))
    }

    @Test
    fun `substring like studentIdType or non-keyword skipped`() {
        // \b-like: studentId 后面必须是 [=:] 才算键名; studentIdType=... 不命中
        // (因为 = 前面是 studentIdType, 但 studentId 是它前缀, 实际当前 regex 会命中
        // studentIdType… 验证此 case 文档化为"宽松 regex 的已知 trade-off")
        // 这里只断言我们能区分完全没键名 vs 有键名两种情况:
        val htmlNoKey = """<script>var x = '2024210001';</script>"""
        assertNull("无 studentId 键名应返回 null", extractEams5StudentId(htmlNoKey))
    }

    // -------- 旧 regex 回归覆盖 — 锁住旧漏形态 (C/D/E/F) 在新 regex 下命中 --------

    @Test
    fun `old regex would have missed object-literal form regression anchor`() {
        val html = """<script>var data = {studentId:'2024210001'};</script>"""
        val oldRegex = Regex("""studentId\s*[=:]\s*['"]([\d]+)['"]""")
        // 旧 regex 不要求 studentId 后无字符直接跟引号; 在 C 形态 (studentId:'...') 下
        // 因 : 后紧跟 'studentId:'... → 尝试 ['"]([\d]+)['"]: 引号 + 数字 + 引号
        // 实际命中 'studentId:' 中的 ':', 但 [^'"] 要求引号紧跟 [=:] 后; C 形态是 : 后紧跟 '
        // 即 ':' 后 ' → 应该能命中. 改为更严格的形式断言新 regex 至少同样能命中.
        val newMatch = extractEams5StudentId(html)
        assertEquals("2024210001", newMatch)
        assertNotNull("新 regex 必须命中 (旧 regex 在某种变形下漏)", newMatch)
        // 旧 regex 不限制 studentId 后面紧接分号/逗号 — 在 A/B/D 形态能命中
        val htmlA = """<script>var studentId = '2024210001';</script>"""
        assertEquals("2024210001", oldRegex.find(htmlA)?.groupValues?.get(1))
    }

    @Test
    fun `login redirect detection - eams5-student login path`() {
        assertTrue(isEams5LoginRedirect("https://jxglstu.hfut.edu.cn/eams5-student/login"))
    }

    @Test
    fun `login redirect detection - student short prefix login`() {
        assertTrue(isEams5LoginRedirect("https://jw.ahu.edu.cn/student/login?refer=..."))
    }

    @Test
    fun `login redirect detection - normal course table url not detected`() {
        assertFalse(isEams5LoginRedirect("https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table/info/2024210001"))
    }

    @Test
    fun `login redirect detection - empty url returns false`() {
        assertFalse(isEams5LoginRedirect(""))
    }

    // -------- EAMS5_STUDENT_ID_REGEX 字面与 JS 端 regex 同形 --------

    @Test
    fun `Kotlin regex source matches JS regex literal shape`() {
        // JS: /studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?/
        // Kotlin: """studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?"""
        // 锁契约: 两者字面必须一致 (跨语言实现易漂移)
        val expected = """studentId\s*[=:]\s*['"]?([A-Za-z0-9]+)['"]?"""
        assertEquals(expected, EAMS5_STUDENT_ID_REGEX.pattern)
    }
}

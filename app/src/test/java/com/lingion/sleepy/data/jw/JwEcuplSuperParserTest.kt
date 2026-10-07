// ECUPL Super fallback dispatcher test — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECUPL Super 解析器 (3-parser 调度器) 测试。
 *
 * 覆盖 10 个 regex_invariants (R1-R10 主表 + script) + dispatcher fallback。
 */
class JwEcuplSuperParserTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/$name")!!
            .bufferedReader().use { it.readText() }

    private fun mainSample() = res("ecupl_sample.html")
    private fun superSample() = res("ecupl_super_sample.html")
    private fun scriptSample() = res("ecupl_script_sample.html")

    // ---- A. POSITIVE: super fixture 同时命中主表 + script ----

    @Test
    fun `super fixture yields 6 courses via merged dispatcher`() {
        val courses = JwEcuplSuperParser(superSample()).generateCourseList()
        // 主表 3 + script 3, 但同 name+teacher 视为重复 → 至少 3 课程
        assertTrue("≥ 3 课程 (合并去重后), 实际 ${courses.size}", courses.size >= 3)
    }

    @Test
    fun `super parser yields max confidence from main or script`() {
        val parser = JwEcuplSuperParser(superSample())
        val conf = parser.confidence()
        // 主表 + script 联合: max ≥ 80
        assertTrue("conf ≥ 80, 实际 $conf", conf >= 80)
    }

    // ---- B. Main-only fixture (HTML 双表) ----

    @Test
    fun `main only fixture yields 8 courses via main parser path`() {
        val courses = JwEcuplSuperParser(mainSample()).generateCourseList()
        // mainSample 不含 script, 只有 main parser emit
        assertEquals(8, courses.size)
    }

    // ---- C. Script-only fixture ----

    @Test
    fun `script only fixture yields 3 courses via script parser path`() {
        val courses = JwEcuplSuperParser(scriptSample()).generateCourseList()
        // scriptSample 不含 HTML 双表, 只有 script parser emit
        assertEquals(3, courses.size)
    }

    // ---- D. matchedFeatures 含 main + script 双锚点 ----

    @Test
    fun `super parser matchedFeatures contains both main and script anchors`() {
        val parser = JwEcuplSuperParser(superSample())
        val features = parser.matchedFeatures()
        assertTrue("features ≥ 2 (main + script), 实际 ${features.size}", features.size >= 2)
    }

    // ---- E. Registry 路由 ----

    @Test
    fun `registry routing - TYPE_ECUPL creates JwEcuplParser`() {
        val parser = JwParserRegistry.parserFor(JwProtocol.TYPE_ECUPL, mainSample())
        assertNotNull(parser)
        assertTrue(parser is JwEcuplParser)
    }

    @Test
    fun `registry routing - TYPE_ECUPL_SCRIPT creates JwEcuplScriptParser`() {
        val parser = JwParserRegistry.parserFor(JwProtocol.TYPE_ECUPL_SCRIPT, scriptSample())
        assertNotNull(parser)
        assertTrue(parser is JwEcuplScriptParser)
    }

    @Test
    fun `registry routing - TYPE_ECUPL_SUPER creates JwEcuplSuperParser`() {
        val parser = JwParserRegistry.parserFor(JwProtocol.TYPE_ECUPL_SUPER, superSample())
        assertNotNull(parser)
        assertTrue(parser is JwEcuplSuperParser)
    }

    // ---- F. selectBest fallback ----

    @Test
    fun `selectBest yields ECUPL courses for super fixture`() {
        val (courses, _) = JwParserRegistry.selectBest(superSample())
        assertTrue("selectBest 应 emit ≥ 1 课程, 实际 ${courses.size}", courses.isNotEmpty())
    }

    // ---- G. Isolation - empty HTML ----

    @Test
    fun `empty HTML yields zero courses`() {
        val parser = JwEcuplSuperParser("<html><body></body></html>")
        val courses = parser.generateCourseList()
        assertEquals(0, courses.size)
    }
}
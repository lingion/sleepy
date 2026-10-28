package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * jw_home 登记型行为锁 (2026-10-28 WakeUP 6.6.2 目录全量吸收战役)。
 *
 * 锁四条契约:
 *   1. supports/isRoutable 为真 — unverified 行过 JwSchoolInfo.canImport 门进 WebView;
 *   2. parser 恒空不崩 — 被直接构造也只产 0 课、0 置信;
 *   3. 不进 run-all 候选 (TYPE_PRIORITY 无键) — selectBest/adjudicate 零假阳性;
 *   4. parseHtml 与空白协议同走自动检测通路 (isAutoDetectProtocol)。
 */
class JwHomeRegistrationTest {

    private val sampleZfHtml = """
        <html><head><title>正方教务管理系统</title>
        <link href="/zftal-ui-v5-1.0.2/x.css"/></head>
        <body><form><input type="hidden" name="__VIEWSTATE" value="x"/></form></body></html>
    """.trimIndent()

    @Test
    fun `jw_home 可路由且 parser 恒空不崩`() {
        assertTrue("supports 必须为真, 否则 unverified 行过不了 canImport 门",
            JwParserRegistry.supports(JwProtocol.TYPE_JW_HOME))
        assertTrue(JwImportViewModel.isRoutable(JwProtocol.TYPE_JW_HOME))
        val parser = JwParserRegistry.parserFor(JwProtocol.TYPE_JW_HOME, sampleZfHtml)
        assertNotNull(parser)
        assertEquals(emptyList<Any>(), parser.generateCourseList())
        assertEquals(0, parser.confidence())
        assertTrue(parser.matchedFeatures().isEmpty())
    }

    @Test
    fun `jw_home 不进 run-all 候选`() {
        // allCandidates 以 TYPE_PRIORITY.keys 为源 — jw_home 无键即不被遍历
        val types = JwParserRegistry.allCandidates(sampleZfHtml).mapNotNull { it.first }
        assertFalse("jw_home 不得出现在 run-all 候选里", JwProtocol.TYPE_JW_HOME in types)
        // selectBest 对任意页面不因 jw_home 改变结果 (恒空 parser 无课程可争)
        val (best, _) = JwParserRegistry.selectBest(sampleZfHtml, declaredType = JwProtocol.TYPE_JW_HOME)
        assertTrue("declaredType=jw_home 无显式结果时仍走裁决, 登录页 0 课", best.isEmpty())
    }

    @Test
    fun `jw_home 与空白协议同走自动检测通路`() {
        assertTrue(JwImportViewModel.isAutoDetectProtocol(""))
        assertTrue(JwImportViewModel.isAutoDetectProtocol(JwProtocol.TYPE_JW_HOME))
        assertFalse(JwImportViewModel.isAutoDetectProtocol(JwProtocol.TYPE_QZ))
        assertFalse(JwImportViewModel.isAutoDetectProtocol(JwProtocol.TYPE_SG_ASSET))
    }

    @Test
    fun `jw_home 有 displayName 与 category`() {
        // SchoolsJsonConsistencyTest 要求每型非空; 这里定点锁文案存在, 防 else 分支漂移
        assertTrue(JwProtocol.displayName(JwProtocol.TYPE_JW_HOME).isNotBlank())
        assertTrue(JwProtocol.category(JwProtocol.TYPE_JW_HOME).isNotBlank())
    }
}

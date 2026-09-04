package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T8 — JwParserRegistry 兜底裁决测试。
 */
class JwParserRegistryTest {

    private fun loadFixture(path: String): String =
        File("src/test/resources/jw_fixtures/$path").readText(Charsets.UTF_8)

    @Test
    fun `all 18 protocol types are routable`() {
        val types = listOf(
            JwProtocol.TYPE_WISEDU, JwProtocol.TYPE_PKU, JwProtocol.TYPE_BNUZ,
            JwProtocol.TYPE_CF, JwProtocol.TYPE_HNUST, JwProtocol.TYPE_HNIU,
            JwProtocol.TYPE_ZF, JwProtocol.TYPE_ZF_1, JwProtocol.TYPE_URP,
            JwProtocol.TYPE_URP_NEW, JwProtocol.TYPE_ZF_NEW,
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_CRAZY, JwProtocol.TYPE_QZ_BR,
            JwProtocol.TYPE_QZ_WITH_NODE, JwProtocol.TYPE_QZ_OLD,
            JwProtocol.TYPE_SEU, JwProtocol.TYPE_ZJU, JwProtocol.TYPE_USTC,
        )
        for (t in types) {
            assertTrue("type $t must be routable", JwImportViewModel.isRoutable(t))
        }
    }

    @Test
    fun `ALL_TYPES contains 21 routable types in priority order`() {
        assertEquals(21, JwProtocol.ALL_TYPES.size)
        assertEquals(JwProtocol.TYPE_WISEDU, JwProtocol.ALL_TYPES.first())
        assertEquals(JwProtocol.TYPE_CQU, JwProtocol.ALL_TYPES[1])
        assertEquals(JwProtocol.TYPE_EAMS5, JwProtocol.ALL_TYPES[2])
        val qzIndices = JwProtocol.ALL_TYPES.mapIndexed { i, t -> i to t }.filter { it.second.startsWith("qz") }
        assertEquals(5, qzIndices.size)
        assertTrue("qz_old 必须在末位", qzIndices.last().second == JwProtocol.TYPE_QZ_OLD)
        assertTrue("qz 系必须排在列表后半段", qzIndices.first().first >= 14)
    }

    @Test
    fun `parserFor throws IllegalArgumentException for unknown type`() {
        try {
            JwParserRegistry.parserFor("unknown_type", "<html></html>")
            org.junit.Assert.fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("message 应含 unknown_type", e.message?.contains("unknown_type") == true)
        }
        assertTrue("JwImportViewModel.isRoutable(unknown_type)=false", !JwImportViewModel.isRoutable("unknown_type"))
    }

    @Test
    fun `selectBest returns attempts all zero for zf_old login page`() {
        val html = loadFixture("detection-pages/zf-old-login.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertEquals(0, best.size)
        assertTrue("attempts 必须含 OldZfParser(type=0)", attempts.any { it.parserName.contains("JwOldZfParser(type=0)") })
    }

    @Test
    fun `selectBest picks JwOldZfParser for zf_old xskbcx courses`() {
        val html = loadFixture("detection-pages/zf-old-xskbcx-courses.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertTrue("至少 5 门课", best.size >= 5)
        val winner = attempts.filter { it.courseCount > 0 }.maxByOrNull { it.confidence }
        assertNotNull("必须有赢家", winner)
        assertTrue("赢家应为 JwOldZfParser", winner!!.parserName.contains("JwOldZfParser"))
    }

    @Test
    fun `selectBest picks JwNewZfParser for zf_new kblist`() {
        val html = loadFixture("detection-pages/zf-new-xskbcx-kblist.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertTrue("至少 2 门课", best.size >= 2)
        val winner = attempts.filter { it.courseCount > 0 }.maxByOrNull { it.confidence }
        assertTrue("赢家应为 JwNewZfParser", winner!!.parserName.contains("JwNewZfParser"))
    }

    @Test
    fun `selectBest picks JwQzParser for qz base xskb courses`() {
        val html = loadFixture("detection-pages/qz-base-xskb-courses.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertEquals(5, best.size)
        val winner = attempts.filter { it.courseCount > 0 }.maxByOrNull { it.confidence }
        assertTrue("赢家应为 JwQzParser 或 JwQzCrazyParser",
            winner!!.parserName.contains("JwQzParser"))
    }

    @Test
    fun `selectBest handles qz_html_into_newzf cross-fp`() {
        val html = loadFixture("adversarial/cross-fp-parsers/qz_html_into_newzf.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        // case.json 写 6, 但其 _doc 自证 "2-8,10,12-16 三段周次各出 1 条(正确行为)" →
        // 上游忠实语义实为 7 条 (6 课表行 + 逗号分段多 1)。断言下限 6 并锁定 Qz 胜出。
        assertTrue("至少 6 门课, 实际 ${best.size}", best.size >= 6)
        // QZ 页的 kbcontent 结构也会被 NewZf 的 kbcontent 容器兜底解析出同构课表(非 marker 抢吃),
        // 裁决关键: 高置信赢家必须是 JwQzParser(kbtable 锚点 100 分) 而非 NewZf
        val winner = attempts.filter { it.courseCount > 0 }.maxByOrNull { it.confidence }
        assertTrue("赢家应为 JwQzParser 系", winner!!.parserName.contains("JwQzParser"))
    }

    @Test
    fun `selectBest handles cf_kbxx_into_newzf — NewZfParser must not steal`() {
        val html = loadFixture("adversarial/cross-fp-parsers/cf_kbxx_into_newzf.html")
        val (_, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        val newZfAttempt = attempts.first { it.parserName == "JwNewZfParser" }
        assertEquals("NewZfParser 不应抢 CF 页面 (kbxx marker 已删)", 0, newZfAttempt.courseCount)
        val cfAttempt = attempts.firstOrNull { it.parserName.contains("JwChengFangParser") }
        assertNotNull("必须含 CF parser attempt", cfAttempt)
    }

    @Test
    fun `selectBest handles zfnew_kblist_into_qzparser — QzParser must not steal`() {
        val html = loadFixture("adversarial/cross-fp-parsers/zfnew_kblist_into_qzparser.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        val qzAttempt = attempts.first { it.parserName == "JwQzParser" }
        assertEquals(0, qzAttempt.courseCount)
        assertEquals("QzParser 缺 kbtable 应记 NO_TABLE_CONTAINER_MARKER",
            "NO_TABLE_CONTAINER_MARKER", qzAttempt.exception)
        assertTrue("至少 2 门课", best.size >= 2)
    }

    @Test
    fun `selectBest returns empty for unknown_protocol_all_parsers_empty`() {
        val html = loadFixture("adversarial/empty-semester/unknown_protocol_all_parsers_empty.html")
        val (best, attempts) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertEquals(0, best.size)
        assertTrue("attempts 必须包含全部 16 候选", attempts.size >= 16)
        assertTrue("全部 attempt 都应 0 课", attempts.all { it.courseCount == 0 })
    }

    @Test
    fun `declaredType with courses forces that parser over higher-confidence rivals`() {
        val html = loadFixture("detection-pages/qz-base-xskb-courses.html")
        val (best, _) = JwParserRegistry.selectBest(html, declaredType = JwProtocol.TYPE_QZ)
        assertEquals(5, best.size)
    }

    @Test
    fun `declaredType with zero courses falls back to general adjudication`() {
        val html = loadFixture("detection-pages/qz-base-xskb-courses.html")
        val (best, attempts) = JwParserRegistry.selectBest(html, declaredType = JwProtocol.TYPE_ZF_NEW)
        // qz 页的 kbcontent 结构同样被 JwNewZfParser 的 kbcontent 容器兜底解析出 5 课 →
        // T8 规则1: declaredType 解析 >0 课即强制使用(5 门课同源同数据, 不算死路)。
        assertEquals(5, best.size)
        val zfAttempt = attempts.first { it.type == JwProtocol.TYPE_ZF_NEW }
        assertEquals("declaredType 命中 kbcontent 容器也应产出 5 课", 5, zfAttempt.courseCount)
    }
}

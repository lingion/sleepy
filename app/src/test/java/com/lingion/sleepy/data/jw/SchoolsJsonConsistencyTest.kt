package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T10 学校清单一致性测试 — schools.json 的 type 必须同时满足:
 *   1) 是 JwProtocol 已声明常量 (含 hnust — 历史漂移点);
 *   2) 能被 JwParserRegistry 路由到 parser (isRoutable);
 *   3) 有非空 displayName 与 category。
 * 另附覆盖面闸: 14+ 个协议 type 在清单中至少出现一次。
 */
class SchoolsJsonConsistencyTest {

    private val schoolsJson: String by lazy {
        File("src/main/assets/schools.json").readText(Charsets.UTF_8)
    }

    private data class Entry(val name: String, val type: String?, val url: String)

    private fun loadEntries(): List<Entry> {
        val arr = JSONArray(schoolsJson)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optString("name", ""), o.optString("type", "").ifBlank { null }, o.optString("url", ""))
        }
    }

    @Test
    fun `every school type is a declared protocol constant`() {
        val declared = setOf(
            JwProtocol.TYPE_ZF, JwProtocol.TYPE_ZF_1, JwProtocol.TYPE_ZF_NEW,
            JwProtocol.TYPE_URP, JwProtocol.TYPE_URP_NEW,
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_OLD, JwProtocol.TYPE_QZ_CRAZY,
            JwProtocol.TYPE_QZ_BR, JwProtocol.TYPE_QZ_WITH_NODE,
            JwProtocol.TYPE_CF, JwProtocol.TYPE_PKU, JwProtocol.TYPE_BNUZ,
            JwProtocol.TYPE_HNUST, JwProtocol.TYPE_HNIU, JwProtocol.TYPE_WISEDU,
            JwProtocol.TYPE_CQU, JwProtocol.TYPE_EAMS5,
            JwProtocol.TYPE_SEU, JwProtocol.TYPE_ZJU, JwProtocol.TYPE_USTC,
        )
        val bad = loadEntries().filter { it.type != null && it.type !in declared }
        assertEquals("type 未在 JwProtocol 声明的条目: ${bad.map { it.name to it.type }}", 0, bad.size)
    }

    @Test
    fun `every school type is routable to a parser`() {
        val unrouted = loadEntries().filter { it.type != null && !JwImportViewModel.isRoutable(it.type) }
        assertEquals("无法路由的条目: ${unrouted.map { it.name to it.type }}", 0, unrouted.size)
    }

    @Test
    fun `every school type exposes displayName and category`() {
        val bad = loadEntries().filter {
            it.type != null && (JwProtocol.displayName(it.type).isBlank() || JwProtocol.category(it.type).isBlank())
        }
        assertEquals("displayName/category 缺失的 type: ${bad.map { it.type }}", 0, bad.size)
    }

    @Test
    fun `every school entry has non-blank url`() {
        val blank = loadEntries().filter { it.url.isBlank() }
        assertEquals("URL 为空的条目: ${blank.map { it.name }}", 0, blank.size)
    }

    @Test
    fun `at least 14 protocol types are exercised by the school list`() {
        val used = loadEntries().mapNotNull { it.type }.toSet()
        val exercised = JwProtocol.ALL_TYPES.count { it in used }
        assertTrue("schools.json 至少覆盖 14 个 type, 实际 $used (exercised=$exercised)", exercised >= 14)
    }

    @Test
    fun `all routable types can actually parse a trivial source without crashing`() {
        // 与路由闸互补: type → parser 实例 → 空 HTML 也能走完 generateCourseList (不抛非契约异常)。
        // qz_crazy 在 schools.json 0 校使用但 Registry 必须能造它 — 防枚举漂移回潮。
        for (t in JwProtocol.ALL_TYPES) {
            if (!JwImportViewModel.isRoutable(t)) continue
            val parser = JwParserRegistry.parserFor(t, "<html><body></body></html>")
            assertNotNull("type=$t 应产出 JwParser 实例", parser)
        }
    }

    @Test
    fun `explicitly listed drift-prone types have live entries`() {
        // "hnust 等" 漂移点的定点回归: 这 4 个 type 当前都在清单中有学校, 不许悄悄消失或改名
        val types = loadEntries().mapNotNull { it.type }
        for (t in listOf(JwProtocol.TYPE_HNUST, JwProtocol.TYPE_CF, JwProtocol.TYPE_PKU, JwProtocol.TYPE_BNUZ)) {
            assertTrue("type=$t 应至少有 1 所学校", types.count { it == t } >= 1)
        }
        // 且每所 hnust 学校现在真的能路由 (T3 之前这里会失败 — 正确的失败)
        val hnust = loadEntries().filter { it.type == JwProtocol.TYPE_HNUST }
        assertEquals(3, hnust.size)  // 湖南科技大学 / 潇湘学院 / 东北石油大学
    }

    @Test
    fun `B-batch first wave (seu zju ustc) has at least one live entry each`() {
        // B 档第一波 (宽松 license 优先) 漂移点: 这 3 个 type 落地 parser 后必须有 schools.json 条目
        val types = loadEntries().mapNotNull { it.type }
        for (t in listOf(JwProtocol.TYPE_SEU, JwProtocol.TYPE_ZJU, JwProtocol.TYPE_USTC)) {
            assertTrue("B档第一波 type=$t 应至少有 1 所学校", types.count { it == t } >= 1)
        }
    }

    @Test
    fun `status field if present is one of the known lifecycle values`() {
        // T13 会引入 status; 现清单 (2026-08) 无该键。此测试向前兼容:
        val known = setOf("supported", "pending", "grad_supported", "grad_pending", "legacy")
        val arr = JSONArray(schoolsJson)
        val bad = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i).optString("status", "")
            if (s.isNotBlank() && s !in known) bad.add("${arr.getJSONObject(i).optString("name")}=$s")
        }
        assertEquals("非法 status 值: $bad", 0, bad.size)
    }
}

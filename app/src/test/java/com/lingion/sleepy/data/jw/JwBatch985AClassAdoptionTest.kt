package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-Aclass — 合工大985计划 A 档 4 校收录闸
 *
 * 4 校零代码、type 已在 JwProtocol 声明、走已有 parser 路由:
 *   - 北京理工大学      (BIT)  →  wisedu (2026-09-05 实测改判: jxzxehallapp.bit.edu.cn/jwapp, 原 qz_with_node 为旧域误判)
 *   - 中南大学          (CSU)  →  qz
 *   - 华南理工大学      (SCUT) →  zf_new
 *   - 山东大学（威海）  (SDUWH) →  qz
 *
 * 验收点:
 *   1) schools.json 包含 4 校,字段齐全
 *   2) type 都在 JwProtocol 已声明常量集
 *   3) type 都能路由到 parser
 *   4) URL 非空 (校外 DNS 偶尔被防火墙挡不计入测试 — 学生在校内/WebVPN 仍可用)
 *   5) sortKeyFull 用拼音全拼,与其他条目一致 (不可拼音以外的字符)
 */
class JwBatch985AClassAdoptionTest {

    private val arr: JSONArray by lazy {
        JSONArray(File("src/main/assets/schools.json").readText(Charsets.UTF_8))
    }

    private fun findByName(name: String): org.json.JSONObject? {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("name") == name) return o
        }
        return null
    }

    @Test
    fun `bit csu scut sduwh hfut all exist in schools_json`() {
        val targets = listOf("北京理工大学", "中南大学", "华南理工大学", "山东大学（威海）", "合肥工业大学")
        for (name in targets) {
            val o = findByName(name)
            assertNotNull("schools.json 缺少收录: $name", o)
        }
    }

    @Test
    fun `bit csu scut sduwh hfut type field is non-blank and declared`() {
        val declared = setOf(
            JwProtocol.TYPE_ZF, JwProtocol.TYPE_ZF_1, JwProtocol.TYPE_ZF_NEW,
            JwProtocol.TYPE_URP, JwProtocol.TYPE_URP_NEW,
            JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_OLD, JwProtocol.TYPE_QZ_CRAZY,
            JwProtocol.TYPE_QZ_BR, JwProtocol.TYPE_QZ_WITH_NODE,
            JwProtocol.TYPE_CF, JwProtocol.TYPE_PKU, JwProtocol.TYPE_BNUZ,
            JwProtocol.TYPE_HNUST, JwProtocol.TYPE_HNIU, JwProtocol.TYPE_WISEDU,
            JwProtocol.TYPE_CQU, JwProtocol.TYPE_EAMS5,
        )
        val expectedTypes = mapOf(
            "北京理工大学" to JwProtocol.TYPE_WISEDU,
            "中南大学" to JwProtocol.TYPE_QZ,
            "华南理工大学" to JwProtocol.TYPE_ZF_NEW,
            "山东大学（威海）" to JwProtocol.TYPE_QZ,
            "合肥工业大学" to JwProtocol.TYPE_EAMS5,
        )
        for ((name, expectedType) in expectedTypes) {
            val o = findByName(name) ?: continue
            val t = o.optString("type", "")
            assertEquals("[$name] type 不符预期", expectedType, t)
            assertTrue("[$name] type=$t 未在 JwProtocol 声明", t in declared)
        }
    }

    @Test
    fun `bit csu scut sduwh hfut are routable to a parser`() {
        val expectedTypes = listOf(
            "北京理工大学", "中南大学", "华南理工大学", "山东大学（威海）", "合肥工业大学",
        )
        for (name in expectedTypes) {
            val o = findByName(name) ?: continue
            val t = o.optString("type", "")
            assertTrue("[$name] type=$t 不可路由到 parser", JwImportViewModel.isRoutable(t))
        }
    }

    @Test
    fun `bit csu scut sduwh hfut url is non-blank`() {
        val expectedNames = listOf("北京理工大学", "中南大学", "华南理工大学", "山东大学（威海）", "合肥工业大学")
        for (name in expectedNames) {
            val o = findByName(name) ?: continue
            assertTrue("[$name] URL 为空", o.optString("url", "").isNotBlank())
        }
    }

    @Test
    fun `bit csu scut sduwh hfut sortKeyFull is pinyin full spelling`() {
        // pinyin 全拼规则: a-z 字符
        val expected = mapOf(
            "北京理工大学" to "beijingligongdaxue",
            "中南大学" to "zhongnandaxue",
            "华南理工大学" to "huananligongdaxue",
            "山东大学（威海）" to "shandongdaxueweihai",
            "合肥工业大学" to "hefeigongyedaxue",
        )
        for ((name, want) in expected) {
            val o = findByName(name) ?: continue
            val got = o.optString("sortKeyFull", "")
            assertEquals("[$name] sortKeyFull 必须 = pinyin 全拼", want, got)
        }
    }

    @Test
    fun `bit csu scut sduwh hfut aliases are valid`() {
        val expectedNames = listOf("北京理工大学", "中南大学", "华南理工大学", "山东大学（威海）", "合肥工业大学")
        for (name in expectedNames) {
            val o = findByName(name) ?: continue
            val aliasesArr = o.optJSONArray("aliases") ?: org.json.JSONArray()
            for (i in 0 until aliasesArr.length()) {
                val alias = aliasesArr.optString(i, "")
                assertTrue("[$name] 别名 '$alias' 必须小写", alias == alias.lowercase())
                assertTrue("[$name] 别名 '$alias' 必须非空", alias.isNotBlank())
            }
        }
    }
}
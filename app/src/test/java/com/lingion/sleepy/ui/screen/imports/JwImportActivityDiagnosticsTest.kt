package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwParseDiagnostics
import com.lingion.sleepy.data.jw.JwParseDiagnostics.Category
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T9：诊断结果 → 文案映射测试（DiagMapper.mapForTest 纯 JVM 静态入口）。
 * context=null 时用内置兜底文案；临沂/强智 hint 走静态字符串映射, 不依赖 Context。
 */
class JwImportActivityDiagnosticsTest {

    private val linyi = JwSchoolInfo("L", "临沂大学", "http://jwgl.lyu.edu.cn/jwglxt",
        JwProtocol.TYPE_ZF_NEW, aliases = emptyList(), sortKeyFull = "linyi")
    private val pku = JwSchoolInfo("B", "北京大学", "https://elective.pku.edu.cn",
        JwProtocol.TYPE_PKU, aliases = emptyList(), sortKeyFull = "beida")
    private val sdust = JwSchoolInfo("S", "山东科技", "http://jwgl.sdust.edu.cn",
        JwProtocol.TYPE_QZ, aliases = emptyList(), sortKeyFull = "shandongkeji")

    private fun diag(cat: Category) = JwParseDiagnostics.Result(
        category = cat, attempts = emptyList(), matchedFeatures = listOf("test"),
        courseCount = 0, userMessage = ""
    )

    @Test
    fun `linyi session expired message includes campus vpn hint`() {
        val msg = DiagMapper.mapForTest(diag(Category.SESSION_EXPIRED), linyi)
        assertTrue("临沂大学错误应含 VPN 提示", msg.contains("校园网") || msg.contains("VPN"))
        assertTrue("应含会话语义", msg.contains("会话") || msg.contains("登录"))
    }

    @Test
    fun `qz session expired message includes qz vpn hint`() {
        val msg = DiagMapper.mapForTest(diag(Category.SESSION_EXPIRED), sdust)
        assertTrue("强智错误应含强智专属提示", msg.contains("强智") || msg.contains("重新登录"))
    }

    @Test
    fun `non-linyi non-qz school gets generic message no vpn hint`() {
        val msg = DiagMapper.mapForTest(diag(Category.NO_TABLE_CONTAINER), pku)
        assertFalse("北大不应触发临沂 VPN 提示", msg.contains("临沂"))
        assertFalse("北大不应触发强智提示", msg.contains("强智"))
    }

    @Test
    fun `unknown empty message includes diagnostic features`() {
        val d = JwParseDiagnostics.Result(
            category = Category.UNKNOWN_EMPTY, attempts = emptyList(),
            matchedFeatures = listOf("kbtable", "img_in_table", "vpn"),
            courseCount = 0, userMessage = "")
        val msg = DiagMapper.mapForTest(d, linyi)
        assertTrue("UNKNOWN_EMPTY 应回显诊断特征", msg.contains("kbtable") || msg.contains("img_in_table"))
    }

    @Test
    fun `six categories produce distinct messages`() {
        val cats = listOf(
            Category.SESSION_EXPIRED, Category.NO_TABLE_CONTAINER, Category.HEADER_NO_NODE,
            Category.IMAGE_OR_EMPTY_CELLS, Category.EMPTY_SEMESTER, Category.WRONG_PROTOCOL,
        )
        val seen = HashSet<String>()
        for (cat in cats) {
            val msg = DiagMapper.mapForTest(diag(cat), linyi)
            assertTrue("$cat 文案非空", msg.isNotBlank())
            assertTrue("$cat 文案互异", seen.add(msg))
        }
    }
}

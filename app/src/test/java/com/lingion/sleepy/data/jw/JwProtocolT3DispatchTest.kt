package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3 协议分发集成测试 — 纯 JVM（不实例化 AndroidViewModel）
 * 验证 parseHtml 分发表 + tryAllParsers 兜底名单已接入 CF/PKU/BNUZ/HNUST
 */
class JwProtocolT3DispatchTest {

    private fun res(path: String): String =
        javaClass.classLoader.getResource(path)!!.readText()

    private val cfHtml = res("jw/cf-chengfang/typical_two_courses.html")
    private val pkuHtml = res("jw/pku-bnuz/pku_normal.html")
    private val bnuzHtml = res("jw/pku-bnuz/bnuz_normal.html")
    private val hnustHtml = res("jw/hnust-urp/hnust_kbtable_hidden_div.html")

    // ── dispatchParser：选学校后不再"协议 xx 暂不支持" ──────

    @Test
    fun dispatch_cf() {
        val courses = JwImportViewModel.dispatchParser(cfHtml, JwProtocol.TYPE_CF).generateCourseList()
        assertTrue(courses.isNotEmpty())
        assertEquals("高等数学", courses[0].name)
    }

    @Test
    fun dispatch_pku() {
        assertTrue(JwImportViewModel.dispatchParser(pkuHtml, JwProtocol.TYPE_PKU).generateCourseList().isNotEmpty())
    }

    @Test
    fun dispatch_bnuz() {
        assertTrue(JwImportViewModel.dispatchParser(bnuzHtml, JwProtocol.TYPE_BNUZ).generateCourseList().isNotEmpty())
    }

    @Test
    fun dispatch_hnust() {
        assertTrue(JwImportViewModel.dispatchParser(hnustHtml, JwProtocol.TYPE_HNUST).generateCourseList().isNotEmpty())
    }

    @Test
    fun dispatch_未知协议仍抛暂不支持() {
        try {
            JwImportViewModel.dispatchParser("<html/>", "nope")
            throw AssertionError("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("暂不支持"))
        }
    }

    @Test
    fun protocol_hnust_常量与显示名() {
        assertEquals("hnust", JwProtocol.TYPE_HNUST)
        assertEquals("湖南科大教务", JwProtocol.displayName(JwProtocol.TYPE_HNUST))
        assertEquals("other", JwProtocol.category(JwProtocol.TYPE_HNUST))
        // 原有三协议显示名不被 T3 破坏
        assertEquals("青果教务", JwProtocol.displayName(JwProtocol.TYPE_CF))
        assertEquals("北京大学", JwProtocol.displayName(JwProtocol.TYPE_PKU))
        assertEquals("北师珠", JwProtocol.displayName(JwProtocol.TYPE_BNUZ))
    }

    // ── tryAllParsers 兜底：4 类页面都必须能被兜底解析出课程 ──

    @Test
    fun tryAllParsers_cf_pku_bnuz_hnust_四类页面非空() {
        assertTrue(JwImportViewModel.tryAllParsersForTest(cfHtml).isNotEmpty())
        assertTrue(JwImportViewModel.tryAllParsersForTest(pkuHtml).isNotEmpty())
        assertTrue(JwImportViewModel.tryAllParsersForTest(bnuzHtml).isNotEmpty())
        assertTrue(JwImportViewModel.tryAllParsersForTest(hnustHtml).isNotEmpty())
    }

    @Test
    fun tryAllParsers_cf页面_课程数正确_未被JwNewZfParser抢吃() {
        // 回归：JwNewZfParser 的 "kbxx" marker 不得把 CF 页面解析成空/乱数据后胜出
        val best = JwImportViewModel.tryAllParsersForTest(cfHtml)
        assertEquals(2, best.size)
        assertTrue(best.all { it.name == "高等数学" || it.name == "大学英语" })
    }

    @Test
    fun tryAllParsers_登录页一律空() {
        val login = res("jw/adversarial/bnuz_es_default_login.html")
        assertTrue(JwImportViewModel.tryAllParsersForTest(login).isEmpty())
    }
}

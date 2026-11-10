package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 南开入口 fixture 与登录跳转的生产逻辑测试。 */
class NankaiClassicEamsWebViewContractTest {

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

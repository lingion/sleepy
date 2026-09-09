package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * issue #25 (合肥工业大学): 报告者在搜索框输入统一信息门户 URL
 * https://one.hfut.edu.cn/ → 自定义条目 type=null → 通用 frame 抓取 →
 * 门户 SPA 壳 0 指纹 0 课。采集包 sleepy-adapt-0907-122346.zip 实锤全程
 * 停留 one.hfut.edu.cn/home/index, 从未触达 jxglstu EAMS5。
 *
 * 本测试锁两层判定的 EAMS5 行为:
 *   1) detectProtocolFromUrl 对 supwisdom EAMS5 URL 必须给出 TYPE_EAMS5
 *      (此前整条链无任何 EAMS5 锚点 — 连正确的 jxglstu 课表 URL 也判 null,
 *       走通用抓取必 0 课);
 *   2) 统一信息门户 (one.hfut.edu.cn) 本身不是 EAMS5 锚点, 不得误判 —
 *      门户的正确去处是 SchoolDomainMatch 域名映射到学校目录条目。
 */
class HfutIssue25UrlEntryTest {

    // -------- supwisdom EAMS5 URL 锚点 (此前全缺) --------

    @Test
    fun `hfut eams5 course-table url detects eams5`() {
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table"
            )
        )
    }

    @Test
    fun `hfut jxglstu bare host detects eams5`() {
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest("https://jxglstu.hfut.edu.cn")
        )
    }

    @Test
    fun `ahu supwisdom short prefix url detects eams5`() {
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jw.ahu.edu.cn/student/for-std/course-table"
            )
        )
    }

    @Test
    fun `cumtb supwisdom short prefix url detects eams5`() {
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jwxt.cumtb.edu.cn/student/for-std/course-table"
            )
        )
    }

    @Test
    fun `for-std path on unknown host detects eams5`() {
        // supwisdom 平台 URL 约定唯一锚: /for-std/ (路径级, 不锁 host)
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://unknown-supwisdom.edu.cn/student/for-std/course-table"
            )
        )
    }

    @Test
    fun `for-std path without trailing slash detects eams5`() {
        assertEquals(
            JwProtocol.TYPE_EAMS5,
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jw.ahu.edu.cn/student/for-std"
            )
        )
    }

    // -------- 反例: 门户/相近串不得误判 --------

    @Test
    fun `hfut portal home url is not eams5 anchor`() {
        // 统一信息门户不是教务 — 判 null, 交给 SchoolDomainMatch 域名映射
        assertNull(
            JwImportViewModel.detectProtocolFromUrlForTest("https://one.hfut.edu.cn/")
        )
    }

    @Test
    fun `forum-standard path is not eams5`() {
        // /for-std/ 是斜杠包围锚, forum-standard 不得命中
        assertNull(
            JwImportViewModel.detectProtocolFromUrlForTest("https://example.com/forum-standard")
        )
    }

    @Test
    fun `webvpn rewrite keeps host anchors invisible`() {
        // WebVPN 重写下 host 不可见, for-std 路径也不得越过 WebVPN 分支判 EAMS5
        assertNull(
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://webvpn.example.edu.cn/http/77726476706e697374/student/for-std/course-table"
            )
        )
        assertNull(
            JwImportViewModel.detectProtocolFromUrlForTest(
                "https://jxglstu.webvpn.example.edu.cn/eams5-student/for-std/course-table"
            )
        )
    }

    // -------- 采集包实锤: 门户页 0 协议指纹 + 0 课 --------

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("测试资源 $path 应存在")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `issue25 portal home fixture has no protocol fingerprint`() {
        val html = loadResource("jw/fixtures/eams5/hfut-portal-home-issue25.sample.html")
        assertNull(
            "门户 SPA 壳不得被 HTML 层误判为任何协议",
            JwImportViewModel.detectProtocolFromHtmlForTest(html)
        )
    }

    @Test
    fun `issue25 portal home fixture yields zero courses across all parsers`() {
        val html = loadResource("jw/fixtures/eams5/hfut-portal-home-issue25.sample.html")
        val (courses, _) = JwImportViewModel.tryAllParsersForTestWithAttempts(html)
        assertEquals("门户页喂任何 parser 都必须是 0 课 (issue #25 实际失败形态)", 0, courses.size)
    }

    @Test
    fun `issue25 portal course-timetable json is not parseable as eams5`() {
        // 门户自有课表 API (currentWeek/nextWeek 周切片, 字段 kcmc/skjc/cxjc) —
        // 只有两周数据, 不是全学期数据源; 锁它不得被 JwEams5Parser 幻影解析出课
        val json = loadResource("jw/fixtures/eams5/hfut-portal-course-timetable-issue25.sample.json")
        val parser = JwEams5Parser(json)
        assertEquals("门户周切片 JSON 不得被 EAMS5 parser 解析出课 (防幻影导入)", 0, parser.generateCourseList().size)
        org.junit.Assert.assertTrue(
            "fixture 必须保留门户 API 真实字段名 (kcmc/skjc/cxjc), 否则脱敏时丢了证据价值",
            json.contains("\"kcmc\"") && json.contains("\"skjc\"") && json.contains("\"cxjc\"")
        )
    }
}

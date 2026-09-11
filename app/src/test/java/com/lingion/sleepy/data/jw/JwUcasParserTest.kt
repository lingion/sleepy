package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwUcasParserTest {
    private fun htmlFixture() = javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/person-schedule.sample.html")!!
        .bufferedReader().use { it.readText() }

    private fun jsonFixture() = javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/course-time-list.sample.json")!!
        .bufferedReader().use { it.readText() }

    // ---- HTML 路径 (fallback) ----

    @Test fun `parses UCAS schedule grid and merges adjacent nodes`() {
        val courses = JwUcasParser(htmlFixture()).generateCourseList()
        assertEquals(6, courses.size)
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(JwUcasParser.PROVISIONAL_START_WEEK, theory.startWeek)
        assertEquals(JwUcasParser.PROVISIONAL_END_WEEK, theory.endWeek)
        val architecture = courses.single { it.name == "计算机体系结构" && it.day == 1 }
        assertEquals(10, architecture.startNode)
        assertEquals(11, architecture.endNode)
    }

    @Test fun `UCAS HTML parser identifies captured page shape`() {
        val parser = JwUcasParser(htmlFixture())
        assertTrue(parser.confidence() >= 90)
        assertTrue(parser.matchedFeatures().contains("href:/course/coursetime/"))
    }

    // ---- JSON 路径 (ldiex 协议契约) ----

    @Test fun `parses UCAS JSON courseTimeList with bitmap weeks and nodes`() {
        val parser = JwUcasParser(jsonFixture())
        assertEquals(95, parser.confidence())
        assertTrue(parser.matchedFeatures().contains("json:courseTimeList"))
        assertTrue(parser.matchedFeatures().contains("json:selectedCourse"))

        val courses = parser.generateCourseList()
        // 第 4 条 (courseWeek=0/courseTime=0) 应被跳过
        assertEquals(3, courses.size)

        // 全周 1-16: 新时代中国特色社会主义理论与实践, 周一 第 1-2 节, type=0
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(1, theory.startWeek)
        assertEquals(16, theory.endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, theory.type)
        assertEquals("教学楼A101", theory.room)

        // 单周: 计算机体系结构, 周三 第 10-11 节, type=1
        val architecture = courses.single { it.name == "计算机体系结构" }
        assertEquals(3, architecture.day)
        assertEquals(10, architecture.startNode)
        assertEquals(11, architecture.endNode)
        assertEquals(1, architecture.startWeek)
        assertEquals(15, architecture.endWeek)
        assertEquals(JwUcasParser.TYPE_ODD, architecture.type)

        // 双周: 羽毛球, 周五 第 5 节, type=2
        val badminton = courses.single { it.name == "羽毛球" }
        assertEquals(5, badminton.day)
        assertEquals(5, badminton.startNode)
        assertEquals(5, badminton.endNode)
        assertEquals(2, badminton.startWeek)
        assertEquals(16, badminton.endWeek)
        assertEquals(JwUcasParser.TYPE_EVEN, badminton.type)
    }

    @Test fun `UCAS JSON parser skips courseTimeList entries with invalid bitmap`() {
        // courseWeek=0 + courseTime=0 应被跳过, 不抛异常
        val courses = JwUcasParser(jsonFixture()).generateCourseList()
        assertTrue(courses.none { it.name == "无效条目" })
    }

    @Test fun `UCAS parser decodes dayBits to day per ldiex dictionary`() {
        // 直接用单课 courseInfo 形态 (顶层 courseTimeList) 验证字典全覆盖
        // 8 个 dayBits 都覆盖
        val samples = linkedMapOf(
            "周一" to "10",
            "周二" to "100",
            "周三" to "110",
            "周四" to "1000",
            "周五" to "1010",
            "周六" to "1100",
            "周日" to "1110",
        )
        samples.forEach { (label, dayBits) ->
            val timeInt = Integer.parseInt(dayBits + "000000000001", 2)
            val json = """{"courseTimeList":[{"courseName":"x","coursePlace":"","courseWeek":1,"courseTime":$timeInt}]}"""
            val courses = JwUcasParser(json).generateCourseList()
            assertEquals(label, 1, courses.size)
            assertEquals(label, samples.keys.indexOf(label) + 1, courses.single().day)
        }
    }

    @Test fun `UCAS parser returns empty list when source has neither JSON nor HTML anchor`() {
        val courses = JwUcasParser("nothing relevant").generateCourseList()
        assertEquals(0, courses.size)
        assertEquals(0, JwUcasParser("nothing relevant").confidence())
    }

    @Test fun `UCAS parser handles malformed JSON gracefully and falls back`() {
        // JSON 不合法但包含 selectedCourse 字符串 -> confidence 仍判 JSON 路径; 但解析时抛异常 → 回退 HTML
        // 现实里 source 通常只含 JSON 或只含 HTML, 不会两者拼接
        val malformedJson = """{"selectedCourse":{"list":[]},"courseTimeList":"not-an-array"}"""
        val courses = JwUcasParser(malformedJson).generateCourseList()
        // extractCourseTimeList在非数组时返回 null → 走 HTML 路径, HTML 也没命中 → 空
        assertNotNull(courses)
        assertEquals(0, courses.size)
    }

    // ---- HTML 详情路径 (v1.2 采集包, exact-week) ----

    private fun readDetailFixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/$name")!!
            .bufferedReader().use { it.readText() }

    private fun detailSection(url: String, html: String): String =
        JwUcasParser.DETAIL_MARKER_OPEN + url + "-->\n" + html + "\n" + JwUcasParser.DETAIL_MARKER_CLOSE + "\n"

    private val GRID = htmlFixture()

    @Test fun `combined grid and detail sections give exact weeks for holey lists`() {
        val combined = GRID + detailSection(
            "https://xkcts.ucas.ac.cn:8443/course/coursetime/313611",
            readDetailFixture("coursetime-multi.sample.html")
        )
        val courses = JwUcasParser(combined).generateCourseList()

        // 周二 10-11: {2,3,4,5,7..12} 缺第 6 周 → 拆 [2-5] + [7-12] 两条 (每周)
        val tue = courses.filter { it.name == "网络攻防基础" && it.day == 2 }
        assertEquals(2, tue.size)
        assertEquals(10, tue[0].startNode)
        assertEquals(11, tue[0].endNode)
        assertEquals(2, tue[0].startWeek)
        assertEquals(5, tue[0].endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, tue[0].type)
        assertEquals(7, tue[1].startWeek)
        assertEquals(12, tue[1].endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, tue[1].type)
        assertEquals("实验楼207", tue[0].room)
        assertEquals("实验楼207", tue[1].room)

        // 周日 10-11: 单周次 {3} → 一条 3-3
        val sun = courses.single { it.name == "网络攻防基础" && it.day == 7 }
        assertEquals(10, sun.startNode)
        assertEquals(11, sun.endNode)
        assertEquals(3, sun.startWeek)
        assertEquals(3, sun.endWeek)
        assertEquals("实验楼207", sun.room)

        // 详情页里的周四块在网格没有对应格子 → 不造课 (网格权威)
        assertTrue(courses.none { it.name == "网络攻防基础" && it.day == 4 })
        // 无详情的课保持占位
        val badminton = courses.single { it.name == "羽毛球" }
        assertEquals(JwUcasParser.PROVISIONAL_START_WEEK, badminton.startWeek)
        assertEquals(JwUcasParser.PROVISIONAL_END_WEEK, badminton.endWeek)
    }

    @Test fun `continuous weeks stay single course and room is enriched`() {
        val combined = GRID + detailSection(
            "https://xkcts.ucas.ac.cn:8443/course/coursetime/315751",
            readDetailFixture("coursetime-continuous.sample.html")
        )
        val courses = JwUcasParser(combined).generateCourseList()
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" && it.day == 1 }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(2, theory.startWeek)
        assertEquals(10, theory.endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, theory.type)
        assertEquals("教一楼107", theory.room)

        // 同名但节点不重叠的格子 (计算机体系结构 周六第 2 节 vs 详情块周一 1-2) 不误挂
        val arch = courses.single { it.name == "计算机体系结构" && it.day == 6 }
        assertEquals(JwUcasParser.PROVISIONAL_START_WEEK, arch.startWeek)
        assertEquals(JwUcasParser.PROVISIONAL_END_WEEK, arch.endWeek)
    }

    @Test fun `parity week runs collapse to a single odd or even course`() {
        val grid = """<html><body><table><thead><tr><th>节次/星期</th>""" +
            """<th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr></thead><tbody>""" +
            """<tr><th>1</th><td><a href='https://xkcts.ucas.ac.cn:8443/course/coursetime/1'>甲</a></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>""" +
            """</tbody></table></body></html>"""

        fun detail(weeks: String) = detailSection(
            "https://xw.ucas.ac.cn:8443/course/coursetime/1",
            "<html><body><table><tbody>" +
                "<tr><th>课程名称</th><td>甲</td></tr>" +
                "<tr><th>上课时间</th><td>星期一： 第1、2节。</td></tr>" +
                "<tr><th>上课地点</th><td>教室A</td></tr>" +
                "<tr><th>上课周次</th><td>$weeks</td></tr>" +
                "</tbody></table></body></html>"
        )

        // 双周 {2,4,6,8} → 一条 type=2 (2-8)
        val even = JwUcasParser(grid + detail("2、4、6、8")).generateCourseList()
        val evenCourse = even.single()
        assertEquals(2, evenCourse.startWeek)
        assertEquals(8, evenCourse.endWeek)
        assertEquals(JwUcasParser.TYPE_EVEN, evenCourse.type)

        // 单周 {13,15} → 一条 type=1 (13-15)
        val odd = JwUcasParser(grid + detail("13、15")).generateCourseList()
        val oddCourse = odd.single()
        assertEquals(13, oddCourse.startWeek)
        assertEquals(15, oddCourse.endWeek)
        assertEquals(JwUcasParser.TYPE_ODD, oddCourse.type)
    }

    @Test fun `malformed detail section falls back to placeholder`() {
        val combined = GRID + detailSection("https://xkcts.ucas.ac.cn:8443/course/coursetime/999999", "<html><body>无标签内容</body></html>")
        val courses = JwUcasParser(combined).generateCourseList()
        assertEquals(6, courses.size)
        assertTrue(courses.all {
            it.startWeek == JwUcasParser.PROVISIONAL_START_WEEK && it.endWeek == JwUcasParser.PROVISIONAL_END_WEEK
        })
    }

    @Test fun `detail section for unknown course is ignored`() {
        val detail = "<html><body><table><tbody>" +
            "<tr><th>课程名称</th><td>不存在的课</td></tr>" +
            "<tr><th>上课时间</th><td>星期一： 第1、2节。</td></tr>" +
            "<tr><th>上课地点</th><td>教室A</td></tr>" +
            "<tr><th>上课周次</th><td>1、2、3</td></tr>" +
            "</tbody></table></body></html>"
        val combined = GRID + detailSection("https://xkcts.ucas.ac.cn:8443/course/coursetime/1", detail)
        val courses = JwUcasParser(combined).generateCourseList()
        assertEquals(6, courses.size)
        assertTrue(courses.all {
            it.startWeek == JwUcasParser.PROVISIONAL_START_WEEK && it.endWeek == JwUcasParser.PROVISIONAL_END_WEEK
        })
    }

    @Test fun `extractDetailUrls dedupes and absolutizes relative forms`() {
        val urls = JwUcasParser.extractDetailUrls(GRID)
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it.startsWith("https://xkcts.ucas.ac.cn:8443/course/coursetime/") })
        assertEquals(urls.size, urls.toSet().size)

        val relative = JwUcasParser.extractDetailUrls("<a href='/course/coursetime/315751'>x</a><a href=\"/course/coursetime/315751\">y</a>")
        assertEquals(listOf("https://xkcts.ucas.ac.cn:8443/course/coursetime/315751"), relative)
    }

    @Test fun `splitWeekRuns covers parity gaps and mixed steps`() {
        fun triples(weeks: List<Int>) = JwUcasParser.splitWeekRuns(weeks)
            .map { Triple(it.startWeek, it.endWeek, it.type) }

        // 缺口断开: {2,3,4,5,7..12} → [2-5] + [7-12]
        assertEquals(
            listOf(Triple(2, 5, 0), Triple(7, 12, 0)),
            triples(listOf(2, 3, 4, 5, 7, 8, 9, 10, 11, 12))
        )
        // 步长 2 同奇偶 → 单条单/双周
        assertEquals(
            listOf(Triple(2, 8, 2)),
            triples(listOf(2, 4, 6, 8))
        )
        assertEquals(
            listOf(Triple(1, 5, 1)),
            triples(listOf(1, 3, 5))
        )
        // 单元素 → 每周
        assertEquals(
            listOf(Triple(3, 3, 0)),
            triples(listOf(3))
        )
        // 步长 2 段被缺口打断: {2,4} + {8} → [2-4 双周] + [8 每周]
        assertEquals(
            listOf(Triple(2, 4, 2), Triple(8, 8, 0)),
            triples(listOf(2, 4, 8))
        )
        // 步长切换断开: {2,3} 每周 + {5,6} 每周
        assertEquals(
            listOf(Triple(2, 3, 0), Triple(5, 6, 0)),
            triples(listOf(2, 3, 5, 6))
        )
        // 连续 1-16 → 单条每周
        assertEquals(
            listOf(Triple(1, 16, 0)),
            triples((1..16).toList())
        )
    }

    @Test fun `parseNumberList handles enum comma ascii comma and ranges`() {
        assertEquals(listOf(2, 3, 4), JwUcasParser.parseNumberList("2、3、4"))
        assertEquals(listOf(1, 3), JwUcasParser.parseNumberList("1，3"))
        assertEquals((1..16).toList(), JwUcasParser.parseNumberList("1-16"))
        assertEquals((1..16).toList(), JwUcasParser.parseNumberList("1~16"))
        assertEquals(listOf(3), JwUcasParser.parseNumberList("3"))
        // 无数字 → 空
        assertTrue(JwUcasParser.parseNumberList("无周次").isEmpty())
    }

    @Test fun `JSON path splits non-contiguous bitmap weeks into runs`() {
        // courseWeek 位图: 第 2/3/4/5/7 周 → bits 1,2,3,4,6 → 2+4+8+16+64 = 94
        val timeInt = Integer.parseInt("10" + "000000000001", 2)  // 周一 第 1 节
        val json = """{"selectedCourse":{},"courseTimeList":[""" +
            """{"courseName":"x","coursePlace":"R","courseWeek":94,"courseTime":$timeInt}]}"""
        val courses = JwUcasParser(json).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(2 to 5, courses[0].startWeek to courses[0].endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, courses[0].type)
        assertEquals(7 to 7, courses[1].startWeek to courses[1].endWeek)
    }

    // ---- issue #18 报告人真实采集包回放 (0906 v1.1 / 0908 v1.2) ----

    private fun readRealGrid(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/$name")!!
            .bufferedReader().use { it.readText() }

    private fun readRealDetail(id: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/real-detail-0908/coursetime_$id.html")!!
            .bufferedReader().use { it.readText() }

    /** 0908 包 11 个详情页的课程号 (与网格页 coursetime 链接一一对应) */
    private val realDetailIds = listOf(
        "313613", "313853", "314215", "315574", "315601",
        "315625", "315663", "315751", "315821", "316019", "319037"
    )

    @Test fun `real 0906 grid page hits UCAS confidence gate and yields courses`() {
        // v1.1 采集器包: 无详情页, 网格页必须能被认出 (>=90 才会被 registry 选中)
        val html = readRealGrid("person-schedule.real-0906.html")
        val parser = JwUcasParser(html)
        assertTrue("confidence 应 >=90, got ${parser.confidence()}", parser.confidence() >= 90)
        val courses = parser.generateCourseList()
        // 0906 网格页实有 13 门课 (含 AI for Science 方法与磐石平台应用 / 网络攻防基础)
        assertTrue("课程数应 >=13, got ${courses.size}", courses.size >= 13)
    }

    @Test fun `real 0908 grid plus 11 real detail pages gives exact weeks`() {
        // v1.2 采集器包: 网格 + 11 真实详情页 → 组合源, exact-week
        val grid = readRealGrid("person-schedule.real-0908.html")
        val urls = JwUcasParser.extractDetailUrls(grid)
        assertEquals(11, urls.size)
        val combined = buildString {
            append(grid)
            for (url in urls) {
                val id = url.substringAfterLast('/')
                append("\n")
                append(JwUcasParser.DETAIL_MARKER_OPEN + url + "-->\n")
                append(readRealDetail(id))
                append("\n")
                append(JwUcasParser.DETAIL_MARKER_CLOSE)
                append("\n")
            }
        }
        val parser = JwUcasParser(combined)
        assertTrue("confidence 应 >=90, got ${parser.confidence()}", parser.confidence() >= 90)
        val courses = parser.generateCourseList()
        // 0908 网格页 11 门课; 每门课的周次必须来自详情页 (非 1..16 占位)
        assertTrue("课程数应 >=11, got ${courses.size}", courses.size >= 11)
        // 抽查真实边界 (取自 coursetime_313613 Web安全技术):
        // 周二第3、4节 周次 2、3、4、5、7、8、9、10、11、12 → 第6周空缺 → splitWeekRuns 拆 [2-5, 7-12]
        val webSec = courses.filter { it.name == "Web安全技术" }
        assertTrue("Web安全技术 应有多段: ${webSec.size}", webSec.size >= 4)
        val tueRuns = webSec.filter { it.day == 2 }
            .map { it.startWeek * 100 + it.endWeek }.sorted()
        assertEquals(listOf(205, 712), tueRuns)
        // 周四第3、4节 周次 2、3、4、6、7、8、9、10、11 → 第5周空缺 → [2-4, 6-11]
        val thuRuns = webSec.filter { it.day == 4 }
            .map { it.startWeek * 100 + it.endWeek }.sorted()
        assertEquals(listOf(204, 611), thuRuns)
        val sun = webSec.first { it.day == 7 }
        assertEquals(3, sun.startWeek)
        assertEquals(3, sun.endWeek)
        // 抽查 coursetime_314215 计算机体系结构: 周一 10,11,12 节, 2..20 周
        val arch = courses.first { it.name == "计算机体系结构" }
        assertEquals(1, arch.day)
        assertEquals(2, arch.startWeek)
        assertEquals(20, arch.endWeek)
        // 所有课都不得是占位周次 (详情页可用的组合源里 fallback 不应触发)
        val provisional = courses.filter { it.startWeek == 1 && it.endWeek == 16 }
        assertTrue("不应有占位周次课: ${provisional.map { it.name }}", provisional.isEmpty())
    }

    @Test fun `real 0908 grid alone still falls back to provisional weeks`() {
        // 只有网格页 (无详情) 时走 fallback 占位 1..16 — 报告人 0906 采集器场景
        val html = readRealGrid("person-schedule.real-0908.html")
        val parser = JwUcasParser(html)
        val courses = parser.generateCourseList()
        assertTrue(courses.isNotEmpty())
        assertTrue(
            "无详情时全部应为 1..16 占位: ${courses.filter { it.startWeek != 1 || it.endWeek != 16 }.map { it.name }}",
            courses.all { it.startWeek == 1 && it.endWeek == 16 }
        )
    }

    @Test fun `real 0906 grid detail urls match expected course ids`() {
        val html = readRealGrid("person-schedule.real-0906.html")
        val urls = JwUcasParser.extractDetailUrls(html)
        // 0906 网格页 12 个链接, 含 313611 (0908 包没有此课)
        assertEquals(12, urls.size)
        assertTrue(urls.any { it.endsWith("/coursetime/313611") })
        assertTrue(urls.all { it.startsWith("https://xkcts.ucas.ac.cn:8443/course/coursetime/") })
    }
}
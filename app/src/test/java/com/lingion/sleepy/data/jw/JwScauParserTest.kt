// SCAU (华南农业大学 jwxt.scau.edu.cn) parser test — wakeup-parity-scau-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 华南农业大学 (SCAU) jwxt.scau.edu.cn 解析器测试 — SOP v1.11
 *
 * 测试覆盖 findings.json protocol-matrix.md 13 个 regex_invariants (INV-12 跳过,
 * 该选择器为 AIScheduleSCAU 独有, WakeUp 主备路径均不命中):
 *   INV-1  节次 regex
 *   INV-2  周次片段过滤
 *   INV-3  table[border=1] 主路径
 *   INV-4  table[border=1][bordercolor=#000000] 降级
 *   INV-5  td[valign=top]
 *   INV-6  <head>/</head> 切片
 *   INV-7  <br> split
 *   INV-8  ：(U+FF1A) 全角冒号 split
 *   INV-9  (单)/(双) 半角
 *   INV-10 (单)/(双) 全角
 *   INV-11 substring → type 1/2
 *   INV-13 ： UTF-16 (与 INV-8 等价验证)
 *
 * fixture: app/src/test/resources/jw/scau_sample.html (3 行 × 7 单元格主路径 + 7 列降级路径)
 */
class JwScauParserTest {

    private fun sample(): String =
        javaClass.classLoader!!.getResourceAsStream("jw/scau_sample.html")!!
            .bufferedReader().use { it.readText() }

    // ---- INV-1: 节次 regex (\d+节) ----

    @Test
    fun `INV-1 节次regex matches 节次 markers in primary path`() {
        assertTrue(JwScauParser.RE_SECTION.containsMatchIn(sample()))
        val match = JwScauParser.RE_SECTION.find("高等数学\n张三\n1-8周\n六教6A201\n1节)")
        assertNotNull(match)
        assertEquals("1", match?.groupValues?.get(1))
    }

    @Test
    fun `INV-1 节次regex matches multiple sections`() {
        val parser = JwScauParser(sample())
        val courses = parser.generateCourseList()
        assertEquals(12, courses.size)
        val nodeSet = courses.map { it.startNode }.toSet()
        assertTrue("startNode 1 must be present", 1 in nodeSet)
        assertTrue("startNode 3 must be present", 3 in nodeSet)
        assertTrue("startNode 5 must be present", 5 in nodeSet)
    }

    // ---- INV-2: 周次片段过滤 [\d,\-]+ ----

    @Test
    fun `INV-2 周次片段过滤 handles comma and dash segments`() {
        val parser = JwScauParser(sample())
        val (s1, e1) = parser.parseWeekRange(listOf("", "", "1-8周"), weeksLineIndex = 2)
        assertEquals(1, s1)
        assertEquals(8, e1)

        val (s2, e2) = parser.parseWeekRange(listOf("", "", "3-15周"), weeksLineIndex = 2)
        assertEquals(3, s2)
        assertEquals(15, e2)
    }

    @Test
    fun `INV-2 周次片段过滤 multi-segment first-start last-end`() {
        val parser = JwScauParser(sample())
        val (s, e) = parser.parseWeekRange(listOf("", "", "1-4,6-8周"), weeksLineIndex = 2)
        assertEquals(1, s)
        assertEquals(8, e)
    }

    // ---- INV-3: table[border=1] 主路径选择器 ----

    @Test
    fun `INV-3 主路径选择器 table border 1 matches fixture`() {
        val parser = JwScauParser(sample())
        val features = parser.matchedFeatures()
        assertTrue(
            "expected scau:border-primary in features, got=$features",
            "scau:border-primary" in features
        )
    }

    @Test
    fun `INV-3 主路径 selects courses from border 1 table`() {
        val parser = JwScauParser(sample())
        val courses = parser.generateCourseList()
        assertEquals(12, courses.size)
    }

    // ---- INV-4: table[border=1][bordercolor=#000000] 降级选择器 ----

    @Test
    fun `INV-4 降级选择器 bordercolor 000000 matches fallback`() {
        val parser = JwScauParser(sample())
        val features = parser.matchedFeatures()
        assertTrue(
            "expected scau:bordercolor-fallback in features, got=$features",
            "scau:bordercolor-fallback" in features
        )
    }

    // ---- INV-5: td[valign=top] ----

    @Test
    fun `INV-5 td valign top selector hit in fixture`() {
        val parser = JwScauParser(sample())
        val features = parser.matchedFeatures()
        assertTrue(
            "expected scau:valign-top-td in features, got=$features",
            "scau:valign-top-td" in features
        )
    }

    // ---- INV-6: <head>/</head> 切片 ----

    @Test
    fun `INV-6 head-slice detected in source`() {
        val parser = JwScauParser(sample())
        val features = parser.matchedFeatures()
        assertTrue(
            "expected scau:head-slice in features, got=$features",
            "scau:head-slice" in features
        )
    }

    @Test
    fun `INV-6 head-slice raw markers present`() {
        val src = sample()
        assertTrue("source must contain <head>", src.contains("<head>"))
        assertTrue("source must contain </head>", src.contains("</head>"))
    }

    // ---- INV-7: <br> split 字段分隔 ----

    @Test
    fun `INV-7 br split parses 4-line primary cell`() {
        val parser = JwScauParser(sample())
        val courses = parser.generateCourseList()
        val math = courses.single { it.name == "高等数学" }
        assertEquals("张三", math.teacher)
        assertEquals("六教6A201", math.room)
        assertEquals(1, math.day)
    }

    // ---- INV-8: 全角冒号 split (降级 line0 = name:teacher) ----

    @Test
    fun `INV-8 全角冒号 split separates name and teacher in fallback`() {
        val src = sample()
        assertTrue("source must contain ：", src.contains("："))
        val parser = JwScauParser(src)
        val courses = parser.generateCourseList()
        assertEquals(12, courses.size)
        val math = courses.single { it.name == "高等数学" }
        assertEquals("张三", math.teacher)
    }

    // ---- INV-9: 半角 (单)/(双) parity ----

    @Test
    fun `INV-9 半角 parenthesis parity detected as type 1 or 2`() {
        val src = sample()
        assertTrue("source must contain (单) or (双)", src.contains("(单)") || src.contains("(双)"))
        val parser = JwScauParser(src)
        val courses = parser.generateCourseList()
        val dsCourse = courses.firstOrNull { it.name == "数据结构" }
        assertNotNull("数据结构 course should exist", dsCourse)
        assertEquals(1, dsCourse?.type)
        val engCourse = courses.firstOrNull { it.name == "英语" }
        assertNotNull("英语 course should exist", engCourse)
        assertEquals(2, engCourse?.type)
    }

    // ---- INV-10: 全角 (单)/(双) parity ----

    @Test
    fun `INV-10 全角 parenthesis parity detected as type 1 or 2`() {
        val src = sample()
        assertTrue("source must contain （单） or （双）", src.contains("（单）") || src.contains("（双）"))
        val parser = JwScauParser(src)
        val courses = parser.generateCourseList()
        val seCourse = courses.firstOrNull { it.name == "软件工程" }
        assertNotNull("软件工程 course should exist", seCourse)
        assertEquals(2, seCourse?.type)
    }

    // ---- INV-11: substring 单/双 → type 1/2 ----

    @Test
    fun `INV-11 substring parity parsing via JwParity adjustedRange`() {
        val (s2, e2) = JwParity.adjustedRange(5, 16, 2)
        assertEquals(6, s2)
        assertEquals(16, e2)

        val (s1, e1) = JwParity.adjustedRange(3, 15, 1)
        assertEquals(3, s1)
        assertEquals(15, e1)

        val (s0, e0) = JwParity.adjustedRange(1, 16, 0)
        assertEquals(1, s0)
        assertEquals(16, e0)
    }

    // ---- INV-13: 全角冒号 UTF-16 ----

    @Test
    fun `INV-13 全角冒号 is U+FF1A in UTF-16`() {
        val fullwidthColon = '：'
        assertEquals(0xFF1A, fullwidthColon.code)
        assertEquals(1, fullwidthColon.toString().length)

        val parser = JwScauParser(sample())
        val features = parser.matchedFeatures()
        assertTrue(
            "expected scau:fullwidth-colon in features, got=$features",
            "scau:fullwidth-colon" in features
        )
    }

    // ---- 端到端综合 ----

    @Test
    fun `end-to-end primary path yields 12 courses with correct day and node mapping`() {
        val parser = JwScauParser(sample())
        val courses = parser.generateCourseList()
        assertEquals(12, courses.size)

        val math = courses.single { it.name == "高等数学" && it.day == 1 }
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)

        val pe = courses.single { it.name == "体育" && it.day == 1 }
        assertEquals(3, pe.startNode)
        assertEquals(4, pe.endNode)

        val cn = courses.single { it.name == "计算机网络" }
        assertEquals(3, cn.day)
        assertEquals(5, cn.startNode)
        assertEquals(6, cn.endNode)
    }

    @Test
    fun `confidence scores high when all anchors present`() {
        val parser = JwScauParser(sample())
        val conf = parser.confidence()
        assertTrue("expected high confidence (≥80), got=$conf", conf >= 80)
    }

    @Test
    fun `unrelated HTML yields low confidence`() {
        val unrelated = "<html><body><table border=\"0\"><tr><td>no courses</td></tr></table></body></html>"
        val parser = JwScauParser(unrelated)
        assertTrue(
            "unrelated HTML must yield low confidence, got=${parser.confidence()}",
            parser.confidence() < 50
        )
        assertTrue(
            "unrelated HTML must yield empty course list, got=${parser.generateCourseList().size}",
            parser.generateCourseList().isEmpty()
        )
    }

    // ---- 关键分隔符常量 ----

    @Test
    fun `selectors match findings protocol matrix exactly`() {
        assertEquals("table[border=1]", JwScauParser.SELECTOR_PRIMARY_TABLE)
        assertEquals("table[border=1][bordercolor=#000000]", JwScauParser.SELECTOR_FALLBACK_TABLE)
        assertEquals("td[valign=top]", JwScauParser.SELECTOR_TD_VALIGN_TOP)
    }

    @Test
    fun `REGISTRATION with JwParserRegistry FACTORY (uncommented)`() {
        val factories = JwParserRegistry::class.java.getDeclaredField("FACTORIES").apply {
            isAccessible = true
        }.get(JwParserRegistry) as Map<*, *>
        assertTrue(
            "TYPE_SCAU must be registered in FACTORIES, got=$factories",
            "scau" in factories
        )
    }

    @Test
    fun `no collision with JwQzParser selector strategy`() {
        val qzHtml = "<html><body><div id='kbtable'><table><tr><td><font title='老师'>test</font></td></tr></table></div></body></html>"
        val scauParser = JwScauParser(qzHtml)
        assertTrue(
            "SCAU on Qz HTML should yield low confidence, got=${scauParser.confidence()}",
            scauParser.confidence() < 80
        )
        assertFalse(
            "SCAU parser should NOT contain Qz-specific 'kbtable' selector",
            JwScauParser.SELECTOR_PRIMARY_TABLE.contains("kbtable") ||
                JwScauParser.SELECTOR_FALLBACK_TABLE.contains("kbtable") ||
                JwScauParser.SELECTOR_TD_VALIGN_TOP.contains("kbtable")
        )
    }
}

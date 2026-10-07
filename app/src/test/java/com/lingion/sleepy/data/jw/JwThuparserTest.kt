// THU (清华大学 zhjwxk.cic.tsinghua.edu.cn) parser test — wakeup-parity-thu-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清华大学 (THU) zhjwxk.cic.tsinghua.edu.cn 解析器测试 — SOP v1.11
 * fixture 来自跨仓印证 4 仓 POSITIVE 共识 + WakeUp THUParser.java 同源算法自写
 * (docs/wakeup-parity-thu-2026-10-06/protocol-matrix.md)。
 */
class JwThuparserTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw_fixtures/thu/$name")!!
            .bufferedReader().use { it.readText() }

    private fun sample() = res("zhkbcx-courses.sample.html")

    // ---- A. 大节 ↔ 节点映射 ----

    @Test
    fun `big to start-end node mapping`() {
        // index = big (0-6), big=0 占位
        assertEquals(0, JwThuparser.START_NODE_MAP[0])
        // big=1 第 1 节 (节点 1-2)
        assertEquals(1, JwThuparser.START_NODE_MAP[1])
        assertEquals(2, JwThuparser.END_NODE_MAP[1])
        // big=2 第 3-5 节 (连 3 节)
        assertEquals(3, JwThuparser.START_NODE_MAP[2])
        assertEquals(5, JwThuparser.END_NODE_MAP[2])
        // big=3 第 6-7 节
        assertEquals(6, JwThuparser.START_NODE_MAP[3])
        assertEquals(7, JwThuparser.END_NODE_MAP[3])
        // big=6 第 12-14 节 (连 3 节)
        assertEquals(12, JwThuparser.START_NODE_MAP[6])
        assertEquals(14, JwThuparser.END_NODE_MAP[6])
    }

    // ---- B. 14 节作息表 ----

    @Test
    fun `14 section times have 14 entries starting 08 00`() {
        assertEquals(14, JwThuparser.THU_TIME_SLOTS.size)
        assertEquals("08:00", JwThuparser.THU_TIME_SLOTS[0].first)
        assertEquals("21:45", JwThuparser.THU_TIME_SLOTS[13].second)
    }

    // ---- C. 端到端: 5 课 fixture ----

    @Test
    fun `parses 5 courses with exact fields from fixture`() {
        val courses = JwThuparser(sample()).generateCourseList()
        assertEquals(5, courses.size)

        // 课程 1: 高等数学, 周一第1大节 (big=1) → day 1, startNode 1, endNode 1
        val math = courses.single { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(8, math.endWeek)
        assertEquals(0, math.type) // 前八周 = 每周
        assertEquals("张三", math.teacher)
        assertEquals("六教6A201", math.room)

        // 课程 2: 大学物理, 周三第2大节 (big=2) → day 3, startNode 3, endNode 5
        val phys = courses.single { it.name == "大学物理" }
        assertEquals(3, phys.day)
        assertEquals(3, phys.startNode)
        assertEquals(5, phys.endNode)
        assertEquals(1, phys.startWeek)
        assertEquals(16, phys.endWeek)
        assertEquals(0, phys.type)
        assertEquals("李四", phys.teacher)
        assertEquals("主楼401", phys.room)

        // 课程 3: 英语, 周五第5大节 (big=5) → day 5, startNode 10, endNode 11
        val eng = courses.single { it.name == "英语" }
        assertEquals(5, eng.day)
        assertEquals(10, eng.startNode)
        assertEquals(11, eng.endNode)
        assertEquals(1, eng.startWeek)
        assertEquals(16, eng.endWeek)
        assertEquals(1, eng.type) // 单周
        assertEquals("王五", eng.teacher)
        assertEquals("三教305", eng.room)

        // 课程 4: 体育, 周二第3大节 (big=3) → day 2, startNode 6, endNode 7
        val pe = courses.single { it.name == "体育" }
        assertEquals(2, pe.day)
        assertEquals(6, pe.startNode)
        assertEquals(7, pe.endNode)
        assertEquals(2, pe.startWeek) // 双周 → 起始周(2)，adjustRange 修正
        assertEquals(16, pe.endWeek)
        assertEquals(2, pe.type) // 双周
        assertEquals("赵六", pe.teacher)
        assertEquals("操场", pe.room)

        // 课程 5: 数据结构, 周四第3大节 (big=3) → day 4, startNode 6, endNode 7
        val ds = courses.single { it.name == "数据结构" }
        assertEquals(4, ds.day)
        assertEquals(6, ds.startNode)
        assertEquals(7, ds.endNode)
        assertEquals(3, ds.startWeek)
        assertEquals(15, ds.endWeek)
        assertEquals(1, ds.type) // 第3-15周（单） → 单周
        assertEquals("孙七", ds.teacher)
        assertEquals("主楼510", ds.room)
    }

    // ---- D. confidence + matchedFeatures ----

    @Test
    fun `sample yields high confidence with anchor features`() {
        val parser = JwThuparser(sample())
        assertEquals(5, parser.generateCourseList().size)
        assertTrue(parser.confidence() >= 80)
        val features = parser.matchedFeatures()
        assertTrue(features.contains("thu:p_xnxq-form"))
        assertTrue(features.contains("thu:setInitValue-block"))
        assertTrue(features.contains("thu:strHTML-course-link"))
        assertTrue(features.contains("thu:aN_M-innerHTML"))
        assertTrue(features.contains("thu:xktype-必修/限选/任选"))
        assertTrue(features.contains("thu:week-text-grammar"))
    }

    // ---- E. negatives ----

    @Test
    fun `empty html yields zero courses`() {
        val parser = JwThuparser("<html><body></body></html>")
        assertEquals(0, parser.generateCourseList().size)
        assertEquals(0, parser.confidence())
    }

    @Test
    fun `login-only page yields zero with guard feature`() {
        val html = """
            <html><body>
            <div id='login-form'>
                <input type='text' name='username' />
                <form action='/login' method='post'>TUNET CAS 登录</form>
            </div>
            </body></html>
        """.trimIndent()
        val parser = JwThuparser(html)
        assertEquals(0, parser.generateCourseList().size)
        assertEquals(0, parser.confidence())
        assertTrue(parser.matchedFeatures().contains("guard:login-only-no-setInitValue"))
    }

    // ---- F. registry routing ----

    @Test
    fun `registry routes thu type to JwThuparser`() {
        val parser = JwParserRegistry.parserFor(JwProtocol.TYPE_THU, sample())
        assertTrue(parser is JwThuparser)
    }

    @Test
    fun `selectBest with declared thu type returns 5 courses`() {
        val (courses, attempts) = JwParserRegistry.selectBest(sample(), JwProtocol.TYPE_THU)
        assertEquals(5, courses.size)
        val thu = attempts.firstOrNull { it.type == JwProtocol.TYPE_THU }
        assertTrue(thu != null)
        assertEquals(5, thu!!.courseCount)
        assertTrue(thu.confidence >= 80)
    }
}

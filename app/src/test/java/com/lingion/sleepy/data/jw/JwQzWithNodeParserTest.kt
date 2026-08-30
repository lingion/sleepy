package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzWithNodeParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/qz_with_node/$name")
            ?: error("missing fixture: jw/fixtures/qz_with_node/$name")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses space branch - section comes from title text not grid position`() {
        val html = loadFixture("timetable_qzbr_withnode_space.html")
        val courses = JwQzWithNodeParser(html).generateCourseList()
        // expected.json: courseCount=6
        assertEquals(6, courses.size)

        // 高等数学 周三 1-2节 (单周) — 与所在大格一致, 但 type=1 来自 (单周) 后缀
        val highMath = courses.first { it.name == "高等数学" }
        assertEquals(3, highMath.day)
        assertEquals(1, highMath.startNode)
        assertEquals(2, highMath.endNode)
        assertEquals(3, highMath.startWeek)
        assertEquals(15, highMath.endWeek)
        assertEquals(1, highMath.type)
    }

    @Test
    fun `parses split title branch - independent title week and title node`() {
        val html = loadFixture("timetable_qzbr_withnode_split_title.html")
        val courses = JwQzWithNodeParser(html).generateCourseList()
        // expected.json: courseCount=5, 含 5-15,17 拆 2 条
        assertEquals(5, courses.size)

        // 周三 1-2节 高等数学 (单周)
        val highMath = courses.first { it.name == "高等数学" }
        assertEquals(3, highMath.day)
        assertEquals(1, highMath.startNode)
        assertEquals(1, highMath.type)

        // 大学物理 周一 5-6节 5-15,17 多段
        val physics = courses.filter { it.name == "大学物理" }
        assertEquals(2, physics.size)
        assertEquals(5, physics[0].startNode)
        assertEquals(6, physics[0].endNode)
        assertEquals(5, physics[0].startWeek)
        assertEquals(15, physics[0].endWeek)
        assertEquals(17, physics[1].startWeek)
        assertEquals(17, physics[1].endWeek)
    }

    @Test
    fun `parses empty kbtable without exception`() {
        val html = loadFixture("timetable_kbtable_empty.html")
        val courses = JwQzWithNodeParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }
}

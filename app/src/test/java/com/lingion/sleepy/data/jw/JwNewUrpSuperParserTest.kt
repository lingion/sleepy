package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwNewUrpSuperParserTest {

    private val fixtureJson: String by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/new-urp-super/sample-flat-array.json")
            ?: javaClass.getResourceAsStream("/jw/new-urp-super/sample-flat-array.json")
            ?: error("fixture not found")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `parse new urp flat-array bitmap variant`() {
        val parser = JwNewUrpSuperParser(fixtureJson)
        val courses = parser.generateCourseList()

        // 5 entries, each with single id (no nested timeAndPlaceList expansion), so 5 courses total
        assertEquals(5, courses.size)

        // 1. 高等数学: skxq=1 (周一), skjc=1, cxjc=2 → 1-2节, skzc 全部 1 (前20周) → startWeek=1, endWeek=20, type=0
        val c1 = courses[0]
        assertEquals("高等数学", c1.name)
        assertEquals("张教授", c1.teacher)
        assertEquals("主教学楼A301", c1.room)
        assertEquals(1, c1.day)
        assertEquals(1, c1.startNode)
        assertEquals(2, c1.endNode)
        assertEquals(1, c1.startWeek)
        assertEquals(20, c1.endWeek)
        assertEquals(0, c1.type)

        // 2. 线性代数: skxq=2 (周二), skjc=3, cxjc=2 → 3-4节, skzc 1010... → startWeek=1, endWeek=19, type=1 (单周)
        val c2 = courses[1]
        assertEquals("线性代数", c2.name)
        assertEquals("李老师", c2.teacher)
        assertEquals("主教学楼A302", c2.room)
        assertEquals(2, c2.day)
        assertEquals(3, c2.startNode)
        assertEquals(4, c2.endNode)
        assertEquals(1, c2.startWeek)
        assertEquals(19, c2.endWeek)
        assertEquals(1, c2.type)

        // 3. 数据结构: skxq=3 (周三), skjc=5, cxjc=3 → 5-7节, skzc 前15周 → startWeek=1, endWeek=15, type=0
        val c3 = courses[2]
        assertEquals("数据结构", c3.name)
        assertEquals("王教授", c3.teacher)
        assertEquals("信息楼B205", c3.room)
        assertEquals(3, c3.day)
        assertEquals(5, c3.startNode)
        assertEquals(7, c3.endNode)
        assertEquals(1, c3.startWeek)
        assertEquals(15, c3.endWeek)
        assertEquals(0, c3.type)

        // 4. 英语: skxq=4 (周四), skjc=11, cxjc=1 → 11节, skzc 前14周 → type=0
        val c4 = courses[3]
        assertEquals("英语", c4.name)
        assertEquals("外教", c4.teacher)
        assertEquals("文科楼C101", c4.room)
        assertEquals(4, c4.day)
        assertEquals(11, c4.startNode)
        assertEquals(11, c4.endNode)
        assertEquals(1, c4.startWeek)
        assertEquals(14, c4.endWeek)
        assertEquals(0, c4.type)

        // 5. 体育: skxq=5 (周五), skjc=9, cxjc=1 → 9节, skzc 前10周 → type=0
        val c5 = courses[4]
        assertEquals("体育", c5.name)
        assertEquals("教练", c5.teacher)
        assertEquals("体育馆操场", c5.room)
        assertEquals(5, c5.day)
        assertEquals(9, c5.startNode)
        assertEquals(9, c5.endNode)
        assertEquals(1, c5.startWeek)
        assertEquals(10, c5.endWeek)
        assertEquals(0, c5.type)
    }

    @Test
    fun `confidence and matched features`() {
        val parser = JwNewUrpSuperParser(fixtureJson)
        val confidence = parser.confidence()
        assertTrue("expected confidence > 0 for full anchor set, got $confidence", confidence > 0)

        val features = parser.matchedFeatures()
        assertTrue("skzc should be in matchedFeatures", "skzc" in features)
        assertTrue("id should be in matchedFeatures", "id" in features)
        assertTrue("kcm should be in matchedFeatures", "kcm" in features)
    }

    @Test
    fun `empty input returns empty list`() {
        val parser = JwNewUrpSuperParser("")
        val courses = parser.generateCourseList()
        assertNotNull(courses)
        assertEquals(0, courses.size)
    }

    @Test
    fun `garbage input returns empty list without crashing`() {
        val parser = JwNewUrpSuperParser("<html><body>not json</body></html>")
        val courses = parser.generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `embedded array with bracket in course name and nested array`() {
        val html = """<html><script>window.kb=[{"kcm":"数学[实验]","jsm":"老师","cxjc":2,"extra":[1,2],"id":{"skxq":1,"skjc":3,"skzc":"101"}}];</script></html>"""
        val courses = JwNewUrpSuperParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("数学[实验]", courses.single().name)
        assertEquals(3, courses.single().endWeek)
        assertEquals(1, courses.single().type)
    }

    @Test
    fun `many unmatched brackets before valid array do not hide timetable`() {
        val payload = """[{"kcm":"Math [lab]","cxjc":1,"id":{"skxq":2,"skjc":2,"skzc":"101"}}]"""
        val html = "<div>" + "[".repeat(15000) + "</div><script>window.table=$payload;</script>"
        val courses = JwNewUrpSuperParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("Math [lab]", courses.single().name)
    }

    @Test
    fun `HTML attribute quote and irrelevant arrays do not hide script payload`() {
        val html = """<div data-note="odd [ quote" data-other='x'>[1,2]</div><script>const unrelated=[{"x":1}];window.table=[{"kcm":"Math [lab]","cxjc":1,"id":{"skxq":2,"skjc":2,"skzc":"101"}}];</script>"""
        assertEquals("Math [lab]", JwNewUrpSuperParser(html).generateCourseList().single().name)
    }

    @Test
    fun `deep irrelevant arrays do not require nested JSON parsing`() {
        val payload = """[{"kcm":"A","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"1"}}]"""
        val html = "<script>const other=" + "[".repeat(2000) + "0" + "]".repeat(2000) + ";window.table=$payload;</script>"
        assertEquals(1, JwNewUrpSuperParser(html).generateCourseList().size)
    }

    @Test
    fun `deep keyword arrays lacking id are skipped before JSON parse`() {
        val noise = "[".repeat(10000) + "{"kcm":"x","skzc":"1"}" + "]".repeat(10000)
        val valid = """[{"kcm":"Real","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"1"}}]"""
        assertEquals("Real", JwNewUrpSuperParser(noise + valid).generateCourseList().single().name)
    }

    @Test
    fun `deep keyword arrays with unrelated id leave later valid array reachable`() {
        val noise = "[".repeat(4000) + "{"kcm":"x","skzc":"1","id":0}" + "]".repeat(4000)
        val valid = """[{"kcm":"Real","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"1"}}]"""
        assertEquals("Real", JwNewUrpSuperParser(noise + valid).generateCourseList().single().name)
    }

    @Test
    fun `large valid timetable array remains parseable`() {
        val item = """{"kcm":"Big","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"1"}}"""
        val payload = (1..300).joinToString(prefix = "[", postfix = "]") { item }
        assertEquals(300, JwNewUrpSuperParser(payload).generateCourseList().size)
    }

    @Test
    fun `two adjacent weeks preserve ending week and invalid bitmap rejects`() {
        fun payload(bits: String) = """[{"kcm":"A","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"$bits"}}]"""
        assertEquals(2, JwNewUrpSuperParser(payload("110")).generateCourseList().single().endWeek)
        assertEquals(3, JwNewUrpSuperParser(payload("101")).generateCourseList().single().endWeek)
        assertTrue(JwNewUrpSuperParser(payload("10X")).generateCourseList().isEmpty())
        assertTrue(JwNewUrpSuperParser(payload("1".repeat(61))).generateCourseList().isEmpty())
    }

    @Test
    fun `existing URP parser also accepts flat array when URL selects URP_NEW`() {
        val result = JwImportViewModel.parseHtmlStatic(fixtureJson, JwProtocol.TYPE_URP_NEW).first
        assertEquals(5, result.size)
    }

    @Test
    fun `flat array reaches protocol detection and generic registry`() {
        assertEquals(JwProtocol.TYPE_NEW_URP_SUPER,
            JwImportViewModel.detectProtocolFromHtmlForTest(fixtureJson))
        assertEquals(JwProtocol.TYPE_NEW_URP_SUPER,
            JwImportViewModel.detectProtocolForTest(fixtureJson, null))
        assertEquals(5, JwImportViewModel.tryAllParsersForTest(fixtureJson).size)
        val emptyTerm = """[{"kcm":"A","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"000"}}]"""
        assertEquals(JwProtocol.TYPE_NEW_URP_SUPER,
            JwImportViewModel.detectProtocolFromHtmlForTest(emptyTerm))
    }

    @Test
    fun `nested URP bitmap keeps second week and rejects invalid bitmap`() {
        fun payload(bits: String) = """{"dateList":[{"selectCourseList":[{"courseName":"A","timeAndPlaceList":[{"classDay":1,"classSessions":1,"continuingSession":1,"classWeek":"$bits"}]}]}]}"""
        assertEquals(2, JwNewUrpParser(payload("110")).generateCourseList().single().endWeek)
        assertEquals(3, JwNewUrpParser(payload("101")).generateCourseList().single().endWeek)
        assertTrue(JwNewUrpParser(payload("10X")).generateCourseList().isEmpty())
    }

    @Test
    fun `all eight-week bitmaps remain lossless in both URP variants`() {
        fun flat(bits: String) = """[{"kcm":"A","cxjc":1,"id":{"skxq":1,"skjc":1,"skzc":"$bits"}}]"""
        fun nested(bits: String) = """{"dateList":[{"selectCourseList":[{"courseName":"A","timeAndPlaceList":[{"classDay":1,"classSessions":1,"continuingSession":1,"classWeek":"$bits"}]}]}]}"""
        for (mask in 0..255) {
            val bits = (0..7).joinToString("") { if (mask and (1 shl it) != 0) "1" else "0" }
            val expected = (1..8).filter { bits[it - 1] == '1' }.toSet()
            val variants = listOf(
                JwNewUrpSuperParser(flat(bits)).generateCourseList(),
                JwNewUrpParser(nested(bits)).generateCourseList()
            )
            for (courses in variants) {
                val actual = courses.flatMap { course ->
                    (course.startWeek..course.endWeek).filter { week ->
                        course.type == 0 || (course.type == 1 && week % 2 == 1) ||
                            (course.type == 2 && week % 2 == 0)
                    }
                }.toSet()
                assertEquals("bitmap=$bits", expected, actual)
            }
        }
    }
}

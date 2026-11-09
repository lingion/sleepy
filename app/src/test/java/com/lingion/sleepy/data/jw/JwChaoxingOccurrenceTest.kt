package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JwChaoxingOccurrenceTest {
    private data class Slot(val name: String, val teacher: String, val room: String, val day: Int, val week: Int, val node: Int)

    private fun expand(courses: List<JwCourse>): List<Slot> = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { week ->
            (c.startNode..c.endNode).map { Slot(c.name, c.teacher, c.room, c.day, week, it) }
        }
    }

    private fun assertRoutes(source: String, expected: Set<Slot>) {
        for (courses in listOf(
            JwChaoxingParser(source).generateCourseList(),
            JwParserRegistry.selectBest(source, "chaoxing").first,
            JwParserRegistry.selectBest(source).first,
            JwImportViewModel.parseHtmlStatic(source, "chaoxing").first,
        )) {
            val actual = expand(courses)
            assertEquals("missing or invented lesson", expected, actual.toSet())
            assertEquals("duplicate lesson", actual.toSet().size, actual.size)
        }
    }

    @Test fun `both captured row fixtures preserve every field for every week and period`() {
        for (school in listOf("fzmjtc", "jlbtc")) {
            val source = File("src/test/resources/jw/fixtures/chaoxing/$school-grdb.json").readText()
            // Source rows are single periods and explicit comma-separated weeks. No production
            // grouping, range parser or week compressor participates in this independent oracle.
            val expected = Json.parseToJsonElement(source).jsonObject["rows"]!!.jsonArray.flatMap { row ->
                val r = row.jsonObject
                fun text(key: String) = Jsoup.parseBodyFragment(r[key]!!.jsonPrimitive.content).text()
                r["zcstr"]!!.jsonPrimitive.content.split(',').map { week ->
                    Slot(text("kcmc"), text("tmc"), text("croommc"),
                        r["xingqi"]!!.jsonPrimitive.int, week.toInt(), r["xjc"]!!.jsonPrimitive.int)
                }
            }.toSet()
            assertRoutes(source, expected)
        }
    }

    @Test fun `different weeks periods rooms and teachers never form a cross product`() {
        val source = """{"rows":[
            {"kcmc":"实验","xingqi":2,"xjc":1,"zcstr":"1,3","tmc":"张","croommc":"A"},
            {"kcmc":"实验","xingqi":2,"xjc":2,"zcstr":"3,5","tmc":"张","croommc":"A"},
            {"kcmc":"实验","xingqi":2,"xjc":4,"zcstr":"2","tmc":"张","croommc":"B"},
            {"kcmc":"实验","xingqi":2,"xjc":4,"zcstr":"2","tmc":"李","croommc":"C"},
            {"kcmc":"实验","xingqi":2,"xjc":1,"zcstr":"1,3","tmc":"张","croommc":"A"}
        ]}"""
        assertRoutes(source, setOf(
            Slot("实验", "张", "A", 2, 1, 1), Slot("实验", "张", "A", 2, 3, 1),
            Slot("实验", "张", "A", 2, 3, 2), Slot("实验", "张", "A", 2, 5, 2),
            Slot("实验", "张", "B", 2, 2, 4), Slot("实验", "李", "C", 2, 2, 4),
        ))
    }

    @Test fun `nonpositive periods and invalid week strings never become courses`() {
        for ((node, weeks) in listOf(0 to "1", -1 to "1", 1 to "0", 1 to "-2", 1 to "0-3", 1 to "3-1", 1 to "1,garbage")) {
            val source = """{"rows":[{"kcmc":"错误课","xingqi":1,"xjc":$node,"zcstr":"$weeks"}]}"""
            assertTrue("node=$node weeks=$weeks", JwChaoxingParser(source).generateCourseList().isEmpty())
        }
    }

    @Test fun `HTML entities are decoded once for all display fields`() {
        val source = """{"rows":[{"kcmc":"<a>A &amp; B</a>","xingqi":7,"xjc":3,"zcstr":"1","tmc":"张&nbsp;李","croommc":"A &lt; B"}]}"""
        assertRoutes(source, setOf(Slot("A & B", "张 李", "A < B", 7, 1, 3)))
    }
}

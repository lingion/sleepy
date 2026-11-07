package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Explicit school types are required: these row maps cannot be inferred from generic HTML. */
class JwQzSchoolRowMappingTest {
    private val maps = linkedMapOf(
        "qz_single_node" to listOf(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6, 7 to 7, 8 to 8),
        "qz_njust" to listOf(1 to 3, 4 to 5, 6 to 7, 8 to 10, 11 to 13, 11 to 12),
        "qz_wit" to listOf(1 to 2, 3 to 4, 6 to 7, 8 to 9, 10 to 12, 11 to 12),
        "qz_ecust" to listOf(1 to 2, 3 to 4, 4 to 4, 5 to 6, 7 to 8, 8 to 8, 9 to 10, 11 to 12, 17 to 18),
        "qz_ahut" to listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 11, 12 to 12, 13 to 14),
        "qz_bjfu" to listOf(1 to 2, 3 to 4, 5 to 5, 6 to 7, 8 to 9, 10 to 11, 12 to 12, 15 to 16),
    )

    private fun page(type: String, rows: Int, explicit: Boolean = false): String {
        val css = if (type in setOf("qz_wit", "qz_ahut")) "kbcontent1" else "kbcontent"
        return "<table id='kbtable'>" + (1..rows).joinToString("") { row ->
            "<tr><th>第${row}大节</th><td><div class='$css'>课程$row<br>" +
                "<font title='老师'>教师$row</font><br><font title='教室'>教室$row</font><br>" +
                "<font title='周次(节次)'>2-10(双周)${if (explicit) "[13-14节]" else ""}</font>" +
                "</div></td>" + "<td></td>".repeat(6) + "</tr>"
        } + "</table>"
    }

    @Test fun `all six school maps preserve every documented row and fallback row`() {
        for ((type, nodes) in maps) {
            val html = page(type, nodes.size)
            val expected = nodes.mapIndexed { index, pair ->
                JwCourse("课程${index + 1}", "教室${index + 1}", "教师${index + 1}", 1,
                    pair.first, pair.second, 2, 10, 2)
            }
            assertEquals(type, expected, JwParserRegistry.parserFor(type, html).generateCourseList())
            assertEquals("declared $type", expected, JwParserRegistry.selectBest(html, type).first)
            assertEquals("screen $type", expected, JwImportViewModel.parseHtmlStatic(html, type).first)
        }
    }

    @Test fun `explicit sections override each school row map`() {
        for (type in maps.keys) {
            val html = page(type, 1, explicit = true)
            assertEquals(type, listOf(JwCourse("课程1", "教室1", "教师1", 1, 13, 14, 2, 10, 2)),
                JwParserRegistry.selectBest(html, type).first)
        }
    }

    @Test fun `school specific row maps never participate in automatic guessing`() {
        val types = JwParserRegistry.allCandidates(page("qz_njust", 1)).map { it.first }
        for (type in maps.keys) assertFalse(type, type in types)
    }

    @Test fun `school catalog selects the declared row convention`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val expected = mapOf(
            "安徽工业大学" to "qz_ahut", "北京林业大学" to "qz_bjfu", "华东理工大学" to "qz_ecust",
            "南京农业大学" to "qz_single_node", "南京理工大学" to "qz_njust",
            "青岛农业大学" to "qz_bjfu", "西南医科大学" to "qz_single_node",
        )
        for ((name, type) in expected) {
            val school = schools.single { it.name == name }
            assertEquals(name, type, school.type)
            assertEquals(name, true, school.canImport)
        }
    }

    @Test fun `timetable container with row based layout retains each school convention`() {
        for ((type, nodes) in maps) {
            val html = page(type, nodes.size).replace("id='kbtable'", "id='timetable'")
            val expected = nodes.mapIndexed { index, pair ->
                JwCourse("课程${index + 1}", "教室${index + 1}", "教师${index + 1}", 1,
                    pair.first, pair.second, 2, 10, 2)
            }
            assertEquals(type, expected, JwParserRegistry.selectBest(html, type).first)
        }
    }
}

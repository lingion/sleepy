package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwOldQzOccurrenceTest {
    private fun fixture(name: String) = javaClass.getResource("/jw/fixtures/old_qz_super/$name.html")!!.readText()
    private fun parse(source: String, type: String = "qz_old") = JwParserRegistry.parserFor(type, source).generateCourseList()

    @Test fun `super parser selects first populated form instead of concatenating alternate layouts`() {
        val names = listOf("jlict", "ccut", "ccsu", "base")
        for (index in 0..2) {
            val source = names.drop(index).joinToString("") { fixture(it) }
            assertEquals(names[index], parse(fixture(names[index])), parse(source))
        }
    }

    @Test fun `declared old QZ variants use their own form even beside a different table`() {
        val source = listOf("jlict", "ccut", "ccsu", "base").joinToString("") { fixture(it) }
        for ((type, form) in listOf("jlict_qz_old" to "jlict", "ccut_qz_old" to "ccut", "ccsu_qz_old" to "ccsu")) {
            val expected = parse(fixture(form))
            assertEquals(expected, parse(source, type))
            assertEquals(expected, JwParserRegistry.selectBest(source, type).first)
            assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, type).first)
            assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(fixture("base"), type).first)
        }
    }

    @Test fun `JLICT trailing parity applies to the full comma separated week list`() {
        val source = fixture("jlict").replace("1-8单", "1-7,11-15单")
        assertEquals(listOf(
            JwCourse("标题课", "示例教室", "示例教师", 1, 1, 2, 1, 7, 1),
            JwCourse("标题课", "示例教室", "示例教师", 1, 1, 2, 11, 15, 1),
        ), parse(source))
    }

    @Test fun `empty higher priority form permits fallback without manufacturing rows`() {
        val source = "<div id=\"kb\"><table><tr><th>节次</th></tr></table></div>" + fixture("base")
        assertEquals(parse(fixture("base")), parse(source))
    }

    @Test fun `JLICT school catalog declares its verified parser form without changing status`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        assertEquals("jlict_qz_old", schools.single { it.name == "吉林化工大学" }.type)
    }
}

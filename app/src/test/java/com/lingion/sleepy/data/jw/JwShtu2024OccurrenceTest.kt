package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwShtu2024OccurrenceTest {
    private fun html(node: Int, span: Int = 1) = """<table id="jsTbl_1"><tr><td xq="5" jc="$node" rowspan="$span">
        <div class="arrage"><p>研</p><p>第2-6双周(张老师)</p><p>研讨(一班)</p><p>C303</p><p>面授讲课</p></div>
        </td></tr></table>"""

    @Test fun `every official SHTU period carries its actual time`() {
        val expected = listOf(
            "08:15" to "09:00", "09:10" to "09:55", "10:15" to "11:00", "11:10" to "11:55",
            "13:00" to "13:45", "13:55" to "14:40", "15:00" to "15:45", "15:55" to "16:40",
            "16:50" to "17:35", "18:00" to "18:45", "18:55" to "19:40", "19:50" to "20:35", "20:45" to "21:30",
        )
        for ((index, clock) in expected.withIndex()) {
            val node = index + 1
            val courses = listOf(JwCourse("研讨", "C303", "张老师", 5, node, node, 2, 6, 2, clock.first, clock.second))
            assertEquals(courses, JwParserRegistry.parserFor("shtu_post_2024", html(node)).generateCourseList())
            assertEquals(courses, JwParserRegistry.selectBest(html(node), "shtu_post_2024").first)
            assertEquals(courses, JwImportViewModel.parseHtmlStatic(html(node), "shtu_post_2024").first)
        }
        assertEquals(listOf(JwCourse("研讨", "C303", "张老师", 5, 11, 13, 2, 6, 2, "18:55", "21:30")),
            JwParserRegistry.parserFor("shtu_post_2024", html(11, 3)).generateCourseList())
    }

    @Test fun `school clock is not guessed for other JZ tables or invalid nodes`() {
        assertFalse(JwParserRegistry.allCandidates(html(1)).any { it.first == "shtu_post_2024" })
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html(14), "shtu_post_2024").first)
        assertEquals("", JwParserRegistry.selectBest(html(1), "jz").first.single().startTime)
    }

    @Test fun `both declared school entries route to SHTU 2024 and draft retains its clock`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        for (name in listOf("上海科技大学 - 研究生", "上海科技大学（新） - 研究生")) {
            val school = schools.single { it.name == name }
            assertEquals("shtu_post_2024", school.type)
            val courses = JwImportViewModel.parseHtmlStatic(html(11, 3), school.type!!).first
            val snapshot = JwImportDraftSnapshot(school = school, courses = courses, periods = emptyList())
            val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))!!
            val entities = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 7L, "#FF6750A4")
            assertEquals("18:55", entities.single().startTime)
            assertEquals("21:30", entities.single().endTime)
            assertEquals(true, entities.single().ownTime)
        }
    }
}

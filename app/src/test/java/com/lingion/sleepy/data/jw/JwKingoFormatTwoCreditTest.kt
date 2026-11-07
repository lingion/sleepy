package com.lingion.sleepy.data.jw

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic format-two rows with independently specified schedule and metadata. */
class JwKingoFormatTwoCreditTest {
    private fun source(credit: String?, reordered: Boolean = false): String {
        val columns = linkedMapOf(
            "课程" to "[MA101]高等数学", "教师" to "[T01]张老师", "地点" to "A101",
            "节次" to "星期三[3-4节]", "周次" to "1-5", "单双周" to "单",
        ).apply { if (credit != null) put("学分", credit) }.toList().let {
            if (reordered) it.reversed() else it
        }
        return "<table><thead><tr>" + columns.joinToString("") { "<td>${it.first}</td>" } +
            "</tr></thead><tbody><tr>" + columns.joinToString("") { "<td>${it.second}</td>" } +
            "</tr></tbody></table>"
    }

    private fun assertImport(html: String, credit: Float) {
        val expected = listOf(JwCourse("高等数学", "A101", "张老师", 3, 3, 4, 1, 5, 1, credit = credit))
        assertEquals(expected, JwKingoParser(html).generateCourseList())
        assertEquals(expected, JwParserRegistry.parserFor("kingo_new", html).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(html, "kingo_new").first)
        val imported = JwImportViewModel.parseHtmlStatic(html, "kingo_new").first
        assertEquals(expected, imported)
        val snapshot = JwImportDraftSnapshot(JwSchoolInfo("J", "格式二回归"), imported, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))!!
        assertEquals(expected, restored.courses)
        val entity = JwImportViewModel(Application()).toCourseEntities(restored.courses, 9L, "#FF6750A4").single()
        assertEquals(credit, entity.credit, 0f)
        assertEquals(3, entity.day)
        assertEquals(3, entity.startNode)
        assertEquals(2, entity.step)
        assertEquals(1, entity.startWeek)
        assertEquals(5, entity.endWeek)
        assertEquals(1, entity.type)
    }

    @Test fun `format two carries the credit column through every import boundary`() {
        assertImport(source("2.5"), 2.5f)
    }

    @Test fun `credit follows its header when format two columns move`() {
        assertImport(source(" 3.0 ", reordered = true), 3f)
    }

    @Test fun `missing blank invalid negative and nonfinite credit defaults to zero`() {
        for (value in listOf(null, "", "invalid", "-1", "NaN", "Infinity", "1e99")) {
            assertImport(source(value), 0f)
        }
    }
}

package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Synthetic Kingo pages use the checked-in school route; no authenticated timetable is implied. */
class JwJiamusiImportIntegrityTest {
    @Test fun `Jiamusi school route preserves sparse weeks high periods and complete fields through persistence`() {
        val catalog = JSONArray(File("src/main/assets/schools.json").readText())
        val school = (0 until catalog.length()).map { catalog.getJSONObject(it) }
            .single { it.getString("name") == "佳木斯大学" }
        val type = school.getString("type")
        assertEquals("kingo_new", type)
        val html = """<table id="mytable"><tr><td></td><td><div>
            实验课<br>张老师<br>1-5周(单),10-12周(双)[31-32节]<br>实训楼A101</div>
            <div>坏课<br>教师<br>3-1周[1-2节]<br>未知地点</div></td></tr></table>"""
        val expected = listOf(
            JwCourse("实验课", "实训楼A101", "张老师", 2, 31, 32, 1, 5, 1),
            JwCourse("实验课", "实训楼A101", "张老师", 2, 31, 32, 10, 12, 2),
        )
        for (courses in listOf(
            JwParserRegistry.parserFor(type, html).generateCourseList(),
            JwParserRegistry.selectBest(html, type).first,
            JwImportViewModel.parseHtmlStatic(html, type).first,
        )) {
            assertEquals(expected, courses)
            val draft = JwImportDraftSnapshot(JwSchoolInfo("J", "佳木斯大学", type = type), courses, emptyList())
            val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
            assertEquals(expected, restored.courses)
            val entities = JwImportViewModel(android.app.Application())
                .toCourseEntities(restored.courses, 7L, "#FF6750A4")
            assertEquals(expected.map { listOf(it.name, it.teacher, it.room, it.day, it.startNode,
                it.endNode - it.startNode + 1, it.startWeek, it.endWeek, it.type) },
                entities.map { listOf(it.courseName, it.teacher, it.room, it.day, it.startNode,
                    it.step, it.startWeek, it.endWeek, it.type) })
        }
    }
}

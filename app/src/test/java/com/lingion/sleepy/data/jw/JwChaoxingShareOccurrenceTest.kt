package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Synthetic LessonArray payloads derived from WakeUp 6.6.2 o0O0O00, not account captures. */
class JwChaoxingShareOccurrenceTest {
    @Test fun `share URL is recognized independently of academic tenant domains`() {
        assertEquals("chaoxing_share", JwImportViewModel.detectProtocolFromUrlImpl("https://kb.chaoxing.com/curriculum/share.html?id=sample"))
        for (url in listOf("https://kb.chaoxing.com.evil.test/share.html?id=x", "https://example.test/?next=https://kb.chaoxing.com/share.html?id=x", "https://kb.chaoxing.com/share.html")) {
            assertNotEquals("chaoxing_share", JwImportViewModel.detectProtocolFromUrlImpl(url))
        }
        assertEquals("chaoxing", JwImportViewModel.detectProtocolFromUrlImpl("https://hbut.jw.chaoxing.com/admin"))
    }

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwParserRegistry.parserFor("chaoxing_share", source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source, "chaoxing_share").first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, "chaoxing_share").first)
    }

    @Test fun `length online location and unordered sparse weeks preserve all fields`() {
        val source = """[
            {"name":"实验","dayOfWeek":7,"beginNumber":3,"length":3,"location":"A301","onlineLocation":"线上会议123","teacherName":"张老师","weeks":"7, 1,3,3,5"},
            {"name":"同名课","dayOfWeek":1,"beginNumber":8,"length":2,"location":null,"onlineLocation":"线上B","teacherName":null,"weeks":"2,4,10"}
        ]"""
        assertRoutes(source, listOf(
            JwCourse("实验", "A301线上会议123", "张老师", 7, 3, 5, 1, 7, 1),
            JwCourse("同名课", "线上B", "", 1, 8, 9, 2, 4, 2),
            JwCourse("同名课", "线上B", "", 1, 8, 9, 10, 10),
        ))
    }

    @Test fun `repeated weekly fetch rows do not duplicate lessons or merge changed rooms`() {
        val a = """{"name":"实验","dayOfWeek":2,"beginNumber":1,"length":2,"location":"A","teacherName":"张","weeks":"1,3"}"""
        val b = """{"name":"实验","dayOfWeek":2,"beginNumber":3,"length":1,"location":"B","teacherName":"张","weeks":"5"}"""
        assertRoutes("[$a,$a,$b]", listOf(
            JwCourse("实验", "A", "张", 2, 1, 2, 1, 3, 1),
            JwCourse("实验", "B", "张", 2, 3, 3, 5, 5),
        ))
    }

    @Test fun `missing malformed or nonpositive coordinates never fall back to a generic parser`() {
        for ((day, begin, length, weeks) in listOf(
            listOf("0", "1", "2", "1"), listOf("8", "1", "2", "1"),
            listOf("1", "0", "2", "1"), listOf("1", "1", "0", "1"),
            listOf("1", "1", "-1", "1"), listOf("1", "2147483647", "2", "1"),
            listOf("1", "1", "2", ""), listOf("1", "1", "2", "0,1"),
            listOf("1", "1", "2", "1,x"), listOf("1", "1", "2", "1-3"),
        )) assertRoutes("""[{"name":"错误课","dayOfWeek":$day,"beginNumber":$begin,"length":$length,"weeks":"$weeks"}]""", emptyList())
        assertRoutes("""[{"name":"缺长度","dayOfWeek":1,"beginNumber":1,"weeks":"1"}]""", emptyList())
        assertRoutes("""{"rows":[{"kcmc":"其他协议","xingqi":1,"xjc":1,"zcstr":"1"}]}""", emptyList())
    }

    @Test fun `share courses survive draft and entity mapping`() {
        val source = """[{"name":"离散数学","dayOfWeek":4,"beginNumber":5,"length":3,"weeks":"2,4,6","location":"D301","onlineLocation":"","teacherName":"李"}]"""
        val courses = JwParserRegistry.parserFor("chaoxing_share", source).generateCourseList()
        val draft = JwImportDraftSnapshot(school = JwSchoolInfo(sortKey = "F", name = "分享课表", type = "chaoxing_share"), courses = courses, periods = emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(courses, restored.courses)
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4").single()
        assertEquals(listOf("离散数学", "李", "D301"), listOf(entity.courseName, entity.teacher, entity.room))
        assertEquals(listOf(4, 5, 3, 2, 6, 2), listOf(entity.day, entity.startNode, entity.step, entity.startWeek, entity.endWeek, entity.type))
    }

    @Test fun `overlapping weekly responses produce exactly one occurrence per unchanged lesson`() {
        val first = """{"name":"实验","dayOfWeek":2,"beginNumber":1,"length":2,"location":"A","teacherName":"张","weeks":"1,3,5"}"""
        val second = """{"name":"实验","dayOfWeek":2,"beginNumber":1,"length":2,"location":"A","teacherName":"张","weeks":"3,5,7"}"""
        val roomChange = """{"name":"实验","dayOfWeek":2,"beginNumber":1,"length":2,"location":"B","teacherName":"张","weeks":"3,5"}"""
        val teacherChange = """{"name":"实验","dayOfWeek":2,"beginNumber":1,"length":2,"location":"A","teacherName":"李","weeks":"3"}"""
        assertRoutes("[$first,$second,$roomChange,$teacherChange]", listOf(
            JwCourse("实验", "A", "张", 2, 1, 2, 1, 7, 1),
            JwCourse("实验", "B", "张", 2, 1, 2, 3, 5, 1),
            JwCourse("实验", "A", "李", 2, 1, 2, 3, 3),
        ))
    }

    @Test fun `week values beyond the shared draft bound cannot produce unusable courses`() {
        assertRoutes("""[{"name":"错误课","dayOfWeek":2,"beginNumber":1,"length":2,"weeks":"1,367"}]""", emptyList())
    }
}

package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic JZHandCourseInfoItem data; parity constants were checked against the pinned DEX. */
class JwXbellsoftOccurrenceTest {
    private fun row() = JSONObject("""{"kcmc":"操作系统","xqj":7,"djj":5,"dsz":2,"qmz":"1-8;10-12","jsxm":"何老师","skdd":"E501","qz":99,"mz":99,"bjmc":"计科"}""")
    private fun parse(source: String) = JwParserRegistry.parserFor("login_xbellsoft", source).generateCourseList()
    private fun occurrences(courses: List<JwCourse>): List<String> = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.map {
            "${c.name}|${c.teacher}|${c.room}|${c.day}|${c.startNode}|${c.endNode}|$it"
        }
    }.sorted()

    @Test fun `source parity codes and single period semantics preserve exact occurrences`() {
        for (code in listOf(0, 1, 2, 6)) {
            val source = "[${row().put("dsz", code)}]"
            val weeks = ((1..8) + (10..12)).filter { code == 2 || it % 2 == if (code == 1) 1 else 0 }
            val expected = weeks.map { "操作系统|何老师|E501|7|5|5|$it" }.sorted()
            assertEquals(expected, occurrences(parse(source)))
            assertEquals(expected, occurrences(JwParserRegistry.selectBest(source, "login_xbellsoft").first))
            assertEquals(expected, occurrences(JwImportViewModel.parseHtmlStatic(source, "login_xbellsoft").first))
            assertEquals(expected, occurrences(JwJzParser(source).generateCourseList()))
        }
    }

    @Test fun `sparse ranges empty trailing separators and nullable text preserve fields`() {
        val source = "[${row().put("qmz", "1;4;10-12;").put("jsxm", JSONObject.NULL).put("skdd", JSONObject.NULL)}]"
        assertEquals(listOf(1, 4, 10, 11, 12).map { "操作系统|||7|5|5|$it" }.sorted(), occurrences(parse(source)))
    }

    @Test fun `malformed week expressions invalidate the whole row without numeric stripping`() {
        for (weeks in listOf("-1-8", "1.5-8", "1-3;bad;7-9", "0-8", "8-1", "1-367", "1-3-5", "999999999999999999", "")) {
            val source = "[${row().put("qmz", weeks)}]"
            assertTrue(weeks, JwJzParser(source).generateCourseList().isEmpty())
            assertTrue(weeks, parse(source).isEmpty())
        }
    }

    @Test fun `missing and fractional integer fields are never truncated or defaulted`() {
        for ((key, value) in listOf("xqj" to 7.9, "xqj" to 8, "djj" to 5.5, "djj" to 0, "djj" to 31, "dsz" to 1.5)) {
            val source = "[${row().put(key, value)}]"
            assertTrue("$key=$value", JwJzParser(source).generateCourseList().isEmpty())
            assertTrue("$key=$value", parse(source).isEmpty())
        }
        for (key in listOf("xqj", "djj", "dsz", "qmz")) {
            val invalid = row().apply { remove(key) }
            assertTrue(key, parse("[$invalid]").isEmpty())
            assertTrue(key, JwJzParser("[$invalid]").generateCourseList().isEmpty())
        }
    }

    @Test fun `one invalid row cannot contaminate another or trigger a different protocol`() {
        val good = row().put("qmz", "3-4")
        val bad = row().put("kcmc", "错误课").put("djj", 0)
        assertEquals(listOf(JwCourse("操作系统", "E501", "何老师", 7, 5, 5, 3, 4)), parse("[$good,$bad,$good]"))
        assertTrue(JwParserRegistry.selectBest("""[{"courseName":"异协议","weekday":1,"beginSection":1,"weeks":[1]}]""", "login_xbellsoft").first.isEmpty())
    }

    @Test fun `declared raw rows survive draft and entity conversion`() {
        val courses = parse("[${row().put("qmz", "3-7").put("dsz", 1)}]")
        val draft = JwImportDraftSnapshot(JwSchoolInfo("X", "Xbellsoft", type = "login_xbellsoft"), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(courses, restored.courses)
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4").single()
        assertEquals(listOf("操作系统", "何老师", "E501"), listOf(entity.courseName, entity.teacher, entity.room))
        assertEquals(listOf(7, 5, 1, 3, 7, 1), listOf(entity.day, entity.startNode, entity.step, entity.startWeek, entity.endWeek, entity.type))
    }
    @Test fun `overlapping ranges and records represent each physical occurrence once`() {
        val first = row().put("qmz", "1-5;3-7").put("dsz", 1)
        val second = row().put("qmz", "5-9").put("dsz", 1)
        val moved = row().put("qmz", "5").put("skdd", "另一教室").put("dsz", 1)
        val expected = listOf(1,3,5,7,9).map { "操作系统|何老师|E501|7|5|5|$it" } +
            "操作系统|何老师|另一教室|7|5|5|5"
        assertEquals(expected.sorted(), occurrences(parse("[$first,$second,$moved]")))
        assertEquals(expected.sorted(), occurrences(JwJzParser("[$first,$second,$moved]").generateCourseList()))
    }

    @Test fun `parity without any matching week is not an importable course`() {
        assertTrue(parse("[${row().put("qmz", "2").put("dsz", 1)}]").isEmpty())
        assertTrue(parse("[${row().put("qmz", "3").put("dsz", 0)}]").isEmpty())
    }

}

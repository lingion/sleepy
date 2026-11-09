package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwNewUrpOccurrenceTest {
    private fun nested(name: String = "实验", day: Int = 2, node: Int = 3, length: Int = 2, bits: String = "10101") =
        """{"courseName":"$name","attendClassTeacher":"张/李","timeAndPlaceList":[{"classDay":$day,"classSessions":$node,"continuingSession":$length,"classWeek":"$bits","campusName":"东校区","teachingBuildingName":"A楼","classroomName":"301"}]}"""
    private fun flat(name: String, day: Int, node: Int, bits: String) =
        """{"kcm":"$name","jsm":"张/李","jxlm":"A楼","jasm":"301","cxjc":2,"id":{"skxq":$day,"skjc":$node,"skzc":"$bits"}}"""
    private fun root(vararg groups: String) = """{"dateList":[${groups.joinToString(",") { "{\"selectCourseList\":[$it]}" }}]}"""

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals("direct", expected, JwNewUrpParser(source).generateCourseList())
        assertEquals("declared", expected, JwParserRegistry.selectBest(source, "urp_new").first)
        assertEquals("screen", expected, JwImportViewModel.parseHtmlStatic(source, "urp_new").first)
        assertEquals("automatic", expected, JwParserRegistry.selectBest(source).first)
    }

    @Test fun `all dateList groups preserve their own course coordinates and fields`() {
        val source = root(nested(), nested("周日讨论", 7, 7, 3, "01010"))
        assertRoutes(source, listOf(
            JwCourse("实验", "东校区A楼301", "张/李", 2, 3, 4, 1, 5, 1),
            JwCourse("周日讨论", "东校区A楼301", "张/李", 7, 7, 9, 2, 4, 2),
        ))
    }

    @Test fun `empty and malformed groups cannot hide later valid groups`() {
        val valid = root(nested()).substringAfter("\"dateList\":[").dropLast(2)
        val source = """{"dateList":[null,{},"",{"selectCourseList":[null]},$valid]}"""
        assertRoutes(source, listOf(JwCourse("实验", "东校区A楼301", "张/李", 2, 3, 4, 1, 5, 1)))
    }

    @Test fun `nested invalid coordinates do not default to Monday first period`() {
        for (field in listOf("classDay", "classSessions", "continuingSession")) {
            for (value in listOf(null, "bad", "0", "-1", "2147483647", "1.5")) {
                val course = JSONObject(nested())
                val time = course.getJSONArray("timeAndPlaceList").getJSONObject(0)
                if (value == null) time.remove(field) else time.put(field, value)
                assertEquals("$field=$value", emptyList<JwCourse>(), JwNewUrpParser(root(course.toString())).generateCourseList())
            }
        }
        assertRoutes(root(nested(name = "")), emptyList())
    }

    @Test fun `real two dimensional URP array retains every inner group`() {
        val source = "[[],[${flat("实验", 2, 3, "10101")}],[],[${flat("周日讨论", 7, 7, "01010")}]]"
        val expected = listOf(
            JwCourse("实验", "A楼301", "张/李", 2, 3, 4, 1, 5, 1),
            JwCourse("周日讨论", "A楼301", "张/李", 7, 7, 8, 2, 4, 2),
        )
        assertRoutes(source, expected)
        assertEquals(expected, JwNewUrpSuperParser(source).generateCourseList())
        assertEquals(expected, JwNewUrpSuperParser("<script>window.table=$source;</script>").generateCourseList())
        assertEquals(JSONArray(source).toString(), JSONArray(JwNewUrpSuperParser(source).extractJsonArrayForTest(source)).toString())
    }

    @Test fun `nested JSON marker in page text cannot hide actual script data`() {
        val source = "<p>dateList 是课表字段</p><script>window.table=${root(nested())};</script>"
        assertRoutes(source, listOf(JwCourse("实验", "东校区A楼301", "张/李", 2, 3, 4, 1, 5, 1)))
    }

    @Test fun `array variant requires explicit integer coordinates and length`() {
        for (field in listOf("skxq", "skjc", "cxjc")) for (value in listOf(null, "bad", "0", "-1", "1.5")) {
            val row = JSONObject(flat("实验", 2, 3, "10101"))
            val target = if (field == "cxjc") row else row.getJSONObject("id")
            if (value == null) target.remove(field) else target.put(field, value)
            val source = "[[$row]]"
            assertEquals("$field=$value", emptyList<JwCourse>(), JwNewUrpSuperParser(source).generateCourseList())
        }
    }

    @Test fun `DEX URP ajax factory alias reaches both nested and two dimensional variants`() {
        for (source in listOf(root(nested()), "[[${flat("实验", 2, 3, "10101")}]]")) {
            val expected = JwNewUrpParser(source).generateCourseList()
            assertEquals(expected, JwParserRegistry.parserFor("urp_new_ajax", source).generateCourseList())
            assertEquals(expected, JwParserRegistry.selectBest(source, "urp_new_ajax").first)
            assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, "urp_new_ajax").first)
        }
    }
}

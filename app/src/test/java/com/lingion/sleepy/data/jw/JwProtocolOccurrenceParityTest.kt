package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwProtocolOccurrenceParityTest {
    private fun weeks(courses: List<JwCourse>): Set<Int> = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter { week ->
            course.type == 0 || (course.type == 1 && week % 2 == 1) || (course.type == 2 && week % 2 == 0)
        }
    }.toSet()

    @Test fun `every subset of nine weeks survives the imported course bridge unchanged`() {
        for (mask in 1 until (1 shl 9)) {
            val selected = (1..9).filter { mask and (1 shl (it - 1)) != 0 }
            val source = """[{"name":"实验","day":1,"startSection":1,"endSection":2,"weeks":${selected}}]"""
            assertEquals("weeks=$selected", selected.toSet(), weeks(JwSgAssetParser(source).generateCourseList()))
        }
    }

    @Test fun `shuwei table0 array positions determine day and period and bitmap preserves holes`() {
        val slots = JSONArray()
        repeat(21) { slots.put(JSONArray()) }
        fun course(room: String) = JSONObject().put("courseName", "线性代数(课程编码)")
            .put("teacherName", "教师甲").put("roomName", room).put("vaildWeeks", "0100010001")
        slots.getJSONArray(0).put(course("A101"))
        slots.getJSONArray(12).put(course("A202"))
        slots.getJSONArray(12).put(course("A203"))
        val source = JSONObject().put("unitCount", 10).put("activities", slots).toString()
        val actual = JwShuweiParser(source).generateCourseList()
        assertEquals(setOf(1, 5, 9), weeks(actual))
        assertEquals(setOf(Triple(1, 1, "A101"), Triple(2, 3, "A202"), Triple(2, 3, "A203")),
            actual.map { Triple(it.day, it.startNode, it.room) }.toSet())
        assertEquals(setOf("线性代数(课程编码)"), actual.map { it.name }.toSet())
    }

    @Test fun `same schedule with different teacher or room stays distinct`() {
        val source = """{"activities":[
            {"courseName":"实验","weekday":2,"startSection":3,"endSection":4,"weeksStr":"1-8","teacherName":"甲","room":"A"},
            {"courseName":"实验","weekday":2,"startSection":3,"endSection":6,"weeksStr":"1-8","teacherName":"乙","room":"B"}
        ]}"""
        assertEquals(2, JwShuweiParser(source).generateCourseList().size)
    }

    @Test fun `chaoxing bare array and parenthesized week annotation preserve schedule`() {
        val source = """[{"kcmc":"<b>数学</b>","xq":"4","djc":5,"tmc":"教师","croommc":"A101","zc":"1(调整2),3-5","zctype":"0"}]"""
        val actual = JwChaoxingLegacyParser(source).generateCourseList()
        assertEquals(setOf(1, 3, 4, 5), weeks(actual))
        assertEquals(setOf(4), actual.map { it.day }.toSet())
        assertEquals(setOf(5), actual.map { it.startNode }.toSet())
    }
}

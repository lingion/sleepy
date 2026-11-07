package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwEams5OccurrenceTest {
    @Test fun `all student tables and numeric week arrays retain every occurrence`() {
        val source = """{
          "studentTableVms":[
            {"activities":[{"courseName":"课程甲","weekday":1,"startUnit":1,"endUnit":2,
              "room":"A101","teachers":["王老师","李老师"],"weekIndexes":[1,5,9]}]},
            {"activities":[{"courseName":"课程乙","weekday":2,"startUnit":3,"endUnit":4,
              "room":"B202","teachers":["张老师"],"weekIndexes":[2,6]}]}
          ],
          "studentTableVm":{"activities":[{"courseName":"课程丙","weekday":7,"startUnit":9,"endUnit":10,
            "room":"C303","teachers":[],"weekIndexes":[8]}]}
        }"""
        val expected = listOf(1,5,9).map { JwCourse("课程甲", "A101", "王老师/李老师", 1,1,2,it,it) } +
            listOf(2,6).map { JwCourse("课程乙", "B202", "张老师", 2,3,4,it,it) } +
            JwCourse("课程丙", "C303", "", 7,9,10,8,8)
        assertEquals(expected, JwEams5Parser(source).generateCourseList())
    }

    @Test fun `missing weeks and invalid day cannot create a week one class`() {
        for (weeks in listOf("[]", "null", "\"\"", "\"invalid\"")) {
            val source = """{"studentTableVms":[{"activities":[{"courseName":"课程","weekday":2,
                "startUnit":3,"endUnit":4,"weekIndexes":$weeks}]}]}"""
            assertTrue(weeks, JwEams5Parser(source).generateCourseList().isEmpty())
        }
        assertTrue(JwEams5Parser("""{"studentTableVms":[{"activities":[{"courseName":"课程","weekday":8,
          "startUnit":3,"endUnit":4,"weekIndexes":"1-2"}]}]}""").generateCourseList().isEmpty())
    }

    @Test fun `HFUT datum keeps clock times even when period mapping is approximate`() {
        val source = """{"result":{"lessonList":[{"id":7,"courseName":"课程"}],
          "scheduleList":[{"lessonId":7,"weekday":2,"weekIndex":3,"startTime":805,"endTime":940,"periods":2}]}}"""
        val course = JwEams5Parser(source).generateCourseList().single()
        assertEquals("08:05", course.startTime)
        assertEquals("09:40", course.endTime)
    }
}

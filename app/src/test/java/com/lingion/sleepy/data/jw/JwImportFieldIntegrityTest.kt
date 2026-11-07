package com.lingion.sleepy.data.jw

import android.app.Application
import org.junit.Assert.*
import org.junit.Test

class JwImportFieldIntegrityTest {
    private fun entities(courses: List<JwCourse>) = JwImportViewModel(Application()).toCourseEntities(
        JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(JwImportDraftSnapshot(
            JwSchoolInfo(sortKey = "T", name = "字段回归"), courses, emptyList()
        )))!!.courses, 3L, "#FF123456"
    )

    @Test fun `print data credit and lesson notes survive draft and entity mapping`() {
        val source = """{"studentTableVm":{"activities":[
            {"courseName":"实验","weekday":3,"startUnit":5,"endUnit":6,"weekIndexes":[2,6],
             "teachers":["甲","乙"],"room":"A101","credits":2.5,"lessonCode":"LAB01","lessonRemark":"分组教学"}
        ]}}"""
        val courses = entities(JwEams5Parser(source).generateCourseList())
        assertEquals(listOf(2, 6), courses.map { it.startWeek })
        courses.forEach {
            assertEquals(2.5f, it.credit, 0f)
            assertEquals("LAB01分组教学", it.note)
            assertEquals("甲/乙", it.teacher)
        }
    }

    @Test fun `course form section remark survives draft and entity mapping`() {
        val html = """<table class="CourseFormTable"><tr><th>星期一</th></tr><tr>
            <td rowspan="1"><div>&nbsp;实验<br>1-2周<br>(第3-4节)(分组教学)<br>甲<br>A101</div></td>
            </tr></table>"""
        val courses = entities(JwJzParser(html).generateCourseList())
        assertEquals("分组教学", courses.single().note)
    }

    @Test fun `entity mapper must reject invalid coordinates rather than move courses`() {
        val valid = JwCourse("课", day=3, startNode=5, endNode=6, startWeek=2, endWeek=8)
        val invalid = listOf(valid.copy(day=0), valid.copy(day=8), valid.copy(startNode=0),
            valid.copy(endNode=4), valid.copy(startWeek=0), valid.copy(endWeek=1), valid.copy(type=3))
        val vm = JwImportViewModel(Application())
        invalid.forEach { course ->
            assertThrows("$course", IllegalArgumentException::class.java) {
                vm.toCourseEntities(listOf(course), 1L, "#FF123456")
            }
        }
    }

    @Test fun `Eams5 rejects invalid or incomplete actual times before creating courses`() {
        for ((start, end) in listOf(0 to 0, 870 to 950, 800 to 0, 1000 to 900, 2500 to 2600)) {
            val source = """{"result":{"lessonList":[{"id":1,"courseName":"课"}],
                "scheduleList":[{"lessonId":1,"weekday":1,"weekIndex":1,
                 "startTime":$start,"endTime":$end,"periods":2}]}}"""
            assertEquals("$start-$end", emptyList<JwCourse>(), JwEams5Parser(source).generateCourseList())
        }
    }

    @Test fun `AHU duration alone cannot identify the first lesson`() {
        val source = """{"data":{"lessons":[{"id":1,"courseName":"课","weekday":1,"weekIndex":1,
            "startTime":0,"endTime":0,"periods":3}]}}"""
        assertEquals(emptyList<JwCourse>(), JwEams5Parser(source).generateCourseList())
    }
}

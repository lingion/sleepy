package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwNbccOccurrenceParityTest {
    private data class Occurrence(
        val name: String, val teacher: String, val room: String,
        val week: Int, val day: Int, val node: Int,
    )

    private fun occurrences(courses: List<JwCourse>) = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter { week ->
            course.type == 0 || (course.type == 1 && week % 2 == 1) ||
                (course.type == 2 && week % 2 == 0)
        }.flatMap { week ->
            (course.startNode..course.endNode).map { node ->
                Occurrence(course.name, course.teacher, course.room, week, course.day, node)
            }
        }
    }

    @Test fun `NBCC fixture and declared registry preserve all 38 period occurrences`() {
        // This fixture is synthetic, based on the cited NBCC page shape. It is
        // not a claim that a live authenticated NBCC timetable was collected.
        val html = javaClass.getResource("/jw/fixtures/classic_eams/nbcc-course-table-action.html")!!.readText()
        val expected = buildList {
            for (week in listOf(5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17)) {
                for (node in 3..4) add(Occurrence("体育健康-1(020D01G35)", "赵老师/钱老师", "体育馆-201乒乓球馆", week, 4, node))
            }
            for (week in listOf(1, 3, 5, 7, 9)) {
                for (node in 10..11) add(Occurrence("毛泽东思想和中国特色社会主义理论体系概论(032A04C02)", "孙老师", "教学楼-101", week, 1, node))
            }
            for (week in listOf(2, 4, 6, 8)) add(Occurrence("高等数学(下)(032A04C03)", "李老师", "教学楼-202", week, 4, 7))
        }
        for (courses in listOf(
            JwClassicEamsParser(html).generateCourseList(),
            JwParserRegistry.selectBest(html, JwProtocol.TYPE_CLASSIC_EAMS).first,
        )) {
            val actual = occurrences(courses)
            assertEquals(38, actual.size)
            assertEquals(expected.toSet(), actual.toSet())
        }
    }
}

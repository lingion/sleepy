package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwZfRoundTwoParityTest {
    private fun page(fields: String, festival: Boolean = true): String =
        "<table id='table1'><tr>" +
            (if (festival) "<td class='festival'>1</td>" else "") +
            "<td id='2-1' rowspan='2'><div><span class='title'>离散数学</span>" +
            fields + "</div></td></tr></table>"

    private fun course(start: Int = 1, end: Int = 2, type: Int = 0) =
        JwCourse("离散数学", "理科楼305", "陈老师", 2, start, end, 1, 4, type)

    private fun occurrences(courses: List<JwCourse>): Set<Triple<Int, Int, Int>> =
        courses.flatMap { course ->
            (course.startWeek..course.endWeek)
                .filter { course.type == 0 || it % 2 == course.type % 2 }
                .flatMap { week -> (course.startNode..course.endNode).map { Triple(week, course.day, it) } }
        }.toSet()

    @Test fun `HTML fields retain source credit and enrollment note`() {
        val html = page("""
            <p title='教师'>陈老师</p><p title='上课地点'>理科楼305</p>
            <p title='节/周'>(1-2节)1-4周</p><p title='学分'>2.5</p>
            <p title='选课备注'>合班教学</p>
        """.trimIndent())
        assertEquals(listOf(course().copy(credit = 2.5f, note = "合班教学")),
            JwNewZfParser(html).generateCourseList())
    }

    @Test fun `nested title icons retain teacher room and time`() {
        val html = page("""
            <p><i title='教师'></i>陈老师</p><p><i title='上课地点'></i>理科楼305</p>
            <p><i title='周/节'></i>(1-2节)1-4周</p>
        """.trimIndent())
        assertEquals(listOf(course()), JwNewZfParser(html).generateCourseList())
    }

    @Test fun `bootstrap original titles preserve the same fields`() {
        val html = page("""
            <p title='' data-original-title='教师'>陈老师</p>
            <p title='' data-original-title='上课地点'>理科楼305</p>
            <p title='' data-original-title='节/周'>(1-2节)1-4周</p>
        """.trimIndent())
        assertEquals(listOf(course()), JwNewZfParser(html).generateCourseList())
    }

    @Test fun `discontinuous periods preserve the independent occurrence set`() {
        val html = page("""
            <p title='教师'>陈老师</p><p title='上课地点'>理科楼305</p>
            <p title='节/周'>(1-2,5-6节)1-4周(单)</p>
        """.trimIndent())
        val actual = JwNewZfParser(html).generateCourseList()
        assertEquals(setOf(Triple(1, 2, 1), Triple(1, 2, 2), Triple(1, 2, 5), Triple(1, 2, 6),
            Triple(3, 2, 1), Triple(3, 2, 2), Triple(3, 2, 5), Triple(3, 2, 6)), occurrences(actual))
        assertEquals(listOf(course(type = 1), course(start = 5, end = 6, type = 1)), actual)
    }

    @Test fun `explicit cell ids do not require an unrelated festival marker`() {
        val html = page("""
            <p title='教师'>陈老师</p><p title='上课地点'>理科楼305</p>
            <p title='节/周'>(1-2节)1-4周</p>
        """.trimIndent(), festival = false)
        assertEquals(listOf(course()), JwNewZfParser(html).generateCourseList())
    }
}

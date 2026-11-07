package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwKingoFormatTwoParityTest {
    private fun source(parity: String) = """<table><thead><tr><td>课程</td><td>节次</td><td>周次</td><td>单双周</td></tr></thead>
      <tbody><tr><td>[M01]每周实验</td><td>星期五[5-6节]</td><td>1-6,9-12</td><td>$parity</td></tr></tbody></table>"""

    private fun occurrences(courses: List<JwCourse>): List<String> = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter {
            course.type == 0 || it % 2 == course.type % 2
        }.flatMap { week -> (course.startNode..course.endNode).map { node ->
            "${course.name}|${course.day}|$node|$week"
        } }
    }.sorted()

    @Test fun `non parity labels preserve every listed week`() {
        val expected = listOf(1,2,3,4,5,6,9,10,11,12).flatMap { week ->
            listOf("每周实验|5|5|$week", "每周实验|5|6|$week")
        }.sorted()
        for (label in listOf("", "全", "全周", "每周", "无")) {
            val html = source(label)
            assertEquals(label, expected, occurrences(JwKingoParser(html).generateCourseList()))
            assertEquals(label, expected, occurrences(JwParserRegistry.selectBest(html, "kingo_new").first))
            assertEquals(label, expected, occurrences(JwImportViewModel.parseHtmlStatic(html, "kingo_new").first))
        }
    }

    @Test fun `label text containing odd or even uses that parity only`() {
        for ((label, weeks) in listOf("单周" to listOf(1,3,5,9,11), "双周" to listOf(2,4,6,10,12))) {
            val expected = weeks.flatMap { week -> listOf("每周实验|5|5|$week", "每周实验|5|6|$week") }.sorted()
            assertEquals(label, expected, occurrences(JwKingoParser(source(label)).generateCourseList()))
        }
    }
}

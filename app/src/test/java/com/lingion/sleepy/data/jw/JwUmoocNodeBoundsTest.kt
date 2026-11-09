package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic UMOOC cells; assertions expand only advertised week occurrences. */
class JwUmoocNodeBoundsTest {
    @Test fun cellIdentityKeepsHighSundayNodesAndSparseWeeks() {
        val source = table(row("7-31", "下午", block("离散数学", "A", "王老师", "1,3,7周")))
        assertEquals(listOf(1, 3, 7).map { JwCourse("离散数学", "A", "王老师", 7, 31, 31, it, it) }, occurrences(source))
    }

    @Test fun numberedHeadersKeepHighNodesAndOverrideTheCellIdentity() {
        val source = table(row("3-5", "第32节", block("离散数学", "A", "王老师", "2周")) +
            row("7-32", "第31节", block("物理", "B", "李老师", "4周")))
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 3, 32, 32, 2, 2),
            JwCourse("物理", "B", "李老师", 7, 31, 31, 4, 4)), occurrences(source))
    }

    @Test fun resourceGuardKeepsBoundaryHeadersAndCellCoordinates() {
        val source = table(row("1-366", "下午", block("边界课", "A", "王老师", "2周")) +
            row("3-5", "第366节", block("表头课", "B", "李老师", "4周")) +
            row("3-367", "下午", block("坏课", "C", "陈老师", "6周")) +
            row("3-5", "第367节", block("坏表头", "D", "周老师", "8周")))
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 1, 366, 366, 2, 2),
            JwCourse("表头课", "B", "李老师", 3, 366, 366, 4, 4)), occurrences(source))
    }

    @Test fun badCoordinatesAndWeeksNeverEraseAnIndependentHighCourse() {
        val bad = listOf("0-31", "8-31", "3-0", "3-2147483647", "3-1.5", "3-31-32")
            .joinToString("") { row(it, "下午", block("坏课", "X", "周老师", "2周")) } +
            row("3-31", "第0节", block("坏表头", "X", "周老师", "2周")) +
            row("3-31", "下午", block("坏周次", "X", "周老师", "1-8周(除x周)"))
        val source = table(bad + row("3-31", "下午", block("正确", "A", "王老师", "2周")))
        assertEquals(listOf(JwCourse("正确", "A", "王老师", 3, 31, 31, 2, 2)), occurrences(source))
    }

    private fun occurrences(source: String) = JwUmoocParser(source).generateCourseList().flatMap { course ->
        (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }
            .map { course.copy(startWeek = it, endWeek = it, type = 0) }
    }
    private fun block(name: String, room: String, teacher: String, weeks: String) =
        "&lt;&lt;$name&gt;&gt;<br>$room<br>$teacher<br>32学时<br>$weeks<br>"
    private fun row(id: String, header: String, content: String) =
        "<tr class='infolist_hr_common'><th>$header</th><td id='$id'>$content</td></tr>"
    private fun table(rows: String) = "<table id='timetable'>$rows</table>"
}

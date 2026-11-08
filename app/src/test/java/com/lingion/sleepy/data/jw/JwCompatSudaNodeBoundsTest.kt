package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic DataGrid1 pages calibrated against oo0OOoo's rowspan endpoint. */
class JwCompatSudaNodeBoundsTest {
    private fun course(name: String = "高节次实验") = "课程:$name<br>(C303)<br>第1-7周单<br>主讲教师:赵老师"
    private fun table(rows: String) = "<table id='DataGrid1'><tr align='center'><td>星期一</td></tr>$rows</table>"
    private fun emptyRows(count: Int) = "<tr><td></td></tr>".repeat(count)
    private fun expected(first: Int, last: Int, day: Int = 1, name: String = "高节次实验") =
        listOf(JwCourse(name, "C303", "赵老师", day, first, last, 1, 7, 1))

    @Test fun rowspanRetainsThirtyFirstSection() {
        assertEquals(expected(1, 31), JwSudaParser(table("<tr><td rowspan='31'>${course()}</td></tr>")).generateCourseList())
    }

    @Test fun highStartAndSpanRetainResourceBoundary() {
        val source = table(emptyRows(364) + "<tr><td rowspan='2'>${course()}</td></tr>")
        assertEquals(expected(365, 366), JwSudaParser(source).generateCourseList())
    }

    @Test fun invalidSpansNeverBecomeInventedSingleOrThirtySectionCourses() {
        for (span in listOf("0", "-1", "bad", "2147483648", "2147483647", "367")) {
            val source = table("<tr><td rowspan='$span'>${course("坏课")}</td><td>${course()}</td></tr>")
            assertEquals(span, expected(1, 1, day = 2), JwSudaParser(source).generateCourseList())
        }
    }

    @Test fun spanPastBoundaryIsRejectedAndDoesNotReleaseItsWeekdayColumn() {
        val source = table(emptyRows(364) +
            "<tr><td rowspan='3'>${course("越界课")}</td><td></td></tr>" +
            "<tr><td>${course()}</td></tr>")
        assertEquals(expected(366, 366, day = 2), JwSudaParser(source).generateCourseList())
    }

    @Test fun oversizedSpanReservesWeekdayWithoutIntegerOverflow() {
        val source = table("<tr><td rowspan='2147483647'>${course("坏课")}</td><td></td></tr>" +
            "<tr><td>${course()}</td></tr>")
        assertEquals(expected(2, 2, day = 2), JwSudaParser(source).generateCourseList())
    }

    @Test fun missingSpanRetainsSourceSingleSectionDefault() {
        assertEquals(expected(1, 1), JwSudaParser(table("<tr><td>${course()}</td></tr>")).generateCourseList())
    }

    @Test fun physicalRowsPastResourceBoundaryDoNotEmitCourses() {
        assertEquals(emptyList<JwCourse>(), WakeUpCompat.parseSuda(table(emptyRows(366) + "<tr><td>${course()}</td></tr>")))
    }
}

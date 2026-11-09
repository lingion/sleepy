package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzExplicitEndpointTest {
    @Test fun baseRejectsOverflowingExplicitWeekEnd() = rejectsWeekEnd("base")
    @Test fun withNodeRejectsOverflowingExplicitWeekEnd() = rejectsWeekEnd("withNode")
    @Test fun modernRejectsOverflowingExplicitWeekEnd() = rejectsWeekEnd("modern")
    @Test fun timetableRejectsOverflowingExplicitWeekEnd() = rejectsWeekEnd("timetable")

    @Test fun baseRejectsOverflowingExplicitNodeEnd() = rejectsNodeEnd("base")
    @Test fun withNodeRejectsOverflowingExplicitNodeEnd() = rejectsNodeEnd("withNode")
    @Test fun modernRejectsOverflowingExplicitNodeEnd() = rejectsNodeEnd("modern")
    @Test fun timetableRejectsOverflowingExplicitNodeEnd() = rejectsNodeEnd("timetable")

    @Test fun missingEndpointsStillMeanSingleWeekAndSingleNode() {
        for (route in routes) assertEquals(route, listOf(course("单值课程", 2, 2, 7, 7)),
            parse(route, listOf("单值课程" to "7周[2节]")))
    }

    @Test fun compactFourDigitsKeepPackedSemanticsAndExplicitPeriodsCanExceedNinetyNine() {
        for (route in routes.filter { it != "timetable" }) {
            assertEquals(route, listOf(course("紧凑课程", 98, 99, 3, 4)),
                parse(route, listOf("紧凑课程" to "3-4周[9899节]")))
            assertEquals(route, listOf(course("末节课程", 99, 99, 3, 4)),
                parse(route, listOf("末节课程" to "3-4周[9999节]")))
            assertEquals(route, listOf(course("高节次课程", 100, 101, 3, 4)),
                parse(route, listOf("高节次课程" to "3-4周[100-101节]")))
        }
    }

    @Test fun invalidWeekSegmentDoesNotRemoveAnotherExplicitValidSegment() {
        for (route in routes) assertEquals(route, listOf(course("分段课程", 1, 2, 5, 6)),
            parse(route, listOf("分段课程" to "2-2147483648周,5-6周[1-2节]")))
    }

    private fun rejectsWeekEnd(route: String) {
        for (end in overflowEnds) assertEquals("$route end=$end", listOf(goodCourse),
            parse(route, listOf("坏周次课程" to "2-${end}周[1-2节]", good)))
    }

    private fun rejectsNodeEnd(route: String) {
        for (end in overflowEnds) assertEquals("$route end=$end", listOf(goodCourse),
            parse(route, listOf("坏节次课程" to "2-4周[1-${end}节]", good)))
    }

    private fun parse(route: String, entries: List<Pair<String, String>>): List<JwCourse> {
        val html = if (route == "modern") {
            val records = entries.joinToString("") { (name, time) ->
                "<li class='qz-toolitiplists'><div class='qz-tooltipContent-title'>$name</div>" +
                    "<div class='qz-tooltipContent-detailitem'>老师：王老师</div>" +
                    "<div class='qz-tooltipContent-detailitem'>地点：A</div>" +
                    "<div class='qz-tooltipContent-detailitem'>时间：$time</div></li>"
            }
            "<table><tr><td>节次</td><td name='kbDataTd'><ul>$records</ul></td></tr></table>"
        } else {
            val blocks = entries.joinToString("<br>-----<br>") { (name, time) ->
                "<font>$name</font><br><font title='老师'>王老师</font><br>" +
                    "<font title='教室'>A</font><br><font title='周次(节次)'>$time</font>"
            }
            val id = if (route == "timetable") "timetable" else "kbtable"
            "<table id='$id'><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五", "六", "日").joinToString("") { "<th>星期$it</th>" } +
                "</tr><tr><td>第1大节</td><td><div class='kbcontent'>$blocks</div></td>" +
                "<td></td>".repeat(6) + "</tr></table>"
        }
        return when (route) {
            "withNode" -> JwQzWithNodeParser(html).generateCourseList()
            "timetable" -> JwQzTimetableParser(html).generateCourseList()
            else -> JwQzParser(html).generateCourseList()
        }
    }

    private fun course(name: String, first: Int, last: Int, from: Int, to: Int) =
        JwCourse(name, "A", "王老师", 1, first, last, from, to)

    private val routes = listOf("base", "withNode", "modern", "timetable")
    private val overflowEnds = listOf("2147483648", "9223372036854775808")
    private val good = "独立好课" to "3-4周[5-6节]"
    private val goodCourse = course("独立好课", 5, 6, 3, 4)
}

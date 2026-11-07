package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeSchoolGridTest {
    private fun parse(source: String, type: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        assertDeclaredHtmlFrame(type, source, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }

    @Test fun gxicMapsSundayFirstPreservesLocalParityAndSchoolClock() {
        val source = gxicTable("星期天", "星期一", "第0910节", "1-5(单),8", "授课教师：王老师\n开课地点：A\n上课周次：1-5(单),8\n", "离散数学")
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 7, 9, 10, 1, 5, 1, "19:40", "21:10"),
            JwCourse("离散数学", "A", "王老师", 7, 9, 10, 8, 8, 0, "19:40", "21:10"),
        ), parse(source, "gxic"))
    }

    @Test fun gxicMapsMondayFirstAndRejectsUnknownRowsAndWeeks() {
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 3, 4, 2, 8, 2, "10:30", "12:00")),
            parse(gxicTable("星期一", "星期天", "第0304节", "2-8(双)", name = "物理"), "gxic"))
        for ((node, week) in listOf("第1112节" to "1-8", "第0102节" to "8-1", "第0102节" to "0-8", "第0102节" to "1-367"))
            assertEquals(emptyList<JwCourse>(), parse(gxicTable("星期一", "星期天", node, week), "gxic"))
    }

    @Test fun gxnuUsesAriaFieldsAndIndependentSemicolonArrangements() {
        val source = gxnuTable("第1-5(单),8周 星期日 3,5节【A】;第2-8(双)周 星期二 9-10节【B】", "2.5")
        assertEquals(listOf(
            JwCourse("数学", "A", "王老师", 7, 3, 3, 1, 5, 1, credit = 2.5f),
            JwCourse("数学", "A", "王老师", 7, 3, 3, 8, 8, credit = 2.5f),
            JwCourse("数学", "A", "王老师", 7, 5, 5, 1, 5, 1, credit = 2.5f),
            JwCourse("数学", "A", "王老师", 7, 5, 5, 8, 8, credit = 2.5f),
            JwCourse("数学", "B", "王老师", 2, 9, 10, 2, 8, 2, credit = 2.5f),
        ), parse(source, "gxnu"))
    }

    @Test fun gxnuRejectsMalformedTimingAndNormalizesInvalidCredit() {
        for (credit in listOf("NaN", "Infinity", "-2")) assertEquals(
            listOf(JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 8)),
            parse(gxnuTable("第1-8周 星期一 1-2节【A】", credit), "gxnu"))
        for (timing in listOf("第8-1周 星期一 1-2节【A】", "第1-8周 星期八 1-2节【A】", "第1-8周 星期一 31节【A】", "第1-8周 星期一 1-2节", "第1-8周 星期一 未知节【A】"))
            assertEquals(emptyList<JwCourse>(), parse(gxnuTable(timing), "gxnu"))
    }

    @Test fun jxnuKeepsMappedPeriodRowsAndTeacherFallback() {
        val rows = (0..8).joinToString("") { row -> when (row) {
            0 -> "<tr><th>表头</th></tr>"
            1 -> "<tr><td bgcolor='#00ccff'>上午</td><td bgcolor='#cccccc'>1-2节</td><td><div>数学<br>(A)</div></td><td><div>英语<br>(B)</div></td></tr>"
            5 -> "<tr bgcolor='#00ccff'><td><div>午休</div></td></tr>"
            8 -> "<tr><td bgcolor='#cccccc'>10节</td><td><div>数学<br>(C)</div></td></tr>"
            else -> "<tr></tr>"
        } }
        val source = "<table id='_ctl1_dgStudentLesson'><tr><td>序号</td><td>数学</td><td></td><td></td><td>王老师</td></tr><tr><td></td><td>英语</td><td></td><td>李老师</td><td>忽略</td></tr></table>" +
            "<table id='_ctl1_NewKcb'>$rows</table>"
        assertEquals(listOf(
            JwCourse("数学", "A", "王老师", 1, 1, 2, 1, 20), JwCourse("英语", "B", "李老师", 2, 1, 2, 1, 20),
            JwCourse("数学", "C", "王老师", 1, 10, 10, 1, 20),
        ), parse(source, "jxnu"))
    }

    @Test fun jxnuDoesNotInventTeacherOrCourseFromEmptyAndRestCells() {
        val source = "<table id='_ctl1_NewKcb'><tr></tr><tr><td bgcolor='#cccccc'>标签</td><td><div>物理</div></td><td><div><br></div></td></tr></table>"
        assertEquals(listOf(JwCourse("物理", "", "", 1, 1, 2, 1, 20)), parse(source, "jxnu"))
        assertEquals(emptyList<JwCourse>(), parse("<table id='_ctl1_NewKcb'><tr><td>表头</td></tr></table>", "jxnu"))
    }

    @Test fun nnutcKeepsTeacherAfterRoomAndSparsePeriods() {
        val fields = listOf("calculator" to "数学", "environment" to "A[1-5,8单]", "user" to "王老师", "clock" to "第3,5节")
        val source = nnutcTable(fields)
        assertEquals(listOf(
            JwCourse("数学", "A", "王老师", 1, 3, 3, 1, 5, 1),
            JwCourse("数学", "A", "王老师", 1, 5, 5, 1, 5, 1),
        ), parse(source, "nnutc"))
    }

    @Test fun nnutcResetsTeacherAtNextCourseAndRejectsMismatchedIcons() {
        val fields = listOf("calculator" to "数学", "user" to "王老师", "environment" to "A[1-8]", "calculator" to "英语", "environment" to "B[2-6双]", "clock" to "第9-10节")
        assertEquals(listOf(JwCourse("数学", "A", "王老师", 1, 9, 10, 1, 8), JwCourse("英语", "B", "", 1, 9, 10, 2, 6, 2)),
            parse(nnutcTable(fields), "nnutc"))
        for (source in listOf(nnutcTable(fields).replace("data-icon=\"user\"", "data-other=\"user\""),
            nnutcTable(fields).replace("第9-10节", "第0节"), nnutcTable(fields).replace("A[1-8]", "A[8-1]"))) {
            // Invalid icon means the first teacher is unavailable; malformed timing
            // invalidates only that arrangement rather than changing other courses.
            if ("data-other" in source) assertEquals(listOf(JwCourse("数学", "A", "", 1, 9, 10, 1, 8), JwCourse("英语", "B", "", 1, 9, 10, 2, 6, 2)), parse(source, "nnutc"))
            else if ("第0节" in source) assertEquals(emptyList<JwCourse>(), parse(source, "nnutc"))
            else assertEquals(listOf(JwCourse("英语", "B", "", 1, 9, 10, 2, 6, 2)), parse(source, "nnutc"))
        }
        assertEquals(emptyList<JwCourse>(), parse(nnutcTable(fields).replaceFirst("<svg data-icon=\"calculator\"></svg>", ""), "nnutc"))
    }

    private fun gxicTable(firstDay: String, secondDay: String, node: String, weeks: String, title: String = "授课教师：王老师\n开课地点：A\n上课周次：$weeks\n", name: String = "数学") =
        "<table width='98%'><tr><th>节次</th><th>$firstDay</th><th>$secondDay</th></tr><tr><td>$node</td><td><a title='$title'>$name<br>备注</a></td>" + "<td></td>".repeat(6) + "</tr></table>"
    private fun gxnuTable(timing: String, credit: String = "2") = "<table id='xkjbgzszGridIdGrid'><tbody><tr>" +
        listOf("rkjs" to "王老师", "sksjdd" to timing, "xf" to credit, "kcmc" to "数学").joinToString("") { (key, value) -> "<td aria-describedby='xkjbgzszGridIdGrid_$key'>$value</td>" } + "</tr></tbody></table>"
    private fun nnutcTable(fields: List<Pair<String, String>>) = "<table><tbody><tr><td>标签</td><td><div class='course'>" +
        fields.joinToString("") { (icon, value) -> "<svg data-icon=\"$icon\"></svg><label>$value</label>" } + "</div></td>" + "<td></td>".repeat(6) + "</tr></tbody></table>"
}

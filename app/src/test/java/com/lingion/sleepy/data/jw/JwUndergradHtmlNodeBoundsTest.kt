package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic inputs retain each layout's own single-period or two-period coordinates. */
class JwUndergradHtmlNodeBoundsTest {
    @Test fun jmucyKeepsTheSixteenthPairAndSparseWeeks() {
        val source = jmucy(16, "离散数学 A 王老师 第1-4周,6周★英语 李老师 第2-8双周")
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 7, 31, 32, 1, 4),
            JwCourse("离散数学", "A", "王老师", 7, 31, 32, 6, 6),
            JwCourse("英语", "", "李老师", 7, 31, 32, 2, 8, 2),
        ), parse("jmucy", source))
    }

    @Test fun jmucyResourceGuardKeepsTheLastCompletePair() {
        val source = jmucy(183, "边界课 A 王老师 第2周", "越界课 B 李老师 第3周")
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 7, 365, 366, 2, 2)), parse("jmucy", source))
    }

    @Test fun nuaajcKeepsExplicitLengthAndDiscontinuousWeeks() {
        val source = nuaajc(nuaajcRow("离散数学", "31", "3", "1-4,6"))
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 7, 31, 33, 1, 4),
            JwCourse("离散数学", "A", "王老师", 7, 31, 33, 6, 6),
        ), parse("nuaajc", source))
    }

    @Test fun nuaajcRejectsOverflowAndResourceViolationsWithoutLosingGoodRows() {
        val rows = listOf("0" to "1", "367" to "1", "366" to "2", "31" to "0", "31" to "-1",
            "31" to "2147483647", "-2147483648" to "-2147483648")
            .joinToString("") { (node, length) -> nuaajcRow("坏课", node, length) } +
            nuaajcRow("边界课", "365", "2") + nuaajcRow("单节课", "366", "1") + nuaajcRow("正确", "1", "1")
        assertEquals(listOf(
            JwCourse("边界课", "A", "王老师", 7, 365, 366, 2, 2),
            JwCourse("单节课", "A", "王老师", 7, 366, 366, 2, 2),
            JwCourse("正确", "A", "王老师", 7, 1, 1, 2, 2),
        ), parse("nuaajc", nuaajc(rows)))
    }

    @Test fun qmuCountsOnlyCourseRowsAndKeepsItsOneOrTwoPeriodLength() {
        val source = qmu("<tr><th>表头</th></tr>" + qmuRow().repeat(15) +
            "<tr><th>中午</th></tr>" + qmuRow("离散数学<br>王老师<br>A<br>1-4.6周 单周 2节") +
            qmuRow("英语<br>李老师<br>B<br>2-8周 双周 1节"))
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 1, 31, 32, 1, 4, 1),
            JwCourse("离散数学", "A", "王老师", 1, 31, 32, 6, 6, 1),
            JwCourse("英语", "B", "李老师", 1, 33, 33, 2, 8, 2),
        ), parse("qmu", source))
    }

    @Test fun qmuResourceGuardKeepsTheLastPairAndRejectsZeroLength() {
        val source = qmu(qmuRow("坏课<br>王老师<br>A<br>2周 全周 0节") + qmuRow().repeat(181) +
            qmuRow("边界课<br>王老师<br>A<br>2周 全周 2节") + qmuRow("越界课<br>李老师<br>B<br>3周 全周 2节"))
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 1, 365, 366, 2, 2)), parse("qmu", source))
    }

    @Test fun hitszKeepsTheoryAndLabHighNodesWithoutFillingGaps() {
        val source = hitsz("离散数学 [王老师] [第1-8单周,10周][A] 第31,33节",
            "【实验】物理 [第35-36节][第2-8双周] [实验楼]")
        assertEquals(listOf(
            JwCourse("离散数学", "A", "王老师", 1, 31, 31, 1, 8, 1),
            JwCourse("离散数学", "A", "王老师", 1, 33, 33, 1, 8, 1),
            JwCourse("离散数学", "A", "王老师", 1, 31, 31, 10, 10),
            JwCourse("离散数学", "A", "王老师", 1, 33, 33, 10, 10),
            JwCourse("【实验】物理", "实验楼", "", 1, 35, 36, 2, 8, 2),
        ), parse("hitsz", source))
    }

    @Test fun hitszResourceGuardRejectsHugeNodesWhileKeepingIndependentGoodCards() {
        val source = hitsz("边界课 [王老师] [第2周][A] 第366节", "坏课 [李老师] [第3周][B] 第367节",
            "溢出课 [李老师] [第3周][B] 第2147483647节", "正确 [陈老师] [第4周][C] 第1节")
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 1, 366, 366, 2, 2),
            JwCourse("正确", "C", "陈老师", 1, 1, 1, 4, 4)), parse("hitsz", source))
    }

    @Test fun javtcStillRequiresOneOfItsFourDeclaredPairLabels() {
        val title = "编号\n代码\n课程名称：离散数学\n授课教师：王老师\n班级\n上课周次：2\n开课地点：A\n备注"
        fun row(label: String) = "<tr><td>$label</td><td><a title='$title'>课程</a></td>" + "<td></td>".repeat(6) + "</tr>"
        val source = "<table border='1' width='98%'><tr><th>课表</th></tr>" + row("第三十一三十二节") + row("第七八节") + "</table>"
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 1, 7, 8, 2, 2)), parse("javtc", source))
    }

    private fun parse(type: String, source: String) = JwUndergradHtmlParser(source, type).generateCourseList()
    private fun jmucy(group: Int, course: String, nextCourse: String = "") =
        "<table id='ctl00_ContentPlaceHolder3_ScheduleTable'><tr><th>课表</th></tr>" +
            "<tr><td></td></tr>".repeat(group - 1) + "<tr><td>节次</td>" + "<td></td>".repeat(6) +
            "<td>$course</td></tr>" + (if (nextCourse.isEmpty()) "" else "<tr><td>节次</td>" + "<td></td>".repeat(6) + "<td>$nextCourse</td></tr>") + "</table>"
    private fun nuaajc(rows: String) = "<table id='GrwCourseTablePrint'><tr>" +
        listOf("weekly", "unit", "kcm", "week", "roomid", "lsjs", "jsm").joinToString("") { "<th>$it</th>" } + "</tr>$rows</table>"
    private fun nuaajcRow(name: String, node: String, length: String, weeks: String = "2") =
        "<tr><td>$weeks</td><td>$node</td><td>$name</td><td>7</td><td>A</td><td>$length</td><td>王老师</td></tr>"
    private fun qmu(rows: String) = "<table frame='box'>$rows</table>"
    private fun qmuRow(course: String = "") = "<tr><td valign='top'>$course</td></tr>"
    private fun hitsz(vararg cards: String) = "<table><tbody class='ivu-table-tbody'><tr><td>节次</td><td>" +
        cards.joinToString("") { "<div class='ivu-card-body'>$it</div>" } + "</td></tr></tbody></table>"
}

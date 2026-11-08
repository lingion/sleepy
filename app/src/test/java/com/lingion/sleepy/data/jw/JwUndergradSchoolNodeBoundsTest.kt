package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic school forms verify coordinates and all associated fields together. */
class JwUndergradSchoolNodeBoundsTest {
    @Test fun hniuKeepsHighNodesAndSeparateWeekRanges() {
        val source = hniu("离散数学<br>王老师 [1-4,6周][31-33节]<br>A")
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 1, 31, 33, 1, 4),
            JwCourse("离散数学", "A", "王老师", 1, 31, 33, 6, 6)), parse("hniu", source))
    }

    @Test fun hniuResourceGuardPreservesIndependentGoodArrangements() {
        val source = hniu("边界课<br>王老师 [2周][366节]<br>A<br>坏课<br>李老师 [3周][367节]<br>B<br>正确<br>陈老师 [4周][1节]<br>C")
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 1, 366, 366, 2, 2),
            JwCourse("正确", "C", "陈老师", 1, 1, 1, 4, 4)), parse("hniu", source))
    }

    @Test fun sustechKeepsExplicitHighSectionsInsteadOfTheKeySection() {
        val source = sustech("离散数学[王老师][教学班-01][1-8单周,10周][A][第30-31节]")
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 7, 30, 31, 1, 8, 1, note = "01"),
            JwCourse("离散数学", "A", "王老师", 7, 30, 31, 10, 10, note = "01")), parse("sustech", source))
    }

    @Test fun sustechResourceGuardKeepsTheBoundaryAndGoodRows() {
        val source = "[" + listOf("边界课[王老师][班-1][2周][A][第366节]",
            "坏课[李老师][班-2][3周][B][第367节]", "正确[陈老师][班-3][4周][C][第1节]")
            .joinToString(",") { "{\"KEY\":\"xq7_jc1\",\"SKSJ\":\"$it\"}" } + "]"
        assertEquals(listOf(JwCourse("边界课", "A", "王老师", 7, 366, 366, 2, 2, note = "1"),
            JwCourse("正确", "C", "陈老师", 7, 1, 1, 4, 4, note = "3")), parse("sustech", source))
    }

    @Test fun uicKeepsPhysicalRowsSundayAndTeacherLookup() {
        val source = uic("<tr></tr>".repeat(30) + uicRow("离散数学", 7) + uicRow("离散数学", 1))
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 7, 31, 31, 1, 20),
            JwCourse("离散数学", "A", "王老师", 1, 32, 32, 1, 20)), parse("uic", source))
    }

    @Test fun uicResourceGuardKeepsTheLastSupportedRow() {
        val source = uic("<tr></tr>".repeat(365) + uicRow("离散数学", 2).repeat(2))
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 2, 366, 366, 1, 20)), parse("uic", source))
    }

    @Test fun cityuKeepsHighRowSpansAndTheNextWeekday() {
        val source = cityu("<tr></tr>".repeat(30) + "<tr><td rowspan='2'>代码<br>离散数学<br>A</td></tr>" +
            "<tr><td>代码<br>物理<br>B</td></tr>")
        assertEquals(listOf(JwCourse("离散数学", "A", "", 1, 31, 32, 1, 20),
            JwCourse("物理", "B", "", 2, 32, 32, 1, 20)), parse("cityu", source))
    }

    @Test fun cityuResourceGuardKeepsTheLastSupportedRow() {
        val source = cityu("<tr></tr>".repeat(365) + "<tr><td>代码<br>边界课<br>A</td></tr>" +
            "<tr><td>代码<br>过界课<br>B</td></tr>")
        assertEquals(listOf(JwCourse("边界课", "A", "", 1, 366, 366, 1, 20)), parse("cityu", source))
    }

    @Test fun cityuOverflowGuardKeepsTheSpannedColumnOccupied() {
        val source = cityu("<tr></tr>".repeat(30) +
            "<tr><td rowspan='2147483647'>代码<br>坏课<br>X</td><td>代码<br>正确<br>C</td></tr>" +
            "<tr><td>代码<br>后续课<br>D</td></tr>")
        assertEquals(listOf(JwCourse("正确", "C", "", 2, 31, 31, 1, 20),
            JwCourse("后续课", "D", "", 2, 32, 32, 1, 20)), parse("cityu", source))
    }

    @Test fun stuKeepsHighLetterRunsAndNeverFillsUnadvertisedGaps() {
        val source = stu(stuRow("离散数学", "UVWZ双", "") + stuRow("物理", "", "U-Z单"))
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 7, 31, 33, 2, 16, 2, credit = 2.5f),
            JwCourse("离散数学", "A", "王老师", 7, 36, 36, 2, 16, 2, credit = 2.5f),
            JwCourse("物理", "A", "王老师", 1, 31, 36, 2, 16, 1, credit = 2.5f)), parse("stu", source))
    }

    @Test fun stuStillRejectsUnknownCodesAndReversedRanges() {
        val source = stu(listOf("Z-U", "UVa", "0", "U-?", "U单双").joinToString("") { stuRow("坏课", it, "") } +
            stuRow("正确", "Z", ""))
        assertEquals(listOf(JwCourse("正确", "A", "王老师", 7, 36, 36, 2, 16, credit = 2.5f)), parse("stu", source))
    }

    @Test fun nfuKeepsHighSundayNodesAndOptionalRooms() {
        val source = nfu("第1-8周 第31-32节 王老师 A 单周/第10周 第35节 李老师")
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 7, 31, 32, 1, 8, 1),
            JwCourse("离散数学", "", "李老师", 7, 35, 35, 10, 10)), parse("nfu", source))
    }

    @Test fun nfuResourceGuardKeepsGoodArrangementsAfterBadOnes() {
        val source = nfu("第2周 第367节 李老师 B/第3周 第366节 王老师 A/第4周 第1节 陈老师 C")
        assertEquals(listOf(JwCourse("离散数学", "A", "王老师", 7, 366, 366, 3, 3),
            JwCourse("离散数学", "C", "陈老师", 7, 1, 1, 4, 4)), parse("nfu", source))
    }

    private fun parse(type: String, source: String) = JwUndergradSchoolParser(source, type).generateCourseList()
    private fun hniu(content: String) = "<table bordercolordark='#FFFFFF'><tbody><tr><td align='center'>时段</td><td valign='top'>$content</td></tr></tbody></table>"
    private fun sustech(text: String) = "[{\"KEY\":\"xq7_jc1\",\"SKSJ\":\"$text\"}]"
    private fun uic(rows: String) = "<table class='tablestyle-2'><tbody><tr><td>1</td><td>x</td><td>离散数学</td><td>王老师</td></tr></tbody></table>" +
        "<table id='mytimetable'><tr><th>课表</th></tr>$rows</table>"
    private fun uicRow(name: String, day: Int) = "<tr>" + "<td></td>".repeat(day - 1) + "<td>$name<br>A</td></tr>"
    private fun cityu(rows: String) = "<table class='ctt-matrix'><tr><th>课表</th></tr>$rows</table>"
    private fun stu(rows: String) = "<table id='DataGrid1'>$rows</table>"
    private fun stuRow(name: String, sunday: String, monday: String) = "<tr><td>1</td><td>[CS101]$name</td><td>2.5</td><td>王老师</td><td>A</td><td>2-16</td><td>$sunday</td><td>$monday</td></tr>"
    private fun nfu(content: String) = "<table><tr class='ui-widget-content'><td><span>离散数学</span>" +
        "<span></span>".repeat(8) + "<span>$content</span></td></tr></table>"
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBGridTest {
    private data class Occurrence(val name: String, val room: String, val teacher: String, val week: Int, val day: Int, val node: Int,
        val credit: Float = 0f, val note: String = "", val start: String = "", val end: String = "")
    private fun actual(rows: List<JwCourse>) = rows.flatMap { c -> (c.startWeek..c.endWeek)
        .filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { week -> (c.startNode..c.endNode).map { node ->
            Occurrence(c.name, c.room, c.teacher, week, c.day, node, c.credit, c.note, c.startTime, c.endTime)
        } } }.toSet()
    private fun expected(name: String, room: String, teacher: String, weeks: List<Int>, day: Int, nodes: List<Int>) =
        weeks.flatMap { w -> nodes.map { n -> Occurrence(name, room, teacher, w, day, n) } }.toSet()
    private fun parse(html: String, type: String) = assertUndergradBImportRoutes(html, type)
    private fun cell(content: String = "", span: Int = 1) = "<td rowspan='$span'>$content</td>"

    @Test fun sicnuReadsThreeTwoAndOneLineCoursesWithInheritedName() {
        val content = "数学**<br>王师<br>A(2-6周);英语*<br>李师[B(1-5周);赵师[C(7周)"
        val html = "<table class='PintTableStyle'><tr><td width='10'>1</td>" + cell("<span>$content</span>", 2) + "</tr></table>"
        assertEquals(expected("数学", "A", "王师", listOf(2,4,6), 1, listOf(1,2)) +
            expected("英语", "B", "李师", listOf(1,3,5), 1, listOf(1,2)) +
            expected("英语", "C", "赵师", listOf(7), 1, listOf(1,2)), actual(parse(html, "sicnu")))
    }

    @Test fun sicnuMaintainsColumnsAfterEmptyRowAndNewLongerSpan() {
        val html = "<table class='PintTableStyle'><tr><td colspan='8'>上午</td></tr>" +
            "<tr><td width='10'>1</td>" + cell("数学<br>王师<br>A(1-2周)", 2) + cell() + "</tr>" +
            "<tr><td width='10'>2</td>" + cell() + "</tr>" +
            "<tr><td width='10'>3</td>" + cell("物理<br>李师<br>B(4周)", 3) + cell() + "</tr>" +
            "<tr><td width='10'>4</td>" + cell("化学<br>赵师<br>C(6周)") + "</tr></table>"
        assertEquals(expected("数学", "A", "王师", listOf(1,2), 1, listOf(1,2)) +
            expected("物理", "B", "李师", listOf(4), 1, listOf(3,4,5)) +
            expected("化学", "C", "赵师", listOf(6), 2, listOf(4)), actual(parse(html, "sicnu")))
    }

    @Test fun sicnuRejectsBadRangesAndUnboundContinuation() {
        val html = "<table class='PintTableStyle'><tr>" + cell("王师[A(1-8周);数学<br>李师<br>B(9-2周)") + "</tr></table>"
        assertEquals(emptySet<Occurrence>(), actual(parse(html, "sicnu")))
    }

    private fun nuist(content: String, label: String = "1～2", extra: String = "") =
        "<table id='TABLE1'><tr><td>时间</td><td>星期一</td><td>星期二</td></tr><tr><td>$label</td><td>$content</td><td></td></tr>$extra</table>"

    @Test fun nuistDecodesFourAndThreeDigitNodesAndMetadataWeeks() {
        val html = nuist("数学◇王师(2-6)◇}A◇x◇}B◇2-6{1112节}双◆物理◇李师(1-3)◇}C◇x◇}◇1-3{910节}单")
        assertEquals(expected("数学", "B", "王师", listOf(2,4,6), 1, listOf(11,12)) +
            expected("物理", "C", "李师", listOf(1,3), 1, listOf(9,10)), actual(parse(html, "nuist")))
    }

    @Test fun nuistUsesFieldThreeWeeksAndRowNodesWhenPackedValueAbsent() {
        val html = nuist("英语◇周师(备注)◇}A◇周次(4-6)◇}◇4-6{节}", extra =
            "<tr><td>3～4</td><td></td><td>化学◇赵师()◇}D◇()◇}◇8-10{节}双</td></tr>")
        assertEquals(expected("英语", "A", "周师", listOf(4,5,6), 1, listOf(1,2)) +
            expected("化学", "D", "赵师", listOf(8,10), 2, listOf(3,4)), actual(parse(html, "nuist")))
    }

    @Test fun nuistSingleRowsDoNotBecomePairsAndBadWeeksDoNotUseDefaults() {
        val html = nuist("数学◇王师(8-2)◇}A◇x◇}◇8-2{12节}◆英语◇李师(6-6)◇}B◇x◇}◇6-6{节}", "1")
        assertEquals(expected("英语", "B", "李师", listOf(6), 1, listOf(1)), actual(parse(html, "nuist")))
    }

    private fun sitTable(rows: String) = "<form><table><tr><th>头</th></tr><tr><th>头二</th></tr>$rows</table></form>"
    private fun sitEntry(name: String, week: String, room: String = "A", teacher: String = "王师") = "<div name='d1'>$name $week $room x $teacher</div>"

    @Test fun sitReadsStarParityAndRowspanAsNodes() {
        val html = sitTable("<tr><td>1</td>" + cell(sitEntry("数学", "(2-6周**"), 2) + cell(sitEntry("英语", "(5周*", "B", "李师")) + "</tr>")
        assertEquals(expected("数学", "A", "王师", listOf(2,4,6), 1, listOf(1,2)) +
            expected("英语", "B", "李师", listOf(5), 2, listOf(1)), actual(parse(html, "sit")))
    }

    @Test fun sitKeepsThreeRowOccupancyAcrossShrinkingHtmlRows() {
        val html = sitTable("<tr><td>1</td>" + cell(sitEntry("数学", "(1-2周"), 3) + cell() + "</tr>" +
            "<tr><td>2</td>" + cell() + "</tr>" +
            "<tr><td>3</td>" + cell(sitEntry("物理", "(4周", "B", "李师")) + "</tr>" +
            "<tr><td>4</td>" + cell(sitEntry("化学", "(6周", "C", "赵师")) + "</tr>")
        assertEquals(expected("数学", "A", "王师", listOf(1,2), 1, listOf(1,2,3)) +
            expected("物理", "B", "李师", listOf(4), 2, listOf(3)) +
            expected("化学", "C", "赵师", listOf(6), 1, listOf(4)), actual(parse(html, "sit")))
    }

    @Test fun sitRejectsBadWeekAndDoesNotTreatLoginAsTable() {
        assertEquals(emptySet<Occurrence>(), actual(parse(sitTable("<tr><td>1</td>" + cell(sitEntry("数学", "(8-2周")) + "</tr>"), "sit")))
        assertEquals(emptySet<Occurrence>(), actual(parse("<form><input name='password'></form>", "sit")))
    }

    @Test fun sitUsesLastBodyAndAcceptsSourceWeekPrefix() {
        val schedule = sitTable("<tr><td>1</td>" + cell(sitEntry("物理", "第2-6周**", "B", "李师")) + "</tr>")
        val earlier = sitTable("<tr><td>1</td>" + cell(sitEntry("旧数据", "(1周")) + "</tr>")
        assertEquals(expected("物理", "B", "李师", listOf(2,4,6), 1, listOf(1)),
            actual(parse(earlier + "<body>" + schedule, "sit")))
    }

    @Test fun gridBoundsUsePositiveIntegersAndRejectOnlyOverflowingEnd() {
        val html = sitTable("<tr><td>31</td>" + cell(sitEntry("物理", "第400-401周", "B", "李师"), 2) + "</tr>")
        assertEquals(expected("物理", "B", "李师", listOf(400,401), 1, listOf(31,32)), actual(parse(html, "sit")))
        val overflow = sitTable("<tr><td>2147483647</td>" + cell(sitEntry("物理", "第1周"), 2) + "</tr>")
        assertEquals(emptyList<JwCourse>(), parse(overflow, "sit"))
        val last = sitTable("<tr><td>2147483647</td>" + cell(sitEntry("物理", "第1周")) + "</tr>")
        assertEquals(listOf(JwCourse("物理", "A", "王师", 1, Int.MAX_VALUE, Int.MAX_VALUE, 1, 1)), parse(last, "sit"))
    }

    private fun sysuTable(first: String, second: String, rows: String) =
        "<table class='com-table class-schedule-table2'><tr><th class='th2'>节次</th><th colspan='2'>$first</th><th colspan='3'>$second</th></tr></table>" +
            "<table class='com-table class-schedule-table2'><tbody>$rows</tbody></table>"
    private fun sysuEntry(name: String = "数学", weeks: String = "1-5单周") = "<span>$weeks</span><span>(001)$name</span><span>王/师</span><span>A/B</span>"

    @Test fun sysuUsesSundayFirstAndColspanGrouping() {
        val row = "<tr><td class='td2-1'>1</td><td class='11111' colspan='1'></td><td colspan='1' rowspan='2'>" + sysuEntry() +
            "</td><td colspan='3' rowspan='1'>" + sysuEntry("英语", "8-8每周") + "</td></tr>"
        assertEquals(expected("数学", "AB", "王师", listOf(1,3,5), 7, listOf(1,2)) +
            expected("英语", "AB", "王师", listOf(8), 1, listOf(1)), actual(parse(sysuTable("星期日", "星期一", row), "sysu")))
    }

    @Test fun sysuMondayFirstAndMultipleHeaderBoundaries() {
        val row = "<tr><td class='td2-1'>1</td><td colspan='2' rowspan='1'>" + sysuEntry("物理", "2-6双周") +
            "</td><td colspan='3' rowspan='1'>" + sysuEntry("化学", "7-7每周") + "</td></tr>"
        assertEquals(expected("物理", "AB", "王师", listOf(2,4,6), 1, listOf(1)) +
            expected("化学", "AB", "王师", listOf(7), 2, listOf(1)), actual(parse(sysuTable("星期一", "星期日", row), "sysu")))
    }

    @Test fun sysuRejectsCellCrossingDayBoundaryAndMissingSpans() {
        val row = "<tr><td colspan='3' rowspan='1'>" + sysuEntry() + "</td></tr>"
        assertEquals(emptySet<Occurrence>(), actual(parse(sysuTable("星期一", "星期日", row), "sysu")))
    }
}

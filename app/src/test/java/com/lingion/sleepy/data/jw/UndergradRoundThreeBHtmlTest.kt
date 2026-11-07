package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBHtmlTest {
    private data class Occurrence(val name: String, val room: String, val teacher: String, val week: Int,
        val day: Int, val node: Int, val credit: Float, val note: String, val start: String = "", val end: String = "")
    private fun actual(rows: List<JwCourse>) = rows.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { week ->
            (c.startNode..c.endNode).map { node -> Occurrence(c.name, c.room, c.teacher, week, c.day, node, c.credit, c.note, c.startTime, c.endTime) }
        }
    }.toSet()
    private fun expected(name: String, room: String, teacher: String, weeks: List<Int>, day: Int,
        nodes: List<Int>, credit: Float = 0f, note: String = "") = weeks.flatMap { week -> nodes.map { node ->
            Occurrence(name, room, teacher, week, day, node, credit, note)
        } }.toSet()
    private fun parse(html: String, type: String) = assertUndergradBImportRoutes(html, type)

    @Test fun huatUsesNestedBodyAndFifthRowHasThreeNodes() {
        val rows = (0..4).joinToString("") { row -> "<tr valign='middle'><td align='center'>标签</td>" +
            (if (row == 4) "<td><table><tbody><tr><td>数学 王师 H 1201 1-3周</td></tr></tbody></table></td>" else "<td></td>") +
            "<td><table><tbody><tr><td>英语 王师 H 1301 2-4周</td></tr></tbody></table></td></tr>" }
        val courses = parse("<table id='ctl00_ContentPlaceHolder1_CourseTable'>$rows</table>", "huat")
        assertEquals(expected("数学", "1201", "王师", listOf(1,2,3), 1, listOf(9,10,11)) +
            (0..4).flatMap { r -> expected("英语", "1301", "王师", listOf(2,3,4), 2,
                if (r == 4) listOf(9,10,11) else listOf(r*2+1,r*2+2)) }.toSet(), actual(courses))
    }

    @Test fun huatDoesNotInventCoursesForMalformedTail() {
        val raw = "<table id='ctl00_ContentPlaceHolder1_CourseTable'><tr valign='middle'><td><table><tr><td>数学 王师 H 1201 9-2周</td></tr></table></td></tr></table>"
        assertEquals(emptySet<Occurrence>(), actual(parse(raw, "huat")))
        assertEquals(emptyList<JwCourse>(), parse("<table><tr><td>数学</td></tr></table>", "huat"))
    }

    @Test fun huatRetainsHyphenInCourseNameWhileSplittingOnlyWeekTail() {
        val html = "<table id='ctl00_ContentPlaceHolder1_CourseTable'><tr valign='middle'><td><table><tr><td>设计-实验 王师 H 1201 1-3周</td></tr></table></td></tr></table>"
        assertEquals(expected("设计-实验", "1201", "王师", listOf(1,2,3), 1, listOf(1,2)), actual(parse(html, "huat")))
    }

    @Test fun seu2017JoinsTeacherCreditAndRetainsSeparateWeeks() {
        val html = "<table class='tableline'><tr><th>头</th></tr><tr><td>1</td><td>数学</td><td>陈师</td><td>2.5</td></tr></table>" +
            "<table class='tableline'><tr><th>头</th></tr><tr><td>上午</td><td>1-2节</td><td>数学<br>[1-5周]3-4节<br>A(单)<br>英语<br>[8周]5节<br>B</td></tr></table>"
        assertEquals(expected("数学", "A", "陈师", listOf(1,3,5), 1, listOf(3,4), 2.5f) +
            expected("英语", "B", "", listOf(8), 1, listOf(5)), actual(parse(html, "seu_2017")))
        assertEquals(emptySet<Occurrence>(), actual(parse(html.replace("3-4节", "4-3节").replace("[8周]5节", "[8周]0节"), "seu_2017")))
    }

    private fun shuCard(time: String, name: String = "数学") = "<div class='arranged-content'><div class='el-card arranged-course-card'>" +
        listOf("#" to "001", "课程名" to name, "学分" to "3.5", "上课教师" to "陈师", "上课时间" to time, "上课地点" to "A101").joinToString("") { (key, value) ->
            "<div class='card-item cv-clearfix'><div class='label cv-pull-left'>$key:</div><div class='value cv-pull-left'>$value</div></div>"
        } + "</div></div>"

    @Test fun shuPreservesSparseOddWeeksAndSecondArrangement() {
        val html = shuCard("一3-4单(1-3周,9周) 日11-12(2周,6周)")
        assertEquals(expected("数学", "A101", "陈师", listOf(1,3,9), 1, listOf(3,4), 3.5f) +
            expected("数学", "A101", "陈师", listOf(2,6), 7, listOf(11,12), 3.5f), actual(parse(html, "shu2024")))
        assertEquals(emptySet<Occurrence>(), actual(parse(shuCard("一3-4单(2周)"), "shu2024")))
    }

    @Test fun shuFallsBackToDesktopAndUsesTenWeekScheduleDefault() {
        val cells = listOf("1", "物理", "代码", "2", "班级", "周师", "二1-2", "B", "x", "x", "x")
        val html = "<div class='arranged-content'><table class='el-table__body'><tbody><tr class='el-table__row'>" +
            cells.joinToString("") { "<td>$it</td>" } + "</tr></tbody></table></div>"
        assertEquals(expected("物理", "B", "周师", (1..10).toList(), 2, listOf(1,2), 2f), actual(parse(html, "shu2024")))
        assertEquals("26-27秋", JwUndergradBHtmlParser("<a class='gologin-btn'><span class='el-link--inner'>2026-2027学年秋季学期</span></a>", "shu2024").tableName())
        assertEquals(Triple(12, "20:55", "21:40"), JwUndergradBHtmlParser.shuPeriods.last())
    }

    @Test fun shuRejectsMalformedNonemptyWeekOrNodeTokensCompletely() {
        for (timing in listOf("一3-4(abc5)", "一3-4(第2-坏5)", "一3-4(1周,abc5)", "前缀一3-4(2周)",
            "一3-4后缀(2周)", "一3-4(2周)后缀", "一3-4(3-2周)", "一3-4(2147483648周)")) {
            assertEquals(timing, emptySet<Occurrence>(), actual(parse(shuCard(timing), "shu2024")))
        }
    }

    @Test fun sourceDoesNotImposeThirtyNodeOr366WeekLimitsAndDoesNotEnumerateHugeRanges() {
        val shu = parse(shuCard("一31-32(400-401周)"), "shu2024")
        assertEquals(expected("数学", "A101", "陈师", listOf(400,401), 1, listOf(31,32), 3.5f), actual(shu))
        val large = parse(shuCard("一31-32单(1-2147483647周)"), "shu2024")
        assertEquals(listOf(JwCourse("数学", "A101", "陈师", 1, 31, 32, 1, Int.MAX_VALUE, 1, credit = 3.5f)), large)
    }

    @Test fun tjuUsesHeadersAndIndependentWeekFragments() {
        val labels = listOf("备注", "上课地点", "教师", "课程名称", "学分", "上课时间")
        val values = listOf("实验", "B210", "陈师(主讲)王师(助教)", "数据库", "2.5", "星期一 3-4节[1-5单,8],星期三 7节[2-4双]")
        val html = "<table class='el-table__header'><tr>" + labels.joinToString("") { "<th>$it</th>" } + "</tr></table>" +
            "<table class='el-table__body'><tr>" + values.joinToString("") { "<td>$it</td>" } + "</tr></table>"
        assertEquals(expected("数据库", "B210", "陈师,王师", listOf(1,3,5,8), 1, listOf(3,4), 2.5f, "实验") +
            expected("数据库", "B210", "陈师,王师", listOf(2,4), 3, listOf(7), 2.5f, "实验"), actual(parse(html, "tju")))
        assertEquals(emptySet<Occurrence>(), actual(parse(html.replace("3-4节", "4-3节").replace("7节", "0节"), "tju")))
    }

    @Test fun tjuReadsEmbeddedRoomWhenLocationColumnIsAbsent() {
        val html = "<table class='el-table__header'><tr><th>课程名称</th><th>教师</th><th>教学安排</th></tr></table>" +
            "<table class='el-table__body'><tr><td>物理</td><td>李师</td><td>星期日 9-10节[6]D</td></tr></table>"
        assertEquals(expected("物理", "D", "李师", listOf(6), 7, listOf(9,10)), actual(parse(html, "tju")))
    }

    @Test fun hquTransposesDayAndNodeAndRoomLengthOverridesLastPair() {
        val plan = "<table id='CoursePlanTable'><tr><td>数学</td><td>李师</td><td>4</td></tr></table>"
        val cells = "<td>星期一</td>" + "<td></td>".repeat(5) + "<td><div>代码 数学</div><div>C(2节)</div><div>1-5周</div><div>单周</div></td>"
        val html = plan + "<table id='ArrangeTable'><tr><th>头</th></tr><tr>$cells</tr></table>"
        assertEquals(expected("数学", "C", "李师", listOf(1,3,5), 1, listOf(11,12), 4f), actual(parse(html, "hqu")))
        assertEquals(expected("数学", "C", "李师", listOf(1,3,5), 1, listOf(11,12,13), 4f), actual(parse(html.replace("(2节)", ""), "hqu")))
    }

    @Test fun hquKeepsDayAcrossEmptyRowAndUnknownPlan() {
        val html = "<table id='ArrangeTable'><tr><th>头</th></tr><tr><td>周一</td></tr><tr><td>周二</td><td><div>未知课</div><div>A</div><div>4周</div></td></tr></table>"
        assertEquals(expected("未知课", "A", "", listOf(4), 2, listOf(1,2)), actual(parse(html, "hqu")))
    }

    @Test fun changzhouJoinsTeacherAndKeepsSlashNameAndSingleWeeks() {
        val html = "<table id='GVxkall'><tr class='dg1-item'><td>x</td><td>数学</td><td>x</td><td>x</td><td>x</td><td>李师</td></tr></table>" +
            "<table id='GVxkkb'><tr class='dg1-item'><td>第一节</td><td>数学 A 单 1-5,8</td><td>实验/物理 B 全 2,6</td></tr>" +
            "<tr class='dg1-item'><td>第二节</td><td>数学 C 双 2-4</td></tr></table>"
        assertEquals(expected("数学", "A", "李师", listOf(1,3,5), 1, listOf(1)) +
            expected("实验/物理", "B", "", listOf(2,6), 2, listOf(1)) +
            expected("数学", "C", "李师", listOf(2,4), 1, listOf(2)), actual(parse(html, "changzhou")))
    }

    @Test fun changzhouRejectsReversedZeroAndIntegerOverflowWeeks() {
        for (week in listOf("9-2", "0-8", "1-2147483648")) {
            val html = "<table id='GVxkkb'><tr class='dg1-item'><td>1</td><td>数学 A 全 $week</td></tr></table>"
            assertEquals(emptySet<Occurrence>(), actual(parse(html, "changzhou")))
        }
    }

    @Test fun changzhouAllowsPositiveWeekOutsideFormerArtificialBound() {
        val html = "<table id='GVxkkb'><tr class='dg1-item'><td>1</td><td>数学 A 全 400-401</td></tr></table>"
        assertEquals(expected("数学", "A", "", listOf(400,401), 1, listOf(1)), actual(parse(html, "changzhou")))
    }
}

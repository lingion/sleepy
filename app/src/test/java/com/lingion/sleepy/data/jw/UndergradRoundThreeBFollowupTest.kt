package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBFollowupTest {
    private data class Occurrence(val name: String, val room: String, val teacher: String, val week: Int,
        val day: Int, val node: Int, val credit: Float)
    private fun actual(courses: List<JwCourse>) = courses.flatMap { c -> (c.startWeek..c.endWeek)
        .filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { w -> (c.startNode..c.endNode).map { n ->
            Occurrence(c.name,c.room,c.teacher,w,c.day,n,c.credit)
        } } }.toSet()
    private fun expected(name: String, room: String, teacher: String, day: Int, nodes: List<Int>,
        weeks: List<Int> = listOf(1), credit: Float = 0f) = weeks.flatMap { w -> nodes.map { n ->
            Occurrence(name,room,teacher,w,day,n,credit)
        } }.toSet()

    private fun sysuEntry(name: String) = "<span>1-1每周</span><span>(001)$name</span><span>王师</span><span>A</span>"
    private fun sysu(rows: String) = "<table class='com-table class-schedule-table2'><tr><th class='th2'>节</th>" +
        "<th colspan='2'>星期一</th><th colspan='3'>星期二</th></tr></table>" +
        "<table class='com-table class-schedule-table2'><tbody>$rows</tbody></table>"
    private fun td(name: String, colspan: Int, rowspan: Int = 1) = "<td colspan='$colspan' rowspan='$rowspan'>${sysuEntry(name)}</td>"

    @Test fun sysuKeepsTwoAndThreeRowPhysicalOccupancyWithDifferentColumnWidths() {
        val html = sysu("<tr>"+td("数学",2,3)+td("物理",1,2)+td("英语",2)+"</tr>"+
            "<tr>"+td("化学",2)+"</tr>"+
            "<tr>"+td("地理",1,2)+td("历史",2)+"</tr>"+
            "<tr>"+td("艺术",2)+td("体育",2)+"</tr>")
        val want = expected("数学","A","王师",1,listOf(1,2,3)) + expected("物理","A","王师",2,listOf(1,2)) +
            expected("英语","A","王师",2,listOf(1)) + expected("化学","A","王师",2,listOf(2)) +
            expected("地理","A","王师",2,listOf(3,4)) + expected("历史","A","王师",2,listOf(3)) +
            expected("艺术","A","王师",1,listOf(4)) + expected("体育","A","王师",2,listOf(4))
        assertEquals(want, actual(assertUndergradBImportRoutes(source = html, type = "sysu")))
    }

    @Test fun sysuEmptyRowspanCellsStillOccupyTheirPhysicalColumns() {
        val html = sysu("<tr><td class='11111' colspan='2' rowspan='2'></td>"+td("英语",3)+"</tr>"+
            "<tr>"+td("物理",3)+"</tr><tr>"+td("数学",2)+td("化学",3)+"</tr>")
        assertEquals(expected("英语","A","王师",2,listOf(1)) + expected("物理","A","王师",2,listOf(2)) +
            expected("数学","A","王师",1,listOf(3)) + expected("化学","A","王师",2,listOf(3)),
            actual(assertUndergradBImportRoutes(source = html, type = "sysu")))
    }

    @Test fun hquMatchesTheLongestUniquePlanNameInsteadOfItsShortSuffix() {
        val plans = "<table id='CoursePlanTable'><tr><td>数学</td><td>短名教师</td><td>1</td></tr>"+
            "<tr><td>高等数学</td><td>长名教师</td><td>4</td></tr></table>"
        val arrangement = "<table id='ArrangeTable'><tr><td>头</td></tr><tr><td>星期一</td><td>"+
            "<div>代码 高等数学</div><div>A</div><div>1-1周</div></td></tr></table>"
        assertEquals(expected("高等数学","A","长名教师",1,listOf(1,2),credit=4f),
            actual(assertUndergradBImportRoutes(source = plans+arrangement, type = "hqu")))
    }

    @Test fun huatHasNoActualCreditFieldAndKeepsUnknownCreditZero() {
        val html = "<table id='ctl00_ContentPlaceHolder1_CourseTable'><tr valign='middle'><td>"+
            "<table><tbody><tr><td>设计-实验 王师 H 1201 1-1周</td></tr></tbody></table></td></tr></table>"
        assertEquals(expected("设计-实验","1201","王师",1,listOf(1,2)),
            actual(assertUndergradBImportRoutes(source = html, type = "huat")))
    }

    @Test fun suesNegativeCreditIsUnknownAndNormalizedToZero() {
        val source = """{"studentTableVm":{"activities":[{"courseName":"数学","room":"A","weekday":1,
            "startUnit":1,"endUnit":1,"weekIndexes":[1],"teachers":["王师"],"credits":-2}]}}"""
        assertEquals(expected("数学","A","王师",1,listOf(1)),actual(assertUndergradAJsonImportRoutes("sues", source)))
    }

    @Test fun pkuRejectsTheWholeArrangementWhenAnyWeekFragmentIsMalformed() {
        for (weeks in listOf("1~2,bad", "1~2,0", "1~2,9~3", "1~2,2147483648")) {
            val cells=listOf("数学","","","","王师","","","$weeks 周一1~2节 A","已选","","")
            val html="<table class='datagrid'><tbody><tr>"+cells.joinToString(""){"<td>$it</td>"}+"</tr></tbody></table>"
            assertEquals(weeks,emptyList<JwCourse>(),JwPekingParser(html).generateCourseList())
        }
    }

    @Test fun sharedBWeekParsingRejectsPartialMalformedListsInsteadOfKeepingValidPrefix() {
        for (weeks in listOf("1-2,bad", "1-2,0", "1-2,9-3", "1-2,2147483648")) {
            val html="<table class='el-table__header'><tr><th>课程名称</th><th>教师</th><th>上课时间</th></tr></table>"+
                "<table class='el-table__body'><tr><td>数学</td><td>王师</td><td>星期一 1-2节[$weeks]A</td></tr></table>"
            assertEquals(weeks,emptyList<JwCourse>(),JwUndergradBHtmlParser(html,"tju").generateCourseList())
        }
    }
}

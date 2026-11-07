package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class KingoFallbackAuditTest {
    private data class Occurrence(val name: String, val room: String, val teacher: String,
        val week: Int, val day: Int, val node: Int, val credit: Float, val note: String,
        val startTime: String, val endTime: String)
    private fun actual(courses: List<JwCourse>) = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { w ->
            (c.startNode..c.endNode).map { n -> Occurrence(c.name,c.room,c.teacher,w,c.day,n,
                c.credit,c.note,c.startTime,c.endTime) }
        }
    }.toSet()
    private fun want(name: String, teacher: String, room: String, day: Int, nodes: List<Int>,
        weeks: List<Int>, credit: Float = 0f) = weeks.flatMap { w -> nodes.map { n ->
            Occurrence(name,room,teacher,w,day,n,credit,"","","")
        } }.toSet()
    private fun report(name: String, timing: String, column: Int = 10, credit: String = "3.5",
        teacher: String = "陈师[T1]") : String {
        val cells = MutableList(column + 1) { "<td></td>" }
        cells[0] = "<td>选定</td>"
        cells[1] = "<td>[C01]$name</td>"
        cells[2] = "<td>$credit</td>"
        cells[4] = "<td>$teacher</td>"
        cells[column] = "<td>$timing</td>"
        return "<table><tbody><tr style='height:20px'>${cells.joinToString("")}</tr></tbody></table>"
    }
    private fun routes(type: String, html: String): Set<Occurrence> {
        val courses = JwParserRegistry.parserFor(type, html).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(html, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(html, type).first)
        assertDeclaredHtmlFrame(type, html, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "教务依赖回归", type=type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return actual(courses)
    }
    private fun zx(html: String) = routes("kg_zx", html)
    private fun scau(html: String) = routes("scau", html)

    @Test fun pageRptOriginalBranchRetainsFullFieldsAndSparseWeeks() {
        val html = "<div id='pageRpt'>"+report("线性代数","[1-3,7-9周]星期一[1-2节]/A101")+"</div>"
        assertEquals(want("线性代数","陈师","A101",1,listOf(1,2),listOf(1,2,3,7,8,9),3.5f),zx(html))
    }

    @Test fun pageRptGlobalParityAppliesToEverySparseFragment() {
        val html = "<div id='pageRpt'>"+report("数学m","[1-5,8-12单周]星期三[3-4节]/")+"</div>"
        assertEquals(want("数学m","陈师","在线",3,listOf(3,4),listOf(1,3,5,9,11),3.5f),zx(html))
    }

    @Test fun alternatePageRptAppliesGlobalParityButKeepsExplicitSingletonWeeks() {
        val html = "<div id='pageRpt'>"+report("离散数学","二 3-4节 单 [2,4-8周]/B201")+"</div>"
        assertEquals(want("离散数学","陈师","B201",2,listOf(3,4),listOf(2,5,7),3.5f),zx(html))
    }

    @Test fun reportAreaFontBranchRetainsEnSpaceRoomAndSunday() {
        val html = "<div id='reportArea'>"+report("物理","<font>[2-8双周]周日[5-6节] 实验室</font>",12,"2")+"</div>"
        assertEquals(want("物理","陈师","实验室",7,listOf(5,6),listOf(2,4,6,8),2f),zx(html))
    }

    @Test fun oneReportContainerRetainsEveryValidSiblingTable() {
        val html = "<div id='pageRpt'>"+report("数学","[1-1周]星期一[1-2节]/A")+
            report("英语","[2-2周]星期五[3-4节]/B",10,"1","李师[T2]")+"</div>"
        assertEquals(want("数学","陈师","A",1,listOf(1,2),listOf(1),3.5f)+
            want("英语","李师","B",5,listOf(3,4),listOf(2),1f),zx(html))
    }

    @Test fun reportAreaCommaSeparatedNodesKeepTheirGap() {
        val html = "<div id='reportArea'>"+report("实验","<font>[1,3-4周]周五[1,3节] A</font>",12)+"</div>"
        assertEquals(want("实验","陈师","A",5,listOf(1,3),listOf(1,3,4),3.5f),zx(html))
    }

    @Test fun malformedReportWeekFragmentsInvalidateTheirWholeArrangement() {
        for (weeks in listOf("1-2,bad","1-2,0","1-2,9-3","1-2,2147483648")) {
            val html = "<div id='pageRpt'>"+report("数学","[$weeks 周]星期一[1-2节]/A")+"</div>"
            assertEquals(weeks,emptySet<Occurrence>(),zx(html))
        }
    }

    @Test fun mytableCompactMarkerDoesNotLeakToTheNextWeekdayCell() {
        val html = """<table id='mytable'><tr><td class='td'>
            <div class='div1'><span class='xkinfo'>&nbsp;</span></div>
            <div>数据结构&nbsp;王师 1-3周[1-2]&nbsp;A101</div></td>
            <td class='td'><div>英语<br>李师<br>2-6双周[3-4]<br>B202</div></td></tr></table>"""
        assertEquals(want("数据结构","王师","A101",1,listOf(1,2),listOf(1,2,3))+
            want("英语","李师","B202",2,listOf(3,4),listOf(2,4,6)),zx(html))
    }

    @Test fun malformedMytableWeekFragmentsInvalidateTheirWholeArrangement() {
        val html = "<table id='mytable'><tr><td class='td'><div>数学<br>王师<br>1-2,bad周[1-2]<br>A</div></td></tr></table>"
        assertEquals(emptySet<Occurrence>(),zx(html))
    }

    @Test fun scauExplicitPackedNodeBranchRetainsAllFieldsAndSparseWeeks() {
        val html = "<table border='1'><tr><td valign='top'>数学<br>(910节)(双)<br>王师<br>A101<br>2-4周,8周</td></tr></table>"
        assertEquals(want("数学","王师","A101",1,listOf(9,10),listOf(2,4,8)),scau(html))
    }

    @Test fun scauFirstLineNodeBranchKeepsEmptyTeacherAndDoesNotLeakToNextCell() {
        val html = "<table border='1'><tr><td valign='top'>数学(12节)<br>A101<br><br>1-3周</td>"+
            "<td valign='top'>英语<br>(34节)<br>李师<br>B202<br>2周</td></tr></table>"
        assertEquals(want("数学(12节)","","A101",1,listOf(1,2),listOf(1,2,3))+
            want("英语","李师","B202",2,listOf(3,4),listOf(2)),scau(html))
    }

    @Test fun scauExplicitStrategyKeepsMultipleCourseBlocksInOneCell() {
        val html = "<table border='1'><tr><td valign='top'>数学<br>(12节)<br>王师<br>A<br>1周<br>"+
            "物理<br>(56节)<br>陈师<br>B<br>3周</td></tr></table>"
        assertEquals(want("数学","王师","A",1,listOf(1,2),listOf(1))+
            want("物理","陈师","B",1,listOf(5,6),listOf(3)),scau(html))
    }

    @Test fun malformedScauWeekFragmentsInvalidateTheirWholeArrangement() {
        val html = "<table border='1'><tr><td valign='top'>数学<br>(12节)<br>王师<br>A<br>1-2周,bad</td></tr></table>"
        assertEquals(emptySet<Occurrence>(),scau(html))
    }

    @Test fun badReportCannotPreventIndependentGridFallback() {
        val html = "<div id='pageRpt'>"+report("坏课","[bad周]星期一[1-2节]/A")+"</div>"+
            "<table id='mytable'><tr><td class='td'><div>保留课<br>王师<br>1-2周[1-2]<br>B</div></td></tr></table>"
        assertEquals(want("保留课","王师","B",1,listOf(1,2),listOf(1,2)),zx(html))
    }

    @Test fun longExplicitWeekListsAreNotTruncatedAtAnAssumedSemesterLength() {
        val reportHtml = "<div id='pageRpt'>"+report("数学","[31,61周]星期一[1-2节]/A")+"</div>"
        assertEquals(want("数学","陈师","A",1,listOf(1,2),listOf(31,61),3.5f),zx(reportHtml))
        val grid = "<table id='mytable'><tr><td class='td'><div>英语<br>李师<br>61周[3-4]<br>B</div></td></tr></table>"
        assertEquals(want("英语","李师","B",1,listOf(3,4),listOf(61)),zx(grid))
        val scauHtml = "<table border='1'><tr><td valign='top'>物理<br>(56节)<br>王师<br>C<br>31,61周</td></tr></table>"
        assertEquals(want("物理","王师","C",1,listOf(5,6),listOf(31,61)),scau(scauHtml))
    }

    @Test fun unsupportedPagesAndUnknownWeeksDoNotInventDefaultCourses() {
        assertEquals(emptySet<Occurrence>(),zx("<form><input type='password'></form>"))
        val html = "<table border='1'><tr><td valign='top'>数学<br>(12节)<br>王师<br>A<br>未知</td></tr></table>"
        assertEquals(emptySet<Occurrence>(),scau(html))
    }
}

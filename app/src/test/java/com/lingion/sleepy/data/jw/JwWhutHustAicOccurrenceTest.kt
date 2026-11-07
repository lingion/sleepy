package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Synthetic layouts transcribed from the local 6.6.2 parser; no authenticated network claim. */
class JwWhutHustAicOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w|${r.note}" }
        }
    }.sorted()
    private fun verify(type: String, html: String, expected: List<String>) {
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.parserFor(type, html).generateCourseList()))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(html, type).first))
        assertEquals(expected.sorted(), occurrences(JwImportViewModel.parseHtmlStatic(html, type).first))
        assertFalse(JwParserRegistry.allCandidates(html).any { it.first == type })
    }
    private fun whutCourse(name: String, room: String, time: String) =
        "<a>$name<span>@$room</span><span>$time</span></a>"

    @Test fun `WHUT legacy uses own text and first styled cell while keeping sparse weeks and nodes`() {
        val html = "<div id=xqkb><table class=table-class-even>" +
            "<tr><td>上午</td><td>忽略标签</td><td style=''>" +
            whutCourse("力学&amp;实验", "东1", "第1-5单周,第8-10双周(1-2节)") +
            whutCourse("力学&amp;实验", "西2", "第12周(5,7节)") +
            "</td><td></td><td>" + whutCourse("课程乙", "南3", "第3周(8-9节)") +
            "</td></tr><tr><td>下午</td><td style=''>" +
            whutCourse("课程丙", "北4", "第6周(11-12节)") + "</td></tr></table></div>"
        verify("whut", html,
            listOf(1,3,5,8,10).flatMap { w -> listOf(1,2).map { n -> "力学&实验||东1|1|$n|$w|" } } +
                listOf(5,7).map { "力学&实验||西2|1|$it|12|" } +
                listOf(8,9).map { "课程乙||南3|3|$it|3|" } +
                listOf(11,12).map { "课程丙||北4|1|$it|6|" })
    }

    @Test fun `WHUT legacy does not infer missing periods or apply DM remapping to HTML`() {
        val html = "<div id=xqkb><table class=table-class-even><tr><td style=''>" +
            whutCourse("缺节", "A", "第1-4周") + whutCourse("坏周", "A", "第1-4,待定周(1-2节)") +
            whutCourse("物理第八节", "B", "第9周(8节)") + "</td></tr></table></div>"
        verify("whut", html, listOf("物理第八节||B|1|8|9|"))
    }

    private fun detail(time: String, teacher: String, room: String) =
        "<div><span class=text-box>时间:$time</span><span class=text-box>教师:$teacher</span>" +
            "<span class=text-box>教室:$room</span></div>"
    private fun hustBlock(name: String, target: String, details: String) =
        "<section><p>$name</p><p>其他信息</p><p>$target</p><div class=search-details-body>$details</div></section>"

    @Test fun `HUST preserves teacher room and target per arrangement including repeated course names`() {
        val html = "<div class=main-page-block>" + hustBlock("计算机&amp;基础", "一班",
            detail("1-3周 星期一 1-2节", "张老师", "A101") + detail("6-8周 星期日 9-10节", "李老师", "B202")) +
            hustBlock("计算机&amp;基础", "二班", detail("5-5周 星期三 5-6节", "王老师", "C303")) + "</div>"
        verify("hust", html, (1..3).flatMap { w -> (1..2).map { n -> "计算机&基础|张老师|A101|1|$n|$w|开课对象：一班" } } +
            (6..8).flatMap { w -> (9..10).map { n -> "计算机&基础|李老师|B202|7|$n|$w|开课对象：一班" } } +
            (5..6).map { "计算机&基础|王老师|C303|3|$it|5|开课对象：二班" })
    }

    @Test fun `HUST missing labels invalid weeks or incomplete detail cannot borrow preceding fields`() {
        val html = "<div class=main-page-block>" + hustBlock("课程", "一班",
            detail("-1-4周 星期一 1-2节", "T", "A") + detail("4-1周 星期一 1-2节", "T", "A") +
                "<div><span class=text-box>时间:1-4周 星期一 1-2节</span></div>" +
                detail("1-4周 待定 1-2节", "T", "A") + detail("9-9周 星期五 3-3节", "U", "B")) + "</div>"
        verify("hust", html, listOf("课程|U|B|5|3|9|开课对象：一班"))
    }

    private fun aicCourse(name: String, teacher: String, room: String, weeks: String?) =
        "<div class=courseInfo><span>$name</span><span class=teacher>$teacher</span><span class=place>$room</span>" +
            (weeks?.let { "<span class=weekDetail>$it</span>" } ?: "") + "</div>"
    private fun aicTable(content: String) = "<table id=\"table\"><tr><th>节次</th><th>一</th><th>二</th></tr>$content</table>"

    @Test fun `AIC selects semester table and preserves empty rows and columns as single periods`() {
        val weekly = aicTable("<tr><td>1</td><td>" + aicCourse("周表", "错", "错", "1周") + "</td></tr>")
        val semester = aicTable("<tr><td>1</td><td>" + aicCourse("课程甲", "张", "A", "第1-5(单周),第8-10(双周)") +
            aicCourse("课程乙", "李", "B", "第12周") + "</td><td></td></tr>" +
            "<tr><td>2</td><td></td><td></td></tr><tr><td>3</td><td></td><td>" +
            aicCourse("课程甲", "王", "C", "第6周") + "</td></tr>")
        verify("aic", weekly + "<script>getSchedule.do()</script>" + semester + "<script>getScheduleNew.do()</script>",
            listOf(1,3,5,8,10).map { "课程甲|张|A|1|1|$it|" } + "课程乙|李|B|1|1|12|" + "课程甲|王|C|2|3|6|")
    }

    @Test fun `AIC weekly table or missing weekDetail cannot fabricate a semester`() {
        val weekly = aicTable("<tr><td>1</td><td>" + aicCourse("课程", "T", "A", null) + "</td></tr>")
        for (html in listOf(weekly, weekly + "<script>getScheduleNew.do()</script>")) {
            assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html, "aic").first)
        }
    }
}

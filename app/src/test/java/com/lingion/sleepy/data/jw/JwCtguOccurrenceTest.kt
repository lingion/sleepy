package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic legacy GridScore layouts, separate from the existing jwapp vendor collector. */
class JwCtguOccurrenceTest {
    private fun block(name: String, room: String, weeks: String, parity: String, teacher: String,
        separator: String = "<br>") = listOf(name, room, weeks, parity, teacher).joinToString(separator)
    private fun row(vararg cells: String) = cells.joinToString("", "<tr>", "</tr>") { "<td>$it</td>" }
    private fun table(body: String) = "<table id='ctl00_MainContentPlaceHolder_GridScore'>" +
        "<tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th><th>周六</th><th>周日</th></tr>" +
        body + "</table>"
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.flatMap { week ->
            (row.startNode..row.endNode).map { node ->
                "${row.name}|${row.teacher}|${row.room}|${row.day}|$node|$week"
            }
        }
    }.sorted()

    @Test fun `row offset label column and every parity produce full expected arrangements`() {
        val html = table(row(block("标签假课", "L", "第1-3周", "每周", "假老师"),
            block("单周课程(班级一)", "A101", "第1-7周", "单周", "张老师"), "", "", "", "", "",
            block("双周课程(班级二)", "B202", "第1-8周", "双周", "李老师")) +
            row("第三四节", "", "", block("每周课程", "C303", "第9-10周", "每周", "王老师")))
        val expected = listOf(1,3,5,7).flatMap { w -> listOf(1,2).map { "单周课程|张老师|A101|1|$it|$w" } } +
            listOf(2,4,6,8).flatMap { w -> listOf(1,2).map { "双周课程|李老师|B202|7|$it|$w" } } +
            listOf(9,10).flatMap { w -> listOf(3,4).map { "每周课程|王老师|C303|3|$it|$w" } }
        assertEquals(expected.sorted(), occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `same cell courses preserve sparse weeks room and teacher separately`() {
        val html = table(row("第一二节", "<div>" + block("课程甲", "A", "第1,3,7-9周", "单周", "张老师") +
            "</div><div>" + block("课程乙", "B", "第2,8周", "每周", "李老师", "&nbsp;") + "</div>"))
        val expected = listOf(1,3,7,9).flatMap { w -> listOf(1,2).map { "课程甲|张老师|A|1|$it|$w" } } +
            listOf(2,8).flatMap { w -> listOf(1,2).map { "课程乙|李老师|B|1|$it|$w" } }
        assertEquals(expected.sorted(), occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `flat repeated records decode markup and keep empty teacher or room positions`() {
        val html = table(row("第一二节", block("<span>网络&amp;协议</span>(A)", "", "第1-2周", "每周", "张&amp;李", "<BR />") +
            "<br>" + block("课程乙", "实验室", "第4周", "", "")))
        val expected = listOf(1,2).flatMap { w -> listOf(1,2).map { "网络&协议|张&李||1|$it|$w" } } +
            listOf("课程乙||实验室|1|1|4", "课程乙||实验室|1|2|4")
        assertEquals(expected.sorted(), occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `malformed coordinates and week tokens are rejected without contaminating later course`() {
        val invalid = listOf("第0-3周", "第-1-3周", "第1.5-3周", "第5-2周", "第1-3,待定周").map {
            "<div>" + block("坏周", "A", it, "每周", "T") + "</div>"
        }.joinToString("") + "<div>" + block("坏单双", "A", "第1-3周", "待定", "T") + "</div>" +
            "<div>" + block("冲突单双", "A", "第1-3周", "单双周", "T") + "</div>" +
            "<div>" + block("好课", "B", "第6周", "双周", "老师") + "</div>"
        val html = table(row("节次", invalid, "", "", "", "", "", "", block("第八天", "A", "第1周", "每周", "T")))
        assertEquals(listOf("好课|老师|B|1|1|6", "好课|老师|B|1|2|6"),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `duplicate and overlapping week records represent each physical occurrence once`() {
        val a = "<div>" + block("课程甲", "A", "第1-5周", "单周", "张老师") + "</div>"
        val b = "<div>" + block("课程甲", "A", "第5-7周", "单周", "张老师") + "</div>"
        val html = table(row("第一二节", a + a + b))
        assertEquals(listOf(1,3,5,7).flatMap { w -> listOf(1,2).map { "课程甲|张老师|A|1|$it|$w" } }.sorted(),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `missing header or wrong table never supplies negative or guessed nodes`() {
        val data = row("节次", block("不应导入", "A", "第1周", "每周", "T"))
        assertEquals(emptyList<JwCourse>(), JwCtguParser("<table>$data</table>").generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwCtguParser("<table id='ctl00_MainContentPlaceHolder_GridScore'>$data</table>").generateCourseList())
    }

    @Test fun `teacher remains the final field when a course has extra explanatory metadata`() {
        val html = table(row("第一二节", "课程甲(班级)<br>A101<br>第2-4周<br>双周<br>必修课说明<br>张老师"))
        assertEquals(listOf(2,4).flatMap { w -> listOf(1,2).map { "课程甲|张老师|A101|1|$it|$w" } }.sorted(),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `nested wrapper does not concatenate separate course containers`() {
        val html = table(row("第一二节", "<div class='wrapper'><div>" +
            block("课程甲", "A", "第3周", "单周", "张老师") + "</div><div>" +
            block("课程乙", "B", "第6周", "双周", "李老师") + "</div></div>"))
        assertEquals(listOf("课程乙|李老师|B|1|1|6", "课程乙|李老师|B|1|2|6",
            "课程甲|张老师|A|1|1|3", "课程甲|张老师|A|1|2|3"),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `merged weekday cells are rejected instead of shifting the following course`() {
        val html = table("<tr><td>第一二节</td><td colspan='2'></td><td>" +
            block("不能猜星期", "A", "第1周", "每周", "T") + "</td></tr>")
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `rowspanned labels are rejected instead of shifting later row weekdays`() {
        val html = table("<tr><td rowspan='2'>说明</td><td>" +
            block("不能猜坐标", "A", "第1周", "每周", "T") + "</td></tr>" +
            row(block("不能漏周一", "A", "第1周", "每周", "T"), block("不能猜周二", "B", "第1周", "每周", "T")))
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `invalid weeks in a flat second record never donate its teacher to the first`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "张老师") + "<br>" +
            block("坏周课", "B", "第1-3,待定周", "每周", "李老师")))
        assertEquals(listOf("首课|张老师|A|1|1|3", "首课|张老师|A|1|2|3"),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `ten fields with one course keep explanatory metadata and the final teacher`() {
        val cell = listOf("课程甲", "A101", "第2-4周", "双周", "必修", "教学班一", "学分三", "说明甲", "说明乙", "张老师")
            .joinToString("<br>")
        val rows = JwCtguParser(table(row("第一二节", cell))).generateCourseList()
        assertEquals(listOf(2,4).flatMap { w -> listOf(1,2).map { "课程甲|张老师|A101|1|$it|$w" } }.sorted(), occurrences(rows))
    }

    @Test fun `empty final teacher after explanatory metadata remains empty`() {
        val html = table(row("第一二节", "课程甲<br>A101<br>第3周<br>每周<br>仅备注<br>"))
        val rows = JwCtguParser(html).generateCourseList()
        assertEquals(listOf("课程甲||A101|1|1|3", "课程甲||A101|1|2|3"), occurrences(rows))
    }

    @Test fun `multiple flat courses can have their own extra metadata without changing teachers`() {
        val first = "课程甲<br>A<br>第3周<br>每周<br>甲的说明<br>张老师"
        val second = "课程乙<br>B<br>第6周<br>双周<br>乙的说明<br>李老师"
        assertEquals(listOf("课程乙|李老师|B|1|1|6", "课程乙|李老师|B|1|2|6",
            "课程甲|张老师|A|1|1|3", "课程甲|张老师|A|1|2|3"),
            occurrences(JwCtguParser(table(row("第一二节", first + "<br>" + second))).generateCourseList()))
    }

    @Test fun `invalid parity in later flat course does not donate its teacher to earlier course`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "张老师") + "<br>" +
            block("坏单双课", "B", "第6周", "待定", "李老师")))
        assertEquals(listOf("首课|张老师|A|1|1|3", "首课|张老师|A|1|2|3"),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `mixed flat and nested course structure is rejected instead of concatenated`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "张老师") +
            "<div>" + block("后课", "B", "第6周", "每周", "李老师") + "</div>"))
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `wrapper labels are not absorbed into course names`() {
        val html = table(row("第一二节", "<div><span>提示</span><div>" +
            block("首课", "A", "第3周", "每周", "张老师") + "</div></div>"))
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `data header cell does not shift following weekday`() {
        val html = table("<tr><th>第一二节</th><td>" + block("周一课", "A", "第3周", "每周", "张老师") +
            "</td><td>" + block("周二课", "B", "第6周", "双周", "李老师") + "</td></tr>")
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `trailing separator is preserved as the explicit empty teacher`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "老师形文字") + "<br>"))
        assertEquals(listOf("首课||A|1|1|3", "首课||A|1|2|3"),
            occurrences(JwCtguParser(html).generateCourseList()))
    }

    @Test fun `blank separators between flat records cannot create partial course names`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "张老师") + "<br><br>" +
            block("后课", "B", "第6周", "每周", "李老师")))
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }

    @Test fun `opaque second week field is rejected when its record boundary is ambiguous`() {
        val html = table(row("第一二节", block("首课", "A", "第3周", "每周", "张老师") + "<br>" +
            block("后课", "B", "opaque", "每周", "李老师")))
        assertEquals(emptyList<JwCourse>(), JwCtguParser(html).generateCourseList())
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

/** Synthetic DOM contracts derived from the local 6.6.2 parser DEX, not authenticated captures. */
class JwDhuEcjtuOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.flatMap { week ->
            (row.startNode..row.endNode).map { node ->
                "${row.name}|${row.teacher}|${row.room}|${row.day}|$node|$week"
            }
        }
    }.sorted()

    private fun verify(parser: JwParser, expected: List<String>) {
        val rows = parser.generateCourseList()
        assertEquals(expected.sorted(), occurrences(rows))
        val draft = JwImportDraftSnapshot(JwSchoolInfo("test", "synthetic"), rows, emptyList())
        assertEquals(draft, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft)))
    }

    private fun dhu(vararg rows: String) = "<table><tr><td>节次</td>" +
        (1..7).joinToString("") { "<td>星期$it</td>" } + "</tr>" + rows.joinToString("") + "</table>"

    private fun ecjtu(vararg rows: String) = "<table class='table_border'><tr><td>节次</td>" +
        (1..7).joinToString("") { "<td>星期$it</td>" } + "</tr>" + rows.joinToString("") + "</table>"

    @Test fun `DHU four field blocks preserve each name teacher room and parity`() {
        val page = dhu("<tr><td>一节</td><td rowspan='2'>纺织 1-3单周,6双周 张 纺A 数学 4-5周 李 松B</td>" +
            "<td>英语 9周 王 语C</td>" + "<td></td>".repeat(5) + "</tr>")
        verify(JwDhuParser(page), listOf(1, 2).flatMap { node ->
            listOf(1, 3, 6).map { "纺织|张|纺A|1|$node|$it" } +
                listOf(4, 5).map { "数学|李|松B|1|$node|$it" }
        } + "英语|王|语C|2|1|9")
    }

    @Test fun `DHU empty rowspan and colspan reserve weekdays across sparse period labels`() {
        val page = dhu(
            "<tr><td>三节</td><td rowspan='2'></td><td colspan='2'></td><td rowspan='2'>设计 2周 陈 D</td>" + "<td></td>".repeat(3) + "</tr>",
            "<tr><td>四节</td><td>材料 7周 刘 E</td><td></td>" + "<td></td>".repeat(3) + "</tr>",
            "<tr><td>十一节</td><td rowspan='2'>晚课 11周 赵 F</td>" + "<td></td>".repeat(6) + "</tr>"
        )
        verify(JwDhuParser(page), listOf("设计|陈|D|4|3|2", "设计|陈|D|4|4|2", "材料|刘|E|2|4|7",
            "晚课|赵|F|1|11|11", "晚课|赵|F|1|12|11"))
    }

    @Test fun `DHU invalid weeks and ambiguous cross weekday courses never become valid meetings`() {
        val page = dhu("<tr><td>一节</td><td colspan='2'>不明 1周 张 A</td>" +
            "<td>负周 -1-3周 张 A 小数 1.5周 李 B 正常 7周 王 C</td>" + "<td></td>".repeat(4) + "</tr>",
            "<tr><td>一.五节</td><td>假节 1周 张 A</td>" + "<td></td>".repeat(6) + "</tr>")
        verify(JwDhuParser(page), listOf("正常|王|C|3|1|7"))
    }

    @Test fun `DHU invalid rowspan fails closed and trailing optional fields stay empty`() {
        verify(JwDhuParser(dhu("<tr><td>一节</td><td rowspan='-2'>坏课 1周 张 A</td>" +
            "<td></td>".repeat(6) + "</tr>")), emptyList())
        verify(JwDhuParser(dhu("<tr><td>二节</td><td>独课 8周</td>" + "<td></td>".repeat(6) + "</tr>")),
            listOf("独课|||1|2|8"))
    }

    @Test fun `DHU overlapping week segments do not duplicate the same meeting`() {
        val page = dhu("<tr><td>一节</td><td>材料 1-3周,3-5周 张 A 材料 3周 李 B</td>" +
            "<td></td>".repeat(6) + "</tr>")
        verify(JwDhuParser(page), (1..5).map { "材料|张|A|1|1|$it" } + "材料|李|B|1|1|3")
    }

    @Test fun `ECJTU br separated records keep teacher room and sparse nodes without filling holes`() {
        val page = ecjtu("<tr><td>上午</td><td>线性代数<br>张老师@南A<br>1-5(单),8(双) 1,3,5<br>" +
            "大学物理<br>李老师@北B<br>6-7 7,8</td>" + "<td></td>".repeat(6) + "</tr>")
        verify(JwEcjtuParser(page), listOf(1, 3, 5, 8).flatMap { week ->
            listOf(1, 3, 5).map { "线性代数|张老师|南A|1|$it|$week" }
        } + listOf(6, 7).flatMap { week -> listOf(7, 8).map { "大学物理|李老师|北B|1|$it|$week" } })
    }

    @Test fun `ECJTU three token form stores numeric teacher prefix without manufacturing a room`() {
        val page = ecjtu("<tr><td>下午</td>" + "<td></td>".repeat(3) +
            "<td>实验<br>10021 2-4(双) 9,11<br></td>" + "<td></td>".repeat(3) + "</tr>")
        verify(JwEcjtuParser(page), listOf(2, 4).flatMap { week -> listOf(9, 11).map { "实验|10021||4|$it|$week" } })
    }

    @Test fun `ECJTU three token form accepts Chinese teacher verified against Kotlin helper bytecode`() {
        val page = ecjtu("<tr><td>下午</td><td>实验<br>张老师 2-4(双) 9,11<br></td>" +
            "<td></td>".repeat(6) + "</tr>")
        verify(JwEcjtuParser(page), listOf(2, 4).flatMap { week -> listOf(9, 11).map { "实验|张老师||1|$it|$week" } })
    }

    @Test fun `ECJTU rowspan and blank colspan retain visual weekdays without duplicating merged courses`() {
        val page = ecjtu(
            "<tr><td>一</td><td rowspan='2'>跨行<br>陈@C<br>3 1,2<br></td><td colspan='2'></td>" +
                "<td>中课<br>刘@D<br>4 3,4<br></td>" + "<td></td>".repeat(3) + "</tr>",
            "<tr><td>二</td><td>续课<br>王@E<br>7 5,6<br></td>" + "<td></td>".repeat(5) + "</tr>"
        )
        verify(JwEcjtuParser(page), listOf("跨行|陈|C|1|1|3", "跨行|陈|C|1|2|3", "中课|刘|D|4|3|4",
            "中课|刘|D|4|4|4", "续课|王|E|2|5|7", "续课|王|E|2|6|7"))
    }

    @Test fun `ECJTU malformed blocks terminate locally and do not rename later valid courses`() {
        val bad = listOf("-1-4 1,2", "1.5 1,2", "4-1 1,2", "2 0,2", "3 1.5,2", "2 1,31")
            .mapIndexed { index, value -> "错误$index<br>坏教师@坏教室<br>$value<br>" }.joinToString("")
        val page = ecjtu("<tr><td>上午</td><td>$bad" +
            "有效课<br>真教师@真教室<br>9 2,4<br></td>" + "<td></td>".repeat(6) + "</tr>")
        verify(JwEcjtuParser(page), listOf("有效课|真教师|真教室|1|2|9", "有效课|真教师|真教室|1|4|9"))
    }

    @Test fun `ECJTU semantic HTML text and final line without trailing break are preserved`() {
        val page = ecjtu("<tr><td>上午</td>" + "<td></td>".repeat(6) +
            "<td><b>设计&amp;艺术</b><br/><span>张&amp;李@A&amp;B</span><br/>2 11,12</td></tr>")
        verify(JwEcjtuParser(page), listOf("设计&艺术|张&李|A&B|7|11|2", "设计&艺术|张&李|A&B|7|12|2"))
    }

    @Test fun `ECJTU repeated arrangements union overlapping weeks and retain different teachers`() {
        val page = ecjtu("<tr><td>上午</td><td>材料<br>张@A<br>1-3 1,3<br>" +
            "材料<br>张@A<br>3-5 1,3<br>材料<br>李@B<br>3 1,3<br></td>" + "<td></td>".repeat(6) + "</tr>")
        verify(JwEcjtuParser(page), (1..5).flatMap { week -> listOf(1, 3).map { "材料|张|A|1|$it|$week" } } +
            listOf(1, 3).map { "材料|李|B|1|$it|3" })
    }

    @Test fun `ECJTU malformed row width and nonempty colspan never guess a weekday`() {
        val source = ecjtu("<tr><td>一</td><td colspan='2'>未知<br>张@A<br>1 1,2<br></td>" +
            "<td>明确<br>李@B<br>3 5,6<br></td>" + "<td></td>".repeat(4) + "</tr>",
            "<tr><td>二</td><td>短行<br>赵@C<br>8 7,8<br></td></tr>")
        verify(JwEcjtuParser(source), listOf("明确|李|B|3|5|3", "明确|李|B|3|6|3"))
        verify(JwEcjtuParser("<table><tr><td>普通页面</td></tr></table>"), emptyList())
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwHnustOccurrenceTest {
    private fun page(vararg cells: String) = "<table id='kbtable'><tr><td>节次</td>" +
        cells.joinToString("") { "<td>$it</td>" } + "</tr></table>"
    private fun block(id: String, text: String, style: String = "") = "<div id='$id' style='$style'>$text</div>"
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { "${r.name}|${r.teacher}|${r.room}|${r.day}|$it|$w" }
        }
    }.sorted()
    private fun verify(source: String, expected: List<String>) {
        val rows=JwImportViewModel.parseHtmlStatic(source,"hnust").first
        assertEquals(expected.sorted(),occurrences(rows))
        val draft=JwImportDraftSnapshot(JwSchoolInfo("h","湖南科技大学"),rows,emptyList())
        assertEquals(draft,JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft)))
    }

    @Test fun `same div contains independent course names teachers rooms and week sets`() {
        val source=page(block("2-1","课程甲<br>张<br>1-2周<br>A<br>课程乙<br>李<br>7周,9周<br>B","display:none;"))
        verify(source,listOf(3,4).flatMap { n -> listOf(1,2).map { "课程甲|张|A|1|$n|$it" } + listOf(7,9).map { "课程乙|李|B|1|$n|$it" } })
    }

    @Test fun `modern HNUST does not discard visible course blocks or duplicate hidden copy`() {
        val content="课程甲<br>张<br>1-2周<br>A"
        val source=page(block("1-1",content)+block("1-2",content,"display:none;"))
        verify(page(block("1-1",content)),listOf(1,2).flatMap { w -> listOf(1,2).map { "课程甲|张|A|1|$it|$w" } })
        verify(source,listOf(1,2).flatMap { w -> listOf(1,2).map { "课程甲|张|A|1|$it|$w" } })
        assertEquals(1,JwHnustParser(source,0).generateCourseList().size)
        assertEquals(1,JwHnustParser(source,1).generateCourseList().size)
    }

    @Test fun `unknown signed and fractional weeks and period ids never manufacture lessons`() {
        val source=page(block("1-1","负周<br>张<br>-1-4周<br>A<br>坏周<br>李<br>1.5周<br>B<br>有效课<br>王<br>7周<br>C","display:none;")+
            block("0-1","零节<br>张<br>1周<br>A","display:none;")+block("x2-1","坏节<br>张<br>1周<br>A","display:none;")+
            block("2147483647-1","溢出<br>张<br>1周<br>A","display:none;"))
        verify(source,listOf(1,2).map { "有效课|王|C|1|$it|7" })
    }

    @Test fun `parity and whitespace remain local to one complete arrangement`() {
        val source=page("",block("5-2","课程甲<br/>张<br/>1-5单周,8-10双周<br/>A<br/>课程乙<br/>李<br/>12周<br/>B"))
        verify(source,listOf(9,10).flatMap { n -> listOf(1,3,5,8,10).map { "课程甲|张|A|2|$n|$it" } + "课程乙|李|B|2|$n|12" })
    }

    @Test fun `per block metadata does not set name offset for later courses`() {
        val source=page(block("1-1","课程甲<br>一班<br>张<br>1周<br>A<br>课程乙<br>李<br>2周<br>B"))
        verify(source,listOf(1,2).flatMap { listOf("课程甲|张|A|1|$it|1","课程乙|李|B|1|$it|2") })
    }

    @Test fun `a course in label column or eighth weekday is not clamped to monday or sunday`() {
        val content=block("1-1","错误位置<br>张<br>1周<br>A","display:none;")
        verify("<table id='kbtable'><tr><td>$content</td>"+"<td></td>".repeat(7)+"<td>$content</td></tr></table>",emptyList())
    }
}

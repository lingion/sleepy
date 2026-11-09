package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBPkuTest {
    private data class Occurrence(val name: String, val room: String, val teacher: String, val week: Int, val day: Int, val node: Int,
        val start: String, val end: String, val credit: Float, val note: String)
    private fun actual(rows: List<JwCourse>) = rows.flatMap { c -> (c.startWeek..c.endWeek)
        .filter { c.type == 0 || it % 2 == c.type % 2 }.flatMap { week -> (c.startNode..c.endNode).map { node ->
            Occurrence(c.name, c.room, c.teacher, week, c.day, node, c.startTime, c.endTime, c.credit, c.note)
        } } }.toSet()
    private fun expected(name: String, room: String, teacher: String, weeks: List<Int>, day: Int, nodes: List<Int>,
        start: String = "", end: String = "", credit: Float = 0f, note: String = "") = weeks.flatMap { w -> nodes.map { n ->
            Occurrence(name, room, teacher, w, day, n, start, end, credit, note)
        } }.toSet()
    @Test fun pkuKeepsPositiveWeeksAndNodesWithoutArbitraryCeilings() {
        assertEquals(expected("数学", "A", "王师", listOf(400,401), 1, listOf(31,32)),
            actual(assertUndergradBImportRoutes(pku("400~401周 周一31~32节 A"), "pku")))
    }

    private fun pku(timing: String, status: String = "已选") = "<table class='datagrid'><tbody><tr>" +
        listOf("数学", "", "", "", "王师", "", "", timing, status, "", "").joinToString("") { "<td>$it</td>" } + "</tr></tbody></table>"

    @Test fun pkuResetsEachBlockSingleWeekAndSingleNode() {
        val html = pku("1~5周 周一3~4节(单) A<br>8周 周三7节 B")
        assertEquals(expected("数学", "A", "王师", listOf(1,3,5), 1, listOf(3,4)) +
            expected("数学", "B", "王师", listOf(8), 3, listOf(7)), actual(assertUndergradBImportRoutes(html, "pku")))
    }

    @Test fun pkuMalformedBlockDoesNotInheritEarlierCoordinates() {
        val html = pku("1~5周 周一3~4节 A<br>8周 没有时间 B<br>2~4周 周二5~6节 C")
        assertEquals(expected("数学", "A", "王师", listOf(1,2,3,4,5), 1, listOf(3,4)) +
            expected("数学", "C", "王师", listOf(2,3,4), 2, listOf(5,6)), actual(assertUndergradBImportRoutes(html, "pku")))
        assertEquals(emptySet<Occurrence>(), actual(assertUndergradBImportRoutes(pku("1~5周 周一3~4节 A", "未选"), "pku")))
    }

    @Test fun pkuBracketRoomAndInvalidCoordinates() {
        assertEquals(expected("数学", "C101", "王师", listOf(6), 7, listOf(11)), actual(assertUndergradBImportRoutes(pku("6周 周日11节(C101)"), "pku")))
        for (timing in listOf("9~2周 周一1~2节 A", "1~8周 周一0节 A", "0~8周 周一1~2节 A", "1~8周 周一5~3节 A"))
            assertEquals(emptySet<Occurrence>(), actual(assertUndergradBImportRoutes(pku(timing), "pku")))
    }
}

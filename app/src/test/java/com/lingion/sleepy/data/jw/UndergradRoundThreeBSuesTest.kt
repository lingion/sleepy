package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBSuesTest {
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
    private fun activity(name: String, room: String, start: Int, end: Int, weeks: String = "[1,3,9]", firstTime: String = "8:15", lastTime: String = "9:35") =
        """{"courseName":"$name","room":"$room","weekday":2,"startUnit":$start,"endUnit":$end,"weekIndexes":$weeks,"teachers":["王师","李师"],"credits":2.5,"lessonCode":"001","lessonRemark":"实验","startTime":"$firstTime","endTime":"$lastTime"}"""

    @Test fun suesCaptureAndPeriodDefaultsReachUnifiedEntry() {
        assertEquals(JwSuesCaptureScript.script, com.lingion.sleepy.ui.screen.imports.JwSchoolApiCapture.scriptFor("sues"))
        assertEquals(JwSuesParser.periods, JwSourcePeriods.resolve("", "sues", "", emptyList()))
        assertEquals(JwUndergradBHtmlParser.shuPeriods, JwSourcePeriods.resolve("", "shu2024", "", emptyList()))
        val captured = listOf(Triple(1, "07:15", "08:00"))
        for (type in listOf("sues", "shu2024")) assertEquals(captured, JwSourcePeriods.resolve("", type, "", captured))
    }

    @Test fun suesReadsPluralAndSingularTablesAndExplicitSparseWeekSet() {
        val html = """{"studentTableVms":[{"activities":[${activity("数学", "A101多", 3, 5)}]}],"studentTableVm":{"activities":[${activity("物理", "D101多", 3, 5, "[2,6]")}]}}"""
        assertEquals(expected("数学", "A101多", "王师,李师", listOf(1,3,9), 2, listOf(3,4,5), "09:55", "12:00", 2.5f, "001实验") +
            expected("物理", "D101多", "王师,李师", listOf(2,6), 2, listOf(3,4,5), "10:15", "12:20", 2.5f, "001实验"), actual(assertUndergradBImportRoutes(html, "sues")))
    }

    @Test fun suesRoomGroupsCorrectFourthPeriodAndFallbackClock() {
        for ((room, start, end) in listOf(Triple("A101多", "10:40", "11:20"), Triple("J301多", "10:40", "11:20"),
            Triple("J303(中外教室）", "10:55", "11:35"), Triple("普通教室", "10:35", "11:15"))) {
            val html = """{"studentTableVm":{"activities":[${activity("数学", room, 4, 4, "[4]")}]}}"""
            assertEquals(expected("数学", room, "王师,李师", listOf(4), 2, listOf(4), start, end, 2.5f, "001实验"), actual(assertUndergradBImportRoutes(html, "sues")))
        }
        val html = """{"studentTableVm":{"activities":[${activity("英语", "A", 1, 2, "[6]")}]}}"""
        assertEquals(expected("英语", "A", "王师,李师", listOf(6), 2, listOf(1,2), "08:15", "09:35", 2.5f, "001实验"), actual(assertUndergradBImportRoutes(html, "sues")))
        assertEquals(Triple(15,"20:20","21:00"), JwSuesParser.periods.last())
        assertEquals(15, JwSuesParser.periods.size)
    }

    @Test fun suesRejectsInvalidWeekArrayAndDoesNotInventClock() {
        for (weeks in listOf("[]", "[1,0]", "[1,2147483648]", "[1,\"bad\"]")) {
            val html = """{"studentTableVm":{"activities":[${activity("数学", "A", 1, 2, weeks)}]}}"""
            assertEquals(emptySet<Occurrence>(), actual(assertUndergradBImportRoutes(html, "sues")))
        }
        val html = """{"studentTableVm":{"activities":[${activity("英语", "A", 1, 2, "[6]", "25:00", "10:00")}]}}"""
        assertEquals(expected("英语", "A", "王师,李师", listOf(6), 2, listOf(1,2), credit = 2.5f, note = "001实验"), actual(assertUndergradBImportRoutes(html, "sues")))
    }

    @Test fun suesKeepsPositiveWeeksAndNodesWithoutArbitraryCeilings() {
        val source = """{"studentTableVm":{"activities":[${activity("英语", "A", 31, 32, "[400,401]")}]}}"""
        assertEquals(expected("英语", "A", "王师,李师", listOf(400,401), 2, listOf(31,32), "08:15", "09:35", 2.5f, "001实验"),
            actual(assertUndergradBImportRoutes(source, "sues")))
    }
}

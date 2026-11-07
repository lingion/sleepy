package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwHbmzuNuaStumOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w|${r.startTime}|${r.endTime}" }
        }
    }.sorted()
    private fun verify(type: String, source: String, expected: List<String>) {
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.parserFor(type, source).generateCourseList()))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(source, type).first))
        assertEquals(expected.sorted(), occurrences(JwImportViewModel.parseHtmlStatic(source, type).first))
        assertFalse(JwParserRegistry.allCandidates(source).any { it.first == type })
    }
    private fun course(name: String, weeks: String, room: String, teacher: String) =
        "<p><span>$name</span><span>$weeks</span><span>$room</span><span>$teacher</span></p>"

    @Test fun `HBMZU first row is periods one and two and first column is Monday`() {
        val html = "<table class='list_table tac'><tr><td>" + course("课&amp;甲", "第1-5单周,第8-10双周", "A", "张") +
            course("课乙", "第12周", "B", "李") + "</td><td></td><td>" + course("课丙", "第3周", "C", "王") +
            "</td></tr><tr><td></td><td></td><td></td></tr><tr><td>" + course("课丁", "第6周", "D", "赵") + "</td></tr></table>"
        verify("hbmzu", html, listOf(1,3,5,8,10).flatMap { w -> listOf(1,2).map { n -> "课&甲|张|A|1|$n|$w||" } } +
            listOf(1,2).map { "课乙|李|B|1|$it|12||" } + listOf(1,2).map { "课丙|王|C|3|$it|3||" } +
            listOf(5,6).map { "课丁|赵|D|1|$it|6||" })
    }

    @Test fun `HBMZU invalid or incomplete spans do not inherit previous course fields`() {
        val html = "<table class='list_table tac'><tr><td>" + course("坏周", "第1-4,待定周", "A", "T") +
            "<p><span>缺字段</span></p>" + course("好课", "第9周", "B", "U") + "</td></tr></table>"
        verify("hbmzu", html, listOf(1,2).map { "好课|U|B|1|$it|9||" })
    }

    private fun nuaRow(name: String, time: String, teacher: String = "张", room: String = "画室") =
        "<tr><td>$name</td><td>$teacher</td><td>未用</td><td>$time</td><td>$room</td></tr>"

    @Test fun `NUA inserts midday period and preserves every separated weekday and node`() {
        val html = "<table><tr><td>装饰表</td></tr></table><table><tr><th>表头</th></tr>" +
            nuaRow("绘画", "1-3周 周一4,中午,5 周三1,3") + nuaRow("绘画", "6周 周日中午", "李", "工坊") + "</table>"
        verify("nua", html, (1..3).flatMap { w -> listOf(4,5,6).map { n -> "绘画|张|画室|1|$n|$w||" } +
            listOf(1,3).map { n -> "绘画|张|画室|3|$n|$w||" } } + "绘画|李|工坊|7|5|6||")
    }

    @Test fun `NUA unspecified schedules never become Saturday periods one and two for twenty weeks`() {
        val html = "<table><tr><th>表头</th></tr>" + nuaRow("待定", "另行通知") +
            nuaRow("坏周", "-1-4周 周一1,2") + nuaRow("坏节", "1-4周 周一0,2") +
            nuaRow("坏日", "1-4周 周九1,2") + "</table>"
        verify("nua", html, emptyList())
    }

    private fun item(name: String, teacher: String, room: String) = JSONObject().put("f1", 1).put("f2", name)
        .put("f3", teacher).put("f4", room).put("f5", 0).put("f6", JSONArray()).put("f7", "")
    private fun row(node: String, day: String, vararg items: JSONObject) = JSONObject().put("name", node)
        .put(day, JSONArray(items.toList()).toString()).put("start_time", "18:30").put("end_time", "19:15")
    private fun week(week: Any, vararg rows: JSONObject) = JSONObject().put("code", 0).put("msg", "")
        .put("data", JSONObject().put("week", week).put("data", JSONArray(rows.toList())).put("weekdays", JSONArray()))

    @Test fun `STUM decodes nested JSON strings for all days and A B C without filling missing weeks`() {
        val source = JSONArray(listOf(
            week(1, row("A", "monday", item("课甲", "张", "A"), item("课乙", "李", "B")),
                row("B", "monday", item("课甲", "张", "A")), row("C", "sunday", item("课丙", "王", "C"))),
            week(3, row("A", "monday", item("课甲", "赵", "D")), row("10", "wednesday", item("课丁", "钱", "E")))
        )).toString()
        verify("stum", source, listOf("课甲|张|A|1|11|1|18:30|19:15", "课乙|李|B|1|11|1|18:30|19:15",
            "课甲|张|A|1|12|1|18:30|19:15", "课丙|王|C|7|13|1|18:30|19:15", "课甲|赵|D|1|11|3|18:30|19:15",
            "课丁|钱|E|3|10|3|18:30|19:15"))
    }

    @Test fun `STUM invalid nodes weeks and cell payload never default to period one`() {
        val source = JSONArray(listOf(week(1, row("bad", "monday", item("坏节", "T", "A")),
            row("1", "tuesday", item("正常", "U", "B")).put("monday", JSONObject.NULL)
                .put("start_time", "bad").put("end_time", "19:15"),
            row("2", "monday", item("坏JSON", "T", "A")).put("monday", "not-json")),
            week(1.5, row("1", "monday", item("小数周", "T", "A"))),
            week(0, row("1", "monday", item("零周", "T", "A"))))).toString()
        verify("stum", source, listOf("正常|U|B|2|1|1||"))
    }
}

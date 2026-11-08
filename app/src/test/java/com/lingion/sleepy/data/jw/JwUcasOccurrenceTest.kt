package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUcasOccurrenceTest {
    private fun block(nodes: String, room: String, weeks: String, day: String = "一") =
        "<tr><th>上课时间</th><td>星期$day： 第${nodes}节。</td></tr>" +
            "<tr><th>上课地点</th><td>$room</td></tr><tr><th>上课周次</th><td>$weeks</td></tr>"
    private fun detail(name: String, blocks: String) = "<div class=\"mc-body\"><p>课程名称：$name</p><table>$blocks</table></div>"
    private fun grid(vararg rows: Pair<Int, List<Pair<Int, String>>>): String =
        "<title>个人课表</title><table><thead><tr><th>节次/星期</th></tr></thead><tbody>" + rows.joinToString("") { (node, lessons) ->
            "<tr><th>$node</th><td>" + lessons.joinToString("") { (id, name) ->
                "<a href=\"https://xkcts.ucas.ac.cn:8443/course/coursetime/$id\">$name</a>"
            } + "</td></tr>"
        } + "</tbody></table>"
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { week ->
            (r.startNode..r.endNode).map { node -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$node|$week" }
        }
    }.sorted()

    @Test fun `UCAS JSON preserves every sparse node and week combination`() {
        val source = """{"selectedCourse":{},"courseTimeList":[{"courseName":"甲","coursePlace":"A","courseWeek":5,"courseTime":8205}]}"""
        val expected = listOf(1, 3).flatMap { w -> listOf(1, 3, 4).map { n -> "甲||A|1|$n|$w" } }.sorted()
        assertEquals(expected, occurrences(JwUcasParser(source).generateCourseList()))
        assertEquals(expected, occurrences(JwParserRegistry.selectBest(source, "ucas").first))
    }

    @Test fun `UCAS standalone native detail panels preserve names sparse nodes and repeated blocks`() {
        val source = detail("甲&amp;乙", block("1、3、4", "A", "1、3") + block("1、3、4", "B", "8、10")) +
            detail("丙", block("2", "C", "6", "日"))
        val expected = (listOf(1, 3).flatMap { w -> listOf(1, 3, 4).map { n -> "甲&乙||A|1|$n|$w" } } +
            listOf(8, 10).flatMap { w -> listOf(1, 3, 4).map { n -> "甲&乙||B|1|$n|$w" } } + "丙||C|7|2|6").sorted()
        assertEquals(expected, occurrences(JwUcasParser(source).generateCourseList()))
        assertEquals(expected, occurrences(JwImportViewModel.parseHtmlStatic(source, "ucas").first))
    }

    @Test fun `UCAS detail identity includes link id and all matching arrangements`() {
        val source = UcasDetailFetch.combine(grid(1 to listOf(1 to "同名", 2 to "同名"), 2 to listOf(1 to "同名")), listOf(
            "https://xkcts.ucas.ac.cn:8443/course/coursetime/1" to detail("同名", block("1", "A", "1") + block("1", "B", "3")),
            "https://xkcts.ucas.ac.cn:8443/course/coursetime/2" to detail("同名", block("1", "C", "5")),
        ))
        assertEquals(listOf("同名||A|1|1|1", "同名||B|1|1|3", "同名||C|1|1|5"),
            occurrences(JwUcasParser(source).generateCourseList()))
    }

    @Test fun `UCAS grid or malformed detail cannot manufacture provisional weeks`() {
        val html = grid(1 to listOf(1 to "甲"))
        assertEquals(emptyList<JwCourse>(), JwUcasParser(html).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html, "ucas").first)
        for ((nodes, weeks) in listOf("-1、2" to "1", "1" to "-1、2", "1" to "2、待定", "1" to "1-367")) {
            val combined = UcasDetailFetch.combine(html, listOf("https://xkcts.ucas.ac.cn:8443/course/coursetime/1" to detail("甲", block(nodes, "A", weeks))))
            assertEquals(emptyList<JwCourse>(), JwUcasParser(combined).generateCourseList())
        }
    }

    @Test fun `UCAS bitmaps require integers and standalone JSON arrays keep every item`() {
        val valid = """{"courseName":"甲","coursePlace":"A","courseWeek":1,"courseTime":8193}"""
        assertEquals(listOf("甲||A|1|1|1"), occurrences(JwUcasParser("[$valid]").generateCourseList()))
        for (bad in listOf(valid.replace("8193", "8193.5"), valid.replace("8193", "4294975489"))) {
            assertEquals(emptyList<JwCourse>(), JwUcasParser("{\"courseTimeList\":[$bad]}").generateCourseList())
        }
    }

    @Test fun `all eleven captured UCAS detail pages match complete independent occurrences`() {
        data class Expected(val id: String, val name: String, val room: String, val nodes: IntRange, val days: List<Pair<Int, List<Int>>>)
        val expected = listOf(
            Expected("313613", "Web安全技术", "教一楼009", 3..4, listOf(2 to (listOf(2,3,4,5)+(7..12)), 4 to (listOf(2,3,4)+(6..11)), 7 to listOf(3))),
            Expected("313853", "操作系统高级教程", "教一楼107", 7..8, listOf(1 to (2..18).toList(), 3 to (listOf(2,3,4,5)+(7..18)), 6 to listOf(6))),
            Expected("314215", "计算机体系结构", "教一楼107", 10..12, listOf(1 to (2..20).toList())),
            Expected("315574", "学术道德与学术写作规范-分论", "教一楼002", 10..11, listOf(5 to listOf(2,3,6,7,8))),
            Expected("315601", "婚姻与继承法", "教一楼417", 10..12, listOf(3 to (listOf(2,3,4,5)+(7..12)))),
            Expected("315625", "学术道德与学术写作规范-通论", "教一楼208", 5..6, listOf(2 to (13..17).toList())),
            Expected("315663", "羽毛球", "运动场东区体育馆-篮球", 1..2, listOf(5 to (2..16).toList())),
            Expected("315751", "新时代中国特色社会主义理论与实践", "教一楼002", 1..4, listOf(1 to (2..10).toList())),
            Expected("315821", "自然辩证法概论", "教一楼002", 10..13, listOf(4 to (2..11).toList())),
            Expected("316019", "机器学习", "教一楼207", 5..7, listOf(5 to (2..20).toList())),
            Expected("319037", "生成式模型与科学智能应用", "教二楼204", 7..8, listOf(2 to (listOf(2,3,4,5)+(7..12)), 4 to (listOf(2,3,4)+(6..11)), 7 to listOf(3))),
        )
        val allExpected = mutableListOf<String>()
        val detailPages = mutableListOf<Pair<String, String>>()
        for (e in expected) {
            val html = javaClass.getResource("/jw/fixtures/ucas/real-detail-0908/coursetime_${e.id}.html")!!.readText()
            val rows = e.days.flatMap { (day, weeks) -> weeks.flatMap { week -> e.nodes.map { node -> "${e.name}||${e.room}|$day|$node|$week" } } }.sorted()
            assertEquals(e.id, rows, occurrences(JwUcasParser(html).generateCourseList()))
            allExpected += rows
            detailPages += "https://xkcts.ucas.ac.cn:8443/course/coursetime/${e.id}" to html
        }
        val grid = javaClass.getResource("/jw/fixtures/ucas/person-schedule.real-0908.html")!!.readText()
        assertEquals(allExpected.sorted(), occurrences(JwUcasParser(UcasDetailFetch.combine(grid, detailPages)).generateCourseList()))
    }
}

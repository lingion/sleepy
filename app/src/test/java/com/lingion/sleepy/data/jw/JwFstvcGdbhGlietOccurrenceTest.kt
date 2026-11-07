package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwFstvcGdbhGlietOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w" }
        }
    }.sorted()

    private fun verify(type: String, source: String, expected: List<String>): List<JwCourse> {
        val rows = JwImportViewModel.parseHtmlStatic(source, type).first
        assertEquals(expected.sorted(), occurrences(rows))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(source, type).first))
        assertFalse(JwParserRegistry.allCandidates(source).any { it.first == type })
        val draft = JwImportDraftSnapshot(JwSchoolInfo("test", type), rows, emptyList())
        assertEquals(draft, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft)))
        return rows
    }

    private fun gdbh(day: Any, seq: Any, from: Any, to: Any, name: String = "电子技术") = JSONObject()
        .put("cname", name).put("name", "张").put("croomno", "海教楼").put("week", day)
        .put("seq", seq).put("startweek", from).put("endweek", to).put("xf", "2.5")
    private fun envelope(vararg rows: JSONObject) = JSONObject().put("data", JSONArray(rows.toList())).toString()

    @Test fun `GDBH weekday and six period pairs are distinct from teaching weeks`() {
        val source = envelope(*(1..6).map { gdbh(it, it, 9, 10) }.toTypedArray(), gdbh(7, 6, 14, 14).put("name", JSONObject.NULL))
        val expected = (1..6).flatMap { d -> listOf(9,10).flatMap { w -> listOf(d*2-1,d*2).map { n -> "电子技术|张|海教楼|$d|$n|$w" } } } +
            listOf(11,12).map { "电子技术||海教楼|7|$it|14" }
        assertTrue(verify("gdbh", source, expected).all { it.credit == 2.5f })
    }

    @Test fun `GDBH does not manufacture default weeks or clamp malformed fields`() {
        val missing = gdbh(1, 1, 1, 20).also { it.remove("startweek") }
        verify("gdbh", envelope(missing, gdbh(1.5,1,1,20), gdbh(1,0,1,20), gdbh(1,7,1,20),
            gdbh(1,1,8,3), gdbh(1,1,"1.0",4), gdbh(2,3,7,7,"有效课")),
            listOf(5,6).map { "有效课|张|海教楼|2|$it|7" })
    }

    private fun fstvc(name: String, week: Any, day: Any, nodes: Any, teacher: Any = "李", room: Any = "实训楼") =
        JSONObject().put("skbjmc", "软件技术一班").put("kcmc", name).put("skjsxm", teacher).put("zc", week)
            .put("skrq", "2026-09-02").put("jcshow", nodes).put("skcdmc", room).put("sknl", "实验内容")
            .put("xq", 1).put("xqs", day).put("xn", "2026-2027")

    @Test fun `FSTVC uses serializer keys and preserves single week meetings and notes`() {
        val source = JSONArray(listOf(fstvc("网络", 1,3,"1-2"), fstvc("网络", 4,5,"11-12","王","B"),
            fstvc("实践", 8,7,"7",JSONObject.NULL,JSONObject.NULL))).toString()
        val rows = verify("fstvc", source, listOf("网络|李|实训楼|3|1|1", "网络|李|实训楼|3|2|1",
            "网络|王|B|5|11|4", "网络|王|B|5|12|4", "实践|||7|7|8"))
        assertTrue(rows.all { it.note == "实验内容" })
    }

    @Test fun `FSTVC ignores injected cached nodes and rejects invalid coordinates`() {
        val source = JSONArray(listOf(fstvc("有效课", 7,2,"3-4").put("_nodesLi", JSONArray(listOf(11,12))),
            fstvc("坏周", 0,1,"1-2"), fstvc("坏日", 7,0,"1-2"), fstvc("小数", 7.5,1,"1-2"),
            fstvc("倒序", 1,1,"4-2"), fstvc("负节", 1,1,"-1-2"))).toString()
        verify("fstvc", source, listOf(3,4).map { "有效课|李|实训楼|2|$it|7" })
    }

    private fun gliet(rows: List<List<String>>) = "<table role='presentation'>" + rows.mapIndexed { i, cells ->
        "<tr><td>第${i*2+1}、${i*2+2}节</td>" + cells.joinToString("") { "<td>$it</td>" } + "</tr>"
    }.joinToString("") + "</table>"

    @Test fun `GLIET keeps all course blocks and per arrangement rooms`() {
        val source = gliet(listOf(listOf("<div>课号:101张老师<br>电路<br>(1-2)A<br>(7-8)B<br>课号:102李老师<br>信号<br>(4)C</div>",
            "<div>课号:103王老师<br>数学<br>(3-5)D</div>")))
        verify("gliet", source, listOf(1,2).flatMap { n -> listOf(1,2).map { "电路|张老师|A|1|$n|$it" } +
            listOf(7,8).map { "电路|张老师|B|1|$n|$it" } + "信号|李老师|C|1|$n|4" +
            listOf(3,4,5).map { "数学|王老师|D|2|$n|$it" } })
    }

    @Test fun `GLIET source positional contract has single 11th and 13th periods`() {
        val source = gliet((0..6).map { i -> listOf(if (i < 5) "" else "<div>课号:101张<br>晚课<br>(9)A</div>") })
        verify("gliet", source, listOf("晚课|张|A|1|11|9", "晚课|张|A|1|13|9"))
    }

    @Test fun `GLIET nested wrappers do not duplicate courses and invalid ranges do not leak`() {
        val source = gliet(listOf(listOf("<div><div>课号:101张<br>电路<br>(1-2)A<br>(-1-3)B<br>(4-1)C</div></div>")))
        verify("gliet", source, listOf(1,2).flatMap { w -> listOf(1,2).map { "电路|张|A|1|$it|$w" } })
    }

    @Test fun `production paged collectors preserve every original occurrence through draft`() {
        for (type in listOf("fstvc", "gdbh")) {
            val source = javaClass.getResource("/jw/fixtures/school-api/$type-offline-envelope.json")!!.readText()
            val expected = (0 until 65).flatMap { i ->
                val nodes = if (type == "fstvc") (if (i % 2 == 0) listOf(3,4) else listOf(11,12))
                    else listOf((i % 6) * 2 + 1, (i % 6) * 2 + 2)
                val week = 1 + i % (if (type == "fstvc") 20 else 10)
                nodes.map { "课程$i|教师$i|教室$i|${1+i%7}|$it|$week" }
            }
            verify(type, source, expected)
        }
    }

    @Test fun `FSTVC catalog uses native protocol only for former placeholders`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        for (name in listOf("福州职业技术学院", "福州软件职业技术学院", "福建体育职业技术学院")) {
            assertEquals(name, "fstvc", schools.single { it.name == name }.type)
            assertTrue(name, schools.single { it.name == name }.isUnverified)
        }
        assertEquals("sg_asset", schools.single { it.name == "福建信息职业技术学院" }.type)
        val privateEntry = schools.single { it.name == "桂林电子科技大学北海校区" }
        assertEquals("pending", privateEntry.status)
        assertTrue(privateEntry.url.isNullOrEmpty())
    }
}

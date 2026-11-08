package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwAhmuGdeiJluOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w" }
        }
    }.sorted()
    private fun verify(type: String, source: String, expected: List<String>) {
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.parserFor(type, source).generateCourseList()))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(source, type).first))
        assertEquals(expected.sorted(), occurrences(JwImportViewModel.parseHtmlStatic(source, type).first))
        assertFalse(JwParserRegistry.allCandidates(source).any { it.first == type })
    }
    private fun ahmu(name: String?, teacher: String?, room: String, day: Int, first: Int, length: Int, from: Int, to: Int) =
        JSONObject().put("kcmc", name ?: JSONObject.NULL).put("xm", teacher ?: JSONObject.NULL).put("jsmc", room)
            .put("xqj", day).put("sjd", first).put("skcd", length).put("qsz", from).put("jsz", to)

    @Test fun `AHMU preserves entire week ranges repeated meetings and trailing null record`() {
        val source = JSONArray(listOf(ahmu("课程甲", "张", "A", 1, 3, 2, 1, 3),
            ahmu("课程甲", "张", "A", 1, 3, 2, 7, 9), ahmu("课程甲", "李", "B", 7, 11, 3, 4, 4),
            ahmu(null, null, "", 0, 0, 0, 0, 0))).toString()
        verify("ahmu", source, listOf(1,2,3,7,8,9).flatMap { w -> listOf(3,4).map { n -> "课程甲|张|A|1|$n|$w" } } +
            listOf(11,12,13).map { "课程甲|李|B|7|$it|4" })
    }

    @Test fun `AHMU does not truncate fractions clamp coordinates or overflow length`() {
        val source = JSONArray(listOf(ahmu("小数", "T", "A", 1, 1, 2, 1, 2).put("qsz", 1.5),
            ahmu("反周", "T", "A", 1, 1, 2, 4, 2), ahmu("坏日", "T", "A", 0, 1, 2, 1, 2),
            ahmu("溢出", "T", "A", 1, 1, Int.MAX_VALUE, 1, 2), ahmu("好课", null, "B", 3, 5, 1, 9, 9))).toString()
        verify("ahmu", source, listOf("好课||B|3|5|9"))
    }

    @Test fun `AHMU explicit high periods survive declared registry and import paths`() {
        val source = JSONArray(listOf(ahmu("集中实践", "张", "实训楼", 3, 30, 3, 2, 3),
            ahmu("晚课", "李", "实验楼", 7, 31, 1, 6, 6))).toString()
        verify("ahmu", source, (2..3).flatMap { w -> (30..32).map { n -> "集中实践|张|实训楼|3|$n|$w" } } +
            "晚课|李|实验楼|7|31|6")
    }

    private fun gdei(name: String, week: String, node: String?, room: String, teacher: String) =
        "<div><div>$name</div><div><span>$week</span>" + (node?.let { "<span>$it</span>" } ?: "") +
            "</div><div>未用</div><div>$room</div><div>$teacher</div></div>"

    @Test fun `GDEI node override is local to each course and room suffix is independent`() {
        val html = "<table><tbody><tr><td>第1-2节(上午)</td><td>" +
            gdei("课甲", "第1-5单周;第8-10双周", "第5,7节", "实验楼(A座)(40人)", "张") +
            gdei("课乙", "第12周", null, "主楼(30人)", "李") + "</td><td>" +
            gdei("课丙", "第6周", null, "工坊", "王") + "</td></tr></tbody></table>"
        verify("gdei", html, listOf(1,3,5,8,10).flatMap { w -> listOf(5,7).map { n -> "课甲|张|实验楼(A座)|1|$n|$w" } } +
            listOf(1,2).map { "课乙|李|主楼|1|$it|12" } + listOf(1,2).map { "课丙|王|工坊|2|$it|6" })
    }

    @Test fun `GDEI malformed overrides cannot fall back to previous or row periods`() {
        val html = "<table><tbody><tr><td>第3-4节</td><td>" +
            gdei("坏节", "第1-4周", "第-1-2节", "A", "张") + gdei("坏周", "第1-4周;待定", null, "B", "李") +
            gdei("好课", "第9周", null, "C", "王") + "</td></tr></tbody></table>"
        verify("gdei", html, listOf(3,4).map { "好课|王|C|1|$it|9" })
    }

    private fun jlu(vararg rows: JSONObject) = JSONObject().put("datas", JSONObject().put("xsjxrwcx",
        JSONObject().put("rows", JSONArray(rows.toList())))).toString()
    private fun jluRow(name: String, teacher: String?, schedule: String?) = JSONObject().put("KCMC", name)
        .put("RKJS", teacher ?: JSONObject.NULL).put("PKSJDD", schedule ?: JSONObject.NULL)

    @Test fun `JLU multiple schedules preserve distinct rooms parity and single weeks`() {
        val source = jlu(jluRow("课程甲", "张", "[1-5单周,8-10双周 星期一 1-2节]A楼;[12周 星期日 9节]B楼"),
            jluRow("课程甲", "李", "[6周 星期三 5-6节]C楼"), jluRow("无安排", null, null))
        verify("jlu_post", source, listOf(1,3,5,8,10).flatMap { w -> listOf(1,2).map { n -> "课程甲|张|A楼|1|$n|$w" } } +
            "课程甲|张|B楼|7|9|12" + listOf(5,6).map { "课程甲|李|C楼|3|$it|6" })
    }

    @Test fun `JLU invalid text and signed numbers cannot be partially matched as valid ranges`() {
        val source = jlu(jluRow("坏课", "张", "[-1-4周 星期一 1-2节]A;[1-4周,待定 星期一 1-2节]B;" +
            "[1-4周 星期一 -1-2节]C;[4-1周 星期一 1-2节]D"), jluRow("好课", null, "[9周 星期七 3节]"))
        verify("jlu_post", source, listOf("好课|||7|3|9"))
    }

    @Test fun `JLU production collector feeds exact fields to parser and draft`() {
        val source = javaClass.getResource("/jw/fixtures/school-api/jlu-offline-envelope.json")!!.readText()
        verify("jlu_post", source, (1..3).flatMap { w -> listOf(5,6).map { "研究方法|张|研教楼|3|$it|$w" } })
        val courses = JwImportViewModel.parseHtmlStatic(source, "jlu_post").first
        val snapshot = JwImportDraftSnapshot(JwSchoolInfo("J", "吉林研究生"), courses, emptyList())
        assertEquals(snapshot, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot)))
    }

    @Test fun `JLU bracket variants keep independent week and node lists`() {
        for (text in listOf("1-3周 星期一[1,3节]A", "[1-3周] 星期一 [1,3节]A")) {
            verify("jlu_post", jlu(jluRow("课程", "张", text)),
                (1..3).flatMap { w -> listOf(1,3).map { "课程|张|A|1|$it|$w" } })
        }
    }

    @Test fun `declared catalog routes replace only existing registration placeholders`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val expected = mapOf("上海出版印刷高等专科学校" to "aic", "广东女子职业技术学院" to "aic", "成都职业技术学院" to "aic",
            "湖北城市建设职业技术学院" to "aic", "湖北生物科技职业学院" to "aic", "湘潭医卫职业技术学院" to "aic",
            "赣州师范高等专科学校" to "aic", "辽宁职业学院" to "aic", "恩施职业技术学院" to "hbmzu", "南京艺术学院" to "nua",
            "广东省外语艺术职业学院" to "gdei", "广东第二师范学院" to "gdei", "广西信息职业技术学院" to "gdei",
            "江西应用科技学院" to "gdei", "泸州医疗器械职业学院" to "gdei", "吉林大学新研究生管理系统2022 - 研究生" to "jlu_post")
        for ((name, type) in expected) {
            val school = schools.single { it.name == name }
            assertEquals(name, type, school.type)
            assertEquals(name, true, school.isUnverified)
        }
        assertEquals("zf_new", schools.single { it.name == "安徽信息工程学院" }.type)
        assertEquals("sg_asset", schools.single { it.name == "华中科技大学" }.type)
    }
}

package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixed-baseline field counterexamples. These are not authenticated captures. */
class JwOld19ExplicitBoundsTest {
    private fun ccsu(week: String = "1-2", complete: Boolean = false): String = "<table><tr>" +
        (if (complete) (1..5).flatMap { group -> (1..7).map { day -> group to day } } else listOf(1 to 1))
            .joinToString("") { (group, day) -> "<td id='$group-$day-2'>" +
                (if (group == 1 && day == 1) "课程甲<br>占位<br>[甲老师]<br>(${week}周)<br>A101" else "") + "</td>" } + "</tr></table>"
    private fun ccut(group: String, week: String = "1-2") = "<table id='kbtable'><tr><td>节次</td>" +
        "<td><div id='$group-1'>课程甲<br>占位<br>甲老师<br>${week}周<br>A101</div></td>" +
        "<td></td>".repeat(6) + "</tr></table>"
    private fun base(nodes: String, week: String = "1-2") = "<table id='kbtable'><tr><td>节次</td>" +
        "<td><div>课程甲<br>甲老师<br>${week}周[${nodes}节]<br>A101</div></td>" + "<td></td>".repeat(6) + "</tr></table>"
    private fun routes(type: String, source: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        if (source.trimStart().startsWith("<")) assertDeclaredHtmlFrame(type, source, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "显式课次回归", type=type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }
    private fun old(source: String, form: JwOldQzSuperParser.Form): List<JwCourse> {
        val type = when (form) {
            JwOldQzSuperParser.Form.CCSU -> "ccsu_qz_old"
            JwOldQzSuperParser.Form.CCUT -> "ccut_qz_old"
            JwOldQzSuperParser.Form.JLICT -> "jlict_qz_old"
            JwOldQzSuperParser.Form.BASE -> "qz_old"
        }
        val courses = JwOldQzSuperParser(source, form).generateCourseList()
        assertEquals(courses, routes(type, source))
        return courses
    }
    private fun expected(first: Int = 1, last: Int = 2, from: Int = 1, to: Int = 2) =
        listOf(JwCourse("课程甲", "A101", "甲老师", 1, first, last, from, to))

    @Test fun `CCSU existing individual cell is sufficient without all thirty five cells`() {
        assertEquals(expected(), old(ccsu(), JwOldQzSuperParser.Form.CCSU))
    }
    @Test fun `CCSU explicit week sixty five is retained in a complete source grid`() {
        assertEquals(expected(from=65,to=65), old(ccsu("65", true), JwOldQzSuperParser.Form.CCSU))
    }
    @Test fun `CCSU explicit three digit week is retained in a complete source grid`() {
        assertEquals(expected(from=100,to=101), old(ccsu("100-101", true), JwOldQzSuperParser.Form.CCSU))
    }
    @Test fun `CCSU fixed five group topology is still respected`() {
        assertTrue(old(ccsu().replace("1-1-2", "6-1-2"), JwOldQzSuperParser.Form.CCSU).isEmpty())
    }
    @Test fun `CCUT source group sixteen encodes periods thirty one and thirty two`() {
        assertEquals(expected(31,32), old(ccut("16"), JwOldQzSuperParser.Form.CCUT))
    }
    @Test fun `CCUT missing trailing weekdays do not erase the existing Monday column`() {
        assertEquals(expected(), old(ccut("1").replace("<td></td>".repeat(6), ""), JwOldQzSuperParser.Form.CCUT))
    }
    @Test fun `BASE missing trailing weekdays do not erase the existing Monday column`() {
        assertEquals(expected(), old(base("1-2").replace("<td></td>".repeat(6), ""), JwOldQzSuperParser.Form.BASE))
    }
    @Test fun `JLICT missing trailing weekdays keep the title column index`() {
        val source = "<div id='kb'><table><tr><th>节次</th></tr><tr><td>节次</td><td><a title='编号：0&#10;类别：0&#10;名称：课程甲&#10;教师：甲老师&#10;备注：0&#10;周次：1-2&#10;教室：A101'>课程</a></td></tr></table></div>"
        assertEquals(expected(), old(source, JwOldQzSuperParser.Form.JLICT))
    }
    @Test fun `old QZ merged columns cannot silently change the declared weekday`() {
        assertTrue(old(base("1-2").replace("<td><div>","<td colspan='2'><div>"), JwOldQzSuperParser.Form.BASE).isEmpty())
    }
    @Test fun `CCUT explicit group cannot overflow its two period mapping`() {
        for (group in listOf("0","184","2147483647","999999999999999999"))
            assertTrue(group, old(ccut(group), JwOldQzSuperParser.Form.CCUT).isEmpty())
    }
    @Test fun `JLICT row sixteen keeps title fields and real two period coordinates`() {
        val title = "编号：0&#10;类别：0&#10;名称：课程甲&#10;教师：甲老师&#10;备注：0&#10;周次：1-2&#10;教室：A101"
        val source = "<div id='kb'><table><tr><th>节次</th></tr>" +
            "<tr>".plus("<td></td>".repeat(8)).plus("</tr>").repeat(15) +
            "<tr><td>节次</td><td><a title='$title'>课程</a></td>" + "<td></td>".repeat(6) + "</tr></table></div>"
        assertEquals(expected(31,32), old(source, JwOldQzSuperParser.Form.JLICT))
    }
    @Test fun `BASE explicit three digit periods preserve both ends`() {
        assertEquals(expected(100,101), old(base("100-101"), JwOldQzSuperParser.Form.BASE))
    }
    @Test fun `BASE four digit compact periods retain the two two digit fields`() {
        assertEquals(expected(31,32), old(base("3132"), JwOldQzSuperParser.Form.BASE))
    }
    @Test fun `old QZ malformed or resource exceeding coordinates stay rejected`() {
        for (nodes in listOf("0-2","3-1","367-368","2147483647-2147483647"))
            assertTrue(nodes, old(base(nodes), JwOldQzSuperParser.Form.BASE).isEmpty())
        for (week in listOf("0","2-1","367","2147483647"))
            assertTrue(week, old(base("1-2",week), JwOldQzSuperParser.Form.BASE).isEmpty())
    }
    @Test fun `DHU end period comes from Chinese start plus actual rowspan`() {
        val source = "<table><tr><td>二十节</td><td rowspan='12'>课程甲 1-2周 甲老师 A101</td>" +
            "<td></td>".repeat(6) + "</tr></table>"
        assertEquals(expected(20,31), routes("dhu", source))
    }
    @Test fun `ECJTU explicit period thirty one is not invalid input`() {
        val source = "<table class='table_border'><tr><td>上午</td><td>课程甲<br>甲老师@A101<br>1-2 31<br></td>" +
            "<td></td>".repeat(6) + "</tr></table>"
        assertEquals(expected(31,31), routes("ecjtu", source))
    }

    private fun nested(first: Int = 1, length: Int = 2, bits: String = "11"): String {
        val place = JSONObject().put("classDay", 1).put("classSessions", first).put("continuingSession", length)
            .put("classWeek", bits).put("campusName", "A").put("teachingBuildingName", "1").put("classroomName", "01")
        val course = JSONObject().put("courseName", "课程甲").put("attendClassTeacher", "甲老师")
            .put("timeAndPlaceList", JSONArray().put(place))
        return JSONObject().put("dateList", JSONArray().put(JSONObject().put("selectCourseList", JSONArray().put(course)))).toString()
    }
    private fun flat(first: Int = 1, length: Int = 2, bits: String = "11") = JSONArray().put(JSONArray().put(JSONObject()
        .put("kcm", "课程甲").put("jsm", "甲老师").put("jxlm", "A1").put("jasm", "01").put("cxjc", length)
        .put("id", JSONObject().put("skxq", 1).put("skjc", first).put("skzc", bits)))).toString()

    @Test fun `new URP primary retains explicit start above thirty`() {
        assertEquals(expected(31,32), routes("urp_new", nested(first=31)))
    }
    @Test fun `new URP primary retains explicit duration above thirty`() {
        assertEquals(expected(1,31), routes("urp_new", nested(length=31)))
    }
    @Test fun `new URP primary retains bit sixty one`() {
        assertEquals(expected(from=61,to=61), routes("urp_new", nested(bits="0".repeat(60)+"1")))
    }
    @Test fun `new URP fallback retains explicit start above thirty`() {
        assertEquals(expected(31,32), routes("urp_new_ajax", flat(first=31)))
    }
    @Test fun `new URP fallback retains explicit duration above thirty`() {
        assertEquals(expected(1,31), routes("urp_new_ajax", flat(length=31)))
    }
    @Test fun `new URP fallback retains bit one hundred`() {
        assertEquals(expected(from=100,to=100), routes("urp_new_ajax", flat(bits="0".repeat(99)+"1")))
    }
    @Test fun `new URP resource guard rejects overflow before adding duration`() {
        for (source in listOf(nested(first=Int.MAX_VALUE), nested(first=366,length=2), nested(length=0),
            nested(bits="1".repeat(367)), flat(first=Int.MAX_VALUE), flat(first=366,length=2), flat(length=0), flat(bits="1".repeat(367)))) {
            assertTrue(routes("urp_new", source).isEmpty())
        }
    }
    @Test fun `GLIET row index fifteen is explicit single period thirty one`() {
        val source = "<table role='presentation'>" + (0..15).joinToString("") { i ->
            "<tr><td>第${i*2+1}、${i*2+2}节</td><td>" +
                (if(i==15) "<div>课号:101甲老师<br>课程甲<br>(1-2)A101</div>" else "") + "</td></tr>" } + "</table>"
        assertEquals(expected(31,31), routes("gliet", source))
    }
    @Test fun `CPPU explicit block sixteen preserves date fields credit and two periods`() {
        val source = """{"startDate":"2026-08-31","rows":[{"KCMC":"课程甲","JS":"甲老师","DDMC":"A101","XF":2.5,"SKRQ":"2026-08-31","JC":"16@上午"}]}"""
        assertEquals(expected(31,32,1,1).map { it.copy(credit=2.5f) }, routes("cppu", source))
    }
    private fun shcc(first: Int, length: Int) = "<script>var ypData=" + JSONArray().put(JSONObject().put("kcS","课程甲")
        .put("js","甲老师(1-2)").put("jsmc","A101").put("xqj",1).put("djj",first).put("skcd",length)) + ";</script>"
    @Test fun `SHCC explicit start above thirty is retained`() {
        assertEquals(expected(31,32), routes("shcc", shcc(31,2)))
    }
    @Test fun `SHCC explicit duration above thirty is retained`() {
        assertEquals(expected(1,31), routes("shcc", shcc(1,31)))
    }
}

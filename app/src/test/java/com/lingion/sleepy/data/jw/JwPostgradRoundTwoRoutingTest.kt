package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Does not refer to the new implementation class: missing factories fail as real empty routes. */
@RunWith(Parameterized::class)
class JwPostgradRoundTwoRoutingTest(private val type: String, private val source: String, private val expected: JwCourse) {
    @Test fun `declared type owns explicit course fields and cannot parse another protocol`() {
        val (courses, attempts) = JwParserRegistry.selectBest(source, type)
        assertEquals(type, listOf(expected), courses)
        assertTrue(type, attempts.any { it.type == type && it.courseCount == 1 && it.exception == null })
        val foreign = """{"activities":[{"courseName":"其他协议","weekday":1,"beginSection":1,"weeks":[1]}]}"""
        assertTrue(type, JwParserRegistry.selectBest(foreign, type).first.isEmpty())
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases(): List<Array<Any>> {
            fun course(name: String = "课程2", day: Int = 1, note: String = "", credit: Float = 0f) =
                JwCourse(name,"A","王",day,1,1,5,5,credit=credit,note=note)
            val ustc = listOf("序号","限选","代码","学院","类别","课程2","5","王","A: 1(1)")
                .joinToString("") { "<td><span>$it</span></td>" }
            val zju = "<div><form id='kcbForm'></form><table><tr><th>头</th></tr><tr><td><a><strong>课程2</strong><br><br>类别<br>时间||(5周)<br>王<br>A</a></td></tr></table></div>"
            return listOf(
                arrayOf("xjtu_post","<table id='tbl'><tr><td id='abc1'>课程：课程2<br>班级：<br>教师：王<br>教室：A<br>节次：1<br>周次：5周</td></tr></table>",course()),
                arrayOf("uestc_post","<table id='tbl'><tr><th>头</th></tr><tr><td>节</td><td>x/课程2/x/x/王/5周/(1)/A</td></tr></table>",course()),
                arrayOf("bfa_post","<div id='table'><table class='pages-sec-student-scheme-pages-student-timetable-query-timetable-index-tableContent'><tr><td _nk='1'><div><p>序号</p><p>课程2</p><p>限选</p><p>王</p><p>A[5-5]</p></div><div>1节</div></td></tr></table></div>",course(note="限选")),
                arrayOf("gdbyxy","<div id='pageRpt'><table><tr><td>[A1]课程2<br>王<br>[5周]一[1节]<br>A<br></td></tr></table></div>",course()),
                arrayOf("cupl_post","<table id='tabCT'><tr><th>节</th><th>星期一</th></tr><tr><td>第1节</td><td><b>课程2</b><br>第5周<br>教室：A<br>教师：王</td></tr></table>",course()),
                arrayOf("ysu_post","<table id='DataGrid1'><tr align='center'><th>节</th><th>星期一</th></tr><tr><td align='center'>1</td><td>课程2<br>[第5周](教师：王)(教室:A)</td></tr></table>",course()),
                arrayOf("whu_post","<table class='table_con'><tr><td>上午</td><td>节</td><td>课程2 5周1节 A 王</td></tr></table>",course()),
                arrayOf("hrbeu_post","<div id='StuCul_TimetableQry_TimeTable'><table class='WtbodyZlistS'><tr><td>节</td><td field='monday'>课程:课程2<br>教师:王<br>节次:1<br>周次:5周<br>教室:A</td></tr></table></div>",course()),
                arrayOf("shtu_post","<table id='div-table'><tr><td>节</td><td>周一</td></tr><tr><td>1</td><td>课程21班<br>王<br>A<br>第5周</td></tr></table>",course("课程")),
                arrayOf("ustc_post","<table id='tablexskbcx-index-table'><tr>$ustc</tr></table>",course(note="限选")),
                arrayOf("xauat_post","<script>var td=document.getElementById('td_1_41');td.innerHTML += \"课程:课程2<br>代码:A<br>教师：王<br>地点-A(第5周 每周)\";</script>",course()),
                arrayOf("zju_post",JSONArray().put(zju).toString(),course()),
                arrayOf("csv","课程名称,星期,开始节数,结束节数,教师,教室,周数\n课程2,1,1,1,王,A,5",course()),
                arrayOf("shuwei_old","<script>var activity = null;\nvar actTeachers = [{\"name\":\"王\"}];\nactivity = new TaskActivity(\"id\",\"课程2\",\"roomId\",\"A\",\"000001\");\nindex = 0 * unitCount + 0;\ntable0.marshalTable();</script>",course()),
            )
        }
    }
}

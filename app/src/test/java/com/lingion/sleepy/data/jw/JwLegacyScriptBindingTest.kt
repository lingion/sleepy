package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwLegacyScriptBindingTest {
    private fun parse(type: String, html: String)=JwParserRegistry.parserFor(type,html).generateCourseList()
    private val detail="课程:结构2<br>编号:A1<br>教师：陈（主讲）<br>地点-主楼(第2-6周 双周)"
    private val expected=listOf(JwCourse("结构2","主楼","陈",3,7,7,2,6,2))
    @Test fun `XAUAT binds its actual cell receiver across unrelated scripts`() {
        val page="<script>const banner='欢迎';</script><script>const courseCell=document.getElementById('td_3_47');courseCell.innerHTML += "+org.json.JSONObject.quote(detail)+";</script>"
        assertEquals(expected,parse("xauat_post",page))
    }
    @Test fun `XAUAT unrelated coordinates cannot shift lesson assignments`() {
        val page="<script>var menu=document.getElementById('td_1_41'); var tip='td_2_42'; var td=document.getElementById('td_3_47');td.innerHTML += "+org.json.JSONObject.quote(detail)+";</script>"
        assertEquals(expected,parse("xauat_post",page))
    }
    @Test fun `XAUAT multiple writes retain their actual cell and comments stay inert`() {
        val page="<script>let cell=document.getElementById('td_3_47'); /* td_1_41 */ cell . innerHTML += "+org.json.JSONObject.quote(detail)+";cell.innerHTML += "+org.json.JSONObject.quote(detail.replace("结构2","另一课"))+";</script>"
        assertEquals(expected+expected.map { it.copy(name="另一课") },parse("xauat_post",page))
    }
    private fun old(args: String, prelude: String="", slot: String="slot", stride: String="stride")="<script>let session=null;"+prelude+
        "const teachers=[{\"name\":\"王\"}];session = new TaskActivity("+args+");"+
        slot+" = 0 * "+stride+" + 2;grid.activities["+slot+"][0]=session;grid.marshalTable();</script>"
    @Test fun `old Shuwei retains its five argument schema with arbitrary bindings`() {
        val page=old("\"id\",title,\"roomId\",\"A\",\"01010\"","const title=\"专题\";")
        assertEquals(listOf(JwCourse("专题","A","王",1,3,3,1,1),JwCourse("专题","A","王",1,3,3,3,3)),parse("shuwei_old",page))
    }
    @Test fun `old Shuwei seven and eleven quoted argument group positions stay distinct`() {
        for(args in listOf("\"id\",\"专题(旧)\",\"roomId\",\"A\",\"010\",\"extra\",\"甲组\"",
            "\"id\",\"专题(旧)\",\"roomId\",\"A\",\"010\",\"extra\",\"x\",\"y\",\"甲组\",\"z\",\"q\"")) {
            assertEquals(listOf(JwCourse("专题(甲组)","A","王",1,3,3,1,1)),parse("shuwei_old",old(args)))
        }
    }
    @Test fun `old Shuwei uses only the index actually received by activities`() {
        val page=old("\"id\",\"专题\",\"roomId\",\"A\",\"010\"")
            .replace("slot =", "unrelated=4*stride+8;slot =")
        assertEquals(listOf(JwCourse("专题","A","王",1,3,3,1,1)),parse("shuwei_old",page))
    }
    @Test fun `old Shuwei stops coordinate fallback at its marshal call`() {
        val page = """<script>var activity=null;var actTeachers=[{"name":"王"}];
            activity=new TaskActivity("id","专题","roomId","A","010");
            index=0*unitCount+2;table0.marshalTable();index=4*unitCount+8;</script>"""
        assertEquals(listOf(JwCourse("专题","A","王",1,3,3,1,1)),parse("shuwei_old",page))
    }
    @Test fun `old Shuwei binds coordinates to the task actually inserted`() {
        val page=old("\"id\",\"专题\",\"roomId\",\"A\",\"010\"")
            .replace("grid.marshalTable();", "other=4*stride+8;grid.activities[other][0]=unrelated;grid.marshalTable();")
        assertEquals(listOf(JwCourse("专题","A","王",1,3,3,1,1)),parse("shuwei_old",page))
    }
    @Test fun `XAUAT follows aliases and spaced member bindings`() {
        val page="<script>view . cell=document.getElementById('td_3_47');const alias=view.cell;alias . innerHTML += "+org.json.JSONObject.quote(detail)+";</script>"
        assertEquals(expected,parse("xauat_post",page))
    }
    @Test fun `XAUAT invalid reassignment clears a prior cell coordinate`() {
        val page="<script>let cell=document.getElementById('td_3_47');cell=null;cell.innerHTML += "+org.json.JSONObject.quote(detail)+";</script>"
        assertTrue(parse("xauat_post",page).isEmpty())
    }

    @Test
    fun oldShuweiRenamedTitleAccumulatorReplacesPlaceholderLiteral() {
        val page = """<script>let 当前标题 = "专题";
            当前标题 += "2";
            let 当前活动 = null;
            当前活动 = new TaskActivity("id","占位","roomId","A","010");
            当前索引 = 0 * 每日节数 + 2;
            当前表.marshalTable();</script>"""
        assertEquals(listOf(JwCourse("专题2","A","",1,3,3,1,1)), parse("shuwei_old", page))
    }

    @Test
    fun oldShuweiKeepsTheDeclaredTitleWhenMultipleStringAccumulatorsAreAmbiguous() {
        val page = """<script>let 课程标题 = "专题";
            课程标题 += "2";
            let 页面标题 = "教务";
            页面标题 += "系统";
            let 任课教师 = [{"name":"王"}];
            let 当前活动 = null;
            当前活动 = new TaskActivity("id","明确课程","roomId","A","010");
            当前索引 = 0 * 每日节数 + 2;
            当前表.marshalTable();</script>"""
        assertEquals(listOf(JwCourse("明确课程","A","王",1,3,3,1,1)), parse("shuwei_old", page))
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwTableContainerResilienceTest {
    private val noise="<table id='menu'><tr><td>菜单</td><td>公告</td></tr></table>"
    private fun rows(vararg cells: String)="<tr>"+cells.joinToString(""){"<td>$it</td>"}+"</tr>"
    private val bfa="<table id='studentRecordTable'>"+rows("课程","教师","周次","星期","节次","教室")+rows("专题","甲","1,3周","3","1,3","A")+"</table>"
    private val sdu="<table id='ysjddDataTableId'>"+rows(*Array(12){"表头"})+rows("序号","代码","专题","类别","学分","院系","班级","甲","1,3周","3","1","A")+"</table>"
    private val jxau="<table id='KebiaoTable1'><tr><th>课表</th></tr><tr><th>星期</th></tr>"+
        "<tr><td class='left1'>星期三</td><td><dl><a>农业</a><dd>李</dd><dd>3-4(周)</dd><b>B</b></dl></td></tr>"+
        "<tr><td class='left1'>星期一</td></tr>".repeat(6)+"</table>"

    private val gdei="<table>"+rows("第1,3节", "<div><span>专题</span><div><span>第1,3周</span></div><i>类别</i><span>A</span><span>甲</span></div>")+"</table>"
    private val nua="<table><tr><th>表头</th></tr>"+rows("专题","甲","学分","1,3周 周三1,3","A")+"</table>"
    private val dhu="<table>"+rows("节次", "星期一","星期二","星期三","星期四","星期五","星期六","星期日")+
        rows("一节","专题 1,3周 甲 A","","","","","","")+"</table>"
    private fun cases()=listOf(Triple("bfa",bfa,"studentRecordTable"),Triple("sdu",sdu,"ysjddDataTableId"),
        Triple("jxau",jxau,"KebiaoTable1"),Triple("gdei",gdei,null),Triple("nua",nua,null),Triple("dhu",dhu,null))

    private fun verify(type: String, source: String, id: String?) {
        val expected=JwParserRegistry.parserFor(type,source).generateCourseList()
        assertTrue(expected.isNotEmpty())
        val renamed=if(id==null) source else source.replace("id='$id'","id='arbitraryContainer'")
        for (variant in listOf(renamed,noise+renamed,renamed+noise,"<section>"+noise+renamed+"</section>")) {
            assertEquals(type,expected,JwParserRegistry.parserFor(type,variant).generateCourseList())
            assertEquals(type,expected,JwParserRegistry.selectBest(variant,type).first)
        }
        assertTrue(type,JwParserRegistry.parserFor(type,noise).generateCourseList().isEmpty())
    }
    @Test fun `BFA six field row schema survives container rename and surrounding tables`()=verify("bfa",bfa,"studentRecordTable")
    @Test fun `SDU twelve field row schema survives container rename and surrounding tables`()=verify("sdu",sdu,"ysjddDataTableId")
    @Test fun `JXAU weekday and lesson structure survive container rename`()=verify("jxau",jxau,"KebiaoTable1")
    @Test fun `JXAU semantic weekday does not require a decorative class`() {
        val expected=JwJxauParser(jxau).generateCourseList()
        assertEquals(expected,JwJxauParser(jxau.replace("class='left1'","class='changed'")).generateCourseList())
    }
    @Test fun `duplicate complete layouts are selected once and different candidates are ambiguous`() {
        for((type,source,id) in cases()) {
            val renamed=if(id==null) source else source.replace("id='$id'","id='changed'")
            val expected=JwParserRegistry.parserFor(type,source).generateCourseList()
            assertEquals(type,expected,JwParserRegistry.parserFor(type,renamed+renamed).generateCourseList())
            val other=renamed.replace("专题","另一课").replace("农业","另一课")
            assertTrue(type,JwParserRegistry.parserFor(type,renamed+other).generateCourseList().isEmpty())
        }
    }
    @Test fun `GDEI selects the only full semantic grid around unrelated tables`()=verify("gdei",gdei,null)
    @Test fun `NUA selects the only full semantic list around unrelated tables`()=verify("nua",nua,null)
    @Test fun `DHU selects the only full Chinese period grid around unrelated tables`()=verify("dhu",dhu,null)
    @Test fun `SDU data rows do not require a preceding decoration row`() {
        val withoutHeader=sdu.replace(rows(*Array(12){"表头"}),"")
        assertEquals(JwSduParser(sdu).generateCourseList(),JwSduParser(withoutHeader).generateCourseList())
    }
    @Test fun `JXAU extra title rows do not truncate weekdays`() {
        val shifted=jxau.replace("<tr><th>课表</th></tr>","<tr><th>额外标题</th></tr>".repeat(8))
        assertEquals(JwJxauParser(jxau).generateCourseList(),JwJxauParser(shifted).generateCourseList())
    }
    @Test fun `explicit existing containers keep priority over other structural candidates`() {
        for((type,source,id) in cases().filter { it.third!=null }) {
            val other=source.replace("id='$id'","id='different'").replace("专题","另一课").replace("农业","另一课")
            assertEquals(type,JwParserRegistry.parserFor(type,source).generateCourseList(),JwParserRegistry.parserFor(type,other+source).generateCourseList())
        }
    }

}

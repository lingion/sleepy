package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwJsonDataResilienceTest {
    private fun parse(type: String, source: JSONObject)=JwParserRegistry.parserFor(type,source.toString()).generateCourseList()
    private fun kust(vararg cells: Any)=JSONObject().put("data",JSONObject().put("zs","3").put("resultsJsonArr",JSONArray().put(JSONArray()).put(JSONArray()).also { rows ->
        cells.forEach { rows.put(JSONArray().put("").put(it)) }
    }))
    @Test fun `KUST keeps repeated lessons separated by an empty period`() {
        val rows=parse("kust",kust("专题,A;甲","专题,A;甲","","专题,A;甲"))
        assertEquals(listOf(JwCourse("专题","A","甲",1,1,2,3,3),JwCourse("专题","A","甲",1,4,4,3,3)),rows)
    }
    @Test fun `KUST skips a malformed cell without losing following good lessons`() {
        assertEquals(listOf(JwCourse("专题","A","甲",1,2,2,3,3)),parse("kust",kust(JSONObject(),"专题,A;甲")))
    }
    private fun nuit(detail: String)=JSONObject().put("datas",JSONArray().put(JSONObject().put("courseName","专题").put("classDateAndPlace",detail)))
    @Test fun `NUIT commas inside a week list remain part of that arrangement`() {
        val source=nuit("1,3,5周/星期三/第1节-第2节/甲[主讲]/A")
        assertEquals(listOf(1,3,5).map { JwCourse("专题","A","甲",3,1,2,it,it) },parse("nuit",source))
    }
    @Test fun `NUIT separates full appointments without cutting week or room commas`() {
        val source=nuit("1,3周/星期三/第1节-第2节/甲[主讲]/A,东楼，4周/星期日/第5节-第5节/乙[主讲]/B")
        assertEquals(listOf(1,3).map { JwCourse("专题","A,东楼","甲",3,1,2,it,it) }+JwCourse("专题","B","乙",7,5,5,4,4),parse("nuit",source))
    }
    @Test fun `NUIT malformed scalar row does not erase a good following row`() {
        val source=nuit("1周/星期三/第1节-第2节/甲[主讲]/A")
        source.getJSONArray("datas").put(0,JSONObject().put("courseName",JSONArray()).put("classDateAndPlace","bad"))
            .put(JSONObject().put("courseName","专题").put("classDateAndPlace","1周/星期三/第1节-第2节/甲[主讲]/A"))
        assertEquals(listOf(JwCourse("专题","A","甲",3,1,2,1,1)),parse("nuit",source))
    }
    private fun scuCourse(name: String)=JSONObject().put("courseName",name).put("attendClassTeacher","甲").put("timeAndPlaceList",JSONArray().put(JSONObject()
        .put("teachingBuildingName","楼").put("classroomName","A").put("weekDescription","1,3周")
        .put("classSessions",1).put("continuingSession",2).put("classDay",2)))
    @Test fun `SCU retains every explicit date group after unrelated or empty groups`() {
        val source=JSONObject().put("dateList",JSONArray().put(JSONObject().put("selectCourseList",JSONArray()))
            .put(JSONObject().put("selectCourseList",JSONArray().put(scuCourse("专题")))))
        assertEquals(listOf(1,3).map { JwCourse("专题","楼A","甲",3,1,2,it,it) },parse("scu",source))
    }
    @Test fun `SCU explicit malformed duration never becomes a single period`() {
        val row=scuCourse("坏课");row.getJSONArray("timeAndPlaceList").getJSONObject(0).put("continuingSession","broken")
        val source=JSONObject().put("dateList",JSONArray().put(JSONObject().put("selectCourseList",JSONArray().put(row).put(scuCourse("专题")))))
        assertEquals(listOf(1,3).map { JwCourse("专题","楼A","甲",3,1,2,it,it) },parse("scu",source))
    }
    private fun zju(br: String)=JSONObject().put("kbList",JSONArray().put(JSONObject().put("xqj",3).put("dsz","2").put("djj",1).put("skcd",2)
        .put("kcb",listOf("专题","1,3周","甲","A").joinToString(br))))
    @Test fun `ZJU break tag case and closing style do not lose fields`() {
        val expected=parse("zju",zju("<br>"));assertTrue(expected.isNotEmpty())
        for(br in listOf("<BR>","<br/>","<br />","<Br >")) assertEquals(br,expected,parse("zju",zju(br)))
    }
    @Test fun `ZJU explicit malformed duration cannot silently become one period`() {
        val source=zju("<br>");source.getJSONArray("kbList").getJSONObject(0).put("skcd","broken")
        assertTrue(parse("zju",source).isEmpty())
    }
    private fun yethan(slot: String="1",time: String="1周 星期三 1-2节")=JSONObject().put("code","00000").put("data",JSONArray().put(JSONObject()
        .put("courseName","专题").put("staffName","甲").put("classTime$slot",time).put("classPlace$slot","A")))
    @Test fun `YETHAN uses actual numeric schedule slots after forty`() {
        assertEquals(listOf(JwCourse("专题","A","甲",3,1,2,1,1)),parse("yethan",yethan("41")))
    }
    @Test fun `YETHAN explicit inverted node interval does not manufacture a lesson`() {
        assertTrue(parse("yethan",yethan(time="1周 星期三 5-3节")).isEmpty())
        assertTrue(parse("yethan",yethan(time="1周 星期三 0节")).isEmpty())
    }
    private fun wiseduRow()=JSONObject().put("KCM","专题").put("SKJS","甲").put("JASMC","A").put("SKXQ",3).put("KSJC",1).put("JSJC",2).put("SKZC","101")
    private fun wisedu(vararg rows: Any)=JSONObject().put("datas",JSONObject().put("xskcb",JSONObject().put("rows",JSONArray(rows.toList()))))
    @Test fun `Wisedu bad row type cannot erase later valid rows`() {
        assertEquals(listOf(JwCourse("专题","A","甲",3,1,2,1,3,1)),parse("wisedu",wisedu("broken",wiseduRow())))
    }
    @Test fun `Wisedu explicit malformed end cannot become the start`() {
        assertTrue(parse("wisedu",wisedu(wiseduRow().put("JSJC","broken"))).isEmpty())
        assertTrue(parse("wisedu",wisedu(wiseduRow().put("SKXQ",8))).isEmpty())
    }
    @Test fun `KUST out of range weekday columns cannot overwrite Sunday`() {
        val row=JSONArray().put("")
        repeat(7) { row.put("") }
        row.put("坏课,A;甲")
        val source=JSONObject().put("data",JSONObject().put("zs",3).put("resultsJsonArr",JSONArray().put(JSONArray()).put(JSONArray()).put(row)))
        assertTrue(parse("kust",source).isEmpty())
    }
    @Test fun `KUST lesson identity preserves delimiters inside actual fields`() {
        val expected=listOf(JwCourse("专题|A","B","甲",1,1,1,3,3),JwCourse("专题","A|B","甲",1,2,2,3,3))
        assertEquals(expected,parse("kust",kust("专题|A,B;甲","专题,A|B;甲")))
    }
    @Test fun `NUIT malformed explicit week and node bounds are not guessed`() {
        for(detail in listOf("1-x周/星期三/第1节-第2节/甲[主讲]/A",
            "1-367周/星期三/第1节-第2节/甲[主讲]/A", "1周/星期三/第5节-第2节/甲[主讲]/A"))
            assertTrue(detail,parse("nuit",nuit(detail)).isEmpty())
    }
    @Test fun `SCU structural confidence includes actual later date groups`() {
        val source=JSONObject().put("dateList",JSONArray().put("unrelated")
            .put(JSONObject().put("selectCourseList",JSONArray().put(scuCourse("专题")))))
        val parser=JwScuParser(source.toString())
        assertEquals(listOf(1,3).map { JwCourse("专题","楼A","甲",3,1,2,it,it) },parser.generateCourseList())
        assertTrue(parser.confidence()>0)
        assertTrue(parser.matchedFeatures().contains("selectCourseList"))
    }

}

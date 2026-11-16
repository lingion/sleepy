package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwJsonBindingResilienceTest {
    private val zf = """[{"kcmc":"专题","xm":"张/李","cdmc":"A101","xqj":7,"jc":"13-14","zcd":"1,3,5周"}]"""
    private val nested = """{"dateList":[{"selectCourseList":[{"courseName":"专题","attendClassTeacher":"张/李","timeAndPlaceList":[{"classDay":7,"classSessions":13,"continuingSession":2,"classWeek":"10101","classroomName":"A101"}]}]}]}"""
    private val flat = """[[{"kcm":"专题","jsm":"张/李","jasm":"A101","cxjc":2,"id":{"skxq":7,"skjc":13,"skzc":"10101"}}],[],[{"kcm":"实验","cxjc":1,"id":{"skxq":2,"skjc":4,"skzc":"010"}}]]"""
    private val xmu = """[{"KCM":"专题","SKJS":"张/李","SKDD":"A101","SKXQ":7,"KSJC":13,"JSJC":14,"SKZC":"10101"}]"""

    @Test fun `ZF arbitrary bindings and nested wrappers preserve full fields`() {
        val expected = JwNewZfParser(zf).generateCourseList()
        assertTrue(expected.isNotEmpty())
        for (source in listOf("<script>let renamed=$zf;</script>",
            "<script>window.state={payload:{renamed:$zf}};</script>",
            "{\"payload\":{\"renamed\":$zf}}")) {
            assertEquals(expected, JwNewZfParser(source).generateCourseList())
        }
    }

    @Test fun `ZF examples and comments cannot override actual schedule`() {
        val fake = zf.replace("专题", "假数据")
        val source = "<pre>var kbList=$fake;</pre><script>/*var kbList=$fake;*/const example=${JSONObject.quote(fake)};let renamed=$zf;</script>"
        assertEquals(JwNewZfParser(zf).generateCourseList(), JwNewZfParser(source).generateCourseList())
        assertTrue(JwNewZfParser("<script>/*var kbList=$fake;*/</script>").generateCourseList().isEmpty())
    }

    @Test fun `nested URP ignores quoted commented and unrelated dateList markers`() {
        val expected = JwNewUrpParser(nested).generateCourseList()
        val source = "<pre>$nested</pre><script>/*$nested*/const example=${JSONObject.quote(nested)};const other={\"dateList\":\"help\"};let payload={data:$nested};</script>"
        assertEquals(expected, JwNewUrpParser(source).generateCourseList())
        for (noise in listOf("/*$nested*/", "const example=${JSONObject.quote(nested)};")) {
            assertTrue(JwNewUrpParser("<script>$noise</script>").generateCourseList().isEmpty())
        }
    }

    @Test fun `flat URP ignores examples while retaining all two dimensional groups`() {
        val expected = JwNewUrpSuperParser(flat).generateCourseList()
        assertEquals(2, expected.size)
        val source = "<script>/*${flat.replace("专题", "假数据")}*/const text=${JSONObject.quote(flat)};const payload={rows:$flat};</script>"
        assertEquals(expected, JwNewUrpSuperParser(source).generateCourseList())
        assertFalse(JwNewUrpSuperParser("<script>/*$flat*/</script>").hasFlatArraySchema())
    }

    @Test fun `XMU renamed bindings need no trailing declaration and keep week one`() {
        val expected = listOf(1,3,5).map { JwCourse("专题","A101","张/李",7,13,14,it,it) }
        for (binding in listOf("let renamed", "const renamed", "window.payload")) {
            val source = "<script>$binding=$xmu;</script>"
            assertEquals(expected, WakeUpCompat.parseJzXmu(source))
            assertEquals(expected, JwParserRegistry.selectBest(source,"jz").first)
            assertTrue(JwJzParser(source).confidence() > 0)
        }
    }

    @Test fun `XMU comments strings and split schema are not schedules`() {
        for (source in listOf("<pre>var scheduleList=$xmu;var notArrangedList=[];</pre>",
            "<script>/*var scheduleList=$xmu;var notArrangedList=[];*/</script>",
            "<script>var example=${JSONObject.quote(xmu)};</script>",
            "<script>const rows=[{\"KCM\":\"专题\"},{\"SKXQ\":7,\"KSJC\":13,\"JSJC\":14,\"SKZC\":\"10101\"}];</script>")) {
            assertTrue(source, WakeUpCompat.parseJzXmu(source).isEmpty())
        }
    }
}

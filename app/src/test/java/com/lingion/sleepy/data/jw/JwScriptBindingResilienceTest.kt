package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwScriptBindingResilienceTest {
    private fun rename(source: String, names: List<String>): String = names.foldIndexed(source) { i, text, name ->
        text.replace(Regex("\\b${Regex.escape(name)}\\b"), "binding_${i}_changed")
    }

    private val classic = """<script>
        var table0 = new CourseTable(2026,84);
        var unitCount = 13;
        var actTeachers = [{id:1,name:"甲老师"},{id:2,name:"乙老师"}];
        var courseNameLessonNo = "实验(甲),专题";
        activity = new TaskActivity(ids.join(','),names.join(','),"C1",this.courseNameLessonNo,"R1","A;101","0101");
        index = 2 * unitCount + 11;
        table0.activities[index][0] = activity;
        index = 2 * unitCount + 12;
        table0.activities[index][1] = activity;
        table0.marshalTable();
        </script>"""

    @Test fun `classic binding renames and declaration styles preserve complete occurrences`() {
        val expected = listOf(1,3).map { JwCourse("实验(甲),专题","A;101","甲老师/乙老师",3,12,13,it,it) }
        assertEquals(expected,JwClassicEamsParser(classic).generateCourseList())
        val renamed = rename(classic,listOf("table0","unitCount","actTeachers","courseNameLessonNo","activity","index"))
        for (source in listOf(renamed,renamed.replace("var ","let "),renamed.replace("var ","const "))) {
            assertEquals(expected,JwClassicEamsParser(source).generateCourseList())
            assertEquals(expected,JwParserRegistry.selectBest(source,"classic_eams").first)
        }
    }

    @Test fun `classic lexical trivia and constructor spacing preserve values`() {
        val source=classic.replace("new TaskActivity(","new /* note */ TaskActivity (\n")
            .replace("unitCount = 13;","unitCount = /*stride*/ 13\n")
            .replace("index = 2", "index = /*coordinate*/ 2")
        assertEquals(JwClassicEamsParser(classic).generateCourseList(),JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `classic comments strings and page examples cannot create activities`() {
        for(source in listOf("<pre>${classic.removePrefix("<script>").removeSuffix("</script>")}</pre>",
            "<script>/*${classic.removePrefix("<script>").removeSuffix("</script>")}*/</script>",
            "<script>const example=${org.json.JSONObject.quote(classic)};</script>")) {
            assertTrue(source,JwClassicEamsParser(source).generateCourseList().isEmpty())
        }
    }

    @Test fun `ecupl renamed activity and quote whitespace changes preserve full fields`() {
        val source=javaClass.classLoader!!.getResource("jw/ecupl_script_sample.html")!!.readText()
        val expected=JwEcuplScriptParser(source).generateCourseList()
        assertTrue(expected.isNotEmpty())
        for(mutated in listOf(rename(source,listOf("activity")),source.replace("\"","'"),
            source.replace("newActivity(","newActivity (\n").replace("addActivityByTime(","addActivityByTime ( "),
            source.replace("new CourseTable(","new /* table */ CourseTable ( "))) {
            assertEquals(expected,JwEcuplScriptParser(mutated).generateCourseList())
        }
    }

    @Test fun `ecupl comments and unrelated activity bindings cannot supply coordinates`() {
        val source=javaClass.classLoader!!.getResource("jw/ecupl_script_sample.html")!!.readText()
        val expected=JwEcuplScriptParser(source).generateCourseList()
        val withNoise=source.replace("addActivityByTime(activity,1,800,940);",
            "addActivityByTime(unrelated,7,800,940);addActivityByTime(activity,1,800,940);")
        assertEquals(expected,JwEcuplScriptParser(withNoise).generateCourseList())
        val body=source.substringAfter("<script language=\"JavaScript\">").substringBefore("</script>")
        assertTrue(JwEcuplScriptParser("<script>/*$body*/</script>").generateCourseList().isEmpty())
        val separated=source.replace("var activity;", "</script><script>var activity;")
        assertTrue(JwEcuplScriptParser(separated).generateCourseList().isEmpty())
    }

    @Test fun `annual ecupl binds arbitrary table name and keeps origin conversion`() {
        val source="""<table class="listTable"><tr><td>课程</td></tr></table>
            <script>var table0=new CourseTable(2024,84); var unitCount=12;
            activity=new TaskActivity("T1","王老师","C1","法学(C1)","R1","A101","${"0".repeat(35)}11");
            index=2*unitCount+4; index=2*unitCount+5; table0.marshalTable(36,1,20);</script>"""
        val expected=JwEcuplParser(source).generateCourseList()
        assertTrue(expected.isNotEmpty())
        val renamed=rename(source,listOf("table0","unitCount","activity","index"))
        assertEquals(expected,JwEcuplParser(renamed).generateCourseList())
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwStaticScriptVariationsTest {
    private val expected=listOf(JwCourse("实验,甲","A","王",1,1,1,1,1))
    private fun classic(declarations: String, title: String="title")="""<script>$declarations
        event=new TaskActivity("T","王","C",$title,"R","A","01");
        slot=0*stride+0;table.activities[slot][0]=event;table.marshalTable();</script>"""
    @Test fun `static literal concatenation and snapshots through aliases preserve course titles`() {
        for(declarations in listOf("const stride=12;const title='实验'+',甲';",
            "const stride=12;const original='实验,甲';const title=original;",
            "const stride=12;let original='实验,甲';const title=original;original='另一个';"))
            assertEquals(expected,JwClassicEamsParser(classic(declarations)).generateCourseList())
    }
    @Test fun `member whitespace in both declaration and reference preserves real binding`() {
        assertEquals(expected,JwClassicEamsParser(classic("const stride=12;state . title='实验,甲';", "state . title")).generateCourseList())
    }
    @Test fun `unknown reassignment invalidates earlier string and is never executed`() {
        val source=classic("const stride=12;let title='过期课程';title=getFromServer();")
        assertTrue(JwClassicEamsParser(source).generateCourseList().isEmpty())
    }
    @Test fun `parenthesized concatenation and append preserve assignment time snapshots`() {
        for (declarations in listOf(
            "const stride=12;let title=('实验'+(',甲'));",
            "const stride=12;let part='实验';let title=part;part='其他';title+=',甲';",
            "const stride=12;let title='实验';title += (',甲');",
            "const stride=12;state . first='实验';const title=state . first+',甲';"
        )) assertEquals(declarations, expected, JwClassicEamsParser(classic(declarations)).generateCourseList())
    }
    @Test fun `unknown appends and unknown aliases never resurrect stale strings`() {
        for (statements in listOf("title+=getFromServer();", "title=missing;", "title=missing+'甲';")) {
            assertTrue(statements, JwClassicEamsParser(classic("const stride=12;let title='过期课程';$statements")).generateCourseList().isEmpty())
        }
    }
    @Test fun `regular expressions after control flow and return are masked as data`() {
        val fake="""new TaskActivity("T","王","C","假课","R","A","01")"""
        for (prefix in listOf("const sample=", "if (ok) ", "while (ok) ", "function f(){return ", "const sample = ok ? ")) {
            val script=JwScriptSource(prefix+"/"+fake+"/g;")
            assertFalse(prefix, script.matches(Regex("new\\s+TaskActivity")).any())
        }
    }
    @Test fun `regex character classes and escaped slash cannot reveal fake objects`() {
        for (literal in listOf("/[{]new TaskActivity(1)[}]/gi", "/\\/new TaskActivity(1)/")) {
            assertFalse(literal, JwScriptSource("const sample=$literal;").matches(Regex("new\\s+TaskActivity")).any())
        }
    }
    @Test fun `division and division assignment retain source coordinates`() {
        for (code in listOf("const ratio=4/2;", "ratio/=2;", "const ratio=(4+2)/2;", "const ratio={value:4}/2;", "const ratio=state.return/2;")) {
            assertEquals(code, code, JwScriptSource(code).code)
        }
    }
    @Test fun `strings keep a bounded static domain without numeric coercion or calls`() {
        for (expression in listOf("'x'+1", "makeTitle()", "missing", "(".repeat(70)+"'x'"+")".repeat(70))) {
            assertNull(expression, JwScriptSource("").string(expression))
        }
    }
    @Test fun `regular expression literals cannot create structured JSON schedules`() {
        val rows="""[{"kcmc":"假课","xqj":1,"jc":"1","zcd":"1周"}]"""
        assertTrue(JwNewZfParser("<script>const example=/"+rows+"/;</script>").generateCourseList().isEmpty())
    }
    @Test fun `regular expression literals cannot create task activities`() {
        val fake="""new TaskActivity("T","王","C","假课","R","A","01")"""
        val source="<script>const stride=12;const pattern=/"+fake+"/;slot=0*stride+0;table.marshalTable();</script>"
        assertTrue(JwClassicEamsParser(source).generateCourseList().isEmpty())
    }
    @Test fun `ordinary division before actual course code remains inert`() {
        assertEquals(expected,JwClassicEamsParser(classic("const fraction=4/2;const stride=12;const title='实验,甲';")).generateCourseList())
    }
    private val ucas="""[{"courseName":"专题","coursePlace":"A","courseWeek":5,"courseTime":8193}]"""
    @Test fun `UCAS skips unrelated objects and binds actual course time list`() {
        val expected=JwUcasParser("{\"courseTimeList\":"+ucas+"}").generateCourseList()
        assertTrue(expected.isNotEmpty())
        for(source in listOf("<script>const menu={unrelated:true};const payload={courseTimeList:"+ucas+"};</script>",
            "<script>const payload={nested:{courseTimeList:"+ucas+"}};</script>"))
            assertEquals(expected,JwUcasParser(source).generateCourseList())
    }
}

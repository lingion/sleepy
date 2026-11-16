package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwThuBindingResilienceTest {
    private fun sample() = javaClass.classLoader!!.getResource("jw_fixtures/thu/zhkbcx-courses.sample.html")!!.readText()
    private fun renamed(source: String) = source.replace("setInitValue", "renderSchedule")
        .replace("strHTML1", "details").replace("strHTML", "courseMarkup")
        .replace("gridColumns", "headings").replace("gridData", "records").replace("var ", "const ")
    private fun normal() = """
        strHTML += "<a href='page?p_id=ABCDEFG1'>";
        strHTML += "<b>数学</b>";
        strHTML1 += "；王"; strHTML1 += "；必修";
        strHTML1 += "；第1,3周"; strHTML1 += "；A楼";
        document.getElementById('a2_3').innerHTML += strHTML;
    """
    private fun html(body: String) = "<script>function setInitValue(){$body}</script>"

    @Test fun `THU function and buffers can be renamed with coordinate whitespace`() {
        val original = sample()
        val variant = renamed(original).replace("getElementById(", "getElementById ( ")
            .replace(").innerHTML", " ) . innerHTML")
        val expected = JwThuparser(original).generateCourseList()
        assertEquals(5, expected.size)
        assertEquals(expected, JwThuparser(variant).generateCourseList())
        assertTrue(JwThuparser(variant).confidence() >= 90)
        assertEquals(expected, JwParserRegistry.selectBest(variant, "thu").first)
    }

    @Test fun `renamed metadata keeps special clocks teacher and note`() {
        val source = html("""
            var gridColumns=["课程名","任课教师","选课文字说明"];
            var gridData=[["实验","李, 陈","必选, 甲[班]"]];
            strHTML="<a class='blue_red_none' href='page?p_id=ABCDEFG1'><b><font color='blue'>实验</font></b></a><font color='blue'>北大(A楼；第2,5周；时间：8:05-9:40)</font>";
            document.getElementById('a1_7').innerHTML += strHTML;
        """)
        val expected = listOf(2,5).map { JwCourse("实验","A楼(北大)","李, 陈",7,1,2,it,it,startTime="08:05",endTime="09:40",note="必选, 甲[班]") }
        assertEquals(expected, JwThuparser(renamed(source)).generateCourseList())
    }

    @Test fun `unrelated semicolon strings do not enter renamed details`() {
        val source = renamed(html(normal())).replace("details += \"；A楼\";", "noise += \"；无关地点\"; details += \"；A楼\";")
        assertEquals(JwThuparser(html(normal())).generateCourseList(), JwThuparser(source).generateCourseList())
    }

    @Test fun `renamed inherited course keeps its fields across coordinates`() {
        val source = html(normal()+"""strHTML += "<a href='page?p_id=ABCDEFG1'>";
            document.getElementById('a2_4').innerHTML += strHTML;""")
        val expected = listOf(3,4).flatMap { day -> listOf(1,3).map { JwCourse("数学","A楼","王",day,3,5,it,it) } }
        assertEquals(expected, JwThuparser(renamed(source)).generateCourseList())
    }

    @Test fun `separate scripts cannot complete another scripts course`() {
        val partial = normal().substringBefore("document.getElementById")
        val source = "<script>function setInitValue(){$partial}</script>"+
            "<script>document.getElementById('a2_3').innerHTML += strHTML;</script>"
        assertTrue(JwThuparser(source).generateCourseList().isEmpty())
    }

    @Test fun `quoted and commented coordinate examples cannot create courses`() {
        val body = renamed(normal())
        for (text in listOf("/*$body*/", "const example=${JSONObject.quote(body)};", "const example=`$body`;")) {
            assertTrue(JwThuparser("<script>$text</script>").generateCourseList().isEmpty())
        }
    }

    @Test fun `unrelated arrays do not remove metadata from a matching course`() {
        val special = """courseMarkup="<a class='blue_red_none' href='page?p_id=ABCDEFG1'><b><font color='blue'>实验</font></b></a><font color='blue'>北大(A楼；第2周)</font>";
            document.getElementById('a1_7').innerHTML += courseMarkup;"""
        val metadata = """const headings=["课程名","任课教师","选课文字说明"];
            const records=[["实验","李老师","必选说明"]];"""
        val noise = """const unrelated=[["菜单","无关教师","无关说明"]];"""
        val expected = listOf(JwCourse("实验","A楼(北大)","李老师",7,1,2,2,2,note="必选说明"))
        for (body in listOf(noise+metadata+special, metadata+noise+special)) {
            assertEquals(expected, JwThuparser(html(body)).generateCourseList())
        }
    }

    @Test fun `unrelated heading schema does not select columns for course metadata`() {
        val body = """const menuHeadings=["编号","课程名"];
            const menuRows=[["课程列表","无关"]];
            const headings=["任课教师","选课文字说明","课程名"];
            const records=[["李老师","必选说明","实验"]];
            courseMarkup="<a class='blue_red_none' href='page?p_id=ABCDEFG1'><b><font color='blue'>实验</font></b></a><font color='blue'>北大(A楼；第2周)</font>";
            document.getElementById('a1_7').innerHTML += courseMarkup;"""
        assertEquals(listOf(JwCourse("实验","A楼(北大)","李老师",7,1,2,2,2,note="必选说明")),
            JwThuparser(html(body)).generateCourseList())
    }
}

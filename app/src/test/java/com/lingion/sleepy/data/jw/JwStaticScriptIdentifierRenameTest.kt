package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwStaticScriptIdentifierRenameTest {
    private fun sample(path: String) = requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    @Test fun `Nankai renamed portal and script bindings retain every scheduled occurrence`() {
        val source = sample("jw/fixtures/classic_eams/nankai-course-table-action.html")
        val expected = JwParserRegistry.parserFor("classic_eams", source).generateCourseList()
        assertEquals(35, expected.size)
        val changed = source.replace("南开大学 综合教务管理系统", "教学服务")
            .replace("unitCount", "每日节数")
            .replace("index", "时段")
            .replace("actTeachers", "任课教师")
            .replace("actTeacherName", "教师姓名")
            .replace("table0", "表格")
        assertEquals(expected, JwParserRegistry.selectBest(changed, "classic_eams").first)
    }

    @Test fun `THU renamed output and detail buffers preserve all five courses`() {
        val source = sample("jw_fixtures/thu/zhkbcx-courses.sample.html")
        val expected = JwThuparser(source).generateCourseList()
        assertEquals(5, expected.size)
        for (buffer in listOf("课程内容", "markup$", "re\u0301sultat")) {
            val changed = source.replace("strHTML1", "课程详情").replace("strHTML", buffer)
            assertEquals(buffer, expected, JwParserRegistry.selectBest(changed, "thu").first)
        }
    }

    @Test fun `Classic EAMS follows the member path used for the stride and slot`() {
        val html = """<script>
            state . periods = 13;
            event = new TaskActivity("1","王","2","绘图","3","A","0101");
            state . slot = 2 * state.periods + 11;
            timetable.activities[state.slot][0] = event;
            menu.slot = 0 * state.periods + 0;
            state.slot = 2 * state . periods + 12;
            timetable.activities[state . slot][0] = event;
            timetable.marshalTable();
        </script>"""
        val expected = listOf(1, 3).map { week -> JwCourse("绘图","A","王",3,12,13,week,week) }
        assertEquals(expected, JwParserRegistry.selectBest(html, "classic_eams").first)
    }

    @Test fun `Classic EAMS stops at marshal before unrelated numeric assignments`() {
        val source = """<script>
            var unitCount = 12;
            activity = new TaskActivity("1","王","C1","课程","R1","A","01");
            index = 0 * unitCount + 0;
            table0.marshalTable(2,1,20);
            menu = 6 * unitCount + 5;
        </script>"""
        assertEquals(listOf(JwCourse("课程", "A", "王", 1, 1, 1, 1, 1)),
            JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `shuwei_old confidence follows renamed script bindings`() {
        val source = """<script>
            let 当前活动 = null;
            let 任课教师 = [{"name":"王"}];
            当前活动 = new TaskActivity("id","课程2","roomId","A","000001");
            时段 = 0 * 每日节数 + 0;
            课表.marshalTable();
        </script>"""
        val parser = JwPostgradSchoolParser(source, "shuwei_old")
        assertEquals(100, parser.confidence())
        assertEquals(listOf(JwCourse("课程2", "A", "王", 1, 1, 1, 5, 5)), parser.generateCourseList())
    }

    @Test fun `ASCII suffix cannot borrow the value of a longer Chinese binding`() {
        val script = JwScriptSource("const 旧title='其他学期';")
        assertEquals(null, script.resolveString("title", script.text.length, script.text.length))
        assertEquals("其他学期", script.resolveString("旧title", script.text.length, script.text.length))
    }
}

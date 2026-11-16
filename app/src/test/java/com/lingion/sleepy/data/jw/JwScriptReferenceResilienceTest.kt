package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwScriptReferenceResilienceTest {
    private val expected = listOf(JwCourse("实验,甲", "A101", "甲老师/乙老师", 3, 12, 13, 1, 1))
    private fun page(declarations: String, teachers: String = "\"甲老师/乙老师\"") = """<script>
        $declarations
        event = new TaskActivity("T",$teachers,"C",courseTitle,"R","A101","01");
        slot = 2 * slots + 11; timetable.activities[slot].push(event);
        slot = 2 * slots + 12; timetable.activities[slot].push(event);
        timetable.marshalTable(1,1,2);
        </script>"""

    @Test fun `comma declarations preserve scalar array and quoted comma values`() {
        val html = page("const slots=13, courseTitle=\"实验,甲\", menu=[1,2];")
        assertEquals(expected, JwClassicEamsParser(html).generateCourseList())
    }

    @Test fun `line breaks in assignments preserve initializer expressions`() {
        val html = page("const slots =\n 13; const courseTitle =\n \"实验,甲\";")
            .replace("2 * slots + 11", "2 *\n slots +\n 11")
        assertEquals(expected, JwClassicEamsParser(html).generateCourseList())
    }

    @Test fun `teacher join resolves referenced array rather than nearest name array`() {
        val html = page("""const slots=13; const courseTitle="实验,甲";
            const staff=[{name:"甲老师"},{name:"乙老师"}];
            const staffNames=[];
            for (let k=0;k<staff.length;k++) { staffNames.push(staff[k].name); }
            const menu=[{name:"无关菜单"}];""", "staffNames.join(',')")
        assertEquals(expected, JwClassicEamsParser(html).generateCourseList())
    }

    @Test fun `literal name array and map projection both resolve actual teacher bindings`() {
        for (expression in listOf("staffNames.join(',')", "staff.map(p => p.name).join(',')")) {
            val html = page("""const slots=13; const courseTitle="实验,甲";
                const staff=[{name:"甲老师"},{name:"乙老师"}];
                const staffNames=["甲老师","乙老师"];
                const menu=[{name:"无关菜单"}];""", expression)
            assertEquals(expression, expected, JwClassicEamsParser(html).generateCourseList())
        }
    }

    @Test fun `simc renamed table and marshal spacing preserve week origin and clocks`() {
        val html = """<script>const slots=14;
            event=new TaskActivity("T","甲老师","C","实验","R","A101","10");
            slot=0*slots+0; timetable.activities[slot].push(event);
            timetable /* receiver */ . marshalTable ( 1, 1, 2 )
            </script>"""
        assertEquals(listOf(JwCourse("实验","A101","甲老师",1,1,1,1,1,startTime="08:30",endTime="09:15")),
            JwSimcParser(html).generateCourseList())
    }

    @Test fun `simc quoted and commented marshal calls cannot select a week origin`() {
        val task = """const slots=14;
            event=new TaskActivity("T","甲老师","C","实验","R","A101","10");
            slot=0*slots+0; timetable.activities[slot].push(event);"""
        for (noise in listOf("/*table.marshalTable(1,1,2);*/", "const example='table.marshalTable(1,1,2);';")) {
            assertTrue(JwSimcParser("<script>$task $noise</script>").generateCourseList().isEmpty())
        }
    }

    @Test fun `unrelated marshal call cannot override the receiver containing activities`() {
        val html = """<script>const slots=14;
            event=new TaskActivity("T","甲老师","C","实验","R","A101","10");
            slot=0*slots+0; actual.activities[slot].push(event);
            table.marshalTable(2,1,2); actual.marshalTable(1,1,2);</script>"""
        assertEquals(listOf(JwCourse("实验","A101","甲老师",1,1,1,1,1,startTime="08:30",endTime="09:15")),
            JwSimcParser(html).generateCourseList())
    }
}

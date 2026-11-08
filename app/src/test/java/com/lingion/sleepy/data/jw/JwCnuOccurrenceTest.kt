package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

/** Synthetic curriculum-item DOM derived from o00O0O, including negative and multi-course cases. */
class JwCnuOccurrenceTest {
    private fun course(name: String, timing: String, teacher: String = "张老师", room: String = "A301") =
        """<div class="curriculum-item"><div><span>$name</span><span>查看更多</span></div><div>$teacher</div><div>$timing</div><div>$room</div></div>"""

    private fun row(gutters: Int, day: Int, content: String): String = "<tr>" +
        (0 until gutters).joinToString("") { "<td>标签</td>" } +
        (1..7).joinToString("") { "<td>${if (it == day) content else ""}</td>" } + "</tr>"

    private fun table(rows: String) = "<table><tbody>$rows</tbody></table>"

    private fun parsed(html: String) = JwParserRegistry.parserFor("cnu", html).generateCourseList()

    private fun occurrences(courses: List<JwCourse>) = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.map {
            "${c.name}|${c.teacher}|${c.room}|${c.day}|${c.startNode}|${c.endNode}|$it"
        }
    }.sorted()

    @Test fun `eight and nine column rows retain weekdays and isolated course fields`() {
        val html = table(row(1, 1, course("数学I", "1-3,7-8周[1-2节]")) +
            row(2, 7, course("数学II", "2-4周[5-6节]", "李老师", "B202")))
        val expected = listOf(
            JwCourse("数学I", "A301", "张老师", 1, 1, 2, 1, 3),
            JwCourse("数学I", "A301", "张老师", 1, 1, 2, 7, 8),
            JwCourse("数学II", "B202", "李老师", 7, 5, 6, 2, 4),
        )
        assertEquals(expected, parsed(html))
        assertEquals(expected, JwParserRegistry.selectBest(html, "cnu").first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(html, "cnu").first)
    }

    @Test fun `multiple courses in one cell never concatenate names or inherit teachers`() {
        val html = table(row(1, 3, course("数据库", "2-4周[3-4节]") + course("编译原理", "6-7周[3-4节]", "王老师", "C303")))
        assertEquals(listOf(
            JwCourse("数据库", "A301", "张老师", 3, 3, 4, 2, 4),
            JwCourse("编译原理", "C303", "王老师", 3, 3, 4, 6, 7),
        ), parsed(html))
    }

    @Test fun `sparse periods never become a continuous class and parity is retained`() {
        val html = table(row(1, 2, course("实验", "1-7单周[1,3-4节]")))
        val expected = listOf(1, 3, 5, 7).flatMap { week ->
            listOf("实验|张老师|A301|2|1|1|$week", "实验|张老师|A301|2|3|4|$week")
        }.sorted()
        assertEquals(expected, occurrences(parsed(html)))
    }

    @Test fun `invalid timing and label columns cannot create default courses`() {
        for (timing in listOf("周[1-2节]", "0-8周[1-2节]", "1-8周[0-2节]", "1-8周[5-3节]", "-1-8周[1-2节]", "1-8周[1.5节]", "1-8周[2147483648节]", "1-8,待定周[1-2节]")) {
            assertTrue(timing, parsed(table(row(1, 1, course("错误课", timing)))).isEmpty())
        }
        val labels = "<tr><td>${course("标签课", "1-8周[1-2节]")}</td>" + "<td></td>".repeat(7) + "</tr>"
        assertTrue(parsed(table(labels)).isEmpty())
        val wrongWidth = "<tr><td>标签</td><td>${course("列不完整", "1-8周[1-2节]")}</td></tr>"
        assertTrue(parsed(table(wrongWidth)).isEmpty())
    }

    @Test fun `explicit high section survives without an invented thirty section cap`() {
        val html = table(row(1, 1, course("高级实验", "1-8周[31节]")))
        assertEquals(listOf(JwCourse("高级实验", "A301", "张老师", 1, 31, 31, 1, 8)), parsed(html))
    }

    @Test fun `menu tbody cannot hide the real timetable and duplicate blocks do not duplicate occurrences`() {
        val item = course("课程甲", "1-4周[7-8节]")
        val html = table("<tr><td>菜单</td></tr>") + table(row(1, 4, item + item))
        assertEquals(listOf(JwCourse("课程甲", "A301", "张老师", 4, 7, 8, 1, 4)), parsed(html))
    }

    @Test fun `all fields survive draft and entity mapping`() {
        val expected = listOf(JwCourse("机器学习2", "D404", "赵老师", 6, 9, 10, 3, 6))
        val courses = parsed(table(row(2, 6, course("机器学习2", "3-6周[9-10节]", "赵老师", "D404"))))
        val draft = JwImportDraftSnapshot(JwSchoolInfo("C", "长春师范大学", type = "cnu"), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(expected, restored.courses)
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4").single()
        assertEquals(listOf("机器学习2", "D404", "赵老师"), listOf(entity.courseName, entity.room, entity.teacher))
        assertEquals(listOf(6, 9, 2, 3, 6, 0), listOf(entity.day, entity.startNode, entity.step, entity.startWeek, entity.endWeek, entity.type))
    }

    @Test fun `declared source catalog uses the native protocol without changing verification status`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val school = schools.single { it.name == "长春师范大学" }
        assertEquals("cnu", school.type)
        assertTrue(school.isUnverified)
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwNyistJxauOccurrenceTest {
    private fun nyist(time: String, room: String = "A", teacher: String = "张") =
        "<div class='course-content'><div class='name'>网络</div><div class='course-item-list'>" +
            "<div class='teacher'><p class='content'>$teacher</p></div>" +
            "<div class='address'><p class='content'>$room</p></div>" +
            "<div class='time'><p class='content'>$time</p></div></div></div>"
    private fun lesson(name: String = "农业", weeks: String = "1,3-4(周)", teacher: String = "李") =
        "<dl><a>$name</a><dd>$teacher</dd><dd>$weeks</dd><b>B</b></dl>"
    private fun jxau(cells: List<String>, day: String = "星期三") =
        "<table id='KebiaoTable1'><tr><td>说明</td></tr><tr><td>表头</td></tr>" +
            "<tr><td class='left1'>$day</td>" + cells.joinToString("") { "<td>$it</td>" } +
            "</tr>" + "<tr><td class='left1'>星期一</td></tr>".repeat(6) + "</table>"
    private fun occurrences(courses: List<JwCourse>) = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }.flatMap { week ->
            (course.startNode..course.endNode).map { node ->
                "${course.name}|${course.teacher}|${course.room}|${course.day}|$node|$week|${course.startTime}|${course.endTime}"
            }
        }
    }.sorted()

    @Test fun `NYIST independently preserves mixed parity weeks and actual clock`() {
        val courses = JwNyistParser(nyist("第[1-5]周(单) 第[6-8]周(双) 周日 5-6节 14:30~16:00")).generateCourseList()
        assertEquals(listOf(1,3,5,6,8).flatMap { week -> listOf(5,6).map {
            "网络|张|A|7|$it|$week|14:30|16:00"
        } }.sorted(), occurrences(courses))
    }
    @Test fun `NYIST missing time never manufactures the source nineteenth week fallback`() {
        assertTrue(JwNyistParser(nyist("")).generateCourseList().isEmpty())
    }
    @Test fun `NYIST separate arrangements retain their teacher and room`() {
        val courses = JwNyistParser(nyist("第[3]周 周一 1-2节 08:00~09:30") +
            nyist("第[6]周 周四 9-10节 19:00~20:30", "实验室", "王")).generateCourseList()
        assertEquals(listOf(JwCourse("网络", "A", "张", 1,1,2,3,3,startTime="08:00",endTime="09:30"),
            JwCourse("网络", "实验室", "王", 4,9,10,6,6,startTime="19:00",endTime="20:30")), courses)
    }
    @Test fun `NYIST rejects unknown weeks coordinates and invalid clocks`() {
        for (time in listOf("第[0-3]周 周一 1-2节 08:00~09:30", "第[1.5]周 周一 1-2节 08:00~09:30",
            "第[1]周 周八 1-2节 08:00~09:30", "第[1]周 周一 3-1节 08:00~09:30",
            "第[1]周 周一 1-2节 18:00~09:30", "待定 周一 1-2节 08:00~09:30",
            "第[[1]周 周一 1-2节 08:00~09:30")) {
            assertTrue(time, JwNyistParser(nyist(time)).generateCourseList().isEmpty())
        }
    }
    @Test fun `JXAU empty cells advance nodes while separator columns do not`() {
        val html = jxau(listOf("", lesson(), "", "", lesson("分隔假课"), lesson("晚课", "8"),
            lesson("第二分隔假课"), lesson("末课", "9")))
        val courses = JwJxauParser(html).generateCourseList()
        val expected = listOf(1,3,4).flatMap { week -> listOf(3,4).map { "农业|李|B|3|$it|$week||" } } +
            listOf(9,10).map { "晚课|李|B|3|$it|8||" } + listOf(11,12).map { "末课|李|B|3|$it|9||" }
        assertEquals(expected.sorted(), occurrences(courses))
    }
    @Test fun `JXAU multiple lessons in one cell retain sparse weeks and fields`() {
        val html = jxau(listOf(lesson(weeks="2,5",teacher="甲") + lesson("实验", "7-8", "乙")))
        val courses = JwJxauParser(html).generateCourseList()
        assertEquals(listOf(2,5).flatMap { week -> listOf(1,2).map { "农业|甲|B|3|$it|$week||" } }.plus(
            listOf(7,8).flatMap { week -> listOf(1,2).map { "实验|乙|B|3|$it|$week||" } }).sorted(), occurrences(courses))
    }
    @Test fun `JXAU unknown weeks and merged positions are rejected`() {
        assertTrue(JwJxauParser(jxau(listOf(lesson(weeks="1,待定")))).generateCourseList().isEmpty())
        assertTrue(JwJxauParser(jxau(listOf(lesson())).replace("<td>","<td colspan='2'>")).generateCourseList().isEmpty())
    }
    @Test fun `both legacy protocols have explicit routes without cross family fallback`() {
        for ((type, source) in listOf("nyist" to nyist("第[3]周 周一 1-2节 08:00~09:30"),
            "jxau" to jxau(listOf(lesson(weeks="3"))))) {
            assertTrue(type, JwParserRegistry.supports(type))
            assertFalse(type, JwParserRegistry.selectBest(source,type).first.isEmpty())
            assertTrue(type, JwParserRegistry.selectBest("[{\"courseName\":\"无关\",\"weeks\":[1]}]",type).first.isEmpty())
        }
    }
}

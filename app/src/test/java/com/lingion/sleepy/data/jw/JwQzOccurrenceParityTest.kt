package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic protocol cases derived from the local 6.6.2 QZ family, not account captures. */
class JwQzOccurrenceParityTest {
    private fun block(time: String, name: String = "离散数学") = """
        $name<br><font title="教师">陈老师</font><br>
        <font title="上课地点">理科楼305</font><br>$time
    """.trimIndent()

    private fun table(content: String, sundayFirst: Boolean = false, nested: Boolean = false): String {
        val days = if (sundayFirst) listOf("日", "一", "二", "三", "四", "五", "六")
            else listOf("一", "二", "三", "四", "五", "六", "日")
        val course = "<div class='kbcontent'>$content</div>"
        return "<table id='kbtable'><tr><th>节次</th>" +
            days.joinToString("") { "<th>星期$it</th>" } + "</tr><tr><td>上午</td><td>" +
            (if (nested) "<div><div>$course</div></div>" else course) +
            "</td>" + "<td></td>".repeat(6) + "</tr></table>"
    }

    private fun expected(day: Int = 1, start: Int = 5, end: Int = 6, from: Int = 2, to: Int = 8, type: Int = 0,
                         name: String = "离散数学") =
        JwCourse(name, "理科楼305", "陈老师", day, start, end, from, to, type)

    private fun assertRoutes(html: String, expected: List<JwCourse>) {
        assertEquals("base", expected, JwQzParser(html).generateCourseList())
        assertEquals("with node", expected, JwQzWithNodeParser(html).generateCourseList())
        for (type in listOf(JwProtocol.TYPE_QZ, JwProtocol.TYPE_QZ_WITH_NODE)) {
            assertEquals("declared $type", expected, JwParserRegistry.selectBest(html, type).first)
            assertEquals("screen $type", expected, JwImportViewModel.parseHtmlStatic(html, type).first)
        }
        assertEquals("automatic", expected, JwParserRegistry.selectBest(html).first)
    }

    @Test fun `Sunday first grid excludes the td rail and uses explicit sections`() {
        val html = table(block("<font title='周次(节次)'>2-8(周)[5-6节]</font>"), sundayFirst = true)
        assertRoutes(html, listOf(expected(day = 7)))
    }

    @Test fun `independent fields decode packed sections without joining week digits`() {
        val html = table(block("<span title='周次'>2-8周</span><br><span title='节次'>0102节</span>"))
        assertRoutes(html, listOf(expected(start = 1, end = 2)))
    }

    @Test fun `local parity does not leak into the later individual week`() {
        val html = table(block("<font title='周次(节次)'>1-5(单),8(周)[5-6节]</font>"))
        assertRoutes(html, listOf(expected(from = 1, to = 5, type = 1), expected(from = 8, to = 8)))
    }

    @Test fun `all documented week separators preserve holes`() {
        val html = table(block("<font title='周次(节次)'>2、4，6\\8(周) [5-6节]</font>"))
        assertRoutes(html, listOf(2, 4, 6, 8).map { expected(from = it, to = it) })
    }

    @Test fun `wrapper divs do not duplicate courses and separator retains next name`() {
        val first = block("<font title='周次(节次)'>2-8(周)[5-6节]</font>")
        val second = block("<font title='周次(节次)'>10-12(周)[9-10节]</font>", "实验课")
        val html = table("$first<br>-----$second", nested = true)
        assertRoutes(html, listOf(expected(), expected(start = 9, end = 10, from = 10, to = 12, name = "实验课")))
    }

    @Test fun `adjacent courses without dashes keep separate metadata`() {
        val first = block("<font title='周次(节次)'>2-8(周)[5-6节]</font>")
        val second = block("<font title='周次(节次)'>10-12(周)[9-10节]</font>", "实验课")
        assertRoutes(table("$first<br>$second"),
            listOf(expected(), expected(start = 9, end = 10, from = 10, to = 12, name = "实验课")))
    }

    @Test fun `enrollment field second line can carry the schedule`() {
        val html = table(block("<font title='选课人数'>40<br>2-8(周)[5-6节]</font>"))
        assertRoutes(html, listOf(expected()))
    }

    @Test fun `missing or malformed weeks never manufacture week one`() {
        for (time in listOf("", "<font title='周次(节次)'>待定[5-6节]</font>",
            "<font title='周次(节次)'>8-2(周)[5-6节]</font>")) {
            val html = table(block(time))
            assertEquals(emptyList<JwCourse>(), JwQzParser(html).generateCourseList())
            assertEquals(emptyList<JwCourse>(), JwQzWithNodeParser(html).generateCourseList())
            assertEquals("invalid weeks must not fall through to another family's guesses", emptyList<JwCourse>(),
                JwParserRegistry.selectBest(html).first)
        }
    }

    @Test fun `invalid explicit sections do not silently become the grid row`() {
        for (node in listOf("0-2", "6-5", "待定")) {
            val html = table(block("<font title='周次(节次)'>2-8(周)[$node 节]</font>"))
            assertEquals(emptyList<JwCourse>(), JwQzParser(html).generateCourseList())
            assertEquals(emptyList<JwCourse>(), JwQzWithNodeParser(html).generateCourseList())
            assertEquals("invalid sections must not fall through to another family's guesses", emptyList<JwCourse>(),
                JwParserRegistry.selectBest(html).first)
        }
    }

    @Test fun `br names are HTML decoded and empty grid rows keep their section positions`() {
        val html = table(block("<font title='周次(节次)'>2-8(周)</font>", "<b>信号 &amp; 系统</b>"))
            .replace("<tr><td>上午", "<tr>" + "<td></td>".repeat(7) + "</tr><tr><td>上午")
        val result = listOf(expected(start = 3, end = 4, name = "信号 & 系统"))
        assertEquals(result, JwQzBrParser(html).generateCourseList())
        assertEquals(result, JwQzParser(html).generateCourseList())
    }
}

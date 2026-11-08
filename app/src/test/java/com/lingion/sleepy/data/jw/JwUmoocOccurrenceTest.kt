package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic cases for the pinned UMOOC DOM; these are not authenticated page captures. */
class JwUmoocOccurrenceTest {
    private fun block(weeks: String, name: String = "离散数学", room: String = "A301", teacher: String = "陈老师") =
        "&lt;&lt;$name&gt;&gt;<br>$room<br>$teacher<br>32学时<br>$weeks<br>"

    private fun page(content: String, id: String = "3-5", header: String = "下午") =
        """<table id="timetable"><tr class="infolist_hr_common"><th>$header</th><td id="$id">$content</td></tr></table>"""

    private fun occurrences(courses: List<JwCourse>): List<String> = courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.map {
            listOf(c.name, c.teacher, c.room, c.day, c.startNode, c.endNode, it).joinToString("|")
        }
    }.sorted()

    private fun expected(weeks: List<Int>, name: String = "离散数学", room: String = "A301", teacher: String = "陈老师", node: Int = 5) =
        weeks.map { "$name|$teacher|$room|3|$node|$node|$it" }.sorted()

    private fun assertRoutes(html: String, expected: List<String>) {
        assertEquals(expected, occurrences(JwUmoocParser(html).generateCourseList()))
        assertEquals(expected, occurrences(JwParserRegistry.selectBest(html, "umooc").first))
        assertEquals(expected, occurrences(JwImportViewModel.parseHtmlStatic(html, "umooc").first))
    }

    @Test fun `multiple excluded weeks are subtracted before comma tokenization`() {
        for (text in listOf("1-10周（除3,5,8周）", "1-10周(除第3、5、8周)")) {
            assertRoutes(page(block(text)), expected(listOf(1, 2, 4, 6, 7, 9, 10)))
        }
    }

    @Test fun `excluded ranges preserve odd week parity`() {
        assertRoutes(page(block("1-11单周(除3-7周)")), expected(listOf(1, 9, 11)))
    }

    @Test fun `sparse included weeks and their exclusions remain sparse`() {
        assertRoutes(page(block("1,3,7,11周(除3,11周)")), expected(listOf(1, 7)))
    }

    @Test fun `explicit all weeks uses the verified source default independently of previous blocks`() {
        val html = page(block("7-9周", "短课") + block("全周(除2,4周)") + block("单周", "单周课"))
        assertRoutes(html, (expected(listOf(7, 8, 9), "短课") +
            expected((1..18).filter { it != 2 && it != 4 }) +
            expected((1..18).filter { it % 2 == 1 }, "单周课")).sorted())
    }

    @Test fun `malformed and missing week fields do not become partial valid ranges`() {
        for (text in listOf("1-8周(除x周)", "1-8周(除0周)", "1-8周(除5-3周)", "-1-8周", "1-8周,错误", "0-8周", "1-999999999周", "待定")) {
            assertRoutes(page(block(text)), emptyList())
        }
    }

    @Test fun `overlapping blocks are deduplicated by occurrence while room and teacher changes remain`() {
        assertRoutes(page(block("1-5周") + block("3-7周") + block("3周", room = "B202") + block("3周", teacher = "李老师")),
            (expected((1..7).toList()) + expected(listOf(3), room = "B202") + expected(listOf(3), teacher = "李老师")).sorted())
    }

    @Test fun `numbered headers override cell period while invalid coordinates are rejected`() {
        assertRoutes(page(block("2周"), header = "第9节"), expected(listOf(2), node = 9))
        assertRoutes(page(block("2周"), id = "3-31"), expected(listOf(2), node = 31))
        assertRoutes(page(block("2周"), header = "第31节"), expected(listOf(2), node = 31))
        for (id in listOf("0-5", "8-5", "3-0", "3-367", "3-5-7", "3-1.5")) {
            assertRoutes(page(block("2周"), id = id), emptyList())
        }
        assertRoutes(page(block("2周"), header = "第367节"), emptyList())
    }

    @Test fun `excluded occurrences survive draft and final entity mapping`() {
        val courses = JwUmoocParser(page(block("1-9周(除3-6周)"), id = "3-31")).generateCourseList()
        val draft = JwImportDraftSnapshot(JwSchoolInfo("U", "UMOOC", type = "umooc"), courses, emptyList())
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!
        assertEquals(expected(listOf(1, 2, 7, 8, 9), node = 31), occurrences(restored.courses))
        val entities = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 1L, "#FF6750A4")
        assertEquals(expected(listOf(1, 2, 7, 8, 9), node = 31), entities.flatMap { c ->
            (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == c.type % 2 }.map {
                "${c.courseName}|${c.teacher}|${c.room}|${c.day}|${c.startNode}|${c.startNode + c.step - 1}|$it"
            }
        }.sorted())
    }

    @Test fun `invalid native weeks never fall back to unrelated generic row fields`() {
        val html = page(block("1-8周(除x周)")).replace("<tr ",
            "<tr data-course='伪课程' data-day='1' data-node='1' data-weeks='1-16' ")
            .replace("</tr>", "<td>附加字段</td></tr>")
        assertRoutes(html, emptyList())
    }
}

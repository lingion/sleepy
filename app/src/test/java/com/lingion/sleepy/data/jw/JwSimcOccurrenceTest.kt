package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Synthetic inputs derived from 6.6.2 o0O00oO0 and its DEX instructions. */
class JwSimcOccurrenceTest {
    private fun task(bitmap: String, indices: List<Pair<Int, Int>>, name: String = "机电(1001)(A班)", teacher: String = "张老师") =
        "activity = new TaskActivity(\"t\",\"$teacher\",\"c\",\"$name\",\"r\",\"实训楼301\",\"$bitmap\");\n" +
            indices.joinToString("\n") { (day, node) -> "index = $day*unitCount+$node; table.activities[index].push(activity);" }

    private fun page(tasks: String, marshal: String = "table.marshalTable(5,2,8);", unitCount: Boolean = true) = """
        <div class="grid"><table><tr><th>代码</th><th>类别</th><th>课程</th><th>学分</th></tr>
        <tr><td>1001</td><td>必修</td><td>机电</td><td>2.5</td></tr></table></div>
        <script>${if (unitCount) "var unitCount = 14;" else ""}$tasks $marshal</script>
    """.trimIndent()

    private fun assertRoutes(html: String, expected: List<JwCourse>) {
        assertEquals("parser", expected, JwParserRegistry.parserFor("simc", html).generateCourseList())
        assertEquals("declared", expected, JwParserRegistry.selectBest(html, "simc").first)
        assertEquals("screen", expected, JwImportViewModel.parseHtmlStatic(html, "simc").first)
    }

    @Test fun `offset repeated bitmap and remapped sections preserve each occurrence`() {
        val html = page(task("010101", listOf(0 to 0, 0 to 1, 0 to 3, 0 to 4, 0 to 8, 0 to 13, 2 to 6)))
        val expected = buildList {
            for (week in listOf(2, 4, 6, 8)) {
                for ((start, end) in listOf(1 to 2, 4 to 4, 5 to 5, 9 to 10)) {
                    val time = when (start) {
                        1 -> "08:30" to "10:00"
                        4 -> "11:00" to "11:45"
                        5 -> "13:00" to "13:45"
                        else -> "16:15" to "17:45"
                    }
                    add(JwCourse("机电", "实训楼301", "张老师", 1, start, end, week, week,
                        startTime = time.first, endTime = time.second, credit = 2.5f))
                }
            }
            for (week in listOf(2, 4, 6, 8))
                add(JwCourse("机电", "实训楼301", "张老师", 3, 7, 7, week, week,
                    startTime = "14:45", endTime = "15:30", credit = 2.5f))
        }
        assertRoutes(html, expected)
    }

    @Test fun `four extended session types preserve actual times and lunch boundary`() {
        val cases = listOf(
            Triple(9, listOf(1 to 4, 5 to 5), "07:00" to "13:30"),
            Triple(10, listOf(6 to 10), "14:00" to "20:30"),
            Triple(11, listOf(1 to 4, 5 to 8), "08:15" to "15:30"),
            Triple(12, listOf(9 to 10), "16:00" to "22:00"),
        )
        for ((raw, ranges, time) in cases) {
            val html = page(task("10", listOf(6 to raw)), "table.marshalTable(1,1,2);", unitCount = false)
            val expected = ranges.map { (first, last) ->
                JwCourse("机电", "实训楼301", "张老师", 7, first, last, 1, 1,
                    startTime = time.first, endTime = time.second, credit = 2.5f)
            }
            assertRoutes(html, expected)
        }
    }

    @Test fun `overlapping blocks merge only matching weeks teachers and places`() {
        val html = page(task("101", listOf(0 to 0)) + task("011", listOf(0 to 1)) +
            task("001", listOf(0 to 2), teacher = "李老师"), "table.marshalTable(1,1,3);")
        assertRoutes(html, listOf(
            JwCourse("机电", "实训楼301", "张老师", 1, 1, 1, 1, 1, startTime = "08:30", endTime = "09:15", credit = 2.5f),
            JwCourse("机电", "实训楼301", "张老师", 1, 2, 2, 2, 2, startTime = "09:15", endTime = "10:00", credit = 2.5f),
            JwCourse("机电", "实训楼301", "张老师", 1, 1, 2, 3, 3, startTime = "08:30", endTime = "10:00", credit = 2.5f),
            JwCourse("机电", "实训楼301", "李老师", 1, 3, 3, 3, 3, startTime = "10:15", endTime = "11:00", credit = 2.5f),
        ))
    }

    @Test fun `missing or invalid marshal and bitmap never fall back to classic semantics`() {
        for (html in listOf(
            page(task("010101", listOf(0 to 0)), ""),
            page(task("010101", listOf(0 to 0)), "table.marshalTable(0,1,6);"),
            page(task("010101", listOf(0 to 0)), "table.marshalTable(1,9,3);"),
            page(task("01x101", listOf(0 to 0))),
            page(task("010101", listOf(0 to 0)), "table.marshalTable(12,2,8);"),
        )) assertRoutes(html, emptyList())
    }

    @Test fun `simc does not claim ordinary TaskActivity pages automatically`() {
        assertFalse(JwParserRegistry.allCandidates(page(task("010101", listOf(0 to 0)))).any { it.first == "simc" })
    }

    @Test fun `mixed ordinary and extended slots never emit half a clock range`() {
        val html = page(task("10", listOf(0 to 13, 0 to 9)), "table.marshalTable(1,1,2);")
        // The vendor only assigns an extended time when the mapped slot is singular.
        // A mixed 10..11 range has no complete clock definition in the ten-period timetable.
        assertRoutes(html, listOf(JwCourse("机电", "实训楼301", "张老师", 1, 10, 11, 1, 1, credit = 2.5f)))
    }

    @Test fun `school selection and draft restore retain SIMC times and credits`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val html = page(task("10", listOf(2 to 4, 2 to 5)), "table.marshalTable(1,1,2);")
        for (name in listOf("上海工程技术大学高等职业技术学院", "上海市高级技工学校")) {
            val school = schools.single { it.name == name }
            assertEquals(name, "simc", school.type)
            val courses = JwImportViewModel.parseHtmlStatic(html, school.type!!).first
            val snapshot = JwImportDraftSnapshot(school = school, courses = courses, periods = emptyList())
            val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))!!
            val entities = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 7L, "#FF6750A4")
            val entity = entities.single()
            assertEquals("机电", entity.courseName)
            assertEquals("张老师", entity.teacher)
            assertEquals("实训楼301", entity.room)
            assertEquals(3, entity.day)
            assertEquals(5, entity.startNode)
            assertEquals(2, entity.step)
            assertEquals(1, entity.startWeek)
            assertEquals(1, entity.endWeek)
            assertEquals("13:00", entity.startTime)
            assertEquals("14:30", entity.endTime)
            assertEquals(2.5f, entity.credit)
            assertEquals(true, entity.ownTime)
        }
    }
}

package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwNewZfParser 单测 — T4 修复回归基线（整体替换 v1.0.29 旧 3 用例）。
 *
 * 覆盖范围:
 *   - JSON 主流 (kbList) 4 类节次格式: 范围串 / 补零 / 多段 / 缺字段
 *   - 周次: 范围 / 单双周 / 第字前缀 / 花括号 / bitmap / 缺省
 *   - 字段优先级: room=cdmc, teacher=xm, day=xqj/xqjmc
 *   - CF kbxx 防御: teaxms/jxcdmcs/jcdm2/zcs 字段白名单
 *   - 移动端深层包装穿透: {Msg,code,data:[{kbList}]}
 *   - HTML 三种变体: table1+festival / kbgrid_table_0 / kblist_table
 *   - 边界: 空学期 / 缺字段 / 登录页
 *
 * 测试数据来自 /tmp/jw_fixtures/zf-new-kblist 与 /tmp/jw_fixtures/zf-new-html
 * (每对 fixture 有 .expected.json 可对比)
 *
 * 纯 JVM, 不依赖 Android。
 */
class JwNewZfParserTest {

    // ── 工具: 读取 classpath 资源 ──────────────────────────
    private fun readFixture(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("zf-new/$name")
            ?: error("Fixture not found: zf-new/$name (请按 5.1 拷贝)")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun assertCourses(expectedName: String, actual: List<JwCourse>) {
        val expectedJson = readFixture("$expectedName.expected.json")
        val expected = parseExpected(expectedJson)
        assertEquals(
            "fixture=$expectedName 课程数不符",
            expected.size, actual.size
        )
        for ((i, e) in expected.withIndex()) {
            val a = actual[i]
            assertEquals("$expectedName[$i].name", e.name, a.name)
            assertEquals("$expectedName[$i].day", e.day, a.day)
            assertEquals("$expectedName[$i].startNode", e.startNode, a.startNode)
            assertEquals("$expectedName[$i].endNode", e.endNode, a.endNode)
            assertEquals("$expectedName[$i].startWeek", e.startWeek, a.startWeek)
            assertEquals("$expectedName[$i].endWeek", e.endWeek, a.endWeek)
            assertEquals("$expectedName[$i].type", e.type, a.type)
            assertEquals("$expectedName[$i].teacher", e.teacher, a.teacher)
            assertEquals("$expectedName[$i].room", e.room, a.room)
        }
    }

    private data class ExpectedCourse(
        val name: String, val day: Int, val startNode: Int, val endNode: Int,
        val startWeek: Int, val endWeek: Int, val type: Int,
        val teacher: String, val room: String
    )

    private fun parseExpected(json: String): List<ExpectedCourse> {
        // 简化解析: 用 regex 抽 "courses" 数组内的对象, 避免引入额外 JSON 库
        val coursesStart = json.indexOf("\"courses\"")
        val body = if (coursesStart >= 0) json.substring(coursesStart) else json
        val courseBlocks = Regex("""\{[^{}]*"name"\s*:\s*"[^"]*"[^{}]*\}""").findAll(body)
        return courseBlocks.map { block ->
            fun fld(key: String): String =
                Regex(""""$key"\s*:\s*"?([^",}\n]+?)"?[,}\s]""").find(block.value)?.groupValues?.get(1).orEmpty()
            ExpectedCourse(
                name = fld("name"),
                day = fld("day").toIntOrNull() ?: 0,
                startNode = fld("startNode").toIntOrNull() ?: 0,
                endNode = fld("endNode").toIntOrNull() ?: 0,
                startWeek = fld("startWeek").toIntOrNull() ?: 0,
                endWeek = fld("endWeek").toIntOrNull() ?: 0,
                type = fld("type").toIntOrNull() ?: 0,
                teacher = fld("teacher"),
                room = fld("room")
            )
        }.toList()
    }

    // ════════════════════════════════════════════════════════
    // JSON 路径 - 4 类节次格式
    // ════════════════════════════════════════════════════════

    @Test
    fun `json kblist range sections multi-segment and padded`() {
        val src = readFixture("kblist_range_sections.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_range_sections", courses)
    }

    @Test
    fun `json kblist single double weeks strip leading di-prefix`() {
        val src = readFixture("kblist_single_double_weeks.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_single_double_weeks", courses)
    }

    @Test
    fun `json kblist bitmap and extremes parses 32bit`() {
        val src = readFixture("kblist_bitmap_and_extremes.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_bitmap_and_extremes", courses)
    }

    @Test
    fun `json kblist missing fields drops course without jc`() {
        val src = readFixture("kblist_missing_fields.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_missing_fields", courses)
    }

    @Test
    fun `json kblist sjklist xsxx ignored kbList parsed`() {
        val src = readFixture("kblist_with_sjklist_and_empty.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_with_sjklist_and_empty", courses)
    }

    @Test
    fun `json kblist empty semester returns empty list`() {
        val src = readFixture("kblist_no_courses_this_semester.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("空学期应返回空列表", courses.isEmpty())
    }

    // ════════════════════════════════════════════════════════
    // JSON 路径 - 移动端深层包装穿透
    // ════════════════════════════════════════════════════════

    @Test
    fun `json mobile deeply wrapped data-kbList penetrates wrapper`() {
        val src = readFixture("kblist_mobile_deeply_wrapped.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_mobile_deeply_wrapped", courses)
    }

    // ════════════════════════════════════════════════════════
    // JSON 路径 - CF (青果) kbxx 防御
    // ════════════════════════════════════════════════════════

    @Test
    fun `cf-kbxx inline script with teaxms jxcdmcs is skipped silently`() {
        val src = readFixture("cf_typical_two_courses.html")
        val courses = JwNewZfParser(src).generateCourseList()
        // 关键断言: CF 防御使 JwNewZfParser 返回空, 不产脏数据
        assertTrue(
            "CF kbxx 必须被 JwNewZfParser 静默跳过 (teaxms/jxcdmcs/jcdm2/zcs 字段防御), " +
                "实际产出 ${courses.size} 门课: $courses",
            courses.isEmpty()
        )
    }

    // ════════════════════════════════════════════════════════
    // HTML 路径 - 三种新正方实证变体
    // ════════════════════════════════════════════════════════

    @Test
    fun `html table1 festival view upstream NewZFParser structure`() {
        val src = readFixture("table1_festival_view.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("table1_festival_view", courses)
    }

    @Test
    fun `html kbgrid_table_0 grid view shiguang structure`() {
        val src = readFixture("grid_dual_view.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("grid_dual_view", courses)
    }

    @Test
    fun `html kblist_table list view shiguang structure`() {
        val src = readFixture("list_dual_view.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("list_dual_view", courses)
    }

    // ════════════════════════════════════════════════════════
    // HTML 路径 - 边界
    // ════════════════════════════════════════════════════════

    @Test
    fun `html grid empty semester returns empty list`() {
        val src = readFixture("grid_empty_semester.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("grid 空学期应返回空列表", courses.isEmpty())
    }

    @Test
    fun `html grid missing fields drops courses without p_2 p_1`() {
        val src = readFixture("grid_missing_fields.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("grid_missing_fields", courses)
    }

    @Test
    fun `html login page returns empty list for protocol layer diag`() {
        val src = readFixture("login_page.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("登录页应返回空列表", courses.isEmpty())
    }

    @Test
    fun `html kblist login page returns empty list`() {
        val src = readFixture("kblist_empty_semester.html")
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("kblist 登录页应返回空列表", courses.isEmpty())
    }

    // ════════════════════════════════════════════════════════
    // Marker 收紧专项
    // ════════════════════════════════════════════════════════

    @Test
    fun `marker xskbcx does not match URL substring`() {
        val src = """
            <html><body>
            <script>var url = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151";</script>
            <table><tr><td>no data</td></tr></table>
            </body></html>
        """.trimIndent()
        val courses = JwNewZfParser(src).generateCourseList()
        assertTrue("URL 子串不应触发 JSON 解析", courses.isEmpty())
    }

    @Test
    fun `marker kbList takes priority over kbxx`() {
        val src = """
            {"kbList":[{"kcmc":"正课","xqj":1,"jc":"1-2","zcd":"1-16周","cdmc":"A101","xm":"张老师"}],
             "kbxx":[{"kcmc":"垃圾","xqj":9,"jc":"0","zcd":"","cdmc":"","xm":""}]}
        """.trimIndent()
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("正课", courses[0].name)
    }

    // ════════════════════════════════════════════════════════
    // 节次字符串解析专项 (parseSectionRanges)
    // ════════════════════════════════════════════════════════

    @Test
    fun `section range 1-2 yields single section`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
    }

    @Test
    fun `section range 3-4 6-7 multi-segment yields two courses per week-range`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":3,"jc":"3-4,6-7","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(2, courses.size)  // 拆 2 条 (3-4) + (6-7)
        assertEquals(3, courses[0].startNode); assertEquals(4, courses[0].endNode)
        assertEquals(6, courses[1].startNode); assertEquals(7, courses[1].endNode)
    }

    @Test
    fun `section padded 0102 yields 1-2`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"0102","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
    }

    @Test
    fun `section padded 0304 yields 3-4`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"0304","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].startNode)
        assertEquals(4, courses[0].endNode)
    }

    @Test
    fun `section padded 01021314 yields two courses 1-2 and 13-14`() {
        // 注: 规格边界表的 '01121314' 样例是笔误 — 01,12,13,14 分组只能得 (1,12),(13,14);
        // 意图语义(两段连堂 1-2 与 13-14)对应输入 01021314
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"01021314","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(1, courses[0].startNode); assertEquals(2, courses[0].endNode)
        assertEquals(13, courses[1].startNode); assertEquals(14, courses[1].endNode)
    }

    @Test
    fun `section single digit 5 yields 5-5`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"5","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(5, courses[0].startNode); assertEquals(5, courses[0].endNode)
    }

    // ════════════════════════════════════════════════════════
    // 周次字符串解析专项 (parseWeekStr 加固)
    // ════════════════════════════════════════════════════════

    @Test
    fun `week strip di-prefix`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"第1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses[0].startWeek); assertEquals(16, courses[0].endWeek)
    }

    @Test
    fun `week strip braces and di-prefix SHUFEZJ style`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"{第1-16周}","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses[0].startWeek); assertEquals(16, courses[0].endWeek)
    }

    @Test
    fun `week double-zhou suffix not confused with dan`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"第2-16周(双周)","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(2, courses[0].type)  // type=2 双周
    }

    @Test
    fun `week comma segment 1-8 11-16 with dan suffix`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"1-8,11-16周(双)","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(1, courses[0].startWeek); assertEquals(8, courses[0].endWeek)
        assertEquals(11, courses[1].startWeek); assertEquals(16, courses[1].endWeek)
    }

    // ════════════════════════════════════════════════════════
    // 字段优先级专项
    // ════════════════════════════════════════════════════════

    @Test
    fun `room prefers cdmc over jasmc`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"1-16周","jasmc":"甲上101","jxcd":"教学场地","cdmc":"真教室101","xm":"张老师"}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals("真教室101", courses[0].room)  // cdmc 胜出
    }

    @Test
    fun `teacher prefers xm over jsxm`() {
        val src = """{"kbList":[{"kcmc":"A","xqj":1,"jc":"1-2","zcd":"1-16周","cdmc":"","xm":"真教师","jsxm":"变体教师"}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals("真教师", courses[0].teacher)
    }

    @Test
    fun `day uses xqjmc text fallback when xqj missing`() {
        val src = """{"kbList":[{"kcmc":"A","jc":"1-2","zcd":"1-16周","cdmc":"","xm":"","xqjmc":"星期五"}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(5, courses[0].day)
    }

    @Test
    fun `day uses xqjmc zhou-tian alias for Sunday`() {
        val src = """{"kbList":[{"kcmc":"A","jc":"1-2","zcd":"1-16周","cdmc":"","xm":"","xqjmc":"周天"}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(7, courses[0].day)
    }
}

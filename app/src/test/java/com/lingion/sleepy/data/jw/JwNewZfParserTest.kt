package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser-specific regression cases for the new ZF format.
 *
 * The shared fixture matrix in JwParserFixtureTest owns the ordinary fixtures
 * and their nine-field oracle checks. This class keeps only cases that exercise
 * parser-specific branches or real captures that are not represented there.
 */
class JwNewZfParserTest {

    // ── 工具: 读取 classpath 资源 ──────────────────────────
    private fun readFixture(name: String): String = readResource("zf-new/$name")

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
            ?: error("Fixture not found: $path")
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

    /**
     * 浙大城市学院 HZCU (issue #90, WestGu 采集包 2026-10-01) 真实 kbList 报文:
     * zf_new zftal-ui-v5 裸 /kbcx/ 路径, xqj 为字符串数字, zcd 逗号分段非连续周,
     * 教师名已脱敏。16 课原始行 → 节次×周次全展开, 校验 HZCU 形态完整落库。
     */
    @Test
    fun `json kblist hzcu issue90 real capture full expansion`() {
        val src = readFixture("kblist_hzcu_issue90.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_hzcu_issue90", courses)
        assertEquals("HZCU 16 课原始行应展开为 29 条", 29, courses.size)
    }

    @Test
    fun `json kblist hzu real capture parses jc suffix and odd weeks`() {
        val src = readFixture("kblist_hzu_huizhou_20261004.json")
        val courses = JwNewZfParser(src).generateCourseList()

        assertTrue("惠州学院真实课表不应解析为空", courses.isNotEmpty())
        assertEquals("面向对象程序设计", courses.first().name)
        assertEquals(8, courses.first().startNode)
        assertEquals(9, courses.first().endNode)
        assertTrue("应保留单/双周课信息", courses.any { it.type == 1 || it.type == 2 })
    }

    /**
     * 湖北商贸学院 HBSM (2026-10-10 脱敏采集包) 真实 kbList 报文:
     * zf_new zftal-ui-v5 裸 /kbcx/ 路径, jc 带「节」后缀, zcd 含 "11-17周(单)" 单周形态,
     * sjkList 实践环节 (军训/入学教育) 不产课程, 军事理论在 kbList 内正常落库。
     * 18 课原始行 1:1 落库 (jc/zcd 均单段无展开), 教师名已脱敏为某某某。
     */
    @Test
    fun `json kblist hbsm real capture parses odd weeks and skips sjk practice`() {
        val src = readFixture("kblist_hbsm_20261010.json")
        val courses = JwNewZfParser(src).generateCourseList()
        assertCourses("kblist_hbsm_20261010", courses)
        assertEquals("HBSM 18 课原始行应 1:1 落库", 18, courses.size)
        assertTrue("应保留形势与政策-1 的单周 (11-17周(单)) 信息",
            courses.any { it.name == "形势与政策-1" && it.type == 1 && it.startWeek == 11 && it.endWeek == 17 })
        assertTrue("军事理论在 kbList 内, 应正常落库", courses.any { it.name == "军事理论" })
        assertTrue("军训属 sjkList 实践环节, 不应出现在课表", courses.none { it.name == "军训" })
    }

    // ════════════════════════════════════════════════════════
    // JSON 路径 - CF (青果) kbxx 防御
    // ════════════════════════════════════════════════════════

    @Test
    fun `cf-kbxx inline script with teaxms jxcdmcs is skipped silently`() {
        val src = readResource("jw_fixtures/cf-chengfang/typical_two_courses.html")
        val courses = JwNewZfParser(src).generateCourseList()
        // 关键断言: CF 防御使 JwNewZfParser 返回空, 不产脏数据
        assertTrue(
            "CF kbxx 必须被 JwNewZfParser 静默跳过 (teaxms/jxcdmcs/jcdm2/zcs 字段防御), " +
                "实际产出 ${courses.size} 门课: $courses",
            courses.isEmpty()
        )
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

    // ════════════════════════════════════════════════════════
    // 课程名完整保留 — #20 广东医科 zf_new 协议括号不丢
    // ════════════════════════════════════════════════════════
    //
    // parser 现状就是把 kcmc 字段整体当字符串存 — 不做任何清洗/裁剪/归一化.
    // 这两条锁死的就是 issue #20 给 Yinzhixiang152 的承诺:
    // "市场营销学(理论)" / "全口义齿工艺技术(实验)" 进了 CourseEntity 仍是原始字符串.
    // 任何后续归一化 (groupId trim, sortKey, 等等) 都不得动 courseName 字段本身.

    @Test
    fun `bracketed theory kcmc preserved verbatim for GDMU issue 20`() {
        val src = """{"kbList":[{"kcmc":"市场营销学(理论)","xqj":1,"jc":"1-2","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("市场营销学(理论)", courses[0].name)
    }

    @Test
    fun `bracketed experiment kcmc preserved verbatim for GDMU issue 20`() {
        val src = """{"kbList":[{"kcmc":"全口义齿工艺技术(实验)","xqj":2,"jc":"3-4","zcd":"1-16周","cdmc":"","xm":""}]}"""
        val courses = JwNewZfParser(src).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("全口义齿工艺技术(实验)", courses[0].name)
    }
}

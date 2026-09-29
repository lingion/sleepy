package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * issue#55 第四/五/六通路: CSV / HTML / 文本 三路导入恢复非标准时间课程 ownTime。
 *
 * - sleepy-v1/分享文本/JSON/ICS 已在先前测试中覆盖(Issue55OwnTimeRoundTripTest +
 *   Issue55IcsOwnTimeRoundTripTest)。
 * - 导出端只有 WakeUp JSON / 分享文本 / ICS / sleepy-v1 native 四种形态, 本文件所测
 *   CSV / HTML / 文本 三路为"导入专有"格式 — 第三方导出的 CSV/HTML 可能带
 *   「自定义时间/是/true/1」标记列, Sleepy 认出即恢复 ownTime; 不带则当标准作息行。
 * - 文本列序: 名称 老师 教室 星期 节次 周次 类型 [上课时间] — 与 Sleepy 旧制表符格式同规
 *   (SleepyMarkerTest 已锁), 第 8/9 列额外支持 "20:50 22:00" 两列或 "20:50-22:00" 单列。
 */
class Issue55CsvHtmlTextOwnTimeTest {

    private fun parseOrNull(text: String) =
        ScheduleParser.parse(text, defaultTableId = 999L)

    private fun assertOwnTime(
        course: CourseEntity,
        expectedStart: String,
        expectedEnd: String
    ) {
        assertTrue(
            "ownTime 必须恢复; got ownTime=${course.ownTime} startTime=${course.startTime} endTime=${course.endTime}",
            course.ownTime
        )
        assertEquals("startTime", expectedStart, course.startTime)
        assertEquals("endTime", expectedEnd, course.endTime)
        // 节次定位仍保留(网格布局兜底用)
        assertTrue("startNode>0", course.startNode >= 1)
        assertTrue("step>=1", course.step >= 1)
    }

    // ---- CSV: 自定义时间标记 + 起止时间 → ownTime 恢复, 同时验证时间列不污染公共作息收割 ----

    @Test
    fun csv_markerYes_restoresOwnTime_andPrivateTimesExcludedFromNodeTimes() {
        val csv = """
            课程名,教师,教室,星期,节次,周次,类型,开始时间,结束时间,自定义时间
            晚课,李四,C301,3,11-12,1-16,每周,20:50,22:00,是
            高数,张三,A101,1,1-2,1-16,每周,08:00,08:45,否
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val parsed = r.getOrThrow()
        assertEquals(2, parsed.courses.size)

        val own = parsed.courses[0]
        assertOwnTime(own, "20:50", "22:00")
        assertEquals("晚课", own.courseName)
        assertEquals(3, own.day)
        assertEquals(11, own.startNode)
        assertEquals(2, own.step)

        val normal = parsed.courses[1]
        assertFalse("普通行 ownTime 必须 false", normal.ownTime)
        assertEquals("", normal.startTime)
        assertEquals("", normal.endTime)

        // 关键契约: ownTime 行的 20:50-22:00 不应被收割进公共 timeJson
        // (晚课的真实时间由 ownTime 字段承载, 不与作息表的 08:00 系起止冲突)
        val ownStartInTimeJson = parsed.timeJson.contains("20:50")
        val ownEndInTimeJson = parsed.timeJson.contains("22:00")
        assertFalse(
            "ownTime 行的时间不应进公共作息 timeJson; got: ${parsed.timeJson}",
            ownStartInTimeJson && ownEndInTimeJson
        )
        // 而普通行的 08:00-08:45 应正常进作息收割
        assertTrue(
            "非 ownTime 行的时间应正常进作息; got: ${parsed.timeJson}",
            parsed.timeJson.contains("08:00")
        )
    }

    @Test
    fun csv_markerTrue_caseInsensitive_restoresOwnTime() {
        val csv = """
            课程名,教师,教室,星期,节次,周次,开始时间,结束时间,owntime
            夜自习,王五,D404,5,11,1-8,19:00,20:30,True
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertOwnTime(c, "19:00", "20:30")
    }

    @Test
    fun csv_markerOne_restoresOwnTime() {
        val csv = """
            课程名,教师,教室,星期,节次,周次,类型,开始时间,结束时间,自定义时间
            选修,赵六,E505,7,9-10,2-12,双,14:00,15:30,1
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue(r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertOwnTime(c, "14:00", "15:30")
    }

    @Test
    fun csv_noMarkerColumn_stillHarvestsTimes_andOwnTimeFalse() {
        // 普通 CSV: 无标记列 → 当作标准作息行, 不应锁 ownTime
        val csv = """
            课程名,教师,教室,星期,节次,周次,类型,开始时间,结束时间
            高数,张三,A101,1,1-2,1-16,每周,08:00,08:45
            晚课,李四,C301,3,11-12,1-16,每周,20:50,22:00
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val parsed = r.getOrThrow()
        assertEquals(2, parsed.courses.size)
        for (c in parsed.courses) {
            assertFalse("无标记列 → ownTime 必须 false", c.ownTime)
        }
        // 普通行的两端时间都应收割
        assertTrue(parsed.timeJson.contains("08:00"))
        assertTrue(parsed.timeJson.contains("22:00"))
    }

    @Test
    fun csv_markerYesButInvalidTimeRange_demotesToOwnTimeFalse() {
        // 标记是,但时间非法(开始 ≥ 结束) → 视为非法行, ownTime 不锁,
        // 但行本身仍按节点落位(不静默丢), 时间列忽略
        val csv = """
            课程名,教师,教室,星期,节次,周次,开始时间,结束时间,自定义时间
            夜课,钱七,F606,4,8,1-4,22:00,20:00,是
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertFalse("非法时间范围 → ownTime 必须 false", c.ownTime)
        assertEquals(4, c.day)
    }

    // ---- HTML: 自定义时间标记 + 起止时间 → ownTime 恢复 ----

    @Test
    fun html_markerYes_restoresOwnTime() {
        val html = """
            <table>
            <tr><td>课程</td><td>教师</td><td>教室</td><td>星期</td><td>节次</td><td>周次</td><td>开始时间</td><td>结束时间</td><td>自定义时间</td></tr>
            <tr><td>晚课</td><td>李四</td><td>C301</td><td>3</td><td>11-12</td><td>1-16</td><td>20:50</td><td>22:00</td><td>是</td></tr>
            <tr><td>高数</td><td>张三</td><td>A101</td><td>1</td><td>1-2</td><td>1-16</td><td>08:00</td><td>08:45</td><td></td></tr>
            </table>
        """.trimIndent()
        val r = parseOrNull(html)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val parsed = r.getOrThrow()
        assertEquals(2, parsed.courses.size)

        val own = parsed.courses[0]
        assertOwnTime(own, "20:50", "22:00")
        assertEquals("晚课", own.courseName)

        val normal = parsed.courses[1]
        assertFalse("普通行 ownTime 必须 false", normal.ownTime)
    }

    @Test
    fun html_noMarkerColumn_ignoresOwnTime() {
        val html = """
            <table>
            <tr><td>课程</td><td>教师</td><td>教室</td><td>星期</td><td>节次</td><td>周次</td></tr>
            <tr><td>高数</td><td>张三</td><td>A101</td><td>1</td><td>1-2</td><td>1-16</td></tr>
            </table>
        """.trimIndent()
        val r = parseOrNull(html)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        assertEquals(1, r.getOrThrow().courses.size)
        assertFalse(r.getOrThrow().courses[0].ownTime)
    }

    // ---- 文本: 类型后跟钟点列 → ownTime 恢复 ----

    @Test
    fun text_trailingTwoTimeFields_restoresOwnTime() {
        // 列序: 名称 老师 教室 星期 节次 周次 类型 上课开始 上课结束
        // 第 8/9 列两列钟点 = ownTime
        val text = "晚课\t李四\tC301\t3\t11-12\t1-16\t0\t20:50\t22:00"
        val r = parseOrNull(text)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertOwnTime(c, "20:50", "22:00")
        assertEquals("晚课", c.courseName)
    }

    @Test
    fun text_trailingSingleTimeRange_restoresOwnTime() {
        // 列序: 名称 老师 教室 星期 节次 周次 类型 HH:mm-HH:mm
        val text = "夜自习\t王五\tD404\t5\t11\t1-8\t0\t19:00-20:30"
        val r = parseOrNull(text)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertOwnTime(c, "19:00", "20:30")
    }

    @Test
    fun text_noTrailingTime_ownTimeFalse() {
        // 旧制表符格式: 7 列 = 标准课, 不应被误标 ownTime
        val text = "高数\t张三\tA101\t1\t1-2\t1-16\t0"
        val r = parseOrNull(text)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertFalse("7 列无钟点 → ownTime 必须 false", c.ownTime)
        assertEquals("", c.startTime)
        assertEquals("", c.endTime)
    }

    @Test
    fun text_trailingInvalidTime_demotesToOwnTimeFalse() {
        // 类型后跟一个非法钟点(开始≥结束) → ownTime 不锁, 课按节点落位
        val text = "夜课\t钱七\tF606\t4\t8\t1-4\t0\t25:00-26:00"
        val r = parseOrNull(text)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertFalse("非法时间 → ownTime 必须 false", c.ownTime)
    }

    // ---- CSV/HTML 时间列做两位补零归一(与 sleepy-v1 一致) ----

    @Test
    fun csv_singleDigitHour_normalizedToTwoDigits() {
        val csv = """
            课程名,教师,教室,星期,节次,周次,开始时间,结束时间,自定义时间
            早八,孙八,G707,2,1,1-16,8:00,8:45,是
        """.trimIndent()
        val r = parseOrNull(csv)
        assertTrue("failure=${r.exceptionOrNull()}", r.isSuccess)
        val c = r.getOrThrow().courses[0]
        assertEquals("08:00", c.startTime)
        assertEquals("08:45", c.endTime)
    }
}
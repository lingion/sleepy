package com.lingion.sleepy.data.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * ICS 解析回归 — WakeUp 课程表导出的 ICS(真实样本特征)。
 *
 * 此前 parseIcs 的灾难: 周次硬编码 1-16 / 节次 55min 瞎猜 / teacher=整段 DESCRIPTION /
 * startDate=导入当天 / 同课多 VEVENT 不合并。本测试锁定修复后的语义。
 */
class IcsWakeUpImportTest {

    /** 真实文件的最小切片: 单周理论课(1-12) + 双周毛概(2-16) + 散周创业基础 + 实训 17 周 */
    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//YZune//WakeUpSchedule//EN
        BEGIN:VEVENT
        SUMMARY:算法设计与分析（理论）
        DTSTART;TZID=Asia/Shanghai:20260831T082000
        DTEND;TZID=Asia/Shanghai:20260931T100000
        RRULE:FREQ=WEEKLY;UNTIL=20261122T160000Z;INTERVAL=1
        LOCATION:三教337 王锐
        DESCRIPTION:第1 - 2节\n三教337\n王锐
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:毛泽东思想和中国特色社会主义理论体系概论（理论）
        DTSTART;TZID=Asia/Shanghai:20260910T132000
        DTEND;TZID=Asia/Shanghai:20260910T150000
        RRULE:FREQ=WEEKLY;UNTIL=20260916T160000Z;INTERVAL=1
        LOCATION:二教B121 赵丽娜
        DESCRIPTION:第5 - 6节\n二教B121\n赵丽娜
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:毛泽东思想和中国特色社会主义理论体系概论（理论）
        DTSTART;TZID=Asia/Shanghai:20260924T132000
        DTEND;TZID=Asia/Shanghai:20260924T150000
        RRULE:FREQ=WEEKLY;UNTIL=20260930T160000Z;INTERVAL=1
        LOCATION:二教B121 赵丽娜
        DESCRIPTION:第5 - 6节\n二教B121\n赵丽娜
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:毛泽东思想和中国特色社会主义理论体系概论（理论）
        DTSTART;TZID=Asia/Shanghai:20261008T132000
        DTEND;TZID=Asia/Shanghai:20261008T150000
        RRULE:FREQ=WEEKLY;UNTIL=20261014T160000Z;INTERVAL=1
        LOCATION:二教B121 赵丽娜
        DESCRIPTION:第5 - 6节\n二教B121\n赵丽娜
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:毛泽东思想和中国特色社会主义理论体系概论（理论）
        DTSTART;TZID=Asia/Shanghai:20261022T132000
        DTEND;TZID=Asia/Shanghai:20261022T150000
        RRULE:FREQ=WEEKLY;UNTIL=20261028T160000Z;INTERVAL=1
        LOCATION:二教B121 赵丽娜
        DESCRIPTION:第5 - 6节\n二教B121\n赵丽娜
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:创业基础（理论）
        DTSTART;TZID=Asia/Shanghai:20260908T180000
        DTEND;TZID=Asia/Shanghai:20260908T193000
        RRULE:FREQ=WEEKLY;UNTIL=20260914T160000Z;INTERVAL=1
        LOCATION:二教B203 李力
        DESCRIPTION:第9 - 10节\n二教B203\n李力
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:创业基础（理论）
        DTSTART;TZID=Asia/Shanghai:20261013T180000
        DTEND;TZID=Asia/Shanghai:20261013T193000
        RRULE:FREQ=WEEKLY;UNTIL=20261019T160000Z;INTERVAL=1
        LOCATION:二教B203 李力
        DESCRIPTION:第9 - 10节\n二教B203\n李力
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:Linux操作系统课程实训（环节）
        DTSTART;TZID=Asia/Shanghai:20261221T082000
        DTEND;TZID=Asia/Shanghai:20261221T120000
        RRULE:FREQ=WEEKLY;UNTIL=20261227T160000Z;INTERVAL=1
        LOCATION:三教337 辛钢
        DESCRIPTION:第1 - 4节\n三教337\n辛钢
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun wakeUpIcs_parsesWeeksNodesTeacherAnchor() {
        val result = ScheduleParser.parse(ics, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()

        // 学期锚点 = 最早 DTSTART 所在周(2026-08-31 周一)
        assertEquals("2026-08-31", parsed.startDate)

        // 算法(1-12周) + 毛概双周合并1条 + 创业基础散周2条 + 实训(17周) = 5 行
        assertEquals(5, parsed.courses.size)

        val algo = parsed.courses.first { it.courseName.startsWith("算法设计") }
        assertEquals("王锐", algo.teacher)
        assertEquals("三教337", algo.room)
        assertEquals(1, algo.day)
        assertEquals(1, algo.startNode)
        assertEquals(2, algo.step)          // 第1-2节, 非 55min 瞜猜
        assertEquals(1, algo.startWeek)
        assertEquals(12, algo.endWeek)      // UNTIL 20261122 → 周12
        assertEquals(0, algo.type)

        val mao = parsed.courses.first { it.courseName.startsWith("毛泽东") }
        assertEquals(2, mao.startWeek)
        assertEquals(8, mao.endWeek)         // 4个事件 w2,4,6,8 → 双周 [2,8](真实文件 8 个事件 → [2,16])
        assertEquals(2, mao.type)           // 双周
        assertEquals(4, mao.day)
        assertEquals(5, mao.startNode)
        assertEquals(2, mao.step)

        // 创业基础: 散周 2,7 不构成双周序列 → 保持独立两行
        val chuangs = parsed.courses.filter { it.courseName.startsWith("创业") }
        assertEquals(2, chuangs.size)
        assertEquals(setOf(2, 7), chuangs.map { it.startWeek }.toSet())
        chuangs.forEach { assertEquals(0, it.type); assertEquals(2, it.day); assertEquals(9, it.startNode); assertEquals(2, it.step) }

        val practice = parsed.courses.first { it.courseName.startsWith("Linux") }
        assertEquals(17, practice.startWeek)
        assertEquals(17, practice.endWeek)
        assertEquals(1, practice.startNode)
        assertEquals(4, practice.step)      // 第1-4节
    }

    /** 同课同时段每周换教室(实证: 24sp 管理心理学) — 不得误判成假单双周,按教室分段输出 */
    @Test
    fun wakeUpIcs_roomAlternatingCourse_notFakeBiweekly() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//YZune//WakeUpSchedule//EN
            BEGIN:VEVENT
            SUMMARY:管理心理学DTSTART;TZID=Asia/Shanghai:20240507T190000
            DTEND;TZID=Asia/Shanghai:20240507T203500
            RRULE:FREQ=WEEKLY;UNTIL=20240507T160000Z;INTERVAL=1
            LOCATION:博1-A102 段鑫星
            DESCRIPTION:第9 - 10节\n博1-A102\n段鑫星
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:管理心理学DTSTART;TZID=Asia/Shanghai:20240514T190000
            DTEND;TZID=Asia/Shanghai:20240514T203500
            RRULE:FREQ=WEEKLY;UNTIL=20240514T160000Z;INTERVAL=1
            LOCATION:博5-BC区线上教室 段鑫星
            DESCRIPTION:第9 - 10节\n博5-BC区线上教室\n段鑫星
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:管理心理学DTSTART;TZID=Asia/Shanghai:20240521T190000
            DTEND;TZID=Asia/Shanghai:20240521T203500
            RRULE:FREQ=WEEKLY;UNTIL=20240521T160000Z;INTERVAL=1
            LOCATION:博1-A102 段鑫星
            DESCRIPTION:第9 - 10节\n博1-A102\n段鑫星
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()
        // 3 个逐周连续事件 → 合并为 1 行 [11,13] type=0(取首教室)。
        // 关键: 不得因两组教室各自同奇偶被拆成假"单周/双周"两条
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        assertEquals(0, c.type)
        // 锚点 = 最早事件周 → 相对周号 1-3(绝对 11-13 学期周无 ICS 学期信息不可得)
        assertEquals(1, c.startWeek)
        assertEquals(3, c.endWeek)
        assertEquals(2, c.day)
        assertEquals(9, c.startNode)
        assertEquals(2, c.step)
        assertEquals("段鑫星", c.teacher)
        assertEquals("博1-A102", c.room)
    }

    /** 真双周(同教室,weeks 2,4,6,8)必须仍识别为 type=2 — 防止上面的修正误伤 */
    @Test
    fun wakeUpIcs_trueBiweekly_stillDetected() {
        val sb = StringBuilder("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//YZune//WakeUpSchedule//EN\n")
        // 周 2,4,6,8 → 锚 2026-08-31, 周二第5节
        for (w in listOf(2, 4, 6, 8)) {
            val date = java.time.LocalDate.of(2026, 8, 31).plusWeeks((w - 1).toLong()).plusDays(1)
            val d = date.toString().replace("-", "")
            sb.append("""
                BEGIN:VEVENT
                SUMMARY:真双周课
                DTSTART;TZID=Asia/Shanghai:${d}T102000
                DTEND;TZID=Asia/Shanghai:${d}T120000
                RRULE:FREQ=WEEKLY;UNTIL=${d}T160000Z;INTERVAL=1
                LOCATION:A101 李老师
                DESCRIPTION:第5 - 6节\nA101\n李老师
                END:VEVENT
            """.trimIndent() + "\n")
        }
        sb.append("END:VCALENDAR")
        val parsed = ScheduleParser.parse(sb.toString(), defaultTableId = 999L).getOrThrow()
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        // 锚点 = 最早事件所在周(本测试首事件在第 2 周 → 相对周号 1,3,5,7)
        assertEquals(1, c.startWeek)
        assertEquals(7, c.endWeek)
        assertEquals(1, c.type)   // 起始周相对奇数 → 单周序列
        assertEquals("A101", c.room)
    }

    /** Sleepy 自家导出的 ICS(DESCRIPTION:老师：X + INTERVAL=2) 也要能回读 */
    @Test
    fun sleepyIcs_roundTripStillWorks() {
        val table = com.lingion.sleepy.data.entity.TimeTableEntity(
            id = 1, name = "T", startDate = "2026-02-23", maxWeek = 18, nodesPerDay = 13,
            timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:50","end":"09:35"},{"node":3,"start":"10:00","end":"10:45"}]""",
            color = "#FF6750A4", isDefault = true
        )
        val courses = listOf(
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 0, groupId = "", tableId = 1, courseName = "高数", teacher = "张三", room = "A101",
                day = 2, startNode = 1, step = 2, startWeek = 1, endWeek = 16, type = 0, color = "#FF6750A4"
            )
        )
        val exported = ScheduleExporter.exportIcs(table, courses)
        val parsed = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()
        assertEquals(1, parsed.courses.size)
        val c = parsed.courses[0]
        assertEquals("高数", c.courseName)
        assertEquals("张三", c.teacher)
        assertEquals("A101", c.room)
        assertEquals(2, c.day)
        assertEquals(1, c.startNode)
        assertEquals(2, c.step)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(0, c.type)
        assertEquals("2026-02-23", parsed.startDate)
    }

    /** ICS 自带全校作息(DTSTART/DTEND × 第X-Y节) → 解析成 timeJson,导入后时间直接正确 */
    @Test
    fun wakeUpIcs_harvestsTimeTableFromEvents() {
        val parsed = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()

        // timeJson: 节次 → 起止。本样本覆盖 1,3,5,9 四个块锚点
        val timeJson = parsed.timeJson
        assertTrue("timeJson should be harvested", timeJson.isNotBlank())
        val nodes = org.json.JSONArray(timeJson).let { arr ->
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                o.getInt("node") to (o.getString("start") to o.getString("end"))
            }
        }
        // 事件直接给出: 1-2节@08:20-10:00, 1-4节@08:20-12:00, 5-6节@13:20-15:00, 9-10节@18:00-19:30
        // → 块锚点 1@08:20, 4@12:00, 5@13:20, 6@15:00, 9@18:00, 10@19:30
        assertEquals("08:20", nodes[1]?.first)
        assertEquals("12:00", nodes[4]?.second)
        assertEquals("13:20", nodes[5]?.first)
        assertEquals("15:00", nodes[6]?.second)
        assertEquals("18:00", nodes[9]?.first)
        assertEquals("19:30", nodes[10]?.second)
        // 收割出的节数(用于建表 nodesPerDay)
        assertEquals(10, parsed.nodesPerDay)
    }

    /** 无 DESCRIPTION 节次行、无 DTEND 的裸 DTSTART 文件 → 不收割,回退默认(不炸) */
    @Test
    fun wakeUpIcs_noTimeHarvestWhenSparse() {
        val sparse = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:裸课
            DTSTART;TZID=Asia/Shanghai:20260831T082000
            RRULE:FREQ=WEEKLY;INTERVAL=1
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = ScheduleParser.parse(sparse, defaultTableId = 999L).getOrThrow()
        assertTrue(parsed.timeJson.isBlank())
    }
}

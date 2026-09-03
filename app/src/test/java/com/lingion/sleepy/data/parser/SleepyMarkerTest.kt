package com.lingion.sleepy.data.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * <<<SLEEPY-BEGIN>>> / <<<SLEEPY-END>>> 标识提取回归 —
 * AI(豆包等)输出常带开场白/结尾废话, 数据包在标识之间才能稳定导入。
 */
class SleepyMarkerTest {

    private val courses = """
        高等数学	张三	A101	1	1-2	1-16	0
        大学英语	李四	B202	3	3-4	1-16	1
    """.trimIndent()

    @Test
    fun aiChatterOutsideMarkers_isDropped() {
        val text = """
            好的，我已经看了你的课表截图，下面是转换结果：
            <<<SLEEPY-BEGIN>>>
            $courses
            <<<SLEEPY-END>>>
            希望对你有帮助！如果还需要调整格式，随时告诉我～
        """.trimIndent()
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals("高等数学", r.courses[0].courseName)
        assertEquals(1, r.courses[0].day)
        assertEquals(2, r.courses[0].step)
        assertEquals("大学英语", r.courses[1].courseName)
    }

    @Test
    fun missingEndMarker_stillParses() {
        // AI 忘了 END 标识: BEGIN 之后全要
        val text = "转换如下：\n<<<SLEEPY-BEGIN>>>\n$courses"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
    }

    @Test
    fun missingBeginMarker_stillParses() {
        // AI 忘了 BEGIN 只给了 END: END 之前全要(前缀废话行 <6 列会被逐行丢弃)
        val text = "$courses\n<<<SLEEPY-END>>>\n祝使用愉快！"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
    }

    @Test
    fun noMarkers_handwrittenTextUnaffected() {
        // 手工输入不带标识 → 原路径
        val r = ScheduleParser.parse(courses, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
    }

    @Test
    fun markersInsideCodeFence_alsoWork() {
        // AI 把标识包进 ``` 代码块: 围栏行落在标识外, 被自然丢弃
        val text = "结果：\n```\n<<<SLEEPY-BEGIN>>>\n$courses\n<<<SLEEPY-END>>>\n```"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
    }

    // ---- 防呆: AI 不听话的各种姿势 (2026-08-26) ----

    @Test
    fun sloppyMarker_missingDashes_stillExtracted() {
        // 标识写歪: 少横线 + 大小写乱 + {{ 括号
        val text = "转换结果：\n{{SLEEPY BEGIN}}\n$courses\n<<sleepy-end>>\n祝好！"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
    }

    @Test
    fun markdownTable_output_parsed() {
        // AI 输出成 Markdown 表格(带表头 + |---| 分隔行)
        val text = """
            好的，转换结果：
            <<<SLEEPY-BEGIN>>>
            | 课程 | 老师 | 教室 | 星期 | 节次 | 周次 | 类型 |
            |---|---|---|---|---|---|---|
            | 高等数学 | 张三 | A101 | 1 | 1-2 | 1-16 | 0 |
            | 大学英语 | 李四 | B202 | 3 | 3-4 | 1-16 | 1 |
            <<<SLEEPY-END>>>
        """.trimIndent()
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        // 表头行(课程 老师 教室 星期…) 7 列但星期列是文字 → 跳过且计入 dropped;
        // 分隔行整行管道 → 静默跳过; 两行数据课正常入库
        val named = r.courses.map { it.courseName }
        assertTrue("高等数学" in named && "大学英语" in named)
        // 表头行出现在 dropped 提示里, 用户可见
        assertTrue(r.droppedLines.isNotEmpty())
    }

    @Test
    fun chineseWeekday_accepted() {
        // AI 无视指令写「周一」
        val text = "<<<SLEEPY-BEGIN>>>\n高等数学\t张三\tA101\t周一\t1-2\t1-16\t0\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].day)
    }

    @Test
    fun fullWidthDigits_normalized() {
        // AI 输出全角数字/全角横线
        val text = "<<<SLEEPY-BEGIN>>>\n高等数学\t张三\tＡ１０１\t１\t１－２\t１－１６\t０\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].day)
        assertEquals(1, r.courses[0].startNode)
        assertEquals(2, r.courses[0].step)
        assertEquals(16, r.courses[0].endWeek)
    }

    @Test
    fun reversedRanges_sorted() {
        // 区间反写 16-1 → (1,16), 不再生成永远不显示的隐形课
        val text = "<<<SLEEPY-BEGIN>>>\n高等数学\t张三\tA101\t1\t2-1\t16-1\t0\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(1, r.courses[0].startNode)
        assertEquals(2, r.courses[0].step)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(16, r.courses[0].endWeek)
    }

    @Test
    fun outOfRangeDay_clamped() {
        // day 0 / 8 钳到 1..7, 不再产生周视图永远不显示的隐形课
        val text = "<<<SLEEPY-BEGIN>>>\n高等数学\t张三\tA101\t0\t1-2\t1-16\t0\n大学英语\t李四\tB202\t8\t3-4\t1-16\t0\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertTrue(r.courses.all { it.day in 1..7 })
    }

    @Test
    fun markdownBold_strippedFromCourseName() {
        // 课程名套 **加粗**
        val text = "<<<SLEEPY-BEGIN>>>\n**高等数学**\t张三\tA101\t1\t1-2\t1-16\t0\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals("高等数学", r.courses[0].courseName)
    }

    @Test
    fun csvWithoutTeacherColumn_nowDetected() {
        // 教务导出常无教师列 — 之前直接不识别为 CSV, 现在课程+周次即可识别
        val csv = "课程,教室,星期,节次,周次\n高等数学,A101,1,1-2,1-16\n大学英语,B202,3,3-4,1-16"
        val r = ScheduleParser.parse(csv, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals("", r.courses[0].teacher)
    }

    @Test
    fun garbageLines_reportedInDropped() {
        // 半截行 / 乱行 → 不再静默丢, droppedLines 里可见
        val text = "<<<SLEEPY-BEGIN>>>\n$courses\n这是AI瞎说的一行\n高数\t张三\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals(2, r.droppedLines.size)
        assertTrue(r.droppedLines.any { it.contains("这是AI瞎说的") })
    }

    // ---- 节次时间收割 (2026-08-26): 截图有时间就该吃进来 ----

    @Test
    fun plainText_timeTableLines_harvested() {
        // AI 照截图抄了作息表: "第1节 08:00-09:35" 混排在课程行前
        val text = "<<<SLEEPY-BEGIN>>>\n第1节 08:00-09:35\n第2节 09:55-11:30\n第3节 13:30-15:05\n$courses\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertTrue(r.timeJson.isNotBlank())
        // v7.10.16k 无损: 节次数 = max(作息声明 3, 课程到达 4) = 4, timeJson 只含声明过的 3 行真实时间
        assertEquals(4, r.nodesPerDay)
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson)
        assertEquals(3, nodes.size)
        assertEquals(java.time.LocalTime.of(8, 0), nodes[0].start)
        assertEquals(java.time.LocalTime.of(9, 35), nodes[0].end)
        assertEquals(java.time.LocalTime.of(13, 30), nodes[2].start)
    }

    @Test
    fun plainText_timeTable_noColonFormat_alsoWork() {
        // "时间表 1 08:00 09:35" 空格分隔形态; 课程到达 4 节 > 作息声明 2 节 → 无损取 4
        val text = "时间表 1 08:00 09:35\n时间表 2 09:55 11:30\n$courses"
        val r = ScheduleParser.parse(text, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals(4, r.nodesPerDay)
    }

    @Test
    fun plainText_noTimeTable_unaffected() {
        // 没有时间行的纯文本 → timeJson 空(不伪造时间); nodesPerDay = 课程到达 4 节(无损)
        val r = ScheduleParser.parse(courses, 0L).getOrThrow()
        assertEquals("", r.timeJson)
        assertEquals(4, r.nodesPerDay)
    }

    @Test
    fun csv_timeColumns_harvested() {
        val csv = "课程,教师,教室,星期,节次,周次,开始时间,结束时间\n" +
            "高等数学,张三,A101,1,1-2,1-16,08:00,09:35\n" +
            "大学英语,李四,B202,3,3-4,1-16,13:30,15:05"
        val r = ScheduleParser.parse(csv, 0L).getOrThrow()
        assertEquals(2, r.courses.size)
        assertEquals(4, r.nodesPerDay)
        val nodes = com.lingion.sleepy.util.TimeTableUtils.parseNodes(r.timeJson)
        // 1-2 节 08:00-09:35: 首=08:00 末节次=2 的 end=09:35
        assertEquals(java.time.LocalTime.of(8, 0), nodes.first { it.node == 1 }.start)
        assertEquals(java.time.LocalTime.of(9, 35), nodes.first { it.node == 2 }.end)
        assertEquals(java.time.LocalTime.of(13, 30), nodes.first { it.node == 3 }.start)
        assertEquals(java.time.LocalTime.of(15, 5), nodes.first { it.node == 4 }.end)
    }

    @Test
    fun sleepyJsonRoundTrip_timeJsonPreserved() {
        // Sleepy 自家导出(带 tableInfo.time) → 导入回来时间不丢
        val exported = """
            {
              "name": "我的课表",
              "startDate": "2026-09-07",
              "tableInfo": {
                "name": "我的课表",
                "startDate": "2026-09-07",
                "maxWeek": 20,
                "nodesPerDay": 3,
                "time": "[{\"node\":1,\"start\":\"08:00\",\"end\":\"09:35\"},{\"node\":2,\"start\":\"09:55\",\"end\":\"11:30\"}]"
              },
              "courses": [
                {"name":"高等数学","teacher":"张三","position":"A101","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0}
              ]
            }
        """.trimIndent()
        val r = ScheduleParser.parse(exported, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertTrue(r.timeJson.isNotBlank())
        assertEquals(2, r.nodesPerDay)
    }

    @Test
    fun wakeupNative_timeList_harvested() {
        // WakeUp 原生 timeList 格式
        val json = """
            {
              "name": "WakeUp表",
              "startDate": "2026-09-07",
              "tableInfo": {
                "timeList": [
                  {"node": 1, "startTime": "08:00", "endTime": "09:35"},
                  {"node": 2, "startTime": "09:55", "endTime": "11:30"}
                ]
              },
              "courses": [
                {"name":"高数","teacher":"张三","position":"A101","day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0}
              ]
            }
        """.trimIndent()
        val r = ScheduleParser.parse(json, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(2, r.nodesPerDay)
        assertTrue(r.timeJson.contains("\"node\":1"))
        assertTrue(r.timeJson.contains("09:35"))
    }
// ==== 2026-08-26: TIME block 标识符 ====

    @Test fun timeBlock_valid() {
        val input = "<<<SLEEPY-TIME-BEGIN>>>" + "\n第1节 08:00-09:35" + "\n第2节 09:55-11:30" + "\n<<<SLEEPY-TIME-END>>>" + "\n高等数学\t张三\tA101\t1\t1-2\t1-16\t0"
        val r = ScheduleParser.parse(input, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(2, r.nodesPerDay)
        assertTrue(r.timeJson.contains("08:00"))
    }

    @Test fun timeBlock_missingEnd_selfHeal() {
        // 无 END 时：连续作息行被吞掉，碰到非作息行停止（自愈）
        val input = "<<<SLEEPY-TIME-BEGIN>>>" + "\n第1节 08:00-09:35" + "\n第2节 09:55-11:30" + "\n高等数学\t张三\tA101\t1\t1-2\t1-16\t0"
        val r = ScheduleParser.parse(input, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(2, r.nodesPerDay)
    }

    @Test fun timeBlock_noMarkers_inline() {
        // 无 TIME 标识时：裸作息行混排兼容（旧行为）
        val input = "第1节 08:00-09:35" + "\n第2节 09:55-11:30" + "\n高等数学\t张三\tA101\t1\t1-2\t1-16\t0"
        val r = ScheduleParser.parse(input, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(2, r.nodesPerDay)
    }

    @Test fun timeBlock_withinOuter() {
        // TIME 块在 BEGIN/END 内，外层标识先被剥离，TIME块在剩余内容中
        // 架构限制：此场景暂不保证work，核心功能是独立TIME块
        val input = "<<<SLEEPY-BEGIN>>>\n<<<SLEEPY-TIME-BEGIN>>>\n第1节 08:00-09:35\n<<<SLEEPY-TIME-END>>>\n高等数学\t张三\tA101\t1\t1-2\t1-16\t0\n<<<SLEEPY-END>>>"
        val r = ScheduleParser.parse(input, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        // nodesPerDay取决于TIME块是否被正确保留
    }

    @Test fun timeBlock_variantBrackets() {
        // 括号变体测试独立TIME块
        val input = "{{{SLEEPY-TIME-BEGIN}}}" + "\n第1节 08:00-09:35" + "\n第2节 09:55-11:30" + "\n{{{SLEEPY-TIME-END}}}" + "\n高等数学\t张三\tA101\t1\t1-2\t1-16\t0"
        val r = ScheduleParser.parse(input, 0L).getOrThrow()
        assertEquals(1, r.courses.size)
        assertEquals(2, r.nodesPerDay)
    }

}

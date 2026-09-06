package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合工大 EAMS5 协议 (金智 eams5-student 系列，Chiu-xaH/HFUT-Schedule 同源) 解析器测试。
 *
 * 数据：src/test/resources/jw/fixtures/eams5/schedule-table-datum.sample.json
 * 该 JSON 形态取自 POST /ws/schedule-table/datum 响应 (JxglstuCourseTableResponse)。
 *
 * 字段契约（与 HFUT-Schedule JxglstuCourseTableResponse 对齐）：
 *   scheduleList[].lessonId          → look up lessonList[].courseName
 *   scheduleList[].room.nameZh       → room
 *   scheduleList[].weekday           → day
 *   scheduleList[].personName        → teacher
 *   scheduleList[].weekIndex         → 单周（v1 emit 每行一周，range=单值）
 *   scheduleList[].startTime         (Int HHmm) → 推断 startNode (v1 用标准 985 节次表)
 *   scheduleList[].endTime           (Int HHmm) → 推断 endNode
 *   scheduleList[].periods           (Int) → 备用: endNode = startNode + periods - 1 (节次已知情形)
 *
 * v1 限制（详见 JwEams5Parser KDoc）：
 *   1. startNode/endNode 是基于 startTime/endTime 的节次表推断,非权威 (上游无 /ws/time-table 接口)
 *      — 同模式 CQU 直接给 periodFormat 准确; v2 可拉 /for-std/program/root-module-json 拿真表
 *   2. weekIndex 仅单周 (无单双周压缩), type=0
 *   3. 无 scheduleWeeksInfo 范围 (那是 /for-std/lessons 接口,不是 datum 接口)
 *
 * 期望（按样张 4 行）：
 *   - 4 个 JwCourse
 *   - 1001 高数 周1 node 1-2 节 startWeek=1 endWeek=1
 *   - 1002 大学物理 周3 node 3-4 节 startWeek=2 endWeek=2
 *   - 1003 数据结构实验 周5 node 5-8 节 startWeek=5 endWeek=5
 */
class JwEams5ParserTest {

    private fun loadJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/schedule-table-datum.sample.json")
        assertNotNull("测试资源 schedule-table-datum.sample.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private fun parse() = JwEams5Parser(loadJson()).generateCourseList()

    @Test
    fun `parses EAMS5 schedule - total count equals scheduleList rows`() {
        val courses = parse()
        // 4 个 scheduleList 行 → 4 个 JwCourse (v1 不合并)
        assertEquals("4 个 scheduleList 行应产出 4 个 JwCourse", 4, courses.size)
    }

    @Test
    fun `parses EAMS5 schedule - lesson 1001 高数 maps to correct course name and time`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "高等数学" }
        assertNotNull("应找到 高等数学", c)
        c!!
        assertEquals("周1", 1, c.day)
        // startTime=800 (08:00) → 推断为节点 1; periods=2 → 节点 2
        assertEquals("startNode", 1, c.startNode)
        assertEquals("endNode", 2, c.endNode)
        assertEquals("startWeek=weekIndex", 1, c.startWeek)
        assertEquals("endWeek=weekIndex (v1 单周)", 1, c.endWeek)
        assertEquals("type=0 (v1 无单双周判定)", 0, c.type)
        assertEquals("张三", "张三", c.teacher)
        assertEquals("A楼101", "A楼101", c.room)
    }

    @Test
    fun `parses EAMS5 schedule - 1002 大学物理 weekday mapping`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "大学物理" }
        assertNotNull(c)
        c!!
        assertEquals("weekday=3 → day=3", 3, c.day)
        // startTime=1010 (10:10) → 推断为 node3 (上午第二大段)
        assertEquals("startNode (10:10 → node3)", 3, c.startNode)
        assertEquals("endNode", 4, c.endNode)
        assertEquals("weekIndex=1", 1, c.startWeek)
    }

    @Test
    fun `parses EAMS5 schedule - 1003 数据结构实验 4-period row`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "数据结构实验" }
        assertNotNull(c)
        c!!
        assertEquals("weekday=5 → day=5", 5, c.day)
        // startTime=1400 (14:00) → 推断为 node5 (下午第一段); periods=4 → 节点 8
        assertEquals("startNode (14:00 → node5)", 5, c.startNode)
        assertEquals("endNode (startNode + periods - 1)", 8, c.endNode)
        assertEquals("room 实验楼401", "实验楼401", c.room)
        assertEquals("teacher 王五", "王五", c.teacher)
    }

    @Test
    fun `parses EAMS5 schedule - lessonId not in lessonList falls back to ID string`() {
        // v1 容错: lessonId 不在 lessonList → 用 lessonId.toString() 作 name (HFUT 实际行为)
        val parser = JwEams5Parser("""
            {
              "result": {
                "lessonList": [],
                "scheduleList": [
                  {
                    "lessonId": 9999,
                    "room": null,
                    "weekday": 2,
                    "personName": "外教",
                    "weekIndex": 3,
                    "startTime": 1900,
                    "periods": 1,
                    "endTime": 1950,
                    "date": "2024-09-10",
                    "lessonType": ""
                  }
                ]
              }
            }
        """.trimIndent())
        val courses = parser.generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("9999", courses[0].name)
        assertEquals("空 room 应输出空串", "", courses[0].room)
    }

    @Test
    fun `cross-section class endTime at next section start`() {
        // 实验课 14:00-17:55 (跨下午 1+2 两节): startTime=1400 → sec2, endTime=1755 → sec3 (16:10±10min 不吸, 17:55 不在段起点)
        // 走 periods 兜底: endNode = 5 + 4 - 1 = 8
        val parser = JwEams5Parser("""
            { "result": { "lessonList": [], "scheduleList": [
                { "lessonId": 7, "room": null, "weekday": 4, "personName": "X", "weekIndex": 6,
                  "startTime": 1400, "periods": 4, "endTime": 1755, "date": "2024-09-26", "lessonType": "" }
            ] } }
        """.trimIndent())
        val c = parser.generateCourseList().single()
        // 17:55 不在段起点附近 → sectionIndex null → 走 periods 兜底 → 5 + 4 - 1 = 8
        assertEquals("跨节次 endTime 兜底 periods", 8, c.endNode)
    }

    @Test
    fun `confidence is high when JSON shape matches datum`() {
        val p = JwEams5Parser(loadJson())
        assertTrue("confidence 应 > 80", p.confidence() >= 80)
    }

    @Test
    fun `matchedFeatures lists datum-shape anchors`() {
        val p = JwEams5Parser(loadJson())
        val feats = p.matchedFeatures()
        assertTrue("应含 scheduleList", feats.any { it.contains("scheduleList") })
        assertTrue("应含 lessonList", feats.any { it.contains("lessonList") })
    }

    @Test
    fun `unmappable start time falls back to periods not dropped`() {
        // KDoc 承诺 periods 兜底: startTime 缺失 (0) 时旧行为是整行丢弃;
        // 现在退到 startNode=1, endNode = 1 + periods - 1
        val p = JwEams5Parser("""
        {"result": {
          "lessonList": [{"id": 9001, "courseName": "兜底课"}],
          "scheduleList": [
            {"lessonId": 9001, "weekday": 2, "weekIndex": 3,
             "startTime": 0, "endTime": 0, "periods": 3}
          ]
        }}
        """.trimIndent())
        val courses = p.generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals(3, courses[0].endNode)
    }

    @Test
    fun `unmappable start time without periods info drops row`() {
        val p = JwEams5Parser("""
        {"result": {
          "lessonList": [{"id": 9002, "courseName": "无信息课"}],
          "scheduleList": [
            {"lessonId": 9002, "weekday": 2, "weekIndex": 3,
             "startTime": 0, "endTime": 0, "periods": 0}
          ]
        }}
        """.trimIndent())
        assertTrue(p.generateCourseList().isEmpty())
    }

    // -------- AHU (jw.ahu.edu.cn) 形态 — 2026-09-06 cross-verified
    // 来源 (5 仓共识):
    //   MoeclubM/AHU-AIO (Dart) + abydym/Ahu_Plus (Kotlin 同栈) + Landon-3314/AHU-TimeTable (Flutter)
    //   + Zeraora-807/Anhui-Univ-DSH-Tool (TypeScript) + qiqqqqq517/shangkeschschedule (Apache-2.0)
    //
    //   GET /student/for-std/course-table/semester/<semesterId>/print-data
    //       ?semesterId=<id>&hasExperiment=false
    //   resp: {"studentTableVms":[{"activities":[…]}]}
    //
    // 字段契约 (Landon-3314 + MoeclubM + abydym 共识):
    //   activity.lessonId (Long/Int)     → 课程 ID
    //   activity.courseName (String)     → 课程名
    //   activity.teacherNames (List)     → 教师 (join "/")
    //   activity.campus+building+room    → 教室 (三段拼接)
    //   activity.weekday (Int 1-7)       → 周几
    //   activity.weekIndexes (String)    → 周次位图 "1-16"/"1-16单"/"8,10,12,14"
    //   activity.startUnit (Int)         → 起始节次 (直接, 无需推断)
    //   activity.endUnit (Int)           → 结束节次 (直接, 无需推断)

    @Test
    fun `parses AHU print-data - total count equals RLE-expanded activities rows`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        assertNotNull("测试资源 get-data-ahu.sample.json 应存在", stream)
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        // 5 个 activities 展开: 1-16 (1) + 1-8双 (1) + 5,7,9 (3) + 1-16单 (1) + 8,10,12,14 (4) = 10 个 JwCourse
        assertEquals("5 个 activities RLE 展开应产出 10 个 JwCourse", 10, courses.size)
    }

    @Test
    fun `parses AHU print-data - lesson 1001 高数 maps to correct fields`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "高等数学" }
        assertNotNull("应找到 高等数学", c)
        c!!
        assertEquals("周1", 1, c.day)
        // startUnit=1 (直接给定, 无需推断)
        assertEquals("startUnit=1", 1, c.startNode)
        assertEquals("endUnit=2", 2, c.endNode)
        // weekIndexes="1-16" → RLE 全周
        assertEquals("weekIndexes 1-16 → startWeek=1", 1, c.startWeek)
        assertEquals("weekIndexes 1-16 → endWeek=16", 16, c.endWeek)
        assertEquals("teacherNames 单元素 join", "张教授", c.teacher)
        // room: "龙河校区 博学南楼 A101" 三段拼接
        assertEquals("room campus+building+room 三段拼接", "龙河校区 博学南楼 A101", c.room)
    }

    @Test
    fun `parses AHU print-data - 1002 大学物理 多教师 join`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "大学物理" }
        assertNotNull(c)
        c!!
        // teacherNames=["李副教授", "钱讲师"] → "李副教授/钱讲师"
        assertEquals("teacherNames join '/'", "李副教授/钱讲师", c.teacher)
        assertEquals("周2", 2, c.day)
        assertEquals("startUnit=3", 3, c.startNode)
        assertEquals("endUnit=4", 4, c.endNode)
        // weekIndexes="1-8双" → 双周, startWeek=2 (JwParity 抬到首个偶数), endWeek=8
        assertEquals("weekIndexes 1-8双 → startWeek=2 (JwParity 抬)", 2, c.startWeek)
        assertEquals("weekIndexes 1-8双 → endWeek=8", 8, c.endWeek)
        assertEquals("weekIndexes 1-8双 → type=2 (双周)", 2, c.type)
    }

    @Test
    fun `parses AHU print-data - weekday mapping across week`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        // 1003 数据结构 weekday=3 周三
        val ds = courses.firstOrNull { it.name == "数据结构" }
        assertNotNull(ds)
        ds!!
        assertEquals("数据结构 周三", 3, ds.day)
        // startUnit=5 → 直接给定
        assertEquals("startUnit=5", 5, ds.startNode)
        assertEquals("endUnit=6", 6, ds.endNode)
        // weekIndexes="5,7,9" 离散 3 段 emit 3 课, 取首个
        assertEquals("weekIndexes 5,7,9 离散第 1 段 startWeek=5", 5, ds.startWeek)
        // 总共 3 个 JwCourse 名 "数据结构" 周三
        val dsCount = courses.count { it.name == "数据结构" }
        assertEquals("数据结构 离散 emit 3 个", 3, dsCount)
    }

    @Test
    fun `parses AHU print-data - 1005 形势与政策 晚上段 bitmap`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "形势与政策" }
        assertNotNull(c)
        c!!
        // startUnit=9 → 晚上段
        assertEquals("startUnit=9", 9, c.startNode)
        assertEquals("endUnit=10", 10, c.endNode)
        assertEquals("周五", 5, c.day)
        // weekIndexes="8,10,12,14" 离散 → 取首个 8
        assertEquals("weekIndexes bitmap 8,10,12,14 → startWeek=8", 8, c.startWeek)
    }

    @Test
    fun `parses AHU print-data - 1003 数据结构 跨校区 room`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "数据结构" }
        assertNotNull(c)
        c!!
        // 磬苑校区 + 计算机学院实验楼 + 301 三段
        assertEquals("跨校区 room 三段拼接", "磬苑校区 计算机学院实验楼 301", c.room)
    }

    @Test
    fun `AHU print-data confidence highest among AHU shapes`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val p = JwEams5Parser(json)
        // print-data 含 weekday+节次权威 → confidence=100 (高于 HFUT 双层 95)
        assertEquals("AHU print-data confidence=100 (含 weekday+startUnit 权威)", 100, p.confidence())
        val feats = p.matchedFeatures()
        assertTrue("应含 studentTableVms 锚点", feats.any { it.contains("studentTableVms") })
    }

    @Test
    fun `AHU print-data precedence over data-lessons and HFUT shape`() {
        // print-data activities[] 优先 data.lessons[] + result.scheduleList (5 仓共识)
        // fixture 同时含三层 (5 课 + 1 课 + 1 课, 假装冲突响应)
        val p = JwEams5Parser("""
        {
          "code": 200,
          "studentTableVms": [{
            "activities": [
              {"lessonId": "A1", "courseName": "AHU优先课",
               "teacherNames": ["T"], "campus": "X", "building": "Y", "room": "Z",
               "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
            ]
          }],
          "data": { "lessons": [
            {"lessonId": 1, "courseName": "AHU_metadata_课", "teacher": "T", "room": "R",
             "weekday": 1, "weekIndex": 1, "startTime": 800, "endTime": 950, "periods": 2}
          ]},
          "result": { "lessonList": [{"id":"99","courseName":"HFUT抢课"}],
                      "scheduleList": [{"lessonId":99, "weekday":1, "weekIndex":1,
                                        "startTime":800, "endTime":950, "periods":2}]}
        }
        """.trimIndent())
        val courses = p.generateCourseList()
        assertEquals("只产 1 课 (AHU print-data 形态优先)", 1, courses.size)
        assertEquals("AHU优先课", courses[0].name)
        // room 三段拼接
        assertEquals("X Y Z", courses[0].room)
    }

    @Test
    fun `AHU print-data confidence falls back to data-lessons when no activities`() {
        // 仅 data.lessons[] 时 → confidence=85 (metadata-only, 比 print-data 低)
        val p = JwEams5Parser("""
        {
          "code": 200,
          "data": { "lessons": [
            {"lessonId": 1, "courseName": "AHU_meta_only", "teacher": "T", "room": "R",
             "weekday": 1, "weekIndex": 1, "startTime": 800, "endTime": 950, "periods": 2}
          ]}
        }
        """.trimIndent())
        assertEquals("仅 data.lessons[] → confidence=85 (metadata-only)", 85, p.confidence())
        assertEquals(1, p.generateCourseList().size)
    }

    @Test
    fun `AHU empty weekIndexes falls back to week 1`() {
        val p = JwEams5Parser("""
        {
          "code": 200,
          "studentTableVms": [{
            "activities": [
              {"lessonId": "X", "courseName": "TestNoWeek",
               "teacherNames": ["T"], "weekday": 1, "weekIndexes": "",
               "startUnit": 1, "endUnit": 2}
            ]
          }]
        }
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("空 weekIndexes 兜底 1", 1, c.startWeek)
    }

    // -------- parseWeekIndex 边界 (weekIndexes bitmap 单/双周标记) --------
    // 5 仓 cross-verified 2026-09-06: 单周标 '单', 双周标 '双', 'odd'/'even' 兜底

    // -------- parseWeekRanges 完整 RLE 解析 (5 仓 bitmap + JwParity 算法) --------
    // Sleepy type 语义 (JwParity.kt 共识): 0=每周 1=单周 2=双周
    // 算法: 5 仓 cross-validated bitmap 形态 + JwSeuParser.parseWeekRanges 同型

    @Test
    fun `parseWeekRanges - 1-16单 emit 单周 type=1 JwParity 端点修正`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T单", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16单", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        // JwParity.adjustedRange(1, 16, parity=1): startWeek=1 (奇数 ✓), endWeek=16 (不做收缩, 连续显示)
        assertEquals("1-16单 startWeek=1", 1, c.startWeek)
        assertEquals("1-16单 endWeek=16 (JwParity 不收缩 endWeek)", 16, c.endWeek)
        assertEquals("1-16单 type=1 (Sleepy 单周)", 1, c.type)
    }

    @Test
    fun `parseWeekRanges - 2-15双 emit 双周 type=2 JwParity 端点修正`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T双", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "2-15双", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        // startWeek=2 (偶数 ✓), endWeek=15 (JwParity 不收缩, UI 连续显示)
        assertEquals("2-15双 startWeek=2", 2, c.startWeek)
        assertEquals("2-15双 endWeek=15 (JwParity 不收缩)", 15, c.endWeek)
        assertEquals("2-15双 type=2 (Sleepy 双周)", 2, c.type)
    }

    @Test
    fun `parseWeekRanges - 1-16 全周 type=0`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T全", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("1-16 startWeek=1", 1, c.startWeek)
        assertEquals("1-16 endWeek=16", 16, c.endWeek)
        assertEquals("1-16 type=0 (全周)", 0, c.type)
    }

    @Test
    fun `parseWeekRanges - 1-16双 startWeek 抬到首个偶数`() {
        // 1 是奇数, 双周起点必须是偶数, JwParity 抬到 2
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T双起", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16双", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("1-16双 startWeek=2 (JwParity 抬到首个偶数)", 2, c.startWeek)
        assertEquals("1-16双 type=2", 2, c.type)
    }

    @Test
    fun `parseWeekRanges - 1-16单 startWeek 保持 1 (本身就是奇数)`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T单起", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16单", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("1-16单 startWeek=1 (本身就是奇数)", 1, c.startWeek)
    }

    @Test
    fun `parseWeekRanges - 8,10,12,14 离散 emit 多 JwCourse`() {
        // 离散 4 段, 每段独立 type=0
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T离", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "8,10,12,14", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val cs = p.generateCourseList()
        assertEquals("4 段离散 emit 4 个 JwCourse", 4, cs.size)
        assertEquals("第 1 段 startWeek=8", 8, cs[0].startWeek)
        assertEquals("第 2 段 startWeek=10", 10, cs[1].startWeek)
        assertEquals("第 3 段 startWeek=12", 12, cs[2].startWeek)
        assertEquals("第 4 段 startWeek=14", 14, cs[3].startWeek)
        cs.forEach { assertEquals("离散 type=0", 0, it.type) }
    }

    @Test
    fun `parseWeekRanges - 1-8,10-16 组合区间 emit 2 个 JwCourse`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T组", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-8,10-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val cs = p.generateCourseList()
        assertEquals("2 段 emit 2 个 JwCourse", 2, cs.size)
        assertEquals("段 1 startWeek=1 endWeek=8", Triple(1, 8, 0), Triple(cs[0].startWeek, cs[0].endWeek, cs[0].type))
        assertEquals("段 2 startWeek=10 endWeek=16", Triple(10, 16, 0), Triple(cs[1].startWeek, cs[1].endWeek, cs[1].type))
    }

    @Test
    fun `parseWeekRanges - 1-16单 各 emit 单 JwCourse 端点修正`() {
        // 端点相等场景: 5-5单 → startWeek=5, endWeek=5 (JwParity 防倒挂)
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T单端", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "5-5单", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("5-5单 startWeek=5", 5, c.startWeek)
        assertEquals("5-5单 endWeek=5 (无倒挂)", 5, c.endWeek)
        assertEquals("5-5单 type=1", 1, c.type)
    }

    @Test
    fun `parseWeekRanges - 6-6双 JwParity 端点相等时抬 start 到 6`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T双端", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "6-6双", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        // startWeek=6 (偶数 ✓), endWeek=6 (JwParity max(6,6)=6)
        assertEquals("6-6双 startWeek=6", 6, c.startWeek)
        assertEquals("6-6双 endWeek=6 (无倒挂)", 6, c.endWeek)
        assertEquals("6-6双 type=2", 2, c.type)
    }

    @Test
    fun `parseWeekRanges - even 英文兼容 type=2`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Teven", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16 even", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("even → type=2", 2, c.type)
    }

    @Test
    fun `parseWeekRanges - odd 英文兼容 type=1`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Todd", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-16 odd", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("odd → type=1", 1, c.type)
    }

    @Test
    fun `parseWeekRanges - 5-12 区间基本`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T区", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "5-12", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("5-12 startWeek=5", 5, c.startWeek)
        assertEquals("5-12 endWeek=12", 12, c.endWeek)
    }

    @Test
    fun `parseWeekRanges - 单值 5周 emit startWeek=endWeek=5`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T单值", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "5周", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("5周 startWeek=5", 5, c.startWeek)
        assertEquals("5周 endWeek=5", 5, c.endWeek)
    }

    @Test
    fun `parseWeekRanges - 混合 1-8周(单),9-16周(双) 每段独立判定`() {
        // SEU 同型 bug fix: 不能整串 first-match, 必须每段独立
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T混", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "1-8周(单),9-16周(双)", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val cs = p.generateCourseList()
        assertEquals("2 段 emit 2 个 JwCourse", 2, cs.size)
        // 段 1: 单周 → startWeek=1, endWeek=7 (1,3,5,7)
        assertEquals("段 1 (单周) startWeek=1", 1, cs[0].startWeek)
        assertEquals("段 1 (单周) type=1", 1, cs[0].type)
        // 段 2: 双周 → startWeek=10 (JwParity 抬到首个偶数), endWeek=16 (10,12,14,16)
        assertEquals("段 2 (双周) startWeek=10 (JwParity 抬)", 10, cs[1].startWeek)
        assertEquals("段 2 (双周) type=2", 2, cs[1].type)
    }

    @Test
    fun `parseWeekRanges - 空 bitmap 兜底 (1,1,0)`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "T空", "teacherNames": ["t"],
           "weekday": 1, "weekIndexes": "", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        val c = p.generateCourseList().single()
        assertEquals("空 bitmap startWeek=1", 1, c.startWeek)
        assertEquals("空 bitmap endWeek=1", 1, c.endWeek)
        assertEquals("空 bitmap type=0", 0, c.type)
    }

    // -------- teacher 字段多形态 fallback (5 仓 teacherNames + teachers + teacher + personName) --------

    @Test
    fun `AHU print-data - teacherNames 数组 join 斜杠`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test", "teacherNames": ["张三", "李四"],
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("张三/李四", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - teachers 数组 fallback`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test", "teachers": ["甲", "乙", "丙"],
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("甲/乙/丙", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - teacherList 数组 fallback`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test", "teacherList": ["王老师"],
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("王老师", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - 单字符串 teacher fallback`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test", "teacher": "外教",
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("外教", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - personName 兜底 (HFUT 形态字段)`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test", "personName": "兜底教师",
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("兜底教师", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - 全部缺 teacher 字段 emit 空串`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "无师课",
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("无 teacher 应输出空串", "", p.generateCourseList().single().teacher)
    }

    @Test
    fun `AHU print-data - 空数组 teacherNames 兜底到单字符串`() {
        val p = JwEams5Parser("""
        {"studentTableVms": [{"activities": [
          {"lessonId": "A", "courseName": "Test",
           "teacherNames": [], "teacher": "降级到单字符串",
           "weekday": 1, "weekIndexes": "1-16", "startUnit": 1, "endUnit": 2}
        ]}]}
        """.trimIndent())
        assertEquals("空数组降级到 teacher 单字符串", "降级到单字符串",
            p.generateCourseList().single().teacher)
    }
}
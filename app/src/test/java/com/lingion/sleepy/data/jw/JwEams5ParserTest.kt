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
    // 来源: MoeclubM/AHU-AIO (Dart) + qiqqqqq517/shangkeschschedule (Apache-2.0) ahu.js
    //   GET /student/for-std/course-table/get-data?bizTypeId=2&semesterId=<id>&dataId=
    //   resp: {"code":200,"data":{"lessons":[…]}}
    // 字段契约:
    //   lesson.lessonId/courseName/teacher/room (扁平字符串, 与 HFUT 双层结构不同)
    //   lesson.weekday/weekIndex/startTime/endTime/periods (与 HFUT 同构)

    @Test
    fun `parses AHU get-data - total count equals lessons rows`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        assertNotNull("测试资源 get-data-ahu.sample.json 应存在", stream)
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        // 5 个 lessons 行 → 5 个 JwCourse
        assertEquals("5 个 lessons 行应产出 5 个 JwCourse", 5, courses.size)
    }

    @Test
    fun `parses AHU get-data - lesson 1001 高数 maps to correct fields`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "高等数学" }
        assertNotNull("应找到 高等数学", c)
        c!!
        assertEquals("周1", 1, c.day)
        assertEquals("startNode (08:00 → node1)", 1, c.startNode)
        assertEquals("endNode (08:50 → node2)", 2, c.endNode)
        assertEquals("startWeek=weekIndex", 1, c.startWeek)
        assertEquals("endWeek=weekIndex (v1 单周)", 1, c.endWeek)
        assertEquals("teacher 张教授", "张教授", c.teacher)
        assertEquals("room 博学南楼A101", "博学南楼A101", c.room)
    }

    @Test
    fun `parses AHU get-data - weekday mapping across week`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        // 1003 数据结构 weekday=3 周三
        val ds = courses.firstOrNull { it.name == "数据结构" }
        assertNotNull(ds)
        ds!!
        assertEquals("数据结构 周三", 3, ds.day)
        // startTime=1400 → node5 (下午第一段)
        assertEquals("数据结构 startNode (14:00 → node5)", 5, ds.startNode)
        assertEquals("数据结构 endNode (15:50 → node6)", 6, ds.endNode)
        assertEquals("数据结构 startWeek=5", 5, ds.startWeek)
    }

    @Test
    fun `parses AHU get-data - 1005 形势与政策 晚上段`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val courses = JwEams5Parser(json).generateCourseList()
        val c = courses.firstOrNull { it.name == "形势与政策" }
        assertNotNull(c)
        c!!
        // startTime=1900 → node9 (晚上段)
        assertEquals("形势与政策 startNode (19:00 → node9)", 9, c.startNode)
        assertEquals("形势与政策 endNode (20:50 → node10)", 10, c.endNode)
        assertEquals("形势与政策 周五", 5, c.day)
    }

    @Test
    fun `AHU get-data confidence matches HFUT`() {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/eams5/get-data-ahu.sample.json")
        val json = stream!!.bufferedReader().use { it.readText() }
        val p = JwEams5Parser(json)
        assertEquals("AHU data.lessons[] confidence == HFUT 双层 confidence", 95, p.confidence())
        val feats = p.matchedFeatures()
        assertTrue("应含 AHU 锚点 data.lessons", feats.any { it.contains("AHU") || it.contains("data.lessons") })
    }

    @Test
    fun `AHU shape precedence over HFUT shape`() {
        // data.lessons[] 优先 result.lessonList[] (安大形态必须先命中, 避免 HFUT fallback 误抢)
        // fixture 同时含两层 (data.lessons 3 课 + result.scheduleList 1 课, 假装冲突响应)
        val p = JwEams5Parser("""
        {
          "code": 200,
          "data": { "lessons": [
            {"lessonId": 1, "courseName": "AHU优先课", "teacher": "T", "room": "R",
             "weekday": 1, "weekIndex": 1, "startTime": 800, "endTime": 950, "periods": 2}
          ]},
          "result": { "lessonList": [{"id":"99","courseName":"HFUT抢课"}],
                      "scheduleList": [{"lessonId":99, "weekday":1, "weekIndex":1,
                                        "startTime":800, "endTime":950, "periods":2}]}
        }
        """.trimIndent())
        val courses = p.generateCourseList()
        assertEquals("只产 1 课 (AHU 形态优先)", 1, courses.size)
        assertEquals("AHU优先课", courses[0].name)
    }
}
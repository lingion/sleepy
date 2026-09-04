package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JwScuParserTest {

    private fun loadJson(): String =
        File("src/test/resources/jw/fixtures/scu/courses.sample.json").readText(Charsets.UTF_8)

    @Test
    fun `parses SCU courses - total count is correct`() {
        val courses = JwScuParser(loadJson()).generateCourseList()
        // 4 timeAndPlace entries:
        //   1-16周 (连续)     → 1 entry
        //   2-15周 (连续)     → 1 entry
        //   2,4,6,8,10,12,14,16 (离散8周) → 8 entries
        //   1-16周 (连续)     → 1 entry
        // 总计 1+1+8+1 = 11
        assertEquals(11, courses.size)
    }

    @Test
    fun `parses SCU courses - 高等数学 maps to correct fields`() {
        val math = JwScuParser(loadJson()).generateCourseList().first { it.name == "高等数学" }
        // raw classDay=0 → day=1 (Monday)
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)  // start=1, continuing=2, endNode = 1+2-1 = 2
        assertEquals("王教授", math.teacher)
        // room: teachingBuildingName + classroomName = 江安校区一教A楼301
        assertEquals("江安校区一教A楼301", math.room)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals(0, math.type)
    }

    @Test
    fun `parses SCU courses - 大学物理 day mapping (raw 2 to 3)`() {
        val phys = JwScuParser(loadJson()).generateCourseList().first { it.name == "大学物理" }
        assertEquals(3, phys.day)
        assertEquals(5, phys.startNode)
        assertEquals(6, phys.endNode)
        assertEquals(2, phys.startWeek)
        assertEquals(15, phys.endWeek)
    }

    @Test
    fun `parses SCU courses - 离散周 2,4,6,8,10,12,14,16 expands to eight entries`() {
        val lab = JwScuParser(loadJson()).generateCourseList().filter { it.name == "数据结构实验" }
        // 离散周 (按 Sleepy 约定) 每周 1 entry
        assertEquals(8, lab.size)
        assertEquals(5, lab.first().day)
        assertEquals(7, lab.first().startNode)
        val weeks = lab.map { it.startWeek }.sorted()
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), weeks)
        // 每个 entry 是单周范围 (startWeek=endWeek=week)
        assertTrue(lab.all { it.startWeek == it.endWeek })
    }

    @Test
    fun `parses SCU courses - 英语口语 weekday 2 nodes 9-10`() {
        val eng = JwScuParser(loadJson()).generateCourseList().first { it.name == "英语口语" }
        assertEquals(2, eng.day)  // raw 1 → 2
        assertEquals(9, eng.startNode)
        assertEquals(10, eng.endNode)
        assertEquals("望江校区外语楼501", eng.room)
    }

    @Test
    fun `parses SCU courses - weekDescription single week marker kept as type 1`() {
        // weekDescription 真实含 "3-9周单" 形态 (上游 timetable.json 资产实证);
        // 单/双信息在线协议并未丢失 — 保留 type 并做端点修正
        val json = """
        {
          "dateList": [{"selectCourseList": [{
            "attendClassTeacher": "X",
            "courseName": "单周测试",
            "examTypeName": "",
            "coursePropertiesName": "",
            "courseCategoryName": "",
            "restrictedCondition": "",
            "programPlanName": "",
            "studyModeName": "",
            "unit": 0,
            "timeAndPlaceList": [{
              "classroomName": "A101",
              "teachingBuildingName": "一教",
              "weekDescription": "2-15周单",
              "classSessions": 1,
              "continuingSession": 2,
              "classDay": 0,
              "campusName": "望江",
              "coureNumber": "X001",
              "coureSequenceNumber": "01",
              "executiveEducationPlanNumber": "E"
            }]
          }]}]
        }
        """.trimIndent()
        val courses = JwScuParser(json).generateCourseList()
        assertEquals(1, courses.size)
        // 单周=奇数, 起点 2 偶 → 校正 3
        assertEquals(3, courses.first().startWeek)
        assertEquals(15, courses.first().endWeek)
        assertEquals(1, courses.first().type)
    }

    @Test
    fun `parses SCU courses - weekDescription double week paren form kept as type 2`() {
        val json = """
        {
          "dateList": [{"selectCourseList": [{
            "attendClassTeacher": "X",
            "courseName": "双周测试",
            "examTypeName": "",
            "coursePropertiesName": "",
            "courseCategoryName": "",
            "restrictedCondition": "",
            "programPlanName": "",
            "studyModeName": "",
            "unit": 0,
            "timeAndPlaceList": [{
              "classroomName": "B202",
              "teachingBuildingName": "二教",
              "weekDescription": "1-16周（双）",
              "classSessions": 1,
              "continuingSession": 2,
              "classDay": 1,
              "campusName": "望江",
              "coureNumber": "X002",
              "coureSequenceNumber": "01",
              "executiveEducationPlanNumber": "E"
            }]
          }]}]
        }
        """.trimIndent()
        val courses = JwScuParser(json).generateCourseList()
        assertEquals(1, courses.size)
        // 双周=偶数, 起点 1 奇 → 校正 2
        assertEquals(2, courses.first().startWeek)
        assertEquals(16, courses.first().endWeek)
        assertEquals(2, courses.first().type)
    }

    @Test
    fun `parses SCU courses - confidence is high when dateList anchor present`() {
        val parser = JwScuParser(loadJson())
        assertTrue("confidence must be >= 80, was ${parser.confidence()}",
            parser.confidence() >= 80)
    }

    @Test
    fun `parses SCU courses - confidence is zero when dateList missing`() {
        val parser = JwScuParser("""{"other":[]}""")
        assertEquals(0, parser.confidence())
    }

    @Test
    fun `parses SCU courses - matchedFeatures lists selectCourseList anchor`() {
        val features = JwScuParser(loadJson()).matchedFeatures()
        assertTrue("must contain selectCourseList anchor, was $features",
            features.any { it.contains("selectCourseList", ignoreCase = true) })
    }
}
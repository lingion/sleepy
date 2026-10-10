package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * 小爱课程表 presetData 契约 — 按官方 H5 (umi.js) 验证器逐条锁死:
 *
 * - courseInfos[].weeks / sections 必须是 int 数组 (验证器 d(): Array.isArray,
 *   字符串 errCode 4 直接拒收; 预览渲染 sections.filter 亦要求数组)
 * - weeks 值域 [1,30] 过滤 · day [1,7] · name ≤50 字节
 * - timerRes: totalWeek [1,30] · startSemester 13 位字符串 · forenoon [1,10] ·
 *   afternoon/night [0,10] · 三段之和 === sections.length (P 验证器)
 * - 时段边界: startTime <13:00 上午 · <18:00 下午 · 其余晚间 (f 验证器)
 * - 深链: voiceassist://aiweb/?source=&flag=268468224&url=<enc>&presetData=<enc>
 */
class XiaoaiPresetDataTest {

    private fun table() = TimeTableEntity(
        id = 1, name = "TestUni", startDate = "2026-09-07",
        maxWeek = 18, nodesPerDay = 12,
        timeJson = DEFAULT_TIME_JSON,
    )

    private val DEFAULT_TIME_JSON = """
        [{"node":1,"start":"08:00","end":"08:45"},
         {"node":2,"start":"08:55","end":"09:40"},
         {"node":3,"start":"10:00","end":"10:45"},
         {"node":4,"start":"10:55","end":"11:40"},
         {"node":5,"start":"12:40","end":"13:25"},
         {"node":6,"start":"13:35","end":"14:20"},
         {"node":7,"start":"14:40","end":"15:25"},
         {"node":8,"start":"15:35","end":"16:20"},
         {"node":9,"start":"18:30","end":"19:15"},
         {"node":10,"start":"19:25","end":"20:10"}]
    """.trimIndent()

    private fun course(
        name: String = "高等数学",
        day: Int = 1,
        startNode: Int = 1,
        step: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
        type: Int = 0,
    ) = CourseEntity(
        id = 0, groupId = "", tableId = 1,
        courseName = name, teacher = "张三", room = "A101",
        day = day, startNode = startNode, step = step,
        startWeek = startWeek, endWeek = endWeek, type = type,
        color = "#FF6750A4",
    )

    private fun parseImportData(presetData: String): JsonObject {
        val outer = Json.parseToJsonElement(presetData).jsonObject
        return Json.parseToJsonElement(outer["importData"]!!.jsonPrimitive.content).jsonObject
    }

    @Test
    fun weeks_isIntArray_notCommaString() {
        val infos = XiaoaiPresetData.buildCourseInfos(listOf(course()), DEFAULT_TIME_JSON)
        val c = infos[0].jsonObject
        val weeks = c["weeks"]!!
        assertTrue("weeks 必须是数组(官方 d() 验证器 Array.isArray), got: $weeks", weeks is JsonArray)
        val values = weeks.jsonArray.map { it.jsonPrimitive.content.toInt() }
        assertEquals((1..16).toList(), values)
    }

    @Test
    fun sections_isIntArray_andExpandsStep() {
        val infos = XiaoaiPresetData.buildCourseInfos(listOf(course(startNode = 3, step = 3)), DEFAULT_TIME_JSON)
        val sections = infos[0].jsonObject["sections"]!!
        assertTrue("sections 必须是数组", sections is JsonArray)
        assertEquals(listOf(3, 4, 5), sections.jsonArray.map { it.jsonPrimitive.content.toInt() })
    }

    @Test
    fun oddWeeks_type1_expandsToOddNumbersOnly() {
        val odd = XiaoaiPresetData.expandWeeks(course(type = 1))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), odd)
    }

    @Test
    fun evenWeeks_type2_expandsToEvenNumbersOnly() {
        val even = XiaoaiPresetData.expandWeeks(course(type = 2))
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), even)
    }

    @Test
    fun presetData_shape_isV2_parserRes_timerRes() {
        val importData = parseImportData(
            XiaoaiPresetData.build(table(), listOf(course()), nowMillis = 1_700_000_000_000)
        )
        assertEquals(true, importData["isV2"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("0", importData["errorCode"]!!.jsonPrimitive.content)
        assertEquals("TestUni", importData["schoolName"]!!.jsonPrimitive.content)
        assertTrue(importData["parserRes"] is JsonObject)
        assertTrue(importData["timerRes"] is JsonObject)
        assertEquals("1700000000000", importData["t"]!!.jsonPrimitive.content)
    }

    @Test
    fun timerRes_totalWeekAndStartSemester13Digits() {
        val importData = parseImportData(XiaoaiPresetData.build(table(), listOf(course())))
        val timer = importData["timerRes"]!!.jsonObject
        assertEquals("18", timer["totalWeek"]!!.jsonPrimitive.content)
        val startSemester = timer["startSemester"]!!.jsonPrimitive.content
        assertEquals("startSemester 必须是 13 位毫秒时间戳", 13, startSemester.length)
        // 2026-09-07 00:00 UTC+8 = 1788710400000
        assertEquals("1788710400000", startSemester)
    }

    @Test
    fun timerRes_sectionSum_equalsForenoonAfternoonNight() {
        val importData = parseImportData(XiaoaiPresetData.build(table(), listOf(course())))
        val timer = importData["timerRes"]!!.jsonObject
        val forenoon = timer["forenoon"]!!.jsonPrimitive.content.toInt()
        val afternoon = timer["afternoon"]!!.jsonPrimitive.content.toInt()
        val night = timer["night"]!!.jsonPrimitive.content.toInt()
        val sections = timer["sections"]!!.jsonArray
        assertEquals(
            "官方 P 验证器: forenoon+afternoon+night 必须 === sections.length",
            sections.size, forenoon + afternoon + night
        )
        // DEFAULT_TIME_JSON: <13:00 上午 4 节(1-4), <18:00 下午 4 节(5-8), 晚间 2 节(9-10)
        assertEquals(4, forenoon)
        assertEquals(4, afternoon)
        assertEquals(2, night)
    }

    @Test
    fun timerRes_sections_areSectionStartTimeEndTime() {
        val importData = parseImportData(XiaoaiPresetData.build(table(), listOf(course())))
        val sections = importData["timerRes"]!!.jsonObject["sections"]!!.jsonArray
        val first = sections[0].jsonObject
        assertEquals("1", first["section"]!!.jsonPrimitive.content)
        assertEquals("08:00", first["startTime"]!!.jsonPrimitive.content)
        assertEquals("08:45", first["endTime"]!!.jsonPrimitive.content)
    }

    @Test
    fun deepLink_schemeAndParams() {
        val presetData = XiaoaiPresetData.build(table(), listOf(course()))
        val link = XiaoaiPresetData.deepLink(presetData)
        assertTrue("scheme 必须是 voiceassist://aiweb", link.startsWith("voiceassist://aiweb/?"))
        assertTrue("flag 必须是 268468224", link.contains("flag=268468224"))
        assertTrue("url 参数必须是 encode 过的 H5 地址", link.contains("url=https%3A%2F%2Fi.ai.mi.com"))
        assertTrue("presetData 参数必须存在", link.contains("presetData=%7B%22importData%22"))
        // presetData 参数可解码回原文
        val enc = Regex("presetData=([^&]+)").find(link)!!.groupValues[1]
        assertEquals(presetData, java.net.URLDecoder.decode(enc, "UTF-8"))
    }

    @Test
    fun ownTimeCourse_isNormalizedIntoSections() {
        // timeToNode: 起点吸附到 start<=20:50 的最后一节 = 第10节(19:25), 无更晚节 → 单节 [10]
        val ownTime = course(name = "晚课", startNode = 9, step = 2).copy(
            ownTime = true, startTime = "20:50", endTime = "22:00"
        )
        val infos = XiaoaiPresetData.buildCourseInfos(listOf(ownTime), DEFAULT_TIME_JSON)
        val sections = infos[0].jsonObject["sections"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
        assertEquals(listOf(10), sections)
    }

    @Test
    fun emptyCourses_produceEmptyArray() {
        val infos = XiaoaiPresetData.buildCourseInfos(emptyList(), DEFAULT_TIME_JSON)
        assertEquals(0, infos.size)
    }

    @Test
    fun timerSections_acceptsLegacyNodeStartKeyShape() {
        // 真实 DB 并存的历史键形 {nodeStart,startTime,endTime} — 模拟器实测抓到
        val legacy = """
            [{"nodeStart":1,"startTime":"08:00","endTime":"08:45"},
             {"nodeStart":2,"startTime":"08:50","endTime":"09:35"}]
        """.trimIndent()
        val sections = XiaoaiPresetData.parseTimerSections(legacy)
        assertEquals(2, sections.size)
        assertEquals(Triple(1, "08:00", "08:45"), sections[0])
        assertEquals(Triple(2, "08:50", "09:35"), sections[1])
    }
}

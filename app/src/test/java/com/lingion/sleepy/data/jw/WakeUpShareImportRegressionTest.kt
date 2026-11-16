package com.lingion.sleepy.data.jw

import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Server response captured on 2026-11-16; identifiers and personal text are anonymized. */
class WakeUpShareImportRegressionTest {
    private fun parts(): WakeUpShareProtocol.ShareParts {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("wakeup/share-five-segments-sanitized.txt"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return WakeUpShareProtocol.parseRawScheduleData(text)
    }
    private fun parse(parts: WakeUpShareProtocol.ShareParts) =
        ScheduleParser.parse(WakeUpShareClient.buildScheduleJson(parts), 42).getOrThrow()

    @Test fun allThirtyFiveArrangementsKeepTheirOriginalFields() {
        val source = parts()
        val parsed = parse(source)
        assertEquals(35, parsed.courses.size)
        parsed.courses.zip(source.courseDetailRaw).forEach { (course, raw) ->
            val detail = raw.jsonObject
            val id = detail.getValue("id").jsonPrimitive.content
            assertEquals("Course $id", course.courseName)
            assertEquals("Teacher $id", course.teacher)
            assertEquals("Room $id", course.room)
            assertEquals(detail.getValue("day").jsonPrimitive.int, course.day)
            assertEquals(detail.getValue("startNode").jsonPrimitive.int, course.startNode)
            assertEquals(detail.getValue("step").jsonPrimitive.int, course.step)
            assertEquals(detail.getValue("startWeek").jsonPrimitive.int, course.startWeek)
            assertEquals(detail.getValue("endWeek").jsonPrimitive.int, course.endWeek)
            assertEquals(detail.getValue("type").jsonPrimitive.int, course.type)
        }
        val experiment = parsed.courses.single { it.startNode + it.step - 1 == 13 }
        assertEquals(10, experiment.startNode)
        assertEquals(4, experiment.step)
        assertEquals(13, experiment.startWeek)
        assertEquals(16, experiment.endWeek)
    }

    @Test fun allSelectedSourceClocksArePreservedIncludingUnusedTailForReview() {
        val source = parts()
        val parsed = parse(source)
        val rows = TimeTableUtils.parseTimeSlotRows(parsed.timeJson)
        assertEquals(20, parsed.nodesPerDay)
        assertEquals((1..20).toList(), rows.map { it.node })
        rows.zip(source.timeSlotsRaw).forEach { (row, raw) ->
            assertEquals(raw.jsonObject.getValue("startTime").jsonPrimitive.content, row.start)
            assertEquals(raw.jsonObject.getValue("endTime").jsonPrimitive.content, row.end)
        }
        val confirmed = TimeTableUtils.mergeMostComplete("", parsed.timeJson, parsed.nodesPerDay)
        assertEquals(rows, TimeTableUtils.parseTimeSlotRows(confirmed))
        assertEquals("21:35", rows[12].start)
        assertEquals("22:20", rows[12].end)
        assertEquals(TimeTableUtils.PeriodValidationResult.Overlap(13, "22:20", 14, "21:45"),
            TimeTableUtils.validatePeriodRows(rows))
    }

    @Test fun declaredNameAndSemesterLengthSurviveTheOnlineAndJsonParserChain() {
        val source = parts()
        val updated = source.copy(uiConfig = JsonObject(source.uiConfig + ("maxWeek" to JsonPrimitive(19))))
        val parsed = parse(updated)
        assertEquals("WakeUp source term", parsed.tableName)
        assertEquals(19, parsed.maxWeek)
    }

    @Test fun ownClockAndCourseNoteSurviveTheOnlineParserChain() {
        val source = parts()
        val first = source.courseDetailRaw.first().jsonObject
        val changed = JsonObject(first + mapOf(
            "ownTime" to JsonPrimitive(true), "startTime" to JsonPrimitive("18:40"),
            "endTime" to JsonPrimitive("20:15"), "courseNote" to JsonPrimitive("bring equipment"),
        ))
        val parsed = parse(source.copy(courseDetailRaw = JsonArray(listOf(changed))))
        assertEquals(1, parsed.courses.size)
        assertTrue(parsed.courses.single().ownTime)
        assertEquals("18:40", parsed.courses.single().startTime)
        assertEquals("20:15", parsed.courses.single().endTime)
        assertEquals("bring equipment", parsed.courses.single().note)
    }

    @Test fun bothWakeUpJsonAndEncodedShareTextKeepDeclaredSemesterLength() {
        val body = """{"name":"term","tableInfo":{"maxWeek":19},"courses":[{"name":"lab","day":2,"startNode":10,"step":4,"startWeek":13,"endWeek":16,"type":0}]}"""
        assertEquals(19, ScheduleParser.parse(body, 42).getOrThrow().maxWeek)
        val courseJson = Json.parseToJsonElement(body).jsonObject.getValue("courses").toString()
        val shared = buildJsonObject {
            put("name", "term")
            put("tableInfo", buildJsonObject { put("maxWeek", 19) })
            put("courseDetailJson", java.net.URLEncoder.encode(courseJson, "UTF-8"))
        }
        assertEquals(19, ScheduleParser.parse(shared.toString(), 42).getOrThrow().maxWeek)
    }

    @Test fun inactiveOwnClockFieldsDoNotChangePeriodBasedCourses() {
        val source = parts()
        val detail = JsonObject(source.courseDetailRaw.first().jsonObject + mapOf(
            "ownTime" to JsonPrimitive(false), "startTime" to JsonPrimitive("18:40"),
            "endTime" to JsonPrimitive("20:15"),
        ))
        val parsed = parse(source.copy(courseDetailRaw = JsonArray(listOf(detail))))
        assertFalse(parsed.courses.single().ownTime)
        assertEquals("", parsed.courses.single().startTime)
        assertEquals("", parsed.courses.single().endTime)
    }
}

package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic interface fixtures from DanXi-Dev/DanXi 3c1f5f026fbc7ca50e151865f813cdb381807abe. */
class JwFduCrossRepositoryTest {
    @Test fun undergraduateFieldsKeepExplicitUnitsTeachersRoomAndEveryWeek() {
        val source = undergraduate(activity(mapOf(
            "startUnit" to JsonPrimitive(101), "endUnit" to JsonPrimitive(103),
            "weekIndexes" to array(2, 5),
        )))
        assertEquals(listOf(course(1, 101, 103, 2), course(1, 101, 103, 5)), parse(source))
        assertEquals(100, JwFduParser(source).confidence())
        assertEquals(listOf("fdu-undergraduate-print-data"), JwFduParser(source).matchedFeatures())
    }

    @Test fun undergraduateWeekdaysAndPeriodsAlreadyStartAtOne() {
        for (day in 1..6) assertEquals("weekday=$day", listOf(course(day, 1, 1, 1)),
            parse(undergraduate(activity(mapOf("weekday" to JsonPrimitive(day),
                "startUnit" to JsonPrimitive(1), "endUnit" to JsonPrimitive(1), "weekIndexes" to array(1))))))
    }

    @Test fun onlyUndergraduateSundayMovesToThePreviousMondayBasedWeek() {
        assertEquals(listOf(course(7, 3, 4, 1), course(7, 3, 4, 3)),
            parse(undergraduate(activity(mapOf("weekday" to JsonPrimitive(7), "weekIndexes" to array(1, 2, 4))))))
        assertEquals(emptyList<JwCourse>(), parse(undergraduate(activity(mapOf(
            "weekday" to JsonPrimitive(7), "weekIndexes" to array(1))))))
    }

    @Test fun undergraduateDisjointArrangementsDoNotFillMissingPeriodsOrWeeks() {
        val source = undergraduate(
            activity(mapOf("startUnit" to JsonPrimitive(1), "endUnit" to JsonPrimitive(2), "weekIndexes" to array(7, 1, 7))),
            activity(mapOf("startUnit" to JsonPrimitive(5), "endUnit" to JsonPrimitive(6), "weekIndexes" to array(3))),
        )
        assertEquals(listOf(course(1, 1, 2, 1), course(1, 1, 2, 7), course(1, 5, 6, 3)), parse(source))
    }

    @Test fun undergraduateUsesOnlyTheFirstStudentTableIncludingAnEmptyFirstTable() {
        val first = "{\"activities\":[${activity()}]}"
        val second = "{\"activities\":[${activity(mapOf("courseName" to JsonPrimitive("其他课表")))}]}"
        assertEquals(listOf(course(1, 3, 4, 1), course(1, 3, 4, 3)), parse("{\"studentTableVms\":[$first,$second]}"))
        assertEquals(emptyList<JwCourse>(), parse("{\"studentTableVms\":[{\"activities\":[]},$second]}"))
        assertEquals(emptyList<JwCourse>(), parse("{\"studentTableVms\":[{},$second]}"))
    }

    @Test fun undergraduateMalformedCoordinatesDoNotRemoveAnIndependentGoodActivity() {
        val bad = listOf(
            mapOf("weekday" to JsonPrimitive(0)), mapOf("weekday" to JsonPrimitive(8)),
            mapOf("weekday" to JsonPrimitive("1")), mapOf("startUnit" to JsonPrimitive(0)),
            mapOf("weekday" to JsonPrimitive(true)), mapOf("startUnit" to JsonPrimitive(1.0)),
            mapOf("startUnit" to JsonPrimitive(5)), mapOf("startUnit" to JsonPrimitive(1.5)),
            mapOf("endUnit" to JsonPrimitive(367)), mapOf("endUnit" to JsonPrimitive(2147483648L)),
            mapOf("endUnit" to JsonNull), mapOf("endUnit" to JsonPrimitive("4")),
            mapOf("weekIndexes" to array(0, 3)), mapOf("weekIndexes" to array(1, 367)),
            mapOf("weekIndexes" to JsonPrimitive("1-3")), mapOf("weekIndexes" to JsonNull),
            mapOf("weekIndexes" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("3")))),
            mapOf("weekIndexes" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2.5)))),
        )
        for (fields in bad) assertEquals(fields.toString(), listOf(course(1, 3, 4, 1), course(1, 3, 4, 3)),
            parse(undergraduate(activity(fields + ("courseName" to JsonPrimitive("坏安排"))), activity())))
    }

    @Test fun undergraduateRequiresExplicitEndpointsAndAWeekArray() {
        for (missing in listOf("weekday", "startUnit", "endUnit", "weekIndexes")) {
            assertEquals(missing, emptyList<JwCourse>(), parse(undergraduate(JsonObject(activity().filterKeys { it != missing }))))
        }
        assertEquals(emptyList<JwCourse>(), parse(undergraduate(activity(mapOf("weekIndexes" to array())))))
    }

    @Test fun undergraduateMetadataNeverStringifiesObjectsOrInventsAbsentValues() {
        for (fields in listOf(
            mapOf("courseName" to JsonPrimitive(" ")), mapOf("courseName" to JsonObject(emptyMap())),
            mapOf("teachers" to JsonPrimitive("张老师")), mapOf("teachers" to JsonArray(listOf(JsonObject(emptyMap())))),
            mapOf("room" to JsonObject(emptyMap())),
        )) assertEquals(fields.toString(), emptyList<JwCourse>(), parse(undergraduate(activity(fields))))
        val source = undergraduate(activity(mapOf("teachers" to JsonNull, "room" to JsonNull)))
        assertEquals(listOf(course(1, 3, 4, 1).copy(room = "", teacher = ""),
            course(1, 3, 4, 3).copy(room = "", teacher = "")), parse(source))
    }

    @Test fun explicitAllocationBoundaryDoesNotUseTheDefaultClockLength() {
        assertEquals(listOf(course(1, 366, 366, 366)), parse(undergraduate(activity(mapOf(
            "startUnit" to JsonPrimitive(366), "endUnit" to JsonPrimitive(366), "weekIndexes" to array(366))))))
        assertEquals(listOf(pgCourse(7, 366, 366)), parse(postgraduate(pg(mapOf(
            "XQ" to JsonPrimitive(7), "KSJCDM" to JsonPrimitive(366), "ZCBH" to JsonPrimitive("0".repeat(365) + "1"))))))
    }

    @Test fun postgraduateBitmapStartsAtWeekOneAndKsjcdmIsOneExplicitNode() {
        val source = postgraduate(pg(mapOf("XQ" to JsonPrimitive(7), "KSJCDM" to JsonPrimitive(5),
            "ZCBH" to JsonPrimitive("10101"), "JSJCDM" to JsonPrimitive(9))))
        assertEquals(listOf(pgCourse(7, 5, 1), pgCourse(7, 5, 3), pgCourse(7, 5, 5)), parse(source))
        assertEquals(100, JwFduParser(source).confidence())
        assertEquals(listOf("fdu-postgraduate-results"), JwFduParser(source).matchedFeatures())
    }

    @Test fun postgraduateDisjointRecordsAndNullableMetadataStayIndependent() {
        val source = postgraduate(pg(mapOf("KSJCDM" to JsonPrimitive(1))),
            pg(mapOf("KSJCDM" to JsonPrimitive(3), "JSXM" to JsonNull, "JASMC" to JsonNull)))
        assertEquals(listOf(pgCourse(2, 1, 1), pgCourse(2, 1, 3),
            pgCourse(2, 3, 1).copy(room = "", teacher = ""), pgCourse(2, 3, 3).copy(room = "", teacher = "")), parse(source))
    }

    @Test fun postgraduateInvalidBitmapOrCoordinateDoesNotBecomeAnotherSchedule() {
        val bad = listOf(
            mapOf("XQ" to JsonPrimitive(0)), mapOf("XQ" to JsonPrimitive("2")),
            mapOf("KSJCDM" to JsonPrimitive(0)), mapOf("KSJCDM" to JsonPrimitive(367)),
            mapOf("KSJCDM" to JsonPrimitive(1.5)), mapOf("KSJCDM" to JsonPrimitive(2147483648L)),
            mapOf("ZCBH" to JsonPrimitive("10x01")), mapOf("ZCBH" to JsonPrimitive("1".repeat(367))),
            mapOf("ZCBH" to JsonPrimitive(101)), mapOf("ZCBH" to JsonNull),
            mapOf("JSXM" to JsonArray(emptyList())), mapOf("JASMC" to JsonObject(emptyMap())),
        )
        for (fields in bad) assertEquals(fields.toString(), listOf(pgCourse(2, 2, 1), pgCourse(2, 2, 3)),
            parse(postgraduate(pg(fields + ("KCMC" to JsonPrimitive("坏安排"))), pg())))
    }

    @Test fun unrelatedJsonAndAmbiguousEnvelopesAreNeverTimetables() {
        val cases = listOf("{}", "[]", "null", "{broken}", "{\"results\":[]}",
            "{\"results\":[{\"KCMC\":\"成绩\",\"XF\":3,\"CJ\":\"A\"}]}",
            "{\"activities\":[${activity()}]}", "{\"studentTableVm\":{\"activities\":[${activity()}]}}",
            "{\"data\":{\"studentTableVms\":[{\"activities\":[${activity()}]}]}}",
            "{\"studentTableVms\":{},\"results\":[${pg()}]}",
            "{\"studentTableVms\":[{\"activities\":[${activity()}]}],\"results\":[${pg()}]}",
            JsonObject(mapOf("message" to JsonPrimitive(htmlFixture()))).toString(),
        )
        for (source in cases) {
            val parser = JwFduParser(source)
            assertEquals(source, emptyList<JwCourse>(), parser.generateCourseList())
            assertEquals(source, 0, parser.confidence())
            assertEquals(source, emptyList<String>(), parser.matchedFeatures())
        }
    }

    @Test fun malformedUndergraduateEnvelopeCannotSelectAnotherContainer() {
        for (tables in listOf("null", "[]", "[null]", "[{}]", "[{\"activities\":null}]", "[{\"activities\":{}}]")) {
            val source = "{\"studentTableVms\":$tables}"
            assertEquals(source, emptyList<JwCourse>(), parse(source))
            assertEquals(source, 0, JwFduParser(source).confidence())
        }
    }

    @Test fun nonObjectRowsDoNotRemoveGoodRowsAndEmptyBitmapsDoNotInventWeeks() {
        val activityRows = "[null,42,\"invalid\",${activity()}]"
        assertEquals(listOf(course(1, 3, 4, 1), course(1, 3, 4, 3)),
            parse("{\"studentTableVms\":[{\"activities\":$activityRows}]}"))
        assertEquals(listOf(pgCourse(2, 2, 1), pgCourse(2, 2, 3)),
            parse("{\"results\":[null,42,\"invalid\",${pg()}]}"))
        for (bitmap in listOf("", "000")) assertEquals(emptyList<JwCourse>(),
            parse(postgraduate(pg(mapOf("ZCBH" to JsonPrimitive(bitmap))))))
    }

    @Test fun jsonMustBeACompleteStrictDocument() {
        val valid = undergraduate(activity())
        for (source in listOf(valid + " trailing", valid.replace("\"studentTableVms\"", "studentTableVms"),
            valid.replace("\"courseName\"", "'courseName'"))) {
            assertEquals(source, emptyList<JwCourse>(), parse(source))
            assertEquals(source, 0, JwFduParser(source).confidence())
        }
    }

    @Test fun existingHtmlContinuationRowsRetainTheirOwnRoomAndWeeks() {
        val source = htmlFixture()
        assertEquals(listOf(JwCourse("旧课程", "A", "", 1, 1, 2, 1, 1),
            JwCourse("旧课程", "A", "", 1, 1, 2, 3, 3),
            JwCourse("旧课程", "B", "", 7, 5, 6, 8, 10, 2)), parse(source))
        assertEquals(100, JwFduParser(source).confidence())
        assertEquals(listOf("declared-fdu-list"), JwFduParser(source).matchedFeatures())
    }

    @Test fun existingHtmlMalformedRowsDoNotCarryNamesIntoAnotherTable() {
        val source = "<table class='table table-hover'><tbody>" +
            "<tr><td>A</td><td>星期一 第1节</td><td>1周</td><td>备注</td></tr>" +
            "<tr><td>1</td><td>坏课程</td><td>B</td><td>星期一 第1节</td><td>第1-3,未知周</td><td>备注</td></tr>" +
            "<tr><td>2</td><td></td><td>C</td><td>星期一 第1节</td><td>1周</td><td>备注</td></tr>" +
            "</tbody></table><table class='table table-hover'><tbody>" +
            "<tr><td>D</td><td>星期一 第1节</td><td>1周</td><td>备注</td></tr></tbody></table>"
        assertEquals(emptyList<JwCourse>(), parse(source))
    }

    private fun parse(source: String) = JwFduParser(source).generateCourseList()
    private fun array(vararg values: Int) = JsonArray(values.map(::JsonPrimitive))
    private fun activity(overrides: Map<String, JsonElement> = emptyMap()): JsonObject = JsonObject(
        (Json.parseToJsonElement("""{"courseName":" 课程甲 ","teachers":["张老师","王老师"],"room":" H6203 ","weekday":1,"startUnit":3,"endUnit":4,"weekIndexes":[1,3]}""") as JsonObject) + overrides)
    private fun pg(overrides: Map<String, JsonElement> = emptyMap()): JsonObject = JsonObject(
        (Json.parseToJsonElement("""{"KCMC":" 研究生课程 ","JSXM":" 赵老师 ","JASMC":" H3101 ","ZCBH":"101","XQ":2,"KSJCDM":2}""") as JsonObject) + overrides)
    private fun undergraduate(vararg rows: JsonObject) = "{\"studentTableVms\":[{\"activities\":[${rows.joinToString(",")}]}]}"
    private fun postgraduate(vararg rows: JsonObject) = "{\"results\":[${rows.joinToString(",") }]}"
    private fun course(day: Int, first: Int, last: Int, week: Int) = JwCourse("课程甲", "H6203", "张老师/王老师", day, first, last, week, week)
    private fun pgCourse(day: Int, node: Int, week: Int) = JwCourse("研究生课程", "H3101", "赵老师", day, node, node, week, week)
    private fun htmlFixture() = "<table class='table table-hover'><tbody>" +
        "<tr><td>1</td><td>旧课程</td><td>A</td><td>星期一 第1-2节</td><td>第1,3周</td><td>备注</td></tr>" +
        "<tr><td>B</td><td>星期日 第5-6节</td><td>第8-10双周</td><td>备注</td></tr></tbody></table>"
}

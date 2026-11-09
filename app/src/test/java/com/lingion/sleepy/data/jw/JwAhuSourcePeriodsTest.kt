package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JwAhuSourcePeriodsTest {
    @Test fun keepsAllThirteenSourcePeriodsIncludingUnusedTailInConfirmationRows() {
        val source = payload(units())
        val periods = JwAhuSourcePeriods.resolve(source)
        assertEquals(expectedPeriods, periods)
        assertEquals(expectedPeriods, JwSourcePeriods.resolve(source, JwProtocol.TYPE_EAMS5, "安徽大学", emptyList()))
        // This import only uses nodes 1 through 8; source nodes 9 through 13 remain editable.
        assertEquals(expectedPeriods, JwSourcePeriods.confirmationRows(8, periods))
    }

    @Test fun usesExplicitIndicesWithoutRenumberingOrDependingOnArrayOrder() {
        val sparse = JSONArray().put(unit(13, 2130, 2215)).put(unit(1, 800, 845))
        assertEquals(listOf(Triple(1, "08:00", "08:45"), Triple(13, "21:30", "22:15")),
            JwAhuSourcePeriods.resolve(payload(sparse)))
    }

    @Test fun acceptsIntegerStringsButDoesNotInventSchoolDefaultTimes() {
        val row = JSONObject().put("indexNo", "13").put("startTime", "0800").put("endTime", "0845")
        assertEquals(listOf(Triple(13, "08:00", "08:45")),
            JwAhuSourcePeriods.resolve(payload(JSONArray().put(row))))
        for (source in listOf("", "<html>登录</html>", "{}", "{\"timeTableLayout\":{}}",
            "{\"timeTableLayout\":{\"courseUnitList\":[]}}")) {
            assertEquals(source, emptyList<Triple<Int, String, String>>(), JwAhuSourcePeriods.resolve(source))
        }
    }

    @Test fun rejectsTheWholeMatrixWhenAnyRowOrRequiredColumnIsInvalid() {
        val invalidRows = listOf<Any>(JSONObject.NULL, "bad-row", JSONArray(),
            JSONObject().put("startTime", 900).put("endTime", 945),
            JSONObject().put("indexNo", 2).put("endTime", 945),
            JSONObject().put("indexNo", 2).put("startTime", 900),
            unit(2, 900, 945).put("indexNo", true),
            unit(2, 900, 945).put("startTime", "9:00"),
            unit(2, 900, 945).put("endTime", JSONObject.NULL))
        for (row in invalidRows) {
            val matrix = JSONArray().put(unit(1, 800, 845)).put(row).put(unit(3, 1000, 1045))
            assertEquals(row.toString(), emptyList<Triple<Int, String, String>>(),
                JwAhuSourcePeriods.resolve(payload(matrix)))
        }
    }

    @Test fun rejectsInvalidClockRangesInsteadOfReturningTheRemainingRows() {
        for ((start, end) in listOf(960 to 1045, 2400 to 2445, -100 to 945, 900 to 900, 945 to 900)) {
            val matrix = JSONArray().put(unit(1, 800, 845)).put(unit(2, start, end))
            assertEquals("$start-$end", emptyList<Triple<Int, String, String>>(),
                JwAhuSourcePeriods.resolve(payload(matrix)))
        }
    }

    @Test fun rejectsDuplicateOrInvalidExplicitIndicesForTheWholeMatrix() {
        for (index in listOf(0, -1, 1, 367, Int.MAX_VALUE)) {
            val matrix = JSONArray().put(unit(1, 800, 845)).put(unit(index, 900, 945))
            assertEquals("index=$index", emptyList<Triple<Int, String, String>>(),
                JwAhuSourcePeriods.resolve(payload(matrix)))
        }
    }

    @Test fun retainsTheSharedResourceBoundaryAndRejectsOversizedMatrices() {
        assertEquals(listOf(Triple(366, "08:00", "08:45")),
            JwAhuSourcePeriods.resolve(payload(JSONArray().put(unit(366, 800, 845)))))
        val oversized = JSONArray()
        repeat(367) { oversized.put(unit(it + 1, 800, 845)) }
        assertEquals(emptyList<Triple<Int, String, String>>(), JwAhuSourcePeriods.resolve(payload(oversized)))
    }

    private fun payload(rows: JSONArray): String = JSONObject().put("timeTableLayout",
        JSONObject().put("courseUnitList", rows)).put("studentTableVms", JSONArray().put(
            JSONObject().put("activities", JSONArray().put(JSONObject().put("startUnit", 7).put("endUnit", 8)))
        )).toString()

    private fun unit(index: Int, start: Int, end: Int) = JSONObject()
        .put("indexNo", index).put("startTime", start).put("endTime", end).put("dayPart", "MORNING")

    private fun units(): JSONArray = JSONArray().apply {
        expectedPeriods.forEach { (index, start, end) ->
            put(unit(index, start.replace(":", "").toInt(), end.replace(":", "").toInt()))
        }
    }

    // Synthetic clocks exercise the pinned AHU numeric HHmm fields; no school defaults are asserted.
    private val expectedPeriods = listOf(
        Triple(1, "08:00", "08:45"), Triple(2, "08:55", "09:40"),
        Triple(3, "10:00", "10:45"), Triple(4, "10:55", "11:40"),
        Triple(5, "12:00", "12:45"), Triple(6, "12:55", "13:40"),
        Triple(7, "14:00", "14:45"), Triple(8, "14:55", "15:40"),
        Triple(9, "16:00", "16:45"), Triple(10, "16:55", "17:40"),
        Triple(11, "19:00", "19:45"), Triple(12, "20:00", "20:45"),
        Triple(13, "21:30", "22:15"),
    )
}

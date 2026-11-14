package com.lingion.sleepy.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class EffectivePeriodResolverTest {
    // Captured from the seven vendored callers at shiguang_warehouse
    // 7c9e1907635cb1c2d481b316e6f52d69dc77edff. Clock values are explicit source
    // values, including QDU's season-dependent afternoon lessons.
    private val hzuSummer = slots(
        "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 " +
            "14:30-15:20 15:30-16:20 16:40-17:30 17:40-18:30 19:30-20:20 20:30-21:20"
    )
    private val hzuWinter = slots(
        "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 " +
            "14:00-14:50 15:00-15:50 16:10-17:00 17:10-18:00 19:30-20:20 20:30-21:20"
    )
    private val nwafuWinter = slots(
        "08:00-08:45 08:55-09:40 10:10-10:55 11:05-11:50 " +
            "14:00-14:45 14:55-15:40 16:00-16:45 16:55-17:40 19:00-19:45 19:50-20:35 20:40-21:25"
    )
    private val nwafuSummer = slots(
        "08:00-08:45 08:55-09:40 10:10-10:55 11:05-11:50 " +
            "14:30-15:15 15:25-16:10 16:30-17:15 17:25-18:10 19:30-20:15 20:20-21:05 21:10-21:55"
    )
    private val qduSummer = slots(
        "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 " +
            "14:00-14:50 15:00-15:50 16:00-16:50 17:00-17:50 19:00-19:50 " +
            "20:00-20:50 21:00-21:50"
    )
    private val qduWinterDiff = slots(
        "13:30-14:20 14:30-15:20 15:30-16:20 16:30-17:20 18:30-19:20 " +
            "19:30-20:20 20:30-21:20", 5
    )
    private val tsuSummer = slots(
        "08:00-08:45 08:55-09:40 10:00-10:45 10:55-11:40 " +
            "14:30-15:15 15:25-16:10 16:30-17:15 17:25-18:10 19:00-19:45 19:55-20:40"
    )
    private val tsuWinterDiff = slots("14:00-14:45 14:55-15:40 16:00-16:45 16:55-17:40", 5)
    private val ujsSummer = slots(
        "08:00-08:45 08:55-09:40 10:10-10:55 11:05-11:50 " +
            "14:00-14:45 14:55-15:40 16:00-16:45 16:55-17:40 19:00-19:45 19:55-20:40 20:50-21:35"
    )
    private val ujsWinter = slots(
        "08:00-08:45 08:55-09:40 10:10-10:55 11:05-11:50 " +
            "13:30-14:15 14:25-15:10 15:30-16:15 16:25-17:10 18:30-19:15 19:25-20:10 20:20-21:05"
    )
    private val uscWinter = slots(
        "08:20-09:05 09:15-10:00 10:20-11:05 11:15-12:00 " +
            "14:30-15:15 15:25-16:10 16:30-17:15 17:25-18:10 19:30-20:15 20:25-21:10"
    )
    private val uscSummer = slots(
        "08:00-08:45 08:55-09:40 10:00-10:45 10:55-11:40 " +
            "15:00-15:45 15:55-16:40 17:00-17:45 17:55-18:40 20:00-20:45 20:55-21:40"
    )
    private val zwuWinter = slots(
        "08:00-08:45 08:50-09:35 09:50-10:35 10:40-11:25 11:30-12:15 " +
            "13:30-14:15 14:20-15:05 15:20-16:05 16:10-16:55 18:00-18:45 " +
            "18:50-19:35 19:40-20:25 20:30-21:15 21:20-22:05"
    )
    private val zwuSummer = slots(
        "08:00-08:45 08:50-09:35 09:50-10:35 10:40-11:25 11:30-12:15 " +
            "14:00-14:45 14:50-15:35 15:50-16:35 16:40-17:25 18:30-19:15 " +
            "19:20-20:05 20:10-20:55 21:00-21:45 21:50-22:35"
    )
    private val basic = slots("08:00-08:45 09:00-09:45")

    @Test fun hzuPartialSummerHasClosedBoundsAndFallsBackToWinterInGaps() {
        val summer = rule("2026-05-01", "2026-10-04", select(hzuSummer, 5..8))
            .put("defaultClassDuration", 50).put("defaultBreakDuration", 10)
        val plan = validate(hzuWinter, combo(summer))
        assertEquals(50L, plan.publicSchedules.single().defaultClassDuration)
        for (date in listOf("2026-05-01", "2026-10-04")) {
            val rows = rows(plan.resolve(LocalDate.parse(date)))
            assertEquals(10, rows.size)
            assertEquals("14:30", rows[4].start)
            assertEquals("19:30", rows[8].start)
        }
        for (date in listOf("2026-04-30", "2026-10-05", "2027-05-01")) {
            assertEquals("14:00", rows(plan.resolve(LocalDate.parse(date)))[4].start)
        }
    }

    @Test fun nwafuFullSeasonRulesUseTheirConcreteYears() {
        val plan = validate(nwafuWinter, combo(
            rule("2026-10-01", "2027-04-30", nwafuWinter),
            rule("2027-05-01", "2027-09-30", nwafuSummer)
        ))
        assertEquals("20:40", rows(plan.resolve(LocalDate.of(2027, 4, 30)))[10].start)
        assertEquals("21:10", rows(plan.resolve(LocalDate.of(2027, 5, 1)))[10].start)
        assertEquals("20:40", rows(plan.resolve(LocalDate.of(2028, 5, 1)))[10].start)
    }

    @Test fun qduElevenPeriodsSwitchAfternoonByDateAndKeepMorningStable() {
        val plan = validate(qduSummer, combo(
            rule("2026-05-01", "2026-09-30", select(qduSummer, 5..9)),
            rule("2026-10-01", "2027-04-30", qduWinterDiff)
        ))
        val summerStart = rows(plan.resolve(LocalDate.of(2026, 5, 1)))
        assertEquals(11, summerStart.size)
        assertEquals("08:00", summerStart[0].start)
        assertEquals("14:00", summerStart[4].start)
        assertEquals("19:00", summerStart[8].start)
        assertEquals("19:50", summerStart[8].end)
        assertEquals("21:50", summerStart[10].end)

        val winter = rows(plan.resolve(LocalDate.of(2026, 10, 1)))
        assertEquals(11, winter.size)
        assertEquals("08:00", winter[0].start)
        assertEquals("13:30", winter[4].start)
        assertEquals("14:20", winter[4].end)
        assertEquals("18:30", winter[8].start)
        assertEquals("19:20", winter[8].end)
        assertEquals("21:20", winter[10].end)

        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2026, 9, 30)))[4].start)
        assertEquals("13:30", rows(plan.resolve(LocalDate.of(2027, 4, 30)))[4].start)
        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2027, 5, 1)))[4].start)
        assertEquals("10:10", rows(plan.resolve(LocalDate.of(2027, 5, 1)))[2].start)
    }

    @Test fun tsuEmptySummerDifferencesAndOctoberEighthSwitchArePreserved() {
        val plan = validate(tsuSummer, combo(
            rule("2026-05-01", "2026-10-07"),
            rule("2026-10-08", "2027-04-30", tsuWinterDiff)
        ))
        assertTrue(plan.publicSchedules[0].timeSlots.isEmpty())
        assertEquals("14:30", rows(plan.resolve(LocalDate.of(2026, 10, 7)))[4].start)
        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2026, 10, 8)))[4].start)
        assertEquals("19:00", rows(plan.resolve(LocalDate.of(2027, 4, 30)))[8].start)
        assertEquals("14:30", rows(plan.resolve(LocalDate.of(2027, 5, 1)))[4].start)
    }

    @Test fun qduCombinationCanStoreOnlySeasonDifferences() {
        val plan = validate(qduSummer, combo(
            rule("2026-05-01", "2026-09-30"),
            rule("2026-10-01", "2027-04-30", qduWinterDiff)
        ))
        assertTrue(plan.publicSchedules.first().timeSlots.isEmpty())
        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2026, 9, 30)))[4].start)
        assertEquals("13:30", rows(plan.resolve(LocalDate.of(2026, 10, 1)))[4].start)
        assertEquals("08:00", rows(plan.resolve(LocalDate.of(2026, 10, 1)))[0].start)
    }

    @Test fun ujsSemesterIntersectionsAreNotExpandedToTheWholeSeason() {
        val plan = validate(ujsSummer, combo(
            rule("2026-09-07", "2026-10-06", ujsSummer)
                .put("defaultClassDuration", 45).put("defaultBreakDuration", 10),
            rule("2026-10-07", "2027-02-07", ujsWinter)
                .put("defaultClassDuration", 45).put("defaultBreakDuration", 10)
        ))
        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2026, 10, 6)))[4].start)
        assertEquals("13:30", rows(plan.resolve(LocalDate.of(2026, 10, 7)))[4].start)
        assertEquals("20:20", rows(plan.resolve(LocalDate.of(2027, 2, 7)))[10].start)
        assertEquals("14:00", rows(plan.resolve(LocalDate.of(2027, 2, 8)))[4].start)
    }

    @Test fun uscEmptyWinterRuleKeepsTheDistinctMorningBase() {
        val plan = validate(uscWinter, combo(
            rule("2026-05-01", "2026-09-30", uscSummer),
            rule("2026-10-01", "2027-04-30")
        ))
        assertEquals("08:00", rows(plan.resolve(LocalDate.of(2026, 9, 30)))[0].start)
        assertEquals("08:20", rows(plan.resolve(LocalDate.of(2026, 10, 1)))[0].start)
        assertEquals("21:10", rows(plan.resolve(LocalDate.of(2027, 4, 30)))[9].end)
    }

    @Test fun zwuFourteenPeriodsKeepUnsortedSourceOrderAndDatesIndependentOfName() {
        val json = JSONObject(combo(
            rule("2027-05-01", "2027-09-30", zwuSummer),
            rule("2026-10-01", "2027-04-30", zwuWinter)
        )).put("name", "2020-2021 学年作息方案").toString()
        val plan = validate(zwuWinter, json)
        assertEquals(LocalDate.of(2027, 5, 1), plan.publicSchedules[0].startDate)
        assertEquals(LocalDate.of(2026, 10, 1), plan.publicSchedules[1].startDate)
        assertEquals(14, rows(plan.resolve(LocalDate.of(2027, 5, 1))).size)
        assertEquals("22:35", rows(plan.resolve(LocalDate.of(2027, 5, 1)))[13].end)
        assertEquals("22:05", rows(plan.resolve(LocalDate.of(2020, 5, 1)))[13].end)
    }

    @Test fun overlappingRulesUseOriginalFirstMatchEvenWhenFirstDifferenceIsEmpty() {
        val changed = slots("10:00-10:45 11:00-11:45")
        val first = rule("2026-05-20", "2026-06-05", changed).put("name", "first")
        val second = rule("2026-05-01", "2026-06-30").put("name", "second")
        assertEquals("10:00", rows(validate(basic, combo(first, second))
            .resolve(LocalDate.of(2026, 5, 20)))[0].start)
        assertEquals("08:00", rows(validate(basic, combo(second, first))
            .resolve(LocalDate.of(2026, 5, 20)))[0].start)
    }

    @Test fun leapDayAndSingleDayRuleDoNotMoveToMonday() {
        val plan = validate(basic, combo(rule("2028-02-29", "2028-02-29", slots("10:00-10:45", 2))))
        assertEquals("09:00", rows(plan.resolve(LocalDate.of(2028, 2, 28)))[1].start)
        assertEquals("10:00", rows(plan.resolve(LocalDate.of(2028, 2, 29)))[1].start)
        assertEquals("09:00", rows(plan.resolve(LocalDate.of(2028, 3, 1)))[1].start)
    }

    @Test fun nativeBaseAndSourceBaseProduceCompleteNativeJsonUsingRealTimeHelpers() {
        val native = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"09:00","end":"09:45"}]"""
        val json = combo(rule("2026-01-01", "2026-12-31", slots("10:00-10:45", 2)))
        val expected = listOf(TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(2, "10:00", "10:45"))
        val output = EffectivePeriodResolver.resolve(native, json, LocalDate.of(2026, 6, 1))
        assertEquals(expected, rows(output))
        assertEquals(TimeTableUtils.buildTimeJsonFromRows(expected), output)
        assertEquals(output, EffectivePeriodResolver.resolve(basic.toString(), json, "2026-06-01"))
        val first = JSONArray(output).getJSONObject(0)
        assertEquals(setOf("node", "start", "end"), first.keys().asSequence().toSet())
    }

    @Test fun differenceArrayOrderDoesNotRenumberOrReorderTheBase() {
        val diff = JSONArray().put(slots("10:00-10:45", 2).getJSONObject(0))
            .put(slots("08:15-09:00", 1).getJSONObject(0))
        val result = rows(validate(basic, combo(rule("2026-01-01", "2026-12-31", diff)))
            .resolve(LocalDate.of(2026, 1, 1)))
        assertEquals(listOf(1, 2), result.map { it.node })
        assertEquals(listOf("08:15", "10:00"), result.map { it.start })
    }

    @Test fun missingDurationsAreMetadataDefaultsAndExplicitDurationsNeverRebuildClocks() {
        val rule = rule("2026-01-01", "2026-12-31")
            .put("defaultClassDuration", 9_007_199_254_740_991L).put("defaultBreakDuration", 0)
        val plan = validate(basic, combo(rule))
        assertEquals(9_007_199_254_740_991L, plan.publicSchedules[0].defaultClassDuration)
        assertEquals(0L, plan.publicSchedules[0].defaultBreakDuration)
        assertEquals("08:45", rows(plan.resolve(LocalDate.of(2026, 1, 1)))[0].end)
        val defaults = validate(basic, combo(rule("2026-01-01", "2026-12-31"))).publicSchedules[0]
        assertEquals(45L, defaults.defaultClassDuration)
        assertEquals(10L, defaults.defaultBreakDuration)
    }

    @Test fun noRulesAndOptionalEmptyNameReturnTheValidatedBase() {
        val plan = validate(basic, JSONObject(combo()).put("name", "").toString())
        assertEquals("", plan.name)
        assertTrue(plan.publicSchedules.isEmpty())
        assertEquals(plan.baseTimeJson, plan.resolve(LocalDate.of(2026, 1, 1)))
        assertEquals(null, validate(basic, combo()).name)
    }

    @Test fun accepts366BasePeriodsWithoutAnArbitraryThirtyPeriodLimit() {
        val periods = JSONArray()
        repeat(366) { index -> periods.put(JSONObject().put("number", index + 1)
            .put("startTime", LocalTime.MIDNIGHT.plusMinutes(index.toLong()).format(clock))
            .put("endTime", LocalTime.MIDNIGHT.plusMinutes(index + 1L).format(clock))) }
        assertEquals(366, rows(validate(periods, combo()).baseTimeJson).size)
        periods.put(JSONObject().put("number", 367).put("startTime", "06:06").put("endTime", "06:07"))
        invalid(periods.toString(), combo())
        invalid("[]", combo())
    }

    @Test fun rejectsMissingDuplicateGappedUnorderedAndOutOfRangeBaseIdentities() {
        for (numbers in listOf(listOf(0, 1), listOf(1, 1), listOf(1, 3), listOf(2, 1), listOf(1, 367))) {
            val base = JSONArray(basic.toString())
            numbers.forEachIndexed { index, number -> base.getJSONObject(index).put("number", number) }
            invalid(base.toString(), combo())
        }
        val base = JSONArray(basic.toString())
        base.getJSONObject(0).remove("number")
        invalid(base.toString(), combo())
    }

    @Test fun rejectsDuplicateAndUnknownDifferenceIdentities() {
        for (diff in listOf(
            JSONArray().put(slots("10:00-10:45", 2).getJSONObject(0)).put(slots("11:00-11:45", 2).getJSONObject(0)),
            slots("10:00-10:45", 0), slots("10:00-10:45", 3)
        )) invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31", diff)))
    }

    @Test fun rejectsWrongNumericTypesWithoutCoercion() {
        for (value in listOf<Any>("1", true, JSONObject.NULL, 1.5)) {
            val base = JSONArray(basic.toString())
            base.getJSONObject(0).put("number", value)
            invalid(base.toString(), combo())
            val diff = slots("10:00-10:45", 2)
            diff.getJSONObject(0).put("number", value)
            invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31", diff)))
        }
        invalid("""[{"node":"1","start":"08:00","end":"08:45"}]""", combo())
        invalid(basic.toString().replace("\"number\":1", "\"number\":1e0"), combo())
        invalid(basic.toString().replace("\"number\":1", "\"number\":1.0"), combo())
    }

    @Test fun rejectsMissingEmptyWrongTypeAndNonMinuteClocks() {
        for (value in listOf<Any>("", "8:00", "08:0", " 08:00", "08:00 ", "24:00", "08:60", "08:00:00", 800, true, JSONObject.NULL)) {
            for (key in listOf("startTime", "endTime")) {
                val base = JSONArray(basic.toString())
                base.getJSONObject(0).put(key, value)
                invalid(base.toString(), combo())
                val diff = slots("10:00-10:45", 2)
                diff.getJSONObject(0).put(key, value)
                invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31", diff)))
            }
        }
        for (key in listOf("startTime", "endTime")) {
            val base = JSONArray(basic.toString())
            base.getJSONObject(0).remove(key)
            invalid(base.toString(), combo())
        }
    }

    @Test fun rejectsNonPositiveDurationsAndCompleteTableOverlapsIncludingUnchangedNeighbors() {
        for (bad in listOf("08:00-08:00", "08:45-08:00", "23:30-00:15")) {
            invalid(slots(bad).toString(), combo())
        }
        invalid(slots("08:00-09:01 09:00-09:45").toString(), combo())
        for (bad in listOf("08:30-09:15", "07:00-07:45")) {
            invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31", slots(bad, 2))))
        }
        invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31", slots("08:00-09:01", 1))))
    }

    @Test fun rejectsInvalidRulesEvenWhenTheyNeverMatchTheQueryDate() {
        val json = combo(rule("2026-01-01", "2026-12-31"),
            rule("2030-01-01", "2030-12-31", slots("08:30-09:15", 2)))
        assertThrows(IllegalArgumentException::class.java) {
            EffectivePeriodResolver.resolve(basic.toString(), json, LocalDate.of(2026, 1, 1))
        }
    }

    @Test fun rejectsMalformedDatesAndInvertedRangesWithoutNormalizingThem() {
        for (date in listOf("2026-02-29", "2028-02-30", "2026-04-31", "2026-13-01", "2026-00-01",
            "2026-1-01", "2026-01-1", " 2026-01-01", "2026-01-01 ", "2026-01-01T00:00:00", "0000-01-01")) {
            invalid(basic.toString(), combo(rule(date, "2028-12-31")))
            invalid(basic.toString(), combo(rule("2026-01-01", date)))
            assertThrows(IllegalArgumentException::class.java) {
                EffectivePeriodResolver.resolve(basic.toString(), combo(), date)
            }
        }
        invalid(basic.toString(), combo(rule("2026-10-08", "2026-10-07")))
    }

    @Test fun rejectsMissingNullOrWrongTypeRuleFieldsAndMetadata() {
        for (key in listOf("name", "startDate", "endDate", "timeSlots")) {
            val missing = rule("2026-01-01", "2026-12-31")
            missing.remove(key)
            invalid(basic.toString(), combo(missing))
            invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31").put(key, JSONObject.NULL)))
        }
        for (key in listOf("defaultClassDuration", "defaultBreakDuration")) {
            for (value in listOf<Any>("45", 1.5, true, JSONObject.NULL, -1, 9_007_199_254_740_992L)) {
                invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31").put(key, value)))
            }
        }
        invalid(basic.toString(), combo(rule("2026-01-01", "2026-12-31").put("defaultClassDuration", 0)))
        for (json in listOf("{}", "[]", "null", "{\"publicSchedules\":null}", "{\"publicSchedules\":{}}",
            "{\"publicSchedules\":[null]}", "{\"publicSchedules\":[],\"name\":null}")) {
            invalid(basic.toString(), json)
        }
    }

    @Test fun rejectsMalformedAndLenientJsonInsteadOfFallingBackToDefaultPeriods() {
        for (json in listOf("", "[", "null", "{}", "[null]", "[1]", "[{}]", "[{'number':1,'startTime':'08:00','endTime':'08:45'}]",
            "[{number:1,startTime:\"08:00\",endTime:\"08:45\"}]", "$basic trailing", "[$basic,]")) {
            invalid(json, combo())
        }
        for (json in listOf("", "{", "{'publicSchedules':[]}", "{publicSchedules:[]}",
            "{\"publicSchedules\":[],}", "{\"publicSchedules\":[]} trailing", "{\"publicSchedules\":[],\"publicSchedules\":[]}")) {
            invalid(basic.toString(), json)
        }
        invalid("""[{"number":1,"number":1,"startTime":"08:00","endTime":"08:45"}]""", combo())
        invalid("""[{"number":1,"num\u0062er":1,"startTime":"08:00","endTime":"08:45"}]""", combo())
    }

    @Test fun rejectsMixedBaseDialectsAndIncompleteNativeRows() {
        invalid("""[{"node":1,"number":1,"start":"08:00","end":"08:45"}]""", combo())
        invalid("""[{"node":1,"start":"08:00","end":"08:45"},{"number":2,"startTime":"09:00","endTime":"09:45"}]""", combo())
        invalid("""[{"node":1,"start":"08:00"}]""", combo())
        invalid("""[{"node":1,"start":"08:00","end":"08:45","edge":"unknown"}]""", combo())
    }

    @Test fun validatedRuleListsCannotBeMutatedAfterValidation() {
        val plan = validate(basic, combo(rule("2026-01-01", "2026-12-31", slots("10:00-10:45", 2))))
        assertThrows(UnsupportedOperationException::class.java) {
            (plan.publicSchedules as MutableList<*>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (plan.publicSchedules[0].timeSlots as MutableList<*>).clear()
        }
        assertFalse(plan.resolve(LocalDate.of(2026, 1, 1)).isBlank())
    }

    private fun slots(spec: String, first: Int = 1): JSONArray = JSONArray().apply {
        spec.split(' ').forEachIndexed { index, clock ->
            val (start, end) = clock.split('-')
            put(JSONObject().put("number", first + index).put("startTime", start).put("endTime", end))
        }
    }

    private fun select(slots: JSONArray, numbers: IntRange): JSONArray = JSONArray().apply {
        for (index in 0 until slots.length()) {
            val slot = slots.getJSONObject(index)
            if (slot.getInt("number") in numbers) put(JSONObject(slot.toString()))
        }
    }

    private fun rule(start: String, end: String, slots: JSONArray = JSONArray()): JSONObject =
        JSONObject().put("name", "source rule").put("startDate", start).put("endDate", end).put("timeSlots", slots)

    private fun combo(vararg rules: JSONObject): String = JSONObject()
        .put("publicSchedules", JSONArray(rules.toList())).toString()

    private fun validate(base: JSONArray, combo: String) = EffectivePeriodResolver.validate(base.toString(), combo)
    private fun rows(json: String) = TimeTableUtils.parseTimeSlotRows(json)
    private fun invalid(base: String, combo: String) {
        assertThrows("base=$base combo=$combo", IllegalArgumentException::class.java) {
            EffectivePeriodResolver.validate(base, combo)
        }
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm")
}

package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #28: NEU ICS import → Sleepy ICS export → re-import round-trip.
 *
 * The real NEU export embeds a custom PRODID, but the parser must preserve
 * day/node/step/weeks and the exporter must re-emit a Sleepy ICS that survives
 * a second parse (lossless closure).
 */
class NeuRoundTripIcsTest {

    @Test
    fun neuRoundTrip_preservesCourseCountAndNodeSpan() {
        val ics = javaClass.getResourceAsStream("/neu-schedule-2026-2027-1.ics")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("NEU fixture missing")

        val first = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()
        assertTrue("parser dropped all courses", first.courses.isNotEmpty())

        val table = TimeTableEntity(
            id = 999L,
            name = "neu-round-trip",
            startDate = first.startDate,
            maxWeek = first.courses.maxOf { it.endWeek },
            timeJson = first.timeJson,
            createdAt = 0L
        )
        val exported = ScheduleExporter.exportIcs(table, first.courses)
        val second = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()

        assertEquals(
            "Re-imported course count must equal the first parse",
            first.courses.size,
            second.courses.size
        )
        // Day 1..7 only.
        assertTrue(
            "Days must stay within 1..7",
            second.courses.all { it.day in 1..7 }
        )
        // Re-imported nodes must fit within the originally harvested window.
        val maxNode = first.nodesPerDay
        assertTrue(
            "Second-parse nodes must stay within nodesPerDay=$maxNode, got ${second.courses.map { it.startNode + it.step - 1 }.maxOrNull()}",
            second.courses.all { it.startNode >= 1 && it.startNode + it.step - 1 <= maxNode }
        )
    }

    @Test
    fun neuRoundTrip_eventDayWeekIsMappedBackToIdenticalRange() {
        val ics = javaClass.getResourceAsStream("/neu-schedule-2026-2027-1.ics")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("NEU fixture missing")

        val first = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()
        val table = TimeTableEntity(
            id = 999L,
            name = "neu-round-trip",
            startDate = first.startDate,
            maxWeek = first.courses.maxOf { it.endWeek },
            timeJson = first.timeJson,
            createdAt = 0L
        )
        val exported = ScheduleExporter.exportIcs(table, first.courses)
        val second = ScheduleParser.parse(exported, defaultTableId = 999L).getOrThrow()

        val firstByKey = first.courses.groupBy { Triple(it.day, it.startNode, it.step) }
        val secondByKey = second.courses.groupBy { Triple(it.day, it.startNode, it.step) }

        for ((key, originals) in firstByKey) {
            val reimported = secondByKey[key]
                ?: error("No re-imported match for day=${key.first} node=${key.second} step=${key.third}")
            val minStart = originals.minOf { it.startWeek }
            val maxEnd = originals.maxOf { it.endWeek }
            assertEquals(
                "Week span must survive round-trip for $key",
                Pair(minStart, maxEnd),
                Pair(reimported.minOf { it.startWeek }, reimported.maxOf { it.endWeek })
            )
        }
    }

    @Test
    fun neuRoundTrip_preservesDeclaredNodeWindow() {
        val ics = javaClass.getResourceAsStream("/neu-schedule-2026-2027-1.ics")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("NEU fixture missing")

        val first = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()
        val table = TimeTableEntity(
            id = 999L,
            name = "neu-round-trip",
            startDate = first.startDate,
            maxWeek = first.courses.maxOf { it.endWeek },
            timeJson = first.timeJson,
            createdAt = 0L
        )
        val second = ScheduleParser.parse(
            ScheduleExporter.exportIcs(table, first.courses),
            defaultTableId = 999L
        ).getOrThrow()

        assertEquals("Declared node window must survive round-trip", first.nodesPerDay, second.nodesPerDay)
        val firstRows = TimeTableUtils.parseTimeSlotRows(first.timeJson)
        val secondRows = TimeTableUtils.parseTimeSlotRows(second.timeJson)
        assertTrue("Original timetable must contain standard rows", firstRows.any { it.edgeClass == null })
        assertTrue("Re-imported timetable must contain standard rows", secondRows.any { it.edgeClass == null })
    }
}
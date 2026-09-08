package com.lingion.sleepy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuRealFixtureImportTest {
    @Test
    fun realNeuIcs_mapsKnownDailyTimeBlocksToActualSections() {
        val ics = javaClass.getResourceAsStream("/neu-schedule-2026-2027-1.ics")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("NEU fixture is missing")

        val parsed = ScheduleParser.parse(ics, defaultTableId = 999L).getOrThrow()

        assertTrue(parsed.courses.isNotEmpty())
        assertEquals(
            "courses=${parsed.courses.map { it.startNode to it.step }} nodes=${parsed.nodesPerDay} time=${parsed.timeJson}",
            setOf(1, 3, 5, 7, 9),
            parsed.courses.map { it.startNode }.toSet()
        )
        assertEquals(12, parsed.nodesPerDay)
    }
}

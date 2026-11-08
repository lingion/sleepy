package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwPostgradMetadataTest {
    private val defaults = mapOf(
        "shtu_post" to "08:15-09:00 09:10-09:55 10:15-11:00 11:10-11:55 13:00-13:45 13:55-14:40 15:00-15:45 15:55-16:40 16:50-17:35 18:00-18:45 18:55-19:40 19:50-20:35 20:45-21:30",
        "shtu_post_2024" to "08:15-09:00 09:10-09:55 10:15-11:00 11:10-11:55 13:00-13:45 13:55-14:40 15:00-15:45 15:55-16:40 16:50-17:35 18:00-18:45 18:55-19:40 19:50-20:35 20:45-21:30",
        "xjtu_post" to "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 14:30-15:20 15:30-16:20 16:40-17:30 17:40-18:30 19:40-20:30 20:40-21:30 21:40-22:30",
        "ustc_post" to "07:50-08:35 08:40-09:25 09:45-10:30 10:35-11:20 11:25-12:10 14:00-14:45 14:50-15:35 15:55-16:40 16:45-17:30 17:35-18:20 19:30-20:15 20:20-21:05 21:10-21:55",
        "bfa_post" to "08:30-09:15 09:15-10:00 10:15-11:00 11:00-11:45 13:30-14:15 14:15-15:00 15:15-16:00 16:00-16:45 18:00-18:45 18:45-19:30 19:45-20:30 20:30-21:15",
    )

    @Test fun fixedSchoolDefaultsRetainEveryPeriod() {
        for ((type, times) in defaults) {
            val expected = times.split(' ').mapIndexed { index, time ->
                Triple(index + 1, time.substringBefore('-'), time.substringAfter('-'))
            }
            val actual = JwSourcePeriods.resolve("<html></html>", type, "", emptyList())
            assertEquals(type, expected, actual)
            assertTrue(type, actual.zipWithNext().all { (left, right) -> left.third <= right.second })
        }
    }

    @Test fun capturedTimesOverrideSchoolDefaults() {
        val captured = listOf(Triple(1, "09:01", "09:46"), Triple(2, "10:01", "10:46"))
        for (type in defaults.keys + listOf("nwpu_post", "shsmu")) {
            assertEquals(type, captured, JwSourcePeriods.resolve("", type, "", captured))
        }
    }

    @Test fun nwpuWeekCountIsAvailableForHtmlInput() {
        assertEquals(22, JwSourceCalendar.resolveWeekCount("<table id='sample-table-1'></table>", "nwpu_post"))
        assertEquals(0, JwSourceCalendar.resolveWeekCount("<html></html>", "unknown"))
        // Campus and seasonal choice are absent, so a clock must not be invented.
        assertTrue(JwSourcePeriods.resolve("<html></html>", "nwpu_post", "西北工业大学", emptyList()).isEmpty())
    }
}

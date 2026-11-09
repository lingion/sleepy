package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** Source-declared AHU print-data clocks, independent of the nodes used by this import. */
internal object JwAhuSourcePeriods {
    fun resolve(source: String): List<Triple<Int, String, String>> {
        val root = runCatching { JSONObject(source) }.getOrNull() ?: return emptyList()
        val units = root.optJSONObject("timeTableLayout")?.optJSONArray("courseUnitList")
            ?: return emptyList()
        if (units.length() !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) return emptyList()
        val indices = mutableSetOf<Int>()
        val periods = mutableListOf<Triple<Int, String, String>>()
        for (position in 0 until units.length()) {
            // A bad source row invalidates its matrix; never skip it and shift later periods.
            val row = units.optJSONObject(position) ?: return emptyList()
            val index = integer(row, "indexNo") ?: return emptyList()
            if (index !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || !indices.add(index)) return emptyList()
            val start = clock(integer(row, "startTime") ?: return emptyList()) ?: return emptyList()
            val end = clock(integer(row, "endTime") ?: return emptyList()) ?: return emptyList()
            if (!JwCourse.isValidTimeRange(start, end)) return emptyList()
            periods += Triple(index, start, end)
        }
        return periods.sortedBy { it.first }
    }

    private fun integer(row: JSONObject, key: String): Int? = row.opt(key)?.toString()?.trim()
        ?.takeIf { Regex("[0-9]+").matches(it) }?.toIntOrNull()

    private fun clock(value: Int): String? {
        if (value !in 0..2359 || value % 100 !in 0..59) return null
        return (value / 100).toString().padStart(2, '0') + ":" + (value % 100).toString().padStart(2, '0')
    }
}

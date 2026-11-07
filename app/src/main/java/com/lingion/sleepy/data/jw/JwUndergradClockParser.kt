package com.lingion.sleepy.data.jw

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Date and building-dependent layouts declared by the pinned 6.6.2 school routes. */
internal class JwUndergradClockParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { JSONObject(source) }.getOrNull() ?: return emptyList()
        return when (protocol) {
            "shsmu" -> parseShsmu(root)
            "ygu" -> parseYgu(root)
            else -> emptyList()
        }
    }

    private fun parseShsmu(root: JSONObject): List<JwCourse> {
        val referenceDate = JwShsmuCaptureCalendar.resolve(root) ?: return emptyList()
        val list = root.optJSONArray("list") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val name = item.text("Curriculum")
            if (name.isEmpty()) continue
            val start = dateTime(item.text("Start")) ?: continue
            val end = dateTime(item.text("End")) ?: continue
            if (start.toLocalDate() != end.toLocalDate() || !end.isAfter(start)) continue
            // New dated captures require a confirmed calendar; raw historical payloads keep their pinned baseline.
            val days = ChronoUnit.DAYS.between(referenceDate, start.toLocalDate())
            if (days < 0 || days >= 366L * 7) continue
            val week = (days / 7).toInt() + 1
            val startTime = start.format(clockFormat)
            val endTime = end.format(clockFormat)
            if (!JwCourse.isValidTimeRange(startTime, endTime)) continue
            val first = shsmuPeriods.indexOfFirst { it.first == startTime } + 1
            val last = shsmuPeriods.indexOfFirst { it.second == endTime } + 1
            val nodes = if (first > 0 && last >= first) first to last else 1 to 2
            // Always retain both actual times. The source emitted only the unmatched
            // endpoint, which loses a valid interval when just one endpoint matches.
            output += JwCourse(name, item.text("ClassroomAcademy"), item.text("Teacher"),
                start.dayOfWeek.value, nodes.first, nodes.second, week, week,
                startTime = startTime, endTime = endTime)
        }
        return output
    }

    private fun parseYgu(root: JSONObject): List<JwCourse> {
        val data = root.optJSONArray("data") ?: return emptyList()
        val output = mutableListOf<JwCourse>()
        for (dayIndex in 0 until data.length()) {
            val weeks = data.optJSONObject(dayIndex)?.optJSONArray("weekList") ?: continue
            for (weekIndex in 0 until weeks.length()) {
                val records = weeks.optJSONObject(weekIndex)?.optJSONArray("kcbVoList") ?: continue
                for (recordIndex in 0 until records.length()) {
                    val record = records.optJSONObject(recordIndex) ?: continue
                    val name = record.text("kcmc")
                    val day = record.text("xq").toIntOrNull() ?: continue
                    if (name.isEmpty() || day !in 1..7) continue
                    val parity = when (record.text("dsz")) {
                        "", "全" -> 0
                        "单" -> 1
                        "双" -> 2
                        else -> continue
                    }
                    val nodes = schoolNodeRanges(record.text("jcs"))
                    val weekRanges = schoolWeekRanges(record.text("zcs"))
                    if (nodes.isEmpty() || nodes.any { it.second > 11 } || weekRanges.isEmpty()) continue
                    val room = record.text("jsmc").replace(" ", "")
                    val special = listOf("文科楼", "商学楼", "音艺楼", "行政楼", "操场").any { it in room }
                    val periods = if (special) yguSpecialPeriods else yguPeriods
                    for ((firstNode, lastNode) in nodes) for ((firstWeek, lastWeek, _) in weekRanges) {
                        if ((firstWeek..lastWeek).none { parity == 0 || it % 2 == parity % 2 }) continue
                        output += JwCourse(name, room, record.text("rkls"), day,
                            firstNode, lastNode, firstWeek, lastWeek, parity,
                            startTime = periods[firstNode - 1].first, endTime = periods[lastNode - 1].second)
                    }
                }
            }
        }
        return output.distinct()
    }

    private fun dateTime(value: String): LocalDateTime? {
        val match = Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}").matchEntire(value) ?: return null
        return runCatching { LocalDateTime.parse(match.value.replace(' ', 'T')) }.getOrNull()
    }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

    companion object {
        val SHSMU_REFERENCE_DATE: LocalDate = LocalDate.of(2025, 9, 15)
        private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val shsmuPeriods = listOf(
            "08:00" to "08:40", "08:50" to "09:30", "09:40" to "10:20", "10:30" to "11:10",
            "11:20" to "12:00", "13:30" to "14:10", "14:20" to "15:00", "15:10" to "15:50",
            "16:00" to "16:40", "16:50" to "17:30", "17:40" to "18:20", "18:30" to "19:10", "19:20" to "20:00",
        )
        private val yguPeriods = listOf(
            "08:10" to "08:55", "09:00" to "09:45", "10:20" to "11:05", "11:15" to "12:00",
            "14:00" to "14:45", "14:50" to "15:35", "15:55" to "16:40", "16:45" to "17:30",
            "18:30" to "19:15", "19:20" to "20:05", "20:10" to "20:55",
        )
        private val yguSpecialPeriods = yguPeriods.toMutableList().apply {
            this[2] = "10:05" to "10:50"
            this[3] = "10:55" to "11:40"
        }.toList()
    }
}

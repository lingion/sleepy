// ECUPL script sub-protocol parser — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * ECUPL script timetable parser. OpenWakeUp EcuplParser.kt @ 9c2d0436
 * corroborates an eight-argument activity with teacher, course, room,
 * start date and decimal week bitmap. addActivityByTime takes day and
 * start/end minutes; CourseTable maps those minutes to lesson indices.
 * This is an independent implementation of observed protocol facts.
 */
class JwEcuplScriptParser(source: String) : JwParser(source) {

    companion object {
        // ---- 协议指纹 R5/R6/R7 char-by-char 锁定 ----

        /**
         * R5 — CourseTable 起始日 + 时间矩阵
         * 允许空白/换行在参数间 (WakeUp smali 调用常跨多行)
         */
        val RE_COURSE_TABLE: Regex = Regex(
            """new CourseTable\(\s*'([-\d]+?)'\s*,\s*\[([\d\[\],\s]+)]\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** R6 — 时间矩阵 [start_min, end_min] */
        val RE_TIME_PAIR: Regex = Regex("""\[(\d+)\s*,\s*(\d+)]""")

        /** Five-argument legacy fixture marker; not a verified schedule source. */
        val RE_NEW_ACTIVITY_5: Regex = Regex("""newActivity\(\s*"[^"]*"\s*,\s*"[^"]*"\s*,\s*"[^"]*"\s*,\s*"[01-]+"\s*,\s*\d+\s*\)""")

        val RE_NEW_ACTIVITY_8: Regex = Regex("""newActivity\(\s*"[^"]*"\s*,\s*"([^"]*)"\s*,\s*"[^"]*"\s*,\s*"([^"]*)"\s*,\s*"[^"]*"\s*,\s*"([^"]*)"\s*,\s*"([\d-]+)"\s*,\s*(\d+)\s*\)\s*;?""")

        val RE_NEW_ACTIVITY: Regex = RE_NEW_ACTIVITY_8

    }

    override fun generateCourseList(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()

        // 协议族验证
        val table = RE_COURSE_TABLE.find(source) ?: return out
        val semester = runCatching { java.time.LocalDate.parse(table.groupValues[1]) }.getOrNull() ?: return out
        val periods = RE_TIME_PAIR.findAll(table.groupValues[2]).map {
            val start = it.groupValues[1].toIntOrNull()
            val end = it.groupValues[2].toIntOrNull()
            if (start == null || end == null || start >= end || end > 1440) null else start to end
        }.toList()
        if (periods.isEmpty() || periods.any { it == null }) return out
        val script = Regex("""<script[^>]*language\s*=\s*"?JavaScript"?[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
            .findAll(source).map { it.groupValues[1] }
            .firstOrNull { it.contains(table.value) } ?: return out
        val activity = RE_NEW_ACTIVITY_8
        val slot = Regex("""addActivityByTime\(\s*activity\s*,\s*(\d)\s*,\s*(\d+)\s*,\s*(\d+)\s*\);""")
        val starts = Regex("""newActivity\s*\(""").findAll(script).map { it.range.first }.toList()
        for ((i, start) in starts.withIndex()) {
            val end = starts.getOrNull(i + 1) ?: script.length
            val segment = script.substring(start, end)
            val m = activity.find(segment) ?: continue
            if (m.range.first != 0) continue
            val date = runCatching { java.time.LocalDate.parse(m.groupValues[4]) }.getOrNull() ?: continue
            val bitmap = m.groupValues[5].toLongOrNull() ?: continue
            val offset = java.time.temporal.ChronoUnit.WEEKS.between(semester, date).toInt()
            val weeks = (0 until 64).filter { bitmap and (1L shl it) != 0L }
                .map { it + offset + 1 }
            if (weeks.isEmpty() || weeks.any { it !in 1..64 }) continue
            val name = m.groupValues[2].replace(Regex("""\(\d+\)$"""), "").trim()
            val teacher = m.groupValues[1].trim()
            val room = m.groupValues[3].trim()
            if (name.isBlank()) continue
            for (slotMatch in slot.findAll(segment.substring(m.range.last + 1))) {
                val day = slotMatch.groupValues[1].toInt()
                val startMinute = slotMatch.groupValues[2].toIntOrNull() ?: continue
                val endMinute = slotMatch.groupValues[3].toIntOrNull() ?: continue
                val startNode = periods.indexOfFirst { it?.first == startMinute } + 1
                val endNode = periods.indexOfFirst { it?.second == endMinute } + 1
                if (day !in 1..7 || startNode <= 0 || endNode < startNode) continue
                for (range in compactWeeks(weeks)) {
                    out += JwCourse(name = name, room = room, teacher = teacher, day = day,
                        startNode = startNode, endNode = endNode,
                        startWeek = range.first, endWeek = range.second, type = range.third)
                }
            }
        }
        return out
    }

    internal fun parseWeekBitmap(bitmap: String): Triple<Int, Int, Int>? {
        val bits = bitmap.trim()
        if (!bits.matches(Regex("""[01]{1,30}"""))) return null
        return compactWeeks(bits.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }).singleOrNull()
    }

    private fun compactWeeks(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        val pending = weeks.distinct().sorted().toMutableSet()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        while (pending.isNotEmpty()) {
            val first = pending.minOrNull() ?: break
            val step = if (first + 1 in pending) 1 else if (first + 2 in pending) 2 else 1
            var last = first
            while (last + step in pending) last += step
            for (week in first..last step step) pending.remove(week)
            result += Triple(first, last, if (step == 2) if (first % 2 == 1) 1 else 2 else 0)
        }
        return result
    }

    override fun confidence(): Int {
        var hits = 0
        if (RE_COURSE_TABLE.containsMatchIn(source)) hits++
        val timePairs = RE_TIME_PAIR.findAll(source).count()
        if (timePairs >= 1) hits++
        if (RE_NEW_ACTIVITY.containsMatchIn(source) && source.contains("addActivityByTime(")) hits++
        if (source.contains("<script") && source.contains("language=\"JavaScript\"")) hits++
        return when {
            hits >= 4 -> 95
            hits >= 3 -> 80
            hits >= 2 -> 50
            hits >= 1 -> 20
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (RE_COURSE_TABLE.containsMatchIn(source)) add("ecupl-script:r5-newCourseTable")
        val timePairs = RE_TIME_PAIR.findAll(source).count()
        if (timePairs >= 1) add("ecupl-script:r6-timePairs($timePairs)")
        if (RE_NEW_ACTIVITY.containsMatchIn(source)) add("ecupl-script:r7-newActivity")
        if (source.contains("<script") && source.contains("language=\"JavaScript\"")) {
            add("ecupl-script:script-language-javascript")
        }
        if (isEmpty()) add("guard:no-ecupl-script-markers")
    }
}

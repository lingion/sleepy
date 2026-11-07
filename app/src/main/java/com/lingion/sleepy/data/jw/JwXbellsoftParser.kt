package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/** JZHandCourseInfoItem payload: djj is one period; dsz 1 is odd, 2 is all, others are even. */
class JwXbellsoftParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> {
        val rows = runCatching { JSONArray(source) }.getOrNull() ?: return emptyList()
        val courses = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                fun text(key: String): String = (row.opt(key) as? String).orEmpty()
                fun integer(key: String): Int? = row.opt(key)?.takeUnless { it == JSONObject.NULL }
                    ?.toString()?.trim()?.takeIf { Regex("-?[0-9]+").matches(it) }?.toIntOrNull()
                val name = text("kcmc")
                val day = integer("xqj") ?: continue
                val node = integer("djj") ?: continue
                val code = integer("dsz") ?: continue
                if (name.isBlank() || day !in 1..7 || node !in 1..30) continue
                val parity = when (code) { 1 -> 1; 2 -> 0; else -> 2 }
                val tokens = text("qmz").split(';', '；').filter { it.isNotBlank() }
                if (tokens.isEmpty()) continue
                val ranges = tokens.map { token ->
                    Regex("""\s*([0-9]+)(?:\s*-\s*([0-9]+))?\s*""").matchEntire(token)?.let { match ->
                        val first = match.groupValues[1].toIntOrNull()
                        val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull()
                        if (first != null && last != null && first in 1..366 && last in first..366) first to last else null
                    }
                }
                if (ranges.any { it == null }) continue
                for ((first, last) in ranges.filterNotNull()) {
                    if ((first..last).none { parity == 0 || it % 2 == parity % 2 }) continue
                    add(JwCourse(name, text("skdd"), text("jsxm"), day, node, node, first, last, parity))
                }
            }
        }.distinct()
        return courses.groupBy { it.copy(startWeek = 1, endWeek = 1, type = 0) }.values.flatMap { group ->
            val weeks = group.flatMap { course ->
                (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }
            }
            if (weeks.size == weeks.distinct().size) group else mergeCourseWeeks(group)
        }
    }
}

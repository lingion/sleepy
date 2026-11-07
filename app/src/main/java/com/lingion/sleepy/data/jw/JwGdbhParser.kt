package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** GDBH's week is the weekday; seq is a two-period slot, not a period number. */
class JwGdbhParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val rows = JSONObject(source).optJSONArray("data") ?: return@buildList
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val name = (row.opt("cname") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            fun integer(key: String) = row.opt(key)?.toString()?.toIntOrNull()
            val day = integer("week")?.takeIf { it in 1..7 } ?: continue
            val pair = integer("seq")?.takeIf { it in 1..6 } ?: continue
            val from = integer("startweek")?.takeIf { it in 1..366 } ?: continue
            val to = integer("endweek")?.takeIf { it in from..366 } ?: continue
            val credit = row.opt("xf")?.toString()?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0f
            add(JwCourse(name, (row.opt("croomno") as? String)?.trim().orEmpty(),
                (row.opt("name") as? String)?.trim().orEmpty(), day, pair * 2 - 1, pair * 2, from, to, credit = credit))
        }
    }.distinct()
    override fun confidence(): Int = if (source.contains("\"cname\"") && source.contains("\"seq\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("GDBH/data/seq") else emptyList()
}

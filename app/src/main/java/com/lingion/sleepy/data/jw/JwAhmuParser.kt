package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** Factory type ahmu uses AHNUInfo fields; skcd is a length and jsz is inclusive. */
class JwAhmuParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val rows = JSONArray(source)
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = (row.opt("kcmc") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            fun number(key: String) = row.opt(key)?.toString()?.toIntOrNull()
            val day = number("xqj")?.takeIf { it in 1..7 } ?: continue
            val first = number("sjd")?.takeIf { it in 1..30 } ?: continue
            val length = number("skcd")?.takeIf { it in 1..30 } ?: continue
            val last = first + length - 1
            if (last > 30) continue
            val from = number("qsz")?.takeIf { it in 1..366 } ?: continue
            val to = number("jsz")?.takeIf { it in from..366 } ?: continue
            add(JwCourse(name, (row.opt("jsmc") as? String)?.trim().orEmpty(),
                (row.opt("xm") as? String)?.trim().orEmpty(), day, first, last, from, to))
        }
    }.distinct()
    override fun confidence(): Int = if (source.trimStart().startsWith("[") && source.contains("\"qsz\"") && source.contains("\"skcd\"")) 90 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("AHNUInfo/qsz/jsz/skcd") else emptyList()
}

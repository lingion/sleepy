package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** The serialized keys differ from the upstream model's property names. Each row is one week. */
class JwFstvcParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val rows = JSONArray(source)
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val name = (row.opt("kcmc") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val week = row.opt("zc")?.toString()?.toIntOrNull()?.takeIf { it in 1..366 } ?: continue
            val day = row.opt("xqs")?.toString()?.toIntOrNull()?.takeIf { it in 1..7 } ?: continue
            // _nodesLi is an upstream serialization cache, never an authoritative schedule field.
            val nodes = row.opt("jcshow") as? String ?: continue
            for ((first, last) in schoolNodeRanges(nodes)) {
                add(JwCourse(name, (row.opt("skcdmc") as? String)?.trim().orEmpty(),
                    (row.opt("skjsxm") as? String)?.trim().orEmpty(), day, first, last, week, week,
                    note = (row.opt("sknl") as? String)?.trim().orEmpty()))
            }
        }
    }.distinct()
    override fun confidence(): Int = if (source.contains("\"jcshow\"") && source.contains("\"xqs\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("FSTVC/jcshow/zc/xqs") else emptyList()
}

package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** JLU graduate rows contain semicolon-separated, independently scheduled meetings. */
class JwJluPostParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val rows = JSONObject(source).optJSONObject("datas")?.optJSONObject("xsjxrwcx")?.optJSONArray("rows") ?: return@buildList
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = (row.opt("KCMC") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val schedule = row.opt("PKSJDD") as? String ?: continue
            val teacher = (row.opt("RKJS") as? String)?.trim().orEmpty()
            for (meeting in schedule.split(';', '；')) {
                val match = Regex("""\[?(.+?)\]?\s*星期([一二三四五六日七])\s*\[?([^\[\]]+)节\](.*)""")
                    .matchEntire(meeting.trim()) ?: continue
                val weeks = schoolWeekRanges(match.groupValues[1].trim())
                val day = "一二三四五六日".indexOf(match.groupValues[2].replace('七', '日')) + 1
                for ((first, last) in schoolNodeRanges(match.groupValues[3])) for ((from, to, type) in weeks) {
                    add(JwCourse(name, match.groupValues[4].trim(), teacher, day, first, last, from, to, type))
                }
            }
        }
    }.distinct()
    override fun confidence(): Int = if (source.contains("\"xsjxrwcx\"") && source.contains("\"PKSJDD\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("datas/xsjxrwcx/rows/PKSJDD") else emptyList()
}

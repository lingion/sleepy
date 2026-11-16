package com.lingion.sleepy.data.jw

import org.json.JSONArray

/** SHCC embeds course rows as JSON; teacher parentheses contain each meeting's week list. */
class JwShccParser(source: String) : JwParser(source) {
    private val data by lazy { JwScriptJson.find(source) { literal ->
        val rows = literal.value as? JSONArray ?: return@find false
        (0 until rows.length()).any { index -> rows.optJSONObject(index)?.let { row ->
            listOf("kcS", "js", "xqj", "djj", "skcd").all(row::has)
        } == true }
    } }
    override fun generateCourseList(): List<JwCourse> = buildList {
        val rows = data?.value as? JSONArray ?: return@buildList
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = (row.opt("kcS") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            fun number(key: String) = row.opt(key)?.toString()?.toIntOrNull()
            val day = number("xqj")?.takeIf { it in 1..7 } ?: continue
            val first = number("djj") ?: continue
            val length = number("skcd") ?: continue
            val last = explicitScheduleEnd(first, length) ?: continue
            val teacherWeeks = (row.opt("js") as? String)?.trim()?.replace('（', '(')?.replace('）', ')') ?: continue
            val match = Regex("""([^()]*)\((.*)\)""").matchEntire(teacherWeeks) ?: continue
            for ((from, to, type) in schoolWeekRanges(match.groupValues[2])) {
                add(JwCourse(name, (row.opt("jsmc") as? String)?.trim().orEmpty(), match.groupValues[1].trim(),
                    day, first, last, from, to, type))
            }
        }
    }.distinct()
    override fun confidence(): Int = if (data != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (data != null) listOf("shcc-course-schema") else emptyList()
}

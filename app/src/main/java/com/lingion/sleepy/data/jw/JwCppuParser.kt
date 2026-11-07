package com.lingion.sleepy.data.jw

import com.lingion.sleepy.util.DateUtils
import org.json.JSONObject
import java.time.temporal.ChronoUnit

/** CPPU supplies actual lesson dates and a two-period block number before @. */
class JwCppuParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = JSONObject(source)
        val start = JwSourceCalendar.parseDate(root.optString("startDate"))
            ?: throw IllegalArgumentException("缺少有效的开学日期")
        val monday = DateUtils.mondayOf(start)
        val rows = root.optJSONArray("rows") ?: return@buildList
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = (row.opt("KCMC") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val date = JwSourceCalendar.parseDate(row.optString("SKRQ"))
                ?: throw IllegalArgumentException("课程缺少有效的上课日期")
            val days = ChronoUnit.DAYS.between(monday, date)
            if (days !in 0L until 366L * 7) continue
            val week = (days / 7 + 1).toInt()
            val block = (row.opt("JC") as? String)?.substringBefore('@')?.trim()?.toIntOrNull()
                ?.takeIf { it in 1..15 } ?: continue
            val credit = row.opt("XF")?.toString()?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            add(JwCourse(name, (row.opt("DDMC") as? String)?.trim().orEmpty(),
                (row.opt("JS") as? String)?.trim().orEmpty(), date.dayOfWeek.value,
                block * 2 - 1, block * 2, week, week, credit = credit))
        }
    }.distinct()
    override fun confidence(): Int = if (source.contains("\"startDate\"") && source.contains("\"SKRQ\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("CPPU/startDate/SKRQ") else emptyList()
}

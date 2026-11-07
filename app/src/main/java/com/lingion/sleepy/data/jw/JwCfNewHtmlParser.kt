package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** WakeUp 6.6.2 cf_new: each lay-tips attribute contains a labelled lesson table. */
internal class JwCfNewHtmlParser(source: String) : JwParser(source) {
    private val tips by lazy {
        Jsoup.parse(source).select("[lay-tips]").map { it.attr("lay-tips") }
            .filter { it.contains("上课任务[") }
    }

    override fun generateCourseList(): List<JwCourse> = buildList {
        for (tip in tips) {
            val doc = Jsoup.parse(tip)
            val labels = doc.select("th")
            val values = doc.select("td")
            if (labels.size != values.size) continue
            val fields = labels.indices.associate { index ->
                labels[index].text().trim().removeSuffix("：").removeSuffix(":") to values[index].text().trim()
            }
            val rawName = fields["课程"].orEmpty()
            val name = rawName.substringAfter(']').ifBlank { rawName }
            val day = fields["星期"]?.toIntOrNull() ?: continue
            val nodes = Regex("""第?\s*([0-9]+)(?:\s*-\s*([0-9]+))?\s*节""")
                .matchEntire(fields["节次"].orEmpty()) ?: continue
            val first = nodes.groupValues[1].toIntOrNull() ?: continue
            val last = nodes.groupValues[2].ifEmpty { nodes.groupValues[1] }.toIntOrNull() ?: continue
            if (name.isBlank() || day !in 1..7 || first <= 0 || last < first) continue
            val weekRuns = parseWeeks(fields["上课周次"].orEmpty()) ?: continue
            for ((from, to, type) in weekRuns) add(JwCourse(
                name = name, teacher = fields["授课教师"].orEmpty(), room = fields["教学场地"].orEmpty(),
                day = day, startNode = first, endNode = last, startWeek = from, endWeek = to, type = type,
            ))
        }
    }.distinct()

    private fun parseWeeks(text: String): List<Triple<Int, Int, Int>>? {
        if (text.isBlank()) return null
        val runs = mutableListOf<Triple<Int, Int, Int>>()
        for (part in text.split(',', '，')) {
            val token = part.trim().replace('（', '(').replace('）', ')')
            val match = Regex("""第?\s*([0-9]+)(?:\s*-\s*([0-9]+))?\s*(单|双)?周?(?:\((单|双)周?\))?""")
                .matchEntire(token) ?: return null
            val from = match.groupValues[1].toIntOrNull() ?: return null
            val to = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return null
            if (from <= 0 || to < from) return null
            val parity = match.groupValues[3].ifEmpty { match.groupValues[4] }
            val type = when (parity) { "单" -> 1; "双" -> 2; else -> 0 }
            runs += Triple(from, to, type)
        }
        return runs
    }

    override fun confidence(): Int = if (tips.isEmpty()) 0 else 100
    override fun matchedFeatures(): List<String> = if (tips.isEmpty()) emptyList() else listOf("lay-tips", "上课任务[")
}

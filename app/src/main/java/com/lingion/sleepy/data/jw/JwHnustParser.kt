package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * HNUST timetable cells contain independent course/metadata/teacher/weeks/room blocks.
 * The current protocol does not filter by CSS visibility. Explicit legacy modes remain
 * available: zero selects hidden blocks, one selects visible blocks.
 */
class JwHnustParser(source: String, internal val oldQzType: Int = -1) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val table = Jsoup.parse(source).getElementById("kbtable") ?: return@buildList
        for (row in table.select("tr").filter { it.closest("table") == table }) {
            for ((day, cell) in row.children().filter { it.tagName() == "td" }.withIndex()) {
                if (day !in 1..7) continue
                for (div in cell.select("div[id]")) {
                    val pair = div.attr("id").substringBefore('-').toIntOrNull()?.takeIf { it in 1..15 } ?: continue
                    if (!div.attr("id").matches(Regex("""[0-9]+-[^\s]+"""))) continue
                    if (div.select("div[id]").any { it != div }) continue
                    val hidden = Regex("""(?:^|;)\s*display\s*:\s*none\s*(?:;|$)""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(div.attr("style"))
                    if (oldQzType == 0 && !hidden || oldQzType == 1 && hidden) continue
                    val fields = div.html().split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                        .map { Jsoup.parseBodyFragment(it).text().trim() }
                    var blockStart = 0
                    for (i in fields.indices) {
                        if (i < blockStart + 2 || i + 1 >= fields.size || !fields[i].contains('周')) continue
                        val start = blockStart
                        // Even an invalid week field ends its own block; never steal its name for the next course.
                        blockStart = i + 2
                        val headings = fields.subList(start, i - 1).dropWhile(String::isBlank)
                        val name = headings.firstOrNull()?.takeIf(String::isNotBlank) ?: continue
                        val weeks = schoolWeekRanges(fields[i])
                        for ((from, to, parity) in weeks) {
                            add(JwCourse(name, fields[i + 1], fields[i - 1], day, pair * 2 - 1, pair * 2,
                                from, to, parity, note = headings.drop(1).joinToString("\n")))
                        }
                    }
                }
            }
        }
    }.distinct()

    /** T8: kbtable + div(style) + div id 前段数字 = 100; 仅 kbtable = 60 */
    override fun confidence(): Int = try {
        val doc = org.jsoup.Jsoup.parse(source)
        val kbtable = doc.getElementById("kbtable") ?: return 0
        val hiddenDivs = doc.getElementsByAttribute("style")
        val hasIdDiv = doc.select("div[id]").isNotEmpty()
        when {
            hiddenDivs.isNotEmpty() && hasIdDiv -> 100
            hiddenDivs.isNotEmpty() -> 80
            else -> 60
        }
    } catch (e: Exception) { 0 }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("kbtable")) add("id=kbtable")
        if (source.contains("display:none") || source.contains("display: none")) add("div[style=display:none]")
        if (Regex("""div[^>]+id="\d+-\d+"""").containsMatchIn(source)) add("div id=N-M")
    }
}

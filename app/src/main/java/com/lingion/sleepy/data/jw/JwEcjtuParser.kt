package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** Declared ECJTU table_border layout. Explicit period lists may contain gaps. */
class JwEcjtuParser(source: String) : JwParser(source) {
    private val table by lazy { Jsoup.parse(source).selectFirst("table.table_border") }

    override fun generateCourseList(): List<JwCourse> {
        val sourceTable = table ?: return emptyList()
        val rows = legacyDhuEcjtuRows(sourceTable) ?: return emptyList()
        return mergeCourseWeeks(buildList {
            for (row in rows) {
                if (row.firstOrNull { it.column == 0 }?.element?.text()?.trim() == "节次") continue
                for (cell in row) {
                    if (cell.column !in 1..7 || cell.columnSpan != 1) continue
                    val lines = cell.element.html().split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                        .map { Jsoup.parseBodyFragment(it).text().trim() }.filter { it.isNotEmpty() }
                    var blockStart = 0
                    for ((index, line) in lines.withIndex()) {
                        if ('@' in line || index == blockStart) continue
                        val fields = line.split(Regex("\\s+"))
                        val previous = lines[index - 1]
                        // Invalid schedule lines still end their own block, so later names cannot inherit it.
                        val looksLikeSchedule = '@' in previous ||
                            (fields.size in 2..3 && fields.last().any { it.isDigit() })
                        if (!looksLikeSchedule) continue
                        val name = lines[blockStart]
                        val oldStart = blockStart
                        blockStart = index + 1
                        if (fields.size !in 2..3 || name.isBlank() || '@' in name) continue
                        val weeks = schoolWeekRanges(fields[fields.size - 2])
                        val nodes = schoolNodeRanges(fields.last())
                        if (weeks.isEmpty() || nodes.isEmpty()) continue
                        val teacher: String
                        val room: String
                        if (fields.size == 3) {
                            teacher = fields[0]
                            room = ""
                        } else if (index > oldStart + 1 && '@' in previous) {
                            teacher = previous.substringBefore('@').trim()
                            room = previous.substringAfter('@').trim()
                        } else {
                            teacher = ""
                            room = ""
                        }
                        for ((first, last) in nodes) {
                            for ((from, to, parity) in weeks) {
                                add(JwCourse(name, room, teacher, cell.column, first, last, from, to, parity))
                            }
                        }
                    }
                }
            }
        })
    }

    override fun confidence(): Int = if (table != null) 90 else 0
    override fun matchedFeatures(): List<String> = if (table != null) listOf("ECJTU/table_border") else emptyList()
}

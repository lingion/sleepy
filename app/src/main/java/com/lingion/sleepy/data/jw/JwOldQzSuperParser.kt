package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Structural old-QZ variants; source facts and licensing are recorded in the cross-verification ledger. */
class JwOldQzSuperParser(source: String) : JwParser(source) {
    private val doc = Jsoup.parse(source)
    private val weekNode = Regex("""^(.+?)周\[(\d{1,4}(?:[-~]\d{1,2})?)节]$""")
    private val range = Regex("""^(\d{1,2})(?:[-~](\d{1,2}))?$""")
    private val weekday = Regex("""^(?:星期|周)([一二三四五六日天])$""")
    private val fixedId = Regex("""^([1-5])-([1-7])-2$""")

    private fun bounds(text: String, max: Int): IntRange? {
        val m = range.matchEntire(text) ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[2].toIntOrNull() ?: start
        return (start..end).takeIf { start in 1..max && end in start..max }
    }

    private fun weeks(text: String): List<Triple<Int, Int, Int>> {
        val clean = text.trim().trim('(', ')', '[', ']', '（', '）').replace("周", "").replace(" ", "")
        val shared = when { clean.endsWith("单") -> 1; clean.endsWith("双") -> 2; else -> 0 }
        val tokens = clean.removeSuffix("单").removeSuffix("双").split(',', '，')
        val result = mutableListOf<Triple<Int, Int, Int>>()
        for (token in tokens) {
            val type = when { token.endsWith("单") -> 1; token.endsWith("双") -> 2; else -> shared }
            val b = bounds(token.removeSuffix("单").removeSuffix("双"), 64) ?: return emptyList()
            result += Triple(b.first, b.last, type)
        }
        return result
    }

    private fun fields(block: Element) = block.html().split(Regex("""(?i)<br\s*/?>""")).map { Jsoup.parse(it).text().trim() }
    private fun hidden(block: Element): Boolean = (listOf(block) + block.parents()).any {
        Regex("""(?i)(?:^|;)\s*display\s*:\s*none\s*(?:!important)?\s*(?:;|$)""").containsMatchIn(it.attr("style")) || it.hasAttr("hidden")
    }
    private fun blocks(cell: Element): List<Element> = cell.select("div").filter { it.select("div").none { child -> child !== it } }.ifEmpty { listOf(cell) }

    private fun add(result: MutableList<JwCourse>, name: String, teacher: String, room: String, day: Int, nodes: IntRange, weekText: String) {
        if (name.isBlank() || day !in 1..7 || nodes.first !in 1..30 || nodes.last !in nodes.first..30) return
        for ((start, end, type) in weeks(weekText)) result += JwCourse(name, room, teacher.trim('(', ')', '[', ']'), day, nodes.first, nodes.last, start, end, type)
    }

    override fun generateCourseList(): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        // Fixed cell ids explicitly encode section group and weekday (CCSU shape).
        for (cell in doc.select("[id]")) {
            val id = fixedId.matchEntire(cell.id()) ?: continue
            if (hidden(cell)) continue
            val f = fields(cell)
            val group = id.groupValues[1].toInt()
            for (i in f.indices step 5) {
                if (i + 4 >= f.size) continue
                add(result, f[i], f[i + 2], f[i + 4], id.groupValues[2].toInt(), group * 2 - 1..group * 2, f[i + 3])
            }
        }
        // JLICT: link title's seven fields, row index is a documented two-section group.
        doc.getElementById("kb")?.selectFirst("table")?.let { table ->
            val rows = table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } === table }
            for ((index, row) in rows.drop(1).withIndex()) {
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                if (cells.size != 8 || index >= 15) continue
                for (day in 1..7) for (link in cells[day].select("a[title]")) {
                    if (hidden(link)) continue
                    val f = link.attr("title").split(Regex("\\r?\\n")).map { it.substringAfter('：').trim() }
                    if (f.size >= 7) add(result, f[2], f[3], f[6], day, index * 2 + 1..index * 2 + 2, f[5])
                }
            }
        }
        doc.getElementById("kbtable")?.let { table ->
            val rows = table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } === table }
            val header = rows.firstOrNull { row -> row.children().any { weekday.matches(it.text()) } }
            val days = header?.children()?.map { weekday.matchEntire(it.text())?.groupValues?.get(1)?.let { d -> "一二三四五六日天".indexOf(d).let { if (it >= 6) 7 else it + 1 } } }
            for (row in rows) {
                if (row === header) continue
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                // Without headers only the documented label + seven-day layout is unambiguous.
                if (days == null && cells.size != 8) continue
                if (days != null && cells.size != days.size) continue
                for ((column, cell) in cells.withIndex()) {
                    val day = if (days != null) days[column] ?: continue else column
                    if (day !in 1..7) continue
                    for (block in blocks(cell)) {
                        if (hidden(block)) continue
                        val f = fields(block)
                        var recordStart = 0
                        for (i in f.indices) {
                            val m = weekNode.matchEntire(f[i])
                            if (m != null && i >= recordStart + 2 && i + 1 < f.size) {
                                val raw = m.groupValues[2]
                                val nodes = if (raw.length == 4 && raw.all(Char::isDigit)) bounds(raw.take(2) + "-" + raw.takeLast(2), 30) else bounds(raw, 30)
                                if (nodes != null) add(result, f[recordStart], f[i - 1], f[i + 1], day, nodes, m.groupValues[1])
                                recordStart = i + 2
                            } else if (m == null && f[i].endsWith("周") && i >= 3 && i + 1 < f.size) {
                                // CCUT: group-day id establishes nodes; never infer them from text order.
                                val group = Regex("""^([1-9]|1[0-5])-[1-7](?:-\d+)?$""").matchEntire(block.id())?.groupValues?.get(1)?.toIntOrNull() ?: continue
                                add(result, f[i - 3], f[i - 1], f[i + 1], day, group * 2 - 1..group * 2, f[i])
                                recordStart = i + 2
                            }
                        }
                    }
                }
            }
        }
        return result.distinct()
    }

    override fun confidence(): Int = if (generateCourseList().isNotEmpty()) 100 else if (doc.getElementById("kbtable") != null) 50 else 0
    override fun matchedFeatures(): List<String> = buildList {
        if (doc.getElementById("kbtable") != null) add("id=kbtable")
        if (doc.getElementById("kb") != null) add("old-qz:title-table")
        if (doc.select("[id]").any { fixedId.matches(it.id()) }) add("old-qz:fixed-cell-id")
    }
}

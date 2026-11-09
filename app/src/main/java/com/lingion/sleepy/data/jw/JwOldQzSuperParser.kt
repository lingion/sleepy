package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Structural old-QZ variants; source facts and licensing are recorded in the cross-verification ledger. */
class JwOldQzSuperParser(source: String, private val declaredForm: Form? = null) : JwParser(source) {
    enum class Form { JLICT, CCUT, CCSU, BASE }
    private val doc = Jsoup.parse(source)
    private val weekNode = Regex("""^(.+?)周\[([0-9]+(?:[-~][0-9]+)?)节]$""")
    private val range = Regex("""^([0-9]+)(?:[-~]([0-9]+))?$""")
    private val weekday = Regex("""^(?:星期|周)([一二三四五六日天])$""")
    private val fixedId = Regex("""^([1-5])-([1-7])-2$""")

    private fun bounds(text: String, max: Int): IntRange? {
        val m = range.matchEntire(text) ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = if (m.groupValues[2].isEmpty()) start else m.groupValues[2].toIntOrNull() ?: return null
        return (start..end).takeIf { start in 1..max && end in start..max }
    }

    private fun weeks(text: String): List<Triple<Int, Int, Int>> {
        val clean = text.trim().trim('(', ')', '[', ']', '（', '）').replace("周", "").replace(" ", "")
        val tokens = clean.split(',', '，')
        val result = mutableListOf<Triple<Int, Int, Int>>()
        for (token in tokens) {
            val type = when { token.endsWith("单") -> 1; token.endsWith("双") -> 2; else -> 0 }
            val b = bounds(token.removeSuffix("单").removeSuffix("双"), EXPLICIT_SCHEDULE_RESOURCE_BOUND) ?: return emptyList()
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
        if (name.isBlank() || day !in 1..7 || nodes.first !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND ||
            nodes.last !in nodes.first..EXPLICIT_SCHEDULE_RESOURCE_BOUND) return
        for ((start, end, type) in weeks(weekText)) result += JwCourse(name, room, teacher.trim('(', ')', '[', ']'), day, nodes.first, nodes.last, start, end, type)
    }

    override fun generateCourseList(): List<JwCourse> {
        declaredForm?.let { return parseForm(it) }
        // DEX order: JLICT, CCUT, CCSU, then explicit week-and-section records.
        // Alternate representations are fallback choices, never additive tables.
        for (form in Form.entries) {
            val courses = parseForm(form)
            if (courses.isNotEmpty()) return courses
        }
        return emptyList()
    }

    private fun parseForm(form: Form): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        // Fixed cell ids explicitly encode section group and weekday (CCSU shape).
        val fixedCells = doc.select("[id]").filter { fixedId.matches(it.id()) }
        for (cell in fixedCells.takeIf { form == Form.CCSU }.orEmpty()) {
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
        doc.getElementById("kb")?.selectFirst("table")?.takeIf { form == Form.JLICT }?.let { table ->
            val rows = table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } === table }
            for ((index, row) in rows.drop(1).withIndex()) {
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                if (cells.any { it.attr("rowspan").let { span -> span.isNotEmpty() && span != "1" } ||
                    it.attr("colspan").let { span -> span.isNotEmpty() && span != "1" } }) continue
                val nodes = explicitSchedulePair(index + 1) ?: continue
                for (day in 1..7) for (link in cells.getOrNull(day)?.select("a[title]").orEmpty()) {
                    if (hidden(link)) continue
                    val f = link.attr("title").split(Regex("\\r?\\n")).map { it.substringAfter('：').trim() }
                    if (f.size >= 7) add(result, f[2], f[3], f[6], day, nodes, jlictWeeks(f[5]))
                }
            }
        }
        doc.getElementById("kbtable")?.takeIf { form == Form.CCUT || form == Form.BASE }?.let { table ->
            val rows = table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } === table }
            val header = rows.firstOrNull { row -> row.children().any { weekday.matches(it.text()) } }
            val days = header?.children()?.map { weekday.matchEntire(it.text())?.groupValues?.get(1)?.let { d -> "一二三四五六日天".indexOf(d).let { if (it >= 6) 7 else it + 1 } } }
            for (row in rows) {
                if (row === header) continue
                val cells = row.children().filter { it.tagName() in setOf("td", "th") }
                // Source positions identify existing columns; absent trailing weekdays are skipped.
                if (cells.any { it.attr("rowspan").let { span -> span.isNotEmpty() && span != "1" } ||
                    it.attr("colspan").let { span -> span.isNotEmpty() && span != "1" } }) continue
                if (days != null && cells.size > days.size) continue
                for ((column, cell) in cells.withIndex()) {
                    val day = if (days != null) days[column] ?: continue else column
                    if (day !in 1..7) continue
                    for (block in blocks(cell)) {
                        if (hidden(block)) continue
                        val f = fields(block)
                        var recordStart = 0
                        var previousName = ""
                        for (i in f.indices) {
                            val m = weekNode.matchEntire(f[i])
                            if (form == Form.BASE && m != null && i >= 2 && i + 1 < f.size) {
                                val raw = m.groupValues[2]
                                val nodes = if (raw.length == 4 && raw.all(Char::isDigit))
                                    bounds(raw.take(2) + "-" + raw.takeLast(2), EXPLICIT_SCHEDULE_RESOURCE_BOUND)
                                else bounds(raw, EXPLICIT_SCHEDULE_RESOURCE_BOUND)
                                val name = if (i - recordStart >= 2) f[recordStart] else previousName
                                if (nodes != null) add(result, name, f[i - 1], f[i + 1], day, nodes, m.groupValues[1])
                                previousName = name
                                recordStart = i + 2
                            } else if (form == Form.CCUT && m == null && f[i].endsWith("周") && i >= 3 && i + 1 < f.size) {
                                // CCUT: group-day id establishes nodes; never infer them from text order.
                                val id = Regex("""^([0-9]+)-([1-7])(?:-\d+)?$""").matchEntire(block.id()) ?: continue
                                if (id.groupValues[2].toInt() != day) continue
                                val group = id.groupValues[1].toIntOrNull() ?: continue
                                val nodes = explicitSchedulePair(group) ?: continue
                                add(result, f[i - 3], f[i - 1], f[i + 1], day, nodes, f[i])
                                recordStart = i + 2
                            }
                        }
                    }
                }
            }
        }
        return result.distinct()
    }

    private fun jlictWeeks(text: String): String {
        val clean = text.replace("周", "").replace(" ", "")
        // A single trailing marker qualifies the entire title field in this form.
        val global = Regex("""^([0-9,，~\-]+)(单|双)$""").matchEntire(clean) ?: return text
        return global.groupValues[1].split(',', '，').joinToString(",") { it + global.groupValues[2] }
    }

    override fun confidence(): Int = if (generateCourseList().isNotEmpty()) 100 else if (doc.getElementById("kbtable") != null) 50 else 0
    override fun matchedFeatures(): List<String> = buildList {
        if (doc.getElementById("kbtable") != null) add("id=kbtable")
        if (doc.getElementById("kb") != null) add("old-qz:title-table")
        if (doc.select("[id]").any { fixedId.matches(it.id()) }) add("old-qz:fixed-cell-id")
    }
}

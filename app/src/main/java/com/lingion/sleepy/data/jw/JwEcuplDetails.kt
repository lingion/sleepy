package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

internal data class JwEcuplDetails(val name: String, val teacher: String, val credit: Float, val note: String)

internal fun ecuplDetails(source: String): Map<String, JwEcuplDetails> = buildMap {
    for (table in Jsoup.parse(source).select("table.listTable")) {
        val rows = table.select("tr")
        val headers = rows.firstOrNull()?.select("td,th")?.map { it.text().trim() }.orEmpty()
        val code = headers.indexOf("课程序号")
        val name = headers.indexOfFirst { it == "课程名称" || it == "课程名" }
        val teacher = headers.indexOf("教师")
        val credit = headers.indexOf("学分")
        val note = headers.indexOf("备注")
        if (listOf(code, name, teacher, credit, note).any { it < 0 }) continue
        for (row in rows.drop(1)) {
            val cells = row.select("td")
            if (cells.size <= maxOf(code, name, teacher, credit, note)) continue
            val id = cells[code].text().trim()
            if (id.isBlank()) continue
            put(id, JwEcuplDetails(cells[name].text().trim(), cells[teacher].text().trim(),
                cells[credit].text().toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f, cells[note].text().trim()))
        }
    }
}

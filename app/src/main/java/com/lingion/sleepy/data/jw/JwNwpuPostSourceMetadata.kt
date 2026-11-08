package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import java.time.LocalDate

/** School defaults accompany the parsed table; authenticated captured times take priority. */
internal object JwNwpuPostSourceMetadata {
    fun periods(source: String, month: Int = LocalDate.now().monthValue): List<Triple<Int, String, String>> {
        val table = Jsoup.parse(source).selectFirst("#sample-table-1") ?: return emptyList()
        val headers = table.selectFirst("thead tr")?.select("th")?.map { it.text() } ?: return emptyList()
        val timeColumn = headers.indexOfFirst { it.contains("上课时间") }
        if (timeColumn < 0) return emptyList()
        val times = table.select("tbody tr").mapNotNull { it.select("td").getOrNull(timeColumn)?.text() }
        if (times.isEmpty()) return emptyList()
        val changan = times.count { it.contains("长安") }
        val youyi = times.count { !it.contains("长安") && it.contains("友谊") }
        val ranges = when {
            changan >= youyi -> changanPeriods
            month in 5..9 -> youyiSummerPeriods
            else -> youyiWinterPeriods
        }
        return ranges.split(' ').mapIndexed { index, value ->
            Triple(index + 1, value.substringBefore('-'), value.substringAfter('-'))
        }
    }

    fun tableName(source: String): String? {
        val document = Jsoup.parse(source)
        if (document.selectFirst("#sample-table-1") == null) return null
        return document.selectFirst("#xq option[selected]")?.text()?.trim()
            ?.takeIf(String::isNotEmpty)?.let { "西工大研 $it" }
    }

    private const val changanPeriods = "08:30-09:15 09:25-10:10 10:30-11:15 11:25-12:10 12:20-13:05 13:05-13:50 14:00-14:45 14:55-15:40 16:00-16:45 16:55-17:40 19:00-19:45 19:55-20:40 20:40-21:25"
    private const val youyiWinterPeriods = "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 12:10-13:00 13:10-14:00 14:00-14:50 15:00-15:50 16:10-17:00 17:10-18:00 19:00-19:50 20:00-20:50 20:50-21:25"
    private const val youyiSummerPeriods = "08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 12:10-13:10 13:20-14:20 14:30-15:20 15:30-16:20 16:40-17:30 17:40-18:30 19:30-20:20 20:30-21:20 20:40-21:25"
}

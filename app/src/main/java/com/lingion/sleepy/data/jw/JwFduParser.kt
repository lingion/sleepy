package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup

/**
 * FDU undergraduate print-data, postgraduate results, and the older HTML course list.
 *
 * Interface facts: DanXi-Dev/DanXi 3c1f5f0, model/time_table.dart and the two
 * timetable repositories. Only undergraduate Sunday weeks move back one week:
 * JWGL starts its week on Sunday, while this model starts on Monday.
 * Postgraduate ZCBH starts with week one and KSJCDM identifies one period.
 */
class JwFduParser(source: String) : JwParser(source) {
    private val jsonElement by lazy { runCatching { Json.parseToJsonElement(source) }.getOrNull() }
    private val isJsonSource by lazy {
        jsonElement != null || source.trimStart().firstOrNull() in listOf('{', '[', '"')
    }
    private val undergraduateActivities: JsonArray? by lazy {
        val root = jsonElement as? JsonObject
        if (root == null || root.containsKey("results")) null else {
            val tables = root["studentTableVms"] as? JsonArray
            (tables?.firstOrNull() as? JsonObject)?.get("activities") as? JsonArray
        }
    }
    private val postgraduateResults: JsonArray? by lazy {
        val root = jsonElement as? JsonObject
        if (root == null || root.containsKey("studentTableVms")) null else {
            (root["results"] as? JsonArray)?.takeIf { rows ->
                rows.any { row -> row is JsonObject && row.keys.containsAll(listOf("KCMC", "ZCBH", "XQ", "KSJCDM")) }
            }
        }
    }
    private val tables by lazy { Jsoup.parse(source).select("table.table.table-hover") }

    override fun generateCourseList(): List<JwCourse> {
        if (isJsonSource) {
            undergraduateActivities?.let { return parseUndergraduate(it) }
            postgraduateResults?.let { return parsePostgraduate(it) }
            return emptyList()
        }
        return parseHtml()
    }

    private fun parseUndergraduate(activities: JsonArray): List<JwCourse> = activities.flatMap { element ->
        val activity = element as? JsonObject ?: return@flatMap emptyList()
        val name = activity.text("courseName")?.takeIf(String::isNotEmpty) ?: return@flatMap emptyList()
        val day = activity.integer("weekday")?.takeIf { it in 1..7 } ?: return@flatMap emptyList()
        val first = activity.integer("startUnit") ?: return@flatMap emptyList()
        val last = activity.integer("endUnit") ?: return@flatMap emptyList()
        if (first !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || last !in first..EXPLICIT_SCHEDULE_RESOURCE_BOUND)
            return@flatMap emptyList()
        val rawWeeks = activity["weekIndexes"] as? JsonArray ?: return@flatMap emptyList()
        val weeks = rawWeeks.map { value ->
            val week = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            if (week == null || week !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) return@flatMap emptyList()
            if (day == 7) week - 1 else week
        }.filter { it >= 1 }.distinct().sorted()
        val teacher = teachers(activity["teachers"]) ?: return@flatMap emptyList()
        val room = activity.optionalText("room") ?: return@flatMap emptyList()
        // The pinned timetable source does not establish credit or remark fields.
        weeks.map { week -> JwCourse(name, room, teacher, day, first, last, week, week) }
    }.distinct()

    private fun parsePostgraduate(results: JsonArray): List<JwCourse> = results.flatMap { element ->
        val row = element as? JsonObject ?: return@flatMap emptyList()
        val name = row.text("KCMC")?.takeIf(String::isNotEmpty) ?: return@flatMap emptyList()
        val day = row.integer("XQ")?.takeIf { it in 1..7 } ?: return@flatMap emptyList()
        val node = row.integer("KSJCDM")?.takeIf { it in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND }
            ?: return@flatMap emptyList()
        val bitmap = (row["ZCBH"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return@flatMap emptyList()
        if (bitmap.length !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND || bitmap.any { it != '0' && it != '1' })
            return@flatMap emptyList()
        val teacher = row.optionalText("JSXM") ?: return@flatMap emptyList()
        val room = row.optionalText("JASMC") ?: return@flatMap emptyList()
        bitmap.indices.filter { bitmap[it] == '1' }.map { index ->
            val week = index + 1
            JwCourse(name, room, teacher, day, node, node, week, week)
        }
    }.distinct()

    private fun parseHtml(): List<JwCourse> = buildList {
        for (table in tables) {
            var name = ""
            val body = table.selectFirst("tbody") ?: continue
            for (row in body.children().filter { it.tagName() == "tr" }) {
                val cells = row.children().filter { it.tagName() == "td" }
                when (cells.size) {
                    6 -> name = cells[1].text().trim()
                    4 -> Unit
                    else -> { name = ""; continue }
                }
                if (name.isEmpty()) continue
                val fields = cells.takeLast(4)
                val timing = Regex("""星期\s*([一二三四五六日天])\s*[：:]?\s*第\s*([0-9,，\s-]+)\s*节""")
                    .matchEntire(fields[1].text().trim()) ?: continue
                val day = when (timing.groupValues[1]) {
                    "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
                }
                val weeks = schoolWeekRanges(fields[2].text())
                for ((first, last) in schoolNodeRanges(timing.groupValues[2])) for ((from, to, type) in weeks) {
                    add(JwCourse(name, fields[0].text().trim(), "", day, first, last, from, to, type))
                }
            }
        }
    }.distinct()

    override fun confidence(): Int = when {
        undergraduateActivities != null || postgraduateResults != null -> 100
        !isJsonSource && tables.isNotEmpty() -> 100
        else -> 0
    }

    override fun matchedFeatures(): List<String> = when {
        undergraduateActivities != null -> listOf("fdu-undergraduate-print-data")
        postgraduateResults != null -> listOf("fdu-postgraduate-results")
        !isJsonSource && tables.isNotEmpty() -> listOf("declared-fdu-list")
        else -> emptyList()
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()

    private fun JsonObject.optionalText(key: String): String? =
        if (this[key] == null || this[key] == JsonNull) "" else text(key)

    private fun JsonObject.integer(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun teachers(value: JsonElement?): String? {
        if (value == null || value == JsonNull) return ""
        val array = value as? JsonArray ?: return null
        return array.map {
            (it as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return null
        }.filter(String::isNotEmpty).joinToString("/")
    }
}

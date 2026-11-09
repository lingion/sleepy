package com.lingion.sleepy.util

import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections

/**
 * Resolves a source combination against its complete, stable base timetable.
 *
 * [validate] accepts either native `node/start/end` base rows or source
 * `number/startTime/endTime` base rows. A base must use one shape throughout.
 * Combination differences always use the source shape. Output is complete native
 * timeJson, built by [TimeTableUtils.buildTimeJsonFromRows]. Invalid input throws
 * [IllegalArgumentException]; the lenient defaults in existing time helpers are
 * never used to repair it. Callers handle an absent combination before calling.
 */
object EffectivePeriodResolver {
    private const val MAX_PERIODS = 366
    private const val MAX_SOURCE_INTEGER = 9_007_199_254_740_991L
    private val clock = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
    private val date = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val integerLiteral = Regex("-?(?:0|[1-9][0-9]*)")
    private val nativeKeys = setOf("node", "start", "end")
    private val sourceKeys = setOf("number", "startTime", "endTime")

    /** Immutable validation result suitable for resolving several display dates. */
    class ValidatedSchedule internal constructor(
        val name: String?,
        val baseTimeJson: String,
        rules: List<Rule>
    ) {
        val publicSchedules: List<Rule> = Collections.unmodifiableList(rules.toList())

        /** Source order is priority order; bounds are inclusive, gaps use the base. */
        fun resolve(date: LocalDate): String = publicSchedules.firstOrNull {
            !date.isBefore(it.startDate) && !date.isAfter(it.endDate)
        }?.timeJson ?: baseTimeJson
    }

    /** Explicit minute durations are metadata, never instructions to rebuild clocks. */
    class Rule internal constructor(
        val name: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val defaultClassDuration: Long,
        val defaultBreakDuration: Long,
        differences: List<TimeSlotRow>,
        val timeJson: String
    ) {
        val timeSlots: List<TimeSlotRow> = Collections.unmodifiableList(differences.toList())
    }

    /** Validates the base and every rule, including rules not selected for today. */
    fun validate(baseJson: String, comboJson: String): ValidatedSchedule {
        val baseArray = parseJson(baseJson, "base") as? JsonArray
            ?: invalid("base must be an array")
        require(baseArray.size in 1..MAX_PERIODS) { "base must contain 1..$MAX_PERIODS periods" }
        val first = baseArray.first() as? JsonObject ?: invalid("base[0] must be an object")
        val sourceBase = "number" in first
        val base = parseRows(baseArray, sourceBase, "base")
        base.forEachIndexed { index, row ->
            require(row.node == index + 1) { "base period numbers must be ordered and consecutive from 1" }
        }
        validateCompleteRows(base, "base")
        val baseTimeJson = TimeTableUtils.buildTimeJsonFromRows(base)
        val combo = parseJson(comboJson, "combination") as? JsonObject
            ?: invalid("combination must be an object")
        val name = if ("name" in combo) string(combo, "name", "combination") else null
        val rulesArray = combo["publicSchedules"] as? JsonArray
            ?: invalid("combination.publicSchedules must be an array")
        val rules = rulesArray.mapIndexed { index, element ->
            val path = "publicSchedules[$index]"
            val rule = element as? JsonObject ?: invalid("$path must be an object")
            val ruleName = string(rule, "name", path)
            val start = parseDate(string(rule, "startDate", path), "$path.startDate")
            val end = parseDate(string(rule, "endDate", path), "$path.endDate")
            require(!end.isBefore(start)) { "$path.endDate must not precede startDate" }
            val classDuration = duration(rule, "defaultClassDuration", 45L, 1L, path)
            val breakDuration = duration(rule, "defaultBreakDuration", 10L, 0L, path)
            val differencesArray = rule["timeSlots"] as? JsonArray
                ?: invalid("$path.timeSlots must be an array")
            require(differencesArray.size <= base.size) { "$path contains too many difference periods" }
            val differences = parseRows(differencesArray, true, "$path.timeSlots")
            require(differences.map { it.node }.distinct().size == differences.size) {
                "$path contains duplicate difference period numbers"
            }
            require(differences.all { it.node in 1..base.size }) {
                "$path difference period numbers must exist in the base"
            }
            val byNumber = differences.associateBy { it.node }
            val merged = base.map { row ->
                byNumber[row.node]?.let { row.copy(start = it.start, end = it.end) } ?: row
            }
            validateCompleteRows(merged, path)
            Rule(ruleName, start, end, classDuration, breakDuration, differences,
                TimeTableUtils.buildTimeJsonFromRows(merged))
        }
        return ValidatedSchedule(name, baseTimeJson, rules)
    }

    fun resolve(baseJson: String, comboJson: String, date: LocalDate): String =
        validate(baseJson, comboJson).resolve(date)

    fun resolve(baseJson: String, comboJson: String, date: String): String =
        resolve(baseJson, comboJson, parseDate(date, "date"))

    private fun parseJson(raw: String, path: String): JsonElement = try {
        // Validate syntax first, then reject duplicate keys without depending on
        // the different duplicate-key behavior of Android and JVM org.json.
        Json.parseToJsonElement(raw).also { rejectDuplicateKeys(raw, path) }
    } catch (error: Exception) {
        throw IllegalArgumentException("$path must be valid JSON", error)
    }

    private fun rejectDuplicateKeys(raw: String, path: String) {
        val objects = ArrayDeque<MutableSet<String>>()
        var index = 0
        while (index < raw.length) {
            when (raw[index]) {
                '{' -> objects.addLast(mutableSetOf())
                '}' -> objects.removeLast()
                '"' -> {
                    val start = index++
                    while (raw[index] != '"') {
                        if (raw[index] == '\\') index++
                        index++
                    }
                    var next = index + 1
                    while (next < raw.length && raw[next].isWhitespace()) next++
                    if (next < raw.length && raw[next] == ':') {
                        val key = (Json.parseToJsonElement(raw.substring(start, index + 1)) as JsonPrimitive).content
                        require(objects.last().add(key)) { "$path contains duplicate field $key" }
                    }
                }
            }
            index++
        }
    }

    private fun parseRows(array: JsonArray, source: Boolean, path: String): List<TimeSlotRow> {
        val canonical = JSONArray()
        array.forEachIndexed { index, element ->
            val rowPath = "$path[$index]"
            val row = element as? JsonObject ?: invalid("$rowPath must be an object")
            require(row.keys.intersect(if (source) nativeKeys else sourceKeys).isEmpty()) {
                "$rowPath mixes source and native period fields"
            }
            val nodeKey = if (source) "number" else "node"
            val startKey = if (source) "startTime" else "start"
            val endKey = if (source) "endTime" else "end"
            val node = integer(row[nodeKey], "$rowPath.$nodeKey")
            require(node in 1L..MAX_PERIODS.toLong()) { "$rowPath.$nodeKey must be in 1..$MAX_PERIODS" }
            val start = string(row, startKey, rowPath)
            val end = string(row, endKey, rowPath)
            validateClockPair(start, end, rowPath)
            val converted = JSONObject().put("node", node.toInt()).put("start", start).put("end", end)
            if (!source && "edge" in row) {
                val edge = string(row, "edge", rowPath)
                require(edge == "before" || edge == "after") { "$rowPath.edge must be before or after" }
                converted.put("edge", edge)
            }
            canonical.put(converted)
        }
        // Fields and types are already complete and strict, so this real shared
        // parser cannot silently replace bad input with its default twelve rows.
        return TimeTableUtils.parseTimeSlotRows(canonical.toString())
    }

    private fun validateCompleteRows(rows: List<TimeSlotRow>, path: String) {
        var previousEnd: LocalTime? = null
        rows.forEach { row ->
            validateClockPair(row.start, row.end, "$path period ${row.node}")
            val start = LocalTime.parse(row.start)
            require(previousEnd?.let { !start.isBefore(it) } ?: true) {
                "$path period ${row.node} overlaps or precedes the previous period"
            }
            previousEnd = LocalTime.parse(row.end)
        }
    }

    private fun validateClockPair(start: String, end: String, path: String) {
        require(clock.matches(start) && clock.matches(end)) { "$path clocks must be strict HH:mm" }
        require(LocalTime.parse(end).isAfter(LocalTime.parse(start))) { "$path end must be later than start" }
    }

    private fun parseDate(raw: String, path: String): LocalDate {
        require(date.matches(raw)) { "$path must be strict YYYY-MM-DD" }
        val parsed = try {
            LocalDate.parse(raw)
        } catch (error: Exception) {
            throw IllegalArgumentException("$path must be an existing calendar date", error)
        }
        require(parsed.year >= 1) { "$path year must be positive" }
        return parsed
    }

    private fun string(objectValue: JsonObject, key: String, path: String): String {
        val value = objectValue[key] as? JsonPrimitive
        require(value != null && value.isString) { "$path.$key must be a string" }
        return value.content
    }

    private fun integer(element: JsonElement?, path: String): Long {
        val value = element as? JsonPrimitive
        require(value != null && !value.isString && integerLiteral.matches(value.content)) {
            "$path must be an integer"
        }
        return value.longOrNull ?: invalid("$path must be an integer")
    }

    private fun duration(rule: JsonObject, key: String, default: Long, minimum: Long, path: String): Long {
        if (key !in rule) return default
        return integer(rule[key], "$path.$key").also {
            require(it in minimum..MAX_SOURCE_INTEGER) { "$path.$key must be in $minimum..$MAX_SOURCE_INTEGER" }
        }
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
}

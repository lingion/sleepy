package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * The resumable part of the JW import confirmation flow.
 *
 * This is deliberately independent of Room, Compose, and Android lifecycle state so a
 * future screen or persistence adapter can consume it without changing the import model.
 */
data class JwImportDraftSnapshot(
    val school: JwSchoolInfo,
    val courses: List<JwCourse>,
    val periods: List<JwImportDraftPeriod>,
    val termStartDate: String = "",
    val tableName: String = "",
    /** Serialized SmartPeriodConfig; kept opaque to avoid coupling this contract to UI/entity classes. */
    val smartConfigJson: String = "",
    val phase: JwImportDraftPhase = JwImportDraftPhase.CONFIGURE_CONFIRM,
)

data class JwImportDraftPeriod(
    val node: Int,
    val start: String,
    val end: String,
)

enum class JwImportDraftPhase {
    WEBVIEW_LOGIN,
    CONFIGURE_CONFIRM,
}

/** Versioned JSON boundary for [JwImportDraftSnapshot]. */
object JwImportDraftCodec {
    const val SCHEMA_VERSION: Int = 1

    fun toJson(snapshot: JwImportDraftSnapshot): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("phase", snapshot.phase.name)
        put("school", schoolToJson(snapshot.school))
        put("courses", JSONArray().apply {
            snapshot.courses.forEach { put(courseToJson(it)) }
        })
        put("periods", JSONArray().apply {
            snapshot.periods.forEach { put(periodToJson(it)) }
        })
        put("termStartDate", snapshot.termStartDate)
        put("tableName", snapshot.tableName)
        put("smartConfigJson", snapshot.smartConfigJson)
    }.toString()

    /** Returns null for an unsupported schema or any invalid/incomplete external payload. */
    fun fromJson(json: String): JwImportDraftSnapshot? = runCatching {
        val root = JSONObject(json)
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null

        val phase = when (root.optString("phase", JwImportDraftPhase.CONFIGURE_CONFIRM.name)) {
            JwImportDraftPhase.WEBVIEW_LOGIN.name -> JwImportDraftPhase.WEBVIEW_LOGIN
            JwImportDraftPhase.CONFIGURE_CONFIRM.name -> JwImportDraftPhase.CONFIGURE_CONFIRM
            else -> return null
        }
        val school = schoolFromJson(root.optJSONObject("school") ?: return null) ?: return null
        val coursesJson = root.optJSONArray("courses") ?: return null
        val periodsJson = root.optJSONArray("periods") ?: return null
        val courses = buildList {
            for (index in 0 until coursesJson.length()) {
                add(courseFromJson(coursesJson.optJSONObject(index) ?: return null) ?: return null)
            }
        }
        val periods = buildList {
            for (index in 0 until periodsJson.length()) {
                add(periodFromJson(periodsJson.optJSONObject(index) ?: return null) ?: return null)
            }
        }

        JwImportDraftSnapshot(
            school = school,
            courses = courses,
            periods = periods,
            termStartDate = root.optString("termStartDate", ""),
            tableName = root.optString("tableName", ""),
            smartConfigJson = root.optString("smartConfigJson", ""),
            phase = phase,
        )
    }.getOrNull()

    private fun schoolToJson(school: JwSchoolInfo): JSONObject = JSONObject().apply {
        put("sortKey", school.sortKey)
        put("name", school.name)
        put("url", school.url)
        put("type", school.type ?: JSONObject.NULL)
        put("status", school.status)
        put("aliases", JSONArray().apply { school.aliases.forEach(::put) })
        put("sortKeyFull", school.sortKeyFull)
        put("enableFetch", school.enableFetch)
    }

    private fun schoolFromJson(json: JSONObject): JwSchoolInfo? {
        val sortKey = json.optString("sortKey", "")
        val name = json.optString("name", "")
        if (sortKey.isBlank() || name.isBlank()) return null
        val aliasesJson = json.optJSONArray("aliases")
        val aliases = if (aliasesJson == null) {
            emptyList()
        } else {
            buildList {
                for (index in 0 until aliasesJson.length()) {
                    val alias = aliasesJson.optString(index, "")
                    if (alias.isBlank()) return null
                    add(alias)
                }
            }
        }
        return JwSchoolInfo(
            sortKey = sortKey,
            name = name,
            url = json.optString("url", ""),
            type = json.optString("type", "").ifBlank { null },
            status = json.optString("status", JwSchoolInfo.STATUS_SUPPORTED),
            aliases = aliases,
            sortKeyFull = json.optString("sortKeyFull", ""),
            enableFetch = json.optBoolean("enableFetch", false),
        )
    }

    private fun courseToJson(course: JwCourse): JSONObject = JSONObject().apply {
        put("name", course.name)
        put("room", course.room)
        put("teacher", course.teacher)
        put("day", course.day)
        put("startNode", course.startNode)
        put("endNode", course.endNode)
        put("startWeek", course.startWeek)
        put("endWeek", course.endWeek)
        put("type", course.type)
    }

    private fun courseFromJson(json: JSONObject): JwCourse? {
        val course = JwCourse(
            name = json.optString("name", ""),
            room = json.optString("room", ""),
            teacher = json.optString("teacher", ""),
            day = json.optInt("day", 0),
            startNode = json.optInt("startNode", 0),
            endNode = json.optInt("endNode", 0),
            startWeek = json.optInt("startWeek", 0),
            endWeek = json.optInt("endWeek", 0),
            type = json.optInt("type", 0),
        )
        return course.takeIf {
            it.name.isNotBlank() && it.day in 1..7 &&
                it.startNode >= 1 && it.endNode >= it.startNode &&
                it.startWeek >= 1 && it.endWeek >= it.startWeek &&
                it.type >= 0
        }
    }

    private fun periodToJson(period: JwImportDraftPeriod): JSONObject = JSONObject().apply {
        put("node", period.node)
        put("start", period.start)
        put("end", period.end)
    }

    private fun periodFromJson(json: JSONObject): JwImportDraftPeriod? {
        val period = JwImportDraftPeriod(
            node = json.optInt("node", 0),
            start = json.optString("start", ""),
            end = json.optString("end", ""),
        )
        return period.takeIf {
            it.node >= 1 &&
                ((it.start.isBlank() && it.end.isBlank()) ||
                    (it.start.matches(Regex("^\\d{2}:\\d{2}$")) &&
                        it.end.matches(Regex("^\\d{2}:\\d{2}$")) && it.start < it.end))
        }
    }
}

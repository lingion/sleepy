package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** Provenance and presence-aware suggestions, kept separately from editable confirmed values. */
object JwSgSourceMetadata {
    fun capture(source: String, type: String?): String {
        if (type != JwProtocol.TYPE_SG_ASSET || !source.trimStart().startsWith("{")) return ""
        val root = JSONObject(source)
        if (root.opt("sleepySgAsset") != true) return ""
        val metadata = JSONObject().put("schemaVersion", 1)
            .put("config", root.getJSONObject("config"))
        if (root.has("adapter")) metadata.put("adapter", root.getJSONObject("adapter"))
        val json = metadata.toString()
        require(read(json) != null) { "Invalid source timetable metadata" }
        return json
    }

    /** Null means invalid, rather than a full config filled with invented defaults. */
    fun read(json: String): JSONObject? = runCatching {
        val root = JSONObject(json)
        if (root.opt("schemaVersion") != 1) return null
        val config = root.optJSONObject("config") ?: return null
        if (root.has("adapter")) {
            val adapter = root.optJSONObject("adapter") ?: return null
            for (key in listOf("schoolId", "adapterId", "name", "category", "importUrl")) {
                if (adapter.has(key) && adapter.opt(key) !is String) return null
            }
        }
        if (config.has("semesterStartDate") && !config.isNull("semesterStartDate")) {
            val date = config.opt("semesterStartDate") as? String ?: return null
            if (JwSourceCalendar.parseDate(date)?.toString() != date) return null
        }
        for ((key, bounds) in listOf(
            "semesterTotalWeeks" to 1L..366L, "firstDayOfWeek" to 1L..7L,
            "defaultClassDuration" to 1L..9_007_199_254_740_991L,
            "defaultBreakDuration" to 0L..9_007_199_254_740_991L,
        )) if (config.has(key)) {
            val value = config.opt(key)
            if (value !is Number) return null
            val integer = value.toString().toLongOrNull() ?: return null
            if (integer !in bounds) return null
        }
        root
    }.getOrNull()
}

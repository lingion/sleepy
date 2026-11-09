package com.lingion.sleepy.data.jw

import com.lingion.sleepy.util.DateUtils
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Calendar fields read from a declared protocol, before the user confirms the import. */
object JwSourceCalendar {
    fun resolveWeekCount(source: String, type: String?, schoolName: String = ""): Int = runCatching {
        if (type == JwProtocol.TYPE_NWPU_POST) return 22
        if (type == JwProtocol.TYPE_WIST && schoolName.contains("武汉船舶职业技术学院")) return 20
        val root = JSONObject(source)
        when (type) {
            JwProtocol.TYPE_SG_ASSET -> if (root.optBoolean("sleepySgAsset")) {
                root.optJSONObject("config")?.opt("semesterTotalWeeks")?.toString()?.toIntOrNull()
                    ?.takeIf { it in 1..366 } ?: 0
            } else 0
            JwProtocol.TYPE_THU -> JwThuCapturePayload.semesterJson(source)?.let {
                JSONObject(it).optInt("weekCount", 16)
            } ?: 0
            JwProtocol.TYPE_CCIBE -> root.optJSONObject("calendar")?.opt("allWeek")?.toString()?.toIntOrNull()
                ?.takeIf { it in 1..366 } ?: 0
            JwProtocol.TYPE_CF_NEW -> {
                val weeks = root.optJSONArray("requestedWeeks") ?: return 0
                if (!root.optBoolean("sleepyCfNtss") || weeks.length() == 0) return 0
                val values = (0 until weeks.length()).map { index ->
                    weeks.opt(index)?.toString()?.toIntOrNull()?.takeIf { it in 1..366 } ?: return 0
                }
                values.max()
            }
            else -> 0
        }
    }.getOrDefault(0)

    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT)

    internal fun parseDate(value: String?): LocalDate? = value?.trim()?.takeIf {
        Regex("""[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}""").matches(it)
    }?.let { runCatching { LocalDate.parse(it, dateFormat) }.getOrNull() }?.takeIf { it.year >= 1 }

    fun resolveStartDate(source: String, type: String?, capturedDate: String): String {
        if (type == JwProtocol.TYPE_SG_ASSET) {
            val config = runCatching { JSONObject(source).takeIf { it.optBoolean("sleepySgAsset") }
                ?.optJSONObject("config") }.getOrNull()
            if (config?.has("semesterStartDate") == true) {
                return parseDate(config.opt("semesterStartDate") as? String)
                    ?.let { DateUtils.mondayOf(it).toString() }.orEmpty()
            }
        }
        if (type == JwProtocol.TYPE_SHSMU) {
            val root = runCatching { JSONObject(source) }.getOrNull() ?: return ""
            return JwShsmuCaptureCalendar.resolve(root)?.toString().orEmpty()
        }
        val declared = runCatching {
            when (type) {
                JwProtocol.TYPE_CCIBE -> JSONObject(source).optJSONObject("calendar")?.optString("startTime")
                JwProtocol.TYPE_CPPU -> JSONObject(source).optString("startDate")
                else -> null
            }
        }.getOrNull()
        val date = parseDate(declared) ?: parseDate(capturedDate) ?: return ""
        return DateUtils.mondayOf(date).toString()
    }
}

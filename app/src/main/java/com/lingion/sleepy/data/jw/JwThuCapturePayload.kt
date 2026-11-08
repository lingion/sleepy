package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.jsoup.Jsoup

/** Carries one explicitly selected semester's public calendar alongside the captured HTML. */
object JwThuCapturePayload {
    private data class Payload(val html:String,val semesterJson:String)

    fun semester(html:String):String? {
        val values=Jsoup.parse(html).select("[name=p_xnxq]").map { it.`val`().trim() }.filter(String::isNotEmpty).distinct()
        return values.singleOrNull()?.takeIf { Regex("[0-9]+(?:-[0-9]+)*").matches(it) }
    }

    fun sameSemester(before: String, after: String): Boolean =
        semester(before)?.let { it == semester(after) } == true

    fun calendarUrl(html:String):String? = semester(html)?.let { "https://schedule-data.netlify.app/$it.json" }

    fun wrap(html:String,semesterJson:String):String {
        val selected=semester(html) ?: throw IllegalArgumentException("未找到唯一的清华学期字段")
        require(validCalendar(semesterJson)) { "清华学期日历数据无效" }
        return JSONObject().put("sleepyThuCapture",true).put("semester",selected).put("html",html)
            .put("semesterData",JSONObject(semesterJson)).toString()
    }

    fun htmlOrOriginal(source:String):String {
        val root=runCatching { JSONObject(source) }.getOrNull() ?: return source
        if(!root.has("sleepyThuCapture")) return source
        return read(root)?.html.orEmpty()
    }

    fun semesterJson(source:String):String? = runCatching { read(JSONObject(source))?.semesterJson }.getOrNull()

    private fun read(root:JSONObject):Payload? {
        if(root.opt("sleepyThuCapture")!=true) return null
        val html=root.opt("html") as? String ?: return null
        val selected=semester(html) ?: return null
        if(root.opt("semester")!=selected) return null
        val calendar=root.optJSONObject("semesterData")?.toString() ?: return null
        if(!validCalendar(calendar)) return null
        return Payload(html,calendar)
    }

    private fun validCalendar(raw:String):Boolean = runCatching {
        val root=JSONObject(raw)
        (root.has("weekCount") || root.has("reschedule")) && JwThuScriptParser.validSemesterData(raw)
    }.getOrDefault(false)
}

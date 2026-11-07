package com.lingion.sleepy.data.jw

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Absolute SHSMU lesson dates become teaching weeks only after an explicit calendar choice. */
object JwShsmuCaptureCalendar {
    enum class Basis { USER_CONFIRMED, EXISTING_TABLE }
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

    fun date(text: String): LocalDate? = text.takeIf {
        Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it)
    }?.let { runCatching { LocalDate.parse(it, format) }.getOrNull() }?.takeIf { it.year >= 1 }

    fun monday(text: String): LocalDate? = date(text)?.let {
        it.minusDays((it.dayOfWeek.value - 1).toLong())
    }

    fun isDatedCapture(source: String): Boolean = runCatching {
        JSONObject(source).opt("sleepyShsmuCapture") == true
    }.getOrDefault(false)

    fun resolve(root: JSONObject): LocalDate? {
        if (!root.has("sleepyShsmuCapture")) return JwUndergradClockParser.SHSMU_REFERENCE_DATE
        if (root.opt("sleepyShsmuCapture") != true) return null
        val rangeStart = date(root.optString("rangeStart")) ?: return null
        val rangeEnd = date(root.optString("rangeEnd")) ?: return null
        if (rangeEnd.isBefore(rangeStart)) return null
        val basis = runCatching { Basis.valueOf(root.optString("semesterStartBasis")) }.getOrNull() ?: return null
        val start = date(root.optString("semesterStartDate")) ?: return null
        return start.takeIf { basis in Basis.entries && it.dayOfWeek == DayOfWeek.MONDAY }
    }

    fun bind(source: String, firstWeekDate: String, basis: Basis): String {
        val root = JSONObject(source)
        require(root.opt("sleepyShsmuCapture") == true) { "不是上海交通大学医学院日期课表" }
        val start = monday(firstWeekDate) ?: error("请确认第一周的日期")
        val rangeStart = date(root.optString("rangeStart")) ?: error("缺少采集起始日期")
        val rangeEnd = date(root.optString("rangeEnd")) ?: error("缺少采集结束日期")
        require(!rangeEnd.isBefore(rangeStart)) { "采集日期范围无效" }
        val list = root.optJSONArray("list") ?: error("缺少日期课程")
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            if (item.optString("Curriculum").isBlank()) continue
            val lessonDate = date(item.optString("Start").take(10)) ?: continue
            val week = ChronoUnit.DAYS.between(start, lessonDate) / 7 + 1
            require(!lessonDate.isBefore(start) && week <= 366) {
                "第一周日期必须覆盖全部已采集课程，请核对开学日期"
            }
        }
        root.put("semesterStartDate", start.toString())
        root.put("semesterStartBasis", basis.name)
        return root.toString()
    }

    /** Each SHSMU parser row is one absolute dated lesson, never a recurring interval. */
    fun rebase(courses: List<JwCourse>, oldOrigin: String, firstWeekDate: String): List<JwCourse> {
        val old = date(oldOrigin)?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY }
            ?: error("日期课程缺少原始学期起点")
        val next = monday(firstWeekDate) ?: error("请确认第一周的日期")
        val shift = ChronoUnit.WEEKS.between(next, old).toInt()
        return courses.map { course ->
            require(course.type == 0 && course.startWeek == course.endWeek && course.day in 1..7) {
                "日期课程格式无效，请重新采集"
            }
            val week = course.startWeek + shift
            require(week in 1..366) { "第一周日期必须覆盖全部已采集课程，请核对开学日期" }
            course.copy(startWeek = week, endWeek = week)
        }
    }
}

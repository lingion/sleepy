package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.json.JSONArray

/** CCIBE ResponseData carries explicit week bounds; absent bounds do not mean week one. */
class JwCcibeParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = buildList {
        val root = JSONObject(source)
        for ((rows, requestWeek) in pages(root)) {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = (row.opt("kcmc") as? String)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val day = "一二三四五六日".indexOf(row.optString("xqj").trim().replace('七', '日').replace('天', '日')) + 1
                if (day !in 1..7 || row.optString("xqj").trim().length != 1) continue
                val from = requestWeek ?: row.opt("qsz")?.toString()?.toIntOrNull()?.takeIf { it in 1..366 } ?: continue
                val to = requestWeek ?: row.opt("jsz")?.toString()?.toIntOrNull()?.takeIf { it in from..366 } ?: continue
                for ((first, last) in schoolNodeRanges(row.optString("jc"))) {
                    add(JwCourse(name, (row.opt("jxdd") as? String)?.trim().orEmpty(),
                        (row.opt("jsxm") as? String)?.trim().orEmpty(), day, first, last, from, to))
                }
            }
        }
    }.distinct()

    private fun pages(root: JSONObject): List<Pair<JSONArray, Int?>> {
        if (!root.has("weeks")) return listOfNotNull(root.optJSONArray("wdkb")?.let { it to null })
        val calendar = root.optJSONObject("calendar") ?: error("缺少学期日历")
        val count = calendar.opt("allWeek")?.toString()?.toIntOrNull()?.takeIf { it in 1..366 }
            ?: error("缺少有效的学期周数")
        val id = (calendar.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: error("缺少学期标识")
        val pages = root.optJSONArray("weeks") ?: error("逐周课表格式不正确")
        require(pages.length() == count) { "逐周课表不完整" }
        val seen = mutableSetOf<Int>()
        return (0 until pages.length()).map { index ->
            val page = pages.getJSONObject(index)
            val week = page.opt("week")?.toString()?.toIntOrNull()?.takeIf { it in 1..count }
                ?: error("课表周次无效")
            require(seen.add(week)) { "逐周课表包含重复周次" }
            val data = page.optJSONObject("data") ?: error("缺少逐周课表数据")
            data.optJSONObject("calendar")?.let { require(it.optString("id") == id) { "逐周课表学期不一致" } }
            (data.optJSONArray("wdkb") ?: error("缺少课程数组")) to week
        }
    }
    override fun confidence(): Int = if (source.contains("\"wdkb\"") && source.contains("\"calendar\"")) 100 else 0
    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("CCIBE/calendar/wdkb") else emptyList()
}

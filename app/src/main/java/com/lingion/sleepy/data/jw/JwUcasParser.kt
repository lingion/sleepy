package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 中国科学院大学选课系统个人课表解析器。
 *
 * #18 (UCAS 适配) — 数据来源有两条路径:
 *
 * 1. **JSON 路径 (权威)**: ldiex/UCAS_Course_Schedule_Convertor 启发的契约 ——
 *    `selectedCourse.json` 列出学期所有课程, 每门课的 `{courseId}.json` 暴露
 *    `courseTimeList[]`, 每个元素的 `courseWeek` 是整数位图 (低位 = 第 1 周),
 *    `courseTime` 是低位周次编码 + 高位 12 bit 节次位图。解码约定:
 *
 *        weekBinary  = bin(int(courseWeek))[2:][::-1]
 *        timeBinary  = bin(int(courseTime))[2:]
 *        dayBits     = timeBinary[:-12]              // 高位段, 直接查表得星期 1..7
 *        nodeBits    = timeBinary[-12:][::-1]        // 12 bit 节次位图, 位 i=1 -> 第 i+1 节
 *
 *    dayBits 到星期 1..7 的映射采用 ldiex 原仓 hardcode 字典 (POSITIVE 证据),
 *    不发明新编码。 节次位图解析后取最小与最大节次作为 startNode / endNode。
 *
 * 2. **HTML 路径 (fallback)**: 服务端渲染的个人课表页面 `/course/personSchedule`。
 *    表头 `节次/星期`, `tbody > tr > th` 是节次号, `td` 中 `a[href*=/course/coursetime/]`
 *    是课程名。 当没有 JSON 时按当前学期 1-16 周占位导入 (provisional 常量)。
 *
 * 两条路径在 [confidence] / [matchedFeatures] 上是独立的: 命中任一即 confidence >= 90。
 */
class JwUcasParser(source: String) : JwParser(source) {
    companion object {
        /** HTML 路径 #18 详情页尚未捕获, 临时按当前学期 1-16 周占位 */
        const val PROVISIONAL_START_WEEK = 1
        const val PROVISIONAL_END_WEEK = 16

        /**
         * ldiex/UCAS_Course_Schedule_Convertor 实测 dayBits -> 星期 1..7 (POSITIVE 证据)。
         * dayBits 是 `courseTime` 二进制去掉末尾 12 bit 后剩下的前缀, 正序字典序。
         * 任何 dayBits 不在表内 → 视为异常数据, 跳过该 schedule。
         */
        val DAY_BITS: Map<String, Int> = linkedMapOf(
            "10" to 1,
            "11" to 1,
            "100" to 2,
            "110" to 3,
            "1000" to 4,
            "1010" to 5,
            "1100" to 6,
            "1110" to 7
        );

        /** 课程周次类型: 0=每周, 1=单周, 2=双周 —— 与 [JwCourse.type] 契约一致 */
        const val TYPE_DEFAULT = 0
        const val TYPE_ODD = 1
        const val TYPE_EVEN = 2
    }

    override fun generateCourseList(): List<JwCourse> {
        return parseJson() ?: parseHtml()
    }

    override fun confidence(): Int = when {
        // JSON 路径: courseTimeList 列表 + selectedCourse 学期索引同时出现
        source.contains("\"courseTimeList\"") &&
            source.contains("\"selectedCourse\"") -> 95
        // HTML 路径: 个人课表 + 课程详情链接
        source.contains("个人课表") &&
            source.contains("/course/coursetime/") -> 90
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("\"courseTimeList\"")) add("json:courseTimeList")
        if (source.contains("\"selectedCourse\"")) add("json:selectedCourse")
        if (source.contains("个人课表")) add("title:个人课表")
        if (source.contains("/course/coursetime/")) add("href:/course/coursetime/")
    }

    /**
     * JSON 路径: 在 source 中挑出 `courseTimeList` 数组, 逐 schedule 解码。
     * 期望 source 是拼合后的 JSON (selectedCourse + 各 courseId), 或单课 courseInfo.json。
     * 返回 null 表示 source 不含可解析 JSON, 调用方应回退 HTML 路径。
     */
    private fun parseJson(): List<JwCourse>? {
        val courseTimeList = extractCourseTimeList(source) ?: return null
        val result = mutableListOf<JwCourse>()
        for (index in 0 until courseTimeList.length()) {
            val item = courseTimeList.optJSONObject(index) ?: continue
            val name = item.optString("courseName").trim()
            val place = item.optString("coursePlace").trim()
            val weekInt = item.optIntOrNull("courseWeek") ?: continue
            val timeInt = item.optIntOrNull("courseTime") ?: continue
            if (name.isBlank()) continue

            val weeks = decodeWeeks(weekInt)
            if (weeks.isEmpty()) continue
            val decodedTime = decodeTime(timeInt) ?: continue
            val (day, startNode, endNode) = decodedTime
            val type = weeksToType(weeks)

            val (startWeek, endWeek) = weeks.first() to weeks.last()
            result += JwCourse(
                name = name,
                room = place,
                day = day,
                startNode = startNode,
                endNode = endNode,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type
            )
        }
        return result
    }

    /**
     * 兼容两种 JSON 形态:
     *   A. 拼合文档: 顶层含 `courseTimeList` 数组 (ldiex 的 selectedCourse + 各 courseId 拼合场景)
     *   B. 单课文档: 顶层 courseTimeList 或嵌套 data 字段
     */
    private fun extractCourseTimeList(raw: String): JSONArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // 跳过前导 HTML 注释 / 空白, 找首个 '{' 或 '[' 真正 JSON 起点
        // 优先 '{' — 顶层对象更常见, 避开注释里 `[2:]` 这类方括号误命中
        val objStart = trimmed.indexOf('{')
        val arrStart = trimmed.indexOf('[')
        val jsonStart = when {
            objStart >= 0 -> objStart
            arrStart >= 0 -> arrStart
            else -> return null
        }
        val jsonBody = trimmed.substring(jsonStart)
        return try {
            when (jsonBody.first()) {
                '{' -> {
                    val obj = JSONObject(jsonBody)
                    obj.optJSONArray("courseTimeList")
                        ?: obj.optJSONObject("data")?.optJSONArray("courseTimeList")
                }
                '[' -> JSONArray(jsonBody)
                else -> null
            }
        } catch (e: Exception) {
            // JSON 解析失败时返回 null 让调用方回退 HTML 路径; 不打日志避免导入侧噪音
            null
        }
    }

    /**
     * 把 courseWeek 整数转成位图, 取所有 bit=1 的位置 (1-indexed 周次)。
     * 例: courseWeek=21 (0b10101) → [1, 3, 5] (1-indexed)
     */
    private fun decodeWeeks(weekInt: Int): List<Int> {
        if (weekInt <= 0) return emptyList()
        val weeks = mutableListOf<Int>()
        var bit = 0
        var n = weekInt
        while (n > 0) {
            if (n and 1 == 1) weeks += bit + 1
            bit++
            n = n ushr 1
        }
        return weeks
    }

    /**
     * courseTime 整数 → (day, startNode, endNode)。解码规则见 class kdoc。
     * 返回 null 表示 dayBits 不在 [DAY_BITS] 内 (ldiex 字典 hardcode), 数据异常。
     */
    private fun decodeTime(timeInt: Int): Triple<Int, Int, Int>? {
        if (timeInt <= 0) return null
        val binary = java.lang.Integer.toBinaryString(timeInt)
        if (binary.length <= 12) return null
        val dayBits = binary.substring(0, binary.length - 12)
        val day = DAY_BITS[dayBits] ?: return null

        val nodeBits = binary.substring(binary.length - 12).reversed()
        val nodes = mutableListOf<Int>()
        for ((idx, c) in nodeBits.withIndex()) {
            if (c == '1') nodes += idx + 1
        }
        if (nodes.isEmpty()) return null
        return Triple(day, nodes.first(), nodes.last())
    }

    /**
     * 把 [weeks] 列表归类为 type: 全部单周=1, 全部双周=2, 混合=0。
     */
    private fun weeksToType(weeks: List<Int>): Int = when {
        weeks.all { it % 2 == 1 } -> TYPE_ODD
        weeks.all { it % 2 == 0 } -> TYPE_EVEN
        else -> TYPE_DEFAULT
    }

    /**
     * HTML 路径: 服务端课表网格, 没有 JSON 数据时的 fallback。
     */
    private fun parseHtml(): List<JwCourse> {
        val table = Jsoup.parse(source).select("table").firstOrNull { table ->
            table.select("thead th").any { it.text().contains("节次/星期") } &&
                table.select("a[href*=/course/coursetime/]").isNotEmpty()
        } ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        table.select("tbody tr").forEach { row ->
            val node = row.selectFirst("th")?.text()?.trim()?.toIntOrNull() ?: return@forEach
            row.select("> td").forEachIndexed { index, cell ->
                val day = index + 1
                cell.select("a[href*=/course/coursetime/]").map { it.text().trim() }
                    .filter { it.isNotBlank() }.distinct().forEach { name ->
                        result += JwCourse(
                            name = name, day = day, startNode = node, endNode = node,
                            startWeek = PROVISIONAL_START_WEEK,
                            endWeek = PROVISIONAL_END_WEEK
                        )
                    }
            }
        }
        return mergeAdjacentRows(result)
    }

    private fun mergeAdjacentRows(courses: List<JwCourse>): List<JwCourse> = courses
        .groupBy { listOf(it.name, it.day, it.startWeek, it.endWeek, it.type) }
        .flatMap { (_, group) ->
            group.sortedBy { it.startNode }.fold(mutableListOf<JwCourse>()) { merged, course ->
                val previous = merged.lastOrNull()
                if (previous != null && previous.endNode + 1 == course.startNode) {
                    merged[merged.lastIndex] = previous.copy(endNode = course.endNode)
                } else merged += course
                merged
            }
        }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
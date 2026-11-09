package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject

/**
 * 新青果 NTSS (FullCalendar 形态) 课表 JSON 解析器 (type=cf_new)。
 *
 * 登记学校：江西中医药大学 (jiaowu.jxutcm.edu.cn)。原采集包未归档，当前用合成响应验证。
 * 与其它 [JwParser] 子类不同：source 不是 HTML，而是抓取侧 (CF_NEW_FETCH_JS) 组装的
 * 组合 JSON 信封 —
 *
 * ```
 * {"sleepyCfNtss":true,"xnxqdm":"202601","startDate":"2026-09-07",
 *  "weeks":[{"week":1,"rows":[{…}]},…],
 *  "periods":[{"node":1,"start":"08:30","end":"09:10"},…]}
 * ```
 *
 * rows 为 POST /new/student/xsgrkb/getCalendarWeekDatas 原始行（JS 只透传不解码，
 * 跨语言 invariant）。现有适配的字段约定：
 *   kcmc=课名  teaxms=教师  jxcdmc=教学场地  xq=星期(1-7)  ps/pe=起止节（**零填充且可为空串**，
 *   空串时以行内 qssj/jssj 对 periods 节次时间反推节点）
 *   zc=**单周次**（"2"，与所在请求周一致 — 全学期周次归属由逐周请求的 week 决定；
 *   兼容旧假设的全学期串形态：zc 展开多于 1 个周次时以其为准）
 *   kcbh=课号  jxbmc=教学班  bapjxcd='1'=排课不用场地（jxcdmc 空）
 *
 * 抓取侧按页面明确周次逐周采集，兼容历史 bucket week=0 输入。同一门课在其 occurring
 * 的每周各有一行，本 parser 按唯一键 (kcbh|kcmc|teaxms|jxcdmc|xq|ps|pe|jxbmc) 聚合
 * **合并周次集合**，周次归属优先取行自身 zc（两种形态下都正确），缺失才回退所在请求周；
 * 复用 [JwChengFangParser.weekIntList2WeekBeanList] 折叠为 (start,end,type) 三元组。
 */
class JwCfNewParser(source: String) : JwParser(source) {

    /** 节次时间 (periods 行, JS 透传 businessHours) */
    private data class Period(val node: Int, val start: String, val end: String)

    /** 跨周聚合桶：同 key 行共享课务元数据, 周次集合逐周累加 */
    private data class Acc(
        val name: String,
        val room: String,
        val teacher: String,
        val day: Int,
        val startNode: Int,
        val endNode: Int,
        val startTime: String,
        val endTime: String,
        val weeks: MutableList<Int>,
    )

    override fun generateCourseList(): List<JwCourse> {
        val htmlParser = JwCfNewHtmlParser(source)
        if (htmlParser.confidence() > 0) return htmlParser.generateCourseList()
        val root = try { JSONObject(source) } catch (e: Exception) { return emptyList() }
        if (root.has("requestedWeeks")) validateCapture(root)
        val weeksArr = root.optJSONArray("weeks") ?: return emptyList()
        val periods = parsePeriods(root.optJSONArray("periods"))

        // 两遍扫描。第一遍: 收集全部行 + 从行自身派生 节次时间→节点 回退映射 —
        // ps/pe 空串的行 (如 操作系统) 依赖 periods (businessHours) 反推, 但真机上
        // 该变量可能抠不到; 此时用同响应里带 ps+qssj / pe+jssj 的行锚定时间→节点。
        val flatRows = arrayListOf<Pair<JSONObject, Int>>() // (row, bucketWeekNo)
        val startByTime = HashMap<String, MutableSet<Int>>()
        val endByTime = HashMap<String, MutableSet<Int>>()
        fun remember(map: MutableMap<String, MutableSet<Int>>, time: String, node: Int?) {
            if (node != null && node > 0 && time.isNotEmpty()) map.getOrPut(time) { linkedSetOf() }.add(node)
        }
        for (period in periods) {
            remember(startByTime, period.start, period.node)
            remember(endByTime, period.end, period.node)
        }
        for (w in 0 until weeksArr.length()) {
            val week = weeksArr.optJSONObject(w) ?: continue
            val weekNo = week.optInt("week", 0) // week=0 → 单请求全量 bucket, 周次全部来自行 zc
            val rows = week.optJSONArray("rows") ?: continue
            for (i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                flatRows += o to weekNo
                if (o.optString("xq", "").trim().toIntOrNull() !in 1..7) continue
                val ps = o.optString("ps", "").trim().toIntOrNull()
                val qssj = normTime(o.optString("qssj", ""))
                val pe = o.optString("pe", "").trim().toIntOrNull()
                val jssj = normTime(o.optString("jssj", ""))
                if (ps != null && pe != null && ps > 0 && pe >= ps && JwCourse.isValidTimeRange(qssj, jssj)) {
                    remember(startByTime, qssj, ps)
                    remember(endByTime, jssj, pe)
                }
            }
        }

        val acc = LinkedHashMap<String, Acc>()
        for ((o, weekNo) in flatRows) {
            val name = o.optString("kcmc", "").trim()
            if (name.isBlank()) continue
            val teacher = o.optString("teaxms", "").trim()
            val room = o.optString("jxcdmc", "").trim()
                .ifBlank { if (o.optString("bapjxcd", "") == "1") "不用场地" else "" }
            val day = o.optString("xq", "").trim().toIntOrNull() ?: continue
            if (day !in 1..7) continue
            // ps/pe 零填充且可为空串 — 空串时反推节点: businessHours periods →
            // 同响应行自锚定映射 (仍失败则丢行)
            val qssjN = normTime(o.optString("qssj", ""))
            val jssjN = normTime(o.optString("jssj", ""))
            if (o.optString("qssj", "").isNotBlank() && qssjN.isEmpty()) continue
            if (o.optString("jssj", "").isNotBlank() && jssjN.isEmpty()) continue
            if (qssjN.isNotEmpty() && jssjN.isNotEmpty() && !JwCourse.isValidTimeRange(qssjN, jssjN)) continue
            val ps = o.optString("ps", "").trim()
            val pe = o.optString("pe", "").trim()
            // Only absent coordinates may be inferred. Conflicting time maps have no unique answer.
            val startNode = (if (ps.isNotEmpty()) ps.toIntOrNull() else startByTime[qssjN]?.singleOrNull()) ?: continue
            val endNode = (if (pe.isNotEmpty()) pe.toIntOrNull() else endByTime[jssjN]?.singleOrNull()) ?: continue
            if (startNode <= 0 || endNode < startNode) continue
            val hasTime = JwCourse.isValidTimeRange(qssjN, jssjN)
            val startTime = if (hasTime) qssjN else ""
            val endTime = if (hasTime) jssjN else ""
            // 周次归属: 优先行自身 zc — 全量形态 (bucket week=0) 行 zc 即所属周;
            // 逐周兜底形态行 zc == 请求周, 同样正确; zc 缺失才回退所在请求周
            val zc = o.optString("zc", "").trim()
            val addWeeks = if (zc.isNotEmpty()) strictWeeks(zc) else listOf(weekNo).filter { it in 1..366 }
            if (addWeeks.isEmpty()) continue
            val key = listOf(
                o.optString("kcbh", ""), name, teacher, room,
                day.toString(), startNode.toString(), endNode.toString(),
                o.optString("jxbmc", ""),
                startTime, endTime,
            ).joinToString("|")
            val a = acc.getOrPut(key) {
                Acc(name, room, teacher, day, startNode, endNode, startTime, endTime, mutableListOf())
            }
            a.weeks.addAll(addWeeks)
        }

        val result = arrayListOf<JwCourse>()
        for (a in acc.values) {
            for (wb in JwChengFangParser.weekIntList2WeekBeanList(a.weeks.distinct().sorted())) {
                result += JwCourse(
                    name = a.name,
                    room = a.room,
                    teacher = a.teacher,
                    day = a.day,
                    startNode = a.startNode,
                    endNode = a.endNode,
                    startWeek = wb.first,
                    endWeek = wb.second,
                    type = wb.third,
                    startTime = a.startTime,
                    endTime = a.endTime,
                )
            }
        }
        return result
    }

    private fun parsePeriods(arr: JSONArray?): List<Period> {
        if (arr == null) return emptyList()
        val list = arrayListOf<Period>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val node = o.optInt("node", 0)
            if (node < 1) continue
            val start = normTime(o.optString("start", ""))
            val end = normTime(o.optString("end", ""))
            if (JwCourse.isValidTimeRange(start, end)) list += Period(node, start, end)
        }
        return list
    }

    /** "10:15:00"/"10:15" → "10:15" (小时位补零, 兼容 periods 与行内时间格式差异) */
    private fun normTime(t: String): String {
        val match = Regex("""([0-9]{1,2}):([0-9]{1,2})(?::([0-9]{2}))?""").matchEntire(t.trim()) ?: return ""
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        val s = match.groupValues[3].ifEmpty { "0" }.toInt()
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) return ""
        return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }

    private fun strictWeeks(text: String): List<Int> {
        val weeks = sortedSetOf<Int>()
        for (token in text.split(',')) {
            val m = Regex("""([0-9]+)(?:\s*[-~—]\s*([0-9]+))?""").matchEntire(token.trim()) ?: return emptyList()
            val from = m.groupValues[1].toIntOrNull() ?: return emptyList()
            val to = m.groupValues[2].ifEmpty { m.groupValues[1] }.toIntOrNull() ?: return emptyList()
            // Resource bound for malformed input, not a claimed official semester length.
            if (from <= 0 || to < from || to > 366) return emptyList()
            weeks.addAll(from..to)
        }
        return weeks.toList()
    }

    companion object {
        /**
         * 周次串 → 周次列表。逗号分隔的数字为主形态（"1,2,3"），
         * 兼容区间段（"1-16"）与混合（"1-8,10,12-16"）；非法 token 跳过。
         */
        internal fun parseWeekList(zcs: String): List<Int> {
            val rangeRe = Regex("""^(\d+)\s*[-~—]\s*(\d+)$""")
            return zcs.split(',').flatMap { tok ->
                val t = tok.trim()
                val r = rangeRe.find(t)
                if (r != null) {
                    val a = r.groupValues[1].toIntOrNull() ?: return@flatMap emptyList<Int>()
                    val b = r.groupValues[2].toIntOrNull() ?: return@flatMap emptyList<Int>()
                    if (a <= b) (a..b).toList() else emptyList()
                } else {
                    listOfNotNull(t.toIntOrNull())
                }
            }.filter { it >= 1 }
        }
    }

    /** T8: sleepyCfNtss 标记 + kcmc/zc 字段 = 100; 仅标记 = 70 */
    private fun validateCapture(root: JSONObject) {
        fun weekNumber(value: Any?): Int? = (value as? Number)?.toString()
            ?.takeIf { Regex("[0-9]+").matches(it) }?.toIntOrNull()?.takeIf { it in 1..366 }
        val requested = root.optJSONArray("requestedWeeks")
        require(requested != null && requested.length() > 0) { "Missing requested weeks" }
        val expected = (0 until requested.length()).map { weekNumber(requested.opt(it)) }
        require(expected.none { it == null } && expected.distinct().size == expected.size) { "Invalid requested weeks" }
        val buckets = root.optJSONArray("weeks")
        require(buckets != null && buckets.length() == expected.size) { "Incomplete week capture" }
        val actual = (0 until buckets.length()).map { index ->
            val bucket = buckets.optJSONObject(index)
            require(bucket != null && bucket.optJSONArray("rows") != null) { "Invalid week bucket" }
            weekNumber(bucket.opt("week"))
        }
        require(actual.none { it == null } && actual.distinct().size == actual.size &&
            actual.toSet() == expected.toSet()) { "Mismatched week capture" }
    }

    override fun confidence(): Int {
        if (JwCfNewHtmlParser(source).confidence() > 0) return 100
        if (!source.contains("sleepyCfNtss")) return 0
        val hasFields = source.contains("kcmc") && source.contains("\"zc\"")
        return if (hasFields) 100 else 70
    }

    override fun matchedFeatures(): List<String> {
        JwCfNewHtmlParser(source).matchedFeatures().takeIf { it.isNotEmpty() }?.let { return it }
        if (!source.contains("sleepyCfNtss")) return emptyList()
        return buildList {
            add("sleepyCfNtss")
            if (source.contains("weeks")) add("weeks")
            if (source.contains("kcmc")) add("字段=kcmc")
            if (source.contains("teaxms")) add("字段=teaxms")
            if (source.contains("jxcdmc")) add("字段=jxcdmc")
            if (source.contains("\"ps\"") && source.contains("\"pe\"")) add("字段=ps/pe")
        }
    }
}

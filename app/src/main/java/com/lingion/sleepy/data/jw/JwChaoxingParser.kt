package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 超星学习通/ChaoXing「综合教务管理系统」课表 JSON 解析器。
 *
 * 适配学校：吉林工商学院 (jwxt.jlbtc.edu.cn, 2026-09-05 采集包实锤 —
 * 页面页脚 "Powered by ChaoXing") 及其他超星综合教务部署。
 * 与 [JwCquParser] / [JwWiseduParser] 同类：source 不是 HTML，而是课表
 * API 的 JSON 响应（在 WebView 内通过 fetch 拿到，见
 * JwWebViewLoginScreen 的 chaoxing 分支 CHAOXING_FETCH_JS）。
 *
 * 数据来源（WebView 内 fetch 两段）：
 *   1) GET /pkgl/xskb/queryKbForGrdb?sf_request_type=ajax   → 个人课表 rows
 *      （无参数，服务端按会话取学期；学生须已在课表页会话内）
 *   2) GET /admin/api/getZclistByXnxq?xnxq=…&sf_request_type=ajax → 节次时间
 *      （部分部署路径无 /admin 前缀，fetch JS 按学校 URL 推断）
 *
 * 输入 JSON 形态（fetch JS 组装）：
 *   {"xnxq":"2026-2027-1","dqzc":1,
 *    "rows":[{"kcmc":…,"xjc":"1","xingqi":1,"rqxl":"101","zcstr":"1,2,..",
 *             "tmc":…,"croommc":…}],
 *    "periods":[{"jc":"1","kssj":"8:20","jssj":"9:05"}]}
 *
 * 字段映射（超星 → JwCourse）：
 *   kcmc    课程名（班级表形态带 <a onclick=openKckb(..)>，剥 HTML）→ name
 *   croommc 教室（同上剥 HTML；可空——体育课常无固定教室）       → room
 *   tmc     教师（同上剥 HTML）                                  → teacher
 *   xingqi  星期 1..7（int）；缺位回退 rqxl/100                  → day
 *   xjc     节号（字符串/数字皆容）；缺位回退 rqxl%100            → startNode=endNode
 *   zcstr   周次串 "1,2,3" 逗号形态 / "1-16" 区间形态             → 周次段
 *
 * 旧 /admin/pkgl/xskb/sdpkkbList 也可直接返回顶层数组，djc 是单个节号，
 * zc 为周列表或 "1-9(单)周" 等周次串，xingqi 仍为星期。
 * 来源：moeshin/ai-schedule-chaoxing，4650f598 的 provider 和 getWeeks。
 * 旧形态的 xqmc 校区附在教室后，避免同名教室跨校区混合。
 *
 * 合并规则：同(课名,星期,教室,教师,周次串)且节号连续的行 → 合并为
 * startNode..endNode 单条（超星按单节粒度返回行, 连堂课拆多行）。
 */
class JwChaoxingParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 剥离 <a href=.. onclick=..>名字</a> 等标签，留纯文本（班级表接口形态） */
    private fun stripHtml(s: String): String = Jsoup.parseBodyFragment(s).text().trim()

    /** Invalid explicit coordinates must not become a partially invented schedule. */
    private fun expandWeeks(value: JsonElement?): List<Int> {
        if (value is JsonArray) {
            val weeks = value.map {
                (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return emptyList()
            }
            if (weeks.any { it !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND }) return emptyList()
            return weeks.distinct().sorted()
        }
        val zcstr = (value as? JsonPrimitive)?.contentOrNull?.trim()?.removeSuffix("周")?.trim()
            ?: return emptyList()
        if (zcstr.isBlank()) return emptyList()
        val weeks = sortedSetOf<Int>()
        for (part in zcstr.split(',', '，')) {
            val match = Regex("""([0-9]+)(?:\s*[-～~]\s*([0-9]+)(?:\s*\(([单双])\))?)?""").matchEntire(part.trim())
                ?: return emptyList()
            val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
            if (first <= 0 || last < first || last > EXPLICIT_SCHEDULE_RESOURCE_BOUND) return emptyList()
            val parity = when (match.groupValues[3]) { "单" -> 1; "双" -> 0; else -> null }
            weeks.addAll((first..last).filter { parity == null || it % 2 == parity })
        }
        return weeks.toList()
    }

    /** 周次列表 → 连续段 [(startWeek, endWeek, type)]，口径与 [JwWiseduParser.weekRuns] 一致：
     *  单连续段=每周(0)；整体等差 step=2=单周(1)/双周(2)；其余拆多段每段 0。 */
    private fun weekRuns(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var prev = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == prev + 1) prev = w
            else {
                runs += start to prev
                start = w
                prev = w
            }
        }
        runs += start to prev
        if (runs.size == 1) return listOf(Triple(runs[0].first, runs[0].second, 0))
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            val type = if (weeks.first() % 2 == 1) 1 else 2
            return listOf(Triple(weeks.first(), weeks.last(), type))
        }
        return runs.map { Triple(it.first, it.second, 0) }
    }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source) }.getOrNull()
            ?: return emptyList()
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> root["rows"] as? JsonArray
            else -> null
        } ?: return emptyList()

        data class Row(
            val name: String, val day: Int, val node: Int,
            val weeks: List<Int>, val room: String, val teacher: String
        )

        val parsed = mutableListOf<Row>()
        for (el in rows) {
            val o = el as? JsonObject ?: continue
            fun str(k: String): String =
                (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            fun field(vararg keys: String): JsonElement? = keys.firstNotNullOfOrNull { key ->
                o[key]?.takeUnless { it == JsonNull || it is JsonPrimitive && it.contentOrNull?.isBlank() == true }
            }
            fun integer(value: JsonElement?): Int? = (value as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

            val name = stripHtml(str("kcmc"))
            if (name.isBlank()) continue

            // 星期: xingqi 优先, 缺位回退 rqxl 前缀 (rqxl = 星期*100 + 节号)
            val explicitDay = field("xingqi")
            val day = (if (explicitDay != null) integer(explicitDay) else integer(field("rqxl"))?.div(100)) ?: continue
            if (day !in 1..7) continue

            // Each djc is one period, as in the source provider's sections=[djc].
            // Only absent fields permit fallback; malformed explicit coordinates are rejected.
            val explicitNode = field("xjc", "djc")
            val node = (if (explicitNode != null) integer(explicitNode) else
                integer(field("rqxl"))?.let { if (it > 99) it % 100 else it }) ?: continue

            if (node !in 1..EXPLICIT_SCHEDULE_RESOURCE_BOUND) continue

            val weeks = expandWeeks(field("zcstr", "zc"))
            if (weeks.isEmpty()) continue

            val campus = if (field("xjc") == null && field("djc") != null) stripHtml(str("xqmc")) else ""
            val room = stripHtml(str("croommc")) + if (campus.isNotBlank()) "（$campus）" else ""

            parsed.add(
                Row(
                    name = name, day = day, node = node, weeks = weeks,
                    room = room, teacher = stripHtml(str("tmc"))
                )
            )
        }

        // Keep each week's node set attached to the teacher and room supplied by that row.
        // Taking independent unions of weeks and nodes would create a Cartesian product.
        data class Key(val name: String, val day: Int, val teacher: String, val room: String)
        val result = mutableListOf<JwCourse>()
        for ((key, group) in parsed.groupBy { Key(it.name, it.day, it.teacher, it.room) }) {
            val nodesByWeek = sortedMapOf<Int, java.util.SortedSet<Int>>()
            for (row in group) for (week in row.weeks)
                nodesByWeek.getOrPut(week) { sortedSetOf() }.add(row.node)
            for ((nodes, entries) in nodesByWeek.entries.groupBy { it.value.toList() }) {
                val weeks = entries.map { it.key }.sorted()
                val blocks = mutableListOf<Pair<Int, Int>>()
                for (node in nodes) {
                    val previous = blocks.lastOrNull()
                    if (previous != null && node == previous.second + 1)
                        blocks[blocks.lastIndex] = previous.first to node
                    else blocks += node to node
                }
                for ((first, last) in blocks) for ((sw, ew, type) in weekRuns(weeks)) {
                    result.add(JwCourse(
                        name = key.name, room = key.room, teacher = key.teacher,
                        day = key.day, startNode = first, endNode = last,
                        startWeek = sw, endWeek = ew, type = type,
                    ))
                }
            }
        }
        result.sortWith(compareBy({ it.day }, { it.startNode }, { it.name }))
        return result
    }
}

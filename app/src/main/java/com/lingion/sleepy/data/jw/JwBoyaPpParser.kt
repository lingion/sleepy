package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 博雅研究生平台 (超星 chaoxingbook 旗下"博雅研究生", /pp/ 前端) 课表 JSON 解析器。
 *
 * 适配学校：燕山大学研究生 (yjsxt.ysu.edu.cn/pp, fid=41571, 2026-09-06
 * 采集包 + 逐周接口实采 390 行双实锤)。平台为多校 SaaS
 * (代码内含 YANSHANDAXUE / DALIANJIAOTONG 等校 fid 常量), 后续同产品
 * 学校可直接复用本 type。
 * 与 [JwCquParser] / [JwChaoxingParser] 同类：source 不是 HTML, 而是课表
 * API 的 JSON 响应 (WebView 内通过 fetch 拿到, 见 JwWebViewLoginScreen
 * 的 boya_pp 分支 BOYA_PP_FETCH_JS)。
 *
 * 数据来源 (WebView 内 fetch 三段, token 头 + 同源 Cookie):
 *   1) GET /api/microForm/term                     → 学期列表 (termBeginTime/weekEnd 在此)
 *   2) GET /api/schedule/class/setting/current?yearTerm=… → lessonConfig 节次时间
 *   3) GET /api/schedule/table/byStudent?whichWeek=N&yearTerm=…
 *      → 逐周排课行 (2026-09 实测: 不带 whichWeek 返回的是不完整子集,
 *        必须按 weekEnd 逐周抓; 每行自带 whichWeek int; 19 周 390 行)
 *
 * 输入 JSON 形态 (fetch JS 组装; 兼容裸 byStudent 数组与 {code,data} 信封):
 *   {"term":"2026-2027-1","rows":[{…}]}   ← fetch JS 组装形态
 *   [ {…}, … ]                            ← byStudent data 裸数组
 *   {"code":200,"data":[{…}]}             ← byStudent 完整信封
 *
 * 行字段映射 (博雅 → JwCourse):
 *   courseName        课程名                                → name
 *   courseTeacher[].name 教师 (多人按姓名排序后"、"连接)     → teacher
 *   classroomName     教室 (空则回退 classroomCode)          → room
 *   week              星期 1..7 (int)                        → day
 *   lessonNumber      起始节 (int; 每行单节粒度)             → startNode=endNode
 *   whichWeek         周次 (int; 容忍字符串, 回退 originWhichWeek) → 周次集合
 *
 * 合并规则：同 (课名, 星期, 教室, 教师集合) 分组; 组内各节号的周次集合
 * 完全相等且节号连续 → 合并为 startNode..endNode 单条 (研究生课常为
 * 同一周集中授课, 同槽位拆成单节多行)。周次段口径与 [JwChaoxingParser]
 * weekRuns 一致: 单连续段=每周(0); 整体等差 step=2=单周(1)/双周(2); 其余拆多段。
 * suspension(停课) / deleted 行剔除 — 停课课不该进个人课表。
 */
class JwBoyaPpParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 静态锚点快查 (confidence 不做完整解析, 避免 Registry 兜底时 N+1) */
    override fun confidence(): Int =
        if (source.contains("\"rows\"") && source.contains("\"courseName\"")) 90 else 0

    override fun matchedFeatures(): List<String> {
        val hits = mutableListOf<String>()
        if (source.contains("\"rows\"")) hits += "boya_pp:rows"
        if (source.contains("\"courseName\"")) hits += "boya_pp:courseName"
        if (source.contains("\"whichWeek\"")) hits += "boya_pp:whichWeek"
        if (source.contains("\"lessonNumber\"")) hits += "boya_pp:lessonNumber"
        return hits
    }

    /** 行元素取字符串 (缺键/非原始类型返回空串) */
    private fun str(o: kotlinx.serialization.json.JsonObject, k: String): String =
        o[k]?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()

    /** 行元素取 int: 原始数字 / 数字字符串 / [n,…] 数组首元素 皆容 */
    private fun intOf(o: kotlinx.serialization.json.JsonObject, vararg keys: String): Int? {
        for (k in keys) {
            val el = o[k] ?: continue
            val n = when (el) {
                is JsonPrimitive -> el.intOrNull ?: el.contentOrNull?.trim()?.toIntOrNull()
                is kotlinx.serialization.json.JsonArray ->
                    (el.firstOrNull() as? JsonPrimitive)?.intOrNull
                else -> null
            }
            if (n != null) return n
        }
        return null
    }

    override fun generateCourseList(): List<JwCourse> {
        val rows = extractRows() ?: return emptyList()

        data class Prim(
            val name: String, val day: Int, val node: Int, val week: Int,
            val room: String, val teacher: String
        )

        val prims = mutableListOf<Prim>()
        for (el in rows) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue

            // 停课/已删除行剔除
            if ((o["suspension"] as? JsonPrimitive)?.contentOrNull == "true") continue
            if ((o["deleted"] as? JsonPrimitive)?.contentOrNull == "true") continue

            val name = str(o, "courseName")
            if (name.isBlank()) continue

            val day = intOf(o, "week") ?: continue
            if (day !in 1..7) continue

            val node = intOf(o, "lessonNumber") ?: continue
            if (node < 1) continue

            val week = intOf(o, "whichWeek", "originWhichWeek") ?: continue

            val room = str(o, "classroomName").ifBlank { str(o, "classroomCode") }
            val teacher = runCatching {
                (o["courseTeacher"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList()))
                    .mapNotNull { t ->
                        (t as? kotlinx.serialization.json.JsonObject)?.get("name")
                            ?.let { n -> (n as? JsonPrimitive)?.contentOrNull?.trim() }
                            ?.takeIf { it.isNotBlank() }
                    }
                    .distinct().sorted().joinToString("、")
            }.getOrDefault("")

            prims.add(Prim(name, day, node, week, room, teacher))
        }
        if (prims.isEmpty()) return emptyList()

        // 分组: (课名, 星期, 教室, 教师) → 节号 → 周次集合
        val grouped = LinkedHashMap<Triple<String, Int, Pair<String, String>>, MutableMap<Int, MutableSet<Int>>>()
        for (p in prims) {
            val key = Triple(p.name, p.day, p.room to p.teacher)
            grouped.getOrPut(key) { LinkedHashMap() }
                .getOrPut(p.node) { mutableSetOf() }.add(p.week)
        }

        // 组内合并: 节号连续且周次集合相等 → 一条课块; 周次段展开
        val result = mutableListOf<JwCourse>()
        for ((key, nodeWeeks) in grouped) {
            val (name, day, roomTeacher) = key
            val nodes = nodeWeeks.keys.sorted()
            var i = 0
            while (i < nodes.size) {
                var j = i
                while (j + 1 < nodes.size &&
                    nodes[j + 1] == nodes[j] + 1 &&
                    nodeWeeks[nodes[j + 1]] == nodeWeeks[nodes[j]]
                ) j++
                val startNode = nodes[i]
                val endNode = nodes[j]
                val weeks = nodeWeeks[nodes[i]]!!.sorted()
                for ((sw, ew, type) in weekRuns(weeks)) {
                    result.add(
                        JwCourse(
                            name = name,
                            room = roomTeacher.first,
                            teacher = roomTeacher.second,
                            day = day,
                            startNode = startNode,
                            endNode = endNode,
                            startWeek = sw,
                            endWeek = ew,
                            type = type
                        )
                    )
                }
                i = j + 1
            }
        }
        return result
    }

    /** 从 source 提取排课行数组: {rows:[…]} / [ […] ] / {code,data:[…]} 三形态 */
    private fun extractRows() = runCatching {
        val root = json.parseToJsonElement(source)
        when {
            root is kotlinx.serialization.json.JsonArray -> root
            root is kotlinx.serialization.json.JsonObject -> when (val r = root["rows"]) {
                is kotlinx.serialization.json.JsonArray -> r
                else -> when (val d = root["data"]) {
                    is kotlinx.serialization.json.JsonArray -> d
                    is kotlinx.serialization.json.JsonObject -> d["rows"] as? kotlinx.serialization.json.JsonArray
                    else -> null
                }
            }
            else -> null
        }
    }.getOrNull()

    /** 周次列表 → 连续段 [(startWeek, endWeek, type)], 口径与 [JwChaoxingParser] 一致 */
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
}

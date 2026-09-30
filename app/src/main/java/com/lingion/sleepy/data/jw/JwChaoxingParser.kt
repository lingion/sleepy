package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * 合并规则：同(课名,星期,教室,教师,周次串)且节号连续的行 → 合并为
 * startNode..endNode 单条（超星按单节粒度返回行, 连堂课拆多行）。
 */
class JwChaoxingParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 剥离 <a href=.. onclick=..>名字</a> 等标签，留纯文本（班级表接口形态） */
    private fun stripHtml(s: String): String =
        if (s.contains('<')) s.replace(Regex("<[^>]*>"), "").trim() else s.trim()

    /** 周次串 → 连续段列表（1..16 逗号/区间/混合形态） */
    private fun expandWeeks(zcstr: String): List<Int> {
        val weeks = mutableSetOf<Int>()
        for (part in zcstr.split(',', '，')) {
            val t = part.trim()
            if (t.isEmpty()) continue
            if (t.contains('-') || t.contains('～') || t.contains('~')) {
                val se = t.split('-', '～', '~').map { it.trim().toIntOrNull() }
                if (se.size == 2 && se[0] != null && se[1] != null && se[0]!! <= se[1]!!) {
                    for (w in se[0]!!..se[1]!!) weeks.add(w)
                }
            } else {
                t.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks.sorted()
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
        val root = runCatching { json.parseToJsonElement(source).jsonObject }.getOrNull()
            ?: return emptyList()
        val rows = runCatching { root["rows"]!!.jsonArray }.getOrNull() ?: return emptyList()

        data class Row(
            val name: String, val day: Int, val node: Int,
            val weeks: List<Int>, val room: String, val teacher: String
        )

        val parsed = mutableListOf<Row>()
        for (el in rows) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            fun str(k: String): String =
                o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()

            val name = stripHtml(str("kcmc"))
            if (name.isBlank()) continue

            // 星期: xingqi 优先, 缺位回退 rqxl 前缀 (rqxl = 星期*100 + 节号)
            val day = str("xingqi").toIntOrNull()
                ?: str("rqxl").toIntOrNull()?.div(100)
                ?: continue
            if (day !in 1..7) continue

            // 节号: xjc 优先, 缺位回退 rqxl 后两位
            val node = str("xjc").toIntOrNull()
                ?: str("rqxl").toIntOrNull()?.let { if (it > 99) it % 100 else it }
                ?: continue

            val weeks = expandWeeks(str("zcstr"))
            if (weeks.isEmpty()) continue

            parsed.add(
                Row(
                    name = name, day = day, node = node, weeks = weeks,
                    room = stripHtml(str("croommc")), teacher = stripHtml(str("tmc"))
                )
            )
        }

        // 合并: 按 (课名,星期,教师) 分组 → 节号连续段拉通, 周次取并集, 同节多教室
        // 用 "/" 连接。必要性 (闽江师范 2026-09-30 采集实锤): 同一节因按周次换教室
        // 返回多行、各带不同周次串与教室 (如 5..15 周 A 室 + 17,19 周 B 室);
        // 若按 (含周次或含教室) 的行分组, 同一节会渲染成两张重叠课程卡。
        data class Key(val name: String, val day: Int, val teacher: String)
        val result = mutableListOf<JwCourse>()
        for ((key, group) in parsed.groupBy { Key(it.name, it.day, it.teacher) }) {
            val weeks = group.flatMap { it.weeks }.distinct().sorted()
            val roomsByNode = group.groupBy { it.node }
                .mapValues { (_, g) -> g.map { it.room }.filter { it.isNotBlank() }.distinct().joinToString("/") }
            val nodes = group.map { it.node }.distinct().sorted()
            var start = nodes.first()
            var prev = nodes.first()
            val blocks = mutableListOf<Pair<Int, Int>>()
            for (n in nodes.drop(1)) {
                if (n == prev + 1) prev = n
                else {
                    blocks += start to prev
                    start = n; prev = n
                }
            }
            blocks += start to prev
            for ((bs, be) in blocks) {
                val room = (bs..be).mapNotNull { roomsByNode[it]?.takeIf { r -> r.isNotEmpty() } }
                    .distinct().joinToString("/")
                // 周次段: 并集展开后可能多段 (非连续周次, 如 1-3周 + 8-9周)
                for ((sw, ew, type) in weekRuns(weeks)) {
                    result.add(
                        JwCourse(
                            name = key.name, room = room, teacher = key.teacher,
                            day = key.day, startNode = bs, endNode = be,
                            startWeek = sw, endWeek = ew,
                            type = type
                        )
                    )
                }
            }
        }
        result.sortWith(compareBy({ it.day }, { it.startNode }, { it.name }))
        return result
    }
}

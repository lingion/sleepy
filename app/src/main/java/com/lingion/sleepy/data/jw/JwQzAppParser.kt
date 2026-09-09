package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 强智移动教务 SPA 课表 JSON 解析器 (type=qz_app)。
 *
 * 适配学校: 河北资源环境职业技术学院 (jwpt.hebzyhj.edu.cn:1233, 2026-09-09 学生回传
 * 采集包实锤)。与其它 [JwParser] 子类不同: source 不是 HTML, 而是移动端 JSON API 的
 * 响应体 —
 *
 *   POST {ApiUrl}/student/curriculum?week=&kbjcmsid=   header: token: <JWT>
 *   → {"code":"1","Msg":"success~","data":[{date:[…7 天],courses:[…]}],needClassName,needClassRoomNub}
 *
 * 抓取方式: WebView 登录 SPA 后, JwWebViewLoginScreen 注入 QZ_APP_FETCH_JS 先 GET
 * /dist/serverconfig.json (免鉴权) 发现 ApiUrl, 再带 sessionStorage.Token 请求课表;
 * 结果经 __sleepyBridge.onWiseduResult({ok,data}) 桥回 Kotlin (与 wisedu/NEU/CQU
 * 同通道)。JS 侧只做 fetch 与透传, 禁止在 JS 里解码协议字段 (跨语言 invariant:
 * 解码语义唯一落点 = 本 parser, 契约测试锁死)。
 *
 * 字段映射 (强智移动教务 → [JwCourse]):
 *   courseName    → name
 *   teacherName   → teacher
 *   classroomNub  → room (楼-房完整房号, 回退 classroomName → location)
 *   classTime     → day/startNode/endNode — 编码 = 星期+起止节, 如 "10304" =
 *                   周一 3-4 节 (首位=星期 1-7, 后 4 位 = SS EE 各 2 位补零)
 *   classWeek     → 周次集合 "1-4,6-19" (区间+单周混合), 带洞 → 拆连续段
 *                   (整体等差 2 → 单/双周 type 1/2, 与 JwWiseduParser.weekRuns 同语义)
 *
 * date[] (本周 7 天 mxrq/zc) 深埋开学日期锚点, v1 不消费 (确认页手填学期开始日期,
 * 与 NEU/CQU fetch 流程一致); needClassName/needClassRoomNub 为 SPA 显示开关,
 * 与数据本体无关, 不消费。
 *
 * 未登录形态 {"code":"401","Msg":"非法访问：/student/curriculum"} → data 非数组 →
 * emptyList (Registry 按 0 课空学期处理, 真实登录态由 WebView 会话保证)。
 */
class JwQzAppParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = json.parseToJsonElement(source).jsonObject
        val data = (root["data"] as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        val grid = data.firstOrNull() as? kotlinx.serialization.json.JsonObject
        val rows = (grid?.get("courses") as? kotlinx.serialization.json.JsonArray) ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        for (el in rows) {
            if (el !is kotlinx.serialization.json.JsonObject) continue
            val o = el
            fun str(k: String): String = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

            val name = str("courseName")
            if (name.isBlank()) continue
            val teacher = str("teacherName")
            val room = sequenceOf(str("classroomNub"), str("classroomName"), str("location"))
                .firstOrNull { it.isNotBlank() } ?: ""
            val timeSpec = parseClassTime(str("classTime")) ?: continue
            val weeks = parseWeekSpec(str("classWeek").ifBlank { str("classWeekDetails") })

            for ((sw, ew, type) in weekRuns(weeks)) {
                result += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = timeSpec.first,
                    startNode = timeSpec.second,
                    endNode = timeSpec.third,
                    startWeek = sw,
                    endWeek = ew,
                    type = type,
                )
            }
        }
        return result
    }

    /**
     * classTime "10304" → (day=1, start=3, end=4)。
     * 首位 = 星期 (1-7, 禁 0); 后 4 位 = 起止节各 2 位补零; 非数字/长度不足 → null。
     */
    internal fun parseClassTime(v: String): Triple<Int, Int, Int>? {
        if ((v.length != 3 && v.length != 5) || v.any { it < '0' || it > '9' }) return null
        val day = v[0] - '0'
        if (day !in 1..7) return null
        val start = v.substring(1, 3).toIntOrNull() ?: return null
        val end = if (v.length == 5) v.substring(3, 5).toIntOrNull() ?: return null else start
        if (start < 1 || end < start) return null
        return Triple(day, start, end)
    }

    /**
     * 周次集合 "1-4,6-19" → 周次列表。区间 "a-b" + 单周 "a" 混合; 域外/非法 token 忽略。
     * classWeek 为空时回退 classWeekDetails 逗号位图串 (",1,2,3,…")。
     */
    internal fun parseWeekSpec(spec: String): List<Int> {
        val weeks = sortedSetOf<Int>()
        val weekToken = Regex("^(\\d+)-(\\d+)$")
        val single = Regex("^(\\d+)$")
        for (token in spec.split(',')) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val r = weekToken.find(t)
            if (r != null) {
                val lo = r.groupValues[1].toIntOrNull() ?: continue
                val hi = r.groupValues[2].toIntOrNull() ?: continue
                if (lo in 1..30 && hi in lo..30) {
                    for (w in lo..hi) weeks += w
                }
            } else {
                val one = single.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (one in 1..30) weeks += one
            }
        }
        return weeks.toList()
    }

    /**
     * 周次列表 → 连续段 [(startWeek, endWeek, type)]。语义与 JwWiseduParser.weekRuns
     * 一致: 单一连续段 → type=0; 整体等差 step=2 → 单周(1)/双周(2); 多段 → 逐段 type=0。
     */
    internal fun weekRuns(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var prev = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == prev + 1) {
                prev = w
            } else {
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

    override fun confidence(): Int = when {
        source.contains("\"classWeekDetails\"") && source.contains("\"classTime\"") -> 90
        source.contains("\"classWeek\"") && source.contains("\"classTime\"") -> 85
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("\"classWeekDetails\"")) add("classWeekDetails")
        if (source.contains("\"classTime\"")) add("classTime")
        if (source.contains("\"courses\":[")) add("courses[]")
        if (source.contains("\"code\"") && source.contains("\"Msg\"")) add("code/Msg envelope")
    }
}

package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 东南大学教务（正方 URP 系, ehall.seu.edu.cn/jwapp 网关）课表 JSON 解析器。
 *
 * 适配学校：东南大学。
 * 与 [JwCquParser] / [JwEams5Parser] 同类：source 不是 HTML，而是课表 JSON 数组（用户粘入或
 * WebView fetch 拿到）。本 parser 接收纯 JSON 数组字符串，JSON 解析 + 字段映射一次性完成。
 *
 * 数据来源：
 *   - v1（用户粘入）：直接 JSON 数组 `[ {...}, {...} ]`
 *   - v2（WebView fetch）：ehall.seu.edu.cn/jwapp 网关返回 { "datas": { "xskcb": { "rows": [...] } } }
 *
 * 字段映射（SEU JSON → JwCourse）：
 *   KCM 课程名   → name
 *   SKJS 教师     → teacher (null/"null"/"" → "")
 *   JASMC 教室    → room
 *   SKXQ 星期     → day (1=周一..7=周日)
 *   KSJC 起始节   → startNode (Int 直接)
 *   JSJC 结束节   → endNode (Int 直接)
 *   ZCMC 周次串   → 周次段（range/单双周/离散周）
 *   KCH  课程号   → 不映射（Sleepy 无 note 字段）
 *   JXBQH 教学班群号 → 不映射
 *
 * v1 → v2 升级：
 *   1. ZCMC 串解析支持三形态：范围 "1-16周"、单/双周 "1-10周(单)"、离散 "2,4,6周"
 *   2. 离散周逐个 emit JwCourse（type=0 每周）
 *   3. 单/双周 emit 一条 JwCourse（type=1 单周 / type=2 双周）
 *   4. v2 支持自动抓取：WebView fetch JS 注入 ehall.seu.edu.cn/jwapp 网关获取课表
 *
 * 外部佐证：sakimidare/SEUTimetable (Apache-2.0) TableParserUtils.kt parseWeekRange 算法
 * 参考 —— 代码自写（仅复用解析思路，类型签名与字段映射自定）。
 */
class JwSeuParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching {
            json.parseToJsonElement(source.trim())
        }.getOrNull() ?: return emptyList()

        // 支持两种数据格式：
        //   1. 直接数组（v1 用户粘入）：[ {...}, {...} ]
        //   2. 包装对象（v2 WebView fetch）：{ "datas": { "xskcb": { "rows": [...] } } }
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                // 尝试多层解包: datas -> xskcb -> rows
                val datas = root["datas"]?.jsonObject
                val xskcb = datas?.get("xskcb")?.jsonObject
                val rows = xskcb?.get("rows") as? JsonArray
                rows ?: root["data"] as? JsonArray ?: return emptyList()
            }
            else -> return emptyList()
        }

        val out = mutableListOf<JwCourse>()
        for (el in arr) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue

            val name = o["KCM"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()
            if (name.isBlank()) continue

            val teacher = o["SKJS"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty().cleanNull()
            val room = o["JASMC"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty().cleanNull()

            val day = o["SKXQ"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val startNode = o["KSJC"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val endNode = o["JSJC"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: startNode
            val zcmc = o["ZCMC"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

            for ((sw, ew, type) in parseWeekRanges(zcmc)) {
                out += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = day.coerceIn(1, 7),
                    startNode = startNode.coerceAtLeast(1),
                    endNode = endNode.coerceAtLeast(startNode),
                    startWeek = sw,
                    endWeek = ew,
                    type = type,
                )
            }
        }
        return out
    }

    /**
     * 把 ZCMC 串解析为 List of (startWeek, endWeek, type)。
     *
     * 支持三形态：
     *   - 范围："1-16周" → [(1,16,0)]
     *   - 单双周："2-15周(单)" → [(3,15,1)]  // 起点抬到首个奇数周
     *                 "1-16周(双)" → [(2,16,2)]  // 起点抬到首个偶数周
     *   - 离散："2,4,6周" → [(2,2,0),(4,4,0),(6,6,0)]
     *   - 单值："5周" → [(5,5,0)]
     *
     * 注：单/双周只修正起点周 (经 [JwParity.adjustedRange], 端点相等时 end
     * 抬回 start 防倒挂); endWeek 不做奇偶收缩 — UI 连续显示, type 字段
     * 驱动斜体/灰显, 周次命中由下游 inWeek 按奇偶判定。
     */
    internal fun parseWeekRanges(zcmc: String): List<Triple<Int, Int, Int>> {
        if (zcmc.isBlank()) return emptyList()
        val out = mutableListOf<Triple<Int, Int, Int>>()

        // 剥 "周" + 括号形态 "(单)/(双)"; 按逗号拆段 — 单/双标记按段内检测
        // (混合串 "1-8周(单),9-16周(双)" 两段各自成立, 整串 first-match 会标错后半段)
        val clean = zcmc.replace("周", "").replace("（", "(").replace("）", ")")
        val segments = clean.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

        for (seg in segments) {
            val typeInt = when {
                seg.contains("单") -> 1
                seg.contains("双") -> 2
                else -> 0
            }
            val bare = seg.replace("(单)", "").replace("(双)", "")
            if (bare.contains("-")) {
                val parts = bare.split("-", limit = 2).map { it.trim() }
                val a = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val b = parts.getOrNull(1)?.toIntOrNull() ?: a
                // 单双周端点修正 (共享 helper: 端点相等时不产出倒挂区间)
                val (sw, ew) = JwParity.adjustedRange(a, b, typeInt)
                out += Triple(sw, ew, typeInt)
            } else {
                val v = bare.toIntOrNull() ?: continue
                out += Triple(v, v, 0)
            }
        }
        return out
    }

    private fun String.cleanNull(): String =
        if (this.lowercase() == "null") "" else this

    override fun confidence(): Int = when {
        source.contains("\"KCM\"") && source.contains("\"ZCMC\"") -> 90
        source.contains("KCM") && source.contains("ZCMC") -> 70
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("\"KCM\"")) add("KCM")
        if (source.contains("\"ZCMC\"")) add("ZCMC")
        if (source.contains("\"SKXQ\"")) add("SKXQ")
    }
}

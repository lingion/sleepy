package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 金智 Wisedu jwapp 教务平台课表 JSON 解析器。
 *
 * 适配学校：哈尔滨工程大学 (jwgl.hrbeu.edu.cn) 及其他金智微应用平台。
 * 与其它 [JwParser] 子类不同：source 不是 HTML，而是课表 API 的 JSON 响应
 * （在 WebView 内通过 fetch 拿到，见 JwWebViewLoginScreen 的 wisedu 分支）。
 *
 * 数据来源：POST /jwapp/sys/wdkb/modules/xskcb/xskcb.do  (body: XNXQDM=学年学期)
 * 返回结构：{"code":"0","datas":{"xskcb":{"rows":[{...}]}}}
 *
 * 字段映射（教务 → JwCourse）：
 *   KCM   课程名      → name
 *   SKJS  上课教师    → teacher
 *   JASMC 教室名称    → room
 *   SKXQ  星期(1=周一..7=周日) → day
 *   KSJC  开始节次    → startNode
 *   JSJC  结束节次    → endNode
 *   SKZC  周次 bitmap → startWeek/endWeek/type（SKZC 第 i 位(0-indexed)='1' 表示第 (i+1) 周上课）
 *
 * 一门课多时段 = 多行（按行展开）；不连续周次 = 拆成多个连续段，每段一个 JwCourse。
 * 整体等差 step=2 的周次（如 11,13,15,17）压缩成单周/双周（type=1/2）。
 */
class JwWiseduParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = json.parseToJsonElement(source).jsonObject
        val rows = root["datas"]?.jsonObject
            ?.get("xskcb")?.jsonObject
            ?.get("rows")?.jsonArray
            ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        for (el in rows) {
            val o = el.jsonObject
            fun str(k: String): String = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            fun int(k: String): Int? = str(k).toIntOrNull()

            val name = str("KCM")
            if (name.isBlank()) continue
            val teacher = str("SKJS")
            val room = str("JASMC")
            val day = int("SKXQ") ?: continue
            val startNode = int("KSJC") ?: continue
            val endNode = int("JSJC") ?: startNode
            val skzc = str("SKZC")

            for ((sw, ew, type) in weekRuns(skzc)) {
                result += JwCourse(
                    name = name,
                    room = room,
                    teacher = teacher,
                    day = day.coerceIn(1, 7),
                    startNode = startNode.coerceAtLeast(1),
                    endNode = endNode.coerceAtLeast(startNode),
                    startWeek = sw,
                    endWeek = ew,
                    type = type
                )
            }
        }
        return result
    }

    /**
     * SKZC 周次 bitmap → 连续段列表 [(startWeek, endWeek, type)]。
     * type: 0=每周(连续段), 1=单周, 2=双周。
     *
     * - 单个连续段 → type=0。
     * - 整体等差 step=2（全奇或全偶）→ 压缩成单周(1)/双周(2)。
     * - 其余（多段非等差）→ 拆成多个连续段，每段 type=0。
     *
     * 此逻辑已用哈工程真实数据校验：解析周次集合与教务 ZCMC 100% 一致、无损还原。
     */
    private fun weekRuns(skzc: String): List<Triple<Int, Int, Int>> {
        val weeks = skzc.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
        if (weeks.isEmpty()) return emptyList()

        // 拆连续段
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

        if (runs.size == 1) {
            return listOf(Triple(runs[0].first, runs[0].second, 0))
        }
        // 整体单/双周（等差 step=2）
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            val type = if (weeks.first() % 2 == 1) 1 else 2
            return listOf(Triple(weeks.first(), weeks.last(), type))
        }
        return runs.map { Triple(it.first, it.second, 0) }
    }
}

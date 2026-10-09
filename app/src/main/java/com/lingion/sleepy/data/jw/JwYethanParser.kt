package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 西南交通大学 yhxt.swjtu.edu.cn（YETHAN/以专 逐专平台）课表 JSON 解析器。
 *
 * 适配学校：西南交通大学本部 (yhxt.swjtu.edu.cn)。
 * 与 [JwCquParser] 同类：source 不是 HTML，而是课表 API 的 JSON 响应
 * （在 WebView 内通过 fetch 拿到，见 JwWebViewLoginScreen 的 YETHAN 分支）。
 *
 * 数据来源：GET /yethan/common/course-schedule/student-course-schedule（零参数）
 *   headers: ytoken: <JWT>（cookie 取，localStorage 兜底；有值才发）+ .swjtu.edu.cn cookie
 *   登录态也可仅靠 Cookie 通过鉴权（credentials:'include'）；token 取不到不阻断。
 * 返回结构：{"code":"00000","data":[{…}]}
 *
 * 字段映射（教务 → JwCourse）：
 *   courseName → name
 *   staffName  → teacher（整名保留）
 *   classPlace{N}/classTime{N} 成对槽位 (1..40)：
 *     classTime  "7、10-12、14-15周 星期三 5节" → 周次段×星期×节次
 *     classPlace "X30547(犀浦)(教师甲)"        → room（教师括号截掉）
 *
 * classTime 文法（顿号枚举正解，2026-09-15 采集包 85/85 实锤）：
 *   ^([0-9、\-]+)周\s*星期(.)\s*([0-9\-]+)节$
 *   、枚举只作用于周次列表，整串共享一个「星期X 节」后缀
 *   （首版按 、切分各段独立解析 → 11/85 FAIL，已弃）。
 *
 * classPlace 括号判别（4 形态）：
 *   "X30547(犀浦)(教师甲)" → room=X30547(犀浦)（两个括号=第二个是教师）
 *   "X5218 (Martin Levesley)" / "X5218(Martin Levesley)" → room=X5218（无校区）
 *   "北区田径场(犀浦)" / "X2319(犀浦)" → 原样保留（单括号=校区）
 *   "Online(Anna Murawska)" / "Online" → room=Online
 *
 * 失效码（Arex 跨仓验证）：00000 成功；401/A0230/A0422 登录失效 → 空列表。
 * SM2 `_j` 加密只在 /register/…、/sport/…（选课），课表接口无 SM2，Sleepy 不需要。
 *
 * 外部佐证：AmaneSuzuha000/SWJTU_Login（YHXT API + ytoken cookie 直接证据）、
 * 1-nuo/swjtu-course-grabber（ytoken 请求头）、Arex-lbb/auto-course-grabber（A0422 失效码）。
 * 协议证据：docs/swjtu-cross-verify-2026-09-15/（11 仓）。
 */
class JwYethanParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 顿号枚举正解文法：周次列表(顿号/区间) + 共享一个「星期X 节」后缀 */
    internal val classTimeRegex = Regex("""^([0-9、\-]+)周\s*星期(.)\s*([0-9\-]+)节$""")

    private val dayCharToNumber = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '日' to 7, '天' to 7,
    )

    override fun generateCourseList(): List<JwCourse> {
        val rows = runCatching {
            json.parseToJsonElement(source).jsonObject["data"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        val result = mutableListOf<JwCourse>()
        for (el in rows) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            fun str(k: String): String =
                o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()

            val name = str("courseName")
            if (name.isBlank()) continue
            val teacher = str("staffName")

            for (i in 1..40) {
                val timeRaw = str("classTime$i")
                if (timeRaw.isBlank()) continue
                val place = roomOf(str("classPlace$i"))
                val m = classTimeRegex.matchEntire(timeRaw) ?: continue

                val dayChar = m.groupValues[2].firstOrNull() ?: continue
                val day = dayCharToNumber[dayChar] ?: continue
                val nodeStr = m.groupValues[3]
                val startNode = if (nodeStr.contains('-')) {
                    nodeStr.substringBefore('-').trim().toIntOrNull() ?: continue
                } else nodeStr.trim().toIntOrNull() ?: continue
                val endNode = if (nodeStr.contains('-')) {
                    nodeStr.substringAfter('-').trim().toIntOrNull() ?: startNode
                } else startNode

                for ((sw, ew) in weekRuns(m.groupValues[1])) {
                    result += JwCourse(
                        name = name,
                        room = place,
                        teacher = teacher,
                        day = day.coerceIn(1, 7),
                        startNode = startNode.coerceAtLeast(1),
                        endNode = endNode.coerceAtLeast(startNode),
                        startWeek = sw,
                        endWeek = ew,
                        type = 0
                    )
                }
            }
        }
        return result
    }

    /**
     * 周次列表（顿号枚举 + 区间混合，如 "7、10-12、14-15"）→ 连续段列表 [(startWeek, endWeek)]。
     * 顿号只切分列表项；项内 '-' 是区间；单值起止相同。
     */
    internal fun weekRuns(weekList: String): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        for (part in weekList.split('、')) {
            val p = part.trim()
            if (p.isBlank()) continue
            if (p.contains('-')) {
                val s = p.substringBefore('-').trim().toIntOrNull() ?: continue
                val e = p.substringAfter('-').trim().toIntOrNull() ?: continue
                if (s in 1..30 && e in s..30) runs += s to e
            } else {
                val w = p.toIntOrNull() ?: continue
                if (w in 1..30) runs += w to w
            }
        }
        return runs
    }

    /**
     * classPlace 括号判别 → room。
     * 两个括号 → 第二个是教师，截掉；单括号 → 校区，原样保留；
     * Online(教师) → Online；空 → ""。
     */
    internal fun roomOf(place: String): String {
        val p = place.trim()
        if (p.isBlank()) return ""
        // 数开括号: >=2 → 尾括号是教师名
        val openCount = p.count { it == '(' || it == '（' }
        if (openCount >= 2) {
            // 截掉最后一个括号段
            val lastOpen = p.lastIndexOf('(').let { if (it < 0) p.lastIndexOf('（') else it }
            if (lastOpen > 0) return p.substring(0, lastOpen).trim()
        }
        // Online(教师) 特例: 单括号但 Online 无校区语义
        if (p.startsWith("Online", ignoreCase = true)) return "Online"
        // 编码教室的单括号可能是教师（如 X5218 (Martin Levesley)），
        // 校区单括号则保留（如 X2319(犀浦)）。中文括号内容按校区处理。
        val single = Regex("""^([A-Za-z0-9]+)\s*\(([^()]+)\)$""").matchEntire(p)
        if (single != null && single.groupValues[2].none { it in '一'..'鿿' }) {
            return single.groupValues[1].trim()
        }
        return p
    }

    /** student-course-schedule = 90; yethan/common = 80 */
    override fun confidence(): Int = when {
        source.contains("student-course-schedule") || source.contains("\"code\":\"00000\"") &&
            source.contains("\"courseName\"") -> 90
        source.contains("classTime1") || source.contains("classPlace1") -> 80
        source.contains("\"code\":\"A0422\"") || source.contains("\"code\":\"A0230\"") ||
            source.contains("yhxt") -> 60
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("student-course-schedule")) add("student-course-schedule")
        if (source.contains("\"code\":\"00000\"")) add("code.00000")
        if (source.contains("classTime1")) add("classTime1..40")
        if (source.contains("classPlace1")) add("classPlace1..40")
        if (source.contains("staffName")) add("staffName")
    }
}

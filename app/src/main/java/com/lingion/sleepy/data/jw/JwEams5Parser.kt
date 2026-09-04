package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 合工大教务 (金智 EAMS5, eams5-student 系列) 课表 JSON 解析器。
 *
 * 适配学校：合肥工业大学 (jxglstu.hfut.edu.cn)。
 * 与 [JwCquParser] 同类：source 不是 HTML，而是课表 API 的 JSON 响应。
 *
 * 数据来源 (WebView 内 fetch 三段, 见 JwWebViewLoginScreen.EAMS5_FETCH_JS):
 *   1) GET /eams5-student/for-std/course-table                → studentId
 *   2) GET /eams5-student/for-std/lessons?studentId=…        → lessonIds[]
 *   3) POST /eams5-student/ws/schedule-table/datum            → 完整课表
 *      body: {"lessonIds":[…], "studentId":…, "weekIndex":""}
 *      resp: {"result":{"lessonList":[…],"scheduleList":[…],"scheduleGroupList":[…]}}
 *
 * 字段映射（教务 → JwCourse）：
 *   scheduleList[].lessonId          → lessonList[].id 查找 → lessonList[].courseName; 找不到回退 lessonId.toString()
 *   scheduleList[].room.nameZh       → room (null → "")
 *   scheduleList[].weekday           → day (1=周一..7=周日)
 *   scheduleList[].personName        → teacher
 *   scheduleList[].weekIndex         → startWeek = endWeek (v1 每行 = 单周, 见下限制)
 *   scheduleList[].startTime (HHmm)  → 推断 startNode (标准 985 节次表)
 *   scheduleList[].endTime   (HHmm)  → 推断 endNode
 *   scheduleList[].periods           (Int) → 备用: endNode = startNode + periods - 1 (节次已知时优先)
 *
 * v1 限制（与 [JwCquParser] 形态不同）：
 *   1. **节次推断**: HFUT 接口直接给绝对时间 (HHmm),不返回节次节点;
 *      推断表按典型上午 5 节次布局 (08:00 10:10 14:00 16:10 19:00 各为节次段起点)。
 *      v2 可拉 /for-std/program/root-module-json 拿时间表布局纠正。
 *   2. **每行一周**: scheduleList 每行只表示某周一次出现; v1 emit 单周 (startWeek=endWeek=weekIndex, type=0),
 *      不在 parser 内做连续周次合并 (无教学周 bitmap, 单双周判定要 RLE 推断)。
 *   3. **periods 用法**: 当 startTime/endTime 推断不出节点 (罕见边缘), 用 periods 兜底 endNode = startNode + periods - 1。
 *
 * 外部佐证：Chiu-xaH/HFUT-Schedule (jxglstu.hfut.edu.cn 全协议实现)。
 */
class JwEams5Parser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching {
            json.parseToJsonElement(source).jsonObject
        }.getOrNull() ?: return emptyList()

        val result = root["result"]?.jsonObject ?: return emptyList()

        // lessonList → lessonId (String) → courseName (HFUT 用 String, scheduleList 用 Int — 注意互转)
        val nameMap = mutableMapOf<String, String>()
        val lessonArr = result["lessonList"] as? JsonArray
        if (lessonArr != null) {
            for (el in lessonArr) {
                val o = runCatching { el.jsonObject }.getOrNull() ?: continue
                val id = o["id"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
                val name = o["courseName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
                if (id.isNotBlank() && name.isNotBlank()) nameMap[id] = name
            }
        }

        val scheduleArr = result["scheduleList"] as? JsonArray ?: return emptyList()

        val out = mutableListOf<JwCourse>()
        for (el in scheduleArr) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val lessonIdInt = o["lessonId"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val lessonIdStr = lessonIdInt.toString()
            val name = nameMap[lessonIdStr] ?: lessonIdStr

            val room = runCatching {
                o["room"]?.let { it as? JsonObject }?.get("nameZh")?.let {
                    runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
                }
            }.getOrNull()?.trim().orEmpty()

            val teacher = o["personName"]?.let {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }?.trim().orEmpty()

            val day = o["weekday"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val weekIndex = o["weekIndex"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val startTime = o["startTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val endTime = o["endTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val periods = o["periods"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 1

            val inferred = inferNodes(startTime, endTime, periods)
            if (inferred == null) continue

            out += JwCourse(
                name = name.trim(),
                room = room,
                teacher = teacher,
                day = day.coerceIn(1, 7),
                startNode = inferred.first.coerceAtLeast(1),
                endNode = inferred.second.coerceAtLeast(inferred.first),
                startWeek = weekIndex,
                endWeek = weekIndex,
                type = 0,
            )
        }
        return out
    }

    /**
     * 由 startTime/endTime (HHmm) 推断 (startNode, endNode)。
     * 标准 985 布局 (5 节次 × 2 节次 = 10 节点/天), 每节次 ~50 分钟 + 课间/午休/晚饭:
     *   08:00-09:50  (node 1-2)
     *   10:10-12:00  (node 3-4)
     *   14:00-15:50  (node 5-6)
     *   16:10-18:00  (node 7-8)
     *   19:00-20:50  (node 9-10)
     * startTime 落到哪节次起点 → startNode 为该节次首节点;
     * endTime 落在同一节次 → endNode = startNode + 1; 跨节次 → 按 periods 兜底 (startNode + periods - 1)。
     * 失败时回退到 periods 推断。
     */
    internal fun inferNodes(startTime: Int, endTime: Int, periods: Int): Pair<Int, Int>? {
        val startSec = sectionIndex(startTime) ?: return null
        val startNode = startSec * 2 + 1
        val endSec = sectionIndex(endTime)
        val endNode = when {
            endSec == null -> (startNode + periods - 1).coerceAtLeast(startNode)
            endSec == startSec -> startNode + 1
            endSec > startSec -> endSec * 2
            else -> startNode
        }
        return startNode to endNode.coerceAtLeast(startNode)
    }

    /**
     * 时间 (HHmm) → 节次段索引 (0..4 = 上午段 1, 上午段 2, 下午段 1, 下午段 2, 晚上段 1)。
     * 段起点 ±10 分钟内吸到该段; 其它返回 null。
     */
    private fun sectionIndex(time: Int): Int? {
        val hour = time / 100
        val minute = time % 100
        val mins = hour * 60 + minute
        val slots = listOf(
            8 * 60 to 0,         // 08:00 → section 0 (node 1)
            10 * 60 + 10 to 1,   // 10:10 → section 1 (node 3)
            14 * 60 to 2,        // 14:00 → section 2 (node 5)
            16 * 60 + 10 to 3,   // 16:10 → section 3 (node 7)
            19 * 60 to 4,        // 19:00 → section 4 (node 9)
        )
        for ((slotMins, sec) in slots) {
            if (kotlin.math.abs(mins - slotMins) <= 10) return sec
        }
        return null
    }

    /** schedule-table/datum = 100; scheduleList = 90; lessonList = 80 */
    override fun confidence(): Int = when {
        source.contains("scheduleList") && source.contains("lessonList") -> 95
        source.contains("schedule-table/datum") -> 80
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("scheduleList")) add("result.scheduleList")
        if (source.contains("lessonList")) add("result.lessonList")
        if (source.contains("schedule-table/datum")) add("ws/schedule-table/datum")
    }
}
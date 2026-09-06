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
 * 适配学校：合肥工业大学 (jxglstu.hfut.edu.cn) + 安徽大学 (jw.ahu.edu.cn, 金智 EAMS 新版)。
 * 与 [JwCquParser] 同类：source 不是 HTML，而是课表 API 的 JSON 响应。
 *
 * 数据来源 (WebView 内 fetch):
 *
 *   **HFUT 形态** (JwWebViewLoginScreen.EAMS5_FETCH_JS 三段):
 *     1) GET /eams5-student/for-std/course-table                → studentId
 *     2) GET /eams5-student/for-std/lessons?studentId=…        → lessonIds[]
 *     3) POST /eams5-student/ws/schedule-table/datum            → 完整课表
 *        body: {"lessonIds":[…], "studentId":…, "weekIndex":""}
 *        resp: {"result":{"lessonList":[…],"scheduleList":[…],"scheduleGroupList":[…]}}
 *
 *   **AHU 形态** (JwWebViewLoginScreen.EAMS5_AHU_FETCH_JS 三段, 2026-09-06 加):
 *     1) GET /student/for-std/course-table                       → HTML 含 allSemesters
 *     2) (从 allSemesters 取 semesterId, 客户端不提取 studentId — dataId 空服务端按 session 绑定)
 *     3) GET /student/for-std/course-table/get-data?bizTypeId=2&semesterId=<id>&dataId=
 *        resp: {"data":{"lessons":[…]}}
 *        字段: lessonId/courseName/teacher/room/weekday/startTime/endTime/weekIndex
 *        (与 HFUT scheduleList 同构, lessons[] 已是 scheduleList+lessonList 合并形态)
 *
 * 字段映射（教务 → JwCourse）：
 *   scheduleList[].lessonId (HFUT) / lessons[].lessonId (AHU)
 *     → HFUT 查 lessonList[].id → lessonList[].courseName; 找不到回退 lessonId.toString()
 *     → AHU 直接读 courseName (扁平结构)
 *   scheduleList[].room.nameZh (HFUT) / lessons[].room (AHU 字符串) → room
 *   scheduleList[].weekday     → day (1=周一..7=周日)
 *   scheduleList[].personName  / lessons[].teacher      → teacher
 *   scheduleList[].weekIndex                            → startWeek = endWeek
 *   scheduleList[].startTime (HHmm)                     → 推断 startNode (标准 985 节次表)
 *   scheduleList[].endTime   (HHmm)                     → 推断 endNode
 *   scheduleList[].periods (Int)                        → 备用: endNode = startNode + periods - 1
 *
 * v1 限制（同 [JwCquParser]）:
 *   1. **节次推断**: HFUT/AHU 都直接给绝对时间, 不返节次节点;
 *      推断表按典型上午 5 节次布局 (08:00 10:10 14:00 16:10 19:00)。
 *   2. **每行一周**: 每行只表示某周一次出现, v1 emit 单周 (startWeek=endWeek=weekIndex, type=0)。
 *   3. **periods 兜底**: startTime/endTime 推断不出时用 periods 兜底。
 *
 * 外部佐证：
 *   HFUT: Chiu-xaH/HFUT-Schedule, BoynChan/HfutOpenApi, elonzh/django-hfut-auth, Aoi-cn/hfut_schedule_hacker
 *   AHU: MoeclubM/AHU-AIO (Dart), qiqqqqq517/shangkeschschedule (Apache-2.0), abydym/Ahu_Plus (Kotlin)
 */
class JwEams5Parser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching {
            json.parseToJsonElement(source).jsonObject
        }.getOrNull() ?: return emptyList()

        // AHU 形态: data.lessons[] (扁平, 自含 courseName)
        val ahuLessons = root["data"]?.jsonObject?.get("lessons") as? JsonArray
        if (ahuLessons != null && ahuLessons.isNotEmpty()) {
            return parseAhuLessons(ahuLessons)
        }

        // HFUT 形态: result.lessonList[] + result.scheduleList[] (双层)
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
     * AHU 形态解析: `data.lessons[]` 扁平结构 (已合并 scheduleList+lessonList)。
     *
     * 字段映射 (qiqqqqq517 ahu.js 实锤):
     *   lesson.id              → lessonId (Int)
     *   lesson.courseName      → name (扁平, 无需二次查表)
     *   lesson.teacher         → teacher (字符串, 非 list)
     *   lesson.room            → room (字符串)
     *   lesson.weekday         → day (1-7)
     *   lesson.weekIndex       → startWeek = endWeek
     *   lesson.startTime (HHmm)→ 推断 startNode
     *   lesson.endTime   (HHmm)→ 推断 endNode
     *   lesson.periods         → 兜底
     *
     * 与 HFUT scheduleList 同构, 区别仅在 courseName 扁平化 + room/teacher 字符串化。
     */
    private fun parseAhuLessons(lessons: JsonArray): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        for (el in lessons) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val lessonId = o["lessonId"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: o["id"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            if (lessonId.isNullOrBlank()) continue

            val name = o["courseName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: lessonId

            val room = o["room"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()

            val teacher = o["teacher"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: o["personName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()

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
        val startSec = sectionIndex(startTime)
        if (startSec == null) {
            // startTime 不在任何节次段窗口: KDoc 承诺的 periods 兜底 —
            // 起点退到第 1 节, endNode = 1 + periods - 1; 无 periods 信息
            // (<=0) 才放弃该行。此前 startTime 不可映射时直接 return null,
            // periods 完全不参与, 整行静默丢弃。
            if (periods <= 0) return null
            val endNode = (1 + periods - 1).coerceAtLeast(1)
            return 1 to endNode
        }
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

    /** schedule-table/datum = 100; data.lessons = 95; scheduleList = 90; lessonList = 80 */
    override fun confidence(): Int = when {
        source.contains("data") && source.contains("\"lessons\"") -> 95
        source.contains("scheduleList") && source.contains("lessonList") -> 95
        source.contains("schedule-table/datum") -> 80
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("data") && source.contains("\"lessons\"")) add("data.lessons (AHU)")
        if (source.contains("scheduleList")) add("result.scheduleList")
        if (source.contains("lessonList")) add("result.lessonList")
        if (source.contains("schedule-table/datum")) add("ws/schedule-table/datum")
    }
}
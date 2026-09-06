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
 *   **AHU 形态** (JwWebViewLoginScreen.EAMS5_AHU_FETCH_JS, 2026-09-06 加):
 *     1) GET /student/for-std/course-table                       → HTML 含 allSemesters
 *     2) 从 HTML 提取 allSemesters, 取首个 semesterId
 *     3) GET /student/for-std/course-table/semester/<semesterId>/print-data
 *        ?semesterId=<id>&hasExperiment=false
 *        resp: {"studentTableVms":[{"activities":[…],"scheduleGroupVms":[…]}]}
 *        字段 (Landon-3314 + MoeclubM + abydym 共识):
 *          activity.lessonId (Long)         — 课程ID
 *          activity.courseName (String)     — 课程名
 *          activity.teacherNames (List)     — 教师姓名列表
 *          activity.campus (String)         — 校区
 *          activity.building (String)       — 教学楼
 *          activity.room (String)           — 教室
 *          activity.weekday (Int 1-7)       — 周几
 *          activity.weekIndexes (String)    — 周次位图, 形如 "1-16"/"1-16单"/"5,7,9-12"
 *          activity.startUnit (Int)         — 起始节次节点 (1-based)
 *          activity.endUnit (Int)           — 结束节次节点
 *
 *     (旧实现) GET /student/for-std/course-table/get-data?bizTypeId=2&semesterId=<id>&dataId=
 *        resp: {"data":{"lessons":[…]}}  — metadata-only (id/courseCode/courseName/examMode),
 *        不含 weekday/节次, 不可作为课表主数据源; 仅作为课程代码补充 (TODO v2 join by lessonId)。
 *
 * 字段映射（教务 → JwCourse）：
 *   HFUT scheduleList[].lessonId / AHU activities[].lessonId
 *     → 查 lessonList[].id → courseName; 找不到回退 lessonId.toString()
 *   room:
 *     HFUT: scheduleList[].room.nameZh
 *     AHU: campus + " " + building + " " + room (三段独立)
 *   weekday → day (1=周一..7=周日)
 *   teacher:
 *     HFUT: scheduleList[].personName
 *     AHU: activities[].teacherNames.joinToString("/")
 *   week:
 *     HFUT: scheduleList[].weekIndex (单值)
 *     AHU: activities[].weekIndexes (bitmap 字符串, v1 emit 起始周单值, type=0
 *           — 暂不解析 "1-16单" 等格式; v2 可加 RLE 推断)
 *   节次:
 *     HFUT: scheduleList[].startTime/endTime → 推断 startNode/endNode (标准 985 节次表)
 *     AHU: activities[].startUnit/endUnit (直接给定, 无需推断)
 *
 * v1 限制（同 [JwCquParser]）:
 *   1. **HFUT 节次推断**: HFUT 接口直接给绝对时间 (HHmm),不返回节次节点;
 *      推断表按典型上午 5 节次布局 (08:00 10:10 14:00 16:10 19:00 各为节次段起点)。
 *      AHU 直接给 startUnit/endUnit, 无需推断, 更精确。
 *   2. **每行一周**: HFUT 每行一周, type=0。AHU weekIndexes 是 bitmap,
 *      v1 简单取起始周 (左取 [0] 段第 1 个数字), type=0; 不解析 "1-16单" 等单双周标记。
 *   3. **periods 兜底**: HFUT startTime/endTime 推断不出时用 periods 兜底。
 *      AHU 不需要 (startUnit/endUnit 权威)。
 *
 * 外部佐证 (5 仓):
 *   HFUT: Chiu-xaH/HFUT-Schedule, BoynChan/HfutOpenApi, elonzh/django-hfut-auth, Aoi-cn/hfut_schedule_hacker
 *   AHU: MoeclubM/AHU-AIO (Dart, GPL-3.0), qiqqqqq517/shangkeschschedule (Apache-2.0),
 *        abydym/Ahu_Plus (GPL-3.0, Kotlin 同栈), Landon-3314/AHU-TimeTable (Flutter),
 *        Zeraora-807/Anhui-Univ-DSH-Tool (TypeScript)
 */
class JwEams5Parser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching {
            json.parseToJsonElement(source).jsonObject
        }.getOrNull() ?: return emptyList()

        // AHU 形态优先匹配 (5 仓 cross-verified 共识):
        //   1) studentTableVms[0].activities[] (print-data) — 真正课表数据 (weekday/startUnit/...)
        //   2) data.lessons[] (get-data) — metadata-only, 含 courseCode/examMode 但缺节次
        val stVms = root["studentTableVms"] as? JsonArray
        if (stVms != null && stVms.isNotEmpty()) {
            val firstTable = stVms.firstOrNull()?.let { runCatching { it.jsonObject }.getOrNull() }
            val activities = firstTable?.get("activities") as? JsonArray
            if (activities != null && activities.isNotEmpty()) {
                return parseAhuPrintDataActivities(activities)
            }
        }
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
     * AHU print-data 形态解析: `studentTableVms[0].activities[]`
     * (5 仓 cross-verified 共识: MoeclubM + abydym + Landon-3314 + Zeraora-807 + qiqqqqq517)
     *
     * 字段映射 (Landon-3314 academic_course_api_parser.dart + MoeclubM + abydym 共识):
     *   activity.lessonId (Long)         → ID (备用)
     *   activity.courseName (String)     → name
     *   activity.teacherNames (JsonArray)→ teacher (join "/")
     *   activity.campus+building+room    → room (三段拼接)
     *   activity.weekday (Int 1-7)       → day
     *   activity.weekIndexes (String)    → 起始周 (左取首个数字, v1 单周 emit)
     *   activity.startUnit (Int)         → startNode (直接, 无需推断)
     *   activity.endUnit (Int)           → endNode (直接, 无需推断)
     *
     * 与 HFUT scheduleList 形态区别:
     *   - 节次直接给 startUnit/endUnit (1-based 节点), HFUT 给 HHmm 需推断
     *   - weekIndexes 是 bitmap (e.g. "1-16" / "1-16单" / "5,7,9-12"), HFUT 单值
     *   - teacher 是 List<String>, HFUT 单字符串
     *   - room 三段独立 (campus/building/room), HFUT 单字符串 room.nameZh
     */
    private fun parseAhuPrintDataActivities(activities: JsonArray): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        for (el in activities) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val name = o["courseName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: continue

            // teacherNames → List<String> → join "/"
            val teacher = runCatching {
                val arr = o["teacherNames"] as? JsonArray ?: return@runCatching ""
                val names = arr.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                    .filter { it.isNotBlank() }
                names.joinToString("/").ifBlank { "" }
            }.getOrNull().orEmpty()

            // room: campus + building + room 三段拼接 (Landon-3314 实锤)
            val campus = o["campus"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
            val building = o["building"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
            val roomNum = o["room"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
            val room = listOf(campus, building, roomNum).filter { it.isNotEmpty() }.joinToString(" ")

            val day = o["weekday"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val startNode = o["startUnit"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val endNode = o["endUnit"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue

            // weekIndexes bitmap 解析 (v1 简化: 左取首个数字当 startWeek=endWeek, type=0)
            // 真实形态可能是 "1-16" (连续), "1-16单" (单周), "5,7,9-12" (不连续)
            // v2: 完整 RLE 解析 + 单双周标记 (MoeclubM 代码自写, 参考其 dayOfWeek/weeksAndTeachers 处理)
            val weekIndexesStr = o["weekIndexes"]?.let {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }.orEmpty()
            val startWeek = parseFirstWeek(weekIndexesStr)

            out += JwCourse(
                name = name.trim(),
                room = room,
                teacher = teacher,
                day = day.coerceIn(1, 7),
                startNode = startNode.coerceAtLeast(1),
                endNode = endNode.coerceAtLeast(startNode),
                startWeek = startWeek,
                endWeek = startWeek,
                type = 0,
            )
        }
        return out
    }

    /**
     * 从 weekIndexes bitmap 字符串取首个数字当 startWeek。
     * 形如 "1-16" → 1; "5,7,9-12" → 5; "1-16单" → 1; "" → 1 (兜底)
     * v1 简化: 单周 emit, type=0; 不解析单双周标记 (v2 加 RLE)
     */
    private fun parseFirstWeek(weekIndexes: String): Int {
        if (weekIndexes.isBlank()) return 1
        val m = Regex("""\d+""").find(weekIndexes) ?: return 1
        return m.value.toIntOrNull()?.coerceAtLeast(1) ?: 1
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

    /** AHU print-data studentTableVms[0].activities[] = 100 (含 weekday+节次权威);
 *  AHU get-data data.lessons[] = 85 (metadata-only);
 *  HFUT 双层 = 95; schedule-table/datum = 80 */
    override fun confidence(): Int = when {
        source.contains("studentTableVms") && source.contains("activities") -> 100
        source.contains("data") && source.contains("\"lessons\"") -> 85
        source.contains("scheduleList") && source.contains("lessonList") -> 95
        source.contains("schedule-table/datum") -> 80
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("studentTableVms")) add("studentTableVms[0].activities (AHU print-data)")
        if (source.contains("data") && source.contains("\"lessons\"")) add("data.lessons (AHU get-data metadata)")
        if (source.contains("scheduleList")) add("result.scheduleList")
        if (source.contains("lessonList")) add("result.lessonList")
        if (source.contains("schedule-table/datum")) add("ws/schedule-table/datum")
    }
}
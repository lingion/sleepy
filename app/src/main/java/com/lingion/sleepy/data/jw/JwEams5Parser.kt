package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 金智 EAMS5 与新版数维课表 JSON。
 *
 * HFUT 使用 result.lessonList 与 result.scheduleList，AHU 兼容 data.lessons。
 * studentTableVms 和 studentTableVm 的 activities 全部读取，周次可以是数字数组
 * 或带单双周的范围字符串。每个实际周次都保留，缺少周次不生成默认安排。
 *
 * 学校提供的实际起止时间随课程传递；courseUnitList 可提供节次映射。
 * 没有映射时的节次推断只作为占位，显示与导入仍以实际时间为准。
 * activities 的 credits、lessonCode 和 lessonRemark 一并保留。
 *
 * 历史交叉验证参考保留在 docs/hfut-cross-verify-2026-09-06，
 * 本轮新增依据为用户指定的本地 WakeUp 6.6.2 o00O0O00 的 Java 与 DEX。
 */
class JwEams5Parser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching {
            json.parseToJsonElement(source).jsonObject
        }.getOrNull() ?: return emptyList()

        // AHU 形态优先匹配 (5 仓 cross-verified 共识):
        //   1) studentTableVms[].activities[] (print-data) — 真正课表数据 (weekday/startUnit/...)
        //   2) data.lessons[] (get-data) — metadata-only, 含 courseCode/examMode 但缺节次
        if (root["studentTableVms"] is JsonArray || root["studentTableVm"] is JsonObject) {
            val tables = (root["studentTableVms"] as? JsonArray).orEmpty() +
                listOfNotNull(root["studentTableVm"] as? JsonObject)
            return tables.flatMap { table ->
                val activities = (table as? JsonObject)?.get("activities") as? JsonArray
                activities?.let(::parseAhuPrintDataActivities).orEmpty()
            }.distinct()
        }
        val ahuLessons = root["data"]?.jsonObject?.get("lessons") as? JsonArray
        if (ahuLessons != null && ahuLessons.isNotEmpty()) {
            return parseAhuLessons(ahuLessons)
        }

        // HFUT 形态: result.lessonList[] + result.scheduleList[] (双层)
        val result = root["result"]?.jsonObject ?: return emptyList()

        // issue #46: 布局查表 (服务端权威节次表)。JS 链第 3.5 段 POST
        // /ws/schedule-table/timetable-layout 拿 result.courseUnitList 后并入 payload 顶层;
        // 有则 startTime→indexNo 精确映射 (HFUTer/kirsh1 社区共识算法),
        // 无则 fallback heuristic (inferNodes), CUMTB/老包零回归。
        val unitByStart = mutableMapOf<Int, Int>()
        val unitEnds = mutableListOf<Pair<Int, Int>>()   // (endTime, indexNo)
        (root["courseUnitList"] as? JsonArray)?.let { units ->
            for (u in units) {
                val o = runCatching { u.jsonObject }.getOrNull() ?: continue
                val idx = o["indexNo"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: continue
                val st = o["startTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: continue
                val et = o["endTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: continue
                unitByStart[st] = idx
                unitEnds += et to idx
            }
            unitEnds.sortBy { it.first }
        }

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
            if (day !in 1..7 || weekIndex < 1) continue
            val startTime = o["startTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val endTime = o["endTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val periods = o["periods"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 1
            if (!JwCourse.isValidTimeRange(clockTime(startTime), clockTime(endTime))) continue

            // issue #46: 布局查表优先 — schedule.startTime 精确等于 unit.startTime (采集包 122/122);
            // endUnit = endTime >= unit.endTime 的最大 indexNo (11 仓实测零失败)。
            // 查表不完整 (start 缺 / end 无覆盖) → 整行走旧 heuristic。
            val inferred: Pair<Int, Int>? = if (unitByStart.isNotEmpty()) {
                val su = unitByStart[startTime]
                val eu = if (su != null) unitEnds.lastOrNull { it.first <= endTime }?.second else null
                if (su != null && eu != null && eu >= su) su to eu else inferNodes(startTime, endTime, periods)
            } else inferNodes(startTime, endTime, periods)
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
                startTime = clockTime(startTime),
                endTime = clockTime(endTime),
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
            if (day !in 1..7 || weekIndex < 1) continue
            val startTime = o["startTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val endTime = o["endTime"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 0
            val periods = o["periods"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull() ?: 1
            if (!JwCourse.isValidTimeRange(clockTime(startTime), clockTime(endTime))) continue

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
                startTime = clockTime(startTime),
                endTime = clockTime(endTime),
            )
        }
        return out
    }

    /**
     * AHU print-data 形态解析: `studentTableVms[].activities[]`
     * (5 仓 cross-verified 共识: MoeclubM + abydym + Landon-3314 + Zeraora-807 + qiqqqqq517)
     *
     * 字段映射 (Landon-3314 academic_course_api_parser.dart + MoeclubM + abydym 共识):
     *   activity.lessonId (Long)         → ID (备用)
     *   activity.courseName (String)     → name
     *   activity.teacherNames (JsonArray)→ teacher (join "/")
     *   activity.campus+building+room    → room (三段拼接)
     *   activity.weekday (Int 1-7)       → day
     *   activity.weekIndexes (String/Array) → 完整周次范围或显式周次集合
     *   activity.startUnit (Int)         → startNode (直接, 无需推断)
     *   activity.endUnit (Int)           → endNode (直接, 无需推断)
     *
     * 与 HFUT scheduleList 形态区别:
     *   - 节次直接给 startUnit/endUnit (1-based 节点), HFUT 给 HHmm 需推断
     *   - weekIndexes 是周次范围或数字数组 (e.g. "1-16" / "1-16单" / "5,7,9-12"), HFUT 单值
     *   - teacher 是 List<String>, HFUT 单字符串
     *   - room 三段独立 (campus/building/room), HFUT 单字符串 room.nameZh
     */
    private fun parseAhuPrintDataActivities(activities: JsonArray): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        for (el in activities) {
            val o = runCatching { el.jsonObject }.getOrNull() ?: continue
            val name = o["courseName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: continue

            // teacherNames → List<String> → join "/" (5 仓 consensus)
//   防御性 fallback: teachers[] / teacherList / 单字符串 teacher / personName
            val teacher = runCatching {
                val arr = o["teacherNames"] as? JsonArray
                    ?: o["teachers"] as? JsonArray
                    ?: o["teacherList"] as? JsonArray
                if (arr != null) {
                    val names = arr.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                        .filter { it.isNotBlank() }
                    if (names.isNotEmpty()) return@runCatching names.joinToString("/")
                }
                val nestedNames = (o["teacherDeepVms"] as? JsonArray)?.mapNotNull {
                    ((it as? JsonObject)?.get("person") as? JsonObject)?.get("nameZh")
                        ?.let { name -> runCatching { name.jsonPrimitive.contentOrNull }.getOrNull() }
                        ?.trim()?.takeIf(String::isNotEmpty)
                }.orEmpty()
                if (nestedNames.isNotEmpty()) return@runCatching nestedNames.joinToString("/")
                // 单字符串字段兜底
                o["teacher"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim()
                    ?: o["personName"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim()
                    ?: ""
            }.getOrNull().orEmpty()

            // room: campus + building + room 三段拼接 (Landon-3314 实锤)
            val campus = o["campus"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
            val building = o["building"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
            val roomNum = o["room"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim().orEmpty()
                .ifEmpty { runCatching { o["customPlace"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim().orEmpty() }
            val room = listOf(campus, building, roomNum).filter { it.isNotEmpty() }.joinToString(" ")
            // USTC uses these names for HH:mm; other deployments may use calendar dates.
            // Only a complete clock pair augments the authoritative explicit periods.
            val sourceStart = runCatching { o["startDate"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim().orEmpty()
            val sourceEnd = runCatching { o["endDate"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim().orEmpty()
            val hasClock = JwCourse.isValidTimeRange(sourceStart, sourceEnd)
            val startTime = if (hasClock) sourceStart else ""
            val endTime = if (hasClock) sourceEnd else ""

            val day = o["weekday"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val startNode = o["startUnit"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            val endNode = o["endUnit"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.toIntOrNull()
                ?: continue
            if (name.isBlank() || day !in 1..7 || startNode < 1 || endNode < startNode) continue

            // weekIndexes 周次 完整 RLE 解析 — emit 多 JwCourse (单/双周/区间/离散)
            // 5 仓 cross-validated 2026-09-06: parseWeekRanges 复用 JwParity 端点修正
            // 同一活动可在多周出现 → emit 多行 JwCourse (同 SEU parseWeekRanges 形态)
            val weekValue = o["weekIndexes"]
            val weekRanges = if (weekValue is JsonArray) {
                val values = weekValue.map { runCatching { it.jsonPrimitive.contentOrNull?.toIntOrNull() }.getOrNull() }
                if (values.any { it == null || it < 1 }) emptyList()
                else values.filterNotNull().distinct().sorted().map { Triple(it, it, 0) }
            } else {
                parseWeekRanges(runCatching { weekValue?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty())
            }
            for ((startWeek, endWeek, weekType) in weekRanges) {
                out += JwCourse(
                    name = name.trim(),
                    room = room,
                    teacher = teacher,
                    day = day.coerceIn(1, 7),
                    startNode = startNode.coerceAtLeast(1),
                    endNode = endNode.coerceAtLeast(startNode),
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = weekType,
                    startTime = startTime,
                    endTime = endTime,
                    credit = runCatching { o["credits"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() }
                        .getOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f,
                    note = listOf("lessonCode", "lessonRemark").joinToString("") { key ->
                        runCatching { o[key]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                    },
                )
            }
        }
        return out
    }

    /**
     * Parse the existing textual week extension strictly, including odd/even markers.
     * Empty or malformed tokens never manufacture a fallback week. The first matching
     * parity week must remain inside the declared range; the original end is retained.
     */
    internal fun parseWeekRanges(weekIndexes: String): List<Triple<Int, Int, Int>> {
        val text = weekIndexes.trim().replace('（', '(').replace('）', ')')
        if (text.isEmpty()) return emptyList()
        val token = Regex("""第?([0-9]+)(?:\s*-\s*([0-9]+))?\s*(?:([单双]|odd|even)周?|周(?:\(([单双]|odd|even)周?\))?|\(([单双]|odd|even)周?\))?""", RegexOption.IGNORE_CASE)
        val out = mutableListOf<Triple<Int, Int, Int>>()
        for (part in text.split(',', '，')) {
            val match = token.matchEntire(part.trim()) ?: return emptyList()
            val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
            if (first < 1 || last < first) return emptyList()
            val marker = match.groupValues.drop(3).firstOrNull { it.isNotEmpty() }.orEmpty().lowercase()
            val parity = when (marker) { "单", "odd" -> 1; "双", "even" -> 2; else -> 0 }
            val start = first.toLong() + if (parity != 0 && first % 2 != parity % 2) 1 else 0
            if (start > last) continue
            out += Triple(start.toInt(), last, parity)
        }
        return out.distinct()
    }

    private fun clockTime(value: Int): String =
        if (value == 0) "" else value.toString().padStart(4, '0').let { it.take(2) + ":" + it.drop(2) }

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

    /** AHU print-data studentTableVms[].activities[] = 100 (含 weekday+节次权威);
     *  AHU get-data data.lessons[] = 85 (metadata-only);
     *  HFUT 双层 = 95; schedule-table/datum = 80
     */
    override fun confidence(): Int = when {
        source.contains("studentTableVms") && source.contains("activities") -> 100
        source.contains("data") && source.contains("\"lessons\"") -> 85
        source.contains("scheduleList") && source.contains("lessonList") -> 95
        source.contains("schedule-table/datum") -> 80
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("studentTableVms")) add("studentTableVms[].activities (AHU print-data)")
        if (source.contains("data") && source.contains("\"lessons\"")) add("data.lessons (AHU get-data metadata)")
        if (source.contains("scheduleList")) add("result.scheduleList")
        if (source.contains("lessonList")) add("result.lessonList")
        if (source.contains("schedule-table/datum")) add("ws/schedule-table/datum")
    }
}

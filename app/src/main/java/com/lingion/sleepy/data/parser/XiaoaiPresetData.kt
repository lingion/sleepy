package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 小爱课程表 presetData 导出 — 2026-10-10。
 *
 * 通路: 构造小爱官方前端(H5 /import)同构的 presetData, 经
 * `voiceassist://aiweb/?url=<H5>&presetData=<encoded>` intent 唤起小爱 App,
 * H5 从 presetData.importData 读 parserRes.courseInfos 进预览页, 用户在小爱里确认导入。
 * Sleepy 侧零网络零账号 — 数据不经过任何服务器, 与隐私立场一致。
 *
 * presetData 结构(三方独立印证: 小爱官方 umi.js bundle / NEORUAA MIT 实现 / Mercury 实现):
 * ```
 * { "importData": "<JSON 字符串>" }        ← 外层整体 URL-encode 进 intent
 *   importData = {
 *     isV2: true, errorCode: 0, schoolName: "...",
 *     parserRes: { courseInfos: [ { name, teacher, position, day,
 *                                   weeks: [1,2..], sections: [1,2..] } ] },
 *     timerRes:  { totalWeek, startSemester(13位ms), startWithSunday,
 *                  showWeekend, forenoon, afternoon, night,
 *                  sections: [ { section, startTime, endTime } ] },
 *     feedbackId, source, status: "native", id, t: <ms>
 *   }
 * ```
 * weeks/sections 为纯数字数组(NEORUAA/官方 parser 文档一致)。
 *
 * 字段映射(CourseEntity → courseInfos):
 *   courseName→name · teacher→teacher · room→position · day→day
 *   inWeek 展开 startWeek..endWeek + type 单双周过滤 → weeks 数组
 *   startNode..startNode+step-1 → sections 数组
 * timeJson(或绑定 periodTable) node/start/end → timerRes.sections section/startTime/endTime。
 */
object XiaoaiPresetData {

    const val XIAOAI_H5_URL = "https://i.ai.mi.com/h5/precache/ai-schedule/"
    private const val SOURCE = "sleepy"

    /** 唤起小爱的完整 deep link。presetData 已做 URL-encode, url 参数同样 encode。 */
    fun deepLink(presetData: String): String =
        "voiceassist://aiweb/?source=sleepy&flag=268468224" +
            "&url=${URLEncoder.encode(XIAOAI_H5_URL, "UTF-8")}" +
            "&presetData=${URLEncoder.encode(presetData, "UTF-8")}"

    /**
     * 生成完整 presetData 字符串(外层 {importData:"..."} 未 encode 形态, 供 deepLink/测试用)。
     * [periodTable] 优先; null 时回落 [table].timeJson 兼容列。
     */
    fun build(
        table: TimeTableEntity,
        courses: List<CourseEntity>,
        periodTable: PeriodTableEntity? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val timeJson = periodTable?.timeJson ?: table.timeJson
        val timerSections = parseTimerSections(timeJson)

        val importData = buildJsonObject {
            put("isV2", true)
            put("errorCode", 0)
            put("schoolName", table.name)
            put("parserRes", buildJsonObject {
                put("courseInfos", buildCourseInfos(courses, timeJson))
            })
            put("timerRes", buildTimerRes(table, timerSections))
            put("feedbackId", "sleepy_$nowMillis")
            put("source", SOURCE)
            put("status", "native")
            put("id", "sleepy")
            put("t", nowMillis.toString())
        }
        return buildJsonObject {
            put("importData", importData.toString())
        }.toString()
    }

    /**
     * CourseEntity 列表 → courseInfos 数组。空列表返回空数组(由调用方拦空表)。
     * [timeJson] 用于 issue#55 ownTime 课程按节次表归一化 sections 落位。
     */
    internal fun buildCourseInfos(courses: List<CourseEntity>, timeJson: String): JsonArray =
        buildJsonArray {
            courses.forEach { c ->
                val normalized = c.normalizeNode(timeJson)
                add(buildJsonObject {
                    put("name", c.courseName)
                    put("teacher", c.teacher)
                    put("position", c.room)
                    put("day", c.day)
                    put("weeks", intArrayJson(expandWeeks(c)))
                    put("sections", intRangeJson(normalized.startNode, normalized.startNode + normalized.step - 1))
                })
            }
        }

    /** startWeek..endWeek 按 type(0每周/1单周/2双周/3按列) 展开为显式周次数组。 */
    internal fun expandWeeks(c: CourseEntity): List<Int> =
        (c.startWeek..c.endWeek).filter { c.inWeek(it) }

    private fun intArrayJson(values: List<Int>): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }

    private fun intRangeJson(from: Int, to: Int): JsonArray = buildJsonArray {
        (from..to).forEach { add(JsonPrimitive(it)) }
    }

    /**
     * timeJson → timerRes.sections [{section,startTime,endTime}]。
     * 兼容两种键形(真实 DB 并存, 模拟器实测抓到):
     *   规范形 {node,start,end}(TimeTableUtils.buildTimeJsonFromRows 写入)
     *   历史形 {nodeStart,startTime,endTime}(创建向导/旧版写入)
     */
    internal fun parseTimerSections(timeJson: String): List<Triple<Int, String, String>> {
        if (timeJson.isBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(timeJson).let { root ->
                (root as? JsonArray)?.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val node = (o["node"] ?: o["nodeStart"])?.toString()?.toIntOrNull()
                        ?: return@mapNotNull null
                    val start = (o["start"] ?: o["startTime"])?.toString()?.trim('"')
                        ?: return@mapNotNull null
                    val end = (o["end"] ?: o["endTime"])?.toString()?.trim('"')
                        ?: return@mapNotNull null
                    Triple(node, start, end)
                } ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun buildTimerRes(
        table: TimeTableEntity,
        sections: List<Triple<Int, String, String>>,
    ): JsonObject = buildJsonObject {
        put("totalWeek", table.maxWeek)
        put("startSemester", startSemesterMillis(table.startDate))
        put("startWithSunday", false)
        put("showWeekend", true)
        val counts = inferSessionCounts(sections)
        put("forenoon", counts[0])
        put("afternoon", counts[1])
        put("night", counts[2])
        put("sections", buildJsonArray {
            sections.sortedBy { it.first }.forEach { (node, start, end) ->
                add(buildJsonObject {
                    put("section", node)
                    put("startTime", start)
                    put("endTime", end)
                })
            }
        })
    }

    /**
     * 按 startTime 推断上午/下午/晚间节数 (小爱 timerRes 三段统计):
     * <12:00 上午 · [12:00,18:00) 下午 · >=18:00 晚间。缺时间信息的节次归上午。
     */
    internal fun inferSessionCounts(sections: List<Triple<Int, String, String>>): IntArray {
        val morning = mutableSetOf<Int>()
        val afternoon = mutableSetOf<Int>()
        val night = mutableSetOf<Int>()
        sections.forEach { (node, start, _) ->
            val minutes = clockMinutes(start)
            when {
                minutes < 0 -> morning += node
                minutes < 12 * 60 -> morning += node
                minutes < 18 * 60 -> afternoon += node
                else -> night += node
            }
        }
        return intArrayOf(morning.size, afternoon.size, night.size)
    }

    private fun clockMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size < 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        return h * 60 + m
    }

    /** startDate("yyyy-MM-dd") → 当日 00:00 UTC+8 的 13 位毫秒时间戳字符串。 */
    internal fun startSemesterMillis(startDate: String): String {
        val date = runCatching { LocalDate.parse(startDate) }.getOrNull()
            ?: LocalDate.now(ZoneOffset.ofHours(8))
        return date.atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli().toString()
    }
}

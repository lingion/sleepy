package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.TimeSlot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * 时间表 (timeJson) 解析与查询工具。
 *
 * TimeTableEntity.timeJson 格式:
 *   [{"node":1,"start":"08:00","end":"08:45"}, {"node":2,...}, ...]
 *
 * UI 渲染时用 [timeSlotsFor] 把 JSON 转为每节独立的 TimeSlot；
 * 与 WakeUp 默认 12 节制对应，若用户改 nodesPerDay，会按节点列表拆段。
 */
object TimeTableUtils {

    /**
     * 默认节次时间表（12 节 / 45-50 分钟）。
     *
     * 这是 timeJson 的**唯一权威默认值**；
     * TimeTableEntity 默认构造、TimeTableUtils 解析、UI 渲染都从这里走。
     */
    val DEFAULT_TIME_JSON: String = """[
            {"node":1,"start":"08:00","end":"08:45"},
            {"node":2,"start":"08:55","end":"09:40"},
            {"node":3,"start":"10:00","end":"10:45"},
            {"node":4,"start":"10:55","end":"11:40"},
            {"node":5,"start":"14:00","end":"14:45"},
            {"node":6,"start":"14:55","end":"15:40"},
            {"node":7,"start":"16:00","end":"16:45"},
            {"node":8,"start":"16:55","end":"17:40"},
            {"node":9,"start":"19:00","end":"19:45"},
            {"node":10,"start":"19:55","end":"20:40"},
            {"node":11,"start":"20:50","end":"21:35"},
            {"node":12,"start":"21:45","end":"22:30"}
        ]"""

    internal data class NodeTime(val node: Int, val start: LocalTime, val end: LocalTime)

    /** 解析 timeJson -> 按 node 排序的 list */
    internal fun parseNodes(timeJson: String): List<NodeTime> = try {
        val arr = JSONArray(timeJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NodeTime(
                node = o.getInt("node"),
                start = LocalTime.parse(o.getString("start")),
                end = LocalTime.parse(o.getString("end"))
            )
        }.sortedBy { it.node }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 把节点时间表转为每节独立的 TimeSlot。
     * 每个节点变成一行：第1节, 第2节, ...
     */
    fun timeSlotsFor(timeJson: String): List<TimeSlot> {
        val nodes = parseNodes(timeJson)
        if (nodes.isEmpty()) return emptyList()

        return nodes.map { n ->
            TimeSlot(
                label = "${n.node}",
                start = n.start,
                end = n.end,
                displayStart = formatTime(n.start),
                displayEnd = formatTime(n.end),
                nodeStart = n.node,
                nodeEnd = n.node
            )
        }
    }

    /** 课程的开始节-结束节对应的"开始时间-结束时间"。
     *  直接用节点的 start/end 拼接，不依赖外层 TimeSlot。
     *  找不到节点则返回 null。
     */
    fun courseTimeString(courseStartNode: Int, courseStep: Int, timeJson: String, ownTime: Boolean = false, startTime: String = "", endTime: String = ""): String? {
        val parts = courseTimeParts(courseStartNode, courseStep, timeJson, ownTime, startTime, endTime)
        return parts?.let { "${it.first}-${it.second}" }
    }

    /** 课程的 (开始时间, 结束时间)，用于需要分行渲染的场景。
     *  逻辑同 [courseTimeString]，但返回拆分后的两部分，避免外层再 split。 */
    fun courseTimeParts(courseStartNode: Int, courseStep: Int, timeJson: String, ownTime: Boolean = false, startTime: String = "", endTime: String = ""): Pair<String, String>? {
        if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            return Pair(startTime, endTime)
        }
        val nodes = parseNodes(timeJson)
        if (nodes.isEmpty()) return null
        val endNode = courseStartNode + courseStep - 1
        val first = nodes.find { it.node == courseStartNode } ?: return null
        val last = nodes.find { it.node == endNode } ?: return null
        return Pair(formatTime(first.start), formatTime(last.end))
    }

    private fun formatTime(t: LocalTime): String =
        String.format("%02d:%02d", t.hour, t.minute)

    /**
     * 根据课程的 startTime/endTime 反算等效的 (startNode, step)。
     * 用于把 ownTime=true 的课映射到节次网格上。
     *
     * 规则：
     * - startNode = 时间表中 start ≤ courseStart 的最大节点（向下取）
     * - endNode   = 时间表中 end   ≥ courseEnd   的最小节点（向上取）
     * - step      = endNode - startNode + 1
     * - 若 StartTime 早于第一节，用第1节；endTime 晚于最后一节，用最后一节
     * 返回 null 表示无法映射（时间格式错误或时间表为空）。
     */
    fun timeToNode(startTime: String, endTime: String, timeJson: String): Pair<Int, Int>? {
        val nodes = parseNodes(timeJson)
        if (nodes.isEmpty()) return null
        val st = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
        val et = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null

        val startNode = nodes.filter { it.start <= st }.maxByOrNull { it.node }?.node
            ?: nodes.first().node
        val endNode = nodes.filter { it.end >= et }.minByOrNull { it.node }?.node
            ?: nodes.last().node

        if (endNode < startNode) return null
        return Pair(startNode, endNode - startNode + 1)
    }

    /** 便捷: 拿 TimeTableEntity 直接出 slots */
    fun timeSlotsFor(table: TimeTableEntity?): List<TimeSlot> =
        if (table == null) emptyList() else timeSlotsFor(table.timeJson)

    // ------------------------------------------------------------------
    // 编辑用的 row 数据模型 + JSON 互转
    // 共享给 EditTableScreen + ImportScreen.ImportConfirmDialog
    // ------------------------------------------------------------------

    /**
     * 节次编辑用的行模型：node=节次编号, start/end="HH:mm"。
     * 节点编号在删除时会重新 1..N 连续编号。
     */
    data class TimeSlotRow(val node: Int, val start: String, val end: String)

    /** timeJson -> 编辑 rows (按数组顺序) */
    fun parseTimeSlotRows(timeJson: String): List<TimeSlotRow> = try {
        val arr = JSONArray(timeJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TimeSlotRow(
                node = o.optInt("node", i + 1),
                start = o.optString("start", smartStartDefault(i + 1)),
                end = o.optString("end", smartEndDefault(i + 1))
            )
        }
    } catch (_: Exception) {
        (1..12).map { node -> TimeSlotRow(node, smartStartDefault(node), smartEndDefault(node)) }
    }

    /** rows -> timeJson */
    fun buildTimeJsonFromRows(rows: List<TimeSlotRow>): String {
        val arr = JSONArray()
        rows.forEach { row ->
            val obj = JSONObject()
            obj.put("node", row.node)
            obj.put("start", row.start)
            obj.put("end", row.end)
            arr.put(obj)
        }
        return arr.toString()
    }

    /**
     * 删除某 node 后**重新编号**为 1..N (用户友好)，返回新 list。
     */
    fun removeAndRenumber(rows: List<TimeSlotRow>, node: Int): List<TimeSlotRow> =
        rows.filter { it.node != node }
            .mapIndexed { idx, r -> r.copy(node = idx + 1) }

    /**
     * 追加导入扩展 timeJson — 把 incomingJson 中 node > currentMaxNode 的节次复制进来,
     * 时间从 incoming 拿, node 重新连续编号避免冲突。
     * 用于"追加课程时, 课表自动延伸到新最大节次"场景。
     */
    fun extendTimeJsonWith(currentJson: String, incomingJson: String): String {
        val currentRows = parseTimeSlotRows(currentJson)
        val incomingRows = parseTimeSlotRows(incomingJson)
        val currentMaxNode = currentRows.maxOfOrNull { it.node } ?: 0
        val newRows = incomingRows.filter { it.node > currentMaxNode }
        if (newRows.isEmpty()) return currentJson
        // node 重新连续编号(避免原 incoming 跳号, 保持 1..N 连续)
        val merged = currentRows + newRows.mapIndexed { idx, r -> r.copy(node = currentMaxNode + idx + 1) }
        return buildTimeJsonFromRows(merged)
    }

    /**
     * v7.10.16k 无损合并 — "哪个大用哪个"(用户 2026-09-03):
     * 双方作息逐节合并, 结果 = max(两边节次数, requiredNodeCount), 任何一方不得把另一方压小。
     * 同一节次: 导入源非空时间优先(空串视为没声明), 否则原表, 都没有用 smart 默认。
     * 超出双方声明的节次(requiredNodeCount=导入课程实际到达的最大节)用 smart 默认铺底,
     * 保证课程到达 13 节时课表就是 13 节 — 源数据识别到多少节, 结果就多少节。
     */
    fun mergeMostComplete(currentJson: String, incomingJson: String, requiredNodeCount: Int = 0): String {
        // 空串 = 没声明 — 不能进 parseTimeSlotRows(它会 catch 出 12 行 smart 伪声明,
        // 反过来把有真实作息的一方当"缺省"盖掉)
        val currentRows = currentJson.takeIf { it.isNotBlank() }?.let { parseTimeSlotRows(it) } ?: emptyList()
        val incomingRows = incomingJson.takeIf { it.isNotBlank() }?.let { parseTimeSlotRows(it) } ?: emptyList()
        val cur = currentRows.associateBy { it.node }
        val inc = incomingRows.associateBy { it.node }
        val declared = maxOf(
            currentRows.maxOfOrNull { it.node } ?: 0,
            incomingRows.maxOfOrNull { it.node } ?: 0
        )
        if (declared == 0 && requiredNodeCount <= 0) return DEFAULT_TIME_JSON
        val count = maxOf(declared, requiredNodeCount).coerceAtLeast(1)
        val rows = (1..count).map { node ->
            val i = inc[node]
            val c = cur[node]
            TimeSlotRow(
                node = node,
                start = i?.start?.takeIf { it.isNotBlank() } ?: c?.start?.takeIf { it.isNotBlank() } ?: smartStartDefault(node),
                end = i?.end?.takeIf { it.isNotBlank() } ?: c?.end?.takeIf { it.isNotBlank() } ?: smartEndDefault(node)
            )
        }
        return buildTimeJsonFromRows(rows)
    }

    /**
     * 追加一节 (node = maxOfOrNull + 1)，时间留空让用户填。
     */
    fun appendEmptyRow(rows: List<TimeSlotRow>): List<TimeSlotRow> {
        val nextNode = (rows.maxOfOrNull { it.node } ?: 0) + 1
        return rows + TimeSlotRow(nextNode, "", "")
    }

    private fun smartStartDefault(node: Int): String = when {
        node <= 2 -> "08:00"
        node <= 4 -> "10:00"
        node <= 6 -> "14:00"
        node <= 8 -> "16:00"
        node <= 10 -> "19:00"
        else -> "20:50"
    }

    private fun smartEndDefault(node: Int): String = when {
        node <= 2 -> "09:40"
        node <= 4 -> "11:40"
        node <= 6 -> "15:40"
        node <= 8 -> "17:40"
        node <= 10 -> "20:40"
        else -> "22:30"
    }
}

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
 * UI 渲染时用 [timeSlotsFor] 把 JSON 转为 5 个 TimeSlot (1-2, 3-5, 6-7, 8-10, 11-13)
 * 与 WakeUp 默认 12 节制对应；若用户改 nodesPerDay，会按 12 节拆段。
 */
object TimeTableUtils {

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
 * 把节点时间表转为每节独立的 TimeSlot（不再是分组）。
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
     *  直接用节点的 start/end 拼接,不依赖外层 TimeSlot。
     *  找不到节点则返回 null。
     */
    fun courseTimeString(courseStartNode: Int, courseStep: Int, timeJson: String, ownTime: Boolean = false, startTime: String = "", endTime: String = ""): String? {
        if (ownTime && startTime.isNotBlank() && endTime.isNotBlank()) {
            return "$startTime-$endTime"
        }
        val nodes = parseNodes(timeJson)
        if (nodes.isEmpty()) return null
        val endNode = courseStartNode + courseStep - 1
        val first = nodes.find { it.node == courseStartNode } ?: return null
        val last = nodes.find { it.node == endNode } ?: return null
        return "${formatTime(first.start)}-${formatTime(last.end)}"
    }

    private fun formatTime(t: LocalTime): String =
        String.format("%02d:%02d", t.hour, t.minute)

    /** 便捷: 拿 TimeTableEntity 直接出 slots */
    fun timeSlotsFor(table: TimeTableEntity?): List<TimeSlot> =
        if (table == null) emptyList() else timeSlotsFor(table.timeJson)
}

package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.TimeSlot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.temporal.ChronoUnit

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
     * - endNode   = 从 startNode 起沿节点序连续延伸的最后一节 — 课程在节次空隙内
     *   结束时停在空隙前的那一节, 绝不跨过空隙吸附到下一节 (用户 2026-09-09:
     *   12:30 结束跨午间空隙被吸进 14:00 节 = 报障本体; 旧行为 "end ≥ courseEnd
     *   的最小节点" 会跨空隙撑大 step, 已否决)
     * - step      = endNode - startNode + 1
     * - 若 StartTime 早于第一节，用第1节；endTime 晚于最后一节，用最后一节
     * 返回 null 表示无法映射（时间格式错误或时间表为空）。
     */
    fun timeToNode(startTime: String, endTime: String, timeJson: String): Pair<Int, Int>? {
        val nodes = parseNodes(timeJson)
        if (nodes.isEmpty()) return null
        val st = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
        val et = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null

        val startIdx = nodes.indexOfLast { it.start <= st }
        val sIdx = if (startIdx >= 0) startIdx else 0
        var endIdx = sIdx
        var i = sIdx + 1
        while (i < nodes.size && nodes[i].start < et) {
            endIdx = i
            i++
        }

        val startNode = nodes[sIdx].node
        val endNode = nodes[endIdx].node
        if (endNode < startNode) return null
        return Pair(startNode, endNode - startNode + 1)
    }

    // ------------------------------------------------------------------
    // issue#23 §5 渲染: 非常规时间胶囊按真实分钟在网格内按比例定位
    // ------------------------------------------------------------------

    /**
     * 把课程起止时间映射到「槽位行坐标」: 1.0 = 一整行, 小数部分 = 该槽位内按时间的比例。
     * 返回 (startFrac, endFrac); 时间不可解析 / 结束≤开始 / 映射退化返回 null,
     * 调用方应退回整格吸附(timeToNode)。
     *
     * 规则:
     * - 时间落在某槽位 [start, end] 内 → 行下标 + 槽内比例
     * - 落在两槽位空隙 → 归属下一行顶端
     * - 早于首槽位 → 0.0; 晚于末槽位 → 槽位总数(网格底边)
     */
    fun timeToFractionalRows(startTime: String, endTime: String, slots: List<TimeSlot>): Pair<Float, Float>? {
        if (slots.isEmpty()) return null
        val st = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
        val et = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null
        if (et <= st) return null
        fun pos(t: LocalTime): Float = when {
            t <= slots.first().start -> 0f
            t >= slots.last().end -> slots.size.toFloat()
            else -> {
                val i = slots.indexOfFirst { t >= it.start && t <= it.end }
                if (i >= 0) {
                    val dur = ChronoUnit.MINUTES.between(slots[i].start, slots[i].end).coerceAtLeast(1)
                    i + ChronoUnit.MINUTES.between(slots[i].start, t).toFloat() / dur
                } else {
                    // 空隙: 全部归属下一行顶端
                    slots.indexOfFirst { it.start > t }.toFloat()
                }
            }
        }
        val startFrac = pos(st)
        val endFrac = pos(et)
        if (endFrac <= startFrac) return null
        return startFrac to endFrac
    }

    /** 便捷重载: 直接传 timeJson 字符串。 */
    fun timeToFractionalRows(startTime: String, endTime: String, timeJson: String): Pair<Float, Float>? =
        timeToFractionalRows(startTime, endTime, timeSlotsFor(timeJson))

    // ------------------------------------------------------------------
    // 用户反馈 2026-09-09: 非常规课跨节次空隙的渲染期占位节次合成
    // ------------------------------------------------------------------

    /**
     * 渲染期槽位方案 — 标准槽位 + 按当前课程集合合成的**占位节次**(渲染期产物,
     * 绝不写回 timeJson; 与用户手建边缘节点 insertEdgeNode 机制严格无关)。
     */
    data class RenderSlotPlan(val slots: List<TimeSlot>)

    /**
     * 为当前可见课程合成渲染槽位表(纯函数):
     *   1. 非常规课(ownTime)的结束时间**终止在**某节次空隙内(课尾溢出进空隙、
     *      且不再延伸到下一节)时, 该空隙里合成一个占位节次, 范围 = 各溢出课
     *      与空隙交集的贪心并包: 起点 = 各课交叠起点的最小值, 终点 = 各课溢出
     *      终点的最大值(谁长听谁的);
     *   2. 课同时占据空隙两侧节点(连续跨节)时不合成 — 该空隙是常规连堂间隙,
     *      比例渲染按真实分钟表达, 合占位行只会切碎连堂卡;
     *   3. 无溢出 → 槽位表与 timeSlotsFor(timeJson) 完全一致。
     *
     * 占位节次在时间轴上低调呈现: 只显示时间不显示节号(TimeSlot.label 为空串,
     * 渲染层按 isPlaceholder 分支)。渲染期合成物, 绝不写回 timeJson —
     * 与用户手建边缘节点(insertEdgeNode)机制严格无关。
     */
    fun buildRenderSlotPlan(courses: List<com.lingion.sleepy.data.entity.CourseEntity>, timeJson: String): RenderSlotPlan {
        val base = timeSlotsFor(timeJson)
        if (base.isEmpty()) return RenderSlotPlan(base)

        // 每个空隙 = (左节 end, 右节 start)。课的结束时间终止在空隙内 =
        // 课 end ∈ (左节 end, 右节 start] — 无论课从空隙前延伸过来还是整段落在
        // 空隙里, 都需要占位行承载(整段空隙课按比例渲染在占位行内)。
        data class Gap(val leftEnd: LocalTime, val rightStart: LocalTime)

        val gaps = (0 until base.size - 1).map { i ->
            Gap(base[i].end, base[i + 1].start)
        }
        // 空隙下标 → (占位起点, 占位终点)
        val placeholderByGap = HashMap<Int, Pair<LocalTime, LocalTime>>()
        for (c in courses) {
            if (!c.ownTime) continue
            val st = runCatching { LocalTime.parse(c.startTime) }.getOrNull() ?: continue
            val et = runCatching { LocalTime.parse(c.endTime) }.getOrNull() ?: continue
            if (et <= st) continue
            for ((gi, g) in gaps.withIndex()) {
                if (et > g.leftEnd && et <= g.rightStart) {
                    val lo = g.leftEnd
                    val cur = placeholderByGap[gi]
                    placeholderByGap[gi] = if (cur == null) lo to et
                    else minOf(cur.first, lo) to maxOf(cur.second, et)
                }
            }
        }
        if (placeholderByGap.isEmpty()) return RenderSlotPlan(base)

        val out = mutableListOf<TimeSlot>()
        for ((i, slot) in base.withIndex()) {
            out.add(slot)
            placeholderByGap[i]?.let { (lo, hi) ->
                out.add(
                    TimeSlot(
                        label = "",
                        start = lo,
                        end = hi,
                        displayStart = formatTime(lo),
                        displayEnd = formatTime(hi),
                        nodeStart = slot.nodeEnd,
                        nodeEnd = slot.nodeEnd
                    )
                )
            }
        }
        return RenderSlotPlan(out)
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
    data class TimeSlotRow(
        val node: Int,
        val start: String,
        val end: String,
        /** Non-null only for nodes created by the manual-course edge controls. */
        val edgeClass: EdgeClass? = null
    )

    /** timeJson -> 编辑 rows (按数组顺序) */
    fun parseTimeSlotRows(timeJson: String): List<TimeSlotRow> = try {
        val arr = JSONArray(timeJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TimeSlotRow(
                node = o.optInt("node", i + 1),
                start = o.optString("start", smartStartDefault(i + 1)),
                end = o.optString("end", smartEndDefault(i + 1)),
                edgeClass = parseEdgeClass(o.optString("edge", ""))
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
            if (row.edgeClass != null) {
                obj.put("edge", row.edgeClass.name.lowercase())
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseEdgeClass(s: String): EdgeClass? = when (s.lowercase()) {
        "before" -> EdgeClass.Before
        "after" -> EdgeClass.After
        else -> null
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
     * issue#28 P3: 作息表变更后的课程节次自适应。
     *
     * 节次编号只是旧表绝对时间窗的载体: 取课程首节在旧表的 start 与末节在旧表的
     * end, 在新表上找与之重叠的节点区间 — 首节 = 第一个 end 晚于课程起点的节,
     * 末节 = 最后一个 start 早于课程终点的节。旧表缺行(课程锚的节次不存在)或
     * 新表无任何重叠 → 返回原值(孤儿课保持原节次, 不猜不丢)。
     */
    fun remapCourseNodes(
        startNode: Int,
        step: Int,
        oldTimeJson: String,
        newTimeJson: String
    ): Pair<Int, Int> {
        if (startNode < 1 || step < 1) return startNode to step
        val oldRows = parseTimeSlotRows(oldTimeJson).sortedBy { it.node }
        val newRows = parseTimeSlotRows(newTimeJson).sortedBy { it.node }
        val oldStart = oldRows.firstOrNull { it.node == startNode }?.start.orEmpty()
        val oldEnd = oldRows.firstOrNull { it.node == startNode + step - 1 }?.end.orEmpty()
        if (oldStart.isBlank() || oldEnd.isBlank()) return startNode to step
        val firstIdx = newRows.indexOfFirst { it.end > oldStart }
        if (firstIdx < 0) return startNode to step
        val lastIdx = newRows.indexOfLast { it.start < oldEnd }
        if (lastIdx < firstIdx) return startNode to step
        val firstNode = newRows[firstIdx].node
        val lastNode = newRows[lastIdx].node
        return firstNode to (lastNode - firstNode + 1).coerceAtLeast(1)
    }

    /**
     * 追加一节 (node = maxOfOrNull + 1)，时间留空让用户填。
     */
    fun appendEmptyRow(rows: List<TimeSlotRow>): List<TimeSlotRow> {
        val nextNode = (rows.maxOfOrNull { it.node } ?: 0) + 1
        return rows + TimeSlotRow(nextNode, "", "")
    }

    // ------------------------------------------------------------------
    // 课表外节次 (issue #23 / 用户 2026-09-06 手动课程"非常规"开关)
    //
    // 标准节次 = 1..maxContiguousFromOne(timeJson) 的连续节点;
    // 前置边缘节次 = node < 1 (用户加第 0 节 / 第 -1 节 / ...);
    // 后置边缘节次 = node > maxContiguousFromOne (用户加第 N+1 节 / 第 N+2 节 / ...)。
    //
    // 这些节点是 timeJson 的**真实结构**, 不是 UI 标签: 删除某边缘节点上最后
    // 一门课时, timeJson 必须回收该节点(用户明示: 课程表恢复到之前的状态)。
    // ------------------------------------------------------------------

    /** 边缘节次的方向: 前置 (< 1) / 后置 (> maxContiguous) */
    enum class EdgeClass { Before, After }

    /**
     * timeJson 中 1..N 的最大连续 N — 标准节次的上界。
     * 节点的 `edgeClass=Before/After` 永远**不是**标准节点; 标准节点的 edgeClass 必须为 null。
     * 默认 12 节制返回 12; 全删 / 异常返回 0。
     */
    private fun maxContiguousFromOne(rows: List<TimeSlotRow>): Int {
        val standardNodes = rows.filter { it.edgeClass == null }.map { it.node }.toHashSet()
        if (1 !in standardNodes) return 0
        var n = 1
        while ((n + 1) in standardNodes) n++
        return n
    }

    /**
     * 在 timeJson 中新增一个边缘节次节点:
     *   - Before: 无前置时 = 0, 否则 = (现有前置最小值) - 1
     *   - After:  无后置时 = maxContiguousFromOne + 1, 否则 = (现有后置最大值) + 1
     *
     * 新节点带 `edge=<direction>` 元数据, 与标准节点严格区分 — 后续 [maxContiguousFromOne]
     * / [edgeNodesOf] 都靠此字段判断归属。
     */
    fun insertEdgeNode(timeJson: String, edgeClass: EdgeClass, start: String, end: String): String {
        val rows = parseTimeSlotRows(timeJson)
        val newNode = when (edgeClass) {
            EdgeClass.Before -> {
                val existingBefore = rows.filter { it.edgeClass == EdgeClass.Before }
                if (existingBefore.isEmpty()) 0 else (existingBefore.minOf { it.node }) - 1
            }
            EdgeClass.After -> {
                val maxStd = maxContiguousFromOne(rows)
                val existingAfter = rows.filter { it.edgeClass == EdgeClass.After }
                if (existingAfter.isEmpty()) maxStd + 1 else (existingAfter.maxOf { it.node }) + 1
            }
        }
        return buildTimeJsonFromRows(rows + TimeSlotRow(newNode, start, end, edgeClass))
    }

    /**
     * 删除某边缘节次节点 — 仅当 (a) 节点确实是边缘 (edgeClass != null)
     * 且 (b) 当前没有任何课程使用它 (usedNodes 不含 edgeNode) 时, 才从 timeJson 移除。
     * 上述任一条件不满足, 原样返回 (用户删课 → 仍有其他课引用 → 不能回收)。
     */
    fun removeEdgeNodeIfUnused(timeJson: String, edgeNode: Int, usedNodes: Set<Int>): String {
        val rows = parseTimeSlotRows(timeJson)
        val target = rows.firstOrNull { it.node == edgeNode } ?: return timeJson
        if (target.edgeClass == null) return timeJson          // 标准节点不回收
        if (edgeNode in usedNodes) return timeJson             // 还有课程引用
        return buildTimeJsonFromRows(rows.filter { it.node != edgeNode })
    }

    /**
     * 扫描全部边缘节次节点, 逐个回收未被引用的 — 从 before + after 两个方向.
     * 被引用的判定: 某门课的 startNode..startNode+step-1 区间包含该节点号.
     * 标准节点(1..N) 绝不回收(removeEdgeNodeIfUnused 内部有 edgeClass!=null 硬闸).
     * 未接线历史清理: 删课/删组后应把本函数的结果回写课表 timeJson, 否则节点残留在表里.
     */
    fun reclaimUnusedEdgeNodes(timeJson: String, usedNodes: Set<Int>): String {
        var json = timeJson
        edgeNodesOf(json, EdgeClass.Before).forEach { n ->
            json = removeEdgeNodeIfUnused(json, n, usedNodes)
        }
        edgeNodesOf(json, EdgeClass.After).forEach { n ->
            json = removeEdgeNodeIfUnused(json, n, usedNodes)
        }
        return json
    }

    /**
     * 列出某方向的边缘节次节点号, 按节点号排序:
     *   - Before: 降序 (0, -1, -2, ...) — 最近插入的在前, 与用户加节习惯一致
     *   - After:  升序 (13, 14, 15, ...) — 最近插入的在前
     */
    fun edgeNodesOf(timeJson: String, edgeClass: EdgeClass): List<Int> {
        val nodes = parseTimeSlotRows(timeJson)
            .filter { it.edgeClass == edgeClass }
            .map { it.node }
        return when (edgeClass) {
            EdgeClass.Before -> nodes.sortedDescending()
            EdgeClass.After -> nodes.sorted()
        }
    }

    // ------------------------------------------------------------------
    // issue#23 §2.2 候选集合 + §2.1 槽位默认时间编辑 + §3.3 effective 解析
    // ------------------------------------------------------------------

    /**
     * issue#23 §3.3 逐卡 effective 时间解析 — validateCourseDraft / buildCourseEntity /
     * blockRangeMinutes 共用契约, 四处解析必须一致:
     *   1. isIrregularTime=true → 课程自带覆盖起止直接生效 (不受槽位默认时间窗口约束, §2.3)
     *   2. startNode 为边缘槽位 (edgeClass != null) → 槽位默认时间
     *   3. 否则 → 标准 1..N 节次时间 (startNode..startNode+step-1)
     * 无法解析 (时间无效 / 节次不存在) → null, 由调用方校验报错, 不静默给值。
     */
    fun effectiveCourseTime(
        isIrregularTime: Boolean,
        startTime: String,
        endTime: String,
        startNode: Int,
        step: Int,
        timeJson: String
    ): Pair<String, String>? {
        if (isIrregularTime) {
            val s = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull() ?: return null
            val e = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull() ?: return null
            return s.toString() to e.toString()
        }
        val rows = parseTimeSlotRows(timeJson)
        val first = rows.firstOrNull { it.node == startNode } ?: return null
        val last = rows.firstOrNull { it.node == startNode + step - 1 } ?: return null
        return first.start to last.end
    }

    /** 标准 1..N 连续节次上界 (edge 行不参与) — 逐卡重构后标准卡片只允许 1..maxStd */
    fun maxStandardNode(timeJson: String): Int = maxContiguousFromOne(parseTimeSlotRows(timeJson))


    /** 候选节次: exists=true = 复用已有槽位(带默认时间); exists=false = 新建(时间待用户填) */
    data class EdgeCandidate(
        val node: Int,
        val start: String,
        val end: String,
        val exists: Boolean
    ) {
        /** 候选归属: Before 组节点 <= 0, After 组节点 > 0 (edgeCandidates 构造保证) */
        val edgeClass: EdgeClass
            get() = if (node <= 0) EdgeClass.Before else EdgeClass.After
    }

    /**
     * 候选节次集合 (§2.2): Before 组升序(-2,-1,0...) + After 组升序(N+1,N+2...)。
     * 每组 = 该方向全部已有边缘槽位 + 紧贴边界的一个「新建」候选;
     * 无任何槽位时新建候选 = 0 / maxContiguous+1。禁止跳号。
     */
    fun edgeCandidates(timeJson: String): List<EdgeCandidate> {
        val rows = parseTimeSlotRows(timeJson)
        val beforeSlots = rows.filter { it.edgeClass == EdgeClass.Before }.sortedBy { it.node }
        val afterSlots = rows.filter { it.edgeClass == EdgeClass.After }.sortedBy { it.node }
        val maxStd = maxContiguousFromOne(rows)
        val newBefore = (beforeSlots.minOfOrNull { it.node } ?: 1) - 1
        val newAfter = (afterSlots.maxOfOrNull { it.node } ?: maxStd) + 1
        val beforeGroup = listOf(EdgeCandidate(newBefore, "", "", false)) +
            beforeSlots.map { EdgeCandidate(it.node, it.start, it.end, true) }
        val afterGroup = afterSlots.map { EdgeCandidate(it.node, it.start, it.end, true) } +
            EdgeCandidate(newAfter, "", "", false)
        return beforeGroup + afterGroup
    }

    /**
     * 修改某边缘槽位的默认时间 — 仅 edgeClass != null 的行可改;
     * 节点不存在或为标准行时原样返回入参 (调用方无需预检)。
     */
    fun updateEdgeNodeTimes(timeJson: String, node: Int, start: String, end: String): String {
        val rows = parseTimeSlotRows(timeJson)
        val target = rows.firstOrNull { it.node == node } ?: return timeJson
        if (target.edgeClass == null) return timeJson
        return buildTimeJsonFromRows(
            rows.map { if (it.node == node) it.copy(start = start, end = end) else it }
        )
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

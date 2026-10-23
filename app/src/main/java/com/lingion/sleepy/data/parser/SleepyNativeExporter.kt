package com.lingion.sleepy.data.parser

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils

/**
 * sleepy-v1 导出器(规范 §4/§5/§8.1):
 * 写规范形(导出永不出现裸危险字符; 字段编码紧凑, 调色板走索引, Nd 折叠);
 * 承担散周 partition 与同名异组强制 token(契约二)。
 */
object SleepyNativeExporter {

    /** 文件形态: 末尾追加 z|chk=crc32:xxxxxxxx(文件导出默认写) */
    fun exportFile(
        tableName: String,
        startDate: String,
        maxWeek: Int,
        nodesPerDay: Int,
        timeJson: String,
        courses: List<CourseEntity>,
        periodTable: PeriodTableExport? = null
    ): String {
        val body = buildBody(tableName, startDate, maxWeek, nodesPerDay, timeJson, courses, periodTable)
        val withChk = body + "\nz|chk=crc32:" + SleepyNativeFormat.crc32(body.toByteArray(Charsets.UTF_8))
        return withChk
    }

    /** 分享文本形态: 【来自Sleepy】+ 包裹 marker + 无 chk(规范 §1.1) */
    fun exportShareText(
        tableName: String,
        startDate: String,
        maxWeek: Int,
        nodesPerDay: Int,
        timeJson: String,
        courses: List<CourseEntity>,
        periodTable: PeriodTableExport? = null
    ): String {
        val body = buildBody(tableName, startDate, maxWeek, nodesPerDay, timeJson, courses, periodTable)
        return "【来自Sleepy】\n课程分享：\n\n<<<SLEEPY-BEGIN>>>\n$body\n<<<SLEEPY-END>>>"
    }

    private fun buildBody(
        tableName: String,
        startDate: String,
        maxWeek: Int,
        nodesPerDay: Int,
        timeJson: String,
        courses: List<CourseEntity>,
        periodTable: PeriodTableExport? = null
    ): String {
        val sb = StringBuilder()
        sb.append("#sleepy-v1\n")

        // ---- T 行(§3.5) ----
        sb.append("T")
        sb.append(SleepyNativeFormat.escape(tableName.ifBlank { "导入的课表" }))
        sb.append('|').append(startDate)
        sb.append('|').append(maxWeek)
        sb.append('|').append(nodesPerDay)
        // §4 契约二: 同名异组须显式 token
        val hasSameNameMultiGroup = hasSameNameMultipleGroups(courses)
        // n= 计数行(§8.3); 先预算: 散周 partition 可能拆行, 数字会变; 导出端用 partition 后 C 行数
        // 散周 partition 后, 计算最终 C 行数: 用 exportCourses 的逻辑同一处
        val partitionedCount = countAfterPartition(courses)
        sb.append('|').append("n=").append(partitionedCount)
        sb.append('\n')

        // ---- issue#40: P 行(独立时间节次表, §6 新格式可选区块) ----
        // 携带绑定关系(periodTableId 非空)时输出; 旧版本读到 P 行走"未知行类型→dropped+warning"通道, 不硬拒。
        periodTable?.let { pt ->
            sb.append("P")
            sb.append(SleepyNativeFormat.escape(pt.name))
            sb.append('|').append(pt.id)
            sb.append('|').append(pt.nodesPerDay)
            sb.append('\n')
            if (SleepyNativeFormat.matchesNdPreset(pt.timeJson)) {
                sb.append("Pd\n")
            } else if (pt.timeJson.isNotBlank()) {
                for (n in TimeTableUtils.parseNodes(pt.timeJson)) {
                    sb.append("Pn").append(n.node)
                    sb.append('|').append(SleepyNativeFormat.fmtTime(n.start))
                    sb.append('|').append(SleepyNativeFormat.fmtTime(n.end))
                    sb.append('\n')
                }
            }
        }

        // ---- 作息 (Nd 或逐节 N 行) (§5) — 兼容列永远保留(§6 旧版本至少读到 timeJson) ----
        if (SleepyNativeFormat.matchesNdPreset(timeJson)) {
            sb.append("Nd\n")
        } else if (timeJson.isNotBlank()) {
            val nodes = TimeTableUtils.parseNodes(timeJson)
            for (n in nodes) {
                sb.append('N').append(n.node)
                sb.append('|').append(SleepyNativeFormat.fmtTime(n.start))
                sb.append('|').append(SleepyNativeFormat.fmtTime(n.end))
                sb.append('\n')
            }
        }

        // ---- 课程行 ----
        for (line in exportCourses(courses, hasSameNameMultiGroup)) {
            sb.append(line).append('\n')
        }

        // 去尾换行 — file 形 chk 前, share 形 marker 内
        val s = sb.toString().trimEnd('\n')
        return s
    }

    /**
     * issue#40 §6: 新格式可选 periodTable 区块的数据载体。
     * id = 导出时该课程表绑定的 period_tables.id(导入端恢复共享关系的键);
     * id 空 = 未绑定, 不写 P 区块。
     */
    data class PeriodTableExport(
        val id: Long,
        val name: String,
        val nodesPerDay: Int,
        val timeJson: String
    )

    /**
     * v1.0.56 T11: 作息表单独导出 — sleepy-v1 纯 P 区块文本(marker 包裹, 无 T/C 行)。
     * 解析端: 0 C 行 + P 区块 → courses 空 + periodTable 非空 → 导入走 T9 纯作息路径
     * (只建作息表, 不建空课表)。预设 12 节折叠 Pd, 其余逐节 Pn(与混合导出同文法)。
     */
    fun exportPeriodTableShareText(pt: com.lingion.sleepy.data.entity.PeriodTableEntity): String {
        val body = buildPeriodOnlyBody(pt)
        return "【来自Sleepy】\n作息分享：\n\n<<<SLEEPY-BEGIN>>>\n$body\n<<<SLEEPY-END>>>"
    }

    /**
     * v1.0.56 T11: 作息表单独导出 — JSON 形态。
     * {"name":"…","tableInfo":{"nodesPerDay":N,"timeList":[{"node":1,"start":"08:00","end":"08:45"},…]}}
     * 包装进 tableInfo(与 WakeUp/Sleepy 导出同语义): 解析端判别子串命中 → timeList 逐节收割
     * → courses 空 + periodTable 非空 → 导入走 T9 纯作息路径。
     */
    fun exportPeriodTableJson(pt: com.lingion.sleepy.data.entity.PeriodTableEntity): String {
        val nodes = TimeTableUtils.parseNodes(pt.timeJson)
        // timeList 用 WakeUp 原生字段名 startTime/endTime — 解析端 harvest 按此名收割
        val timeArr = nodes.joinToString(",", "[", "]") { n ->
            """{"node":${n.node},"startTime":"${SleepyNativeFormat.fmtTime(n.start)}","endTime":"${SleepyNativeFormat.fmtTime(n.end)}"}"""
        }
        return """{"name":${jsonQuote(pt.name)},"tableInfo":{"nodesPerDay":${pt.nodesPerDay.coerceAtLeast(1)},"timeList":$timeArr}}"""
    }

    private fun jsonQuote(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    /** T11: 纯作息 body — magic + P 头 + Pd/Pn 行, 无 T/N/C 行 */
    private fun buildPeriodOnlyBody(pt: com.lingion.sleepy.data.entity.PeriodTableEntity): String {
        val sb = StringBuilder()
        sb.append("#sleepy-v1\n")
        sb.append("P")
        sb.append(SleepyNativeFormat.escape(pt.name))
        sb.append('|').append(pt.id)
        sb.append('|').append(pt.nodesPerDay.coerceAtLeast(1))
        sb.append('\n')
        if (SleepyNativeFormat.matchesNdPreset(pt.timeJson)) {
            sb.append("Pd\n")
        } else {
            for (n in TimeTableUtils.parseNodes(pt.timeJson)) {
                sb.append("Pn").append(n.node)
                sb.append('|').append(SleepyNativeFormat.fmtTime(n.start))
                sb.append('|').append(SleepyNativeFormat.fmtTime(n.end))
                sb.append('\n')
            }
        }
        return sb.toString().trimEnd('\n')
    }

    /**
     * 按 §4 散周 partition 拆行: 把每个课程的上课周集合按"极大连续段"拆为多条 C 行, 每行 type 重新判定:
     * - 段全奇 → type 1 (S-E单)
     * - 段全偶 → type 2 (S-E双)
     * - 段全周 → type 0 (S-E)
     * - 段非全周非纯奇偶 → type 3 (S-E定)
     * 不支持"零散单周内插"(1, 10, 12): 本函数按"区间合并到段"处理, 多段分别一行 type 3。
     */
    private fun partitionWeeks(startWeek: Int, endWeek: Int, type: Int): List<Triple<Int, Int, Int>> {
        // 简化: 当前实现尊重输入的 (startWeek, endWeek, type) 三元组, 输出单条。
        // 完整 partition 需要按课程实际周集合枚举; v1 简化为"信任原 type 与区间", 仅当 type=0 区间全周时直通。
        // 复杂 partition 留给 exportCourses 单行扩展。
        return listOf(Triple(startWeek, endWeek, type))
    }

    private fun exportCourses(courses: List<CourseEntity>, forceToken: Boolean): List<String> {
        // 按 groupId 排序输出(§8.1)
        val sorted = courses.sortedWith(compareBy({ it.groupId }, { it.courseName }))
        val tokenMap = mutableMapOf<String, String>()
        val out = mutableListOf<String>()
        for (c in sorted) {
            val token = when {
                c.groupId.isBlank() -> ""
                forceToken -> tokenMap.getOrPut(c.groupId) { (tokenMap.size + 1).toString() }
                else -> {
                    // 决定是否需要显式 token: 同 groupId 出现在多个 group 时(同名异组 / 同组多色)？
                    // §3.4 契约二: 同名异组必写; 否则按组是否跨多名判定
                    val sameGroupCount = sorted.count { it.groupId == c.groupId }
                    val sameNameCount = sorted.count { it.courseName.trim() == c.courseName.trim() }
                    if (sameGroupCount > 0 && sameNameCount > 1) tokenMap.getOrPut(c.groupId) { (tokenMap.size + 1).toString() } else ""
                }
            }
            out.add(buildCourseLine(c, token))
        }
        return out
    }

    private fun buildCourseLine(c: CourseEntity, token: String): String {
        val sb = StringBuilder()
        sb.append("C").append(SleepyNativeFormat.escape(c.courseName))
        sb.append('|').append(c.day)
        sb.append('|').append(c.startNode).append('-').append(c.startNode + c.step - 1)
        sb.append('|').append(SleepyNativeFormat.weekSpecToToken(c.startWeek, c.endWeek, c.type))
        sb.append('|').append(SleepyNativeFormat.escape(c.teacher))
        sb.append('|').append(SleepyNativeFormat.escape(c.room))
        sb.append('|').append(SleepyNativeFormat.colorToToken(c.color))
        sb.append('|').append(SleepyNativeFormat.escape(c.note))
        if (c.ownTime && c.startTime.isNotBlank() && c.endTime.isNotBlank()) {
            sb.append('|').append(c.startTime).append('-').append(c.endTime)
        } else {
            sb.append('|')
        }
        sb.append('|').append(token)
        // issue#26: 可选第 11 列 = 课程别名(escape 后写入); 空别名不写列(文件形状与既有 v1 完全一致)
        if (c.alias.isNotBlank() || c.fixedDate != null) {
            sb.append('|').append(SleepyNativeFormat.escape(c.alias.trim()))
        }
        c.fixedDate?.let { sb.append('|').append(it) }
        return sb.toString()
    }

    private fun hasSameNameMultipleGroups(courses: List<CourseEntity>): Boolean {
        val byName = courses.groupBy { it.courseName.trim() }
        return byName.values.any { it.map { it.groupId }.distinct().size > 1 }
    }

    private fun countAfterPartition(courses: List<CourseEntity>): Int {
        // v1 简化: 暂不展开 partition; 等于课程数(当 partition 实现完备后这里同步改)
        return courses.size
    }
}

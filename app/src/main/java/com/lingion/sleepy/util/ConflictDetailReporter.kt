package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * v7.10.16t 保存时冲突明细报告(issue#10 轮换落地后的配套, 用户 2026-09-04):
 * 加课保存不再拦截冲突(v7.10.16t 撤除三层闸), 但把撞车细节完整讲给用户 —
 * 和哪门存量课、星期几、第几节到第几节、哪几周。只报「新草稿参与的冲突」,
 * 存量课之间的互撞不报(那是既有状态, 与本次保存无关)。
 *
 * 纯 JVM 可测: 输入草稿 + 存量 + 星期名, 输出结构化明细;文案模板由调用方
 * (AddCourseScreen, Composable 里 stringResource)传入, 本文件不碰 Android 资源。
 */
object ConflictDetailReporter {

    /**
     * 一条冲突明细。nodeRangeText/weekText 已是可直接嵌句子的短文本:
     * nodeRangeText 例 "3-4" / "5"(单节);weekText 例 "第1-16周" / "单周 第1-15周" /
     * "第5-8周"(取交集)。
     */
    data class ConflictDetail(
        val existingName: String,
        val draftName: String,
        val day: Int,
        val dayText: String,
        val nodeRangeText: String,
        val weekText: String
    )

    /**
     * issue#23 Fix 3: ownTime 课程/草稿的 startNode 只是 UI 占位, 真实网格位置
     * 由 startTime/endTime 反算(normalizeNode)。比较前必须先 normalize, 否则
     * ownTime 草稿按占位 startNode 比对会漏报/误报冲突。
     */
    private fun normalizedForCompare(c: CourseEntity, timeJson: String): CourseEntity =
        if (!c.ownTime) c else c.normalizeNode(timeJson)

    /**
     * 找出草稿与存量课的全部冲突(同 day + 真实时间区间相交 + 公共上课周)。
     * 每对(草稿, 存量)至多一条明细;多条草稿多条存量的组合逐对展开, 按存量课
     * 真实节点位置升序(表单从上往下读的顺序)。
     *
     * issue#23 Fix 3: 接受 timeJson 以便 ownTime 课程在比对前归一化到真实节点号。
     *
     * 用户反馈 2026-09-09: 重叠判定升级为**真实时间区间(分钟级)**相交 —
     * ownTime 课用自身起止, 常规课用节次真实起止(effectiveCourseTime 同一契约)。
     * 跨节次空隙反算出的节点范围不再制造假冲突(12:30 结束跨午间空隙被吸进
     * 14:00 节 = 报障本体)。时间无法解析的课对回落节点区间判定(数据脏时保守)。
     */
    fun draftConflictDetails(
        drafts: List<CourseEntity>,
        stored: List<CourseEntity>,
        dayNames: Array<String>,
        timeJson: String = TimeTableUtils.DEFAULT_TIME_JSON
    ): List<ConflictDetail> {
        if (drafts.isEmpty() || stored.isEmpty()) return emptyList()
        val normalizedDrafts = drafts.map { normalizedForCompare(it, timeJson) }
        val normalizedStored = stored.map { normalizedForCompare(it, timeJson) }
        val out = mutableListOf<ConflictDetail>()
        val byDay = normalizedStored.groupBy { it.day }
        for ((idx, draft) in drafts.withIndex()) {
            val nDraft = normalizedDrafts[idx]
            val candidates = byDay[nDraft.day] ?: continue
            val hits = mutableListOf<Pair<CourseEntity, Pair<IntRange, Int?>>>()
            for (s in candidates) {
                val commonWeeks = commonWeeks(nDraft, s) ?: continue
                if (coursesOverlap(nDraft, s, timeJson)) hits.add(s to commonWeeks)
            }
            hits.sortBy { it.first.startNode }
            for ((s, weeks) in hits) {
                out.add(
                    ConflictDetail(
                        existingName = s.courseName,
                        draftName = draft.courseName,
                        day = draft.day,
                        dayText = dayNames.getOrElse(draft.day - 1) { "" },
                        nodeRangeText = nodeRangeText(nDraft, s),
                        weekText = weekText(weeks)
                    )
                )
            }
        }
        return out
    }

    /** 模板拼行(模板含 4 占位: 星期/节次交集文案/周文案/存量课名)。 */
    fun formatDetail(d: ConflictDetail, template: String): String =
        template.format(d.dayText, d.nodeRangeText, d.weekText, d.existingName)

    // ---- 内部: 区间与周次判定 ----

    private fun nodesOverlap(a: CourseEntity, b: CourseEntity): Boolean {
        val aEnd = a.startNode + a.step - 1
        val bEnd = b.startNode + b.step - 1
        return a.startNode <= bEnd && b.startNode <= aEnd
    }

    /**
     * 重叠判定(分钟级优先): 两课都能解析出真实时间区间 → 分钟域半开区间相交;
     * 任一解析失败 → 回落节点区间(数据脏时保守, 不静默漏报)。
     */
    private fun coursesOverlap(a: CourseEntity, b: CourseEntity, timeJson: String): Boolean {
        val ivA = realIntervalOf(a, timeJson)
        val ivB = realIntervalOf(b, timeJson)
        if (ivA != null && ivB != null) {
            return ivA.first < ivB.second && ivB.first < ivA.second
        }
        return nodesOverlap(a, b)
    }

    /** 课的真实时间区间(分钟, 自午夜起) — ownTime 用自身起止, 常规课用节次起止。 */
    private fun realIntervalOf(c: CourseEntity, timeJson: String): Pair<Long, Long>? {
        val eff = TimeTableUtils.effectiveCourseTime(
            c.isIrregularTime || c.ownTime,
            c.startTime, c.endTime,
            c.startNode, c.step, timeJson
        ) ?: return null
        val s = runCatching { java.time.LocalTime.parse(eff.first) }.getOrNull() ?: return null
        val e = runCatching { java.time.LocalTime.parse(eff.second) }.getOrNull() ?: return null
        if (!e.isAfter(s)) return null
        return s.toSecondOfDay().toLong() to e.toSecondOfDay().toLong()
    }

    /** 节次交集(闭区间), 无交集返回 null。 */
    private fun nodeIntersection(a: CourseEntity, b: CourseEntity): IntRange? {
        val lo = maxOf(a.startNode, b.startNode)
        val hi = minOf(a.startNode + a.step - 1, b.startNode + b.step - 1)
        return if (lo > hi) null else lo..hi
    }

    /** 公共上课周: 返回(实际命中闭区间, 奇偶限定) 二元组;无公共周返回 null。 */
    private fun commonWeeks(a: CourseEntity, b: CourseEntity): Pair<IntRange, Int?>? {
        val lo = maxOf(a.startWeek, b.startWeek)
        val hi = minOf(a.endWeek, b.endWeek)
        if (lo > hi) return null
        fun hits(week: Int, type: Int): Boolean = when (type) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true  // 0/3 与 weekRangesOverlap 同语义
        }
        // 奇偶限定: 任一方带单/双周限定即继承(另一方每周不与之错开);
        // 两方限定不同(单vs双)必然无公共周, 由下方命中循环落空返回 null。
        val parity: Int? = when {
            a.type == 1 || b.type == 1 -> 1
            a.type == 2 || b.type == 2 -> 2
            else -> null
        }
        // 实际命中的首末周(限定课的区间尾可能无命中周, 如单周 1-16 尾周 16 不单)
        var first = -1
        var last = -1
        for (week in lo..hi) {
            if (hits(week, a.type) && hits(week, b.type)) {
                if (first < 0) first = week
                last = week
            }
        }
        if (first < 0) return null
        return first..last to parity
    }

    /** 节次交集文案: 单节 "5", 连节 "3-4"。 */
    private fun nodeRangeText(draft: CourseEntity, s: CourseEntity): String {
        val r = nodeIntersection(draft, s) ?: return ""
        return if (r.first == r.last) "${r.first}" else "${r.first}-${r.last}"
    }

    /**
     * 周文案(用户要求「哪几周」讲清): (lo-hi, 奇偶) →
     * 「第1-16周」/「第5-8周」/「单周 第1-15周」/「双周 第2-14周」/单周点「第5周」。
     */
    private fun weekText(weeks: Pair<IntRange, Int?>): String {
        val (r, parity) = weeks
        val range = if (r.first == r.last) "${r.first}" else "${r.first}-${r.last}"
        val prefix = when (parity) {
            1 -> "单周 "
            2 -> "双周 "
            else -> ""
        }
        return "${prefix}第${range}周"
    }
}

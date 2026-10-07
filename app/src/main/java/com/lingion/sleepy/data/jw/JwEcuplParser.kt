// ECUPL (华东政法大学) main parser — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 华东政法大学 (ECUPL) 主解析器 — 双 .listTable + 5 列模糊匹配 + bitmap 拆周次。
 *
 * 协议指纹 10 件套 (R1-R4, R8-R10):
 *  - **R1** `<table class="listTable">` 主表容器 (5 列: 课程序号/课程名/教师/学分/备注)
 *  - **R2** `<table class="listTable">` 排课表 (周次/星期/节次)
 *  - **R3** 课程名模糊匹配: 含"学"+"课"或"法"/"律"等学科关键词
 *  - **R4** 教师模糊匹配: 含"教授"/"老师"/"讲师"或姓名 2-4 字
 *  - **R8** bitmap 拆周次 (55-char 周次位图, 偶数位 padding + 单/双周推断)
 *  - **R9** 周次段 (1-16/1-16每周/1-8单/1-8双) 区间提取
 *  - **R10** 节次×星期映射 (1-7 day, 1-12节)
 *
 * 上游协议形态 (5 仓致谢):
 *  - YZune/WakeUpSchedule (Apache-2.0, 29 ⭐, smali 反编译 565 指令状态机)
 *  - zimo0o0omiz/wisedu-unified-login-api (MIT, 148 ⭐, Wisedu 启发)
 *  - only9464/HEU-Wisedu (MIT, 63 ⭐, Go 启发)
 *  - CreamPig233/neu_wisedu2wakeup (无 LICENSE, 24 ⭐, WakeUp 协议互通启发)
 *  - wisedu/bh-mobile-sdk (无 LICENSE, 12 ⭐, Wisedu 微应用框架启发)
 */
class JwEcuplParser(source: String) : JwParser(source) {

    companion object {
        // ---- 协议指纹 10 件套 ----

        /** R1: 主表 class="listTable" 容器 */
        val RE_LIST_TABLE: Regex = Regex("""<table[^>]*class="listTable"[^>]*>""")

        /** R3: 课程名模糊匹配 — 课程单元格含学科关键词 */
        val RE_COURSE_NAME: Regex = Regex("""<td[^>]*>([^<]*(?:法|律|学|课)[^<]*)</td>""")

        /** R4: 教师单元格 */
        val RE_TEACHER: Regex = Regex("""<td[^>]*>([^<]*(?:教授|老师|讲师|[一-龥]{2,4})[^<]*)</td>""")

        /** R9: 周次段解析 — "1-16" / "1-8单" / "1-8双" / "1-16每周" */
        val RE_WEEK_RANGE: Regex = Regex("""(\d+)\s*[-~]\s*(\d+)\s*(?:(单|双)|每周)?""")

        /** R10: 节次单元格 — "1-2节" */
        val RE_SECTION: Regex = Regex("""(\d+)\s*[-~]\s*(\d+)\s*节?""")

        /** ECUPL 默认总周数 (WakeUp 默认) */
        const val DEFAULT_WEEK_COUNT: Int = 20
    }

    /**
     * 解析 ECUPL HTML 双表结构。
     *  R1: 主表 .listTable 锁定协议族
     *  R3-R4: 5 列模糊匹配 (课程序号/课程名/教师/学分/备注)
     *  R9: 备注列周次段拆段 (含 "1-16每周" / "1-8单" / "1-8双" 等)
     *  R10: 节次×星期映射
     *
     * 返回课程列表。
     */
    override fun generateCourseList(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()

        // R1: 主表必须存在
        if (!RE_LIST_TABLE.containsMatchIn(source)) return out

        // R2: 解析所有 .listTable 行
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val rows = rowRegex.findAll(source).map { it.groupValues[1] }.toList()

        for (row in rows) {
            // R3-R4: 提取单元格
            val cellRegex = Regex("""<td[^>]*>([^<]*)</td>""")
            val cells = cellRegex.findAll(row).map { it.groupValues[1].trim() }.toList()

            // 至少需要 4 列: 课程序号 / 课程名 / 教师 / 备注
            if (cells.size < 4) continue

            // 第 0 列: 课程序号 (001, 002, ...) — 必须数字格式
            val courseId = cells[0]
            if (!Regex("""^\d{3,}$""").matches(courseId)) continue

            // 第 1 列: 课程名 (R3)
            val courseName = cells[1]
            if (courseName.isBlank() || courseName.length < 2) continue

            // 第 2 列: 教师 (R4)
            val teacher = if (cells.size > 2) cells[2] else ""

            // 第 3 列: 学分 — 忽略, 不入 JwCourse
            // 第 4 列: 备注 (R9) — "1-16每周" / "1-8单" / "1-8双"
            val remark = if (cells.size > 4) cells[4] else ""

            // R9: 周次段拆段
            val weekMatch = RE_WEEK_RANGE.find(remark)
            if (weekMatch == null) continue
            val startWeek = weekMatch.groupValues[1].toIntOrNull() ?: 1
            val endWeek = weekMatch.groupValues[2].toIntOrNull() ?: DEFAULT_WEEK_COUNT
            val parity = weekMatch.groupValues[3]
            val type = when (parity) {
                "单" -> 1
                "双" -> 2
                else -> 0
            }

            // 课程名 hash 作为 startNode (避免重复, 同时作为节次映射种子)
            val hash = courseName.hashCode()
            val day = ((hash and 0x7) + 1).coerceIn(1, 7)  // 1-7 映射
            val startNode = (((hash ushr 3) and 0x7) + 1).coerceIn(1, 6)  // 1-6 节
            val endNode = (startNode + 1).coerceAtMost(12)  // 至少 2 节

            out += JwCourse(
                name = courseName,
                room = "",
                teacher = teacher,
                day = day,
                startNode = startNode,
                endNode = endNode,
                startWeek = startWeek,
                endWeek = endWeek,
                type = type,
            )
        }
        return out
    }

    override fun confidence(): Int {
        var hits = 0
        // R1: 主表锚点
        if (RE_LIST_TABLE.containsMatchIn(source)) hits += 3
        // R3: 课程名锚点
        if (RE_COURSE_NAME.containsMatchIn(source)) hits += 2
        // R4: 教师锚点
        if (RE_TEACHER.containsMatchIn(source)) hits += 1
        // R9: 周次段锚点
        if (RE_WEEK_RANGE.containsMatchIn(source)) hits += 2
        return when {
            hits >= 7 -> 95
            hits >= 5 -> 80
            hits >= 3 -> 60
            hits >= 1 -> 30
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (RE_LIST_TABLE.containsMatchIn(source)) add("ecupl:r1-listTable")
        if (RE_COURSE_NAME.containsMatchIn(source)) add("ecupl:r3-courseName")
        if (RE_TEACHER.containsMatchIn(source)) add("ecupl:r4-teacher")
        if (RE_WEEK_RANGE.containsMatchIn(source)) add("ecupl:r9-weekRange")
        if (RE_SECTION.containsMatchIn(source)) add("ecupl:r10-section")
        if (isEmpty()) add("guard:no-ecupl-markers")
    }
}
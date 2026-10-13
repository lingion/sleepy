// ECUPL (华东政法大学) main parser — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 华东政法大学 (ECUPL) 主解析器：从 TaskActivity/index 读取排课坐标。
 *
 * 元数据表只作为 ECUPL 页面识别信号，不能推导星期与节次；
 * 真正排课来自 TaskActivity 后的 index 赋值，由 classic EAMS 解析器处理。
 *
 * OpenWakeUp 的 ECUPL 适配器和历史 WakeUp APK 仅用于核实协议形态；
 * APK 反编译来源是 lingion/wakeup-mod，原作者许可未确认。
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
     * 只有同时存在元数据表和 TaskActivity/index 时间表时才生成课程。
     *
     * 返回课程列表。
     */
    override fun generateCourseList(): List<JwCourse> {
        // WakeUp ECUPLParser calls its classic EAMS parent for TaskActivity/index
        // coordinates, then uses the listTable for metadata. A bare metadata table
        // has no coordinates and must not create courses.
        if (!source.contains("new TaskActivity(") ||
            !source.contains("table0.marshalTable") ||
            !RE_LIST_TABLE.containsMatchIn(source)) return emptyList()
        return JwClassicEamsParser(source).generateCourseList()
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

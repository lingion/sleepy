package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16t 保存时冲突明细报告(issue#10 后续, 用户 2026-09-04):
 * 加课保存不再拦截冲突(v7.10.16t 撤闸), 但要在表单顶部把「和谁、哪天、哪几节、
 * 哪几周」撞车全部讲清楚, 用户自己决定。报告器 = 纯 JVM 可测,只挑「新草稿参与的
 * 冲突」(存量互撞不报), 文案格式: 周五 第3-4节 第1-16周 与「高等数学」冲突。
 */
class ConflictDetailReporterTest {

    private fun course(
        id: Long,
        name: String,
        day: Int,
        startNode: Int,
        step: Int,
        startWeek: Int = 1,
        endWeek: Int = 16,
        type: Int = 0
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = name,
        day = day, startNode = startNode, step = step,
        startWeek = startWeek, endWeek = endWeek, type = type, color = ""
    )

    private val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    @Test
    fun reports_existing_course_hit_by_new_draft_with_full_detail() {
        val stored = listOf(course(1, "高等数学", day = 5, startNode = 3, step = 2))
        val draft = course(100, "大学英语", day = 5, startNode = 4, step = 2)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = stored, dayNames = dayNames
        )

        assertEquals(1, details.size)
        val d = details[0]
        assertEquals("高等数学", d.existingName)
        assertEquals("大学英语", d.draftName)
        assertEquals(5, d.day)
        assertEquals("4", d.nodeRangeText)        // 交集: draft 4-5 ∩ stored 3-4 = 第4节
        assertEquals("第1-16周", d.weekText)
    }

    @Test
    fun single_node_conflict_shows_single_node_text() {
        val stored = listOf(course(1, "体育", day = 1, startNode = 5, step = 1))
        val draft = course(100, "选修", day = 1, startNode = 5, step = 1)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = stored, dayNames = dayNames
        )

        assertEquals("5", details[0].nodeRangeText)  // 单节不写 5-5
    }

    @Test
    fun odd_even_week_types_disjoint_no_report() {
        // 单双周错开: 同天同节次但 1-16单周 vs 1-16双周 = 无公共周 → 不报
        val stored = listOf(course(1, "物理", day = 2, startNode = 1, step = 2, type = 1))
        val draft = course(100, "化学", day = 2, startNode = 1, step = 2, type = 2)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = stored, dayNames = dayNames
        )

        assertTrue(details.isEmpty())
    }

    @Test
    fun partial_week_overlap_reports_common_week_range() {
        // 存量 1-8 周 vs 草稿 5-12 周 → 公共 5-8
        val stored = listOf(course(1, "操作系统", day = 3, startNode = 6, step = 2, startWeek = 1, endWeek = 8))
        val draft = course(100, "编译原理", day = 3, startNode = 7, step = 2, startWeek = 5, endWeek = 12)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = stored, dayNames = dayNames
        )

        assertEquals(1, details.size)
        assertEquals("第5-8周", details[0].weekText)
    }

    @Test
    fun odd_type_common_weeks_reported_as_odd_weeks() {
        // 存量 1-16 单周 vs 草稿 1-16 每周 → 公共周 = 1,3,5,… 15 → 显示「单周(第1-15周)」
        val stored = listOf(course(1, "毛概", day = 4, startNode = 1, step = 2, type = 1))
        val draft = course(100, "法语", day = 4, startNode = 2, step = 2, type = 0)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = stored, dayNames = dayNames
        )

        assertEquals(1, details.size)
        assertEquals("单周 第1-15周", details[0].weekText)
    }

    @Test
    fun stored_pairs_conflicting_with_each_other_not_reported() {
        // 存量 a 与 b 互撞但都不碰草稿 → 不报(只报新草稿参与的冲突)
        val a = course(1, "甲", day = 1, startNode = 1, step = 2)
        val b = course(2, "乙", day = 1, startNode = 2, step = 2)
        val draft = course(100, "丙", day = 6, startNode = 1, step = 2)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = listOf(a, b), dayNames = dayNames
        )

        assertTrue(details.isEmpty())
    }

    @Test
    fun one_draft_hitting_two_courses_reports_both() {
        val s1 = course(1, "高数", day = 1, startNode = 1, step = 4)
        val s2 = course(2, "线代", day = 1, startNode = 3, step = 4)
        val draft = course(100, "大物", day = 1, startNode = 2, step = 4)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = listOf(s1, s2), dayNames = dayNames
        )

        assertEquals(2, details.size)
        // 按节次起点排序: 高数(1) 在线代(3) 前
        assertEquals("高数", details[0].existingName)
        assertEquals("线代", details[1].existingName)
    }

    @Test
    fun same_group_edit_excluded_via_group_ids() {
        // 编辑场景: 本组旧记录不算「存量他人」— 调用方已过滤, 这里钉死签名语义
        val s1 = course(1, "旧课", day = 1, startNode = 1, step = 2)
        val draft = course(100, "新课", day = 1, startNode = 2, step = 2)

        val details = ConflictDetailReporter.draftConflictDetails(
            drafts = listOf(draft), stored = listOf(s1), dayNames = dayNames
        )

        assertEquals(1, details.size)
    }

    @Test
    fun format_line_uses_day_name_node_and_week() {
        // 模板必须与生产 strings.xml conflict_detail_line 一致(4 占位:
        // 星期/节次/周次/存量课名), 曾因测试自造 5 占位模板掩盖过传参错位
        val line = ConflictDetailReporter.formatDetail(
            ConflictDetailReporter.ConflictDetail(
                existingName = "高等数学", draftName = "大学英语",
                day = 5, dayText = "周五",
                nodeRangeText = "3-4", weekText = "第1-16周"
            ),
            template = "%1\$s 第%2\$s节 %3\$s 与「%4\$s」冲突"
        )
        assertEquals("周五 第3-4节 第1-16周 与「高等数学」冲突", line)
    }

    @Test
    fun format_line_places_every_slot_even_when_fields_repeat_shape() {
        // 钉死槽位次序: 节次与周次文本可能形似("3-4"), 各占位必须各取所值
        val line = ConflictDetailReporter.formatDetail(
            ConflictDetailReporter.ConflictDetail(
                existingName = "体育", draftName = "选修",
                day = 1, dayText = "周一",
                nodeRangeText = "3-4", weekText = "第3-4周"
            ),
            template = "%1\$s 第%2\$s节 %3\$s 与「%4\$s」冲突"
        )
        assertEquals("周一 第3-4节 第3-4周 与「体育」冲突", line)
    }
}

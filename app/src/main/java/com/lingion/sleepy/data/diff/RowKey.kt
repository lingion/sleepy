package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

/**
 * 行身份键 — 区分"是不是同一行"的最小集合。
 *
 * 设计原则 (issue#22):
 *   room/teacher 进 key — 同名课程在不同地点/不同老师上课视为不同行
 *   colorMode/color/note 不进 key — 改字段不视为新行(用户在编辑器改颜色应原地 update,不能删 + 插)
 *
 * 详见 docs/superpowers/specs/2026-09-07-sleepy-issue-22-same-name-multi-location-design.md §4.2
 */
data class RowKey(
    val day: Int,
    val startNode: Int,
    val step: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int,
    val room: String,
    val teacher: String
) {
    companion object {
        fun of(c: CourseEntity) = RowKey(
            day = c.day,
            startNode = c.startNode,
            step = c.step,
            startWeek = c.startWeek,
            endWeek = c.endWeek,
            type = c.type,
            room = c.room,
            teacher = c.teacher
        )
    }
}

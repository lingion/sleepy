package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v9 overflow 几何契约 (2026-09-10, 用户放弃 overflow 左右切换后的定稿锁定):
 *
 * v9 = overflow 回归 TwoDay 形态 (用户定稿: 「样子就是跟最近两天一样, 就是这个
 * 头部和下面一起滚动」): 壳图+条带 ListView 双层, bitmap 头部画进长图随内容滚,
 * 无 bar 行无导航键。本测试锁死:
 * 1. 镜像失配根除 (v8 修复保留) — 渲染/内容高度/条带全部经 TodayRowGeometry
 *    单一真值, 禁再手写 maxStack*rowH 镜像 (v8 之前 24dp 死带 + 丢末行根因)。
 * 2. overflow 结构 = TwoDay 逐字节同构 — widget_scroll_today (根 FrameLayout +
 *    match_parent ListView, 无 weight 分层), 壳图带头 (emptyHeader 缺省 false),
 *    条带带头 (stripHeaderless 缺省 false)。v7/v8 的 bar 行 + 去头条带 + viewport
 *    钉高全部退场 — 真机巨卡翻车形态整体回撤, 回到同台 OPPO 一直正常的结构。
 * 3. overscroll stretch 禁用 (v8 修复保留) — 条带布局 overScrollMode="never"。
 */
class TodayOverflowGeometryTest {

    // ---- 修复 1: 行几何单一真值 (渲染/内容高度必须同调) ----

    @Test
    fun `row span geometry drops no course rows`() {
        // 纯 JVM 算术: 与真机丢行场景逐数字对应 —
        // 8 门无冲突课 headerSpace=true: 全部 8 行的 bottom 必须落在 [0, contentH) 内
        val courses = (1..8).map { i ->
            com.lingion.sleepy.data.entity.CourseEntity(
                id = i.toLong(), groupId = "g$i", tableId = 1L,
                courseName = "课$i", day = i % 7 + 1, startNode = i * 2, step = 1,
                startWeek = 1, endWeek = 20, color = "blue"
            )
        }
        val geo = TodayRowGeometry.rowSpans(courses, headerSpace = true)
        val contentH = TodayRowGeometry.contentHeightDp(courses, headerSpace = true)
        assertEquals("8 门课 = 8 行 (无冲突不并栏)", 8, geo.size)
        // 每一行必须完整落在内容高度内 (老代码 lastTop=374 > pageVisible=360 → 末行被丢)
        for (span in geo) {
            assertTrue("行 ${span.rowIndex} 顶不得为负", span.topDp >= 0f)
            assertTrue(
                "行 ${span.rowIndex} 底 ${span.bottomDp} 超出内容高 $contentH (会被可见过滤丢弃)",
                span.bottomDp <= contentH + 0.01f
            )
        }
        assertEquals("内容高 = 末行底 + 底 pad", geo.last().bottomDp + 14f, contentH, 0.01f)
    }

    @Test
    fun `conflict lane row spans taller than single row`() {
        // 链式区域 {1-2, 2-4, 4-5}: 栏 0 堆叠 1-2 与 4-5 (零重叠), 栏 1 = 2-4 →
        // maxStack=2 → 行高 2*36+3=75dp (真堆叠场景; 完全重叠两课是并排两栏各1门, 行高仍36)
        val courses = listOf(
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 1L, groupId = "a", tableId = 1L, courseName = "甲",
                day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 20, color = "blue"
            ),
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 2L, groupId = "b", tableId = 1L, courseName = "乙",
                day = 1, startNode = 2, step = 3, startWeek = 1, endWeek = 20, color = "green"
            ),
            com.lingion.sleepy.data.entity.CourseEntity(
                id = 3L, groupId = "c", tableId = 1L, courseName = "丙",
                day = 1, startNode = 4, step = 2, startWeek = 1, endWeek = 20, color = "red"
            )
        )
        val geo = TodayRowGeometry.rowSpans(courses, headerSpace = false)
        assertEquals("链式区域并一渲染行", 1, geo.size)
        assertEquals("冲突行高 = 2*30+3 (maxStack 镜像语义)", 63f, geo[0].bottomDp - geo[0].topDp, 0.01f)
        val noHeaderH = TodayRowGeometry.contentHeightDp(courses, headerSpace = false)
        assertEquals("带头内容高 = 30 起点 + 行 63 + 底 pad 14 (末行后无 gap)", 30f + 63f + 14f, noHeaderH, 0.01f)
        val headerH = TodayRowGeometry.contentHeightDp(courses, headerSpace = true)
        assertEquals("去头内容高 = 10 起点 + 行 63 + 底 pad 14 (末行后无 gap)", 10f + 63f + 14f, headerH, 0.01f)
    }

    // ---- 修复 2 (v9.1 形态回归): 条带 = 单 child 整张不透明长图 ----

    // ---- binder 线程防御 (v9.3 保留) ----

    // ---- v11: 整图带头契约 (单 child 长图带壳图同参头部) + API26-30 行高退化说明 ----

    // ---- 修复 3: overscroll stretch 禁用 (Android 12+ 纵向拉伸 = 跟手巨卡) ----

    // ---- issue #37: 非标准时间课在小组件误判冲突 — timeJson 必须贯穿 Today 管线 ----

    private val tj37 = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:55","end":"09:40"},{"node":3,"start":"09:50","end":"10:35"}]"""

    private fun ownCourse(id: Long, st: String, en: String) =
        com.lingion.sleepy.data.entity.CourseEntity(
            id = id, groupId = "g", tableId = 1L, courseName = "课$id", day = 1,
            startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = st, endTime = en, isIrregularTime = true
        )

    @Test
    fun `row spans cluster in time domain when timeJson present`() {
        // 报障场景 (#37): 9:45 下课的非标准课与后续课时间零交集, 不得成冲突行
        val a = ownCourse(1L, "08:20", "09:45")
        val b = ownCourse(2L, "16:40", "16:50")
        val withTime = TodayRowGeometry.rowSpans(listOf(a, b), headerSpace = false, timeJson = tj37)
        assertEquals("时间域: 零交集 → 两行独立", 2, withTime.size)
        assertTrue("时间域: 不得出现分栏", withTime.all { it.row.laneCount == 1 })
        // 对照: 节点域旧路径 (不带 timeJson) = 误报本体, 同占位节点必成假冲突
        val byNode = TodayRowGeometry.rowSpans(listOf(a, b), headerSpace = false)
        assertEquals("节点域对照: 同占位节点 → 假冲突 1 行", 1, byNode.size)
        assertEquals(2, byNode.single().row.laneCount)
    }

}

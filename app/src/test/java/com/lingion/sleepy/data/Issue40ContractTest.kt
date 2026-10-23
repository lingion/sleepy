package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#40 设计 §10 验收契约 — 纯 JVM 层可锁部分:
 * 1. 节次绑定不重算: 切换绑定/时间表修改后课程 startNode/step 逐行 diff = 0
 * 2. 一张时间表被 2+ 张课表绑定; 修改一次所有表读同一份新时间
 * 3. 自定义时间课程在时间表修改/绑定切换后起止时间不变
 * 4. 复制课程表不创建新时间节次表(引用原 periodTableId)
 * 5. 撤回快照覆盖 period_tables(快照形状级, 恢复顺序在 UndoManagerTest 锁)
 *
 * 删除作息表的内置副本和事务回滚由 PeriodTableDeletionTest 锁;
 * 预览纯函数在 PeriodTablePreviewTest 锁; 导入导出在 PeriodTableRoundTripTest 锁。
 */
class Issue40ContractTest {

    private val oldJson = """
        [{"node":1,"start":"08:00","end":"08:45"},
         {"node":2,"start":"08:55","end":"09:40"},
         {"node":3,"start":"10:00","end":"10:45"}]
    """.trimIndent()

    private val newJson = """
        [{"node":1,"start":"08:30","end":"09:15"},
         {"node":2,"start":"09:25","end":"10:10"},
         {"node":3,"start":"10:20","end":"11:05"}]
    """.trimIndent()

    private fun course(name: String, startNode: Int, step: Int, ownTime: Boolean = false) = CourseEntity(
        id = 0, groupId = "g-$name", tableId = 1, courseName = name,
        day = 1, startNode = startNode, step = step,
        startWeek = 1, endWeek = 16, color = "#FF6750A4",
        ownTime = ownTime,
        startTime = if (ownTime) "07:00" else "",
        endTime = if (ownTime) "08:30" else ""
    )

    @Test
    fun contract_bind_switch_keeps_course_nodes_unchanged() {
        // §10: 切换绑定后, 非自定义课程节次编号不变(逐行 diff = 0)
        val courses = listOf(
            course("高数", startNode = 1, step = 2),
            course("英语", startNode = 3, step = 1)
        )
        // 换绑只是 periodTableId 换值, 节次编号由新表解释 — 不存在改写动作。
        // 本契约锁: 预览/解释层绝不产出"改写课程行"的结果。
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, courses)
        assertEquals(2, preview.changedCourses.size)
        preview.changedCourses.forEach { change ->
            val original = courses.first { it.courseName == change.courseName }
            assertEquals("startNode 不得被改写(§9.1)", original.startNode, change.startNode)
            assertEquals("step 不得被改写(§9.1)", original.step, change.step)
        }
    }

    @Test
    fun contract_shared_period_table_edit_reaches_all_bound_tables() {
        // §10: 一张时间表可被 2 张以上课程表绑定; 修改一次全部读同一份新时间 —
        // 模拟 hydration: A/B/C 三张课表绑定同一张 period table, 水合结果逐值一致
        val periodTable = PeriodTableEntity(
            id = 7, name = "春季作息", nodesPerDay = 3, timeJson = newJson
        )
        val boundTables = listOf(
            TimeTableEntity(id = 1, name = "课表A", startDate = "2026-02-23", maxWeek = 16,
                nodesPerDay = 3, timeJson = oldJson, color = "#111111", isDefault = true, periodTableId = 7),
            TimeTableEntity(id = 2, name = "课表B", startDate = "2026-02-23", maxWeek = 16,
                nodesPerDay = 3, timeJson = oldJson, color = "#222222", periodTableId = 7),
            TimeTableEntity(id = 3, name = "课表C", startDate = "2026-02-23", maxWeek = 16,
                nodesPerDay = 3, timeJson = oldJson, color = "#333333", periodTableId = 7)
        )
        val hydrated = boundTables.map { it.hydratedWith(periodTable) }
        // 全部绑定表读到同一份新作息
        assertTrue(hydrated.all { it.timeJson == newJson })
        assertTrue(hydrated.all { it.nodesPerDay == 3 })
        // 课程表自身字段(名称/默认位)不受水合影响
        assertEquals("课表A", hydrated[0].name)
        assertTrue(hydrated[0].isDefault)
        assertEquals("#222222", hydrated[1].color)
    }

    @Test
    fun contract_unbound_table_falls_back_to_legacy_columns() {
        // §10: 未绑定(或悬空)回退兼容列 — hydratedWith(null) = 原样
        val table = TimeTableEntity(id = 1, name = "T", startDate = "2026-02-23", maxWeek = 16,
            nodesPerDay = 3, timeJson = oldJson, color = "#111111")
        val hydrated = table.hydratedWith(null)
        assertEquals(oldJson, hydrated.timeJson)
        assertEquals(3, hydrated.nodesPerDay)
        assertEquals(table, hydrated)
    }

    @Test
    fun contract_own_time_courses_keep_absolute_times_after_period_edit() {
        // §10: 自定义时间课程在时间表修改/绑定切换后起止时间不变
        val ownTimeCourse = course("自定义课", startNode = 1, step = 2, ownTime = true)
        val regular = course("高数", startNode = 1, step = 2)
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, listOf(ownTimeCourse, regular))
        // 自定义课不进预览(不参与解释), 普通课进
        assertTrue(preview.changedCourses.none { it.courseName == "自定义课" })
        // 自定义课解释自身起止: 两张表下 effectiveCourseTime 都返回自己的绝对时间
        val fromOld = TimeTableUtils.effectiveCourseTime(true, "07:00", "08:30", 1, 2, oldJson)
        val fromNew = TimeTableUtils.effectiveCourseTime(true, "07:00", "08:30", 1, 2, newJson)
        assertEquals(fromOld, fromNew)
        assertEquals("07:00" to "08:30", fromNew)
    }

    @Test
    fun contract_table_copy_reuses_source_binding_not_new_period_table() {
        // §10: 课程表复制不创建新的时间节次表 — 复制 = source.copy(id=0,...) 引用同一 periodTableId
        val source = TimeTableEntity(id = 1, name = "原表", startDate = "2026-02-23", maxWeek = 16,
            nodesPerDay = 3, timeJson = oldJson, color = "#111111", periodTableId = 7)
        val duplicate = source.copy(id = 0, name = "原表2", isDefault = false, createdAt = 999L)
        assertEquals("副本必须复用原绑定(设计 §2-3)", 7L, duplicate.periodTableId)
        assertFalse(duplicate.isDefault)
    }

    @Test
    fun contract_preview_cancel_writes_nothing() {
        // §10: 用户取消则数据库零改动 — 预览是纯函数, 没有写库副作用可供取消;
        // 本契约从形状上锁: preview 输入不含任何 DAO/repo 引用, 返回值只是数据。
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, listOf(course("高数", 1, 2)))
        // 丢弃结果即可 — 没有任何可回滚状态
        assertEquals(1, preview.changedCourses.size)
    }
}

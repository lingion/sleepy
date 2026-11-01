package com.lingion.sleepy.console.handlers

import com.lingion.sleepy.console.MockRuleStore
import com.lingion.sleepy.util.AppClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8 Step 1（JVM 面）：session/reset 判据纯函数 —
 * 只清 console: 前缀表 + ImportDraft 全清 + 时钟偏移归零 + mock 规则清空
 * + catalog 覆盖层清空; 非前缀表一律不动（brief "全留" 判据的守恒面）。
 */
class SessionResetUnitTest {

    private val tables = listOf(
        SessionHandler.ConsoleTable(1L, "console: 触发测试 A"),
        SessionHandler.ConsoleTable(2L, "console: 触发测试 B"),
        SessionHandler.ConsoleTable(3L, "我的真实课表"),
        SessionHandler.ConsoleTable(4L, "流体云触发测试"),   // DebugTriggers 产物: 非 console: 前缀 → 不动
        SessionHandler.ConsoleTable(5L, "导入的课表"),
    )

    @Test
    fun onlyConsolePrefixedTables_areSelectedForDeletion() {
        val doomed = SessionHandler.planTableDeletions(tables)
        assertEquals(listOf(1L, 2L), doomed.map { it.id })
        // 守恒: 非前缀表绝不进删除计划
        assertTrue(doomed.none { it.name.startsWith("流体") || it.name == "我的真实课表" })
    }

    @Test
    fun reset_zeroesClockOffset_andClearsMockRules() {
        AppClock.offsetBy(5_000L)
        MockRuleStore.put(
            MockRuleStore.MockRule("x", 200, "text/html", emptyMap(), "b".toByteArray())
        )
        assertEquals(5_000L, AppClock.offsetMs)
        assertEquals(1, MockRuleStore.list().size)

        SessionHandler.resetVolatileState()
        assertEquals(0L, AppClock.offsetMs)
        assertTrue(MockRuleStore.list().isEmpty())
    }
}

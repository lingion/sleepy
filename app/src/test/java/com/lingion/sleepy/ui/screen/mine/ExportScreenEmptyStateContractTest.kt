package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用户反馈 bug 契约: 无课表但有作息表时, 导出页不得整页判空。
 *
 * 根因: ExportScreen 空态判定 `table == null && selectedPeriodTable == null`
 * 在 tables 为空时恒真(初始 exportPeriodTableId=null → selectedPeriodTable=null),
 * return@Scaffold 短路整页 — 用户永远没机会打开选择器选中作息表。
 *
 * 契约: 空态必须额外要求 allPeriodTables 也为空; 只有课表与作息表都不存在
 * 才显示「暂无可导出的课表」。有作息表 → 正常渲染, 选择器可选作息表,
 * 原生 + JSON 两条导出路径可达。
 */
class ExportScreenEmptyStateContractTest {
    private val root = File(System.getProperty("sleepy.test.root") ?: ".")
    private fun source(): String =
        File(root, "app/src/main/java/com/lingion/sleepy/ui/screen/mine/ExportScreen.kt").readText()

    @Test
    fun `empty state requires both course tables and period tables to be absent`() {
        val src = source()
        // 修复后的空态判定必须包含 allPeriodTables.isEmpty() —
        // 无课表但有作息表时不再命中空态。
        assertTrue(
            "ExportScreen 空态判定缺 allPeriodTables.isEmpty() — 无课表但有作息表时整页被拦",
            src.contains("table == null && selectedPeriodTable == null && allPeriodTables.isEmpty()")
        )
    }

    @Test
    fun `page body renders when period tables exist without course tables`() {
        val src = source()
        // 顶部信息卡仍以 selectedPeriodTable 优先取名 — 无课表时标题不得是空串。
        assertTrue(
            "顶部卡标题必须 selectedPeriodTable 优先, 否则无课表时显示空名",
            src.contains("selectedPeriodTable?.name ?: table?.name")
        )
        // 作息表两条导出路径(原生 + JSON)仍由 selectedPeriodTable != null 分支承载。
        assertTrue(src.contains("exportPeriodTableShareText(selectedPeriodTable)"))
        assertTrue(src.contains("exportPeriodTableJson(selectedPeriodTable)"))
    }

    @Test
    fun `no stray early return keeps period-only users out of the page`() {
        val src = source()
        // 空态 Box 后紧跟 return@Scaffold 只允许出现一次(空态本身);
        // 防止后续再引入第二个把作息表用户拦在门外的早退。
        assertEquals(1, Regex("return@Scaffold").findAll(src).count())
    }

    private fun assertEquals(expected: Int, actual: Int) {
        assertTrue("expected $expected but was $actual", expected == actual)
    }
}

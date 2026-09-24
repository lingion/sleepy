package com.lingion.sleepy.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-09-23 用户报告三症状契约: 水合读取统一, 编辑页元数据不能丢。
 * 仓库无 Robolectric, 因此按现有契约测试风格锁 UI 源码中的数据通道。
 */
class HydrationConsistencyContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val scheduleScreen by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleScreen.kt").readText()
    }
    private val addCourseScreen by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt").readText()
    }
    private val editTableScreen by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt").readText()
    }
    private val importSheet by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportSheet.kt").readText()
    }

    @Test
    fun schedule_grid_uses_hydrated_table_for_time_slots() {
        val cardsGridCall = Regex(
            "timeSlots\\s*=\\s*TimeTableUtils\\.timeSlotsFor\\(state\\.effectiveCurrentTable\\)"
        )
        assertTrue(
            "网格视图的 timeSlots 必须从 effectiveCurrentTable 水合读取,否则绑定作息表新增节次不渲染",
            cardsGridCall.containsMatchIn(scheduleScreen)
        )
        assertFalse(
            "网格视图不得继续把原始 currentTable 传给 timeSlotsFor",
            Regex("timeSlots\\s*=\\s*TimeTableUtils\\.timeSlotsFor\\(state\\.currentTable\\)")
                .containsMatchIn(scheduleScreen)
        )
    }

    @Test
    fun add_course_time_domain_uses_hydrated_table() {
        assertTrue(
            "加课页的 currentTable 必须是 effectiveCurrentTable,否则 maxStd 停在兼容列旧节次数",
            Regex("val currentTable\\s*=\\s*state\\.effectiveCurrentTable")
                .containsMatchIn(addCourseScreen)
        )
        assertFalse(
            "加课页不得从 state.currentTable 读取节次时间域",
            Regex("val currentTable\\s*=\\s*state\\.currentTable")
                .containsMatchIn(addCourseScreen)
        )
    }

    @Test
    fun edit_table_persists_updated_metadata_in_all_save_routes() {
        assertTrue(
            "共享作息表编辑必须使用原子元数据+作息表双写",
            editTableScreen.contains("viewModel.updateTableMetadataWithPeriodTable(")
        )
        assertTrue(
            "解绑/换绑确认必须使用单事务元数据+绑定复合方法, 避免竞态与快照分裂",
            editTableScreen.contains("viewModel.updateTableMetadataAndBind(updated, null)") &&
                editTableScreen.contains("viewModel.updateTableMetadataAndBind(metadata, targetId, periodContent)")
        )

    }

    @Test
    fun import_time_domain_writes_bound_owner_in_all_existing_table_modes() {
        // P1(2026-09-23 workflow 审计): 绑定共享作息表的课表, 导入延伸节次时
        // 必须写 period_tables(真实 owner), 不得污染兼容列(症状1可经导入复现)。
        val replaceBranch = importSheet.substringAfter("ImportApplyMode.ReplaceCurrent ->")
            .substringBefore("ImportApplyMode.AppendNonConflict ->")
        val appendNonConflictBranch = importSheet.substringAfter("ImportApplyMode.AppendNonConflict ->")
            .substringBefore("ImportApplyMode.AppendAsNew ->")
        val appendAllBranch = importSheet.substringAfter("ImportApplyMode.AppendAll ->")

        assertTrue(
            "ReplaceCurrent(整表替换)绑定态必须经 savePeriodTable 写作息表 owner",
            replaceBranch.contains("repo.savePeriodTable(")
        )
        assertTrue(
            "AppendNonConflict(仅追加不冲突)绑定态必须经 savePeriodTable 写作息表 owner",
            appendNonConflictBranch.contains("repo.savePeriodTable(")
        )
        assertTrue(
            "AppendAll(全部追加)绑定态必须经 savePeriodTable 写作息表 owner",
            appendAllBranch.contains("repo.savePeriodTable(")
        )
        assertTrue(
            "三个写时间域分支都必须先探查绑定关系(getPeriodTable)",
            replaceBranch.contains("repo.getPeriodTable(it)") &&
                appendNonConflictBranch.contains("repo.getPeriodTable(it)") &&
                appendAllBranch.contains("repo.getPeriodTable(it)")
        )
    }
}

package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 返回恢复精确页面状态契约 — 用户报障:
 *   ① 通用设置 → 二级页(节假日/小组件管理) → 返回: 通用设置滚动位置归零、折叠卡全收起;
 *   ② 教务直连导入: 学校列表翻到一半 → 选校进 WebView → 返回: 列表从头开始、搜索词丢失;
 *   ③ 底栏 tab(课表/今日/管理/我的)往返同样丢滚动位置。
 *
 * 根因: MainActivity.AppRoot 与 JwImportActivity 的 stage 机都是条件组合
 * (if (topOverlay()==X) {...; return}), 被覆盖页整体离开组合树,
 * remember/rememberSaveable 状态销毁 — 且未用 SaveableStateHolder 提供独立
 * 保存作用域, 回来时滚动位置/展开态/输入全部按初始值重建。
 *
 * 修复契约(结构锁定; 先例: ScheduleViewModeSessionContractTest —
 * 仓库无 Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *   1. AppRoot 必须 rememberSaveableStateHolder() 并对每个 overlay 分支包
 *      SaveableStateProvider(稳定 key) — 栈页状态(滚动/折叠展开/输入)跨覆盖往返保真;
 *   1b. **AddCourse 例外**: 编辑/新增课程会话**不纳入** SaveableStateProvider —
 *      基线 §1.3 明确编辑课程会话在旋转/进程恢复时安全丢弃(防恢复成空表单重复加课),
 *      必须有注释锚定此例外及理由;
 *   2. 4 个 tab 的内容切换处同样包 SaveableStateProvider(currentTab.name);
 *   3. GeneralSettingsScreen 的 expandedSections 必须 rememberSaveable(折叠展开态跨返回存活);
 *   4. JwImportActivity 的 stage 条件组合必须含 SaveableStateProvider(key=stage 类名);
 *   5. SchoolSelectScreen 的 query 必须 rememberSaveable(搜索词跨 WebView 往返存活)。
 */
class BackRestoreSaveableContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
            java.io.File("/Users/lingion_k/sleepy-worktrees/back-restore/app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val mainSource: String by lazy { loadSource("MainActivity.kt") }
    private val generalSource: String by lazy { loadSource("ui/screen/mine/GeneralSettingsScreen.kt") }
    private val jwImportSource: String by lazy { loadSource("ui/screen/imports/JwImportActivity.kt") }
    private val schoolSelectSource: String by lazy { loadSource("ui/screen/imports/SchoolSelectScreen.kt") }

    /** 契约 1: AppRoot 必须引入 SaveableStateHolder 并用它包裹 overlay 分支内容 */
    @Test
    fun appRoot_uses_SaveableStateProvider_for_overlay_branches() {
        assertTrue(
            "AppRoot must obtain a SaveableStateHolder via rememberSaveableStateHolder()",
            Regex("""rememberSaveableStateHolder\(\)""").containsMatchIn(mainSource)
        )
        // overlay 分支内容必须经 SaveableStateProvider 包裹(至少覆盖 General/Holiday/WidgetManagement
        // 三个报障页; 用 onBack = { popOverlay() } 段定位各分支体)
        val branchKeys = listOf("General", "Holiday", "WidgetManagement")
        branchKeys.forEach { key ->
            assertTrue(
                "Overlay branch '$key' content must be wrapped in SaveableStateProvider(\"$key\")",
                Regex("""SaveableStateProvider\(\s*"$key"\s*\)""").containsMatchIn(mainSource)
            )
        }
    }

    /** 契约 1b: AddCourse 分支必须留在 SaveableStateProvider 之外, 且注释锚定例外理由 */
    @Test
    fun appRoot_AddCourse_branch_is_deliberately_outside_SaveableStateProvider() {
        // 找 AddCourse 分支(条件行起点 → 下一个 overlay 分支之前), 其分支体内不得出现 SaveableStateProvider
        val branchStart = mainSource.indexOf("if (topOverlay() == OverlayScreen.AddCourse")
        assertTrue("AddCourse branch not found in AppRoot conditional composition", branchStart >= 0)
        val nextBranch = mainSource.indexOf("if (topOverlay() == OverlayScreen.AllTables", branchStart)
        assertTrue("Cannot delimit AddCourse branch (next branch marker missing)", nextBranch > branchStart)
        val addCourseBranch = mainSource.substring(branchStart, nextBranch)
        assertFalse(
            "AddCourse (edit-course session) must NOT be wrapped in SaveableStateProvider — " +
                "edit sessions are safely discarded on rotation/process restore (baseline §1.3, " +
                "prevents restoring an empty form and duplicate course creation)",
            addCourseBranch.contains("SaveableStateProvider")
        )
        // 例外必须有注释锚定(防后人"顺手补全"把编辑会话也纳入恢复)
        assertTrue(
            "AddCourse exception must be documented with a comment explaining why (baseline §1.3)",
            mainSource.contains("§1.3") || mainSource.contains("重复加课")
        )
    }

    /** 契约 2: 4 个 tab 的内容切换处必须包 SaveableStateProvider(currentTab.name) */
    @Test
    fun mainTabs_wraps_each_tab_content_in_SaveableStateProvider() {
        val tabs = listOf("Schedule", "Today", "Manage", "Mine")
        tabs.forEach { tab ->
            assertTrue(
                "Tab '$tab' content must be wrapped in SaveableStateProvider(currentTab.name)",
                Regex("""SaveableStateProvider\(\s*currentTab\.name\s*\)""").containsMatchIn(mainSource)
            )
        }
    }

    /** 契约 3: GeneralSettingsScreen 的折叠展开态必须 rememberSaveable */
    @Test
    fun generalSettingsScreen_expandedSections_uses_rememberSaveable() {
        assertTrue(
            "GeneralSettingsScreen must import androidx.compose.runtime.saveable.rememberSaveable",
            generalSource.contains("androidx.compose.runtime.saveable.rememberSaveable")
        )
        assertFalse(
            "expandedSections must not be plain remember — collapse state must survive overlay round-trip",
            Regex("""var\s+expandedSections\s+by\s+remember\s*\{""").containsMatchIn(generalSource)
        )
        assertTrue(
            "expandedSections must be declared via rememberSaveable",
            Regex("""var\s+expandedSections\s+by\s+rememberSaveable""").containsMatchIn(generalSource)
        )
    }

    /** 契约 4: JwImportActivity 的 stage 条件组合必须含 SaveableStateProvider(key=stage 类名) */
    @Test
    fun jwImportActivity_stage_branches_use_SaveableStateProvider() {
        assertTrue(
            "JwImportActivity must obtain a SaveableStateHolder via rememberSaveableStateHolder()",
            Regex("""rememberSaveableStateHolder\(\)""").containsMatchIn(jwImportSource)
        )
        // key 用 stage 类名(SelectSchool / WebViewLogin); ConfigureConfirm 是 Dialog 无需滚动恢复但同样包, 统一作用域
        listOf("SelectSchool", "WebViewLogin", "ConfigureConfirm").forEach { stage ->
            assertTrue(
                "Stage '$stage' branch must be wrapped in SaveableStateProvider keyed by stage class name",
                Regex("""SaveableStateProvider\(\s*[^)]*"$stage"|SaveableStateProvider\(\s*stage::class""").containsMatchIn(jwImportSource)
            )
        }
    }

    /** 契约 5: SchoolSelectScreen 的搜索词必须 rememberSaveable */
    @Test
    fun schoolSelectScreen_query_uses_rememberSaveable() {
        assertFalse(
            "SchoolSelectScreen query must not be plain remember — search term must survive WebView round-trip",
            Regex("""var\s+query\s+by\s+remember\s*\{""").containsMatchIn(schoolSelectSource)
        )
        assertTrue(
            "SchoolSelectScreen query must be declared via rememberSaveable",
            Regex("""var\s+query\s+by\s+rememberSaveable""").containsMatchIn(schoolSelectSource)
        )
    }

    /** 不得退化: 修复不得把编辑课程会话例外破坏掉(AppRoot 的栈 saver 语义保留) */
    @Test
    fun appRoot_keeps_overlayStack_saver_editing_course_exception() {
        assertTrue(
            "overlayStack rememberSaveable with editingCourse==null guard must remain (baseline §1.3)",
            Regex("""if\s*\(editingCourse\s*==\s*null\)\s*stack\s+else\s+emptyList\(\)""").containsMatchIn(mainSource)
        )
    }
}

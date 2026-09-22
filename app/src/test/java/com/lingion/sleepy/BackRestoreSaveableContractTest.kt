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
 *   1. 每个 overlay 屏幕必须是 NavHost 的 composable(Routes.X) 目的地 —
 *      NavHost 为每个返回栈条目持有独立 SaveableState, 被覆盖时保存、弹回时恢复,
 *      滚动位置/折叠展开/输入跨往返保真(ref #45 迁移前由 AppRoot 手工
 *      SaveableStateProvider(overlay.name) 承担, 迁移后归 NavHost, 契约不变);
 *   1b. **AddCourse 例外**: 编辑/新增课程会话**不纳入**任何保存作用域 —
 *      基线 §1.3 明确编辑课程会话在旋转/进程恢复时安全丢弃(防恢复成空表单重复加课),
 *      必须有恢复守卫锚定此例外;
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
    private val navSource: String by lazy { loadSource("ui/nav/SleepyNavHost.kt") }
    private val routesSource: String by lazy { loadSource("ui/nav/SleepyRoutes.kt") }
    private val navigatorSource: String by lazy { loadSource("ui/nav/SleepyNavigator.kt") }
    private val generalSource: String by lazy { loadSource("ui/screen/mine/GeneralSettingsScreen.kt") }
    private val jwImportSource: String by lazy { loadSource("ui/screen/imports/JwImportActivity.kt") }
    private val schoolSelectSource: String by lazy { loadSource("ui/screen/imports/SchoolSelectScreen.kt") }

    /**
     * 契约 1: Routes 里除 main 之外每个路由常量都必须在 SleepyNavHost 注册为
     * composable(Routes.X) 目的地。NavHost 给每个返回栈条目一套独立 SaveableState,
     * 被覆盖时保存、弹回时恢复 — 这就是"返回后滚动/折叠/输入还在"的机制本体
     * (ref #45 迁移前由 AppRoot 手工 SaveableStateProvider(overlay.name) 承担, 迁移后归 NavHost)。
     * 自维护不变量: 新增路由常量却漏注册 = 那屏一进去就丢状态, 本测试直接红。
     */
    @Test
    fun every_overlay_route_is_registered_as_a_navhost_destination() {
        // 官方 nav3 版: SleepyRoute sealed 的每个子类(除 Main)都必须有 entry<SleepyRoute.X> 注册。
        // NavDisplay 给每个返回栈条目一套独立 SaveableState(经 SaveableStateHolderNavEntryDecorator),
        // 被覆盖时保存、弹回时恢复 — 机制本体不变,只是锚点从 composable() 换成 entry<>()。
        val routes = Regex("""@Serializable\s+(?:data\s+)?(?:object|class)\s+(\w+)""")
            .findAll(routesSource)
            .map { it.groupValues[1] }
            .filter { it != "Main" }
            .toList()
        assertTrue("SleepyRoute 表未解析到任何 overlay 路由", routes.isNotEmpty())
        routes.forEach { name ->
            assertTrue(
                "路由 SleepyRoute.$name 必须在 SleepyNavHost 注册 entry<SleepyRoute.$name> — " +
                    "漏注册 = 该屏没有独立保存作用域, 返回即丢状态",
                Regex("""entry<SleepyRoute\.$name>""").containsMatchIn(navSource)
            )
        }
    }

    /**
     * 契约 1b: AddCourse 目的地刻意不在任何 SaveableStateProvider 内。
     * 基线 §1.3: 编辑课程会话在旋转/进程恢复时安全丢弃 — 若纳入保存作用域,
     * 进程重建会恢复出 editingCourse=null 的空表单, 用户以为在编辑却实际新增 → 重复加课。
     */
    @Test
    fun addCourse_route_is_deliberately_outside_any_saveable_scope() {
        val block = balancedBlock(navSource, "entry<SleepyRoute.AddCourse>")
        assertTrue("SleepyNavHost AddCourse 目的地不存在", block.isNotEmpty())
        assertFalse(
            "AddCourse 禁止 SaveableStateProvider(基线 §1.3 编辑会话可丢弃例外, 防恢复空表单重复加课)",
            Regex("""SaveableStateProvider\(""").containsMatchIn(block)
        )
        assertTrue(
            "AddCourse 例外必须留注释锚定理由(防后人\"顺手补全\"把编辑会话纳入恢复)",
            block.contains("§1.3") || block.contains("重复加课")
        )
    }

    /** 契约 1b-2: 编辑课程会话必须是纯内存态(禁 rememberSaveable), 否则恢复出空表单重复加课 */
    @Test
    fun editing_course_session_is_not_persisted() {
        val session = Regex("""class NavSession[\s\S]*?\n\}""").find(navigatorSource)?.value.orEmpty()
        assertTrue("NavSession 不存在", session.isNotEmpty())
        assertTrue(
            "editingCourse 必须是普通 mutableStateOf(进程恢复即丢弃)",
            Regex("""var editingCourse\b[\s\S]{0,80}?by\s+mutableStateOf""").containsMatchIn(session)
        )
        assertFalse(
            "editingCourse 禁止 rememberSaveable(基线 §1.3)",
            Regex("""editingCourse[\s\S]{0,120}?rememberSaveable""").containsMatchIn(session)
        )
    }

    /** 契约 1b-3: 恢复守卫 — 进程恢复后 editing=true 但会话为空时必须弹掉 AddCourse */
    @Test
    fun restore_guard_pops_add_course_when_session_is_gone() {
        val guard = balancedBlock(
            navSource,
            "LaunchedEffect(currentRoute, deepLinkCourse?.id)",
        )
        assertTrue("AddCourse 恢复守卫不存在", guard.isNotEmpty())
        assertTrue(
            "守卫必须在 editing=true 且会话为空时弹掉 add_course",
            Regex("""currentRoute\?\.editing == true\s*&&\s*session\.editingCourse == null\s*&&\s*deepLinkCourse == null""").containsMatchIn(guard)
        )
        // currentRoute 的类型判定锚定 AddCourse(上方 val currentRoute = ... as? SleepyRoute.AddCourse)
        val typedAnchor = Regex("""currentRoute\s*=\s*navigator\.backStack\.lastOrNull\(\)\s*as\?\s*SleepyRoute\.AddCourse""")
            .containsMatchIn(navSource)
        assertTrue("守卫必须针对 SleepyRoute.AddCourse(currentRoute 的 as? 判型)", typedAnchor)
        assertTrue("守卫必须调用 navigator.pop", guard.contains("navigator.pop()"))
    }

    /** 从 anchor 起做花括号配平取完整代码块(比非贪婪正则可靠) */
    private fun balancedBlock(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        if (start < 0) return ""
        val open = source.indexOf('{', start)
        if (open < 0) return ""
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
        }
        return ""
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

    /** 契约 6: SchoolRow 校名与第二行「协议 · 网址」首端对齐 — 徽章与间距必须同增同减 */
    @Test
    fun schoolRow_badge_and_spacer_share_fate() {
        // 根因: 徽章 supported 不渲染但 Spacer(6.dp) 无条件画 → 无徽章时校名比
        // 第二行「协议 · 网址」凭空右移 6dp, 两行首端错位 (2026-09-13 用户报障)。
        // 锁法: SchoolRow 体内 SchoolStatusBadge 与 Spacer 必须 1:1 同现 (徽章包裹在
        // status != SUPPORTED 条件内)。禁止无条件 Spacer 回归。
        val rowStart = schoolSelectSource.indexOf("fun SchoolRow(")
        assertTrue("SchoolRow composable not found", rowStart >= 0)
        val rowEnd = schoolSelectSource.indexOf("private fun SchoolStatusBadge", rowStart)
        assertTrue("Cannot delimit SchoolRow body", rowEnd > rowStart)
        val rowBody = schoolSelectSource.substring(rowStart, rowEnd)
        val badgeCount = Regex("""SchoolStatusBadge\(school = school\)""").findAll(rowBody).count()
        val spacerCount = Regex("""Spacer\(modifier = Modifier\.size\(6\.dp\)\)""").findAll(rowBody).count()
        assertTrue(
            "SchoolRow 内 SchoolStatusBadge 与 6dp Spacer 必须成对出现 (同增同减, 首端对齐): badge=$badgeCount spacer=$spacerCount",
            badgeCount == spacerCount && badgeCount >= 1,
        )
        // 徽章调用必须包在 status 条件分支内(禁止无条件调用回归)
        assertTrue(
            "SchoolRow 的徽章+Spacer 必须包在 status != SUPPORTED 条件内",
            Regex("""if\s*\(\s*school\.status\s*!=\s*JwSchoolInfo\.STATUS_SUPPORTED\s*\)\s*\{[^}]*SchoolStatusBadge""").containsMatchIn(rowBody)
        )
    }

}

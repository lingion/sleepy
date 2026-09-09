package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表视图模式(周视图/网格卡片)会话级存活契约 — 用户反馈: 在网格视图编辑课程,
 * 保存/返回后被弹回周视图(启动默认), 每次都要手动切回网格。
 *
 * 根因: viewMode 原是 ScheduleScreen 内部 remember { AppPrefs.getStartView(...) }
 * 的组合局部态 — AddCourse/EditCourse overlay 打开时 AppRoot 提前 return
 * (MainActivity), 整个 ScheduleScreen 离开组合树, remember 状态销毁; 返回后
 * 按启动默认重建。切 tab(Today/Manage/Mine)经 MainTabs 的 when 同样整页移除。
 *
 * 修复契约(结构锁定; 先例: JwQzAppWebViewContractTest / WidgetInfoXmlContractTest —
 * 仓库无 Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *   1. ScheduleScreen 禁止再从 AppPrefs.getStartView 初始化 viewMode
 *      (防回归成组合局部态自取默认 → overlay 往返即丢);
 *   2. viewMode / onViewModeChange 必须以参数注入(状态提升);
 *   3. ViewMode 枚举必须对 MainActivity 可见(禁 private — 跨文件注入需要具名类型);
 *   4. MainActivity(AppRoot = 会话层, 与 currentTab/overlayStack 同级) 持有唯一
 *      真相源: remember 初始化读启动默认, 经 MainTabs 两个调用位(贴底 Scaffold /
 *      Dock)双路注入 ScheduleScreen。
 * 不落盘契约: 会话内只读 KEY_START_VIEW, 禁写 putStartView
 * (AppPrefs.kt:58 — 启动默认与会话手动切换分离是有意设计, 手动切换不入 prefs)。
 */
class ScheduleViewModeSessionContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
            java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val screenSource: String by lazy {
        loadSource("ui/screen/schedule/ScheduleScreen.kt")
    }
    private val mainSource: String by lazy {
        loadSource("MainActivity.kt")
    }

    /** 契约 1: ScheduleScreen 不得自带 AppPrefs 启动默认 — 状态提升后组合局部态自取默认即回归本 bug */
    @Test
    fun scheduleScreen_does_not_initialize_viewMode_from_AppPrefs() {
        assertFalse(
            "ScheduleScreen must not read AppPrefs.getStartView; viewMode is injected from the session layer",
            screenSource.contains("AppPrefs.getStartView")
        )
    }

    /** 契约 2: viewMode 与回调必须走参数注入 */
    @Test
    fun scheduleScreen_receives_viewMode_via_injected_parameters() {
        assertTrue(
            "ScheduleScreen signature must declare 'viewMode: ViewMode' parameter",
            Regex("""viewMode\s*:\s*ViewMode""").containsMatchIn(screenSource)
        )
        assertTrue(
            "ScheduleScreen signature must declare 'onViewModeChange: (ViewMode) -> Unit' parameter",
            Regex("""onViewModeChange\s*:\s*\(ViewMode\)\s*->\s*Unit""").containsMatchIn(screenSource)
        )
    }

    /** 契约 2b: 内部切换必须只经回调写, 禁再出现局部 var viewMode 重组内自持 */
    @Test
    fun scheduleScreen_does_not_own_local_viewMode_state() {
        assertFalse(
            "ScheduleScreen must not declare 'var viewMode by remember' — state ownership belongs to AppRoot",
            Regex("""var\s+viewMode\s+by\s+remember""").containsMatchIn(screenSource)
        )
    }

    /** 契约 3: ViewMode 枚举必须跨文件可见(MainActivity 注入需要具名类型) */
    @Test
    fun viewMode_enum_is_visible_outside_ScheduleScreen_file() {
        assertFalse(
            "ViewMode enum must not be 'private' (file-private) — MainActivity injects it",
            Regex("""private\s+enum\s+class\s+ViewMode""").containsMatchIn(screenSource)
        )
    }

    /** 契约 4: 会话层持有唯一真相源, 初始化仍读启动默认 */
    @Test
    fun mainActivity_AppRoot_holds_session_level_viewMode_state() {
        assertTrue(
            "AppRoot must hoist session-level 'scheduleViewMode' via remember",
            Regex("""var\s+scheduleViewMode\s+by\s+remember""").containsMatchIn(mainSource)
        )
        assertTrue(
            "Hoisted state initializer must still read the launch default (AppPrefs.getStartView)",
            mainSource.contains("AppPrefs.getStartView")
        )
    }

    /** 契约 4b: MainTabs 两个调用位(贴底 Scaffold / Dock)都必须把状态注入下去 */
    @Test
    fun mainActivity_passes_viewMode_through_every_MainTabs_call_site() {
        val callSites = Regex("""MainTabs\(""").findAll(mainSource).toList()
        assertTrue("MainTabs call sites not found in MainActivity", callSites.size >= 2)
        callSites.forEach { match ->
            // 取该调用括号到闭合前的一段(下一个 "}" 前的参数区足够覆盖命名实参)
            val tail = mainSource.substring(match.range.first)
            val params = tail.substringAfter("MainTabs(").substringBefore(")")
            assertTrue(
                "Every MainTabs call site must pass viewMode = scheduleViewMode",
                Regex("""viewMode\s*=\s*scheduleViewMode""").containsMatchIn(params)
            )
            assertTrue(
                "Every MainTabs call site must pass onViewModeChange writing back to scheduleViewMode",
                Regex("""onViewModeChange\s*=\s*\{\s*scheduleViewMode\s*=\s*it\s*\}""").containsMatchIn(params)
            )
        }
    }

    /** 契约 4c: ScheduleScreen 调用位必须接到注入参数 */
    @Test
    fun mainActivity_scheduleTab_wires_injected_state_into_ScheduleScreen() {
        val callIdx = mainSource.indexOf("ScheduleScreen(")
        assertTrue("ScheduleScreen call site not found in MainActivity", callIdx >= 0)
        val params = mainSource.substring(callIdx).substringBefore(")")
        assertTrue(
            "ScheduleScreen call must forward viewMode",
            Regex("""viewMode\s*=\s*viewMode""").containsMatchIn(params)
        )
        assertTrue(
            "ScheduleScreen call must forward onViewModeChange",
            Regex("""onViewModeChange\s*=\s*onViewModeChange""").containsMatchIn(params)
        )
    }

    /** 不落盘契约: 修复不得把会话手动切换写进 AppPrefs */
    @Test
    fun session_switch_never_persists_to_AppPrefs() {
        val screenTouchesPrefsWrite =
            Regex("""AppPrefs\.putStartView""").containsMatchIn(screenSource) ||
                Regex("""AppPrefs\.putStartView""").containsMatchIn(mainSource)
        assertFalse(
            "Manual view-mode switch is session-level only; AppPrefs.putStartView must not be called (launch-default stays separate)",
            screenTouchesPrefsWrite
        )
    }
}

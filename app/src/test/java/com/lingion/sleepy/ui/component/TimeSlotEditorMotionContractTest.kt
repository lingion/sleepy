package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时段编辑器动效契约 (MD3E motion 全量补齐 Task 5, 2026-10-29):
 *   1. 禁原始 spring()/tween() — 拖拽 settle/非法落点弹回/边界冻结全部改走
 *      motionScheme 规格: settle+取消走 fastSpatialSpec (回弹 = expressive 落定性格),
 *      非法落点弹回与边界冻结走 defaultSpatialSpec (收稳, 不弹)。
 *   2. 行容器重排缓动 — rows 宿主 Column 挂 animateContentSize(defaultSpatialSpec):
 *      删行/末尾增行导致的高度变化平滑过渡 (行在普通 Column 里, 无 lazy 列表,
 *      animateItem() 不适用 — 计划宣告的 fallback 路径)。
 *   3. 行入场 AnimatedVisibility — remember { MutableTransitionState(false).apply
 *      { targetState = true } } 官方「普通列表新项入场」配方: 位置复用的旧行不重播,
 *      新增行只播一次; enter/exit 用 sleepyExpandSpecs 展开家族 (T1 交付)。
 *   4. 拖拽态视觉互换全部缓动 — containerColor(dropInvalid errorContainer /
 *      isDragging primaryContainer / isBlank secondaryContainer)经 animateColorAsState
 *      (defaultEffectsSpec), 高度 0↔6dp 经 animateDpAsState (defaultSpatial)。
 *   5. 不许破的既有接线 — LaunchedEffect(rows) 的 draggingIndex==null 竞态守卫
 *      (2026-10-06 实测 race 修法)、pointerInput(index, reorderEnabled) 重启键、
 *      行语义 stateDescription/onLongClick。
 *
 * 载体形态: ManualTimeSlotEditor 是 private, 公开 TimeSlotEditor 走到手动行宿主
 * 需穿过节次绑定 tab 状态机, Robolectric harness 需要整套测试替身 — 按 T3/T4
 * 定型先例 (MainTabsTransitionContractTest / FloatLayerMotionContractTest),
 * 声明式接线用源码结构锁定, 读源头文件等价于读编译产物。
 */
class TimeSlotEditorMotionContractTest {

    private fun loadSource(fileName: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/ui/component/"),
            java.io.File("src/main/java/com/lingion/sleepy/ui/component/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            java.io.File(root, fileName).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $fileName")

    private val editorSource: String by lazy { loadSource("TimeSlotEditor.kt") }

    // ---- 契约 1: 禁原始规格 ----

    @Test
    fun editor_has_no_raw_tween_or_spring_specs() {
        assertFalse("TimeSlotEditor.kt 不得写原始 tween() — 用 motionScheme 家族",
            Regex("""\btween\(""").containsMatchIn(editorSource))
        assertFalse("TimeSlotEditor.kt 不得写原始 spring() — settle 归 fastSpatialSpec",
            Regex("""\bspring\(""").containsMatchIn(editorSource))
    }

    @Test
    fun drag_settles_and_returns_use_motion_scheme_specs() {
        assertTrue("settle/取消归位必须用 fastSpatialSpec",
            editorSource.contains("fastSpatialSpec"))
        assertTrue("非法落点弹回/边界冻结必须用 defaultSpatialSpec",
            editorSource.contains("defaultSpatialSpec"))
        // 四个原裸 spring() 站点逐一锁规格 (精确文本 = 最强断言):
        // 取消归位 + 成功落定两处 settle 走 fastSpatialSpec;
        // 非法落点弹回 + 边界冻结两处 return 走 defaultSpatialSpec。
        assertEquals("拖拽 settle 两处 animateTo(0f, settleSpec)", 2,
            Regex("""animateTo\(\s*0f,\s*settleSpec\)""").findAll(editorSource).count())
        assertEquals("非法落点弹回一处 animateTo(0f, returnSpec)", 1,
            Regex("""animateTo\(\s*0f,\s*returnSpec\)""").findAll(editorSource).count())
        assertTrue("边界冻结必须带 returnSpec",
            Regex("""animateTo\(\s*\(to - from\) \* rowPitch,\s*returnSpec""")
                .containsMatchIn(editorSource))
    }

    // ---- 契约 2: 宿主重排缓动 ----

    @Test
    fun row_host_reflows_with_animate_content_size() {
        assertTrue("行容器必须挂 animateContentSize 平滑增删行的高度变化",
            Regex("""animateContentSize\(""").containsMatchIn(editorSource))
    }

    // ---- 契约 3: 行入场 AnimatedVisibility + 展开家族 ----

    @Test
    fun rows_enter_via_animated_visibility_expand_family() {
        assertTrue("行必须经 AnimatedVisibility 入场",
            Regex("""AnimatedVisibility\(""").containsMatchIn(editorSource))
        assertTrue("入场规格必须复用 sleepyExpandSpecs 家族 (不得手抄)",
            editorSource.contains("sleepyExpandSpecs("))
        assertTrue("新行入场必须用 MutableTransitionState 配方",
            Regex("""MutableTransitionState\(false\)""").containsMatchIn(editorSource))
    }

    // ---- 契约 4: 拖拽态视觉互换缓动 ----

    @Test
    fun drag_state_swaps_are_animated() {
        assertTrue("容器色(拖拽/非法/空白)必须 animateColorAsState 缓动",
            Regex("""animateColorAsState\(""").containsMatchIn(editorSource))
        assertTrue("高度 0↔6dp 必须 animateDpAsState 缓动",
            Regex("""animateDpAsState\(""").containsMatchIn(editorSource))
        assertTrue("色彩缓动规格必须来自 motionScheme defaultEffects",
            editorSource.contains("defaultEffectsSpec"))
    }

    // ---- 契约 5: 既有接线不许破 ----

    @Test
    fun drag_race_guard_and_restart_keys_survive() {
        assertTrue("LaunchedEffect(rows) 的 draggingIndex==null 竞态守卫不许破",
            Regex("""LaunchedEffect\(rows\)\s*\{\s*if \(draggingIndex == null\)""")
                .containsMatchIn(editorSource))
        assertTrue("pointerInput 重启键 (index, reorderEnabled) 不许变",
            Regex("""pointerInput\(index,\s*reorderEnabled\)""").containsMatchIn(editorSource))
        assertTrue("行语义 stateDescription 接线不许破",
            editorSource.contains("stateDescription = stateDesc"))
        assertTrue("行语义 onLongClick 无障碍入口不许破",
            Regex("""onLongClick\(label""").containsMatchIn(editorSource))
    }
}

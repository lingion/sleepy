package com.lingion.sleepy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主页面 tab 切换过渡契约 (MD3E motion 全量补齐 Task 3, 2026-10-29):
 *   1. MainTabs 的 when(currentTab) 组合必须包在 AnimatedContent(targetState = currentTab) 里 —
 *      tab 内容切换从"瞬移"变为 MD3E content 家族过渡 (scaleIn 0.92 + fadeIn / scaleOut →0.92 + fadeOut);
 *   2. 规格只准来自 SleepyMotion 工厂 sleepyContentEnter()/sleepyContentExit() —
 *      且必须在可组合体内先算好再捕获进 transitionSpec lambda (该 lambda 非可组合,
 *      直接在里面调 @Composable 工厂编不过, NavHost 迁移时已踩过);
 *   3. ContentTransform 必须显式 SizeTransform(clip = false) — 四个 tab 页尺寸差异大,
 *      togetherWith 默认 clip=true 会在过渡中裁切内容;
 *   4. 禁原始 tween()/spring() 出现在 MainActivity (全走 motionScheme 家族);
 *   5. 回归锚: SaveableStateProvider(currentTab.name) 接线形状不变
 *      (与 BackRestoreSaveableContractTest 契约 2 同源 — AnimatedContent 过渡期双组合
 *      正是该保态语义的用武之地, key 不得改成 targetState 以外的东西)。
 *
 * 载体形态说明: 仓库 compose 管线测试 (IntRangeSliderGhostPipelineTest 型) 无法faithful
 * 托管 MainTabs — 它要求真 ScheduleViewModel(SleepyApp.get().repository) + navigator.session,
 * rememberSaveable 探针又必须注入四个真实 tab 屏内部 (需测试替身, 越界)。本文件按
 * BackRestoreSaveableContractTest 先例做源码结构锁定: 声明式接线读源头文件等价于读编译产物。
 */
class MainTabsTransitionContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val mainSource: String by lazy { loadSource("MainActivity.kt") }

    /** 契约 1: tab 内容切换必须经 AnimatedContent(targetState = currentTab)。 */
    @Test
    fun mainTabs_animates_content_switching_via_AnimatedContent_on_currentTab() {
        assertTrue(
            "MainTabs 的 tab 切换必须包在 AnimatedContent(targetState = currentTab) 中",
            Regex("""AnimatedContent\(\s*targetState\s*=\s*currentTab""").containsMatchIn(mainSource)
        )
    }

    /** 契约 2: 过渡规格来自 SleepyMotion content 工厂, 且在可组合体内捕获。 */
    @Test
    fun mainTabs_uses_sleepyContent_factories_captured_before_the_spec_lambda() {
        assertTrue(
            "必须使用 sleepyContentEnter() 工厂 (不得手抄规格)",
            Regex("""=\s*sleepyContentEnter\(\)""").containsMatchIn(mainSource)
        )
        assertTrue(
            "必须使用 sleepyContentExit() 工厂 (与 enter 成对)",
            Regex("""=\s*sleepyContentExit\(\)""").containsMatchIn(mainSource)
        )
        // enter/exit 的工厂调用必须出现在 transitionSpec = { 之前 (捕获模式的机械判据):
        val firstFactoryUse = minOf(
            mainSource.indexOf("sleepyContentEnter()"),
            mainSource.indexOf("sleepyContentExit()"),
        )
        val specLambda = mainSource.indexOf("transitionSpec = {")
        assertTrue(
            "工厂结果必须在可组合体内先算、再捕获进 transitionSpec lambda (lambda 非可组合)",
            firstFactoryUse in 0 until specLambda
        )
    }

    /** 契约 3: ContentTransform 显式 SizeTransform(clip = false)。 */
    @Test
    fun mainTabs_content_transform_disables_size_clipping_for_oversized_pages() {
        assertTrue(
            "四个 tab 页尺寸差异大, 过渡必须 SizeTransform(clip = false)",
            Regex("""SizeTransform\(\s*clip\s*=\s*false""").containsMatchIn(mainSource)
        )
        assertTrue(
            "过渡必须走 ContentTransform 显式构造 (togetherWith 默认 clip=true)",
            Regex("""ContentTransform\(""").containsMatchIn(mainSource)
        )
    }

    /** 契约 4: MainActivity 禁止原始 tween()/spring() — 规格全走 motionScheme 家族。 */
    @Test
    fun main_tabs_host_has_no_raw_tween_or_spring_specs() {
        assertFalse(
            "tab 过渡不得写原始 tween() — 用 SleepyContent 家族",
            Regex("""\btween\(""").containsMatchIn(mainSource)
        )
        assertFalse(
            "tab 过渡不得写原始 spring() — 用 SleepyContent 家族",
            Regex("""\bspring\(""").containsMatchIn(mainSource)
        )
    }

    /** 契约 5 (回归锚): SaveableStateProvider(currentTab.name) 接线形状在 wrap 后原样存活。
     *  锚定 holder. 调用形态 — :310 注释里也写着 SaveableStateProvider(currentTab.name),
     *  不带前缀的正则会把注释数进去 (RED 跑实证 5 处), 排除散文误计。 */
    @Test
    fun saveable_state_provider_shape_survives_the_AnimatedContent_wrap() {
        val providerCalls = Regex("""holder\.SaveableStateProvider\(\s*currentTab\.name\s*\)""").findAll(mainSource).count()
        assertTrue(
            "4 个 tab 必须各自包 holder.SaveableStateProvider(currentTab.name), 实得 $providerCalls",
            providerCalls == 4
        )
    }
}

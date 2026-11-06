package com.lingion.sleepy.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 主题切换色彩缓动契约 (MD3E motion 全量补齐 Task 8, 2026-10-29):
 *
 * 官方 AnimatedTheme/AnimatedMaterialTheme 已从 material3 移除
 * (1.5.0-beta02 classes.jar 复核零命中, brief 预验证成立) — 等价语义在
 * SleepyThemeProvider 单点重建: 48 个 ColorScheme 色彩槽各挂
 * animateColorAsState, 规格取 MotionScheme.expressive().defaultEffectsSpec
 * (brief: 色彩族走 effects 档)。
 *
 * 选型裁决 (brief 双路线的证据化落点):
 *   - 整树 Crossfade 否决: 瞬态双组合会重挂载 AppRoot 子树, NavDisplay/
 *     rememberSaveable/DisposableEffect 全暴露于状态丢失风险 (本文件历史
 *     注释记录的 preset early-return 丢状态事故即同类风险)。
 *   - per-surface 逐点 animateColorAsState 否决: 只覆盖改造过的站点,
 *     一次性整树切 (system dark) 仍硬切 — miss。
 *   - 采纳 re-provide transitioning colorScheme: 组合树形状不变, 消费者
 *     零改动即全局渐变, 与旧 AnimatedTheme 语义等同。
 *
 * 行为终态与缓动存在性由 Robolectric 管线测试
 * ThemeSwitchCrossfadeSmokeTest 锁 (存在性/往返/状态存活)。
 */
class ThemeSwitchMotionContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    private val theme: String by lazy { loadSource("ui/theme/Theme.kt") }

    @Test
    fun provider_wires_transitioning_scheme() {
        assertTrue(
            "m3Scheme 必须经 .transitioning() 缓动包装再喂 MaterialExpressiveTheme",
            Regex("""wakeToM3Scheme\(wc, darkTheme\)\.transitioning\(\)""").containsMatchIn(theme)
        )
        assertTrue("MaterialExpressiveTheme 仍消费 m3Scheme", theme.contains("colorScheme = m3Scheme"))
    }

    @Test
    fun wrapper_animates_every_color_slot_with_effects_spec() {
        val body = theme.substringAfter("private fun ColorScheme.transitioning(")
            .substringBefore("@Composable\nfun SleepyThemeProvider(")
        // ColorScheme 色彩槽定数 48 (beta02 javap 复核); 槽数漂移 = 升级漏包, 即红
        assertEquals("transitioning() 必须逐槽取主题色动画全部 48 色", 48,
            Regex("""animateColorSlot\(""").findAll(body).count())
        assertTrue("全 48 槽显式重建 scheme (copy 基础值不生效)", body.contains("copy("))
        // 规格单一来源在共享 helper: expressive defaultEffects (brief: 色彩走 effects 档)。
        // 切片止于 transitioning 的 KDoc 起始 (/**) — 该 KDoc 复述了规格字面量,
        // 若入切片则 helper 体写坏也能虚绿。
        val helper = theme.substringAfter("private fun animateColorSlot(")
            .substringBefore("/**")
        assertTrue("helper 必须 animateColorAsState", helper.contains("animateColorAsState("))
        assertTrue("规格来源 MotionScheme.expressive()", helper.contains("MotionScheme.expressive()"))
        assertTrue("色彩族走 defaultEffectsSpec (brief 约束)", helper.contains("defaultEffectsSpec"))
    }

    @Test
    fun whole_tree_crossfade_stays_ruled_out() {
        // 反向闸: 禁止改回整树 Crossfade (双组合重挂载风险, 类 doc 选型记录)
        assertFalse("Theme.kt 禁出现 Crossfade( — 整树双挂载路线已裁决否决",
            Regex("""Crossfade\(""").containsMatchIn(theme))
    }

    @Test
    fun course_palette_switch_stays_instant_ruling() {
        // LocalCoursePalette 仍直换 (Task 8 范围 = M3 colorScheme; 课程色卡
        // 是分类色身份而非主题面, 缓动无视觉语义, 终审查可翻案)
        assertTrue("palette 选择仍为 if(darkTheme) 直三元",
            Regex("""val palette = if \(darkTheme\) DarkCoursePalette else LightCoursePalette""").containsMatchIn(theme))
    }
}

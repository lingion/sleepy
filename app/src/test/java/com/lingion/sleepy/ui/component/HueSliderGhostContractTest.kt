package com.lingion.sleepy.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HueSlider 色相条幽灵接线源码契约 (spec-slider-ghost-appwide-2026-10-29 §3.1
 * 色相变体 + §4 调用点; 先例 DisplaySliderGhostContractTest)。
 *
 * 锁:
 * 1. HueSlider 签名新增 initialHue: Float? = null (默认 null → 两既有调用零破坏);
 *    色相条底衬是彩虹, 单头共享件 SingleSliderGhostMark 的 primary 色辨不清,
 *    故此处走专用双描细条 (白芯+深描), 判据仍复用 singleSliderGhostVisible —
 *    禁手抄判据 (epsilon 字面量 0.5f 只允许出现在调用它那一行)。
 * 2. 两调用点均传 initialHue: ColorPickerDialog 用 remember 打开弹窗时刻的
 *    initialHSV[0]; AppearanceScreen 课程配色种子用进编辑区时刻的基线。
 * 3. epsilon = 0.5f (色相 1 度步进取半档, spec §3.1)。
 */
class HueSliderGhostContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val picker by lazy { loadSource("ui/component/ColorPickerDialog.kt") }
    private val appearance by lazy { loadSource("ui/screen/mine/AppearanceScreen.kt") }

    @Test
    fun hue_slider_signature_has_optional_initial_hue() {
        assertTrue(
            "HueSlider 签名缺 initialHue: Float? = null",
            Regex("""initialHue:\s*Float\?\s*=\s*null""").containsMatchIn(picker),
        )
    }

    @Test
    fun ghost_reuses_shared_predicate_with_half_degree_epsilon() {
        assertTrue("未消费共享判据 singleSliderGhostVisible", picker.contains("singleSliderGhostVisible("))
        assertEquals("epsilon 0.5f 只允许出现一次 (判据调用)", 1, Regex("""0\.5f""").findAll(picker).count())
        assertTrue(
            "判据参必须挂 hue/initialHue",
            Regex("""singleSliderGhostVisible\(\s*hue,\s*initialHue,\s*0\.5f\s*\)""").containsMatchIn(picker),
        )
        assertTrue("幽灵必须挂管线定位锚 testTag(\"hue-ghost\")", picker.contains("""testTag("hue-ghost")"""))
    }

    @Test
    fun both_call_sites_pass_initial_hue_baseline() {
        // ColorPickerDialog 内调用: 打开弹窗时刻 initialHSV[0]
        assertTrue(
            "ColorPickerDialog 调用点未传 initialHue",
            Regex("""initialHue\s*=\s*(hueBaseline|remember|\w*[Bb]aseline)""").containsMatchIn(picker),
        )
        // AppearanceScreen 课程配色种子调用点 (HueSlider 实参里含 lambda 括号, 正则难锚, 锁赋值式)
        assertTrue(
            "AppearanceScreen HueSlider 调用点未传 initialHue 基线",
            appearance.contains("initialHue = hueBaseline"),
        )
        assertTrue(
            "AppearanceScreen 基线必须是进编辑区时刻的 seed remember",
            Regex("""val\s+hueBaseline\s*=\s*remember\s*\{\s*seed\.coerceAtLeast\(0f\)""").containsMatchIn(appearance),
        )
    }
}

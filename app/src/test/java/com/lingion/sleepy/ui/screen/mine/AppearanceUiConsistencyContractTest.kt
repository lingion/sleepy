package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观页控件一致性契约 (2026-10-20 用户裁定):
 * PR#189 课程配色区与深浅色 Tab 各自独创了一套控件形态(三单选 RadioButton +
 * 裸 Slider + 手搓选中盒 Row), 与全 app 轨道式分段控件(SegmentedSwitcher)不一致,
 * 视觉/交互双脱节。本测试源码级锁定改造结果, 防回归。
 *
 * 先例: CustomThemeUiContractTest / HolidaySettingsContractTest —
 * 仓库无 Compose UI 测试, 声明式接线读源头文件等价于读编译产物。
 *
 * 契约:
 *   1. AppearanceScreen 禁 RadioButton — 课程配色三档必须走 SegmentedSwitcher 轨道;
 *   2. AppearanceScreen 禁裸 material3.Slider — 色相滑条必须用共享组件 HueSlider
 *      (与 ColorPickerDialog 全 app 色相取色同源), 且文件不得残留 material3.Slider 导入;
 *   3. 深浅色 Tab 必须用 SegmentedSwitcher — 禁手搓 Row+if(sel) 选中盒(旧模式整段封禁);
 *   4. SegmentedSwitcher 在外观页至少出现 2 处(深浅 Tab + 课程配色三档);
 *   5. 重算按钮必须走 SleepyTheme.Buttons 规约(shape/regularHeight), 禁手搓 shape;
 *   6. HueSlider 必须支持松手提交回调 onHueCommit(内部接 detectDragGestures 的
 *      onDragStopped) — 拖动只动本地值, 松手才落库重算(旧 Slider 的
 *      onValueChangeFinished 语义在新组件上必须有等价物, 否则拖动每帧重算全表)。
 */
class AppearanceUiConsistencyContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("src/main/java/com/lingion/sleepy/"),
            java.io.File("app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val appearanceSource: String by lazy { loadSource("ui/screen/mine/AppearanceScreen.kt") }
    private val colorPickerSource: String by lazy { loadSource("ui/component/ColorPickerDialog.kt") }

    @Test
    fun uniform_color_remains_a_fourth_exclusive_choice() {
        val section = appearanceSource.substringAfter("private fun CourseColorSection(")
        assertTrue(section.contains("null to stringResource(R.string.course_color_uniform)"))
        assertTrue(section.contains("selected = if (colorless) null else mode"))
        assertTrue(section.contains("if (selected == null) onColorlessSelect() else onModeChange(selected)"))
        assertTrue(section.contains("if (!colorless && mode == AppPrefs.COURSE_COLOR_MODE_CUSTOM)"))
        val modeChange = appearanceSource.substringAfter("onModeChange = { newMode ->")
            .substringBefore("onSeedCommit =")
        assertTrue(modeChange.contains("AppPrefs.setCourseColorless(context, false)"))
        assertTrue(appearanceSource.contains("AppPrefs.setCourseColorless(context, true)"))
    }

    /** 契约 1: 外观页不再出现 RadioButton(导入+调用;注释提及不算) */
    @Test
    fun appearance_screen_has_no_radio_button() {
        assertFalse(
            "Course-color three-mode must use SegmentedSwitcher; RadioButton import is banned",
            appearanceSource.contains("androidx.compose.material3.RadioButton")
        )
        assertFalse(
            "RadioButton( call site must be gone from AppearanceScreen",
            appearanceSource.contains("RadioButton(")
        )
    }

    /** 契约 2: 裸 material3.Slider 封禁 — 色相走共享 HueSlider, 导入同步清理 */
    @Test
    fun appearance_screen_uses_shared_hue_slider_not_raw_slider() {
        assertFalse(
            "raw material3.Slider import must be gone after switching to HueSlider",
            appearanceSource.contains("androidx.compose.material3.Slider")
        )
        assertFalse(
            "raw Slider( call is banned; use the shared HueSlider component",
            Regex("""(?<!Hue)(?<![A-Za-z])Slider\(""").containsMatchIn(appearanceSource)
        )
        assertTrue(
            "hue control must be the shared HueSlider from ColorPickerDialog",
            appearanceSource.contains("HueSlider(")
        )
    }

    /** 契约 3: 深浅色 Tab 的手搓选中盒整段封禁 */
    @Test
    fun theme_mode_tab_no_handrolled_selected_box() {
        assertFalse(
            "light/dark tab must be SegmentedSwitcher, not the hand-rolled if(sel) box",
            appearanceSource.contains("if (sel) colors.primary else colors.surfaceContainerHigh")
        )
    }

    /** 契约 4: 轨道控件在外观页出现 ≥2 处(深浅 Tab + 课程配色) */
    @Test
    fun track_switcher_used_for_both_segmented_controls() {
        val count = Regex("""SegmentedSwitcher\(""").findAll(appearanceSource).count()
        assertTrue(
            "expected SegmentedSwitcher at least twice (theme mode tab + course color modes), found $count",
            count >= 2
        )
    }

    /** 契约 5: 重算按钮走全 app 按钮规约 */
    @Test
    fun recalc_button_follows_sleepy_button_convention() {
        assertTrue(
            "recalc button shape must be SleepyTheme.Buttons.shape",
            appearanceSource.contains("SleepyTheme.Buttons.shape")
        )
        assertTrue(
            "recalc button height must be SleepyTheme.Buttons.regularHeight",
            appearanceSource.contains("SleepyTheme.Buttons.regularHeight")
        )
    }

    /** 契约 6: HueSlider 具备松手提交能力(拖动节流落库的前提) */
    @Test
    fun hue_slider_supports_commit_on_drag_stop() {
        assertTrue(
            "HueSlider must expose onHueCommit for release-time persistence",
            colorPickerSource.contains("onHueCommit")
        )
        assertTrue(
            "onHueCommit must be wired to detectDragGestures onDragEnd (cancel path must not commit)",
            colorPickerSource.contains("onDragEnd = {") && colorPickerSource.contains("onDragCancel = {")
        )
        assertTrue(
            "AppearanceScreen must commit seed only on release (onHueCommit site)",
            appearanceSource.contains("onHueCommit =")
        )
    }
}

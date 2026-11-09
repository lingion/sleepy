package com.lingion.sleepy.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 液态玻璃按钮范围契约 (2026-11-04 用户三裁):
 *
 *   1. 长条形操作按钮必须玻璃化: 保存课表设置、删除课表、编辑课程、应用/新增上课时段、
 *      创建课表/作息表、确认导入、解析预览、复制提示词、更新检查、恢复全部课表数据、
 *      调整/保存单次调课、保存/删除自定义主题、修改/删除作息表、刷新小组件、重算日期 —
 *      这些文件的 Button / FilledTonalButton 一律走 GlassButton / GlassTonalButton 包装。
 *      锁定方式: import 别名存在, 且文件内不再出现裸 `import androidx.compose.material3.Button(`
 *      全限定调用。
 *   2. 宽大列表入口 / 导航入口保持扁平(2026-11-03 裁定不变, 禁玻璃回归):
 *      GeneralSettingsScreen 迁移导出/导入行、ExportScreen 表选择卡/PickerRow/ExportItem、
 *      ImportSheet ImportMethodRow、SettingsCards DisplayModeOption、
 *      SchoolSelectScreen SchoolRow、TimeSlotEditor BindChoiceRow。
 *      锁定方式: 这些函数体内不得出现 `.glassControl(`。
 *   3. 教务直连右侧字母索引条不加玻璃按钮背景(2026-11-04 裁定):
 *      SchoolSelectScreen AlphabetIndexBar 函数体内不得出现 `.glassControl(`。
 *
 * 载体形态: 纯 UI 源码结构, 按 ExpandChevronMotionContractTest 定型先例用源码锚定;
 * 行为(点击回调/禁用语义)由 GlassButtons 包装自身保证, 不在本契约重复。
 */
class LiquidGlassButtonScopeContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    /** 契约 1: 操作按钮文件必须把 Button/FilledTonalButton 别名到玻璃包装。 */
    @Test
    fun operation_button_files_route_through_glass_wrappers() {
        val fullGlassAlias = listOf(
            "ui/screen/mine/EditTableScreen.kt" to "Button",
            "ui/screen/mine/CustomThemeEditorScreen.kt" to "Button",
            "ui/screen/mine/PeriodTablesScreen.kt" to "Button",
            "ui/screen/mine/PeriodTableEditScreen.kt" to "Button",
            "ui/screen/mine/AboutScreen.kt" to "Button",
            "ui/screen/imports/ImportSheet.kt" to "Button",
            "ui/screen/schedule/ScheduleScreen.kt" to "Button",
            "ui/screen/edit/AddCourseScreen.kt" to "Button",
            "ui/screen/edit/SingleOccurrenceCourseForm.kt" to "Button",
            "ui/screen/edit/OccurrenceEditor.kt" to "Button",
            "ui/screen/mine/HolidaySettingsScreen.kt" to "Button",
            "ui/component/CourseDetailSheet.kt" to "Button",
        )
        for ((path, alias) in fullGlassAlias) {
            val src = loadSource(path)
            assertTrue(
                "$path must route its $alias through the glass wrapper (2026-11-04 用户: 长条操作按钮要液态玻璃)",
                src.contains("import com.lingion.sleepy.ui.component.liquidglass.Glass$alias as $alias")
            )
            assertFalse(
                "$path must not import the bare material3 $alias",
                src.contains("import androidx.compose.material3.$alias\n")
            )
        }
        val tonalAlias = listOf(
            "ui/screen/schedule/ScheduleScreen.kt",
            "ui/screen/edit/AddCourseScreen.kt",
            "ui/screen/edit/OccurrenceEditor.kt",
            "ui/screen/mine/AllTablesScreen.kt",
            "ui/screen/mine/WidgetSettingsScreen.kt",
            "ui/screen/mine/AppearanceScreen.kt",
            "ui/screen/mine/HolidaySettingsScreen.kt",
        )
        for (path in tonalAlias) {
            val src = loadSource(path)
            assertTrue(
                "$path must alias FilledTonalButton to GlassTonalButton (长条 tonal 操作键玻璃化)",
                src.contains("import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton")
            )
            assertFalse(
                "$path must not import the bare material3 FilledTonalButton",
                src.contains("import androidx.compose.material3.FilledTonalButton\n")
            )
        }
        // 全限定裸调用清零: FQN 调用绕开别名, 等于漏网扁平键。
        // 唯一豁免 = ReminderScreen 的 onOpenHoliday(假期规则二级页入口, 契约 2 要求它保持扁平)。
        val tonalFiles = tonalAlias + listOf("ui/screen/mine/ReminderScreen.kt", "ui/screen/edit/SingleOccurrenceCourseForm.kt")
        for (path in fullGlassAlias.map { it.first } + tonalFiles) {
            val src = loadSource(path)
            assertFalse(
                "$path still contains a fully-qualified androidx.compose.material3.Button(...) call",
                Regex("""androidx\.compose\.material3\.Button\(""").containsMatchIn(src)
            )
            val fqnTonal = Regex("""androidx\.compose\.material3\.FilledTonalButton\(""").findAll(src).count()
            val allowed = if (path == "ui/screen/mine/ReminderScreen.kt") 1 else 0
            assertTrue(
                "$path has $fqnTonal fully-qualified FilledTonalButton calls, expected at most $allowed",
                fqnTonal <= allowed
            )
        }
        // TimeSlotEditor 的 添加一节 小号操作键本就未随 119e1150 扁平化, 锁住不退化
        assertTrue(
            "TimeSlotEditor must keep its add-period Button on the glass wrapper",
            loadSource("ui/component/TimeSlotEditor.kt")
                .contains("import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button")
        )
    }

    /** 契约 2: 宽大列表入口/导航入口保持扁平, 玻璃不得回归(2026-11-03 裁定不变)。 */
    @Test
    fun wide_navigation_entries_stay_flat() {
        // 这些文件当前整文件零 glassControl — 文件级锁最严(任何再玻璃化即红)。
        for (path in listOf(
            "ui/screen/mine/GeneralSettingsScreen.kt",
            "ui/screen/mine/ExportScreen.kt",
            "ui/component/SettingsCards.kt",
            "ui/screen/imports/SchoolSelectScreen.kt",
            "ui/component/TimeSlotEditor.kt",
        )) {
            assertFalse(
                "$path holds wide navigation/list entries — must stay flat (2026-11-03 裁定), no glassControl",
                loadSource(path).contains(".glassControl(")
            )
        }
        // ImportSheet 别处仍有合规玻璃, 只锁 ImportMethodRow 函数体
        val methodRow = loadSource("ui/screen/imports/ImportSheet.kt")
            .substringAfter("private fun ImportMethodRow(")
            .substringBefore("private fun FormatRow(")
        assertFalse(
            "ImportMethodRow is a navigation entry into second-level pages — must stay flat",
            methodRow.contains(".glassControl(")
        )
        // ReminderScreen 的假期规则跳转行 = 二级页导航入口, 保持扁平裸 Material
        val reminder = loadSource("ui/screen/mine/ReminderScreen.kt")
        val beforeHolidayJump = reminder.substringBefore("onClick = onOpenHoliday,")
        assertTrue(
            "onOpenHoliday jumps to the holiday-rules page — must stay flat (2026-11-04 用户: 大导航入口不加玻璃)",
            beforeHolidayJump.trimEnd().endsWith("androidx.compose.material3.FilledTonalButton(")
        )
    }

    /** 契约 3: 字母索引条不加玻璃按钮背景 (2026-11-04 用户: 教务直连最右 alphabet 无必要加玻璃)。 */
    @Test
    fun alphabet_index_bar_has_no_glass() {
        val rail = loadSource("ui/screen/imports/SchoolSelectScreen.kt")
            .substringAfter("private fun AlphabetIndexBar(")
            .substringBefore("private fun SchoolRow(")
        assertFalse(
            "AlphabetIndexBar letters must not carry glassControl — 每个字母一块玻璃按钮是噪点",
            rail.contains(".glassControl(")
        )
        // 拖动/点按的跳转能力保留: Y→index 手势与 onLetterTap 回调仍在
        assertTrue("AlphabetIndexBar must keep its pointer-input drag mapping", rail.contains("onLetterTap(letters[idx])"))
    }

    /** Geometry and gestures are exercised by FloatingGlassControlTest and render tests. */
    @Test
    fun segmented_switcher_uses_a_sibling_source_and_draws_lens_last() {
        val src = loadSource("ui/component/SegmentedSwitcher.kt")
        assertTrue("track and labels must be sampled from an ordinary sibling", src.contains(".layerBackdrop(trackBackdrop)"))
        assertTrue("scene and local track must both reach the lens", src.contains("rememberCombinedBackdrop(scene, trackBackdrop)"))
        assertTrue("Tab must use the shared control optics", src.contains("LiquidGlassStyle.controlLens"))
        assertFalse("Tab must not override the shared refraction", src.contains("refraction = -8.5"))
        val backing = src.indexOf("backing.placeRelative(0, 0)")
        val lens = src.indexOf("lens.placeRelativeWithLayer", backing)
        assertTrue("source and glyphs must draw before the refracting lens", backing >= 0 && lens > backing)
        assertTrue("moving between segments must trigger selection feedback", src.contains("LiquidGlassHaptics.selectionClick(view)"))
        assertFalse("root track must not clip the raised lens", src.contains(".clip(RoundedCornerShape(14.dp))"))
    }

    @Test
    fun liquid_glass_slider_thumb_and_classic_tab_are_mode_gated() {
        val segmented = loadSource("ui/component/SegmentedSwitcher.kt")
        assertTrue("Classic Tab mode must not install the custom drag-stealing pointer handler",
            segmented.contains("if (glassActive) Modifier.pointerInput") ||
                segmented.contains("if (!glassActive) return@pointerInput"))
        assertTrue("glass Tab lens must sample the host scene prefix, never an ancestor-registered local backdrop",
            segmented.contains("backdrop = lensBackdrop") &&
                Regex("backdropAt\\(maxOf\\(1f, consumerDepth\\)\\)").containsMatchIn(segmented) &&
                !segmented.contains("layerBackdrop("))

        val range = loadSource("ui/component/IntRangeSlider.kt")
        assertTrue("two-ended Material RangeSlider must provide glass thumbs in Liquid Glass mode",
            range.contains("startThumb = { rangeSliderState ->") &&
                range.contains("endThumb = { rangeSliderState ->") &&
                range.contains("GlassSliderThumb(trackBackdrop = trackBackdrop)"))
        val schedule = loadSource("ui/screen/mine/ScheduleDisplayScreen.kt")
        assertTrue("single-ended Material Slider must use the same shared glass thumb",
            schedule.contains("thumb = { _ -> GlassSliderThumb(trackBackdrop = trackBackdrop) }") &&
                schedule.contains("SliderState(value = value, steps = 0, trackRange = range)"))
        val sliderThumb = loadSource("ui/component/liquidglass/GlassSliderThumb.kt")
        assertTrue("slider thumb must use Toggle's resting horizontal capsule geometry",
            sliderThumb.contains("RESTING_WIDTH = 37f") &&
                sliderThumb.contains("RESTING_HEIGHT = 24f") &&
                sliderThumb.contains("LIFT_GROWTH_WIDTH = 16f") &&
                sliderThumb.contains("LIFT_GROWTH_HEIGHT = 6f") &&
                sliderThumb.contains("RoundedCornerShape(50)"))
        assertTrue("slider thumb must draw with its own size so the 44dp Material slot does not stretch the capsule",
            sliderThumb.contains("Box(\n        modifier\n            .size(width.dp, height.dp)"))
        assertTrue(
            "resting thumb must be a light tint of the theme color, not the track's primary " +
                "(2026-11-08 用户: 静止时是白色/淡色, 按主题色里的淡色, 不得跟轨道同色)",
            Regex("""lerp\(thumbColor, Color\.White, 0\.85f\)""").containsMatchIn(sliderThumb),
        )
        assertTrue(
            "pressed lens must refract the track pixels beneath it: the sampling source combines the " +
                "host scene prefix with the recorded track layer (2026-11-08 用户: 摁起来要把轨道包进透镜折射, " +
                "不得是里面没东西的透明色块)",
            sliderThumb.contains("rememberCombinedBackdrop") &&
                sliderThumb.contains("backdropAt") &&
                sliderThumb.contains("backdrop = lensSource"),
        )

        // 幽灵态 = 滑块静置外形 (2026-11-08 用户: "滑块长什么样，幽灵态就长什么样；
        // 不是液态玻璃椭圆滑块配 Material 竖条幽灵") — 双头与单头两条通路都要按
        // UiStyle 分支, Classic 4dp 细条通路一字不动 (SingleSliderGhostGeometryTest
        // 像素对拍继续锁 Material thumb 同位)。
        assertTrue("glass RangeSlider ghost must reuse the thumb's resting capsule width",
            range.contains("GlassSliderThumbMetrics.RESTING_WIDTH.dp"))
        assertTrue("glass RangeSlider ghost must center the capsule on the track band",
            Regex("""thumbTop = \(size\.height - thumbH\) / 2f""").containsMatchIn(range))
        assertTrue("glass RangeSlider ghost must tint with the thumb's light resting color",
            range.contains("GlassSliderThumbMetrics.restingColor("))
        assertTrue("glass DisplaySlider ghost must draw at the thumb's capsule height",
            schedule.contains("thumbHeightDp = if (LocalUiStyle.current == UiStyle.LiquidGlass)") &&
                schedule.contains("24.dp else null"))
        assertTrue("single-slider ghost renders capsule only when a thumb height is supplied (Classic keeps the 4dp bar)",
            range.contains("thumbHeightDp: Dp? = null"))
    }

    @Test
    fun switch_lens_sampling_is_centered_on_the_resting_track() {
        val src = loadSource("ui/component/liquidglass/GlassSwitch.kt")
        assertTrue("sampler must keep the thumb layout centered; sampling must not move the production thumb",
            !src.contains("Modifier.offset(x = lensOffsetX, y = lensOffsetY)") &&
                src.contains("val thumbWidth = (37f + 12f * stretch) + 16f * lift") &&
                src.contains("val thumbHeight = (24f - 2f * stretch) + 6f * lift"))
        assertTrue("lens must sample the checked track itself to achieve symmetric refraction",
            src.contains("backdrop = trackBackdrop") && src.contains("shape = { thumbShape }"))
        val backgroundIndex = src.indexOf(".background(thumbColor.copy")
        val lensIndex = src.indexOf(".drawBackdrop(", backgroundIndex)
        assertTrue("Toggle must paint the translucent thumb before the lens so the track is visible inside it", backgroundIndex >= 0 && lensIndex > backgroundIndex)
    }

    /** 契约 5: Toggle 静置几何保留 37×24dp 横椭圆，交互抬升为越轨道的折射胶囊。
     *  参考帧锁镜片中心采样、按下状态与轨道同心；应用轨道坐标被采样一次，玻璃只围绕
     *  thumb 中心产生对称折射。原有 sarunw iOS 26 几何与 22dp travel 继续保留。
     *  用户额外确认：动态 Toggle 采样时，轨道上方/下方必须同量向中心缩放，不得一侧
     *  保留空隙而另一侧无空隙。
     *  静置 37×24dp，轨道 63×28dp，行程 22dp；按压镜片尺寸、折射和 release
     *  回弹由 GlassSwitchTest 与参考帧视觉测试双重验证。 */
    @Test
    fun glass_switch_stretches_into_capsule_on_press() {
        val src = loadSource("ui/component/liquidglass/GlassSwitch.kt")
        // 实测几何: 滑块基准 37×24 (2pt 全周留白), 行程 22dp = 63−37−4
        assertTrue(
            "pressed thumb must widen from the measured 37pt ellipse with lift",
            Regex("""thumbWidth = \(37f \+ 12f \* stretch\) \+ 16f \* lift""").containsMatchIn(src)
        )
        assertTrue("travel must leave the measured 2dp inset on both ends (22dp)", src.contains("22.dp.toPx()"))
        assertTrue("offset must use the 22dp travel", src.contains("* 22f).dp"))
        // 2026-11-06 用户: 升镜玻璃必须越出轨道上下缘 — 高度随 lift 生长是硬要求
        assertTrue(
            "lift height must add 6dp before the physical press scale so its rim reaches past both edges " +
                "(2026-11-06 用户: 浮起的玻璃块高度不够，没有超出上下边缘; measured iOS press lens ~1.18× track)",
            Regex("""thumbHeight = \(24f - 2f \* stretch\) \+ 6f \* lift""").containsMatchIn(src)
        )
        // 但静置态仍须是横椭圆: 生长只许 6dp, 不许旧 40x38 方形气泡借道回归 (全升 53:30=1.77 胶囊比)
        assertTrue(
            "lift height growth must stay at 6dp — enough to overflow 28dp track, " +
                "not enough to square the capsule",
            Regex("""thumbHeight = \(24f - 2f \* stretch\) \+ 6f \* lift""").containsMatchIn(src)
        )
        assertTrue("thumb shape must be fully-rounded (ellipse at rest, capsule when wide)",
            Regex("""RoundedCornerShape\(50\)""").containsMatchIn(src))
        assertFalse("scaling a sampled texture must not magnify or displace the underlying track", src.contains("scaleX = 1f + 0.2f * lift"))
        assertTrue("pressed glass capsule must share the control optical factory",
            src.contains("val baseLens = LiquidGlassStyle.controlLens(scheme, dark)"))
        val chain = src.substringAfter(".requiredSize((thumbWidth *").substringBefore("contentAlignment = Alignment.Center")
        assertTrue("shadow must follow the shared FLClash profile", chain.contains("controlLensShadow(dark, lift)"))
        assertTrue("lens backdrop must follow the capsule shape", chain.contains("shape = { thumbShape }"))
        assertTrue("light pressed thumb must reveal the sampled backdrop as an optical lens",
            chain.contains("thumbColor.alpha * (1f - lift)"))
        assertFalse("thumb chain must not pin a plain CircleShape", chain.contains("CircleShape"))
    }
}

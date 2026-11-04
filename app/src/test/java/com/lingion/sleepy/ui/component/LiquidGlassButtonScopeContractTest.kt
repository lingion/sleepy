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

    /** 契约 4: 分段滑块拖动浮起 — thumb graphicsLayer 由 dragging 驱动的 lift 放大 + 投影;
     *  轨道外层去 clip(底色改带形状的 background 自绘圆角), 让浮起的滑块越出轨道边缘。 */
    @Test
    fun segmented_switcher_thumb_lifts_while_dragging() {
        val src = loadSource("ui/component/SegmentedSwitcher.kt")
        assertTrue(
            "lift must be driven by dragging state",
            src.contains("animateFloatAsState(if (dragging) 1f else 0f")
        )
        val thumbBlock = src.substringAfter(".testTag(\"segment-thumb\")").substringBefore("// 层1..n: 点击段")
        assertTrue("thumb lift must scale X", thumbBlock.contains("scaleX = 1f + 0.155f * l"))
        assertTrue("thumb lift must scale Y", thumbBlock.contains("scaleY = 1f + 0.155f * l"))
        assertTrue("thumb lift must cast elevation shadow", thumbBlock.contains("shadowElevation = if (l > 0f) 8.dp.toPx() * l else 0f"))
        assertTrue("thumb lift layer must not clip its own shadow", thumbBlock.contains("clip = false"))
        // 轨道外层不再 clip(14dp): 投影与放大需要越出轨道; 圆角由带形状的 background 自绘。
        assertFalse(
            "track outer modifier must not clip at 14dp anymore (float-out requires overflow)",
            src.contains(".clip(RoundedCornerShape(14.dp))")
        )
        assertTrue(
            "track fill must carry its own 14dp rounded shape",
            src.contains(".background(controlFill(containerColor ?: colors.surfaceContainer), RoundedCornerShape(14.dp))")
        )
    }

    /** 契约 5: 开关按压泡成胶囊 — 宽度随 lift 拉大, 高度不随 lift 抬升(保持近圆基准),
     *  镜片/投影/底色跟随胶囊形状; CircleShape 只许留在轨道上。 */
    @Test
    fun glass_switch_stretches_into_capsule_on_press() {
        val src = loadSource("ui/component/liquidglass/GlassSwitch.kt")
        assertTrue(
            "pressed thumb must widen with lift",
            Regex("""thumbWidth = \(24f \+ 13f \* stretch\) \+ 16f \* lift""").containsMatchIn(src)
        )
        assertFalse(
            "thumb height must NOT grow with lift — 40x38 方形气泡是旧 bug, 胶囊要求宽显著大于高",
            Regex("""thumbHeight = .*lift""").containsMatchIn(src)
        )
        assertTrue("thumb shape must be fully-rounded (circle at rest, capsule when wide)",
            Regex("""RoundedCornerShape\(50\)""").containsMatchIn(src))
        val chain = src.substringAfter(".requiredSize(thumbWidth.dp, thumbHeight.dp)").substringBefore("contentAlignment = Alignment.Center")
        assertTrue("shadow must follow the capsule shape", chain.contains(".shadow((6f * lift).dp, thumbShape, clip = false)"))
        assertTrue("lens backdrop must follow the capsule shape", chain.contains("shape = { thumbShape }"))
        assertTrue("thumb fill must follow the capsule shape", chain.contains("thumbColor.copy(alpha = thumbColor.alpha * (1f - lift)), thumbShape)"))
        assertFalse("thumb chain must not pin a plain CircleShape", chain.contains("CircleShape"))
    }
}

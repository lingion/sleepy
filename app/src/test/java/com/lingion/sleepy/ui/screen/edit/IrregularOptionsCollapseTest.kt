package com.lingion.sleepy.ui.screen.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用户反馈 2026-09-09: 加课表单里「非常规节次」「非常规时间」两个开关平铺,
 * 用户误以为表单不能添加正常课程。契约:
 *  - 两个开关收进同一个折叠栏「非常规选项」(irregular_options_section), 默认折叠;
 *  - 编辑已启用任一非常规项的课程时折叠栏自动展开 (否则看不到已启用项);
 *  - 标准节次字段 (start_node/step_count) 留在折叠栏外 — 普通课程主路径不受折叠影响;
 *  - 展开交互沿用仓库既有模式 (ExpandMore chevron 旋转 + AnimatedVisibility, 参考
 *    EditTableScreen 节次时间表 / SettingsCards.SettingsCard);
 *  - 六 locale 文案齐全 (irregular_options_section / _sub)。
 *
 * 仓库无 Robolectric, 折叠默认值与摘要文案走 JVM 纯逻辑直测 (MeetingBlockDraft
 * 构造先例: BuildCourseEntityAliasTest); UI 结构走源码级守卫 (先例: TodayDateNavWiringTest)。
 */
class IrregularOptionsCollapseTest {

    // ---- A. 折叠栏默认展开状态: 由构造参数派生 ----

    @Test
    fun new_normal_block_collapsed_by_default() {
        val b = draft(isIrregularNode = false, isIrregularTime = false)
        assertFalse("新建普通课程卡: 折叠栏必须默认收起", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_irregular_node_block_expands_by_default() {
        val b = draft(isIrregularNode = true, isIrregularTime = false)
        assertTrue("编辑已启用非常规节次的卡: 折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_irregular_time_block_expands_by_default() {
        val b = draft(isIrregularNode = false, isIrregularTime = true)
        assertTrue("编辑已启用非常规时间的卡: 折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    @Test
    fun editing_both_irregular_block_expands_by_default() {
        val b = draft(isIrregularNode = true, isIrregularTime = true)
        assertTrue("两项都启用时折叠栏必须自动展开", b.irregularOptionsExpanded)
    }

    private fun draft(isIrregularNode: Boolean, isIrregularTime: Boolean) = MeetingBlockDraft(
        id = 1,
        days = androidx.compose.runtime.mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40",
        isIrregularNode = isIrregularNode,
        isIrregularTime = isIrregularTime
    )

    // ---- B. 折叠态摘要: 收起但有启用项时, 栏头露出启用了什么 ----

    @Test
    fun summary_blank_when_no_option_active() {
        assertEquals(
            "",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = false,
                startTime = "08:00", endTime = "09:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_node_when_only_node_active() {
        assertEquals(
            "非常规节次 · 第 0 节",
            irregularOptionsSummary(
                isIrregularNode = true, isIrregularTime = false,
                startTime = "", endTime = "",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_time_when_only_time_active() {
        assertEquals(
            "非常规时间 · 19:00–20:40",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = true,
                startTime = "19:00", endTime = "20:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_lists_both_joined_with_slash() {
        assertEquals(
            "非常规节次 · 第 -1 节 / 非常规时间 · 19:00–20:40",
            irregularOptionsSummary(
                isIrregularNode = true, isIrregularTime = true,
                startTime = "19:00", endTime = "20:40",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 -1 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    @Test
    fun summary_time_without_valid_range_shows_label_only() {
        // 跨午夜守卫会清空 endTime — 摘要降级为只显示开关名, 不拼半截区间
        assertEquals(
            "非常规时间",
            irregularOptionsSummary(
                isIrregularNode = false, isIrregularTime = true,
                startTime = "23:00", endTime = "",
                nodeSwitchLabel = "非常规节次", edgeNodeLabel = "第 0 节",
                timeSwitchLabel = "非常规时间"
            )
        )
    }

    // ---- C. 源码级守卫: AddCourseScreen 表单结构 ----

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private fun editorSource(): String =
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/edit/AddCourseScreen.kt").readText()

    /** IrregularOptionsSection 函数体: 到下一个 @Composable 声明为止 */
    private fun sectionBody(src: String): String =
        src.substringAfter("fun IrregularOptionsSection").substringBefore("\n@Composable")

    /** MeetingBlockEditor 函数体: 到下一个 @Composable 声明为止 */
    private fun editorBody(src: String): String =
        src.substringAfter("fun MeetingBlockEditor").substringBefore("\n@Composable")

    @Test
    fun section_composable_exists_with_expand_collapse_affordance() {
        val src = editorSource()
        assertTrue("AddCourseScreen 必须定义 IrregularOptionsSection 折叠栏",
            src.contains("fun IrregularOptionsSection"))
        val body = sectionBody(src)
        assertTrue("折叠栏必须用 AnimatedVisibility 展开/收起内容",
            body.contains("AnimatedVisibility"))
        assertTrue("折叠栏必须有 chevron (ExpandMore)",
            body.contains("ExpandMore"))
        assertTrue("chevron 必须随展开旋转 180f (仓库既有模式: EditTableScreen/SettingsCard)",
            body.contains("rotate(") && body.contains("180f"))
    }

    @Test
    fun both_irregular_switches_live_inside_section() {
        val body = sectionBody(editorSource())
        assertTrue("「非常规节次」开关必须在 IrregularOptionsSection 内部",
            body.contains("R.string.irregular_node_switch"))
        assertTrue("「非常规时间」开关必须在 IrregularOptionsSection 内部",
            body.contains("R.string.irregular_time_switch"))
        assertTrue("折叠栏标题必须用 irregular_options_section 文案",
            body.contains("R.string.irregular_options_section"))
        // 展开后的条件内容也收在栏内: 槽位摘要 + 起止时间输入
        assertTrue("非常规节次的槽位摘要必须随开关收进折叠栏",
            body.contains("R.string.edge_node_range"))
        assertTrue("非常规时间的时长输入必须随开关收进折叠栏",
            body.contains("R.string.irregular_duration_label"))
    }

    @Test
    fun standard_node_fields_stay_outside_section() {
        val src = editorSource()
        val body = editorBody(src)
        assertTrue("标准节次字段 (start_node/step_count) 必须留在折叠栏外",
            body.contains("R.string.start_node") && body.contains("R.string.step_count"))
        assertTrue("MeetingBlockEditor 必须挂载 IrregularOptionsSection",
            body.contains("IrregularOptionsSection("))
        assertFalse("两个非常规开关不得再平铺在 MeetingBlockEditor 主体里",
            body.contains("R.string.irregular_node_switch") ||
                body.contains("R.string.irregular_time_switch"))
    }

    @Test
    fun no_border_stroke_or_outlined_button_in_editor() {
        val src = editorSource()
        assertFalse("Sleepy 全 app 禁 BorderStroke", src.contains("BorderStroke"))
        assertFalse("Sleepy 全 app 禁 OutlinedButton", src.contains("OutlinedButton"))
    }

    // ---- D. 六 locale 文案齐全 ----

    @Test
    fun section_strings_present_in_all_six_locales() {
        val locales = listOf("values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW")
        for (locale in locales) {
            val f = findUpward("app/src/main/res/$locale/strings.xml")
            val text = f.readText()
            assertTrue("$locale 缺 irregular_options_section",
                text.contains("name=\"irregular_options_section\""))
            assertTrue("$locale 缺 irregular_options_section_sub",
                text.contains("name=\"irregular_options_section_sub\""))
        }
    }
}

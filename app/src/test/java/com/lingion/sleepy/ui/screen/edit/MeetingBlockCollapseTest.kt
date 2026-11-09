package com.lingion.sleepy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 时段卡整卡折叠（用户定令 2026-11-09）：进编辑页所有时段卡默认全折叠，卡面只留
 * 标题 + 摘要 + 星期选择 + 标准节次滑杆；「课表点胶囊 → 跳到对应时段」命中的那张卡
 * 开局展开（跳转接线本身由 EditCourseJumpToClickedSlotTest 锁）。
 * 新建课首卡与「新增时段」新卡展开 — 用户进来就是要填这张卡，折叠碍主路径。
 *
 * 先例照抄（IrregularOptionsCollapseTest）：AddCourseScreen 挂 viewModel/SleepyApp
 * 太重无法在 JVM 测试里装配整屏 — 折叠默认值走 JVM 纯逻辑直测，UI 结构与接线走源码级守卫。
 */
class MeetingBlockCollapseTest {

    private fun draft() = MeetingBlockDraft(
        id = 1,
        days = mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40"
    )

    private fun draftExpanded() = MeetingBlockDraft(
        id = 1,
        days = mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40",
        expanded = true
    )

    // ---- A. 折叠态默认值 ----

    @Test
    fun block_collapsed_by_default() {
        assertFalse("时段卡必须默认折叠 — 进编辑页先全收起", draft().expanded)
    }

    @Test
    fun expanded_constructor_param_propagates() {
        // 锁构造参数传递: initialMeetingBlock 新建分支与「新增时段」新卡靠 expanded=true
        // 生效; 若类体 shadowing 写错 (mutableStateOf(false) 之类), 这两处行为静默退化
        assertTrue("传 expanded=true 的卡必须开局展开", draftExpanded().expanded)
    }

    // ---- B. 源码级守卫: 折叠边界 ----

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

    /** MeetingBlockEditor 函数体: 到下一个 @Composable 声明为止 */
    private fun editorBody(src: String): String =
        src.substringAfter("fun MeetingBlockEditor").substringBefore("\n@Composable")

    @Test
    fun collapse_animated_with_chevron() {
        val body = editorBody(editorSource())
        assertTrue("整卡折叠必须用 AnimatedVisibility 收放内容",
            body.contains("AnimatedVisibility(") && body.contains("visible = block.expanded"))
        assertTrue("卡头必须有 chevron 且随展开旋转 180f (仓库既有模式: IrregularOptionsSection)",
            body.contains("rotate(") && body.contains("180f") && body.contains("block.expanded"))
    }

    @Test
    fun collapsed_body_keeps_summary_days_and_node_slider() {
        val body = editorBody(editorSource())
        val av = body.indexOf("AnimatedVisibility(")
        assertTrue("AnimatedVisibility 必须存在", av > 0)
        // 摘要/星期/节次滑杆在折叠区之前 = 收起态仍可见 (用户拍板「摘要 + 星期/节次可见」);
        // 第一个 IntRangeSlider 是节次滑杆, 周次滑杆在其后 — 位置序即折叠边界
        assertTrue("摘要必须在折叠区外 (收起态可见)", body.indexOf("MeetingBlockSummary(") in 0 until av)
        assertTrue("星期选择必须在折叠区外 (收起态可见)", body.indexOf("MultiDayPicker(") in 0 until av)
        assertTrue("标准节次滑杆必须在折叠区外 (收起态可见)", body.indexOf("IntRangeSlider(") in 0 until av)
    }

    @Test
    fun week_teacher_room_note_color_live_in_expanded_area() {
        val body = editorBody(editorSource())
        val av = body.indexOf("AnimatedVisibility(")
        assertTrue(av > 0)
        for (marker in listOf(
            "IrregularOptionsSection(",
            "R.string.week_every",
            "R.string.course_teacher",
            "R.string.course_room",
            "R.string.course_note",
            "ColorSection(",
        )) {
            assertTrue("「$marker」必须在折叠区内 (收起态隐藏)", body.indexOf(marker) > av)
        }
    }

    @Test
    fun validation_issues_stay_visible_when_collapsed() {
        val body = editorBody(editorSource())
        val av = body.indexOf("AnimatedVisibility(")
        assertTrue(av > 0)
        // 注意不能锚 "if (issues.isNotEmpty())" — 卡背景色那行(AnimatedVisibility 之前)也有同串;
        // 错误文案渲染块以独有注释定位
        val issuesRender = body.indexOf("校验错误永远可见")
        assertTrue("校验错误必须渲染在 AnimatedVisibility 之后 (不随折叠隐藏)",
            issuesRender > av)
        assertTrue("错误文案必须在折叠区之后逐条渲染",
            body.indexOf("issues.forEach") > av)
    }

    // ---- C. 源码级守卫: 展开侧接线 ----

    @Test
    fun jump_effect_expands_matched_block_once_per_block() {
        val src = editorSource()
        // 命中卡展开走已有 targetIdx (findTargetBlockIndex, 匹配规则不得重造)
        assertTrue("跳转 effect 必须展开命中的那张卡",
            src.contains("meetingBlocks[targetIdx].expanded = true"))
        // 一次性守卫记块对象身份: group 回填 clear+重建块 → 必须再展开再滚;
        // 用户手动收起后新增时段触发的 size 跳变 → 块对象不变, 不得强行撑开
        assertTrue("展开+滚动必须按块对象身份去重",
            src.contains("autoExpandedBlock !== meetingBlocks[targetIdx]"))
    }

    @Test
    fun new_course_first_block_starts_expanded() {
        val src = editorSource()
        val body = src.substringAfter("private fun initialMeetingBlock").substringBefore("internal fun buildCourseEntity")
        val nullBranch = body.substringBefore("val days =")
        val editBranch = body.substringAfter("val days =")
        assertTrue("新建课首卡必须展开 — 用户进来就是要填这张卡",
            nullBranch.contains("expanded = true"))
        assertFalse("编辑课首卡保持默认折叠 — 展开交给跳转 effect 按命中决定",
            editBranch.contains("expanded"))
    }

    @Test
    fun added_slot_block_starts_expanded() {
        val src = editorSource()
        val start = src.indexOf("id = nextBlockId")
        assertTrue("「新增时段」按钮代码必须存在", start > 0)
        // 该按钮里的 MeetingBlockDraft 调用一直延伸到 add_slot 文案之前 — 新卡必须展开
        val window = src.substring(start, src.indexOf("R.string.add_slot", start))
        assertTrue("「新增一个上课时段」的新卡必须展开", window.contains("expanded = true"))
    }
}

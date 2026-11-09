package com.lingion.sleepy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun block_collapsed_by_default() {
        assertFalse("时段卡必须默认折叠 — 进编辑页先全收起", draft().expanded)
    }

    @Test
    fun expanded_constructor_param_propagates() {
        assertTrue("传 expanded=true 的卡必须开局展开", draftExpanded().expanded)
    }

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
    fun collapsed_body_shows_only_title_and_summary_text() {
        val body = editorBody(editorSource())
        val av = body.indexOf("AnimatedVisibility(")
        assertTrue("AnimatedVisibility 必须存在", av > 0)
        assertTrue("摘要必须在折叠区外 (收起态可见)", body.indexOf("MeetingBlockSummary(") in 0 until av)
        assertTrue("星期选择必须收进折叠区 (收起态不显示)", body.indexOf("MultiDayPicker(") > av)
        val firstSlider = body.indexOf("IntRangeSlider(")
        assertTrue("标准节次滑杆必须收进折叠区 (收起态不显示)", firstSlider > av)
    }

    @Test
    fun delete_cross_and_chevron_both_at_right_chevron_rightmost() {
        val body = editorBody(editorSource())
        val header = body.substringAfter("卡头 = 折叠开关").substringBefore("AnimatedVisibility")
        val summary = header.indexOf("MeetingBlockSummary(")
        val cross = header.indexOf("R.string.delete_slot")
        val chevron = header.indexOf("Icons.Outlined.ExpandMore")
        assertTrue("文案必须在最左 (weight 1f 独占行宽)", summary > 0)
        assertTrue("删除叉与箭头必须都排在文案之后 (收最右侧)", summary < cross && summary < chevron)
        assertTrue("箭头必须是行内最后一个控件 (最右), 叉在箭头左侧相邻", cross < chevron)
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
        val issuesRender = body.indexOf("校验错误永远可见")
        assertTrue("校验错误必须渲染在 AnimatedVisibility 之后 (不随折叠隐藏)",
            issuesRender > av)
        assertTrue("错误文案必须在折叠区之后逐条渲染",
            body.indexOf("issues.forEach") > av)
    }

    @Test
    fun jump_effect_expands_matched_block_once_per_block() {
        val src = editorSource()
        assertTrue("跳转 effect 必须展开命中的那张卡",
            src.contains("meetingBlocks[targetIdx].expanded = true"))
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
        val window = src.substring(start, src.indexOf("R.string.add_slot", start))
        assertTrue("「新增一个上课时段」的新卡必须展开", window.contains("expanded = true"))
    }
}

package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新日志 Markdown 解析(纯 Kotlin, 无 Android 依赖)。
 * 替换黑盒 MarkdownText: 解析出的块结构由 Compose 端显式排版,
 * 标题/列表/粗体/链接都有确定的视觉结果,不再依赖第三方库的隐式行为。
 */
class MarkdownBlocksTest {

    @Test
    fun parsesHeadingLevels() {
        val blocks = MarkdownBlocks.parse("## New\n\nbody")
        assertEquals(listOf(
            MarkdownBlocks.Block.Heading(2, "New"),
            MarkdownBlocks.Block.Paragraph("body")
        ), blocks)
    }

    @Test
    fun parsesConsecutiveListItemsIntoOneGroup() {
        val blocks = MarkdownBlocks.parse("- a\n- b\n- c")
        assertEquals(
            listOf(MarkdownBlocks.Block.Bullet(listOf("a", "b", "c"))),
            blocks
        )
    }

    @Test
    fun keepsListSplitFromParagraph() {
        val blocks = MarkdownBlocks.parse("intro:\n\n- a\n- b")
        assertEquals(listOf(
            MarkdownBlocks.Block.Paragraph("intro:"),
            MarkdownBlocks.Block.Bullet(listOf("a", "b"))
        ), blocks)
    }

    @Test
    fun inlineBoldAndCodeAndLinkSplit() {
        val spans = MarkdownBlocks.parseInline("看 **加粗** 和 `code` 与 [链接](https://x.y)")
        assertEquals(listOf(
            MarkdownBlocks.Inline.Text("看 "),
            MarkdownBlocks.Inline.Bold("加粗"),
            MarkdownBlocks.Inline.Text(" 和 "),
            MarkdownBlocks.Inline.Code("code"),
            MarkdownBlocks.Inline.Text(" 与 "),
            MarkdownBlocks.Inline.Link("链接", "https://x.y")
        ), spans)
    }

    @Test
    fun plainTextYieldsSingleParagraph() {
        val blocks = MarkdownBlocks.parse("v1.0.40\n\nThree additions and one fix.")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MarkdownBlocks.Block.Paragraph })
    }

    @Test
    fun githubBulletWithNestedIndentIsTreatedAsListItem() {
        // release notes 有时用 2 空格缩进的子项; 至少不能当成普通段落吞掉列表结构
        val blocks = MarkdownBlocks.parse("- a\n  - b")
        val list = blocks.filterIsInstance<MarkdownBlocks.Block.Bullet>().firstOrNull()
        assertTrue("no bullet block", list != null)
        assertEquals(listOf("a", "b"), list?.items)
    }
}

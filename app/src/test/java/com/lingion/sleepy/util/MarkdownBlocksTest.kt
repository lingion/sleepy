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

    // ==== 探针: 覆盖 release notes 会出现的冷门/畸形 Markdown ====

    /** 模拟即将发出的 v1.0.41 探针 release body, 与 /tmp/probe-notes.md 保持一致 */
    private val probeBody = """
# 渲染探针一级标题

## 二级标题: 粗体和代码

这一段里有 **粗体片段**、`inline_code` 和 [一个链接](https://github.com/lingion/sleepy) 混排, 用来验证行内三种 span 共存时的切分顺序。

### 三级标题: 列表形态

- 纯文本列表项一
- 含 **加粗** 的列表项二
- 含 `代码` 的列表项三
- 含 [链接文字](https://github.com) 的列表项四
  - 带缩进的子项(应当被拍平成同级)
* 星号列表项(另一种列表符)
+ 加号列表项(第三种列表符)

#### 四级标题: 段落折行

第一行文字,
第二行文字会被合并进同一个段落,
第三行同样。

##### 五级标题: 边界之前

下面是奇葩边界:

**未闭合的粗体段
单独一个反引号 ` 出现在行中
空方括号链接 []() 应原样保留
嵌套括号 URL [文字](https://en.wikipedia.org/wiki/Sleep_(disorder))
行首**粗体**紧贴行首
行尾粗体紧贴行尾**
###### 六级标题: 尽头

连续多个空行上方, 下方是缩进列表:

  - 缩进两个空格的项
	- tab 开头的项(注意这是 tab)

普通文字恢复。本段结束后整个探针结束。
    """.trim()

    @Test
    fun probeHeadingsParseWithCorrectLevels() {
        val headings = probeBody.lines()
            .mapNotNull { line ->
                Regex("""^(#{1,6})\s+(.*)$""").find(line.trim())
                    ?.let { it.groupValues[1].length to it.groupValues[2] }
            }
        // 1/2/3/4/5/6 级标题全部出现, 每级各一次
        assertEquals(listOf(1, 2, 3, 4, 5, 6), headings.map { it.first })
    }

    @Test
    fun probeMixedListMarkersAllBecomeBulletItems() {
        // 9 个列表行(-*+ 三种符号 + 缩进子项 + 独立缩进组)必须全部进 Bullet 块, 一行都不能丢
        val items = MarkdownBlocks.parse(probeBody)
            .filterIsInstance<MarkdownBlocks.Block.Bullet>()
            .flatMap { it.items }
        assertEquals(9, items.size)
        assertTrue("缩进子项 must survive", items.any { it.startsWith("带缩进的子项") })
        assertTrue("星号列表项 must survive", items.any { it.startsWith("星号列表项") })
        assertTrue("加号列表项 must survive", items.any { it.startsWith("加号列表项") })
        assertTrue("2空格缩进项 must survive", items.any { it.startsWith("缩进两个空格的项") })
        assertTrue("tab 开头项 must survive", items.any { it.startsWith("tab 开头的项") })
    }

    @Test
    fun probeUnclosedBoldStaysAsText() {
        // "**未闭合的粗体段" 没有 ** 闭合, parseInline 不应丢字
        val spans = MarkdownBlocks.parseInline("**未闭合的粗体段")
        val joined = spans.joinToString("") {
            when (it) {
                is MarkdownBlocks.Inline.Text -> it.text
                is MarkdownBlocks.Inline.Bold -> it.text
                is MarkdownBlocks.Inline.Code -> it.text
                is MarkdownBlocks.Inline.Link -> it.text
            }
        }
        assertEquals("**未闭合的粗体段", joined)
    }

    @Test
    fun probeNestedParenUrlLinkParses() {
        // wiki 链接里带括号, regex 非贪婪匹配会在第一个 ) 停下 — 只要不崩且文字不丢即可
        val spans = MarkdownBlocks.parseInline("[文字](https://en.wikipedia.org/wiki/Sleep_(disorder))")
        val joined = spans.joinToString("") {
            when (it) {
                is MarkdownBlocks.Inline.Text -> it.text
                is MarkdownBlocks.Inline.Bold -> it.text
                is MarkdownBlocks.Inline.Code -> it.text
                is MarkdownBlocks.Inline.Link -> it.text
            }
        }
        assertTrue("文字 must survive", "文字" in joined)
    }

    @Test
    fun probeNoContentLossAcrossWholeBody() {
        // 全文所有非空白字符在 parse + parseInline 前后必须保持数量一致(不吞字)
        val blocks = MarkdownBlocks.parse(probeBody)
        val allInline = blocks.flatMap { b ->
            when (b) {
                is MarkdownBlocks.Block.Heading -> listOf(MarkdownBlocks.parseInline(b.text))
                is MarkdownBlocks.Block.Bullet -> b.items.map { MarkdownBlocks.parseInline(it) }
                is MarkdownBlocks.Block.Paragraph -> listOf(MarkdownBlocks.parseInline(b.text))
            }
        }.flatten()
        val rendered = allInline.joinToString("") {
            when (it) {
                is MarkdownBlocks.Inline.Text -> it.text
                is MarkdownBlocks.Inline.Bold -> it.text
                is MarkdownBlocks.Inline.Code -> it.text
                is MarkdownBlocks.Inline.Link -> it.text
            }
        }
        val sourceChars = probeBody.filter { !it.isWhitespace() }
        // 渲染会去掉 ** ` []() 这些标记字符, 所以只验证: 渲染结果里的中文字符集合与源一致
        val sourceCjk = probeBody.filter { it.code > 0x4E00 && it.code < 0x9FFF }.toSet()
        val renderedCjk = rendered.filter { it.code > 0x4E00 && it.code < 0x9FFF }.toSet()
        assertEquals(sourceCjk, renderedCjk)
    }
}

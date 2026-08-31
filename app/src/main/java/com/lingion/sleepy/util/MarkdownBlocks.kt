package com.lingion.sleepy.util

/**
 * 更新日志 Markdown 解析 — 纯 Kotlin, 无 Android/Compose 依赖, 可 JVM 单测。
 *
 * 取代第三方 MarkdownText 黑盒: 上一版用 compose-markdown(Markwon) 渲染,
 * 真机上标题/列表/粗体与普通正文无视觉差异且行为不可控。现在解析出确定性的
 * 块结构 [Block] + 行内结构 [Inline], 由 UpdateChangelogDialog 用 Compose
 * 显式排版 — 每种块的实际视觉结果由本仓库代码决定, 不依赖第三方隐式行为。
 *
 * 覆盖 release notes 实际用到的语法: # 标题、- 列表、**粗体**、`代码`、
 * [文字](链接)、普通段落。其余 Markdown 语法按普通文本原样显示(安全兜底)。
 */
object MarkdownBlocks {

    /** 块级元素 */
    sealed class Block {
        /** ATX 标题, level 1-6 */
        data class Heading(val level: Int, val text: String) : Block()
        /** 连续列表项合为一组 */
        data class Bullet(val items: List<String>) : Block()
        /** 普通段落(可能多行, 已合并为单串) */
        data class Paragraph(val text: String) : Block()
    }

    /** 行内元素 */
    sealed class Inline {
        data class Text(val text: String) : Inline()
        data class Bold(val text: String) : Inline()
        data class Code(val text: String) : Inline()
        data class Link(val text: String, val url: String) : Inline()
    }

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        var listItems = mutableListOf<String>()
        var paragraphLines = mutableListOf<String>()

        fun flushList() {
            if (listItems.isNotEmpty()) {
                blocks += Block.Bullet(listItems.toList())
                listItems = mutableListOf()
            }
        }
        fun flushParagraph() {
            if (paragraphLines.isNotEmpty()) {
                blocks += Block.Paragraph(paragraphLines.joinToString(" ").trim())
                paragraphLines = mutableListOf()
            }
        }
        fun flushAll() { flushList(); flushParagraph() }

        for (rawLine in markdown.lines()) {
            val line = rawLine.trimEnd()
            val t = line.trim()
            when {
                // 空行 = 块边界
                t.isEmpty() -> flushAll()
                // ATX 标题: #..###### + 空格 + 内容
                Regex("""^(#{1,6})\s+(.*)$""").matches(t) -> {
                    flushAll()
                    val m = Regex("""^(#{1,6})\s+(.*)$""").find(t)!!
                    blocks += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }
                // 列表项: - / * / + + 空格; 容忍嵌套缩进(拍平)
                Regex("""^[-*+]\s+(.*)$""").matches(t) -> {
                    flushParagraph()
                    listItems += t.removePrefix("-").removePrefix("*").removePrefix("+").trim()
                }
                else -> {
                    flushList()
                    paragraphLines += t
                }
            }
        }
        flushAll()
        return blocks
    }

    /** 行内解析: **粗体**、`代码`、[文字](链接) → 有序 span 列表 */
    fun parseInline(text: String): List<Inline> {
        val spans = mutableListOf<Inline>()
        // 一趟扫描, 三种模式按出现位置取最早
        val patterns = listOf(
            Regex("""\*\*(.+?)\*\*"""),                       // bold
            Regex("""`([^`]+)`"""),                            // code
            Regex("""\[([^\]]+)]\(([^)]+)\)""")                // link
        )
        var i = 0
        while (i < text.length) {
            var bestMatch: MatchResult? = null
            var bestKind = -1
            for ((kind, p) in patterns.withIndex()) {
                val m = p.find(text, i)
                if (m != null && (bestMatch == null || m.range.first < bestMatch.range.first)) {
                    bestMatch = m; bestKind = kind
                }
            }
            if (bestMatch == null) {
                spans += Inline.Text(text.substring(i))
                break
            }
            if (bestMatch.range.first > i) {
                spans += Inline.Text(text.substring(i, bestMatch.range.first))
            }
            when (bestKind) {
                0 -> spans += Inline.Bold(bestMatch.groupValues[1])
                1 -> spans += Inline.Code(bestMatch.groupValues[1])
                else -> spans += Inline.Link(bestMatch.groupValues[1], bestMatch.groupValues[2])
            }
            i = bestMatch.range.last + 1
        }
        return spans.filter { it !is Inline.Text || it.text.isNotEmpty() }
    }
}

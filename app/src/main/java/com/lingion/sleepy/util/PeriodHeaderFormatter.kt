package com.lingion.sleepy.util

/** Formats the configurable left-hand period header without changing course data. */
object PeriodHeaderFormatter {
    private val chinese = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四", "十五", "十六")
    private val financial = listOf("壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾", "拾壹", "拾贰", "拾叁", "拾肆", "拾伍", "拾陆")
    private val circled = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩", "⑪", "⑫", "⑬", "⑭", "⑮", "⑯")

    fun label(node: Int, style: String): String = when (style) {
        "chinese" -> chinese.getOrNull(node - 1) ?: node.toString()
        "financial" -> financial.getOrNull(node - 1) ?: node.toString()
        "circled" -> circled.getOrNull(node - 1) ?: node.toString()
        "roman" -> roman(node)
        else -> node.toString()
    }

    fun range(start: Int, end: Int, style: String): String {
        if (start == end) return label(start, style)
        val separator = if (style == "arabic") "-" else "-"
        val suffix = if (style == "arabic") "节" else ""
        return "${label(start, style)}$separator${label(end, style)}$suffix"
    }

    fun fullLabel(node: Int, style: String): String = "第${label(node, style)}节"

    private fun roman(number: Int): String {
        val compact = listOf("", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ", "Ⅺ", "Ⅻ")
        if (number in 1..12) return compact[number]
        if (number !in 1..39) return number.toString()
        val values = listOf(10 to "Ⅹ", 9 to "Ⅸ", 5 to "Ⅴ", 4 to "Ⅳ", 1 to "Ⅰ")
        var remaining = number
        return buildString {
            for ((value, glyph) in values) while (remaining >= value) {
                append(glyph)
                remaining -= value
            }
        }
    }
}

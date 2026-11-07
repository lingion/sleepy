package com.lingion.sleepy.data.jw

/** Explicit week lists for declared school layouts; unknown text invalidates the whole arrangement. */
internal fun schoolWeekRanges(raw: String, trailingParityAppliesToAll: Boolean = false): List<Triple<Int, Int, Int>> {
    var text = raw.trim().replace('（', '(').replace('）', ')').replace(Regex("\\s+"), "")
    if (text.isEmpty()) return emptyList()
    var globalParity = ""
    if (trailingParityAppliesToAll && text.count { it == '单' || it == '双' } == 1) {
        val suffix = Regex("""(?:\(([单双])周?\)|([单双])周?)$""").find(text)
        if (suffix != null) {
            globalParity = suffix.groupValues[1].ifEmpty { suffix.groupValues[2] }
            text = text.take(suffix.range.first)
        }
    }
    val result = mutableListOf<Triple<Int, Int, Int>>()
    for (token in text.split(',', '，')) {
        val match = Regex("""第?([0-9]+)(?:-([0-9]+))?([单双])?周?(?:\(([单双])周?\))?""")
            .matchEntire(token) ?: return emptyList()
        val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
        val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
        // Resource bound, not a claim about a school's semester length.
        if (first !in 1..366 || last !in first..366) return emptyList()
        val markers = listOf(globalParity, match.groupValues[3], match.groupValues[4]).filter { it.isNotEmpty() }.distinct()
        if (markers.size > 1) return emptyList()
        val parity = when (markers.firstOrNull()) { "单" -> 1; "双" -> 2; else -> 0 }
        result += Triple(first, last, parity)
    }
    return result.distinct()
}

internal fun schoolNodeRanges(raw: String): List<Pair<Int, Int>> {
    val nodes = sortedSetOf<Int>()
    for (token in raw.split(',', '，')) {
        val match = Regex("""([0-9]+)(?:\s*-\s*([0-9]+))?""").matchEntire(token.trim()) ?: return emptyList()
        val first = match.groupValues[1].toIntOrNull() ?: return emptyList()
        val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptyList()
        if (first !in 1..30 || last !in first..30) return emptyList()
        nodes += first..last
    }
    val result = mutableListOf<Pair<Int, Int>>()
    for (node in nodes) {
        val last = result.lastOrNull()
        if (last != null && node == last.second + 1) result[result.lastIndex] = last.first to node
        else result += node to node
    }
    return result
}

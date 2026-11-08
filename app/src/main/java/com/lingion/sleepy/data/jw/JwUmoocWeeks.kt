package com.lingion.sleepy.data.jw

/** UMOOC week expressions. Exclusions must be removed as sets before splitting included ranges. */
internal fun umoocWeeks(raw: String): List<Int> {
    val text = raw.trim().replace('（', '(').replace('）', ')').replace(Regex("\\s+"), "")
    val exclusion = Regex("""^(.*?)(?:\(除([^()]*)\)|除([^()]*))$""").matchEntire(text)
    val include = exclusion?.groupValues?.get(1) ?: text
    val exclude = exclusion?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }

    fun weekSet(expression: String, allowSourceDefault: Boolean): Set<Int>? {
        // DEX o0O 0x46a..0x524 declares 18 for these three explicit labels only.
        // Missing or unknown week text never receives this default.
        if (allowSourceDefault && expression in listOf("全周", "单周", "双周")) {
            return (1..18).filter { expression == "全周" || it % 2 == if (expression == "单周") 1 else 0 }.toSet()
        }
        val weeks = sortedSetOf<Int>()
        for (token in expression.split(',', '，', '、', '.')) {
            val match = Regex("""(?:([单双全])周?)?第?([0-9]+)(?:-([0-9]+))?([单双全])?周?(?:\(([单双全])周?\))?""")
                .matchEntire(token) ?: return null
            val first = match.groupValues[2].toIntOrNull() ?: return null
            val last = match.groupValues[3].ifEmpty { match.groupValues[2] }.toIntOrNull() ?: return null
            if (first !in 1..366 || last !in first..366) return null
            val markers = listOf(match.groupValues[1], match.groupValues[4], match.groupValues[5])
                .filter { it.isNotEmpty() }.distinct()
            if (markers.size > 1) return null
            val parity = when (markers.firstOrNull()) { "单" -> 1; "双" -> 2; else -> 0 }
            weeks += (first..last).filter { parity == 0 || it % 2 == parity % 2 }
        }
        return weeks
    }

    val included = weekSet(include, allowSourceDefault = true) ?: return emptyList()
    val excluded = if (exclude == null) emptySet() else weekSet(exclude, allowSourceDefault = false) ?: return emptyList()
    return (included - excluded).sorted()
}

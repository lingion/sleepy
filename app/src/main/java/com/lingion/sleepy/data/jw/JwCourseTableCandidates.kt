package com.lingion.sleepy.data.jw

import org.jsoup.nodes.Element

/** Keep a complete table together. Ambiguous structural fallbacks never combine different layouts. */
internal fun courseTableCandidates(
    document: Element,
    preferredSelector: String?,
    parse: (Element) -> List<JwCourse>,
): List<JwCourse> {
    val preferred = preferredSelector?.let(document::select).orEmpty().flatMap { root ->
        if (root.tagName() == "table") listOf(root) else root.select("table")
    }.distinct()
    fun read(table: Element) = runCatching { parse(table) }.getOrDefault(emptyList())
    preferred.firstNotNullOfOrNull { table -> read(table).takeIf { it.isNotEmpty() } }?.let { return it }
    val candidates = document.select("table").filter { it !in preferred }.map(::read).filter { it.isNotEmpty() }
    val first = candidates.firstOrNull() ?: return emptyList()
    val contents = first.groupingBy { it }.eachCount()
    return if (candidates.all { it.groupingBy { course -> course }.eachCount() == contents }) first else emptyList()
}

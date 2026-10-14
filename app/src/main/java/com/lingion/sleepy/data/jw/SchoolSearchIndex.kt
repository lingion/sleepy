package com.lingion.sleepy.data.jw

import com.lingion.sleepy.util.PinyinMatcher
import java.util.Locale

/** Build searchable terms once per catalogue, rather than rebuilding pinyin on every keystroke. */
class SchoolSearchIndex(private val schools: List<JwSchoolInfo>) {
    private data class Entry(val school: JwSchoolInfo, val terms: List<String>, val aliases: Set<String>)
    private val entries = schools.map { school ->
        val aliases = school.aliases.map { it.lowercase(Locale.ROOT) }.toSet()
        Entry(school, listOf(school.name, school.sortKeyFull,
            PinyinMatcher.namePinyinShort(school.name, school.sortKey))
            .map { it.lowercase(Locale.ROOT) } + aliases, aliases)
    }

    fun search(query: String): List<JwSchoolInfo> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return schools
        return entries.filter { entry -> entry.terms.any { q in it } }
            .sortedByDescending { q in it.aliases }.map { it.school }
    }
}

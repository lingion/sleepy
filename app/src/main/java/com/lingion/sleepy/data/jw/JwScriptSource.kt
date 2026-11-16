package com.lingion.sleepy.data.jw

import org.json.JSONTokener
import org.jsoup.Jsoup

/** Static script text and a same-length lexical mask. No JavaScript is executed. */
internal class JwScriptSource(source: String) {
    val text: String
    val code: String
    data class Assignment(val target: String, val operator: String, val expression: String, val start: Int)

    init {
        val raw = if (source.trimStart().startsWith('<'))
            Jsoup.parse(source).select("script").joinToString("\n") { it.data() } else source
        val plain = raw.toCharArray()
        val mask = raw.toCharArray()
        var quote: Char? = null
        var escaped = false
        var line = false
        var block = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val next = raw.getOrNull(i + 1)
            when {
                line -> { plain[i] = if (c == '\n' || c == '\r') c else ' '; mask[i] = plain[i]; if (c == '\n' || c == '\r') line = false }
                block -> {
                    plain[i] = if (c == '\n' || c == '\r') c else ' '; mask[i] = plain[i]
                    if (c == '*' && next == '/') { plain[i + 1] = ' '; mask[++i] = ' '; block = false }
                }
                quote != null -> {
                    mask[i] = ' '
                    if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == quote) quote = null
                }
                c == '/' && (next == '/' || next == '*') -> {
                    line = next == '/'; block = next == '*'
                    plain[i] = ' '; mask[i] = ' '; plain[i + 1] = ' '; mask[++i] = ' '
                }
                c == '\'' || c == '"' || c == '`' -> { quote = c; mask[i] = ' ' }
            }
            i++
        }
        text = String(plain)
        code = String(mask)
    }

    fun matches(regex: Regex): Sequence<MatchResult> = regex.findAll(text)
        .filter { code[it.range.first] == text[it.range.first] && !code[it.range.first].isWhitespace() }

    /** Balanced argument/array contents, including nested arrays, objects and calls. */
    fun arguments(open: Int): Pair<List<String>, Int>? {
        val stack = ArrayDeque<Char>()
        val values = mutableListOf<String>()
        var start = open + 1
        for (i in open until code.length) {
            when (val c = code[i]) {
                '(', '[', '{' -> { stack.addLast(c); if (stack.size > 64) return null }
                ')', ']', '}' -> {
                    val expected = when(c) { ')' -> '('; ']' -> '['; else -> '{' }
                    if (stack.removeLastOrNull() != expected) return null
                    if (stack.isEmpty()) {
                        if (i > start || values.isNotEmpty()) values += text.substring(start, i).trim()
                        return values to i
                    }
                }
                ',' -> if (stack.size == 1) { values += text.substring(start, i).trim(); start = i + 1 }
            }
        }
        return null
    }

    val assignments: List<Assignment> by lazy {
        matches(Regex("""[\w$]+(?:\.[\w$]+)*\s*(\+=|=(?!=|>))\s*""")).map { match ->
            val operator = match.groupValues[1]
            val target = match.value.substringBefore(operator).trim()
            val start = match.range.last + 1
            var end = start
            var depth = 0
            while (end < code.length) {
                val c = code[end]
                if (depth == 0 && (c == ';' || c == '\n' || c == '\r')) break
                if (c in "([{") depth++ else if (c in ")]}") { if (depth == 0) break; depth-- }
                end++
            }
            Assignment(target, operator, text.substring(start, end).trim(), match.range.first)
        }.toList()
    }

    fun string(expression: String): String? = expression.trim().takeIf {
        Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""").matches(it)
    }?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }

    fun resolveString(binding: String, before: Int, until: Int): String? {
        val key = binding.removePrefix("this.")
        val values = assignments.filter { it.target == key || it.target == binding }
        val preceding = values.filter { it.start < before }
        val selected = if (preceding.isNotEmpty()) preceding else values.filter { it.start in before until until }
        var result: String? = null
        for (item in selected) {
            val value = string(item.expression) ?: continue
            result = if (item.operator == "+=") result?.plus(value) else value
        }
        return result
    }
}

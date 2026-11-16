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
        val masked = JwScriptMask.read(raw)
        text = masked.first
        code = masked.second
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
        matches(Regex("""[\w$]+(?:\s*\.\s*[\w$]+)*\s*(\+=|=(?!=|>))\s*""")).map { match ->
            val operator = match.groupValues[1]
            val target = match.value.substringBefore(operator).replace(Regex("\\s+"), "")
            val start = match.range.last + 1
            var end = start
            var depth = 0
            while (end < code.length) {
                val c = code[end]
                if (depth == 0 && (c == ';' || c == ',')) break
                if (depth == 0 && (c == '\n' || c == '\r')) {
                    var left = end - 1
                    while (left >= start && code[left].isWhitespace()) left--
                    var right = end + 1
                    while (right < code.length && code[right].isWhitespace()) right++
                    val previous = code.getOrNull(left)?.takeIf { left >= start }
                    val next = code.getOrNull(right)
                    // A line terminator after an operator or before a member/call is
                    // part of the initializer. Completed statements may omit ';'.
                    val continued = (previous != null && previous in "+-*/%=?:&|^!~<>") ||
                        (next != null && next in ".([+-*/%?:&|^<>")
                    if (!continued) break
                }
                if (c in "([{") depth++ else if (c in ")]}") { if (depth == 0) break; depth-- }
                end++
            }
            Assignment(target, operator, text.substring(start, end).trim(), match.range.first)
        }.toList()
    }

    /** Bind marshal parameters to the table that actually receives activities. */
    fun marshalArguments(): List<Int>? {
        val binding = "[\\w$]+(?:\\s*\\.\\s*[\\w$]+)*"
        fun compact(value: String) = value.replace(Regex("\\s+"), "")
        val receivers = matches(Regex("($binding)\\s*\\.\\s*activities\\s*\\["))
            .map { compact(it.groupValues[1]) }.toSet()
        return matches(Regex("($binding)\\s*\\.\\s*marshalTable\\s*\\("))
            .filter { receivers.isEmpty() || compact(it.groupValues[1]) in receivers }
            .mapNotNull { match -> arguments(match.range.last)?.first?.takeIf { it.size == 3 }
                ?.map { it.toIntOrNull() }?.takeIf { values -> values.all { it != null } }?.filterNotNull() }
            .distinct().toList().singleOrNull()
    }

    fun string(expression: String): String? = staticString(expression, emptyMap())

    private fun key(value: String) = value.replace(Regex("\\s+"), "").removePrefix("this.")

    /** Only string literals, snapshots of bindings, parentheses and string concatenation. */
    private fun staticString(expression: String, bindings: Map<String, String>): String? {
        var index = 0
        fun spaces() { while (index < expression.length && expression[index].isWhitespace()) index++ }
        fun read(depth: Int): String? {
            if (depth > 64) return null
            val result = StringBuilder()
            while (true) {
                spaces()
                val value = when (val ch = expression.getOrNull(index)) {
                    '\'', '"' -> {
                        val start = index++
                        while (index < expression.length) {
                            if (expression[index] == '\\') { index = minOf(index + 2, expression.length); continue }
                            if (expression[index++] == ch) break
                        }
                        val literal = expression.substring(start, index)
                        if (literal.length < 2 || literal.last() != ch) return null
                        runCatching { JSONTokener(literal).nextValue() as? String }.getOrNull()
                    }
                    '(' -> {
                        index++
                        val nested = read(depth + 1) ?: return null
                        spaces()
                        if (expression.getOrNull(index++) != ')') return null
                        nested
                    }
                    else -> {
                        val match = Regex("""[\w$]+(?:\s*\.\s*[\w$]+)*""").find(expression, index)
                            ?.takeIf { it.range.first == index } ?: return null
                        index = match.range.last + 1
                        bindings[key(match.value)]
                    }
                } ?: return null
                if (result.length.toLong() + value.length > 1_048_576) return null
                result.append(value)
                spaces()
                if (expression.getOrNull(index) != '+') return result.toString()
                index++
            }
        }
        val result = read(0) ?: return null
        spaces()
        return result.takeIf { index == expression.length }
    }

    fun resolveString(binding: String, before: Int, until: Int): String? {
        val name = key(binding)
        val deferred = Regex("""[\w$]+(?:\s*\.\s*[\w$]+)*""").matches(binding.trim()) &&
            assignments.none { key(it.target) == name && it.start < before }
        val limit = if (deferred) until else before
        val values = mutableMapOf<String, String>()
        var expanded = 0L
        for (item in assignments) {
            if (item.start >= limit) break
            val target = key(item.target)
            val value = staticString(item.expression, values)
            val result = if (item.operator == "+=") values[target]?.let { previous ->
                value?.takeIf { previous.length.toLong() + it.length <= 1_048_576 }?.let(previous::plus)
            } else value
            if (result == null) values.remove(target) else {
                expanded += result.length
                if (expanded > 1_048_576) return null
                values[target] = result
            }
        }
        return staticString(binding, values)
    }
}

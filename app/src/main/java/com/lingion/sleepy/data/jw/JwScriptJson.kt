package com.lingion.sleepy.data.jw

import org.json.JSONTokener
import org.jsoup.Jsoup

/** Read static JSON literals by schema, without executing scripts or depending on variable names. */
internal object JwScriptJson {
    data class Literal(val json: String, val value: Any, val binding: String?)
    private val bindingPattern = Regex("""([\w$]+(?:\.[\w$]+)*)\s*=\s*$""")

    fun find(source: String, accepts: (Literal) -> Boolean): Literal? {
        val scripts = if (source.trimStart().startsWith('<')) {
            Jsoup.parse(source).select("script").map { it.data() }
        } else listOf(source)
        for (script in scripts) {
            var parsedChars = 0L
            for ((start, end) in ranges(script).sortedBy { it.first }) {
                val length = end - start + 1
                if (parsedChars + length > script.length.toLong() * 3) continue
                parsedChars += length
                val json = script.substring(start, end + 1)
                val value = runCatching { JSONTokener(json).nextValue() }.getOrNull() ?: continue
                val prefix = script.substring(maxOf(0, start - 256), start)
                val binding = bindingPattern.find(prefix)?.groupValues?.get(1)
                val literal = Literal(json, value, binding)
                if (accepts(literal)) return literal
            }
        }
        return null
    }

    private fun ranges(script: String): List<Pair<Int, Int>> {
        val code = JwScriptMask.read(script).second
        val result = mutableListOf<Pair<Int, Int>>()
        val pending = mutableListOf<Pair<Int, Int>>()
        val stack = ArrayDeque<Pair<Int, Char>>()
        var tooDeep = false
        var index = 0
        while (index < script.length) {
            val ch = code[index]
            when {
                ch == '[' || ch == '{' -> {
                    stack.addLast(index to ch)
                    if (stack.size > 64) tooDeep = true
                }
                (ch == ']' || ch == '}') && stack.isNotEmpty() -> {
                    val (start, open) = stack.removeLast()
                    if ((open == '[') != (ch == ']')) tooDeep = true
                    if (!tooDeep) pending += start to index
                    if (stack.isEmpty()) {
                        if (!tooDeep) result += pending
                        pending.clear()
                        tooDeep = false
                    }
                }
            }
            index++
        }
        return result
    }
}

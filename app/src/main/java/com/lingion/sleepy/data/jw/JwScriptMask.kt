package com.lingion.sleepy.data.jw

/** Preserve offsets while hiding comments, strings and regexes from static data readers. */
internal object JwScriptMask {
    fun read(raw: String): Pair<String, String> {
        val plain = raw.toCharArray()
        val code = raw.toCharArray()
        val controls = ArrayDeque<Boolean>()
        val objects = ArrayDeque<Boolean>()
        var operand = true
        var token = ""
        var i = 0
        fun hide(start: Int, end: Int, data: Boolean) {
            for (n in start until end) if (raw[n] != '\n' && raw[n] != '\r') {
                code[n] = ' '
                if (data) plain[n] = ' '
            }
        }
        while (i < raw.length) {
            val c = raw[i]
            val next = raw.getOrNull(i + 1)
            when {
                c.isWhitespace() -> i++
                c == '/' && next == '/' -> {
                    val start = i
                    while (i < raw.length && raw[i] !in "\r\n") i++
                    hide(start, i, true)
                }
                c == '/' && next == '*' -> {
                    val start = i
                    val close = raw.indexOf("*/", i + 2)
                    i = if (close < 0) raw.length else close + 2
                    hide(start, i, true)
                }
                c == '\'' || c == '"' || c == '`' -> {
                    val start = i++
                    while (i < raw.length) {
                        if (raw[i] == '\\') { i = minOf(i + 2, raw.length); continue }
                        if (raw[i++] == c) break
                    }
                    hide(start, i, false)
                    operand = false
                    token = "literal"
                }
                c == '/' && operand && next != '=' -> {
                    val start = i++
                    var characterClass = false
                    while (i < raw.length && raw[i] !in "\r\n") {
                        val ch = raw[i++]
                        if (ch == '\\') { if (i < raw.length) i++; continue }
                        if (ch == '[') characterClass = true
                        if (ch == ']') characterClass = false
                        if (ch == '/' && !characterClass) break
                    }
                    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] in "_$")) i++
                    hide(start, i, true)
                    operand = false
                    token = "literal"
                }
                c.isLetter() || c in "_$" -> {
                    val start = i++
                    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] in "_$")) i++
                    val member = token == "."
                    token = raw.substring(start, i)
                    operand = !member && token in setOf("return", "throw", "case", "delete", "void", "typeof", "new", "in", "instanceof", "yield", "await", "else", "do")
                }
                c.isDigit() -> {
                    i++
                    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] in "._")) i++
                    token = "literal"
                    operand = false
                }
                else -> {
                    when (c) {
                        '(' -> { controls.addLast(token in setOf("if", "while", "for", "with", "switch", "catch")); operand = true }
                        ')' -> operand = controls.removeLastOrNull() == true
                        '{' -> { objects.addLast(operand && token !in setOf("", ";", ")", "=>", "else", "try", "finally", "do")); operand = true }
                        '}' -> operand = objects.removeLastOrNull() != true
                        ']' -> operand = false
                        '.' -> operand = false
                        '+', '-' -> if (next == c) { i++; operand = false } else operand = true
                        else -> operand = true
                    }
                    token = if (c == '=' && next == '>') { i++; operand = true; "=>" } else c.toString()
                    i++
                }
            }
        }
        return String(plain) to String(code)
    }
}

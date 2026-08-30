package com.lingion.sleepy.data.jw

import org.json.JSONArray

/**
 * 青果/乘方教务（CF）解析器 — T3。
 *
 * 协议：JwProtocol.TYPE_CF = "cf"
 * 上游：dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) ChengFangParser.kt
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/ChengFangParser.kt
 *
 * 页面结构：HTML 内嵌 `var kbxx = [{kcmc,teaxms,jxcdmcs,xq,jcdm2,zcs},...]`（通常跨多行 script）。
 * 字段语义（全 String）：
 *   kcmc=课名  teaxms=教师（整体保留不拆分）  jxcdmcs=教室
 *   xq=星期(1-7 数字串)  jcdm2=节次代码("1,2"/"05,06"/"11")  zcs=周次("1,3,5")
 *
 * 对上游的有意偏离（fixture 实证）：
 *   ① substringBefore(';') → 括号配对提取（教室值内含分号/转义引号时不截断）
 *   ② zcs toInt() → toIntOrNull()（空周次跳过该条而非 NumberFormatException）
 */
class JwChengFangParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        val json = extractKbxxJson(source) ?: return result
        val arr = try { JSONArray(json) } catch (e: Exception) { return result }

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("kcmc", "").trim()
            if (name.isBlank()) continue
            val teacher = o.optString("teaxms", "").trim()
            val room = o.optString("jxcdmcs", "").trim()
            val day = o.optString("xq", "").trim().toIntOrNull() ?: continue

            val jcdm2 = o.optString("jcdm2", "").trim()
            if (jcdm2.isBlank()) continue
            val nodes = jcdm2.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (nodes.isEmpty()) continue
            val startNode = nodes.first()
            val step = nodes.last() - startNode + 1

            val zcs = o.optString("zcs", "").trim()
            if (zcs.isBlank()) continue
            val weekList = zcs.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (weekList.isEmpty()) continue

            for (wb in weekIntList2WeekBeanList(weekList)) {
                result += JwCourse(
                    name = name, room = room, teacher = teacher,
                    day = day.coerceIn(1, 7),
                    startNode = startNode.coerceAtLeast(1),
                    endNode = (startNode + step - 1).coerceAtLeast(startNode),
                    startWeek = wb.first.coerceAtLeast(1),
                    endWeek = wb.second.coerceAtLeast(wb.first),
                    type = wb.third
                )
            }
        }
        return result
    }

    companion object {
        /**
         * 从 HTML 里抠 `var kbxx = [...]`。
         * 字符串感知的括号配对，找不到标记 / 找不到 '[' / 括号不平衡 → null。
         */
        fun extractKbxxJsonForTest(html: String): String? = extractKbxxJson(html)

        private fun extractKbxxJson(html: String): String? {
            val marker = "var kbxx"
            val idx = html.indexOf(marker)
            if (idx < 0) return null
            var arrStart = idx + marker.length
            while (arrStart < html.length && html[arrStart] != '[') arrStart++
            if (arrStart >= html.length) return null

            var depth = 0
            var inStr = false
            var esc = false
            for (i in arrStart until html.length) {
                val c = html[i]
                if (esc) { esc = false; continue }
                if (c == '\\') { esc = true; continue }
                if (c == '"') { inStr = !inStr; continue }
                if (inStr) continue
                when (c) {
                    '[' -> depth++
                    ']' -> { depth--; if (depth == 0) return html.substring(arrStart, i + 1) }
                }
            }
            return null
        }

        internal fun weekIntList2WeekBeanList(input: List<Int>): List<Triple<Int, Int, Int>> {
            if (input.isEmpty()) return emptyList()
            val a = input.sorted()
            var reset = 0
            var start = 0; var end = 0; var type = -1
            val list = ArrayList<Triple<Int, Int, Int>>()
            fun flush() { list.add(Triple(start, end, type)); type = -1; reset = 0 }
            for (i in a.indices) {
                if (reset == 1) flush()
                if (i < a.size - 1) {
                    val gap = a[i + 1] - a[i]
                    if (type == -1) {
                        start = a[i]
                        when (gap) {
                            1 -> { type = 0; end = a[i + 1] }
                            2 -> { type = if (a[i] % 2 != 0) 1 else 2; end = a[i + 1] }
                            else -> { end = a[i]; type = 0; reset = 1 }
                        }
                    } else {
                        when {
                            type == 0 && gap == 1 -> end = a[i + 1]
                            (type == 1 || type == 2) && gap == 2 -> end = a[i + 1]
                            else -> reset = 1
                        }
                    }
                }
                if (i == a.size - 1) {
                    if (type == -1) { start = a[i]; end = a[i]; type = 0 }
                    list.add(Triple(start, end, type))
                }
            }
            return list
        }
    }

    /** T8: var kbxx + CF 字段四件套 = 100; 仅 var kbxx = 70 */
    override fun confidence(): Int {
        if (!source.contains("var kbxx")) return 0
        val hasFields = source.contains("kcmc") && (source.contains("teaxms") ||
            source.contains("jxcdmcs") || source.contains("jcdm2"))
        return if (hasFields) 100 else 70
    }

    override fun matchedFeatures(): List<String> {
        if (!source.contains("var kbxx")) return emptyList()
        return buildList {
            add("var kbxx")
            if (source.contains("kcmc")) add("字段=kcmc")
            if (source.contains("teaxms")) add("字段=teaxms")
            if (source.contains("jxcdmcs")) add("字段=jxcdmcs")
            if (source.contains("jcdm2")) add("字段=jcdm2")
        }
    }
}

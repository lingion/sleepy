// ECUPL script sub-protocol parser — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 华东政法大学 (ECUPL) script 子协议解析器。
 *
 * 协议层: WakeUp `o00000O0` 1:1 对应 — `<script language=JavaScript>` 内嵌
 * `new CourseTable(...)` + `newActivity(...)` + `addActivityByTime(...)` 三件套。
 *
 * 协议指纹 3 件套:
 *  - **R5** `new CourseTable\('([-\d]+?)',\[([\d\[\],\s]+)]\)`
 *  - **R6** `\[(\d+),(\d+)]`
 *  - **R7** `newActivity\("...";"...";...;"...;[-\d]+";\d+\);[\s\S]+?addActivityByTime\(activity,\d,\d,\d+\)`
 *
 * 上游协议形态 (5 仓致谢):
 *  - LonelyMarch/OpenWakeUp EcuplParser.kt (AGPL-3.0, 主参考)
 *  - YZune/WakeUpSchedule ECUPLSuperParser.smali (Apache-2.0, smali 616 行)
 *  - ershiyidian/WakeUp_SHU smali dump (INDIRECT)
 *  - zimo0o0omiz/wisedu-unified-login-api (MIT, INDIRECT)
 *  - CreamPig233/neu_wisedu2wakeup (无 LICENSE, INDIRECT)
 */
class JwEcuplScriptParser(source: String) : JwParser(source) {

    companion object {
        // ---- 协议指纹 R5/R6/R7 char-by-char 锁定 ----

        /**
         * R5 — CourseTable 起始日 + 时间矩阵
         * 允许空白/换行在参数间 (WakeUp smali 调用常跨多行)
         */
        val RE_COURSE_TABLE: Regex = Regex(
            """new CourseTable\(\s*'([-\d]+?)'\s*,\s*\[([\d\[\],\s]+)]\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** R6 — 时间矩阵 [start_min, end_min] */
        val RE_TIME_PAIR: Regex = Regex("""\[(\d+),(\d+)]""")

        /**
         * R7 — newActivity + addActivityByTime 配对
         * 跨行, 允许空白在 tokens 间
         * 兼容 5 参数形态 (WakeUp ECUPL: name, teacher, room, weekBitmap, id)
         * 与 8 参数形态 (经典 EAMS/TaskActivity 变体: code, name, dummy, teacher, dummy2, room, weekBitmap, id)
         */
        val RE_NEW_ACTIVITY_5: Regex = Regex(
            """newActivity\(\s*"(.*?)"\s*,\s*"(.*?)"\s*,\s*"(.*?)"\s*,\s*"([-\d]+)"\s*,\s*(\d+)\s*\);[\s\S]+?addActivityByTime\(\s*activity\s*,\s*(\d)\s*,\s*(\d)\s*,\s*(\d+)\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        val RE_NEW_ACTIVITY_8: Regex = Regex(
            """newActivity\(\s*".*?"\s*,\s*"(.*?)"\s*,\s*".+?"\s*,\s*"(.+?)"\s*,\s*".*?"\s*,\s*"(.*?)"\s*,\s*"([-\d]+)"\s*,\s*(\d+)\s*\);[\s\S]+?addActivityByTime\(\s*activity\s*,\s*(\d)\s*,\s*(\d)\s*,\s*(\d+)\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        val RE_NEW_ACTIVITY: Regex = Regex(
            """newActivity\(\s*(?:"(.*?)"\s*,\s*"(.*?)"\s*,\s*"(.*?)"\s*,\s*"([-\d]+)"\s*,\s*(\d+)|".*?"\s*,\s*"(.*?)"\s*,\s*".+?"\s*,\s*"(.+?)"\s*,\s*".*?"\s*,\s*"(.*?)"\s*,\s*"([-\d]+)"\s*,\s*(\d+))\s*\);[\s\S]+?addActivityByTime\(\s*activity\s*,\s*(\d)\s*,\s*(\d)\s*,\s*(\d+)\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        const val DEFAULT_WEEK_COUNT: Int = 20
    }

    override fun generateCourseList(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()

        // 协议族验证
        val hasCourseTable = RE_COURSE_TABLE.containsMatchIn(source)
        val timePairMatches = RE_TIME_PAIR.findAll(source).toList()
        if (!hasCourseTable && timePairMatches.isEmpty()) return out

        val timePairs = timePairMatches.map { m ->
            val startMin = m.groupValues[1].toInt()
            val endMin = m.groupValues[2].toInt()
            startMin to endMin
        }

        val matches = RE_NEW_ACTIVITY.findAll(source).toList()
        for (m in matches) {
            val is5Arg = m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty()
            val name = (if (is5Arg) m.groupValues[1] else m.groupValues[6]).trim()
            val teacher = (if (is5Arg) m.groupValues[2] else m.groupValues[7]).trim()
            val room = (if (is5Arg) m.groupValues[3] else m.groupValues[8]).trim()
            val weekBitmap = (if (is5Arg) m.groupValues[4] else m.groupValues[9]).trim()
            val day = m.groupValues[11].toIntOrNull() ?: continue
            val startNode = m.groupValues[12].toIntOrNull() ?: continue
            val endNode = m.groupValues[13].toIntOrNull() ?: startNode

            if (name.isBlank() || day !in 1..7 || startNode < 1 || endNode < startNode) continue

            // 周次 bitmap 拆段
            val (sw, ew, type) = parseWeekBitmap(weekBitmap)
            out += JwCourse(
                name = name,
                room = room,
                teacher = teacher,
                day = day,
                startNode = startNode,
                endNode = endNode,
                startWeek = sw,
                endWeek = ew,
                type = type,
            )
        }
        return out
    }

    internal fun parseWeekBitmap(bitmap: String): Triple<Int, Int, Int> {
        val clean = bitmap.trim()
        val rangeMatch = Regex("""(\d+)\s*[-~]\s*(\d+)\s*[（(]?(单|双)?[）)]?""").find(clean)
        if (rangeMatch != null) {
            val sw = rangeMatch.groupValues[1].toInt()
            val ew = rangeMatch.groupValues[2].toInt()
            val parity = rangeMatch.groupValues[3]
            val type = when (parity) {
                "单" -> 1
                "双" -> 2
                else -> 0
            }
            return Triple(sw, ew, type)
        }
        // bitmap 形态 fallback: 既兼容 "1111111111111111" (连续 0/1 串) 也兼容 "1-1-1" / "1-16"
        val segments = bitmap.split('-').map { it.trim() }.filter { it.isNotEmpty() }
        val weeks = mutableListOf<Int>()
        if (segments.size > 1) {
            for ((i, seg) in segments.withIndex()) {
                if (seg == "1") weeks += i + 1
            }
        } else if (clean.all { it == '0' || it == '1' }) {
            for ((i, ch) in clean.withIndex()) {
                if (ch == '1') weeks += i + 1
            }
        }
        if (weeks.isEmpty()) return Triple(1, DEFAULT_WEEK_COUNT, 0)
        val isOdd = weeks.all { it % 2 == 1 }
        val isEven = weeks.all { it % 2 == 0 }
        return when {
            isOdd && weeks.size > 1 -> Triple(weeks.first(), weeks.last(), 1)
            isEven && weeks.size > 1 -> Triple(weeks.first(), weeks.last(), 2)
            else -> Triple(weeks.first(), weeks.last(), 0)
        }
    }

    override fun confidence(): Int {
        var hits = 0
        if (RE_COURSE_TABLE.containsMatchIn(source)) hits++
        val timePairs = RE_TIME_PAIR.findAll(source).count()
        if (timePairs >= 1) hits++
        if (RE_NEW_ACTIVITY.containsMatchIn(source)) hits++
        if (source.contains("<script") && source.contains("language=\"JavaScript\"")) hits++
        return when {
            hits >= 4 -> 95
            hits >= 3 -> 80
            hits >= 2 -> 50
            hits >= 1 -> 20
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (RE_COURSE_TABLE.containsMatchIn(source)) add("ecupl-script:r5-newCourseTable")
        val timePairs = RE_TIME_PAIR.findAll(source).count()
        if (timePairs >= 1) add("ecupl-script:r6-timePairs($timePairs)")
        if (RE_NEW_ACTIVITY.containsMatchIn(source)) add("ecupl-script:r7-newActivity")
        if (source.contains("<script") && source.contains("language=\"JavaScript\"")) {
            add("ecupl-script:script-language-javascript")
        }
        if (isEmpty()) add("guard:no-ecupl-script-markers")
    }
}
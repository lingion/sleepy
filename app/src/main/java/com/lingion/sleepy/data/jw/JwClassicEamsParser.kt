package com.lingion.sleepy.data.jw

/**
 * 经典金智/树维 EAMS (`courseTableForStd!courseTable.action` 系列) 课表解析器。
 *
 * 适配学校 (2026-09 211 批量收录 B1 档): 电子科技大学 eams.uestc.edu.cn /
 * 上海财经大学 eams.sufe.edu.cn / 湖南师范大学 jwglnew.hunnu.edu.cn /
 * 南京航空航天大学 aao-eas.nuaa.edu.cn (南航走 courseTableStudent!* 入口,
 * 页面内嵌 TaskActivity 结构同款)。
 *
 * 页面结构 (与强智/正方 HTML 表格完全不同): 课表数据在页面内嵌 JS 块 —
 *   var table0 = new CourseTable(2019,84);
 *   var unitCount = 12;
 *   var actTeachers = [{id:7396,name:"张三",lab:true}];
 *   activity = new TaskActivity(教师ID, 教师名, 课号, 课名, roomId, 教室, 周次位图, ...);
 *   index = D*unitCount+P;                     // D=0基星期 P=0基节次
 *   table0.activities[index][...] = activity;
 *   table0.marshalTable(2,1,21);
 * HTML 表格 `#manualArrangeCourseTable` 是空壳 (JS 端 fillTable 渲染), DOM 无数据。
 *
 * 关键规则 (调研 classic-eams.md 多源交叉验证):
 *   - 周次位图 01 串, 下标 0 是占位符, 下标 i=1 即第 i 周 (勿 +1);
 *     单双周由位图奇偶天然表达, 稀疏周展开成逐周行 (type 按 span 奇偶压缩)
 *   - index = D*unitCount+P → day=D+1, node=P+1; unitCount 从页面抠 (禁写死:
 *     电子科大/天大/安工程 12, 湖南师大 13, 河南理工 11); 兼容 `index =42;`
 *     纯数字形态 (day = i/unitCount, node = i%unitCount, 均 +1)
 *   - 教师 [1] 可能是 actTeacherName.join(',') 表达式 → 块起点前 2500 字符内
 *     反查最近的 var actTeachers = [...] 抠 name (hpu.js 形态)
 *   - 参数切分用带引号/括号深度状态机 (课名可含逗号), 禁 naive split(',')
 *   - 异常防护: 教师 = "-1" 跳块 (停课标记); 教室 = "停课" 不输出;
 *     上财 2026-08 起第 4 参是 this.courseNameLessonNo 表达式 → 块后反查 var 值
 *
 * 算法形态参考: shiguang_warehouse (MIT) hunnu.js/uestc.js/hpu.js +
 * WakeupSchedule_Kotlin (Apache-2.0) ImportViewModel.kt; 代码自写。
 */
class JwClassicEamsParser(source: String) : JwParser(source) {

    /** 一条 TaskActivity 记录 (块级, 尚未按 index/位图展开) */
    private data class Task(
        val teacher: String,
        val name: String,
        val room: String,
        val weeks: List<Int>,
        val blockStart: Int,   // 块在 source 中的起点 (actTeachers 反查窗口)
        val nameExpr: String?, // 第 4 参为表达式时记原式 (courseNameLessonNo)
    )

    override fun generateCourseList(): List<JwCourse> {
        val unitCount = unitCountFromPage() ?: return emptyList()
        val out = mutableListOf<JwCourse>()

        for ((task, indexPairs) in blocks(unitCount)) {
            if (task.teacher == "-1" || task.teacher.isBlank()) continue // 停课/异常排课标记 (WakeupSchedule 经验)
            if (task.room == "停课") continue          // 电子科大: 停课教室扣整条
            val resolvedName = resolveCourseName(task)
            if (resolvedName.isBlank()) continue

            // (day, startNode, endNode) 连堂合并 + 每周展开 → 与强智位图语义对齐
            val (day, startNode, endNode) = mergeConsecutiveNodes(indexPairs)
            for (week in task.weeks) {
                out += JwCourse(
                    name = resolvedName,
                    room = task.room,
                    teacher = task.teacher,
                    day = day,
                    startNode = startNode,
                    endNode = endNode,
                    startWeek = week,
                    endWeek = week,
                    type = 0,
                )
            }
        }
        return out
    }

    /** 页面 var unitCount = N; — 拿不到返回 null (硬失败, 别猜默认值) */
    private fun unitCountFromPage(): Int? =
        Regex("""\bvar\s+unitCount\s*=\s*(\d+)\s*;""").find(source)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 切出每课块并解析 TaskActivity 参数 + 后随 index 赋值。
     * 块起点 = new TaskActivity( 出现处; 终点 = 本块的 index 赋值段结束
     * (下一个 var teachers/var actTeachers 或 marshalTable 之前)。
     */
    private fun blocks(unitCount: Int): List<Pair<Task, List<Pair<Int, Int>>>> {
        val result = mutableListOf<Pair<Task, List<Pair<Int, Int>>>>()
        val actRe = Regex("""new\s+TaskActivity\(""")
        val matches = actRe.findAll(source).toList()

        for ((i, m) in matches.withIndex()) {
            val argStart = m.range.last + 1
            val (args, _) = splitArgs(source, argStart)
            if (args.size < 7) continue

            // 教师: 老版 args[1] 是字面姓名串; 新版 args[0]/args[1] 是 actTeacher*.join 表达式,
            // 块前 2500 字符反查 var actTeachers (hpu.js 形态)。表达式判据: 含 join( 且非引号串。
            val nameArgRaw = args[1].trim()
            val teacher = if (nameArgRaw.startsWith("\"") || nameArgRaw.startsWith("'")) {
                unquote(nameArgRaw)
            } else if (nameArgRaw.contains("join(")) {
                resolveTeachersFromActTeachers(m.range.first)
            } else {
                unquote(nameArgRaw)
            }
            val nameArg = args[3]
            val room = unquote(args[5])
            val bitmap = unquote(args[6])
            val weeks = bitmapToWeeks(bitmap)
            if (weeks.isEmpty()) continue

            val task = Task(
                teacher = teacher.trim(),
                name = if (nameArg.startsWith("\"")) unquote(nameArg) else "",
                room = room.trim(),
                weeks = weeks,
                blockStart = m.range.first,
                nameExpr = if (nameArg.startsWith("\"")) null else nameArg.trim(),
            )

            // 本块的 index 段: TaskActivity(...) 之后到下一个块声明 (或 marshalTable) 之间
            val blockEnd = if (i + 1 < matches.size) matches[i + 1].range.first else source.length
            val tail = source.substring(m.range.last + 1, blockEnd)
            val indexPairs = mutableListOf<Pair<Int, Int>>()
            // 兼容: index =D*unitCount+P; / index=D*12+P; / index =42; 三种形态
            val idxRe = Regex("""index\s*=\s*(?:(\d+)\s*\*\s*(?:unitCount|\d+)\s*\+\s*(\d+)|(\d+))\s*;""")
            for (im in idxRe.findAll(tail)) {
                val d = im.groupValues[1]
                val p = im.groupValues[2]
                if (d.isNotEmpty() && p.isNotEmpty()) {
                    indexPairs += d.toInt() to p.toInt()
                } else {
                    val linear = im.groupValues[3].toInt()
                    indexPairs += linear / unitCount to linear % unitCount
                }
            }
            if (indexPairs.isNotEmpty()) result += task to indexPairs
        }
        return result
    }

    /**
     * 带引号/括号深度状态的参数切分 (hunnu.js splitArgs 形态)。
     * 返回 (参数列表, 闭括号位置)。参数内逗号 (引号中/括号内) 不切分。
     */
    private fun splitArgs(src: String, start: Int): Pair<List<String>, Int> {
        val args = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var depth = 0
        var i = start
        var closed = -1
        while (i < src.length) {
            val ch = src[i]
            when {
                ch == '"' && !isEscaped(src, i) -> { inQuote = !inQuote; cur.append(ch) }
                ch == '(' && !inQuote -> { depth++; cur.append(ch) }
                ch == ')' && !inQuote -> {
                    if (depth == 0) { closed = i; break }
                    depth--; cur.append(ch)
                }
                ch == ',' && !inQuote && depth == 0 -> { args += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotBlank() || args.isNotEmpty()) args += cur.toString().trim()
        return args to closed
    }

    private fun isEscaped(src: String, i: Int): Boolean {
        var back = 0
        var j = i - 1
        while (j >= 0 && src[j] == '\\') { back++; j-- }
        return back % 2 == 1
    }

    /** 块起点前 2500 字符内最近的 var actTeachers = [{id:..,name:".."},..] → 逗号连名 */
    private fun resolveTeachersFromActTeachers(blockStart: Int): String {
        val segStart = (blockStart - 2500).coerceAtLeast(0)
        val seg = source.substring(segStart, blockStart)
        val arrayRe = Regex("""var\s+actTeachers\s*=\s*\[([^\]]*)\]\s*;""")
        val arr = arrayRe.findAll(seg).lastOrNull()?.groupValues?.get(1) ?: return ""
        val nameRe = Regex("""name\s*:\s*"([^"]*)"""")
        return nameRe.findAll(arr).mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    /** 上财形态: 第 4 参 = this.courseNameLessonNo → 块后反查 var courseNameLessonNo = ".."; */
    private fun resolveCourseName(task: Task): String {
        task.name.takeIf { it.isNotBlank() }?.let { return cleanCourseName(it) }
        val expr = task.nameExpr ?: return ""
        if (!expr.contains("courseNameLessonNo")) return ""
        val re = Regex("""var\s+courseNameLessonNo\s*=\s*"([^"]*)"\s*;""")
        return re.find(source)?.groupValues?.get(1).orEmpty()
    }

    /** 课名尾缀课程编码剥离: "大学物理Ⅱ(D1200440.18)" → "大学物理Ⅱ" (uestc.js 形态) */
    private fun cleanCourseName(name: String): String =
        name.replace(Regex("""\s*\([A-Z]{1,3}\d+\.[\w.]+\)\s*$"""), "").trim()

    /**
     * 位图 → 周列表。下标 0 占位, 下标 i=1 即第 i 周 (勿 +1, 四源同证)。
     * 超长位图 (53/54 位) 天然兼容。
     */
    private fun bitmapToWeeks(bitmap: String): List<Int> =
        bitmap.mapIndexedNotNull { i, ch -> if (i >= 1 && ch == '1') i else null }

    /** index 对 (0基) → (day, startNode, endNode): 排序后连续节次合并成连堂 */
    private fun mergeConsecutiveNodes(pairs: List<Pair<Int, Int>>): Triple<Int, Int, Int> {
        val sorted = pairs.sortedWith(compareBy({ it.first }, { it.second }))
        val day = sorted.first().first + 1
        val nodes = sorted.map { it.second + 1 }.distinct().sorted()
        // 连续段取首尾 (跨天 index 不该出现在同一块; 出现时取最小段保守处理)
        var start = nodes.first()
        var end = nodes.first()
        for (n in nodes.drop(1)) {
            if (n == end + 1) end = n else break
        }
        return Triple(day, start, end)
    }

    private fun unquote(arg: String): String {
        val t = arg.trim()
        if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '\'' && t.last() == '\''))) {
            return t.substring(1, t.length - 1)
        }
        return t
    }

    /** 三锚齐中 95 (manualArrangeCourseTable + TaskActivity + unitCount); 仅 TaskActivity 50..79 */
    override fun confidence(): Int = when {
        source.contains("manualArrangeCourseTable") &&
            source.contains("new TaskActivity(") &&
            Regex("""\bvar\s+unitCount\s*=\s*\d+\s*;""").containsMatchIn(source) -> 95
        source.contains("manualArrangeCourseTable") && source.contains("new TaskActivity(") -> 90
        source.contains("new TaskActivity(") -> when {
            Regex("""\bvar\s+unitCount\s*=\s*\d+\s*;""").containsMatchIn(source) -> 70
            else -> 55
        }
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("manualArrangeCourseTable")) add("table#manualArrangeCourseTable")
        if (source.contains("new TaskActivity(")) add("new TaskActivity(...)")
        if (Regex("""\bvar\s+unitCount\s*=\s*\d+\s*;""").containsMatchIn(source)) add("var unitCount = N")
        if (source.contains("marshalTable")) add("table0.marshalTable")
        if (source.contains("courseTableForStd")) add("courseTableForStd")
    }
}

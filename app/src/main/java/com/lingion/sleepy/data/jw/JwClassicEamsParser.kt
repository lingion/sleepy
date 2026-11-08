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
 *   - 教师 [1] 可能是 actTeacherName.join(',') 表达式 → 块起点之前
 *     反查最近的 var actTeachers = [...] 抠 name (hpu.js 形态)
 *   - 参数切分用带引号/括号深度状态机 (课名可含逗号), 禁 naive split(',')
 *   - 异常防护: 教师 = "-1" 跳块 (停课标记); 教室 = "停课" 不输出;
 *     上财 2026-08 起第 4 参是 this.courseNameLessonNo 表达式 → 块后反查 var 值
 *
 * 算法形态参考: shiguang_warehouse (MIT) hunnu.js/uestc.js/hpu.js +
 * WakeupSchedule_Kotlin (Apache-2.0) ImportViewModel.kt; 代码自写。
 */
class JwClassicEamsParser(
    source: String,
    private val transformWeekBitmap: (String) -> String = { it },
) : JwParser(source) {

    /** 一条 TaskActivity 记录 (块级, 尚未按 index/位图展开) */
    private data class Task(
        val teacher: String,
        val name: String,
        val room: String,
        val weeks: List<Int>,
        val blockStart: Int,   // 块在 source 中的起点 (actTeachers 反查窗口)
        val nameExpr: String?, // 第 4 参为表达式时记原式 (courseNameLessonNo)
    )

    internal data class Activity(
        val name: String,
        val teacher: String,
        val room: String,
        val weeks: List<Int>,
        val indices: List<Pair<Int, Int>>,
    )

    /** Share JavaScript decoding while keeping each protocol's week and section semantics separate. */
    internal fun decodedActivities(defaultUnitCount: Int? = null): List<Activity> {
        val count = (unitCountFromPage() ?: defaultUnitCount)?.takeIf { it > 0 } ?: return emptyList()
        return blocks(count).mapNotNull { (task, indices) ->
            val name = resolveCourseName(task)
            if (task.teacher == "-1" || task.room == "停课" || name.isBlank()) null
            else Activity(name, task.teacher, task.room, task.weeks, indices)
        }
    }

    override fun generateCourseList(): List<JwCourse> {
        if (!Regex("""new\s+TaskActivity\(""").containsMatchIn(source) &&
            source.contains("newActivity(") && source.contains("addActivityByTime(")) {
            return timeActivities()
        }
        val out = mutableListOf<JwCourse>()

        for (task in decodedActivities()) {
            // (day, startNode, endNode) 连堂合并 + 每周展开 → 与强智位图语义对齐
            for ((day, startNode, endNode) in mergeConsecutiveNodes(task.indices)) {
                for (week in task.weeks) {
                    out += JwCourse(
                        name = task.name,
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
        }
        return out
    }

    private fun timeActivities(): List<JwCourse> =
        JwEcuplScriptParser(source).generateCourseList()

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
            val (args, closingParen) = splitArgs(source, argStart)
            if (args.size < 7 || closingParen < 0) continue

            // 教师: 老版 args[1] 是字面姓名串; 新版 args[0]/args[1] 是 actTeacher*.join 表达式,
            // 块前反查 var actTeachers (hpu.js 形态)。表达式判据: 含 join( 且非引号串。
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
            val bitmap = transformWeekBitmap(unquote(args[6]))
            val weeks = bitmapToWeeks(bitmap)
            if (weeks.isEmpty()) continue

            val task = Task(
                teacher = teacher.trim(),
                name = if (isQuoted(nameArg)) unquote(nameArg) else "",
                room = room.trim(),
                weeks = weeks,
                blockStart = m.range.first,
                nameExpr = if (isQuoted(nameArg)) null else nameArg.trim(),
            )

            // 本块的 index 段: TaskActivity(...) 之后到下一个块声明 (或 marshalTable) 之间
            val blockEnd = if (i + 1 < matches.size) matches[i + 1].range.first else source.length
            val tail = source.substring(m.range.last + 1, blockEnd)
            val indexPairs = mutableListOf<Pair<Int, Int>>()
            // 兼容: index =D*unitCount+P; / index=D*12+P; / index =42; 三种形态
            val idxRe = Regex("""index\s*=\s*(?:(\d+)\s*\*\s*(unitCount|\d+)\s*\+\s*(\d+)|(\d+))\s*;""")
            for (im in idxRe.findAll(tail)) {
                val d = im.groupValues[1]
                val multiplier = im.groupValues[2]
                val p = im.groupValues[3]
                if (d.isNotEmpty() && p.isNotEmpty()) {
                    val day = d.toIntOrNull() ?: continue
                    val node = p.toIntOrNull() ?: continue
                    if (multiplier == "unitCount") {
                        if (day in 0..6 && node in 0 until unitCount) indexPairs += day to node
                    } else {
                        val stride = multiplier.toIntOrNull()?.takeIf { it > 0 } ?: continue
                        val linear = day.toLong() * stride + node
                        if (linear in 0 until 7L * unitCount) indexPairs += (linear / unitCount).toInt() to (linear % unitCount).toInt()
                    }
                } else if (im.groupValues[4].isNotEmpty()) {
                    val linear = im.groupValues[4].toLongOrNull() ?: continue
                    if (linear in 0 until 7L * unitCount) indexPairs += (linear / unitCount).toInt() to (linear % unitCount).toInt()
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
        var quote: Char? = null
        var depth = 0
        var i = start
        var closed = -1
        while (i < src.length) {
            val ch = src[i]
            when {
                quote != null -> {
                    if (ch == quote && !isEscaped(src, i)) quote = null
                    cur.append(ch)
                }
                ch == '"' || ch == '\'' -> { quote = ch; cur.append(ch) }
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> {
                    if (depth == 0) { closed = i; break }
                    depth--; cur.append(ch)
                }
                ch == ',' && depth == 0 -> { args += cur.toString().trim(); cur.clear() }
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

    /** Resolve the closest declared teacher array without an arbitrary character cutoff. */
    private fun resolveTeachersFromActTeachers(blockStart: Int): String {
        val seg = source.substring(0, blockStart)
        val arrayRe = Regex("""var\s+actTeachers\s*=\s*\[([^\]]*)\]\s*;""")
        val arr = arrayRe.findAll(seg).lastOrNull()?.groupValues?.get(1) ?: return ""
        val nameRe = Regex("""(?:["']name["']|\bname)\s*:\s*("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""")
        return nameRe.findAll(arr).map { unquote(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    /** 上财形态: 第 4 参 = this.courseNameLessonNo → 块后反查 var courseNameLessonNo = ".."; */
    private fun resolveCourseName(task: Task): String {
        // verbatim: 课程名整体入库, 不做任何清洗/剥离
        // 历史误伤: cleanCourseName("大学物理Ⅱ(D1200440.18)") → "大学物理Ⅱ" 丢了课程编码
        task.name.takeIf { it.isNotBlank() }?.let { return it }
        val expr = task.nameExpr ?: return ""
        if (!expr.contains("courseNameLessonNo")) return ""
        val re = Regex("""var\s+courseNameLessonNo\s*=\s*"([^"]*)"\s*;""")
        val preceding = re.findAll(source.substring(0, task.blockStart)).lastOrNull()
        if (preceding != null) return preceding.groupValues[1]
        val nextTask = Regex("""new\s+TaskActivity\(""").find(source, task.blockStart + 1)?.range?.first
            ?: source.length
        return re.find(source.substring(task.blockStart, nextTask))?.groupValues?.get(1).orEmpty()
    }

    /**
     * 位图 → 周列表。下标 0 占位, 下标 i=1 即第 i 周 (勿 +1, 四源同证)。
     * 超长位图 (53/54 位) 天然兼容。
     */
    private fun bitmapToWeeks(bitmap: String): List<Int> {
        if (bitmap.isEmpty() || bitmap.any { it != '0' && it != '1' }) return emptyList()
        return bitmap.mapIndexedNotNull { i, ch -> if (i >= 1 && ch == '1') i else null }
    }

    /** Each assignment is one occurrence. Merge only adjacent periods on the same weekday. */
    private fun mergeConsecutiveNodes(pairs: List<Pair<Int, Int>>): List<Triple<Int, Int, Int>> = buildList {
        for ((day, slots) in pairs.groupBy { it.first }.toSortedMap()) {
            val nodes = slots.map { it.second + 1 }.distinct().sorted()
            var start = nodes.first()
            var end = start
            for (node in nodes.drop(1)) {
                if (node == end + 1) {
                    end = node
                } else {
                    add(Triple(day + 1, start, end))
                    start = node
                    end = node
                }
            }
            add(Triple(day + 1, start, end))
        }
    }

    private fun isQuoted(value: String): Boolean = value.length >= 2 &&
        (value.first() == '"' || value.first() == '\'') && value.last() == value.first()

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

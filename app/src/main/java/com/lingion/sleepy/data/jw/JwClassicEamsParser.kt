package com.lingion.sleepy.data.jw

import org.json.JSONArray

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

    private val script by lazy { JwScriptSource(source) }
    private val taskPattern = Regex("""new\s+TaskActivity\s*\(""")

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
        if (script.matches(taskPattern).none() &&
            script.matches(Regex("""newActivity\s*\(""")).any() && script.matches(Regex("""addActivityByTime\s*\(""")).any()) {
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

    /** Resolve the stride actually referenced by coordinate arithmetic. */
    private fun unitCountFromPage(): Int? {
        val referenced = script.matches(Regex("""[\w$]+\s*=\s*\d+\s*\*\s*([\w$]+)\s*\+"""))
            .map { it.groupValues[1] }.toSet()
        val counts = script.assignments.filter { it.target in referenced && it.operator == "=" }
            .mapNotNull { it.expression.toIntOrNull()?.takeIf { n -> n > 0 } }.distinct()
        if (counts.size == 1) return counts.single()
        // The legacy linear-index form has no arithmetic reference to its stride.
        return script.assignments.lastOrNull { it.target == "unitCount" }?.expression?.toIntOrNull()
    }

    /**
     * 切出每课块并解析 TaskActivity 参数 + 后随 index 赋值。
     * 块起点 = new TaskActivity( 出现处; 终点 = 本块的 index 赋值段结束
     * (下一个 var teachers/var actTeachers 或 marshalTable 之前)。
     */
    private fun blocks(unitCount: Int): List<Pair<Task, List<Pair<Int, Int>>>> {
        val result = mutableListOf<Pair<Task, List<Pair<Int, Int>>>>()
        val matches = script.matches(taskPattern).toList()

        for ((i, m) in matches.withIndex()) {
            val argStart = m.range.last + 1
            val (args, closingParen) = script.arguments(argStart - 1) ?: continue
            if (args.size < 7 || closingParen < 0) continue

            // 教师: 老版 args[1] 是字面姓名串; 新版 args[0]/args[1] 是 actTeacher*.join 表达式,
            // 块前反查 var actTeachers (hpu.js 形态)。表达式判据: 含 join( 且非引号串。
            val nameArgRaw = args[1].trim()
            val teacher = if (nameArgRaw.startsWith("\"") || nameArgRaw.startsWith("'")) {
                unquote(nameArgRaw)
            } else if (Regex("""\.join\s*\(""").containsMatchIn(nameArgRaw)) {
                resolveTeachers(nameArgRaw, m.range.first)
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
            val blockEnd = matches.getOrNull(i + 1)?.range?.first ?: script.text.length
            val tail = script.code.substring(closingParen + 1, blockEnd)
            val referencedIndices = Regex("""\.activities\s*\[\s*([\w$]+)\s*]""")
                .findAll(tail).map { it.groupValues[1] }.toSet()
            val indexPairs = mutableListOf<Pair<Int, Int>>()
            val formula = Regex("""(\d+)\s*\*\s*([\w$]+|\d+)\s*\+\s*(\d+)""")
            for (assignment in script.assignments.filter { it.start in (closingParen + 1) until blockEnd }) {
                if (referencedIndices.isNotEmpty() && assignment.target !in referencedIndices) continue
                val expression = assignment.expression
                val coordinate = formula.matchEntire(expression)
                val linear = if (coordinate != null) {
                    val day = coordinate.groupValues[1].toLongOrNull() ?: continue
                    val multiplier = coordinate.groupValues[2]
                    val node = coordinate.groupValues[3].toLongOrNull() ?: continue
                    val declaration = script.assignments.lastOrNull {
                        it.target == multiplier && it.start < assignment.start
                    }
                    // Some declared protocols supply the stride in an external script.
                    // Keep the caller's protocol default only when no declaration exists.
                    val stride = multiplier.toLongOrNull() ?: if (declaration == null) unitCount.toLong()
                        else declaration.expression.toLongOrNull() ?: continue
                    if (stride <= 0) continue
                    if (multiplier.toLongOrNull() == null && (day !in 0..6 || node !in 0 until stride)) continue
                    runCatching { Math.addExact(Math.multiplyExact(day, stride), node) }.getOrNull() ?: continue
                } else {
                    if (referencedIndices.isEmpty() && assignment.target != "index") continue
                    expression.toLongOrNull() ?: continue
                }
                if (linear in 0 until 7L * unitCount) indexPairs +=
                    (linear / unitCount).toInt() to (linear % unitCount).toInt()
            }
            if (indexPairs.isNotEmpty()) result += task to indexPairs
        }
        return result
    }

    /** Follow the joined binding and its actual name projection, without executing scripts. */
    private fun resolveTeachers(expression: String, before: Int): String {
        val binding = "[\\w$]+(?:\\.[\\w$]+)*"
        fun names(raw: String, objects: Boolean): String? {
            val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return null
            if (rows.length() == 0) return null
            val values = (0 until rows.length()).map { index ->
                if (objects) rows.optJSONObject(index)?.opt("name") as? String else rows.opt(index) as? String
            }
            if (values.any { it == null }) return null
            return values.filterNotNull().map(String::trim).filter(String::isNotBlank).joinToString("/")
        }
        fun value(target: String): String? = script.assignments.lastOrNull {
            it.target == target && it.start < before
        }?.expression
        val joined = Regex("^($binding)\\s*\\.\\s*join\\s*\\(").find(expression)?.groupValues?.get(1)
        if (joined != null) {
            value(joined)?.let { names(it, false) }?.let { return it }
            val push = Regex("${Regex.escape(joined)}\\s*\\.\\s*push\\s*\\(\\s*($binding)\\s*\\[\\s*[\\w$]+\\s*]\\s*\\.\\s*name\\s*\\)")
            val reset = script.assignments.lastOrNull { it.target == joined && it.start < before }?.start ?: 0
            val recordBinding = script.matches(push).filter { it.range.first in reset until before }
                .lastOrNull()?.groupValues?.get(1)
            if (recordBinding != null) return value(recordBinding)?.let { names(it, true) }.orEmpty()
        }
        val mapped = Regex("^($binding)\\s*\\.\\s*map\\s*\\(\\s*([\\w$]+)\\s*=>\\s*\\2\\s*\\.\\s*name\\s*\\)\\s*\\.\\s*join\\s*\\(")
            .find(expression)?.groupValues?.get(1)
        if (mapped != null) return value(mapped)?.let { names(it, true) }.orEmpty()
        // Older simplified captures omit the projection loop. Preserve their
        // established nearest-schema fallback only when no actual reference exists.
        return script.assignments.asSequence().filter { it.start < before }
            .mapNotNull { names(it.expression, true) }.lastOrNull().orEmpty()
    }

    private fun resolveCourseName(task: Task): String {
        task.name.takeIf(String::isNotBlank)?.let { return it }
        val expression = task.nameExpr ?: return ""
        val nextTask = script.matches(taskPattern).firstOrNull { it.range.first > task.blockStart }
            ?.range?.first ?: script.text.length
        return script.resolveString(expression, task.blockStart, nextTask).orEmpty()
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

    private fun unquote(arg: String): String = script.string(arg) ?: arg.trim()

    /** 三锚齐中 95 (manualArrangeCourseTable + TaskActivity + unitCount); 仅 TaskActivity 50..79 */
    override fun confidence(): Int = when {
        source.contains("manualArrangeCourseTable") &&
            script.matches(taskPattern).any() &&
            (unitCountFromPage() != null) -> 95
        source.contains("manualArrangeCourseTable") && script.matches(taskPattern).any() -> 90
        script.matches(taskPattern).any() -> when {
            (unitCountFromPage() != null) -> 70
            else -> 55
        }
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("manualArrangeCourseTable")) add("table#manualArrangeCourseTable")
        if (script.matches(taskPattern).any()) add("new TaskActivity(...)")
        if ((unitCountFromPage() != null)) add("var unitCount = N")
        if (source.contains("marshalTable")) add("table0.marshalTable")
        if (source.contains("courseTableForStd")) add("courseTableForStd")
    }
}

package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 北京交通大学教学支撑平台 (AA, aa.bjtu.edu.cn) 课表解析器 — issue #19 适配
 * (教务跨仓验证 SOP, 2026-09-09)。
 *
 * # 协议证据
 * BJTU = 自研 Django 系 (CAS SSO + MIS 门户桥 + AA 教学支撑平台)。7 维对比矩阵与
 * 24 候选逐仓 verdict 见 docs/bjtu-cross-verify-2026-09-09/ (POSITIVE 12 / INDIRECT 7 /
 * NULL_EVIDENCE 2 / NEGATIVE 2 / FETCH_FAILED 1)。与 Sleepy 既有协议族 (正方/强智/金智/
 * URP/jwglxt/自建门户 REST) 均不同构, 故独立 TYPE_BJTU。
 *
 * # 数据路径 (WebView 会话内同源 fetch, 无学期/周次参数 — 服务端按会话决定)
 *   GET /course_selection/courseselect/stuschedule/    本学期课表
 *   GET /course_selection/courseselecttask/schedule/   选课任务课表 (全学期)
 * 证据仓: HFDLYS/BJTUselfService (MIT) + wan300/bjtu_mis_Android (MIT) 双仓独立证实。
 * 两段 HTML 以 [DOC_MARKER_PREFIX] 标记拼成一个 source 喂给本 parser, 段间独立解析、
 * 取课程数最多者 (并列时 schedule 全学期优先) — 单端点失败不拖垮另一端点。
 *
 * # 页面形状 (三仓交叉一致)
 *   table.table, 表头 th = 星期一..星期日; 数据行首格 = `第N节 <span>[HH:MM-HH:MM]</span>`;
 *   课程格 div 块 = code 前缀 `[A-Z]\d+[A-Z]? [NN]` + span 课名 + 内层 div `第01-16周 <i>教师</i>`
 *   + span.text-muted 教室 "校区, 楼, 室"; 变体 div.ellipsis[title=完整文本]。
 * 节次时间: 页面行首格自带 [HH:MM-HH:MM] (权威, 由 BJTU_FETCH_JS 抽出随 periods 回传);
 * 周次文法: 第A-B周 / 第2,4,6周 / 第X周 + (单/双)奇偶后缀。
 *
 * # 已知上游 bug (禁复制)
 * fish2lab/bjtu-cli 的 parseCourseWeeks 先剥 "第/周" 再 regex, 单/双后缀信息丢失 —
 * 本 parser 保留原文全文匹配 ([WEEK_NUM_RE] + 奇偶过滤), 不剥前后缀。
 */
class JwBjtuParser(source: String) : JwParser(source) {
    companion object {
        /** JwCourse.type 契约: 0=每周 1=单周 2=双周 */
        const val TYPE_DEFAULT = 0
        const val TYPE_ODD = 1
        const val TYPE_EVEN = 2

        /** 组合源分段标记: `<mark>label</mark>-->HTML`, label ∈ {stuschedule, schedule} */
        const val DOC_MARKER_PREFIX = "<!--sleepy-bjtu-doc:"

        /** AA 课表数据端点 (相对 aa.bjtu.edu.cn, WebView 同源 fetch) */
        const val PATH_STUSCHEDULE = "/course_selection/courseselect/stuschedule/"
        const val PATH_SCHEDULE = "/course_selection/courseselecttask/schedule/"

        /** host 锚点 (schools.json url 与 WebView 落地页校验用) */
        const val HOST_SUFFIX = "bjtu.edu.cn"

        /**
         * 跨语言 invariant 源码常量 (SOP 铁律 3): JwWebViewLoginScreen.kt 的 BJTU_FETCH_JS
         * 里 `new RegExp('...')` 的 JS 字符串字面量必须与这里逐字符相等,
         * JwBjtuParserTest 断言锁死。Kotlin 侧 `\\s` 在文件源码里就是反斜杠+s 两个字符,
         * JS 字符串字面量 '第\\s*(\\d+)\\s*节' 解析后正是同一 regex 文本。
         */
        const val PERIOD_LABEL_RE_SRC = "第\\s*(\\d+)\\s*节"
        const val PERIOD_TIME_RE_SRC = "(\\d{1,2}:\\d{2})\\s*[-–—~至]\\s*(\\d{1,2}:\\d{2})"

        private val PERIOD_LABEL_RE = Regex(PERIOD_LABEL_RE_SRC)
        private val PERIOD_TIME_RE = Regex(PERIOD_TIME_RE_SRC)

        /**
         * 周次文法 (矩阵 D4 canonical, wan300 仓实锚): 完整形态必以 第 开头、以 周 收口,
         * 枚举分隔符 ,，、 与范围分隔符 -~—–－至到 都在形态内部; 可带 (单/双) 奇偶后缀。
         * 锚定形态是刻意的: 教室 "3-302" 这类裸数字段禁被当成周次, 教师姓氏 "单"
         * 禁被当成奇偶过滤 — 只在形态匹配区内取数。
         */
        private val WEEK_FORM_RE =
            Regex("""第[\d,，、\s\-~—–－至到]+?周(?:\s*[（(]\s*(单|双)\s*[）)])?""")

        /** 形态内的数字/范围 token (仅在 WEEK_FORM_RE 命中区内使用) */
        private val WEEK_NUM_RE = Regex("""(\d{1,2})(?:\s*[-~—–－至到]\s*(\d{1,2}))?""")

        /** 异族协议负锚点: 命中即非 BJTU 页面 (防 fallback 裁决在正方/强智/jwglxt 页误抢) */
        private val FOREIGN_ANCHOR_RE = Regex("""jwglxt|xskbcx|zftal|default2\.aspx|xs_main|jqGrid""")

        /** 登录/失效页锚点 (矩阵 D7): AA 自登录路径或 Django csrf 表单 */
        private val LOGIN_PAGE_RE = Regex("""/client/login/|csrfmiddlewaretoken""")

        /** 表头星期锚点: 课表页必有, 登录页必无 — 双向判别用 */
        private const val DAY_ANCHOR = "星期一"

        /** 表头 星期X → 1..7 (日/天 都算第 7) */
        private val DAY_HEADER_NAMES = linkedMapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7
        )

        /** 周次上限 (防病态大数展开; AA 学期最长 25 周) */
        private const val MAX_WEEK = 30

        /**
         * 组合源切段: 无标记 → 整体一段 ("plain"); 有标记 → 逐段 (label, body)。
         * 标记内 label 截到 "-->", body 到下一个标记或文末。
         */
        internal fun sections(source: String): List<Pair<String, String>> {
            if (!source.contains(DOC_MARKER_PREFIX)) return listOf("plain" to source)
            val out = mutableListOf<Pair<String, String>>()
            var idx = source.indexOf(DOC_MARKER_PREFIX)
            while (idx >= 0) {
                val labelStart = idx + DOC_MARKER_PREFIX.length
                val arrow = source.indexOf("-->", labelStart)
                if (arrow < 0) break
                val label = source.substring(labelStart, arrow).trim()
                val bodyStart = arrow + 3
                val next = source.indexOf(DOC_MARKER_PREFIX, bodyStart)
                val bodyEnd = if (next >= 0) next else source.length
                out += label to source.substring(bodyStart, bodyEnd)
                idx = next
            }
            return out
        }

        /**
         * 周次文本 → 可表示周次段列表 (逐形态展开)。
         *   "第1-16周(单)"   → [1..15 单周]
         *   "第1-16周（双）"  → [2..16 双周]
         *   "第01-16周"      → [1..16 每周]
         *   "第2,4,6周"      → [2..6 双周] (步长 2 连续 → 奇偶段, UCAS splitWeekRuns 同构)
         *   "第1-4,6-10周"   → [1..4 每周] + [6..10 每周] (缺口断段)
         *   "第1-8周 第9-16周" → 两形态 → 两段
         * 奇偶后缀只在形态收口括号内识别 — 教室数字 ("3-302") 与教师姓氏 ("单")
         * 不在形态区内, 不受影响。
         * 返回空 = 文本无可表示周次 (调用方跳过该块, 禁编造 1-16 占位)。
         */
        internal fun parseWeekRuns(text: String): List<WeekRun> {
            val out = mutableListOf<WeekRun>()
            for (form in WEEK_FORM_RE.findAll(text)) {
                val nums = mutableListOf<Pair<Int, Int?>>()
                for (m in WEEK_NUM_RE.findAll(form.value)) {
                    val a = m.groupValues[1].toIntOrNull() ?: continue
                    val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
                    nums += a to b
                }
                if (nums.isEmpty()) continue
                val parity = when (form.groupValues[1]) {
                    "单" -> 1
                    "双" -> 0
                    else -> -1
                }
                val weeks = sortedSetOf<Int>()
                for ((a, b) in nums) {
                    for (w in a..(b ?: a)) if (w in 1..MAX_WEEK) weeks += w
                }
                val filtered = if (parity >= 0) weeks.filter { it % 2 == parity } else weeks.toList()
                if (filtered.isEmpty()) continue
                // 步长切段 (UCAS splitWeekRuns 同构): 步长 1 连续 = 每周段,
                // 步长 2 连续 = 单/双周段, 缺口/步长切换处断段
                var runStart = 0
                var prev = 0
                var step = 0
                for (w in filtered) {
                    when {
                        runStart == 0 -> { runStart = w; prev = w; step = 0 }
                        w == prev + 1 && step != 2 -> { prev = w; step = 1 }
                        w == prev + 2 && step != 1 -> { prev = w; step = 2 }
                        else -> { out += mkRun(runStart, prev, step); runStart = w; prev = w; step = 0 }
                    }
                }
                out += mkRun(runStart, prev, step)
            }
            // 去重 (ellipsis 块 title 与内层 div 会给出同一 第A-B周 形态两遍; WeekRun 是 data class)
            return out.distinct()
        }

        /** 步长 → JwCourse.type: 步长 2 连续段按段首奇偶定单/双, 其余每周 */
        private fun mkRun(startWeek: Int, endWeek: Int, step: Int): WeekRun = WeekRun(
            startWeek = startWeek,
            endWeek = endWeek,
            type = when {
                step == 2 && startWeek % 2 == 1 -> TYPE_ODD
                step == 2 -> TYPE_EVEN
                else -> TYPE_DEFAULT
            }
        )

        /** 表头文本 星期X/礼拜X/周X → 1..7; 其余 0 */
        internal fun dayOfWeek(text: String): Int {
            val t = text.trim()
            if (t.isEmpty() || t.length > 4) return 0
            for ((suffix, day) in DAY_HEADER_NAMES) {
                if (t == "星期$suffix" || t == "礼拜$suffix" || t == "周$suffix") return day
            }
            return 0
        }

        /**
         * 静态表锚点 (无标记路径的 confidence 来源): 星期表头 + 节次行首 + 周次文法齐备,
         * 且无异族协议锚点。
         */
        internal fun hasTableAnchors(s: String): Boolean =
            s.contains(DAY_ANCHOR) &&
                PERIOD_LABEL_RE.containsMatchIn(s) &&
                s.contains("周") &&
                !FOREIGN_ANCHOR_RE.containsMatchIn(s)

        /**
         * 登录/失效页判别 (矩阵 D7): 命中登录锚点 且 无课表表头锚点。
         * 后半条防误杀 — AA 真课表页内嵌 Django 表单时也会带 csrfmiddlewaretoken。
         */
        internal fun isLoginLike(body: String): Boolean =
            LOGIN_PAGE_RE.containsMatchIn(body) && !body.contains(DAY_ANCHOR)
    }

    /** 可表示周次段: startWeek..endWeek + type (每周/单周/双周) */
    internal data class WeekRun(val startWeek: Int, val endWeek: Int, val type: Int)

    /** 课程格块解析中间态 (合并前) */
    private data class BlockInfo(
        val name: String,
        val day: Int,
        val node: Int,
        val weeksText: String,
        val teacher: String,
        val room: String,
    )

    override fun generateCourseList(): List<JwCourse> {
        // 异族协议守卫: 正方/强智/jwglxt 形态页面不是 AA 课表, 防误抢 (含 declaredType 强制分发误用)。
        if (!source.contains(DOC_MARKER_PREFIX) && FOREIGN_ANCHOR_RE.containsMatchIn(source)) {
            return emptyList()
        }
        // 段间独立解析: 每段 (label, courses), 登录页段跳过; 取课程数最多者,
        // 并列时段优先级 schedule(全学期)=2 > stuschedule(本学期)=1 > 其他=0。
        data class Candidate(val label: String, val courses: List<JwCourse>)
        val candidates = mutableListOf<Candidate>()
        for ((label, body) in sections(source)) {
            if (isLoginLike(body)) continue
            val courses = parseTable(body) ?: continue
            if (courses.isEmpty()) continue
            candidates += Candidate(label, courses)
        }
        return candidates.maxWithOrNull(
            compareBy<Candidate>({ it.courses.size }, { sectionPriority(it.label) })
        )?.courses ?: emptyList()
    }

    override fun confidence(): Int = when {
        source.contains(DOC_MARKER_PREFIX) -> 95
        hasTableAnchors(source) -> 92
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains(DOC_MARKER_PREFIX)) add("bjtu:combined-doc")
        for (label in sections(source).map { it.first }) {
            if (label == "stuschedule") add("path:stuschedule")
            if (label == "schedule") add("path:schedule")
        }
        if (source.contains(DAY_ANCHOR)) add("th:星期一")
        if (PERIOD_LABEL_RE.containsMatchIn(source)) add("cell:第N节")
        if (PERIOD_TIME_RE.containsMatchIn(source)) add("cell:[HH:MM-HH:MM]")
        if (LOGIN_PAGE_RE.containsMatchIn(source)) add("guard:login-page")
        if (!source.contains(DOC_MARKER_PREFIX) && FOREIGN_ANCHOR_RE.containsMatchIn(source)) {
            add("guard:foreign-anchor")
        }
    }

    /** 段优先级: schedule(全学期)=2 > stuschedule(本学期)=1 > 其他=0 */
    private fun sectionPriority(label: String): Int = when (label) {
        "schedule" -> 2
        "stuschedule" -> 1
        else -> 0
    }

    /**
     * 解析一段课表 HTML。定位表头行 (≥5 个星期格) → 逐数据行 (行首格 第N节) →
     * 逐课程格块 → 分组 (名|周次文本|教室|星期) 合并相邻节次 → 段×周次段展开。
     * 返回 null = 找不到表头 (非课表页)。
     */
    private fun parseTable(html: String): List<JwCourse>? {
        val doc = Jsoup.parse(html)
        var dayStartCol = -1
        for (tr in doc.select("tr")) {
            val cells = tableCells(tr)
            val firstDayCol = cells.withIndex().firstOrNull { dayOfWeek(it.value.text()) > 0 }
            if (cells.size - (firstDayCol?.index ?: cells.size) >= 5 && firstDayCol != null) {
                dayStartCol = firstDayCol.index
                break
            }
        }
        if (dayStartCol < 0) return null

        val infos = mutableListOf<BlockInfo>()
        for (tr in doc.select("tr")) {
            val cells = tableCells(tr)
            if (cells.isEmpty()) continue
            val node = PERIOD_LABEL_RE.find(cells.first().text())
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            for (col in dayStartCol until minOf(dayStartCol + 7, cells.size)) {
                val day = col - dayStartCol + 1
                for (block in cellBlocks(cells[col])) {
                    val name = courseNameOf(block)
                    if (name.isBlank()) continue
                    // part = 一个授课班次 (一个周次 div), 教师与周次段一一对应;
                    // ellipsis 块的 title 属性即完整文本, 单 part, 不与内层 div 重复展开。
                    for ((weeksText, teacher) in partsOf(block)) {
                        infos += BlockInfo(
                            name = name,
                            day = day,
                            node = node,
                            weeksText = weeksText,
                            teacher = teacher,
                            room = roomOf(block),
                        )
                    }
                }
            }
        }

        val courses = mutableListOf<JwCourse>()
        val groups = linkedMapOf<String, MutableList<BlockInfo>>()
        for (b in infos) {
            groups.getOrPut("${b.name}|${b.weeksText}|${b.room}|${b.day}") { mutableListOf() } += b
        }
        for (members in groups.values) {
            val first = members.first()
            val runs = parseWeekRuns(first.weeksText)
            if (runs.isEmpty()) continue
            // 同组相邻节次合并 (10,11 → 10-11); 缺口 >1 断段
            val segments = mutableListOf<Pair<Int, Int>>()
            for (n in members.map { it.node }.distinct().sorted()) {
                val last = segments.lastOrNull()
                if (last != null && n == last.second + 1) segments[segments.lastIndex] = last.first to n
                else segments += n to n
            }
            for (seg in segments) {
                for (run in runs) {
                    courses += JwCourse(
                        name = first.name,
                        room = first.room,
                        teacher = first.teacher,
                        day = first.day,
                        startNode = seg.first,
                        endNode = seg.second,
                        startWeek = run.startWeek,
                        endWeek = run.endWeek,
                        type = run.type,
                    )
                }
            }
        }
        return courses
    }

    /** 行内单元格序列 (th/td, 保序) */
    private fun tableCells(tr: Element): List<Element> =
        tr.children().filter { it.tagName() == "th" || it.tagName() == "td" }

    /**
     * 课程格 → 课程块列表: 直接子 div 且非空 (真实形态一课一块);
     * 无子 div 时整格当一个块 (宽容形态)。
     */
    private fun cellBlocks(cell: Element): List<Element> =
        cell.select("> div").filter { it.text().isNotBlank() }.ifEmpty { listOf(cell) }

    /** 课名: 第一个非 text-muted 的 span; 兜底 code 前缀 `]` 之后文本 */
    private fun courseNameOf(block: Element): String {
        val span = block.select("span")
            .firstOrNull { !it.hasClass("text-muted") && it.text().isNotBlank() }
        if (span != null) return span.text().trim()
        val text = block.text().trim()
        val bracket = text.indexOf(']')
        if (bracket in 0 until text.lastIndex) return text.substring(bracket + 1).trim()
        return text
    }

    /**
     * 课程块 → (周次文本, 教师) part 列表, part = 一个授课班次 (一个周次 div)。
     * - ellipsis 块: title 属性即完整文本 → 单 part (title, 块内全部 i), 不与内层 div 重复。
     * - 普通块: 每个含 周 的内层 div 一个 part, 教师取该 div 内的 i 标签。
     * 返回空 = 无可解析周次 (调用方跳过, 禁编造)。
     */
    private fun partsOf(block: Element): List<Pair<String, String>> {
        val title = block.attr("title")
        if (title.contains("周")) {
            return listOf(title to teacherOf(block))
        }
        val parts = mutableListOf<Pair<String, String>>()
        // 只取直接子 div (jsoup 的 select("div") 会把 block 自身也算进结果, 会让每课翻倍)
        for (d in block.children()) {
            if (d.tagName() != "div") continue
            val t = d.text()
            if (t.contains("周")) {
                val teacher = d.select("i").map { it.text().trim() }.filter { it.isNotBlank() }
                    .distinct().joinToString(",")
                parts += t to teacher
            }
        }
        return parts.distinctBy { it.first }
    }

    /** 教师: 块内全部 i 标签 (ellipsis/title 路径用) */
    private fun teacherOf(block: Element): String =
        block.select("i").map { it.text().trim() }.filter { it.isNotBlank() }
            .distinct().joinToString(",")

    /** 教室: span.text-muted ("校区, 楼, 室" 形态) */
    private fun roomOf(block: Element): String =
        block.select("span.text-muted").map { it.text().trim() }
            .firstOrNull { it.isNotBlank() }.orEmpty()
}

// THU (清华大学 zhjwxk.cic.tsinghua.edu.cn) parser — wakeup-parity-thu-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

/**
 * 清华大学 (THU) 教务解析器 — zhjwxk.cic.tsinghua.edu.cn
 *
 * 协议指纹 6 件套 (跨仓印证 4 仓 POSITIVE 共识, 1 仓 NULL_EVIDENCE, 3 仓 NEGATIVE):
 *  1. `<form action="/syxk.vsyxkKcapb.do">` + `name="p_xnxq"` 学期字段
 *  2. `<script>` 块内 `function setInitValue() {` + `Event.observe(...)`
 *  3. `strHTML += "<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>"` 课程链接
 *  4. `strHTML += "<b>课程名</b>"` 课程名 (复用 WakeUp C1 模板)
 *  5. `strHTML1 += "；教师"；课程类型"；周次"；地点"` 4 行连续 (C1 模式, 中文分号 ；)
 *  6. `getElementById('a{big}_{day}').innerHTML += strHTML` 网格坐标
 *  + C2 模式 `strHTML = "<a class='blue_red_none' ...><b><font color='blue'>...</font></b></a><font color='blue'>...</font>";`
 *
 * 上游协议形态 (POSITIVE cross-verify):
 *  - WakeUp THUParser.java (Apache-2.0) — 节次↔大节映射 + 7 行网格坐标 + gridColumns/gridData 双轨
 *  - Starrah/THUCourseHelper THUCourseDataSouce.kt (MIT) — strHTML += 状态机 + PAT_C2 blue_red_none
 *
 * 协议演化: 2020-2026 同协议稳定 (WakeUp 7 年未改 endpoint); schedule-data.netlify.app/{semester}.json
 * 单源独证 1/8 (Starrah), Sleepy 不引用此路径, 维持零依赖。
 *
 * 跨语言不变式 (SOP 铁律 3): THUParser.java ↔ 本文件 regex literal char-by-char 锁定;
 * Sleepy 没有 WebView JS 入口, 铁律 3 只在 Java↔Kotlin 间执行。
 */
class JwThuparser(source: String) : JwParser(source) {

    companion object {
        // ---- 节次↔大节映射 (WakeUp THUParser f9220/f9221 OooO0O0 / OooO0OO) ----

        /**
         * 大节索引 (big, 0-6) → 实际起课节点 (startNode)。
         * big=0 占位 (无课); big=1 第 1 节; big=2 第 3-5 节 (连 3 节);
         * big=3 第 6-7 节; big=4 第 8-9 节; big=5 第 10-11 节; big=6 第 12-14 节 (连 3 节)。
         * 与 WakeUp THUParser f9220OooO0O0 一致, SOP 铁律 3 跨仓不变式锁定。
         */
        val START_NODE_MAP: IntArray = intArrayOf(0, 1, 3, 6, 8, 10, 12)

        /**
         * 大节索引 (big, 0-6) → 实际结课节点 (endNode)。
         * 与 WakeUp THUParser f9221OooO0OO 一致。
         */
        val END_NODE_MAP: IntArray = intArrayOf(0, 2, 5, 7, 9, 11, 14)

        /**
         * 清华 14 节作息表 (硬编码, 4/8 仓 cross-verify 共识)。
         * pair[0] = 起始时间 HH:MM, pair[1] = 结束时间 HH:MM, index = 节次 1-14。
         * 与 WakeUp THUParser.OooO0O0() 同源 (WakeUp 字段名 TimeDetail 节次+起始+结束)。
         */
        val THU_TIME_SLOTS: List<Pair<String, String>> = listOf(
            "08:00" to "08:45",
            "08:50" to "09:35",
            "09:50" to "10:35",
            "10:40" to "11:25",
            "11:30" to "12:15",
            "13:30" to "14:15",
            "14:20" to "15:05",
            "15:20" to "16:05",
            "16:10" to "16:55",
            "17:05" to "17:50",
            "17:55" to "18:40",
            "19:20" to "20:05",
            "20:10" to "20:55",
            "21:00" to "21:45",
        )

        // ---- regex 常量 (SOP 铁律 3: char-by-char 与 Starrah/THUCourseHelper 跨语言不变式锁定) ----

        /** 课程链接 `strHTML += "<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>"` — 8 位 courseId。 */
        val RE_C1_LINK = Regex("strHTML \\+= \"<a class='mainHref' href='.*?;([0-9A-Z]{8})'.*?>\"")

        /** 课程名 `strHTML += "<b>(.*)</b>"`。 */
        val RE_C1_TITLE = Regex("""strHTML \+= "<b>(.*)</b>"""")

        /** 课程后续字段 (教师/课程属性/周安排/地点) — `strHTML1 += "；(.*)"` (中文分号 ；)。 */
        val RE_C1_DATA = Regex("""strHTML1 \+= ?"；(.*)"""")

        /** 网格坐标 `getElementById('a(\d)_(\d)').innerHTML += strHTML` — big (大节) + day (星期)。 */
        val RE_C1_WEEKBIG = Regex("""getElementById\('a(\d)_(\d)'\).innerHTML \+= strHTML""")

        /**
         * 特殊课程 (C2 模式, Starrah 实证为实验课/教辅类): 单行 strHTML = "..." 含 blue_red_none class
         *  + 蓝色教师 + 蓝色附加信息 ("北大"/"北外" / 周次 / 时间)。
         */
        val RE_C2_BLUE_RED_NONE = Regex(
            """strHTML = "<a class='blue_red_none' href='.*?p_id=([0-9A-Z]+)'.*?><b><font color='blue'>(.*?)</font></b></a><font color='blue'>(.*?)</font>";"""
        )

        /** 学期字段 `name="p_xnxq" value="..."` — 跨仓印证协议指纹 1。 */
        val RE_SEMESTER = Regex("""name="p_xnxq" value="([\d\-]+?)"""")

        /** setInitValue 函数定义 (定位 JS 块起点)。 */
        val RE_SET_INIT_VALUE = Regex("""function setInitValue\(\)""")

        /** Event.observe (定位 JS 块终点)。 */
        val RE_EVENT_OBSERVE = Regex("""Event\.observe""")

        /** 数字串 — 用于周次数字解析。 */
        val RE_NUMBER = Regex("""\d+""")
    }

    /** 默认学期长度 (WakeUp 默认 16, 清华常见 18, 走保守 16)。 */
    private val defaultWeekCount: Int = 16

    /** 课程类型识别 (教务统计 XKTYPE_LIST, Starrah 实证)。 */
    private val xkTypeList = listOf("必修", "限选", "任选")

    // ---- 内部状态机 ----

    /** setInitValue JS 块解析状态机当前阶段。 */
    private enum class ParseState {
        IDLE,           // 0: 等待课程链接 (C1) 或 单行 (C2)
        GOT_LINK,       // 1: 已识别课程链接, 等待课程名 (C1)
        GOT_TITLE,      // 2: 已识别课程名, 等待教师 (C1)
        GOT_TEACHER,    // 3: 已识别教师, 等待课程类型 (C1)
        GOT_TYPE,       // 4: 已识别课程类型, 等待周次 (C1)
        GOT_WEEKS,      // 5: 已识别周次, 等待地点 (C1)
        GOT_LOCATION,   // 6: 已识别地点, 等待 grid 坐标 (C1)
    }

    /** 当前正在累积的课程信息 (C1 模式状态机内)。 */
    private data class PendingCourse(
        var courseId: String = "",
        var name: String = "",
        var teacher: String = "",
        var type: String = "",
        var weekText: String = "",
        var location: String = "",
        var big: Int = 0,  // 大节 (0-6)
        var day: Int = 0,  // 星期 (1-7)
    )

    override fun generateCourseList(): List<JwCourse> {
        val out = mutableListOf<JwCourse>()
        if (!source.contains("function setInitValue") || !source.contains("Event.observe")) {
            return emptyList()
        }

        // 提取 JS 块 (function setInitValue() { ... })
        val startIdx = RE_SET_INIT_VALUE.find(source)?.range?.first ?: return emptyList()
        val endIdx = RE_EVENT_OBSERVE.find(source, startIdx)?.range?.first ?: source.length
        val jsBlock = source.substring(startIdx, endIdx)
        val lines = jsBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var state = ParseState.IDLE
        var pending = PendingCourse()
        val foundCourses = mutableMapOf<String, PendingCourse>()  // courseId → PendingCourse (跨段复用)

        for (trimmed in lines) {
            when (state) {
                ParseState.IDLE -> {
                    // 优先 C2 (单行 blue_red_none 模式, 跨周实验课/辅修)
                    val c2 = RE_C2_BLUE_RED_NONE.find(trimmed)
                    if (c2 != null) {
                        val name = c2.groupValues[2]
                        val detStr = c2.groupValues[3]
                        out += parseBlueRedNone(name, detStr)
                        continue
                    }
                    // 否则试 C1 链接
                    val c1Link = RE_C1_LINK.find(trimmed)
                    if (c1Link != null) {
                        val courseId = c1Link.groupValues[1]
                        // 检查是否为已有课程 (复用 pending)
                        pending = foundCourses[courseId]?.copy(big = 0, day = 0) ?: PendingCourse(courseId = courseId)
                        state = ParseState.GOT_LINK
                    }
                }

                ParseState.GOT_LINK -> {
                    val m = RE_C1_TITLE.find(trimmed)
                    if (m != null) {
                        pending = pending.copy(name = m.groupValues[1])
                        foundCourses[pending.courseId] = pending
                        state = ParseState.GOT_TITLE
                    }
                }

                ParseState.GOT_TITLE -> {
                    val m = RE_C1_DATA.find(trimmed)
                    if (m != null) {
                        pending = pending.copy(teacher = m.groupValues[1])
                        state = ParseState.GOT_TEACHER
                    }
                }

                ParseState.GOT_TEACHER -> {
                    val m = RE_C1_DATA.find(trimmed)
                    if (m != null) {
                        val str = m.groupValues[1]
                        if (str in xkTypeList) {
                            pending = pending.copy(type = str)
                            state = ParseState.GOT_TYPE
                        }
                        // 非 XKTYPE_LIST → 回退 (Starrah 实证, 例: 直接周次)
                    }
                }

                ParseState.GOT_TYPE -> {
                    val m = RE_C1_DATA.find(trimmed)
                    if (m != null) {
                        pending = pending.copy(weekText = m.groupValues[1])
                        state = ParseState.GOT_WEEKS
                    }
                }

                ParseState.GOT_WEEKS -> {
                    val m = RE_C1_DATA.find(trimmed)
                    if (m != null) {
                        pending = pending.copy(location = m.groupValues[1])
                        state = ParseState.GOT_LOCATION
                    }
                }

                ParseState.GOT_LOCATION -> {
                    val m = RE_C1_WEEKBIG.find(trimmed)
                    if (m != null) {
                        pending = pending.copy(big = m.groupValues[1].toInt(), day = m.groupValues[2].toInt())
                        // 解析并 emit
                        out += emitCourse(pending)
                        // 保留课程 (跨段复用), 重置 big/day
                        foundCourses[pending.courseId] = pending.copy(big = 0, day = 0)
                        state = ParseState.IDLE
                        pending = PendingCourse()
                    }
                }
            }
        }
        return out
    }

    /**
     * C2 (blue_red_none) 课程解析 — 跨周实验课/辅修, 字段挤在单行 strHTML。
     * "北大"/"北外" 类附注识别为辅修; 周次 "(1-8周)" / "(1-16周)" 紧跟其后。
     * 地点位于 <font color='blue'>...</font> 第二段。
     */
    private fun parseBlueRedNone(name: String, detStr: String): List<JwCourse> {
        val course = JwCourse(
            name = name,
            teacher = "",
            room = detStr.trim(),
            day = 0,
            startNode = 0,
            endNode = 0,
            startWeek = 1,
            endWeek = defaultWeekCount,
            type = 0,
        )
        return listOf(course)
    }

    /**
     * 将 pending 课程 emit 为 JwCourse: 大节↔节点映射 + 周次文字解析 + 端点修正。
     */
    private fun emitCourse(p: PendingCourse): JwCourse {
        if (p.name.isBlank() || p.big !in 0..6 || p.day !in 1..7) return emptyCourse(p.name)
        val startNode = START_NODE_MAP[p.big]
        val endNode = END_NODE_MAP[p.big]
        if (startNode == 0 || endNode == 0) return emptyCourse(p.name)  // big=0 无课
        val (sw, ew, type) = parseWeekText(p.weekText)
        val (sw2, ew2) = JwParity.adjustedRange(sw, ew, type)
        return JwCourse(
            name = p.name,
            teacher = p.teacher.trim(),
            room = p.location.trim(),
            day = p.day,
            startNode = startNode,
            endNode = endNode,
            startWeek = sw2,
            endWeek = ew2,
            type = type,
        )
    }

    private fun emptyCourse(name: String) = JwCourse(
        name = name, teacher = "", room = "",
        day = 0, startNode = 0, endNode = 0,
        startWeek = 0, endWeek = 0, type = 0,
    )

    /**
     * 解析周次文字 (清华独有字典):
     *  "全周" → (1, 16, 0)
     *  "前八周" → (1, 8, 0)
     *  "后八周" → (9, 16, 0)
     *  "单周" → (1, 16, 1)
     *  "双周" → (2, 16, 2)
     *  "第X-Y周" → (X, Y, 0)
     *  "第X-Y周(单/双)" → (X, Y, 1/2)
     *  "第X周" → (X, X, 0)
     */
    internal fun parseWeekText(text: String): Triple<Int, Int, Int> {
        val t = text.trim().trimStart('；', ';')
        return when {
            t == "全周" -> Triple(1, defaultWeekCount, 0)
            t == "前八周" -> Triple(1, 8, 0)
            t == "后八周" -> Triple(9, defaultWeekCount, 0)
            t == "单周" -> Triple(1, defaultWeekCount, 1)
            t == "双周" -> Triple(2, defaultWeekCount, 2)
            t.matches(Regex("""第\d+-\d+周（单）""")) || t.matches(Regex("""第\d+-\d+周\(单\)""")) -> {
                val nums = RE_NUMBER.findAll(t).toList().map { it.value.toInt() }
                Triple(nums[0], nums[1], 1)
            }
            t.matches(Regex("""第\d+-\d+周（双）""")) || t.matches(Regex("""第\d+-\d+周\(双\)""")) -> {
                val nums = RE_NUMBER.findAll(t).toList().map { it.value.toInt() }
                Triple(nums[0], nums[1], 2)
            }
            t.matches(Regex("""第\d+-\d+周""")) -> {
                val nums = RE_NUMBER.findAll(t).toList().map { it.value.toInt() }
                Triple(nums[0], nums[1], 0)
            }
            t.matches(Regex("""第\d+周""")) -> {
                val n = RE_NUMBER.find(t)?.value?.toInt() ?: 1
                Triple(n, n, 0)
            }
            else -> Triple(1, defaultWeekCount, 0)
        }
    }

    override fun confidence(): Int {
        var hits = 0
        if (RE_SEMESTER.containsMatchIn(source)) hits++
        if (RE_SET_INIT_VALUE.containsMatchIn(source)) hits++
        if (RE_C1_LINK.containsMatchIn(source)) hits++
        if (RE_C1_TITLE.containsMatchIn(source)) hits++
        if (RE_C1_WEEKBIG.containsMatchIn(source)) hits++
        if (RE_C1_DATA.containsMatchIn(source)) hits++
        return when {
            hits >= 6 -> 95
            hits >= 5 -> 80
            hits >= 4 -> 70
            hits >= 3 -> 50
            hits >= 2 -> 30
            hits >= 1 -> 10
            else -> 0
        }
    }

    override fun matchedFeatures(): List<String> {
        val out = mutableListOf<String>()
        if (source.contains("zhjwxk") || source.contains("cic.tsinghua")) out += "thu:login-or-sso-marker"
        if (RE_SEMESTER.containsMatchIn(source)) out += "thu:p_xnxq-form"
        if (RE_SET_INIT_VALUE.containsMatchIn(source)) out += "thu:setInitValue-block"
        if (RE_C1_LINK.containsMatchIn(source)) out += "thu:strHTML-course-link"
        if (RE_C1_WEEKBIG.containsMatchIn(source)) out += "thu:aN_M-innerHTML"
        if (xkTypeList.any { source.contains(it) }) out += "thu:xktype-必修/限选/任选"
        if (RE_C1_DATA.containsMatchIn(source)) out += "thu:week-text-grammar"
        if (out.isEmpty()) out += "guard:login-only-no-setInitValue"
        return out
    }
}

package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 中国科学院大学选课系统个人课表解析器。
 *
 * #18 (UCAS 适配) — 数据来源有三条路径:
 *
 * 1. **HTML 详情路径 (权威, exact-week)**: `/course/personSchedule` 网格页的每个课程格
 *    链到 `/course/coursetime/<courseId>` 详情页 (托管在 xkcts.ucas.ac.cn:8443,
 *    免登录)。详情页按「上课时间 / 上课地点 / 上课周次」三行一组给数据:
 *
 *        星期二： 第3、4节。  |  教一楼009  |  2、3、4、5、7、8、9、10、11、12
 *
 *    周次是顿号枚举 (实测字符集仅 0-9 与 、), 与 JSON 契约的位图编码完全不同源。
 *    证据: issue #18 v1.2 采集包 `4-detail-nav/` 11/11 详情页 + 无 cookie 直连探测 200
 *    (2026-09-08)。
 *
 * 2. **JSON 路径 (ldiex 契约, exact-week)**: ldiex/UCAS_Course_Schedule_Convertor 启发的契约 ——
 *    `selectedCourse.json` 列出学期所有课程, 每门课的 `{courseId}.json` 暴露
 *    `courseTimeList[]`, 每个元素的 `courseWeek` 是整数位图 (低位 = 第 1 周),
 *    `courseTime` 是低位周次编码 + 高位 12 bit 节次位图。解码约定:
 *
 *        weekBinary  = bin(int(courseWeek))[2:][::-1]
 *        timeBinary  = bin(int(courseTime))[2:]
 *        dayBits     = timeBinary[:-12]              // 高位段, 直接查表得星期 1..7
 *        nodeBits    = timeBinary[-12:][::-1]        // 12 bit 节次位图, 位 i=1 -> 第 i+1 节
 *
 *    dayBits 到星期 1..7 的映射采用 ldiex 原仓 hardcode 字典 (POSITIVE 证据),
 *    不发明新编码。 节次位图解析后取最小与最大节次作为 startNode / endNode。
 *
 * 3. **HTML 网格路径 (fallback)**: 服务端渲染的个人课表页面 `/course/personSchedule`。
 *    表头 `节次/星期`, `tbody > tr > th` 是节次号, `td` 中 `a[href*=/course/coursetime/]`
 *    是课程名。当既无详情数据也无 JSON 时按当前学期 1-16 周占位导入 (provisional 常量)。
 *
 * 三条路径的 confidence 特征在 [confidence] / [matchedFeatures] 上是独立的:
 * 命中任一即 confidence >= 90。
 */
class JwUcasParser(source: String) : JwParser(source) {
    companion object {
        /** HTML 网格路径无详情数据时的占位周次 (v1.2 采集包已证实详情页才是权威来源) */
        const val PROVISIONAL_START_WEEK = 1
        const val PROVISIONAL_END_WEEK = 16

        /**
         * 组合源里详情页段的分段标记。[UcasDetailFetch] 把网格 HTML 与逐课详情页
         * 拼成一个 source 喂给本 parser; 解析器按标记切段, 标记里的 URL 仅信息性。
         */
        const val DETAIL_MARKER_OPEN = "<!--sleepy-ucas-detail:"
        const val DETAIL_MARKER_CLOSE = "<!--/sleepy-ucas-detail-->"

        /** UCAS 详情站 host (相对形态 /course/coursetime/<id> 补全用) */
        const val DETAIL_HOST = "https://xkcts.ucas.ac.cn:8443"

        /**
         * ldiex/UCAS_Course_Schedule_Convertor 实测 dayBits -> 星期 1..7 (POSITIVE 证据)。
         * dayBits 是 `courseTime` 二进制去掉末尾 12 bit 后剩下的前缀, 正序字典序。
         * 任何 dayBits 不在表内 → 视为异常数据, 跳过该 schedule。
         */
        val DAY_BITS: Map<String, Int> = linkedMapOf(
            "10" to 1,
            "11" to 1,
            "100" to 2,
            "110" to 3,
            "1000" to 4,
            "1010" to 5,
            "1100" to 6,
            "1110" to 7
        );

        /** 课程周次类型: 0=每周, 1=单周, 2=单双周(位图) — 与 [JwCourse.type] 契约一致 */
        const val TYPE_DEFAULT = 0
        const val TYPE_ODD = 1
        const val TYPE_EVEN = 2

        // ---- HTML 详情路径 (v1.2 采集包, exact-week) ----

        /** 详情页 URL: 绝对形态 (网格真实形态) */
        private val DETAIL_URL_ABS = Regex("""https?://[^\s"'<>]+/course/coursetime/\d+""")

        /** 详情页 URL: 相对形态容错 */
        private val DETAIL_URL_REL = Regex("""["'\s](/course/coursetime/\d+)""")

        /**
         * 从网格 HTML 提取详情页 URL 列表 (绝对 + 相对, 去重保序)。
         * 抓取器 [UcasDetailFetch] 用它决定要抓哪些详情页。
         */
        fun extractDetailUrls(gridHtml: String): List<String> {
            val found = LinkedHashSet<String>()
            found += DETAIL_URL_ABS.findAll(gridHtml).map { it.value }
            for (m in DETAIL_URL_REL.findAll(gridHtml)) {
                found += DETAIL_HOST + m.groupValues[1]
            }
            return found.toList()
        }

        /** 组合源切段: 取每个详情页 HTML 段 (标记内 URL 不参与解析) */
        internal fun detailSections(source: String): List<String> =
            DETAIL_SECTION_RE.findAll(source).map { it.groupValues[2] }.toList()

        private val DETAIL_SECTION_RE = Regex(
            Regex.escape(DETAIL_MARKER_OPEN) + "(.*?)" + Regex.escape("-->") +
                "(.*?)" + Regex.escape(DETAIL_MARKER_CLOSE),
            RegexOption.DOT_MATCHES_ALL
        )

        /** 详情页行标签 (真实页面形态, 2026-09-08 v1.2 采集包) */
        private const val LABEL_NAME = "课程名称"
        private const val LABEL_TIME = "上课时间"
        private const val LABEL_PLACE = "上课地点"
        private const val LABEL_WEEKS = "上课周次"

        /** 星期X → 1..7 (日/天 都算第 7 天) */
        private val DAY_NAMES = linkedMapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7
        )

        /** "星期二： 第3、4节。" → 星期/节次列表 */
        private val DETAIL_TIME_RE =
            Regex("""星期\s*([一二三四五六日天])\s*[：:]\s*第?\s*([\d、，,\s\-–—~至]+?)\s*节""")

        /** "课程名称：X" (名字到下一个标签为止) */
        private val DETAIL_NAME_RE = Regex("""课程名称\s*[：:]\s*([^<\r\n]+?)\s*<""")

        /** HTML 注释 (名字提取前先剥, 防注释文本带 课程名称： 误命中) */
        private val HTML_COMMENT_RE = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

        /** 数字列表 token: 纯数字或范围 (1-16 / 1~16 / 1至16) */
        private val NUM_TOKEN = Regex("""(\d+)\s*(?:[-–—~至]\s*(\d+))?""")

        /**
         * "2、3、4、5、7、8" → [2,3,4,5,7,8]。顿号枚举是实测唯一形态 (字符集仅 0-9 与 、),
         * 范围/逗号/全角逗号是宽容超集。范围 token 展开为逐周。
         */
        internal fun parseNumberList(text: String): List<Int> {
            val out = mutableListOf<Int>()
            for (m in NUM_TOKEN.findAll(text)) {
                val a = m.groupValues[1].toIntOrNull() ?: continue
                val b = m.groupValues[2].toIntOrNull()
                if (b == null) {
                    out += a
                } else {
                    for (w in a..b) out += w
                }
            }
            return out
        }

        /**
         * 任意周次集合 → 可表示周次段 ([WeekRun])。
         *
         * JwCourse 周次模型是 (startWeek, endWeek, type)。顿号枚举的周次集合常带洞
         * (实测 {2,3,4,5,7..12} 缺第 6 周), 强行 first..last + 每周会给空洞周造课;
         * 拆分规则: 步长 1 → 每周段; 步长 2 → 单/双周段; 两种步长混排时在步长切换处断开,
         * 缺口 > 2 也断开。单元素段 → 每周。
         */
        internal fun splitWeekRuns(weeks: List<Int>): List<WeekRun> {
            val sorted = weeks.filter { it > 0 }.distinct().sorted()
            if (sorted.isEmpty()) return emptyList()
            val runs = mutableListOf<WeekRun>()
            var runStart = 0
            var prev = 0
            var step = 0  // 0=未定, 1=每周, 2=单/双周
            fun flush() {
                if (runStart == 0) return
                val type = if (step == 2) {
                    if (runStart % 2 == 1) TYPE_ODD else TYPE_EVEN
                } else TYPE_DEFAULT
                runs += WeekRun(runStart, prev, type)
            }
            for (w in sorted) {
                when {
                    runStart == 0 -> { runStart = w; prev = w; step = 0 }
                    w == prev + 1 && step != 2 -> { prev = w; step = 1 }
                    w == prev + 2 && step != 1 -> { prev = w; step = 2 }
                    else -> { flush(); runStart = w; prev = w; step = 0 }
                }
            }
            flush()
            return runs
        }

        /**
         * 解析一个 `/course/coursetime/<id>` 详情页 HTML → [DetailPage]。
         * 三行一组 (上课时间/上课地点/上课周次) 按顺序组装; 缺时间行或周次行不完整的组丢弃。
         * 返回 null 表示页面没有 课程名称 或一个完整块都没有 (非详情页/异常页)。
         */
        internal fun parseDetailPage(html: String): DetailPage? {
            val doc = Jsoup.parse(html)
            // 课程名称两种实测/宽容形态: ① 真实页 <p>课程名称：X</p> (标签+名同一文本节点);
            // ② 宽容兼容 th/td 分离行 (课程名称 | X)
            val name = DETAIL_NAME_RE.find(HTML_COMMENT_RE.replace(html, ""))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.select("tr").firstNotNullOfOrNull { tr ->
                    if (tr.selectFirst("th")?.text()?.trim() == LABEL_NAME) {
                        tr.selectFirst("td")?.text()?.trim()
                    } else null
                }
            if (name.isNullOrBlank()) return null

            val blocks = mutableListOf<DetailBlock>()
            var pendingDay = 0
            var pendingNodes: List<Int> = emptyList()
            var pendingRoom = ""
            var pendingValid = false
            for (tr in doc.select("tr")) {
                val th = tr.selectFirst("th")?.text()?.trim()
                val td = tr.selectFirst("td")?.text()?.trim().orEmpty()
                when (th) {
                    LABEL_TIME -> {
                        val m = DETAIL_TIME_RE.find(td)
                        val nodes = m?.let { parseNumberList(it.groupValues[2]) }.orEmpty()
                        pendingDay = m?.let { DAY_NAMES[it.groupValues[1]] } ?: 0
                        pendingNodes = nodes
                        pendingRoom = ""
                        pendingValid = pendingDay > 0 && nodes.isNotEmpty()
                    }
                    LABEL_PLACE -> if (pendingValid) pendingRoom = td
                    LABEL_WEEKS -> {
                        if (pendingValid) {
                            val weeks = parseNumberList(td).filter { it > 0 }
                            if (weeks.isNotEmpty() && pendingNodes.isNotEmpty()) {
                                blocks += DetailBlock(
                                    day = pendingDay,
                                    startNode = pendingNodes.first(),
                                    endNode = pendingNodes.last(),
                                    room = pendingRoom,
                                    weeks = weeks
                                )
                            }
                        }
                        pendingValid = false
                    }
                }
            }
            if (blocks.isEmpty()) return null
            return DetailPage(name, blocks)
        }
    }

    override fun generateCourseList(): List<JwCourse> {
        return parseJson() ?: parseHtml()
    }

    override fun confidence(): Int = when {
        // JSON 路径: courseTimeList 列表 + selectedCourse 学期索引同时出现
        source.contains("\"courseTimeList\"") &&
            source.contains("\"selectedCourse\"") -> 95
        // HTML 路径: 个人课表 + 课程详情链接
        source.contains("个人课表") &&
            source.contains("/course/coursetime/") -> 90
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("\"courseTimeList\"")) add("json:courseTimeList")
        if (source.contains("\"selectedCourse\"")) add("json:selectedCourse")
        if (source.contains("个人课表")) add("title:个人课表")
        if (source.contains("/course/coursetime/")) add("href:/course/coursetime/")
    }

    /**
     * JSON 路径: 在 source 中挑出 `courseTimeList` 数组, 逐 schedule 解码。
     * 期望 source 是拼合后的 JSON (selectedCourse + 各 courseId), 或单课 courseInfo.json。
     * 返回 null 表示 source 不含可解析 JSON, 调用方应回退 HTML 路径。
     */
    private fun parseJson(): List<JwCourse>? {
        val courseTimeList = extractCourseTimeList(source) ?: return null
        val result = mutableListOf<JwCourse>()
        for (index in 0 until courseTimeList.length()) {
            val item = courseTimeList.optJSONObject(index) ?: continue
            val name = item.optString("courseName").trim()
            val place = item.optString("coursePlace").trim()
            val weekInt = item.optIntOrNull("courseWeek") ?: continue
            val timeInt = item.optIntOrNull("courseTime") ?: continue
            if (name.isBlank()) continue

            val weeks = decodeWeeks(weekInt)
            if (weeks.isEmpty()) continue
            val decodedTime = decodeTime(timeInt) ?: continue
            val (day, startNode, endNode) = decodedTime

            // 位图周次集合 → 可表示周次段; 带洞集合拆多段, 禁 first..last 有损合并
            for (run in splitWeekRuns(weeks)) {
                result += JwCourse(
                    name = name,
                    room = place,
                    day = day,
                    startNode = startNode,
                    endNode = endNode,
                    startWeek = run.startWeek,
                    endWeek = run.endWeek,
                    type = run.type
                )
            }
        }
        return result
    }

    /**
     * 兼容两种 JSON 形态:
     *   A. 拼合文档: 顶层含 `courseTimeList` 数组 (ldiex 的 selectedCourse + 各 courseId 拼合场景)
     *   B. 单课文档: 顶层 courseTimeList 或嵌套 data 字段
     */
    private fun extractCourseTimeList(raw: String): JSONArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // 跳过前导 HTML 注释 / 空白, 找首个 '{' 或 '[' 真正 JSON 起点
        // 优先 '{' — 顶层对象更常见, 避开注释里 `[2:]` 这类方括号误命中
        val objStart = trimmed.indexOf('{')
        val arrStart = trimmed.indexOf('[')
        val jsonStart = when {
            objStart >= 0 -> objStart
            arrStart >= 0 -> arrStart
            else -> return null
        }
        val jsonBody = trimmed.substring(jsonStart)
        return try {
            when (jsonBody.first()) {
                '{' -> {
                    val obj = JSONObject(jsonBody)
                    obj.optJSONArray("courseTimeList")
                        ?: obj.optJSONObject("data")?.optJSONArray("courseTimeList")
                }
                '[' -> JSONArray(jsonBody)
                else -> null
            }
        } catch (e: Exception) {
            // JSON 解析失败时返回 null 让调用方回退 HTML 路径; 不打日志避免导入侧噪音
            null
        }
    }

    /**
     * 把 courseWeek 整数转成位图, 取所有 bit=1 的位置 (1-indexed 周次)。
     * 例: courseWeek=21 (0b10101) → [1, 3, 5] (1-indexed)
     */
    private fun decodeWeeks(weekInt: Int): List<Int> {
        if (weekInt <= 0) return emptyList()
        val weeks = mutableListOf<Int>()
        var bit = 0
        var n = weekInt
        while (n > 0) {
            if (n and 1 == 1) weeks += bit + 1
            bit++
            n = n ushr 1
        }
        return weeks
    }

    /**
     * courseTime 整数 → (day, startNode, endNode)。解码规则见 class kdoc。
     * 返回 null 表示 dayBits 不在 [DAY_BITS] 内 (ldiex 字典 hardcode), 数据异常。
     */
    private fun decodeTime(timeInt: Int): Triple<Int, Int, Int>? {
        if (timeInt <= 0) return null
        val binary = java.lang.Integer.toBinaryString(timeInt)
        if (binary.length <= 12) return null
        val dayBits = binary.substring(0, binary.length - 12)
        val day = DAY_BITS[dayBits] ?: return null

        val nodeBits = binary.substring(binary.length - 12).reversed()
        val nodes = mutableListOf<Int>()
        for ((idx, c) in nodeBits.withIndex()) {
            if (c == '1') nodes += idx + 1
        }
        if (nodes.isEmpty()) return null
        return Triple(day, nodes.first(), nodes.last())
    }

    /**
     * 把 [weeks] 列表归类为 type: 全部单周=1, 全部双周=2, 混合=0。
     */
    private fun weeksToType(weeks: List<Int>): Int = when {
        weeks.all { it % 2 == 1 } -> TYPE_ODD
        weeks.all { it % 2 == 0 } -> TYPE_EVEN
        else -> TYPE_DEFAULT
    }

    /**
     * HTML 路径: 服务端课表网格 + (可选) 组合源里的详情页段。
     *
     * 网格定位 (课程名 × 星期 × 节次) 是权威; 详情块仅 enrich 周次/教室,
     * 不会凭详情造网格里没有的课。没有详情段时整体回退 1-16 占位。
     */
    private fun parseHtml(): List<JwCourse> {
        val table = Jsoup.parse(source).select("table").firstOrNull { table ->
            table.select("thead th").any { it.text().contains("节次/星期") } &&
                table.select("a[href*=/course/coursetime/]").isNotEmpty()
        } ?: return emptyList()

        // 平铺网格格子 → (name, day, node); 同格同名去重由 distinct 完成
        val entries = mutableListOf<Triple<String, Int, Int>>()
        table.select("tbody tr").forEach { row ->
            val node = row.selectFirst("th")?.text()?.trim()?.toIntOrNull() ?: return@forEach
            row.select("> td").forEachIndexed { index, cell ->
                val day = index + 1
                cell.select("a[href*=/course/coursetime/]").map { it.text().trim() }
                    .filter { it.isNotBlank() }.distinct().forEach { name ->
                        entries += Triple(name, day, node)
                    }
            }
        }

        // 详情段 → 页面模型, 平铺成 (课程名, 块) 列表 (网格链接文本与详情页 课程名称 同源)
        val allBlocks = detailSections(source)
            .mapNotNull { parseDetailPage(it) }
            .flatMap { page -> page.blocks.map { page.courseName to it } }

        // 每个 (name, day, node) 找第一个 (同名 + 同日 + 节次落块区间) 的详情块;
        // 分组键带块身份 → 同一块命中的相邻格子自然共享周次, 后续按组合并节次
        fun blockIndexFor(name: String, day: Int, node: Int): Int? {
            allBlocks.forEachIndexed { i, (bname, b) ->
                if (bname == name && b.day == day && node in b.startNode..b.endNode) return i
            }
            return null
        }

        val grouped = entries.groupBy { (name, day, node) ->
            Triple(name, day, blockIndexFor(name, day, node))
        }
        val result = mutableListOf<JwCourse>()
        for ((key, group) in grouped) {
            val (name, day, blockIdx) = key
            val block = blockIdx?.let { allBlocks[it].second }
            val room = block?.room.orEmpty()
            val runs = block?.weeks?.let { splitWeekRuns(it) }
                ?: listOf(WeekRun(PROVISIONAL_START_WEEK, PROVISIONAL_END_WEEK, TYPE_DEFAULT))

            // 同组内相邻节次合并 (10,11 → 10-11)
            val segments = mutableListOf<Pair<Int, Int>>()
            for (n in group.map { it.third }.sorted()) {
                val last = segments.lastOrNull()
                if (last != null && n == last.second + 1) segments[segments.lastIndex] = last.first to n
                else segments += n to n
            }
            for (seg in segments) {
                for (run in runs) {
                    result += JwCourse(
                        name = name,
                        room = room,
                        day = day,
                        startNode = seg.first,
                        endNode = seg.second,
                        startWeek = run.startWeek,
                        endWeek = run.endWeek,
                        type = run.type
                    )
                }
            }
        }
        return result
    }

    /** 详情页解析模型: 课程名 + 时间/地点/周次块列表 */
    internal data class DetailPage(val courseName: String, val blocks: List<DetailBlock>)

    /** 单个时间块: 星期 + 节次区间 + 教室 + 周次集合 */
    internal data class DetailBlock(
        val day: Int,
        val startNode: Int,
        val endNode: Int,
        val room: String = "",
        val weeks: List<Int> = emptyList()
    )

    /** 可表示周次段: startWeek..endWeek + type (每周/单周/双周) */
    internal data class WeekRun(val startWeek: Int, val endWeek: Int, val type: Int)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
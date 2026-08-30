package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.jsoup.Jsoup

/**
 * 正方教务（新版）课表解析器 — T4 修复版。
 *
 * 适配 zf_new 协议学校。正方新版教务（jwglxt，SpringMVC / Vue）课表：
 *   1. 数据可能嵌入在 `<script>` 标签 JSON 中（kbList API 响应直出 / Vue data）
 *   2. 或渲染为 HTML 表格（三种实证变体，见 [parseHtmlTable] 容器优先级）
 *
 * T4 修复要点：
 *   - JSON marker 收紧为 "kbList" > "xskbcx_json" > "kbxx"（删臆造的 "tmp_list" 与
 *     会命中 URL 子串的 "xskbcx"），严格 JSON 键匹配（findJsonKeyIndex）
 *   - 字段优先级重排：room=cdmc 首位（jasmc 是教学班场地不当教室）、teacher=xm/jsxm、
 *     day=xqj 数字 + xqjmc 文本兜底
 *   - jc 节次串解析 parseSectionRanges：支持 "1-2" 范围 / "0102" 补零 / "3-4,6-7" 多段
 *   - zcd 周次加固：剥 { } 第，(单)(单周)(双)(双周) 显式枚举
 *   - CF 防御：元素含 teaxms/jxcdmcs/jcdm2/zcs 任一字段即跳过（青果页面不产脏数据）
 *   - 深层包装穿透：{Msg,code,data:[{kbList:[…]}]} 递归下钻 findKbListArray
 *
 * 参考：
 *   - dIT8Zv/WakeupSchedule_BUPT NewZFParser.kt
 *   - 拾光 shiguang_warehouse zhengfang_01.js（kbgrid/kblist 双视图）
 *   - zfn_api / FlowCourse（kbList 主流形态与 jc 多形态交叉验证）
 */
class JwNewZfParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        // 1. 尝试从页面提取嵌入的 JSON
        parseEmbeddedJson().takeIf { it.isNotEmpty() }?.let { return it }

        // 2. HTML 表格解析
        return parseHtmlTable()
    }

    // ─── JSON 提取 ────────────────────────────────────────────

    /**
     * 标记关键字（按上游 NewZFParser.kt / 拾光 zhengfang_01.js / FlowCourse / zfn_api /
     * GZUS-PRO / SHUFEZJ / NBUT / HUEL / QUT 调研结论排序）：
     *   - "kbList": 主流, FlowCourse 嗅探条件原文 text.indexOf('kbList') !== -1
     *   - "xskbcx_json": SHUFEZJ 注释提及, 极少版本
     *   - "kbxx": v1.0.29 兼容(老 fixture 使用), 保留但降级到最后; CF 防御靠字段 guard
     *
     * 已剔除（调研确认无佐证或是 bug 源）：
     *   - "tmp_list": 无任何上游/适配器实证, 属臆造
     *   - "xskbcx": 作 marker 会命中 URL 子串如 /kbcx/xskbcx_cxXsgrkb.html, 产生假 JSON
     */
    private val JSON_MARKERS = listOf("\"kbList\"", "\"xskbcx_json\"", "\"kbxx\"")

    private fun parseEmbeddedJson(): List<JwCourse> {
        for (marker in JSON_MARKERS) {
            val idx = findJsonKeyIndex(source, marker) ?: continue
            val jsonStr = extractBalanced(source, idx) ?: continue
            val courses = parseCourseJsonArray(jsonStr)
            if (courses.isNotEmpty()) return courses
        }

        // 尝试纯 JSON 页面（API 响应被 WebView 直接渲染, 或移动端深层包装）
        val trimmed = source.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val courses = parseCourseJsonArray(trimmed)
            if (courses.isNotEmpty()) return courses
        }

        return emptyList()
    }

    /**
     * 严格 JSON 键搜索: 找到 marker 后, 要求后续非空字符为 JSON 开始符 ({ 或 [)。
     * 命中位置返回 JSON 开始符的下标, 以便 extractBalanced 从该处起算。
     *
     * 反例(应跳过):
     *   ".../kbcx/xskbcx_cxXsgrkb.html..."  // URL 子串, xskbcx 后面是 '_', 非空白+{
     *   "var foo = \"xskbcx_json_other\""   // 标识符前缀, xskbcx_json 后面是 '_'
     */
    private fun findJsonKeyIndex(s: String, quotedKey: String): Int? {
        var from = 0
        while (true) {
            val idx = s.indexOf(quotedKey, from)
            if (idx < 0) return null
            val after = idx + quotedKey.length
            if (after >= s.length) return null
            // 跳过空白
            var p = after
            while (p < s.length && s[p].isWhitespace()) p++
            if (p >= s.length) { from = idx + 1; continue }
            when (s[p]) {
                '{', '[' -> return p
                else -> { from = idx + 1; continue }
            }
        }
    }

    /** 从 start 位置提取配对的 JSON 数组/对象（括号匹配） */
    private fun extractBalanced(s: String, start: Int): String? {
        if (start >= s.length) return null
        val open = s[start]
        val close = when (open) {
            '[' -> ']'
            '{' -> '}'
            else -> return null
        }
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            if (c == '\\') { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                open -> depth++
                close -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun parseCourseJsonArray(jsonStr: String): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        try {
            // 可能是数组直接开始，也可能包在对象里
            val arr: JSONArray = when (jsonStr[0]) {
                '[' -> JSONArray(jsonStr)
                '{' -> {
                    val obj = org.json.JSONObject(jsonStr)
                    findKbListArray(obj) ?: return emptyList()
                }
                else -> return emptyList()
            }

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue

                // ── CF(青果) 字段白名单防御 ──────────────────────
                // CF 的 kbxx 元素字段: kcmc/teaxms/jxcdmcs/xq/jcdm2/zcs,
                // 与 zf_new 别名不重合; 检测任一 CF 独有字段 → 整条跳过
                if (o.has("teaxms") || o.has("jxcdmcs") || o.has("jcdm2") || o.has("zcs")) continue

                // ── 课程名(必填) ────────────────────────────────
                val name = firstStr(o, "kcmc", "kcm", "kc_mc", "courseName", "rlkcmc", "jxbmc")
                if (name.isBlank()) continue

                // ── 教师(主流=xm, jsxm=变体) ────────────────────
                val teacher = firstStr(o, "xm", "jsxm", "teacher", "attendClassTeacher", "skjs")

                // ── 教室(主流=cdmc; jasmc 是教学班场地, 不当教室) ──
                val room = firstStr(o, "cdmc", "classroomName", "jxcd", "jsmc", "jxlh", "jasdm")

                // ── 星期(优先数字 xqj, 文本 xqjmc 兜底) ──────────
                val day = firstInt(o, "xqj", "kcxq", "xq", "classDay", "skxq")
                    ?: xqjmcToInt(o) ?: continue

                // ── 节次(字符串, 支持范围/补零/多段) ──────────────
                val jcStr = firstStr(o, "jcs", "jc", "classSessions", "ksjcsd", "ksjc")
                if (jcStr.isBlank()) continue  // 缺节次串 → 不产课程

                // ── 周次 ───────────────────────────────────────
                val zcStr = firstStr(o, "zcd", "kkzc", "zc", "classWeek", "skzc")
                val ranges = parseWeekStr(zcStr)

                // ── 节次展开: '1-2' → [(1,2)]; '3-4,6-7' → [(3,4),(6,7)]; '0102' → [(1,2)] ──
                for (node in parseSectionRanges(jcStr)) {
                    for (r in ranges) {
                        result += JwCourse(
                            name = name,
                            room = room,
                            teacher = teacher,
                            day = day.coerceIn(1, 7),
                            startNode = node.first.coerceAtLeast(1),
                            endNode = node.second.coerceAtLeast(node.first),
                            startWeek = r.first,
                            endWeek = r.second,
                            type = r.third
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // JSON 解析失败，静默（tryAllParsers 候选链兜底）
        }
        return result
    }

    /**
     * 递归查找 JSON 对象树中名为 kbList / kbxx / tmp_list 的 JSONArray。
     *
     * 移动端标准版真实形态 (FlowCourse 抓包):
     *   {"Msg":"success","code":"1","data":[{"date":[...],"kbList":[...]}]}
     * kbList 在 data[0] 对象里, 必须递归下钻。
     */
    private fun findKbListArray(obj: org.json.JSONObject, depth: Int = 0): JSONArray? {
        if (depth > 4) return null  // 防御: 最多下钻 4 层
        for (key in listOf("kbList", "kbxx", "tmp_list")) {
            val v = obj.optJSONArray(key)
            if (v != null) return v
        }
        for (key in obj.keys()) {
            val v = obj.optJSONObject(key)
            if (v != null) {
                findKbListArray(v, depth + 1)?.let { return it }
                continue
            }
            val arr = obj.optJSONArray(key)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val inner = arr.optJSONObject(i) ?: continue
                    findKbListArray(inner, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * 节次字符串 → List<startNode to endNode>
     *
     * 三种输入形态(zfn_api/FlowCourse/NBUT/SHUFEZJ 四源交叉确认):
     *   1. 范围串: "1-2" / "6-7" / "5" — 按 '-' split
     *   2. 两位补零: "0102" / "0304" — 偶数长度纯数字串, 每 2 位一节
     *   3. 多段逗号: "3-4,6-7" — 每段独立, 拆多条课程
     */
    private fun parseSectionRanges(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        s.split(",", "，", ";", "；").forEach { seg ->
            val t = seg.trim()
            if (t.isEmpty()) return@forEach
            // 形态 1: 含 '-' 的范围串
            if (t.contains("-")) {
                val parts = t.split("-", limit = 2).map { it.trim() }
                val a = parts.getOrNull(0)?.toIntOrNull()
                val b = parts.getOrNull(1)?.toIntOrNull()
                if (a != null && b != null && a in 1..16 && b in 1..16) {
                    out += a to b
                }
                return@forEach
            }
            // 纯数字
            val digits = t.filter { it.isDigit() }
            if (digits.isEmpty()) return@forEach
            // 形态 2: 偶数长度补零串 ("0102" → 01,02)
            if (digits.length >= 2 && digits.length % 2 == 0 && digits.startsWith("0")) {
                var i = 0
                var ok = true
                val nums = mutableListOf<Int>()
                while (i + 1 < digits.length) {
                    val v = digits.substring(i, i + 2).toIntOrNull()
                    if (v == null || v !in 1..16) { ok = false; break }
                    nums += v
                    i += 2
                }
                if (ok && nums.size >= 2) {
                    // 相邻两位一组: 0102 → (1,2), 01121314 → (1,2),(13,14)
                    var k = 0
                    while (k + 1 < nums.size) {
                        out += nums[k] to nums[k + 1]
                        k += 2
                    }
                } else if (ok && nums.size == 1) {
                    out += nums[0] to nums[0]
                }
                return@forEach
            }
            // 形态 1 变体: 纯数字单节 "5"
            val v = digits.toIntOrNull()
            if (v != null && v in 1..16) out += v to v
        }
        return out
    }

    /**
     * xqjmc 星期文本 → 1..7, 无法识别返回 null。
     * 兼容 "星期一"/"周一"/"星期日"/"周日"/"周天"
     */
    private fun xqjmcToInt(o: org.json.JSONObject): Int? {
        val s = o.optString("xqjmc", "").trim()
        if (s.isEmpty()) return null
        val zh = charArrayOf('一', '二', '三', '四', '五', '六', '日', '天')
        for (c in s) {
            val idx = zh.indexOf(c)
            if (idx >= 0) {
                // '日'/'天' 在下标 6/7 → 都是 7 (周日)
                return if (idx >= 6) 7 else idx + 1
            }
        }
        return null
    }

    private fun firstStr(o: org.json.JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun firstInt(o: org.json.JSONObject, vararg keys: String): Int? {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank()) return v.toIntOrNull()
        }
        return null
    }

    /** 周次字符串 → (start, end, type) 范围列表（T4 加固：剥 { } 第，单双周显式枚举） */
    private fun parseWeekStr(s0: String): List<Triple<Int, Int, Int>> {
        // 1. 先剥离花括号与 '第' 字 (SHUFEZJ: '{第1-16周}')
        val s = s0.replace("{", "").replace("}", "").replace("第", "").trim()
        if (s.isBlank()) return listOf(Triple(1, 16, 0))
        val result = mutableListOf<Triple<Int, Int, Int>>()

        // 2. bitmap 模式：11111111111100000（每位 = 第 N 周）
        if (s.length >= 10 && s.all { it == '0' || it == '1' }) {
            val weeks = s.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
            return bitsToRanges(weeks)
        }

        // 3. 范围/列表模式: "1-16" / "1-16周" / "1-16周(单)" / "1,3,5,7" / "1-8,11-16周(双)"
        s.split(",", "，", ";", "；").forEach { part0 ->
            val part = part0.trim()
            if (part.isEmpty()) return@forEach
            // 单双周后缀显式枚举 (防误判 '单元'/'双方' 等词)
            val type = when {
                part.contains("(单)") || part.contains("(单周)") || part.endsWith("单") || part.endsWith("单周") -> 1
                part.contains("(双)") || part.contains("(双周)") || part.endsWith("双") || part.endsWith("双周") -> 2
                else -> 0
            }
            val cleaned = part
                .replace("周", "")
                .replace("(", "").replace(")", "")
                .replace("（", "").replace("）", "")
                .trim()
            if (cleaned.contains("-")) {
                val parts = cleaned.split("-", limit = 2)
                val start = parts.getOrNull(0)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                val end = parts.getOrNull(1)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                if (start != null && end != null) result += Triple(start, end, type)
            } else {
                val v = cleaned.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
                if (v != null) result += Triple(v, v, type)
            }
        }
        return if (result.isEmpty()) listOf(Triple(1, 16, 0)) else result
    }

    private fun bitsToRanges(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var i = 0
        while (i < weeks.size) {
            val start = weeks[i]
            var end = start
            if (i + 1 < weeks.size && weeks[i + 1] - start == 2) {
                // 单/双周模式
                end = weeks[i + 1]
                var k = i + 1
                while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 2) { k++; end = weeks[k] }
                val type = if (start % 2 == 1) 1 else 2
                result += Triple(start, end, type)
                i = k + 1
            } else if (i + 1 < weeks.size && weeks[i + 1] - start == 1) {
                // 连续周
                end = weeks[i + 1]
                var k = i + 1
                while (k + 1 < weeks.size && weeks[k + 1] - weeks[k] == 1) { k++; end = weeks[k] }
                result += Triple(start, end, 0)
                i = k + 1
            } else {
                result += Triple(start, end, 0)
                i++
            }
        }
        return result
    }

    // ─── HTML 表格解析（兜底） ─────────────────────────────────

    /** 移植自 JwOldZfParser 的 (N-M节) 模式, table1/kbgrid 变体共用 */
    private val NODE_PATTERN = Regex("""\(\d{1,2}[-]*\d*节""")

    /**
     * 新正方 HTML 渲染后的课表解析。容器优先级:
     *   1. #table1 + td.festival + div.title + p[title=...] (上游 NewZFParser.kt)
     *   2. #kbgrid_table_0 + td.td_wrap + .timetable_con.text-left (shiguang 网格)
     *   3. #kblist_table tbody + td:first-child 节次 + td[1] .title (shiguang 列表)
     *   4. #kbtable/#kbgrid/.kbcapi-table/[id*=kb] (现有强智/kbgrid 兜底)
     *   5. parseHtmlTableFromQz 兜底 (空列表返回)
     */
    private fun parseHtmlTable(): List<JwCourse> {
        val doc = Jsoup.parse(source)

        // ── 1. 上游 NewZFParser.kt 期望结构 ──
        val table1 = doc.getElementById("table1")
        if (table1 != null && table1.selectFirst("td.festival") != null) {
            val result = parseTable1FestivalView(table1)
            if (result.isNotEmpty()) return result
        }

        // ── 2. shiguang 网格视图 ──
        val gridTable = doc.getElementById("kbgrid_table_0")
        if (gridTable != null) {
            val result = parseKbgridTable0(gridTable)
            if (result.isNotEmpty()) return result
        }

        // ── 3. shiguang 列表视图 ──
        val listTable = doc.getElementById("kblist_table")
        if (listTable != null) {
            val result = parseKblistTable(listTable)
            if (result.isNotEmpty()) return result
        }

        // ── 4. 现有强智 / kbgrid 兜底(维持原状) ──
        val container = doc.getElementById("kbtable")
            ?: doc.getElementById("kbgrid")
            ?: doc.selectFirst("table.el-table__body")
            ?: doc.selectFirst(".kbcapi-table")
            ?: doc.selectFirst("[id*=kb]")
        if (container != null) {
            val result = parseKbcontentContainer(container)
            if (result.isNotEmpty()) return result
        }

        // ── 5. parseHtmlTableFromQz 兜底(空列表返回, 不抛异常) ──
        return parseHtmlTableFromQz()
    }

    /**
     * 上游 NewZFParser.kt (dIT8Zv/WakeupSchedule_BUPT) 移植实现。
     * 适配 table#table1 + td.festival + div.title + p[title=教师/上课地点/节/周]
     */
    private fun parseTable1FestivalView(table: org.jsoup.nodes.Element): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val trs = table.getElementsByTag("tr")
        for (tr in trs) {
            // 行头: 节次数字 (class="festival")
            val nodeStr = tr.getElementsByClass("festival").text().trim()
            if (nodeStr.isEmpty()) continue
            val rowNode = nodeStr.toIntOrNull() ?: continue

            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                // 跳过无 id 的 td(表头星期单元格)
                val tdId = td.attr("id")
                if (tdId.isEmpty()) continue
                // td.id 首字符 → 星期 (id 格式: "日-节" 如 "2-1")
                val day = tdId[0].toString().toIntOrNull() ?: continue
                if (day !in 1..7) continue

                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    // 跳过空白 div
                    val courseText = div.text().trim()
                    if (courseText.length <= 1) continue
                    // 课程名(div.title)
                    val courseName = div.getElementsByClass("title").text().trim()
                    if (courseName.isEmpty()) continue

                    // p[title=教师/上课地点/节/周]
                    var teacher = ""
                    var room = ""
                    var timeStr = ""
                    val pList = div.getElementsByTag("p")
                    for (p in pList) {
                        when (p.attr("title")) {
                            "教师" -> teacher = p.text().trim()
                            "上课地点" -> room = p.text().trim()
                            "节/周", "周/节" -> timeStr = p.text().trim()
                        }
                    }
                    if (timeStr.isEmpty()) continue

                    // 节/周文本解析: "(N-M节)X-Y周(单),A-B周(双)"
                    val nodeInfo = NODE_PATTERN.find(timeStr)?.value ?: continue
                    val nodes = nodeInfo.substring(1).removeSuffix("节").split("-")
                    var startNode = nodes.getOrNull(0)?.toIntOrNull() ?: continue
                    val endNode = nodes.getOrNull(1)?.toIntOrNull() ?: startNode
                    // (N-M节) 优先, 行头 rowNode 仅作缺省
                    if (startNode <= 0) startNode = rowNode

                    // 周次段
                    val weekList = NODE_PATTERN.replace(timeStr, "").split(",")
                    for (weekPart in weekList) {
                        val trimmed = weekPart.trim()
                        if (trimmed.isEmpty()) continue
                        val ranges = parseWeekStr(trimmed)
                        for (r in ranges) {
                            result += JwCourse(
                                name = courseName,
                                room = room,
                                teacher = teacher,
                                day = day,
                                startNode = startNode,
                                endNode = endNode,
                                startWeek = r.first,
                                endWeek = r.second,
                                type = r.third
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * shiguang_warehouse resources/zhengfang_jiaowu/zhengfang_01.js parserTbale 移植。
     * 适配 #kbgrid_table_0 + td.td_wrap + .timetable_con.text-left
     */
    private fun parseKbgridTable0(table: org.jsoup.nodes.Element): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val trs = table.getElementsByTag("tr")
        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                val tdId = td.attr("id")
                if (tdId.isEmpty()) continue
                val day = tdId.split("-").getOrNull(0)?.toIntOrNull() ?: continue
                if (day !in 1..7) continue

                val timetableCons = td.getElementsByClass("timetable_con")
                for (tc in timetableCons) {
                    val titleDiv = tc.getElementsByClass("title").firstOrNull()
                    val name = titleDiv?.text()?.trim().orEmpty()
                    if (name.isEmpty()) continue

                    // p[0] = 节/周; p[1] = 地点; p[2] = 教师
                    val pList = tc.getElementsByTag("p")
                    if (pList.size < 3) continue  // 缺字段直接跳过(对齐 grid_missing_fields 边界)
                    val infoStr = pList[0].text().trim()
                    val position = pList[1].text().trim()
                    val teacher = pList[2].text().trim()

                    // 节次解析: infoStr 必须含 "(N-M节)"
                    val nodeMatch = NODE_PATTERN.find(infoStr) ?: continue
                    val nodes = nodeMatch.value.substring(1).removeSuffix("节").split("-")
                    val startNode = nodes.getOrNull(0)?.toIntOrNull() ?: continue
                    val endNode = nodes.getOrNull(1)?.toIntOrNull() ?: startNode

                    // 周次段: 剥 (N-M节) 后按逗号拆
                    val weekStr = NODE_PATTERN.replace(infoStr, "").trim()
                    val ranges = parseWeekStr(weekStr)
                    if (ranges.isEmpty()) continue

                    for (r in ranges) {
                        result += JwCourse(
                            name = name,
                            room = position,
                            teacher = teacher,
                            day = day,
                            startNode = startNode,
                            endNode = endNode,
                            startWeek = r.first,
                            endWeek = r.second,
                            type = r.third
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * shiguang_warehouse zhengfang_01.js parserList 移植。
     * 适配 #kblist_table tbody 按星期分组 + td[0] 节次 + td[1] .title + 3 个带前缀的 font
     *
     * 注意: tbody[0] 是视图控制, tbody[1..7]=周一..周日 (index 0 跳过)。
     */
    private fun parseKblistTable(table: org.jsoup.nodes.Element): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val tbodies = table.getElementsByTag("tbody")
        for ((index, tbody) in tbodies.withIndex()) {
            if (index == 0) continue
            if (index > 7) break  // 防御越界
            val day = index  // 1=周一, 2=周二, ..., 7=周日

            val trs = tbody.getElementsByTag("tr")
            for (tr in trs) {
                val tds = tr.getElementsByTag("td")
                if (tds.size < 2) continue  // 表头行 th-only 无 td, 跳过
                val sectionStr = tds[0].text().trim()
                if (sectionStr.isEmpty()) continue
                // 解析节次: "1-2" / "5-6" → (start, end)
                val nodes = sectionStr.split("-", limit = 2)
                val startNode = nodes.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val endNode = nodes.getOrNull(1)?.trim()?.toIntOrNull() ?: startNode

                // td[1] 内 .title div
                val titleDiv = tds[1].getElementsByClass("title").firstOrNull()
                val name = titleDiv?.text()?.trim().orEmpty()
                if (name.isEmpty()) continue

                // 整块文本 + 前缀正则剥字段
                //   周数：1-16周 / 上课地点：教学楼A101 / 教师　：张老师 (U+3000 全角空格)
                val allText = tds[1].text()
                val weekMatch = Regex("""周数\s*[：:]\s*(.+?)(?=上课地点|教师|$)""").find(allText)
                val roomMatch = Regex("""上课地点\s*[：:]\s*(.+?)(?=教师|$)""").find(allText)
                val teacherMatch = Regex("""教师[\s　]*[：:]\s*(.+?)$""").find(allText)

                val weekStr = weekMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val room = roomMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val teacher = teacherMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

                if (weekStr.isEmpty()) continue
                val ranges = parseWeekStr(weekStr)
                if (ranges.isEmpty()) continue

                for (r in ranges) {
                    result += JwCourse(
                        name = name,
                        room = room,
                        teacher = teacher,
                        day = day,
                        startNode = startNode,
                        endNode = endNode,
                        startWeek = r.first,
                        endWeek = r.second,
                        type = r.third
                    )
                }
            }
        }
        return result
    }

    /**
     * 现有强智 kbcontent 容器解析（原 parseHtmlTable 内循环提取为命名函数）。
     */
    private fun parseKbcontentContainer(container: org.jsoup.nodes.Element): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val trs = container.getElementsByTag("tr")
        var nodeCount = 0
        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) continue
            val firstCellText = tds.firstOrNull()?.text()?.trim().orEmpty()
            val isSectionHeader = firstCellText.contains("节") &&
                tds.none { it.getElementsByClass("kbcontent").isNotEmpty() }
            if (isSectionHeader) continue
            nodeCount++
            var day = 0
            for (td in tds) {
                day++
                val cells = td.getElementsByClass("kbcontent")
                if (cells.isEmpty()) continue
                for (cell in cells) {
                    val html = cell.html()
                    if (html.isBlank()) continue
                    val parts = html.split("-----")
                    for (part in parts) {
                        result += parseCell(part.trim(), day, nodeCount)
                    }
                }
            }
        }
        return result
    }

    private fun parseCell(html: String, day: Int, nodeCount: Int): List<JwCourse> {
        val cellDoc = Jsoup.parse(html)
        val name = try {
            Jsoup.parse(html.substringBefore("<font").trim()).text()
        } catch (e: Exception) {
            html.substringBefore("<font").trim()
        }
        if (name.isBlank()) return emptyList()

        val teacher = cellDoc.getElementsByAttributeValue("title", "老师").text().trim()
        val room = cellDoc.getElementsByAttributeValue("title", "教室").text().trim()
        val weekStr = cellDoc.getElementsByAttributeValue("title", "周次(节次)")
            .text().substringBefore("(周)")

        val ranges = parseWeekStr(weekStr)
        val node = nodeCount * 2 - 1

        // 展开全部周次段（之前只取 ranges.first()，会丢失 "1-11周(单),13-16周" 的后半段）
        return ranges.map { r ->
            JwCourse(
                name = name,
                room = room,
                teacher = teacher,
                day = day,
                startNode = node,
                endNode = node + 1,
                startWeek = r.first,
                endWeek = r.second,
                type = r.third
            )
        }
    }

    /** 完全 fallback 到 QZ 解析逻辑 */
    private fun parseHtmlTableFromQz(): List<JwCourse> {
        return JwQzParser(source).generateCourseList()
    }
}

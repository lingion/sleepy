package com.lingion.sleepy.data.jw

/**
 * 正方教务（老版）课表解析器 — issue #5。
 *
 * 适配 zf / zf_1 协议学校（default2.aspx 时代，个人课表页 xskbcx.aspx）。
 * 移植上游 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) ZhengFangParser.kt：
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/ZhengFangParser.kt
 *
 * 页面结构：
 *   - 外层表格 id="Table1"（部分学校 class="blacktab" 或无标识，由兜底链处理 — T1 G5）
 *   - 节次行头："第N节"（数字或中文数字一~二十）；合并行头 "第N-M节" / "第N,M节" /
 *     "第一节-第二节" 按首段 N 解析（T1 G6，有意偏离上游 — 上游返回 -1 导致整行错位）
 *   - 课程单元格（<a> 内 <br> 分隔）：
 *       课程名<br>[属性词]<br>{第N-M周}[|单周|双周]<br>[老师<br>]教室
 *   - 同格多门课用 <br><br>（type=0）或 <br><br><br>（type=0 异常变体）分隔
 *
 * type 参数对应上游 zfType：0 = 通用变体，1 = zf_1 变体（单元格无 <a> 链接，
 * 内容以空格分隔且周次带花括号）。
 *
 * T1 有意偏离上游的修复（上游同病）：
 *   G1  parseTime 的 (N-M节) 写回 startNode（上游本就写回，sleepy 移植时丢成死代码）
 *   G2  COURSE_PROPERTY 对齐上游 47 项（sleepy 原缺 23 项）
 *   G3  parseImportBean1 的 hasTypeFlag 每门课后复位（上游永不复位）
 *   G4  parseImportBean1 时间 token 为末尾 token 时不再 split[preIndex+1] 越界
 *   G5  表格选择器兜底 blacktab / 含"星期一"的第一个 table
 *   G6  合并节次行头按首段解析（上游返回 -1）
 *   G7  "周天"→7 别名（sleepy 扩展，上游仅"周日"）
 *   G8  OTHER_HEADER 补"中午"（sleepy 扩展，上游无）
 *   G12 parseTime 补 result[1]=step（上游 L214, sleepy 移植漏行 — 缺此行 endNode 恒 startNode-1）
 */
class JwOldZfParser(source: String, internal val type: Int = 0) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val doc = org.jsoup.Jsoup.parse(source)
        // T1 G5: #Table1 → table.blacktab → 文本含"星期一"的第一个 table
        val table1 = doc.getElementById("Table1")
            ?: doc.select("table.blacktab").firstOrNull()
            ?: pickTableByMonday(doc)
            ?: return emptyList()
        val trs = table1.getElementsByTag("tr")
        val importBeanList = ArrayList<ImportBean>()
        var node = -1
        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            var countFlag = false
            var countDay = 0
            for (td in tds) {
                val courseSource = td.text().trim()
                if (courseSource.length <= 1) {
                    if (countFlag) {
                        countDay++
                    }
                    continue
                }
                if (OTHER_HEADER.contains(courseSource)) {
                    // 表头行（"时间" "星期一" "中午" 等组头）
                    continue
                }
                val result = parseHeaderNodeString(courseSource)
                if (result != -1) {
                    // T1 G6: 合并行头"第N-M节"也在此返回首段 N, 不落入下方 countDay++
                    node = result
                    countFlag = true
                    continue
                }
                countDay++
                when (type) {
                    0 -> importBeanList.addAll(parseImportBean(countDay, td.html(), node))
                    else -> importBeanList.addAll(parseImportBean1(countDay, courseSource, node))
                }
            }
        }
        return importList2CourseList(importBeanList, source)
    }

    /** type=0：标准老正方单元格，<a>课程名<br>周次<br>[老师<br>教室] </a>，多门课 <br><br> 分隔（未改动） */
    private fun parseImportBean(cDay: Int, html: String, node: Int): List<ImportBean> {
        val courses = ArrayList<ImportBean>()
        var isAbnormal = false
        val inner = html.substringBeforeLast("</td>")
        val courseSplits = if (inner.contains("<br><br><br>")) {
            isAbnormal = true
            inner.split("<br><br><br>")
        } else {
            inner.split("<br><br>")
        }
        for (courseStr in courseSplits) {
            val split = courseStr.substringAfter("\">").substringBeforeLast("</a>").split("<br>")
                .map { stripAnchorTag(it.trim()) }
            if (split.isEmpty() || split.size < 3) continue
            val temp: ImportBean = if (split[1] in COURSE_PROPERTY) {
                if (split.size == 4) {
                    ImportBean(startNode = node, name = split[0],
                        timeInfo = split[2],
                        room = split[3], teacher = "", cDay = cDay)
                } else {
                    ImportBean(startNode = node, name = split[0],
                        timeInfo = split[2],
                        room = split[4], teacher = split[3], cDay = cDay)
                }
            } else {
                if (split.size == 3) {
                    if (!isAbnormal) {
                        ImportBean(startNode = node, name = split[0],
                            timeInfo = split[1],
                            room = split[2], teacher = "", cDay = cDay)
                    } else {
                        ImportBean(startNode = node, name = split[0],
                            timeInfo = split[1],
                            room = "", teacher = split[2], cDay = cDay)
                    }
                } else {
                    ImportBean(startNode = node, name = split[0],
                        timeInfo = split[1],
                        room = split[3], teacher = split[2], cDay = cDay)
                }
            }
            courses.add(temp)
        }
        return courses
    }

    /**
     * type=1（zf_1）：单元格无 <a>，以空格分隔，周次带花括号。
     * T1 修复: G3 hasTypeFlag 每门课后复位 / G4 末尾 token 越界守卫 / DEF-1 preIndex==0 守卫。
     */
    private fun parseImportBean1(cDay: Int, source: String, node: Int): List<ImportBean> {
        val courses = ArrayList<ImportBean>()
        val split = source.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
        var preIndex = -1
        var hasTypeFlag = false
        for (i in split.indices) {
            if (split[i].contains('{') && split[i].contains('}')) {
                if (preIndex != -1) {
                    if (preIndex < 1) {
                        // DEF-1: brace token 在 split[0] 时 split[preIndex-1] 越界, 该段无课程名, 跳过
                        preIndex = i
                        continue
                    }
                    if (split[preIndex - 1] in COURSE_PROPERTY) {
                        hasTypeFlag = true
                    }
                    val temp = ImportBean(startNode = node,
                        name = if (hasTypeFlag && preIndex >= 2) split[preIndex - 2] else split[preIndex - 1],
                        timeInfo = split[preIndex],
                        room = "", teacher = "", cDay = cDay)
                    // T1 G4: 边界守卫 — 时间 token 后无足够 token 时 teacher/room 留空, 不越界
                    if ((i - preIndex - 2) == 1) {
                        if (preIndex + 1 < split.size) temp.teacher = split[preIndex + 1]
                    } else {
                        if (preIndex + 1 < split.size) temp.teacher = split[preIndex + 1]
                        if (preIndex + 2 < split.size) temp.room = split[preIndex + 2]
                    }
                    courses.add(temp)
                    // T1 G3: 每门课构造完成后立即复位, 否则同格下一门无属性行的课名错取前前 token
                    hasTypeFlag = false
                    preIndex = i
                } else {
                    preIndex = i
                }
            }
            if (i == split.size - 1) {
                if (preIndex < 1) continue
                if (split[preIndex - 1] in COURSE_PROPERTY) {
                    hasTypeFlag = true
                }
                val temp = ImportBean(startNode = node,
                    name = if (hasTypeFlag && preIndex >= 2) split[preIndex - 2] else split[preIndex - 1],
                    timeInfo = split[preIndex],
                    room = "", teacher = "", cDay = cDay)
                // T1 G4: 末尾分支同款守卫 (原代码在时间 token 为最后一个 token 时
                // (i-preIndex)==0 走 else 取 split[preIndex+1] → IndexOutOfBoundsException)
                if ((i - preIndex) == 1) {
                    if (preIndex + 1 < split.size) temp.teacher = split[preIndex + 1]
                } else {
                    if (preIndex + 1 < split.size) temp.teacher = split[preIndex + 1]
                    if (preIndex + 2 < split.size) temp.room = split[preIndex + 2]
                }
                courses.add(temp)
                hasTypeFlag = false
            }
        }
        return courses
    }

    private fun importList2CourseList(importList: ArrayList<ImportBean>, source: String): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        for (i in importList) {
            val time = parseTime(i, i.timeInfo, source)
            // 周次串里带"周X"时以串为准，否则用网格列号（"周天"经 WEEK_ALIAS 归 7）
            val day = if (i.timeInfo.length >= 2 && getWeekFromChinese(i.timeInfo.substring(0, 2)) > 0) {
                time[0]
            } else {
                i.cDay
            }
            result.add(
                JwCourse(
                    name = i.name, day = day, room = i.room ?: "",
                    teacher = i.teacher ?: "", startNode = i.startNode,
                    endNode = i.startNode + time[1] - 1,
                    type = time[4],
                    startWeek = time[2],
                    endWeek = time[3]
                )
            )
        }
        return result
    }

    /**
     * 返回 [day, step, startWeek, endWeek, type]。
     * T1 G1: 签名改为收 ImportBean 实例（上游 ZhengFangParser.parseTime 同样收 importBean），
     * 使 (N-M节) 分支能写回 bean.startNode。
     */
    private fun parseTime(bean: ImportBean, time: String, source: String): IntArray {
        val result = IntArray(5)
        // day: 周次串以"周X"开头时从串里取（G7: "周天"→7 走 WEEK_ALIAS）
        if (time.startsWith("周")) {
            val idx = getWeekFromChinese(time.substring(0, 2))
            if (idx > 0) result[0] = idx
        }
        if (result[0] == 0) {
            // 从源码里数课程名之前出现了多少次行标记（"Center" 是老正方 td 对齐样式）
            var startIndex = source.indexOf(">第${bean.startNode}节</td>")
            if (startIndex == -1) {
                startIndex = source.indexOf(">第${getNodeStr(bean.startNode)}节</td>")
            }
            var endIndex = 0
            if (startIndex != -1) {
                endIndex = source.indexOf(bean.name, startIndex)
            }
            if (startIndex != -1 && endIndex != -1) {
                result[0] = countStr(source.substring(startIndex, endIndex), "Center")
            }
        }

        // step（连上节数）
        var step = 0
        when {
            time.contains("节/") -> {
                val numLocate = time.indexOf("节/")
                step = time.substring(numLocate - 1, numLocate).toIntOrNull() ?: 0
            }
            time.contains(",") -> {
                var locate = 0
                step = 1
                while (time.indexOf(",", locate) != -1 && locate < time.length) {
                    step += 1
                    locate = time.indexOf(",", locate) + 1
                }
            }
            time.contains("第${bean.startNode}节") -> {
                step = 1
            }
        }
        if (step == 0) {
            val matchResult = NODE_PATTERN.find(time)
            if (matchResult != null) {
                val nodeInfo = matchResult.value
                val nodes = nodeInfo.substring(1, nodeInfo.length - 1).split("-".toRegex()).dropLastWhile { it.isEmpty() }
                if (nodes.isNotEmpty()) {
                    // T1 G1: 真正写回（上游 Common/ZhengFangParser 语义; 原为空 let 死代码）
                    bean.startNode = nodes[0].toIntOrNull() ?: bean.startNode
                }
                if (nodes.size > 1) {
                    val s = nodes[0].toIntOrNull() ?: bean.startNode
                    val e = nodes[1].toIntOrNull() ?: s
                    step = e - s + 1
                }
            }
        }
        if (step == 0) step = 1
        // T1 G12: 补移植漏行(上游 L214 result[1] = step) — 缺此行 endNode 恒 = startNode-1
        result[1] = step

        // 周数 {第N-M周
        var startWeek = 1
        var endWeek = 20
        val weekResult = WEEK_PATTERN.find(time)
        if (weekResult != null) {
            val weekInfo = weekResult.value
            val weeks = weekInfo.substring(2, weekInfo.length - 1).split("-".toRegex()).dropLastWhile { it.isEmpty() }
            if (weeks.isNotEmpty()) {
                weeks[0].toIntOrNull()?.let {
                    startWeek = it
                    result[2] = it
                }
            }
            if (weeks.size > 1) {
                weeks[1].toIntOrNull()?.let {
                    endWeek = it
                    result[3] = it
                }
            }
        } else {
            // 无花括号周次时按整学期处理（与上游默认一致; sleepy 显式回填保留）
            result[2] = startWeek
            result[3] = endWeek
        }

        // 单双周
        if (time.contains("单周")) {
            result[4] = 1
        } else if (time.contains("双周")) {
            result[4] = 2
        }

        return result
    }

    private data class ImportBean(
        var name: String,
        var timeInfo: String,
        var teacher: String?,
        var room: String?,
        var startNode: Int,
        val cDay: Int
    )

    companion object {
        // 正则与上游 Common.kt 逐字符一致, 勿改
        private val NODE_PATTERN = Regex("""\(\d{1,2}[-]*\d*节""")
        private val WEEK_PATTERN = Regex("""\{第\d{1,2}[-]*\d*周""")
        private val HEADER_NODE_PATTERN = Regex("""第.*节""")

        /** 表头词（与上游 Common.otherHeader 一致 + T1 G8 追加"中午", 后者为 sleepy 扩展非上游原文） */
        private val OTHER_HEADER = arrayOf(
            "时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "早晨", "上午", "下午", "晚上", "中午"
        )

        /**
         * 课程属性词（上游 Common.courseProperty 47 项全量; T1 G2 补齐后 24→47）。
         * 前 24 项为 sleepy 原有(恰为上游前 24 项, 顺序一致), 后 23 项按上游顺序追加。
         */
        private val COURSE_PROPERTY = arrayOf(
            "任选", "限选", "实践选修", "必修课", "选修课", "必修", "选修", "专基", "专选",
            "公必", "公选", "义修", "选", "必", "主干", "专限", "公基", "值班", "通选",
            "思政必", "思政选", "自基必", "自基选", "语技必",
            "语技选", "体育必", "体育选", "专业基础课", "双创必", "双创选",
            "新生必", "新生选", "学科必修", "学科选修",
            "通识必修", "通识选修", "公共基础", "第二课堂",
            "学科实践", "专业实践", "专业必修", "辅修", "专业选修",
            "外语", "方向", "专业必修课", "全选"
        )

        /** 与上游 Common.chineseWeekList 一致(8 元素)。T1 G7: "周天"别名放 WEEK_ALIAS, 勿追加进数组(下标会变 8) */
        private val CHINESE_WEEK_LIST = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** T1 G7: 部分学校打印"周天", 归一为 7 */
        private val WEEK_ALIAS = mapOf("周天" to 7)

        /** 上游 Common.getWeekFromChinese 语义 + WEEK_ALIAS 兜底; 返回 0 = 非"周X"词 */
        private fun getWeekFromChinese(chineseWeek: String): Int {
            val idx = CHINESE_WEEK_LIST.indexOf(chineseWeek)
            if (idx > 0) return idx
            return WEEK_ALIAS[chineseWeek] ?: 0
        }

        private val CN_NUM = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7,
            "七" to 7, "八" to 8, "九" to 9, "十" to 10, "十一" to 11, "十二" to 12,
            "十三" to 13, "十四" to 14, "十五" to 15, "十六" to 16, "十七" to 17,
            "十八" to 18, "十九" to 19, "二十" to 20
        )

        /**
         * "第N节" 行头 → N；不是行头返回 -1。
         * T1 G6（有意偏离上游）: 区间/逗号行头按首段解析 —
         *   "第3-4节"→3  "第1,2节"→1  "第一节-第二节"→1(剥首尾后是"一节-第二", 首段再剥 第/节)
         * 上游 Common.parseHeaderNodeString 对以上均返回 -1。
         */
        private fun parseHeaderNodeString(str: String): Int {
            if (!HEADER_NODE_PATTERN.matches(str)) return -1
            val raw = str.substring(1, str.length - 1)
            val firstSeg = raw.split('-', '—', '~', ',')
                .firstOrNull()
                ?.trim()
                ?.removePrefix("第")
                ?.removeSuffix("节")
                .orEmpty()
            if (firstSeg.isEmpty()) return -1
            return firstSeg.toIntOrNull() ?: CN_NUM[firstSeg] ?: -1
        }

        /** T1 G5: 兜底 — 遍历所有 table 取文本含"星期一"的第一个（Speas-y/ClassScheduleApp 同策略） */
        private fun pickTableByMonday(doc: org.jsoup.nodes.Document): org.jsoup.nodes.Element? {
            return doc.select("table").firstOrNull { it.text().contains("星期一") }
        }

        private fun getNodeStr(node: Int): String = when (node) {
            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "七"
            8 -> "八"; 9 -> "九"; 10 -> "十"; 11 -> "十一"; 12 -> "十二"; 13 -> "十三"
            14 -> "十四"; 15 -> "十五"; 16 -> "十六"
            else -> ""
        }

        private fun countStr(str1: String, str2: String): Int {
            var times = 0
            var startIndex = 0
            var findIndex = str1.indexOf(str2, startIndex)
            while (findIndex != -1 && findIndex != str1.length - 1) {
                times += 1
                startIndex = findIndex + 1
                findIndex = str1.indexOf(str2, startIndex)
            }
            if (findIndex == str1.length - 1) {
                times += 1
            }
            return times
        }

        /** 剥掉残留的 <a> / <a href=...> 前缀（部分页面课程单元无 href 属性） */
        private fun stripAnchorTag(s: String): String {
            var t = s
            if (t.startsWith("<a")) {
                val gt = t.indexOf('>')
                t = if (gt >= 0) t.substring(gt + 1) else t.substring(2)
            }
            return t
        }
    }

    /** T8: #Table1 + 花括号周次 = 100; blacktab = 90; 仅 Table1 = 70 */
    override fun confidence(): Int = try {
        val doc = org.jsoup.Jsoup.parse(source)
        val table1 = doc.getElementById("Table1")
        val blacktab = doc.select("table.blacktab").firstOrNull()
        val hasWeek = WEEK_PATTERN.containsMatchIn(source)
        when {
            (table1 != null || blacktab != null) && hasWeek -> 100
            blacktab != null -> 90
            table1 != null -> 70
            else -> 0
        }
    } catch (e: Exception) { 0 }

    override fun matchedFeatures(): List<String> = try {
        val doc = org.jsoup.Jsoup.parse(source)
        buildList {
            if (doc.getElementById("Table1") != null) add("id=Table1")
            if (doc.select("table.blacktab").firstOrNull() != null) add("class=blacktab")
            if (source.contains("<a")) add("<a>课程链接")
            if (WEEK_PATTERN.containsMatchIn(source)) add("{第N-M周}")
        }
    } catch (e: Exception) { emptyList() }
}

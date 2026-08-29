package com.lingion.sleepy.data.jw

/**
 * 正方教务（老版）课表解析器 — issue #5。
 *
 * 适配 zf / zf_1 协议学校（default2.aspx 时代，个人课表页 xskbcx.aspx）。
 * 忠实移植上游 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) ZhengFangParser.kt：
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/ZhengFangParser.kt
 *
 * 页面结构：
 *   - 外层表格 id="Table1"
 *   - 节次行头："第N节"（N 为数字或中文数字）
 *   - 课程单元格（<a> 内 <br> 分隔）：
 *       课程名<br>{第N-M周}或{第N-M周|单周}<br>老师<br>教室
 *   - 同格多门课用 <br><br>（type=0 变体）或 <br><br><br>（type=1 变体）分隔
 *
 * type 参数对应上游 zfType：0 = 通用变体，1 = zf_1 变体（单元格无 <a> 链接，
 * 内容以空格分隔且周次带花括号）。
 */
class JwOldZfParser(source: String, private val type: Int = 0) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val doc = org.jsoup.Jsoup.parse(source)
        val table1 = doc.getElementById("Table1") ?: return emptyList()
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
                    // 表头行（"时间" "星期一" 等）
                    continue
                }
                val result = parseHeaderNodeString(courseSource)
                if (result != -1) {
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

    /** type=0：标准老正方单元格，<a>课程名<br>周次<br>[老师<br>教室] </a>，多门课 <br><br> 分隔 */
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

    /** type=1（zf_1）：单元格无 <a>，以空格分隔，周次带花括号 */
    private fun parseImportBean1(cDay: Int, source: String, node: Int): List<ImportBean> {
        val courses = ArrayList<ImportBean>()
        val split = source.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
        var preIndex = -1
        var hasTypeFlag = false
        for (i in split.indices) {
            if (split[i].contains('{') && split[i].contains('}')) {
                if (preIndex != -1) {
                    if (split[preIndex - 1] in COURSE_PROPERTY) {
                        hasTypeFlag = true
                    }
                    val temp = ImportBean(startNode = node, name = if (hasTypeFlag && preIndex >= 2) split[preIndex - 2] else split[preIndex - 1],
                        timeInfo = split[preIndex],
                        room = "", teacher = "", cDay = cDay)
                    if ((i - preIndex - 2) == 1) {
                        temp.teacher = split[preIndex + 1]
                    } else {
                        temp.teacher = split[preIndex + 1]
                        temp.room = split[preIndex + 2]
                    }
                    courses.add(temp)
                    preIndex = i
                } else {
                    preIndex = i
                }
            }
            if (i == split.size - 1) {
                if (preIndex == -1) continue
                if (split[preIndex - 1] in COURSE_PROPERTY) {
                    hasTypeFlag = true
                }
                val temp = ImportBean(startNode = node, name = if (hasTypeFlag && preIndex >= 2) split[preIndex - 2] else split[preIndex - 1],
                    timeInfo = split[preIndex],
                    room = "", teacher = "", cDay = cDay)
                if ((i - preIndex) == 1) {
                    temp.teacher = split[preIndex + 1]
                } else {
                    temp.teacher = split[preIndex + 1]
                    temp.room = split[preIndex + 2]
                }
                courses.add(temp)
            }
        }
        return courses
    }

    private fun importList2CourseList(importList: ArrayList<ImportBean>, source: String): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        for (i in importList) {
            val time = parseTime(i.timeInfo, i.startNode, source, i.name)
            // 周次串里带"周X"时以串为准，否则用网格列号
            val day = if (i.timeInfo.length >= 2 && i.timeInfo.substring(0, 2) in CHINESE_WEEK_LIST) time[0] else i.cDay
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

    /** 返回 [day, step, startWeek, endWeek, type]，逻辑与上游 parseTime 一致 */
    private fun parseTime(time: String, startNode: Int, source: String, courseName: String): IntArray {
        val result = IntArray(5)
        // day: 周次串以"周X"开头时从串里取
        if (time.startsWith("周")) {
            val dayStr = time.substring(0, 2)
            val idx = CHINESE_WEEK_LIST.indexOf(dayStr)
            if (idx > 0) result[0] = idx
        }
        if (result[0] == 0) {
            // 从源码里数课程名之前出现了多少次行标记（"Center" 是老正方 td 对齐样式）
            var startIndex = source.indexOf(">第${startNode}节</td>")
            if (startIndex == -1) {
                startIndex = source.indexOf(">第${getNodeStr(startNode)}节</td>")
            }
            var endIndex = 0
            if (startIndex != -1) {
                endIndex = source.indexOf(courseName, startIndex)
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
            time.contains("第${startNode}节") -> {
                step = 1
            }
        }
        if (step == 0) {
            val matchResult = NODE_PATTERN.find(time)
            if (matchResult != null) {
                val nodeInfo = matchResult.value
                val nodes = nodeInfo.substring(1, nodeInfo.length - 1).split("-".toRegex()).dropLastWhile { it.isEmpty() }
                if (nodes.isNotEmpty()) {
                    nodes[0].toIntOrNull()?.let { }
                }
                if (nodes.size > 1) {
                    val s = nodes[0].toIntOrNull() ?: startNode
                    val e = nodes[1].toIntOrNull() ?: s
                    step = e - s + 1
                }
            }
        }
        if (step == 0) step = 1

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
            // 无花括号周次时按整学期处理（与上游默认一致）
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
        private val NODE_PATTERN = Regex("""\(\d{1,2}[-]*\d*节""")
        private val WEEK_PATTERN = Regex("""\{第\d{1,2}[-]*\d*周""")
        private val HEADER_NODE_PATTERN = Regex("""第.*节""")

        /** 表头词：出现在 td 里说明是表头行而非课程（与上游 Common.otherHeader 一致） */
        private val OTHER_HEADER = arrayOf(
            "时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "早晨", "上午", "下午", "晚上"
        )

        /** 课程属性词（"必修" "选修" 等），出现在课程名后说明单元格多了一行（与上游 Common.courseProperty 一致） */
        private val COURSE_PROPERTY = arrayOf(
            "任选", "限选", "实践选修", "必修课", "选修课", "必修", "选修", "专基", "专选",
            "公必", "公选", "义修", "选", "必", "主干", "专限", "公基", "值班", "通选",
            "思政必", "思政选", "自基必", "自基选", "语技必"
        )

        private val CHINESE_WEEK_LIST = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

        private val CN_NUM = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7,
            "七" to 7, "八" to 8, "九" to 9, "十" to 10, "十一" to 11, "十二" to 12,
            "十三" to 13, "十四" to 14, "十五" to 15, "十六" to 16, "十七" to 17,
            "十八" to 18, "十九" to 19, "二十" to 20
        )

        /** "第N节" 行头 → N；不是行头返回 -1（与上游 parseHeaderNodeString 一致） */
        private fun parseHeaderNodeString(str: String): Int {
            var node = -1
            if (HEADER_NODE_PATTERN.matches(str)) {
                val nodeStr = str.substring(1, str.length - 1)
                node = nodeStr.toIntOrNull() ?: CN_NUM[nodeStr] ?: -1
            }
            return node
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
}

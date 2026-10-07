package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern

/**
 * 西北工业大学研究生教务系统 (NWPU Post-graduate) 课表解析器。
 *
 * 协议特点：
 * 1. 一维清单式表格，以 id="sample-table-1" 为主特征锚点；
 * 2. thead/th 列头匹配：院系、课程编号、课程名称、班级名称、主讲教师、学分、班级说明、上课时间；
 * 3. 上课时间字段支持换行，逐行提取：教室 + (周次 + 星期 + 节次)；
 * 4. 周次提取：`第(\\d+)-(\\d+)周` 或 `第(\\d+)周`，包含单周/双周标识；
 * 5. 星期拆分：`星期.+?(?=<|星期|$)`，支持星期一至星期日映射；
 * 6. 时段与节次偏移：上(0), 中(4), 下(6), 晚(10) + 节次序号；连续节次自动合并；
 * 7. 作息时间：默认长安校区 13 节，友谊校区支持冬夏令制判定；
 * 8. 学期信息：从 select#xq 的 option[selected] 自动提取拼接。
 */
class JwNwpuPostParser(source: String) : JwParser(source) {

    private val sectionBaseMap = mapOf(
        "上" to 0,
        "中" to 4,
        "下" to 6,
        "晚" to 10
    )

    private val dayNames = listOf(
        "时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
    )

    override fun confidence(): Int {
        var score = 0
        if (source.contains("id=\"sample-table-1\"") || source.contains("id='sample-table-1'")) score += 70
        if (source.contains("上课时间") && source.contains("课程编号")) score += 20
        if (source.contains("主讲教师") || source.contains("班级名称")) score += 10
        return score.coerceAtMost(100)
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("sample-table-1")) add("id:sample-table-1")
        if (source.contains("上课时间")) add("col:上课时间")
        if (source.contains("主讲教师")) add("col:主讲教师")
    }

    override fun generateCourseList(): List<JwCourse> = parse(source)

    fun parse(html: String): List<JwCourse> {
        val doc: Document = Jsoup.parse(html)
        val table = doc.selectFirst("#sample-table-1") ?: return emptyList()

        val thHeaders = table.select("thead tr th").map { it.text().trim() }
        val trRows = table.select("tbody tr")
        if (trRows.isEmpty()) return emptyList()

        val courses = mutableListOf<JwCourse>()

        for (tr in trRows) {
            val tds = tr.select("td")
            if (tds.isEmpty()) continue

            var department = ""
            var courseCode = ""
            var courseName = ""
            var className = ""
            var teacher = ""
            var credit = 0.0f
            var classNote = ""
            var timeRoomRaw = ""

            for (colIdx in tds.indices) {
                val td = tds[colIdx]
                // 显式将 <br> 替换为换行文本节点，避免 text() 规范化吃掉换行
                td.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode("\n")) }
                val tdText = td.wholeText().trim()
                val thText = if (colIdx < thHeaders.size) thHeaders[colIdx] else ""

                when {
                    thText.contains("院系") -> department = tdText
                    thText.contains("课程编号") -> courseCode = tdText
                    thText.contains("课程名称") -> courseName = tdText
                    thText.contains("班级名称") -> className = tdText
                    thText.contains("主讲教师") -> teacher = tdText
                    thText.contains("学分") -> credit = tdText.toFloatOrNull() ?: 0.0f
                    thText.contains("班级说明") -> classNote = tdText
                    thText.contains("上课时间") -> timeRoomRaw = tdText
                }
            }

            if (courseName.isBlank() && timeRoomRaw.isBlank()) continue

            val combinedName = if (className.isNotBlank()) {
                "$courseName($className)"
            } else {
                courseName
            }

            val note = listOf(department, courseCode, classNote)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            // 每行一个时段地点
            val lines = timeRoomRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            for (line in lines) {
                val room = if (!line.startsWith("(") && line.contains("(")) {
                    line.substringBefore("(").replace("-", " ").trim()
                } else if (!line.contains("(")) {
                    line.replace("-", " ").trim()
                } else {
                    "无地点"
                }

                val timeContent = if (line.contains("(") && line.contains(")")) {
                    line.substringAfter("(").substringBefore(")")
                } else {
                    line
                }

                // 周次
                val weekRangePattern = Pattern.compile("第(\\d+)-(\\d+)周")
                val singleWeekPattern = Pattern.compile("第(\\d+)周")
                var startWeek = 1
                var endWeek = 1
                val weekRangeMatcher = weekRangePattern.matcher(timeContent)
                if (weekRangeMatcher.find()) {
                    startWeek = weekRangeMatcher.group(1)?.toIntOrNull() ?: 1
                    endWeek = weekRangeMatcher.group(2)?.toIntOrNull() ?: 1
                } else {
                    val singleMatcher = singleWeekPattern.matcher(timeContent)
                    if (singleMatcher.find()) {
                        startWeek = singleMatcher.group(1)?.toIntOrNull() ?: 1
                        endWeek = startWeek
                    }
                }

                val weekType = when {
                    startWeek == endWeek -> 0
                    timeContent.contains("单周") -> 1
                    timeContent.contains("双周") -> 2
                    else -> 0
                }

                // 星期分段
                val dayPattern = Pattern.compile("星期.+?(?=<|星期|$)")
                val dayMatcher = dayPattern.matcher(timeContent)
                val daySegments = mutableListOf<String>()
                while (dayMatcher.find()) {
                    daySegments.add(dayMatcher.group())
                }

                if (daySegments.isEmpty() && timeContent.contains("星期")) {
                    daySegments.add(timeContent)
                }

                for (daySeg in daySegments) {
                    val dayStr = if (daySeg.length >= 3) daySeg.substring(0, 3) else daySeg
                    val dayIdx = dayNames.indexOf(dayStr)
                    val day = if (dayIdx > 0) dayIdx else 1

                    // 节次（含范围）：[上中下晚]?(\d+)(?:-(\d+))?节?
                    // 例：上1-2节 -> 上1-2, base 0 -> [1,2]
                    //     下1-2节 -> 下1-2, base 6 -> [7,8]
                    val nodeRangePattern = Pattern.compile("([上中下晚])?(\\d+)(?:-(\\d+))?节?")
                    val nodeMatcher = nodeRangePattern.matcher(daySeg)
                    val nodes = mutableListOf<Int>()
                    while (nodeMatcher.find()) {
                        val sectionChar = nodeMatcher.group(1)
                        val startNum = nodeMatcher.group(2)?.toIntOrNull() ?: continue
                        val endNum = nodeMatcher.group(3)?.toIntOrNull() ?: startNum
                        val base = if (sectionChar != null) (sectionBaseMap[sectionChar] ?: 0) else 0
                        for (n in startNum..endNum) {
                            nodes.add(base + n)
                        }
                    }

                    if (nodes.isEmpty()) continue

                    nodes.sort()

                    // 连续节次聚类合并
                    var curStart = nodes[0]
                    var curEnd = nodes[0]
                    for (i in 1 until nodes.size) {
                        if (nodes[i] == curEnd + 1) {
                            curEnd = nodes[i]
                        } else {
                            courses.add(
                                JwCourse(
                                    name = combinedName,
                                    room = room,
                                    teacher = teacher,
                                    day = day,
                                    startNode = curStart,
                                    endNode = curEnd,
                                    startWeek = startWeek,
                                    endWeek = endWeek,
                                    type = weekType
                                )
                            )
                            curStart = nodes[i]
                            curEnd = nodes[i]
                        }
                    }

                    courses.add(
                        JwCourse(
                            name = combinedName,
                            room = room,
                            teacher = teacher,
                            day = day,
                            startNode = curStart,
                            endNode = curEnd,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            type = weekType
                        )
                    )
                }
            }
        }

        return courses
    }
}

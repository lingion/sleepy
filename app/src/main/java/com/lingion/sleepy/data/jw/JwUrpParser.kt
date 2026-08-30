package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 老版 URP 教务系统解析器（HTML 表格版本）
 *
 * 数据来源：URP 综合教务（部分校）课表页用 HTML 表格展示，class 为
 *   "displayTag" 或 "table table-striped table-bordered"。
 * 表头：课程名/教师/星期/节次/周次/教学楼/教室/节数
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) UrpParser.kt 简化而来
 * https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/UrpParser.kt
 */
class JwUrpParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> {
        val result = arrayListOf<JwCourse>()
        val doc = Jsoup.parse(source)
        // T10: URP 网格变体 (td[id='day_node'] + div.class_div) — 真实结构来自 urp_01.js
        val grid = parseUrpGrid(doc)
        if (grid.isNotEmpty()) return grid

        var tables = doc.getElementsByAttributeValue("class", "displayTag")
        if (tables.isEmpty()) {
            tables = doc.getElementsByAttributeValue("class", "table table-striped table-bordered")
        }
        if (tables.isEmpty()) return result

        for (table in tables) {
            // 跳过非课表（第一行含"星期一"的是顶部信息行）
            if (table.text().contains("星期一")) continue

            val thead = table.getElementsByTag("thead").firstOrNull() ?: continue
            val ths = thead.getElementsByTag("th")
            val headSize = ths.size

            var nameIdx = -1
            var teacherIdx = -1
            var weekIdx = -1
            var dayIdx = -1
            var nodeIdx = -1
            var stepIdx = -1
            var buildingIdx = -1
            var roomIdx = -1

            ths.eachText().forEachIndexed { i, s ->
                when (s.trim()) {
                    "课程名" -> nameIdx = i
                    "教师" -> teacherIdx = i
                    "周次" -> weekIdx = i
                    "星期" -> dayIdx = i
                    "节次" -> nodeIdx = i
                    "节数" -> stepIdx = i
                    "教学楼" -> buildingIdx = i
                    "教室" -> roomIdx = i
                }
            }
            // ★ 周/节数列索引缺失时无法安全对齐列，跳过此表（避免 IndexOutOfBounds 崩溃）
            if (weekIdx == -1 || nodeIdx == -1 || nameIdx == -1) continue

            val tbody = table.getElementsByTag("tbody").firstOrNull() ?: continue
            var courseName = ""
            var teacher = ""

            for (tr in tbody.getElementsByTag("tr")) {
                val tds = tr.getElementsByTag("td")
                val wholeFlag = tds.size > headSize - weekIdx
                val acDayIdx = if (wholeFlag) dayIdx else dayIdx - weekIdx
                if (tds[acDayIdx].text().trim().isBlank()) continue

                if (wholeFlag) {
                    courseName = tds[nameIdx].text()
                    teacher = tds[teacherIdx].text().trim()
                }

                val room = try {
                    val bIdx = if (wholeFlag) buildingIdx else buildingIdx - weekIdx
                    val rIdx = if (wholeFlag) roomIdx else roomIdx - weekIdx
                    tds[bIdx].text().trim() + tds[rIdx].text().trim()
                } catch (e: Exception) { "" }

                val nodeE = tds[if (wholeFlag) nodeIdx else nodeIdx - weekIdx]
                val startNode = getStartNode(nodeE.text())
                val step = if (stepIdx != -1) {
                    val sIdx = if (wholeFlag) stepIdx else stepIdx - weekIdx
                    getStep(tds[sIdx].text().trim())
                } else {
                    val end = nodeE.text().trim().substringAfter('-').substringBefore('节').trim().toIntOrNull() ?: startNode
                    end - startNode + 1
                }
                val day = getDay(tds[acDayIdx].text())
                val acWeekIdx = if (wholeFlag) weekIdx else 0
                val weekStr = tds[acWeekIdx].text().trim()

                val ranges = weekStrToRanges(weekStr)
                for (r in ranges) {
                    result += JwCourse(
                        name = courseName, room = room, teacher = teacher, day = day,
                        startNode = startNode, endNode = startNode + step - 1,
                        startWeek = r.first, endWeek = r.second, type = r.third
                    )
                }
            }
        }
        return result
    }

    private fun getDay(str: String): Int = try {
        str.trim().toInt()
    } catch (e: Exception) {
        when (str.trim()) {
            "星期一" -> 1; "星期二" -> 2; "星期三" -> 3; "星期四" -> 4
            "星期五" -> 5; "星期六" -> 6; "星期日", "星期天" -> 7
            else -> 1
        }
    }

    private fun getStartNode(s: String): Int {
        val t = s.trim()
        return if (t.contains('-')) {
            t.substringBefore('-').toIntOrNull() ?: 1
        } else {
            t.substringAfter('第').substringBefore('大').substringBefore('小').toIntOrNull() ?: 1
        }
    }

    private fun getStep(s: String): Int = s.toIntOrNull() ?: 1

    private fun weekStrToRanges(weekStr: String): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        if (weekStr.isBlank()) {
            result += Triple(1, 20, 0)
            return result
        }
        // 支持 "1-16周", "1-16周(单)", "2,4,6,8,10周"(逗号列表按上游 weekIntList2WeekBeanList 归并)
        val type = when {
            weekStr.contains('单') -> 1
            weekStr.contains('双') -> 2
            else -> 0
        }
        val cleaned = weekStr.replace("周", "").replace("(", "").replace(")", "")
            .replace("单", "").replace("双", "").trim()
        if (cleaned.contains('-')) {
            val parts = cleaned.split('-')
            val s = parts[0].trim().toIntOrNull() ?: 1
            val e = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: s
            result += Triple(s, e, type)
        } else if (cleaned.contains(',')) {
            // 逗号列表: 归并成连续/单双周段 (T10 契约: "2,4,6,8,10周" → (2,10,type=2))
            val weeks = cleaned.split(',').mapNotNull { it.trim().toIntOrNull() }
            result += JwChengFangParser.weekIntList2WeekBeanList(weeks)
        } else {
            val v = cleaned.toIntOrNull() ?: 1
            result += Triple(v, v, type)
        }
        return result
    }

    /**
     * T10: URP 网格变体解析 — td[id='<day>_<node>'] 定位, div.class_div 含 >=5 个 <p>:
     * p[0]=课名, p[1]=星号占位, p[2]=教师, p[3]=周次串, p[4]=节次串, p[5]=教室(可选)
     */
    private fun parseUrpGrid(doc: org.jsoup.nodes.Document): List<JwCourse> {
        val result = mutableListOf<JwCourse>()
        val gridTds = doc.select("td[id]") ?: return result
        for (td in gridTds) {
            val id = td.attr("id")
            val parts = id.split("_")
            if (parts.size != 2) continue
            val day = parts[0].toIntOrNull() ?: continue
            if (day !in 1..7) continue
            val node = parts[1].toIntOrNull() ?: continue
            val divs = td.getElementsByClass("class_div")
            for (div in divs) {
                val ps = div.getElementsByTag("p")
                if (ps.size < 5) continue
                val name = ps[0].text().trim()
                if (name.isEmpty()) continue
                val teacher = ps[2].text().trim()
                val weekStr = ps[3].text().trim()
                val nodeStr = ps[4].text().trim()
                val ranges = gridWeekRanges(weekStr)
                if (ranges.isEmpty()) continue
                // 节次串 "N-M节" → start/end
                val nParts = nodeStr.replace("节", "").split("-")
                val startNode = nParts.getOrNull(0)?.trim()?.toIntOrNull() ?: node
                val endNode = nParts.getOrNull(1)?.trim()?.toIntOrNull() ?: startNode
                val room = if (ps.size >= 6) ps[5].text().trim() else ""
                for (r in ranges) {
                    result += JwCourse(
                        name = name, teacher = teacher, room = room, day = day,
                        startNode = startNode, endNode = endNode,
                        startWeek = r.first, endWeek = r.second, type = r.third
                    )
                }
            }
        }
        return result
    }

    /** 网格变体周次串: "1-16周" / "2-14周单周" / "1-8,10-17周" / "3-12周双周" → 归并段 */
    private fun gridWeekRanges(weekStr: String): List<Triple<Int, Int, Int>> {
        if (weekStr.isBlank()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val cleaned = weekStr.replace("周", "")
        cleaned.split(',', '，', ';', '；').forEach { seg0 ->
            val seg = seg0.trim()
            if (seg.isEmpty()) return@forEach
            val type = when {
                seg.contains('单') -> 1
                seg.contains('双') -> 2
                else -> 0
            }
            val digits = seg.filter { it.isDigit() || it == '-' }
            if (digits.contains('-')) {
                val parts = digits.split('-')
                val s = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val e = parts.getOrNull(1)?.toIntOrNull() ?: s
                if (type == 0) {
                    result += Triple(s, e, 0)
                } else {
                    // 单/双周: 展开成奇/偶周序列再归并 ("2-14周单周" → (3,13,1))
                    val weeks = (s..e).filter { if (type == 1) it % 2 == 1 else it % 2 == 0 }
                    result += JwChengFangParser.weekIntList2WeekBeanList(weeks)
                }
            } else {
                val v = digits.toIntOrNull() ?: return@forEach
                result += Triple(v, v, type)
            }
        }
        return result
    }

    /** T8: displayTag = 100; table-striped = 90; urp grid td[id] = 100 */
    override fun confidence(): Int = when {
        Regex("""td[^>]+id="\d+_\d+"""").containsMatchIn(source) && source.contains("class_div") -> 100
        source.contains("displayTag") -> 100
        source.contains("table-striped") -> 90
        else -> 0
    }

    override fun matchedFeatures(): List<String> = buildList {
        if (source.contains("displayTag")) add("class=displayTag")
        if (source.contains("table-striped")) add("class=table table-striped table-bordered")
    }
}

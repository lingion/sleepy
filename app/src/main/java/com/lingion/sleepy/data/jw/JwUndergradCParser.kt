package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Explicit layouts from the fixed 6.6.2 baseline. Unknown arrangements produce no course. */
class JwUndergradCParser(source: String, private val layout: String) : JwParser(source) {
    private val document by lazy { Jsoup.parse(source) }

    override fun generateCourseList(): List<JwCourse> = when (layout) {
        "xhtd" -> xhtd()
        "xytc" -> xytc()
        "cidp" -> cidp()
        "sdpei" -> sdpei()
        "shu" -> shu()
        "cqu" -> cqu()
        "jmpt" -> jmpt()
        "lngd" -> lngd()
        "vatuu" -> vatuu()
        "yzzy" -> yzzy()
        else -> emptyList()
    }.distinct()

    override fun confidence(): Int = if (when (layout) {
        "xhtd" -> document.selectFirst("#kbtable div[id]") != null
        "xytc" -> document.getElementById("report_tSxsgrkbcx") != null
        "cidp" -> document.selectFirst("#scheduleTable .Content") != null
        "sdpei" -> document.getElementById("tableMain") != null
        "shu" -> document.selectFirst("[name=rowclass]") != null
        "cqu" -> "print-schedule-table" in source && document.selectFirst(".item-box") != null
        "jmpt" -> document.select(".admintable").size > 1
        "lngd" -> document.selectFirst("#root .kbappTimetableDayColumnRoot___1DlDV, #wdkb-kb .kbappTimetableDayColumnRoot") != null
        "vatuu" -> document.selectFirst(".table_border") != null
        "yzzy" -> document.selectFirst("#timetable .kbcontent") != null
        else -> false
    }) 100 else 0

    private fun MutableList<JwCourse>.slots(name: String, room: String, teacher: String, day: Int,
        nodes: String, weeks: String, credit: Float = 0f, note: String = "", parity: Int = 0) {
        if (name.isBlank() || day !in 1..7 || !credit.isFinite() || credit < 0) return
        val nodeRanges = nodeRanges(nodes)
        val ranges = weekRanges(weeks)
        if (nodeRanges.isEmpty() || ranges.isEmpty()) return
        for ((start, end, kind) in ranges) for ((first, last) in nodeRanges) {
            if (parity != 0 && kind != 0 && parity != kind) continue
            val type = if (parity == 0) kind else parity
            if ((start..end).none { type == 0 || it % 2 == type % 2 }) continue
            add(JwCourse(name.trim(), room.trim(), teacher.trim(), day, first, last, start, end, type,
                credit = credit, note = note.trim()))
        }
    }

    private fun nodeRanges(raw:String):List<Pair<Int,Int>> {
        val ranges=mutableListOf<Pair<Int,Int>>()
        for(token in raw.split(',', '，')) {
            val match=Regex("([0-9]+)(?:\\s*-\\s*([0-9]+))?").matchEntire(token.trim()) ?: return emptyList()
            val first=match.groupValues[1].toIntOrNull()?.takeIf { it>0 } ?: return emptyList()
            val last=match.groupValues[2].ifBlank { match.groupValues[1] }.toIntOrNull()?.takeIf { it>=first } ?: return emptyList()
            ranges+=first to last
        }
        val result=mutableListOf<Pair<Int,Int>>()
        for(range in ranges.sortedBy { it.first }) {
            val previous=result.lastOrNull()
            if(previous!=null && range.first.toLong()<=previous.second.toLong()+1) result[result.lastIndex]=previous.first to maxOf(previous.second,range.second)
            else result+=range
        }
        return result
    }

    private fun weekRanges(raw: String): List<Triple<Int, Int, Int>> {
        var text = raw.trim().replace('（', '(').replace('）', ')')
            .replace('、', ',').replace('\\', ',').replace('~', '-').replace('～', '-')
        val stepped = Regex("^(.*?)隔([①-⑳])周?$").matchEntire(text)
        if (stepped != null) {
            val base = schoolWeekRanges(stepped.groupValues[1].trim().removeSuffix("周"))
            if (base.isEmpty() || base.any { it.third != 0 }) return emptyList()
            val step = stepped.groupValues[2][0].code - '①'.code + 1
            return base.flatMap { (start, end, _) -> (start..end step step).map { Triple(it,it,0) } }
        }
        if ('隔' in text) return emptyList()
        // Parentheses around a whole week expression are formatting, not an extra time field.
        if (text.startsWith('(') && text.endsWith(')') && text.count { it == '(' } == 1) text = text.drop(1).dropLast(1)
        return schoolWeekRanges(text, trailingParityAppliesToAll = true)
    }

    private fun text(html: String) = Jsoup.parseBodyFragment(html).text().trim()
    private fun lines(html: String) = html.split(Regex("(?i)<br\\s*/?>")).map(::text)
    private fun day(raw: String): Int {
        val value = raw.trim().removePrefix("星期").removePrefix("周")
        return value.toIntOrNull() ?: when(value) { "一"->1; "二"->2; "三"->3; "四"->4; "五"->5; "六"->6; "七","日","天"->7; else->0 }
    }

    private fun xhtd(): List<JwCourse> = buildList {
        for (cell in document.select("#kbtable div[id]")) {
            val coordinate = Regex("([0-9]+)-([1-7])").matchEntire(cell.id()) ?: continue
            if (cell.selectFirst("nobr") == null) continue
            val block = coordinate.groupValues[1].toLongOrNull()?.takeIf { it > 0 && it <= Int.MAX_VALUE / 2L } ?: continue
            var fields = lines(cell.html())
            if (fields.size % 5 == 1 && fields.last().isBlank()) fields = fields.dropLast(1)
            if (fields.size % 5 != 0) continue
            for (part in fields.chunked(5)) slots(part[0],part[4],part[2],coordinate.groupValues[2].toInt(),
                "${block*2-1}-${block*2}",part[3])
        }
    }

    private fun bridge(html: String): Pair<String,String>? {
        val pieces = html.split(Regex("(?i)<br\\s*/?>"))
        return when(pieces.size) {
            2 -> pieces[0] to pieces[1]
            3 -> if (text(pieces[0]).length + text(pieces[1]).length < 4)
                pieces[0]+pieces[1] to pieces[2] else pieces[0] to pieces[1]+pieces[2]
            else -> null
        }
    }

    private fun xytc(): List<JwCourse> = buildList {
        for (cell in document.select("#report_tSxsgrkbcx td")) {
            val tokens = cell.html().split("&nbsp;")
            val entries = mutableListOf<List<String>>()
            when(tokens.size) {
                6 -> entries += tokens
                7 -> entries += listOf(tokens[0],tokens[1],tokens[3],tokens[4],tokens[5],tokens[6])
                11,13 -> {
                    val extra = if(tokens.size==13) 1 else 0
                    val offset = 5+extra
                    val split = bridge(tokens[offset]) ?: continue
                    entries += listOf(tokens[0],tokens[1],tokens[2+extra],tokens[3+extra],tokens[4+extra],split.first)
                    entries += listOf(split.second,tokens[offset+1],tokens[offset+2+extra],tokens[offset+3+extra],tokens[offset+4+extra],tokens[offset+5+extra])
                }
            }
            for (entry in entries) slots(text(entry[0]),text(entry[4]),text(entry[5]),day(text(entry[2])),
                text(entry[3]).removeSuffix("节"),lines(entry[1]).lastOrNull(String::isNotBlank).orEmpty())
        }
    }

    private fun MutableList<JwCourse>.spanCourse(item: Element, weekday: Int, start: Int, end: Int) {
        val name = item.selectFirst(".spLUName")?.text().orEmpty().removePrefix("《").removeSuffix("》")
        slots(name,item.selectFirst(".spBuilding")?.text().orEmpty()+item.selectFirst(".spClassroom")?.text().orEmpty(),
            item.selectFirst(".spTeacherName")?.text().orEmpty(),weekday,"$start-$end",item.selectFirst(".spWeekInfo")?.text().orEmpty())
    }

    private fun cidp(): List<JwCourse> = buildList {
        for (cell in document.select("#scheduleTable .Content")) {
            val start = cell.attr("data-class").toIntOrNull()?.takeIf { it in 1 until Int.MAX_VALUE } ?: continue
            val weekday = cell.attr("data-week").toIntOrNull() ?: continue
            for (item in cell.select(".divOneClass")) spanCourse(item,weekday,start,start+1)
        }
    }

    private fun sdpei(): List<JwCourse> = buildList {
        val table = document.getElementById("tableMain") ?: return@buildList
        val occupiedUntil = IntArray(7)
        var node = 0
        for (row in table.select("tr")) {
            if (row.selectFirst("th")?.text().isNullOrBlank()) continue
            node++
            var column = 0
            for (cell in row.children().filter { it.tagName()=="td" }) {
                if (cell.attr("align").equals("center",true)) continue
                while(column < 7 && occupiedUntil[column] >= node) column++
                if(column >= 7) break
                val height = cell.attr("rowspan").ifBlank { "1" }.toIntOrNull()?.takeIf { it > 0 && it.toLong()+node-1 <= Int.MAX_VALUE } ?: break
                val end = node+height-1
                occupiedUntil[column] = end
                for (item in cell.select(".divOneClass")) spanCourse(item,column+1,node,end)
                column++
            }
        }
    }

    private fun shu(): List<JwCourse> = buildList {
        val token = Regex("([一二三四五六七日])([0-9]+-[0-9]+)")
        for (row in document.select("[name=rowclass]")) {
            val fields = row.children().map { it.text().trim() }
            if(fields.size<8) continue
            val credit = fields[3].toFloatOrNull()?.takeIf { it.isFinite() && it>=0 } ?: 0f
            val matches = token.findAll(fields[6]).toList()
            for ((index, match) in matches.withIndex()) {
                val suffix = fields[6].substring(match.range.last+1,matches.getOrNull(index+1)?.range?.first ?: fields[6].length).trim()
                val week = suffix.replace('（','(').replace('）',')').removePrefix("(").removeSuffix(")")
                slots(fields[2],fields[7],fields[5],day(match.groupValues[1]),match.groupValues[2],week,credit)
            }
        }
    }

    private fun cqu(): List<JwCourse> = buildList {
        if("print-schedule-table" !in source) return@buildList
        for(item in document.select(".item-box")) {
            val time = item.selectFirst(".item-time")?.text().orEmpty().trim()
            val match = Regex("^([^\\s]+周)\\s+([^\\s]+)节\\s+(星期[一二三四五六七日天1-7])$").matchEntire(time) ?: continue
            slots(item.selectFirst(".item-name")?.text().orEmpty().substringBeforeLast('['),
                item.selectFirst(".item-location")?.text().orEmpty(),item.selectFirst(".item-instr")?.text().orEmpty().substringBefore('-'),
                day(match.groupValues[3]),match.groupValues[2],match.groupValues[1])
        }
    }

    private fun jmpt(): List<JwCourse> = buildList {
        val table = document.select(".admintable").getOrNull(1) ?: return@buildList
        var carriedDay = 0
        var remaining = 0
        for(row in table.select("tr")) {
            val cells = row.children().filter { it.tagName()=="td" }
            if(cells.isEmpty()) continue
            val offset: Int
            if(cells[0].hasAttr("rowspan")) {
                carriedDay = day(cells[0].text()); remaining = cells[0].attr("rowspan").toIntOrNull()?.takeIf { it > 0 } ?: 0
                offset=1
            } else offset=0
            if(remaining<=0 || cells.size<offset+9) { carriedDay=0; remaining=0; continue }
            remaining--
            val f=cells.drop(offset).map { it.text().trim() }
            val instruction=f[8]
            val parity=when { "双" in instruction -> if("不" in instruction) 1 else 2
                "单" in instruction -> if("不" in instruction) 2 else 1; else -> 0 }
            slots(f[3],f[6],f[5],carriedDay,"${f[1]}-${f[2]}",f[0],note=f[7],parity=parity)
        }
    }

    private fun lngd(): List<JwCourse> = buildList {
        val modern = document.getElementById("root")!=null
        val columns = if(modern) document.select("#root .kbappTimetableDayColumnRoot___1DlDV")
            else document.select("#wdkb-kb .kbappTimetableContentContainer .kbappTimetableDayColumnRoot")
        for((columnIndex,column) in columns.withIndex()) {
            if(columnIndex>=7) break
            val courses=column.select(if(modern) ".kbappTimetableCourseRenderCourseItem___MgPtp" else ".kbappTimetableCourseRenderCourseItemContainer")
            for(item in courses) {
                val details=item.select(if(modern) ".kbappTimetableCourseRenderCourseItemInfoText___2Zmwu" else ".kbappTimetableCourseRenderCourseItemInfoText")
                val title=if(modern) item.selectFirst(".title___3o2RH")?.text().orEmpty() else details.firstOrNull()?.text().orEmpty()
                val titleMatch=Regex("^(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*学分?$").matchEntire(title)
                    ?: Regex("^(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)$").matchEntire(title) ?: continue
                val credit=titleMatch.groupValues[2].toFloatOrNull() ?: continue
                for(detail in if(modern) details else details.drop(1)) {
                    val content=detail.text()
                    val node=Regex("第([0-9]+)节-第([0-9]+)节").find(content) ?: continue
                    val week=Regex("[0-9]+(?:-+[0-9]+)?周").findAll(content).joinToString(",") { it.value.replace(Regex("-+"),"-") }
                    val teacher=Regex("周([\\u4e00-\\u9fa5]+)").find(content)?.groupValues?.get(1).orEmpty()
                    val fields=content.trim().split(Regex("\\s+"))
                    val room=if(fields.size>2) fields.last() else ""
                    slots(titleMatch.groupValues[1],room,teacher,columnIndex+1,"${node.groupValues[1]}-${node.groupValues[2]}",week,credit)
                }
            }
        }
    }

    private fun vatuu(): List<JwCourse> = buildList {
        val table=document.selectFirst(".table_border") ?: return@buildList
        if(table.id()=="table3") return@buildList
        val rows=table.select("tr")
        val start=rows.firstOrNull()?.select("td")?.indexOfFirst { "星期一" in it.text() } ?: -1
        if(start<0) return@buildList
        for((rowIndex,row) in rows.withIndex()) {
            if(rowIndex < 1) continue
            for((column,cell) in row.children().filter { it.tagName()=="td" }.withIndex()) {
                val weekday=column-start+1
                if(weekday !in 1..7) continue
                val fields=lines(cell.html().replace("&nbsp;","<br>"))
                if(fields.size%4!=0) continue
                for(group in fields.chunked(4)) {
                    val nameTeacher=Regex("^(.*)[（(]([^()（）]*)[）)]$").matchEntire(group[0]) ?: continue
                    slots(nameTeacher.groupValues[1],group[2],nameTeacher.groupValues[2],weekday,rowIndex.toString(),group[1])
                }
            }
        }
    }

    private fun yzzy(): List<JwCourse> = buildList {
        val rows=document.select("#timetable tr")
        for((node,row) in rows.withIndex()) {
            if(node !in 1..13) continue
            for((column,cell) in row.children().filter { it.tagName()=="td" }.withIndex()) {
                for(item in cell.select(".kbcontent")) {
                    val name=item.select("font").firstOrNull { it.attr("onmouseover").removeSuffix(";")=="kbtc(this)" }?.text().orEmpty()
                    val raw=item.selectFirst("font[title=周次(节次)]")?.text().orEmpty()
                    val week=Regex("^([^()]+)\\(周\\)(?:.*)$").matchEntire(raw)?.groupValues?.get(1) ?: continue
                    slots(name,item.selectFirst("font[title=教室]")?.text().orEmpty(),item.selectFirst("font[title=教师]")?.text().orEmpty(),
                        column+1,node.toString(),week)
                }
            }
        }
    }
}

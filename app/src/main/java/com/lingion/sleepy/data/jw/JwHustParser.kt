package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** HUST detail cards bind each time, teacher and classroom to its own course target. */
class JwHustParser(source: String) : JwParser(source) {
    private val root by lazy { Jsoup.parse(source).selectFirst(".main-page-block") }

    override fun generateCourseList(): List<JwCourse> = buildList {
        for (block in root?.children().orEmpty()) {
            val paragraphs = block.select("p")
            if (paragraphs.size < 3) continue
            val name = paragraphs[0].text().trim()
            if (name.isEmpty()) continue
            val note = "开课对象：" + paragraphs[2].text().trim()
            for (body in block.select(".search-details-body")) for (detail in body.children()) {
                val boxes = detail.select(".text-box")
                if (boxes.size < 3) continue
                fun field(index: Int, label: String): String? {
                    val text = boxes[index].text().trim().replace('：', ':')
                    return text.takeIf { it.startsWith("$label:") }?.substringAfter(':')?.trim()
                }
                val time = field(0, "时间") ?: continue
                val teacher = field(1, "教师") ?: continue
                val room = field(2, "教室") ?: continue
                val parts = time.split(Regex("\\s+"))
                if (parts.size != 3 || !parts[2].endsWith("节")) continue
                val day = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日").indexOf(parts[1]) + 1
                if (day !in 1..7) continue
                for ((first, last) in schoolNodeRanges(parts[2].removeSuffix("节"))) {
                    for ((from, to, type) in schoolWeekRanges(parts[0])) {
                        add(JwCourse(name, room, teacher, day, first, last, from, to, type, note = note))
                    }
                }
            }
        }
    }.distinct()

    override fun confidence(): Int = if (root != null) 100 else 0
    override fun matchedFeatures(): List<String> = if (root != null) listOf("main-page-block/search-details-body") else emptyList()
}

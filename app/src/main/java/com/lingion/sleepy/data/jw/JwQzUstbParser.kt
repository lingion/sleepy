package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

class JwQzUstbParser(source: String) : JwQzParser(source) {
    override val usesSchoolRowMapping: Boolean = true

    override fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<JwCourse>) {
        val body = Jsoup.parseBodyFragment(infoStr).body()
        val room = body.getElementsByAttributeValue("title", "教室").firstOrNull() ?: return
        val parts = room.html().split(Regex("(?:&nbsp;)+"), limit = 2)
            .map { Jsoup.parseBodyFragment(it).text().trim() }
        if (parts.size != 2) return
        val location = body.getElementsByAttributeValue("title", "上课地点").text().trim()
            .removeSurrounding("[", "]")
        room.text(location + parts[0])
        body.getElementsByAttributeValue("title", "节次").remove()
        body.appendElement("font").attr("title", "周次").text(parts[1])
        super.convert(day, nodeCount, body.html(), courseList)
    }
}

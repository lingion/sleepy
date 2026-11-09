package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

class JwQzFsptParser(source: String) : JwQzParser(source) {
    override val usesSchoolRowMapping: Boolean = true

    override fun convert(day: Int, nodeCount: Int, infoStr: String, courseList: MutableList<JwCourse>) {
        val body = Jsoup.parseBodyFragment(infoStr).body()
        val time = body.getElementsByAttributeValue("title", "周次(节次)").firstOrNull() ?: return
        time.text(time.text().substringBefore('[').replace("(周)", "周"))
        body.getElementsByAttributeValue("title", "节次").remove()
        super.convert(day, nodeCount, body.html(), courseList)
    }
}

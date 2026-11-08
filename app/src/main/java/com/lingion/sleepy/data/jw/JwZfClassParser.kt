package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

class JwZfClassParser(source: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = JwZfVariantHtml.parseClass(source)

    override fun confidence(): Int = if (Jsoup.parse(source).selectFirst("table#Table6, table#table6") != null) 100 else 0

    override fun matchedFeatures(): List<String> = if (confidence() > 0) listOf("zf-class-table6") else emptyList()
}

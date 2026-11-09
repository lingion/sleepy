package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/** Optional source name, used only when seeding a fresh import confirmation. */
object JwSourceTableName {
    fun resolve(source: String, type: String?, schoolName: String): String? = when (type) {
        JwProtocol.TYPE_NWPU_POST -> JwNwpuPostSourceMetadata.tableName(source)
        JwProtocol.TYPE_SHU2024 -> JwUndergradBHtmlParser(source, type).tableName()
        JwProtocol.TYPE_WIST -> if (schoolName.contains("武汉船舶职业技术学院")) {
            val classes = Jsoup.parse(source).select("td[data-role=item] .mtt_arrange_item .mtt_item_bjmc")
                .map { it.text().trim() }.filter(String::isNotEmpty).distinct().sorted()
            "武船" + classes.joinToString(",") + "课表"
        } else null
        else -> null
    }
}

package com.lingion.sleepy.data.jw

/**
 * 强智 qz_with_node 变体解析器。
 *
 * 差异：title="周次(节次)" 的文本携带节次信息（不再让节次=所在大格位置），
 * 支持三种文本形态：
 *   1) 含空格 → "1-16(周) 1-2节" — 按空格拆 [周次段, 节次段]
 *   2) 无空格且无 [ ] → 独立 title="周次" + title="节次"
 *   3) 其它 → "周N-X节[Y-Z节]" / "1-16(周)[1-2节]" 格式按 '周[' ']' 拆
 *
 * 样本学校：北邮 jwgl.bupt.edu.cn/jsxsd、广外 jxgl.gdufs.edu.cn/jsxsd、
 *          北邮 WebVPN、北理工、北理工珠海、海南大学、江苏师大等 14 所。
 *
 * 上游源码：dIT8Zv/WakeupSchedule_BUPT QzWithNodeParser.kt (Apache-2.0)
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzWithNodeParser.kt
 */
class JwQzWithNodeParser(source: String) : JwQzParser(source) {

    // 6.6.2 also reads explicit sections in the base parser. Use the same field decoder
    // so teacher fallbacks, packed sections and local week parity cannot drift apart.

    /** T8 §2.5: 继承 + title=周次(节次) 含空格 / 独立 title=周次+节次 特征 */
    override fun matchedFeatures(): List<String> {
        val base = super.matchedFeatures()
        val jsoup = org.jsoup.Jsoup.parse(source)
        val withNode = jsoup.getElementsByAttributeValue("title", "周次(节次)")
        return base + when {
            withNode.any { it.text().contains(' ') } -> listOf("title=周次(节次)空格")
            jsoup.getElementsByAttributeValue("title", "周次").isNotEmpty() -> listOf("title=周次独立", "title=节次独立")
            else -> emptyList()
        }
    }
}

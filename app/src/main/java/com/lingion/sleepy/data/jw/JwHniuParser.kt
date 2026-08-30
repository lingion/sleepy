package com.lingion.sleepy.data.jw

import org.jsoup.Jsoup

/**
 * 湖南信息职业技术学院（HNIU）解析器 — T8。
 *
 * 协议：JwProtocol.TYPE_HNIU = "hniu"
 * 上游 wakeup 的 Common.kt 保留 type 常量但无独立 parser（实际按老正方 Table1 结构解析）。
 * T8 按 §2.5 清单实现 confidence 锚点：bordercolordark="#FFFFFF"。
 *
 * 解析委托给 [JwOldZfParser]（HNIU 教务页面为老正方 Table1 变体）。
 */
class JwHniuparser(source: String) : JwParser(source) {

    private val delegate = JwOldZfParser(source)

    override fun generateCourseList(): List<JwCourse> = delegate.generateCourseList()

    /** T8 §2.5: 命中 bordercolordark="#FFFFFF" = 100（HNIU 页面专属表格样式锚点） */
    override fun confidence(): Int = try {
        if (source.lowercase().contains("bordercolordark=\"#ffffff\"")) 100 else 0
    } catch (e: Exception) { 0 }

    override fun matchedFeatures(): List<String> =
        if (source.lowercase().contains("bordercolordark=\"#ffffff\""))
            listOf("bordercolordark=#FFFFFF") else emptyList()
}

package com.lingion.sleepy.data.jw

/**
 * 湖南信息职业技术学院课表解析器。
 * 按 bordercolordark 表格的显式星期、周次和节次解析。
 */
class JwHniuparser(source: String) : JwParser(source) {

    private val delegate = JwUndergradSchoolParser(source, "hniu")

    override fun generateCourseList(): List<JwCourse> = delegate.generateCourseList()

    override fun confidence(): Int = try {
        if (source.lowercase().contains("bordercolordark=\"#ffffff\"")) 100 else 0
    } catch (e: Exception) { 0 }

    override fun matchedFeatures(): List<String> =
        if (source.lowercase().contains("bordercolordark=\"#ffffff\""))
            listOf("bordercolordark=#FFFFFF") else emptyList()
}

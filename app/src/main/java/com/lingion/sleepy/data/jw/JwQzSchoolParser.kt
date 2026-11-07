package com.lingion.sleepy.data.jw

/** School row conventions observed in the local WakeUp 6.6.2 parser family. */
class JwQzSchoolParser(source: String, private val schoolType: String) : JwQzParser(source) {
    override val usesSchoolRowMapping: Boolean = true
    override val tableName: String =
        if (schoolType in setOf(JwProtocol.TYPE_QZ_WIT, JwProtocol.TYPE_QZ_AHUT)) "kbcontent1" else "kbcontent"

    override fun rowNodes(row: Int): Pair<Int, Int> {
        if (schoolType == JwProtocol.TYPE_QZ_SINGLE_NODE) return row to row
        val mapping = when (schoolType) {
            JwProtocol.TYPE_QZ_NJUST -> listOf(1 to 3, 4 to 5, 6 to 7, 8 to 10, 11 to 13)
            JwProtocol.TYPE_QZ_WIT -> listOf(1 to 2, 3 to 4, 6 to 7, 8 to 9, 10 to 12)
            JwProtocol.TYPE_QZ_ECUST -> listOf(1 to 2, 3 to 4, 4 to 4, 5 to 6, 7 to 8, 8 to 8, 9 to 10, 11 to 12)
            JwProtocol.TYPE_QZ_AHUT -> listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 11, 12 to 12)
            JwProtocol.TYPE_QZ_BJFU -> listOf(1 to 2, 3 to 4, 5 to 5, 6 to 7, 8 to 9, 10 to 11, 12 to 12)
            else -> emptyList()
        }
        return mapping.getOrNull(row - 1) ?: super.rowNodes(row)
    }

    override fun parseCourseName(infoStr: String): String =
        if (schoolType == JwProtocol.TYPE_QZ_BJFU)
            org.jsoup.Jsoup.parseBodyFragment(infoStr.substringBefore("<br>")).text().trim()
        else super.parseCourseName(infoStr)

    override fun matchedFeatures(): List<String> = super.matchedFeatures() + "declared-row-map=$schoolType"
}

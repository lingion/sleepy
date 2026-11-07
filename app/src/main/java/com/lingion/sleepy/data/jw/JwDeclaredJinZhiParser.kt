package com.lingion.sleepy.data.jw

/** Explicit catalog variants never fall through to unrelated marker-based parsers. */
internal class JwDeclaredJinZhiParser(source: String, private val protocol: String) : JwParser(source) {
    override fun generateCourseList(): List<JwCourse> = when (protocol) {
        "jz_1" -> WakeUpCompat.parseJzHtml(source)
        "jz_x" -> WakeUpCompat.parseJzX(source)
        "wist" -> WakeUpCompat.parseJzWist(source)
        "xmu" -> WakeUpCompat.parseJzXmu(source)
        "swjtu_post" -> nativeChain()
        else -> emptyList()
    }

    private fun nativeChain(): List<JwCourse> {
        for (parse in listOf(WakeUpCompat::parseJzHtml, WakeUpCompat::parseJzMtt,
            WakeUpCompat::parseJzWist, WakeUpCompat::parseJzX, WakeUpCompat::parseJzPost,
            WakeUpCompat::parseJzXTable, WakeUpCompat::parseJzXmu)) {
            val courses = parse(source)
            if (courses.isNotEmpty()) return courses
        }
        return emptyList()
    }
}

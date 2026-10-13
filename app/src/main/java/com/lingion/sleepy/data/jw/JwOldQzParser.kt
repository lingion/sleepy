package com.lingion.sleepy.data.jw

/** Stable qz_old entry point. See old-qz-super-cross-verify-2026-10-13 for source evidence. */
class JwOldQzParser(source: String) : JwParser(source) {
    private val delegate = JwOldQzSuperParser(source)
    override fun generateCourseList(): List<JwCourse> = delegate.generateCourseList()
    override fun confidence(): Int = delegate.confidence()
    override fun matchedFeatures(): List<String> = delegate.matchedFeatures()
}

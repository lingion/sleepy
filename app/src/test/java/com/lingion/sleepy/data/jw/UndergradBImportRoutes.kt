package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals

internal fun assertUndergradBImportRoutes(source: String, type: String): List<JwCourse> {
    val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
    assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
    assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
    if (type != "sues") assertDeclaredHtmlFrame(type, source, courses)
    val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "本科协议回归", type = type), courses, emptyList())
    assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
    return courses
}

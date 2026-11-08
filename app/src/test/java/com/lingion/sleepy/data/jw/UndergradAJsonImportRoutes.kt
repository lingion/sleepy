package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals

internal fun assertUndergradAJsonImportRoutes(type: String, source: String): List<JwCourse> {
    val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
    assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
    assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
    val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "JSON协议回归", type = type), courses, emptyList())
    assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
    return courses
}

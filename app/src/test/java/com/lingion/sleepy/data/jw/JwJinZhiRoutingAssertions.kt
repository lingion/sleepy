package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Every caller also compares the returned courses against independently written expected fields. */
internal fun assertJinZhiImportRoutes(source: String): List<JwCourse> {
    val parser = JwJzParser(source)
    val direct = parser.generateCourseList()
    assertEquals("declared registry", direct, JwParserRegistry.selectBest(source, JwProtocol.TYPE_JZ).first)
    assertEquals("import screen dispatch", direct, JwImportViewModel.parseHtmlStatic(source, JwProtocol.TYPE_JZ).first)
    assertEquals("automatic registry", direct, JwParserRegistry.selectBest(source).first)
    assertDeclaredHtmlFrame(JwProtocol.TYPE_JZ, source, direct)
    if (direct.isNotEmpty()) {
        assertTrue("native JZ page must have its own strong detection evidence", parser.confidence() >= 90)
        assertTrue("native JZ page must explain its detection evidence", parser.matchedFeatures().isNotEmpty())
    }
    return direct
}

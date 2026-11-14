package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JwSchoolSourceCodecTest {
    @Test
    fun `source snapshot preserves school identity and protocol entry`() {
        val school = JwSchoolInfo(
            sortKey = "H",
            name = "哈尔滨示例大学",
            url = "https://jw.example.edu.cn/",
            type = "wisedu",
            catalogId = "example-university-undergraduate",
        )

        val decoded = JwImportDraftCodec.schoolFromSourceJson(
            JwImportDraftCodec.toSchoolSourceJson(school)
        )

        assertNotNull(decoded)
        assertEquals(school.name, decoded?.name)
        assertEquals(school.url, decoded?.url)
        assertEquals(school.type, decoded?.type)
        assertEquals(school.catalogId, decoded?.catalogId)
    }

    @Test
    fun `non-JW and malformed snapshots are not eligible for update`() {
        assertNull(JwImportDraftCodec.schoolFromSourceJson(""))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("""{"schemaVersion":1,"sourceType":"file"}"""))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("not json"))
    }
}

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
            JwImportDraftCodec.toSchoolSourceJson(school),
            listOf(school),
        )

        assertNotNull(decoded)
        assertEquals(school.name, decoded?.name)
        assertEquals(school.url, decoded?.url)
        assertEquals(school.type, decoded?.type)
        assertEquals(school.catalogId, decoded?.catalogId)
    }

    @Test
    fun `non-JW and malformed snapshots are not eligible for update`() {
        assertNull(JwImportDraftCodec.schoolFromSourceJson("", emptyList()))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("""{"schemaVersion":1,"sourceType":"file"}""", emptyList()))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("not json", emptyList()))
    }

    @Test
    fun `source URL is taken from the catalog instead of an imported snapshot`() {
        val school = JwSchoolInfo(
            sortKey = "H",
            name = "哈尔滨示例大学",
            url = "https://jw.example.edu.cn/",
            type = "wisedu",
            catalogId = "example-university-undergraduate",
        )
        val forgedSnapshot = JwImportDraftCodec.toSchoolSourceJson(
            school.copy(url = "https://attacker.example/login")
        )

        val resolved = JwImportDraftCodec.schoolFromSourceJson(forgedSnapshot, listOf(school))

        assertEquals(school.url, resolved?.url)
        assertEquals(school.catalogId, resolved?.catalogId)
    }

    @Test
    fun `unknown catalog identity cannot enable an update action`() {
        val school = JwSchoolInfo(
            sortKey = "H",
            name = "哈尔滨示例大学",
            url = "https://jw.example.edu.cn/",
            type = "wisedu",
            catalogId = "example-university-undergraduate",
        )
        val forgedSnapshot = JwImportDraftCodec.toSchoolSourceJson(
            school.copy(url = "https://attacker.example/login", catalogId = "unknown-school")
        )

        assertNull(JwImportDraftCodec.schoolFromSourceJson(forgedSnapshot, listOf(school)))
    }

    @Test
    fun `catalog identity cannot override a mismatched school name or protocol`() {
        val school = JwSchoolInfo(
            sortKey = "H",
            name = "哈尔滨示例大学",
            url = "https://jw.example.edu.cn/",
            type = "wisedu",
            catalogId = "example-university-undergraduate",
        )
        val forgedSnapshot = JwImportDraftCodec.toSchoolSourceJson(
            school.copy(name = "另一所学校", type = "newzf")
        )

        assertNull(JwImportDraftCodec.schoolFromSourceJson(forgedSnapshot, listOf(school)))
    }
}

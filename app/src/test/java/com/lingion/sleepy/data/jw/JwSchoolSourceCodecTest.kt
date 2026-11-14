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
            sourceVerified = true,
        )

        assertNotNull(decoded)
        assertEquals(school.name, decoded?.name)
        assertEquals(school.url, decoded?.url)
        assertEquals(school.type, decoded?.type)
        assertEquals(school.catalogId, decoded?.catalogId)
    }

    @Test
    fun `non-JW and malformed snapshots are not eligible for update`() {
        assertNull(JwImportDraftCodec.schoolFromSourceJson("", emptyList(), sourceVerified = true))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("""{"schemaVersion":1,"sourceType":"file"}""", emptyList(), sourceVerified = true))
        assertNull(JwImportDraftCodec.schoolFromSourceJson("not json", emptyList(), sourceVerified = true))
        val snapshot = JwImportDraftCodec.toSchoolSourceJson(
            JwSchoolInfo("H", "学校", "https://jw.example.edu", "wisedu")
        )
        assertNull(JwImportDraftCodec.schoolFromSourceJson(snapshot, emptyList(), sourceVerified = false))
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

        val resolved = JwImportDraftCodec.schoolFromSourceJson(
            forgedSnapshot, listOf(school), sourceVerified = true,
        )

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

        assertNull(JwImportDraftCodec.schoolFromSourceJson(
            forgedSnapshot, listOf(school), sourceVerified = true,
        ))
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

        assertNull(JwImportDraftCodec.schoolFromSourceJson(
            forgedSnapshot, listOf(school), sourceVerified = true,
        ))
    }

    @Test
    fun `verified custom school outside catalog keeps its exact URL`() {
        val school = JwSchoolInfo(
            sortKey = "",
            name = "自定义教务",
            url = "https://jw.custom.example/login",
            type = "wisedu",
        )

        val resolved = JwImportDraftCodec.schoolFromSourceJson(
            JwImportDraftCodec.toSchoolSourceJson(school),
            emptyList(),
            sourceVerified = true,
        )

        assertEquals(school.url, resolved?.url)
        assertEquals(school.name, resolved?.name)
    }

    @Test
    fun `custom source cannot use unsafe URL or missing trust marker`() {
        val custom = JwSchoolInfo("", "自定义教务", "https://user:pass@jw.example/login", "wisedu")

        assertNull(JwImportDraftCodec.schoolFromSourceJson(
            JwImportDraftCodec.toSchoolSourceJson(custom), emptyList(), sourceVerified = true,
        ))
        assertNull(JwImportDraftCodec.schoolFromSourceJson(
            JwImportDraftCodec.toSchoolSourceJson(custom.copy(url = "javascript:alert(1)")),
            emptyList(), sourceVerified = true,
        ))
        assertNull(JwImportDraftCodec.schoolFromSourceJson(
            JwImportDraftCodec.toSchoolSourceJson(custom.copy(url = "https://jw.example/login")),
            emptyList(), sourceVerified = false,
        ))
    }
}

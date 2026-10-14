package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class SchoolSearchIndexTest {
    @Test fun `pending and graduate entries support names aliases initials and full pinyin`() {
        val school = JwSchoolInfo("B", "北京大学", status = JwSchoolInfo.STATUS_PENDING,
            aliases = listOf("PKU", "北大"), sortKeyFull = "beijingdaxue", catalogId = "one")
        val graduate = school.copy(name = "北京大学 - 研究生", catalogId = "two")
        val index = SchoolSearchIndex(listOf(school, graduate))
        for (query in listOf("北京", " pKu ", "北大", "bjdx", "beijingdaxue")) {
            assertEquals(query, listOf(school, graduate), index.search(query))
        }
        assertEquals(listOf(graduate), index.search("研究生"))
        assertFalse(school.canImport)
    }

    @Test fun `large catalogs retain stable keys and exact alias priority`() {
        val schools = (0 until 4000).map { number ->
            JwSchoolInfo("T", "测试学校$number", status = JwSchoolInfo.STATUS_PENDING,
                aliases = listOf("school$number"), catalogId = "test:$number")
        }
        val index = SchoolSearchIndex(schools)
        assertEquals(schools, index.search(" "))
        assertEquals(4000, index.search("测试").map { it.stableKey }.toSet().size)
        assertEquals(schools[123], index.search("school123").first())
        assertTrue(index.search("missing").isEmpty())
    }
}

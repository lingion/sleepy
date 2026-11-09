package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentJwSchoolsTest {

    private fun school(name: String) = JwSchoolInfo(
        sortKey = "X", name = name, catalogId = "X_$name"
    )

    @Test fun `a successful import becomes the first recent school`() {
        assertEquals(
            listOf("X_西南交通大学", "X_北京交通大学"),
            RecentJwSchools.merge(listOf("X_北京交通大学"), "X_西南交通大学")
        )
    }

    @Test fun `re-importing an existing school moves it to the front without duplicates`() {
        assertEquals(
            listOf("B", "A", "C"),
            RecentJwSchools.merge(listOf("A", "B", "C"), "B")
        )
        assertEquals(listOf("A"), RecentJwSchools.merge(listOf("A", "A"), "A"))
    }

    @Test fun `history is capped at three schools`() {
        assertEquals(
            listOf("E", "A", "B"),
            RecentJwSchools.merge(listOf("A", "B", "C", "D"), "E")
        )
        assertEquals(RecentJwSchools.MAX_ENTRIES, 3)
    }

    @Test fun `a blank import key never enters history nor reshuffles it`() {
        assertEquals(listOf("A", "B"), RecentJwSchools.merge(listOf("A", "B"), ""))
        assertEquals(emptyList<String>(), RecentJwSchools.merge(emptyList(), "  "))
    }

    @Test fun `blank duplicate and oversized records are sanitized on read`() {
        assertEquals(
            listOf("A", "B", "C"),
            RecentJwSchools.sanitize(listOf("", "A", "A", "  ", "B", "C", "D", "E"))
        )
    }

    @Test fun `resolve keeps recorded order and drops catalogue entries that no longer exist`() {
        val schools = listOf(school("西南交通大学"), school("北京交通大学"), school("同济大学"))
        val keys = listOf("X_同济大学", "X_已下架", "X_西南交通大学")
        assertEquals(
            listOf("同济大学", "西南交通大学"),
            RecentJwSchools.resolve(keys, schools).map { it.name }
        )
    }

    @Test fun `resolve never shows more than three schools`() {
        val schools = (1..5).map { school("学校$it") }
        val keys = schools.map { it.stableKey }
        assertEquals(3, RecentJwSchools.resolve(keys, schools).size)
    }

    // ===== 接线契约：只在导入成功落库后记录，空态不占列表项，字母索引跟着偏移 =====

    private fun loadSource(relPath: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { java.io.File(it, relPath).readText() }
            ?: error("Unable to load source: $relPath")

    private val activitySource: String by lazy { loadSource("ui/screen/imports/JwImportActivity.kt") }
    private val screenSource: String by lazy { loadSource("ui/screen/imports/SchoolSelectScreen.kt") }

    @Test fun `recording happens once at the successful import exit`() {
        val occurrences = Regex("recordRecentJwSchool\\(").findAll(activitySource).count()
        assertEquals("记录必须只挂在一处成功收口", 1, occurrences)
        val exit = activitySource.substringAfter("suspend fun finishOrReport").substringBefore("\n                }")
        assertTrue("点选学校但未导入不得写盘", exit.contains("recordRecentJwSchool("))
        assertTrue(exit.contains("school.stableKey"))
    }

    @Test fun `recent card sits above the city card and contributes to the index offset`() {
        val recentItem = screenSource.indexOf("item(key = \"recent_schools\")")
        val cityItem = screenSource.indexOf("item(key = \"possible_schools\")")
        assertTrue("最近导入卡片必须存在", recentItem >= 0)
        assertTrue("最近导入卡片必须在「可能的学校」之上", recentItem < cityItem)
        assertTrue(screenSource.contains("query.isBlank() && recentSchools.isNotEmpty()"))
        assertTrue(screenSource.contains("1 + (if (showRecommendations) 1 else 0) + (if (showRecentSchools) 1 else 0)"))
    }
}
